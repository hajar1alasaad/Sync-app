package com.example.ui.wallet

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.TransactionRow
import com.example.data.payment.NowPaymentsInvoiceResponse
import com.example.data.payment.NowPaymentsService
import com.example.data.repository.UserWalletRepository
import com.example.data.supabase.SupabaseManager
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

sealed interface WalletUiState {
    data object Loading : WalletUiState
    data class Success(
        val balance: Double = 0.0,
        val currency: String = "USD",
        val transactions: List<TransactionRow> = emptyList(),
        val isGeneratingInvoice: Boolean = false,
        val activeInvoice: NowPaymentsInvoiceResponse? = null,
        val paymentStatusText: String? = null,
        val depositSuccessMessage: String? = null,
        val errorMessage: String? = null
    ) : WalletUiState
    data class Error(val message: String) : WalletUiState
}

class WalletViewModel(
    private val walletRepository: UserWalletRepository = UserWalletRepository.get()
) : ViewModel() {

    private val _uiState = MutableStateFlow<WalletUiState>(WalletUiState.Loading)
    val uiState: StateFlow<WalletUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null

    init {
        loadWallet()
        observeSharedWallet()
    }

    private fun observeSharedWallet() {
        viewModelScope.launch {
            walletRepository.balance.collect { newBalance ->
                val current = _uiState.value
                if (current is WalletUiState.Success) {
                    _uiState.value = current.copy(balance = newBalance)
                }
            }
        }
        viewModelScope.launch {
            walletRepository.transactions.collect { newTxList ->
                val current = _uiState.value
                if (current is WalletUiState.Success) {
                    _uiState.value = current.copy(transactions = newTxList)
                }
            }
        }
    }

    fun loadWallet() {
        viewModelScope.launch {
            val user = SupabaseManager.auth.currentUserOrNull()
            val userId = user?.id ?: "sync-user"
            walletRepository.attachUser(userId, viewModelScope)

            _uiState.value = WalletUiState.Success(
                balance = walletRepository.balance.value,
                currency = "USD",
                transactions = walletRepository.transactions.value
            )
        }
    }

    fun refreshWallet() {
        viewModelScope.launch {
            val user = SupabaseManager.auth.currentUserOrNull()
            val userId = user?.id ?: "sync-user"
            walletRepository.fetchWallet(userId)
        }
    }

    /**
     * Requirement 2: Real Payment Gateway Workflow (NOWPayments Integration):
     * 1. Generates a real payment invoice via NOWPayments API.
     * 2. Launches Chrome Custom Tabs with the invoice URL.
     * 3. Polls for payment status and only credits the balance upon verification.
     */
    fun initiateNowPaymentsDeposit(
        context: Context,
        amount: Double,
        onInvoiceReady: (NowPaymentsInvoiceResponse) -> Unit = {}
    ) {
        if (amount <= 0.0) return

        viewModelScope.launch {
            val current = _uiState.value as? WalletUiState.Success
            if (current != null) {
                _uiState.value = current.copy(
                    isGeneratingInvoice = true,
                    errorMessage = null,
                    paymentStatusText = "Connecting to NOWPayments gateway..."
                )
            }

            val result = NowPaymentsService.createInvoice(
                amount = amount,
                orderDescription = "Sync Digital Wallet Deposit ($${String.format(Locale.US, "%.2f", amount)})"
            )

            result.onSuccess { invoice ->
                Log.d(TAG, "Invoice generated: ${invoice.id} -> ${invoice.invoiceUrl}")
                val updatedState = (_uiState.value as? WalletUiState.Success)?.copy(
                    isGeneratingInvoice = false,
                    activeInvoice = invoice,
                    paymentStatusText = "Awaiting checkout confirmation"
                )
                if (updatedState != null) {
                    _uiState.value = updatedState
                }

                // Launch Chrome Custom Tabs for user payment
                launchPaymentUrl(context, invoice.invoiceUrl)
                onInvoiceReady(invoice)

                // Start polling payment status in background
                startPaymentStatusPolling(invoice.id, amount)
            }.onFailure { err ->
                Log.e(TAG, "Failed creating invoice: ${err.message}")
                val updatedState = (_uiState.value as? WalletUiState.Success)?.copy(
                    isGeneratingInvoice = false,
                    errorMessage = "Failed to create payment invoice: ${err.localizedMessage ?: "Network error"}"
                )
                if (updatedState != null) {
                    _uiState.value = updatedState
                }
            }
        }
    }

    private fun launchPaymentUrl(context: Context, url: String) {
        try {
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
            customTabsIntent.launchUrl(context, Uri.parse(url))
        } catch (e: Throwable) {
            Log.w(TAG, "CustomTabs failed, fallback to ACTION_VIEW: ${e.message}")
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (err: Throwable) {
                Log.e(TAG, "Cannot launch browser: ${err.message}")
            }
        }
    }

    private fun startPaymentStatusPolling(invoiceId: String, amount: Double) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch(Dispatchers.IO) {
            // Poll for up to 2 minutes every 4 seconds
            val maxAttempts = 30
            var attempts = 0
            while (attempts < maxAttempts) {
                delay(4000)
                attempts++
                val check = NowPaymentsService.checkInvoiceStatus(invoiceId, amount)
                val verification = check.getOrNull()
                if (verification != null && verification.isCompleted) {
                    Log.d(TAG, "NOWPayments status confirmed as finished!")
                    confirmAndCreditPayment(invoiceId, amount)
                    break
                }
            }
        }
    }

    /**
     * Confirms and credits the verified payment into the Supabase database.
     * Eliminates fake local increments and ensures wallets table updates.
     */
    fun confirmAndCreditPayment(
        invoiceId: String,
        amount: Double,
        onComplete: () -> Unit = {}
    ) {
        pollingJob?.cancel()
        viewModelScope.launch {
            val user = SupabaseManager.auth.currentUserOrNull()
            val userId = user?.id ?: "sync-user"

            val creditResult = walletRepository.creditDeposit(
                userId = userId,
                amount = amount,
                invoiceId = invoiceId,
                description = "NOWPayments Invoice #$invoiceId"
            )

            creditResult.onSuccess { newBal ->
                val current = _uiState.value as? WalletUiState.Success
                if (current != null) {
                    _uiState.value = current.copy(
                        balance = newBal,
                        activeInvoice = null,
                        isGeneratingInvoice = false,
                        paymentStatusText = null,
                        depositSuccessMessage = "Deposited $${String.format(Locale.US, "%.2f", amount)} successfully."
                    )
                }
                onComplete()
            }.onFailure { err ->
                val current = _uiState.value as? WalletUiState.Success
                if (current != null) {
                    _uiState.value = current.copy(
                        errorMessage = "Error crediting balance: ${err.message}"
                    )
                }
            }
        }
    }

    fun dismissMessages() {
        val current = _uiState.value as? WalletUiState.Success
        if (current != null) {
            _uiState.value = current.copy(
                depositSuccessMessage = null,
                errorMessage = null
            )
        }
    }

    fun cancelActiveInvoice() {
        pollingJob?.cancel()
        val current = _uiState.value as? WalletUiState.Success
        if (current != null) {
            _uiState.value = current.copy(
                activeInvoice = null,
                isGeneratingInvoice = false,
                paymentStatusText = null
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }

    companion object {
        private const val TAG = "WalletViewModel"
    }
}
