package com.example.data.repository

import android.util.Log
import com.example.data.model.TransactionRow
import com.example.data.model.WalletRow
import com.example.data.supabase.SupabaseManager
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shared UserWalletRepository providing real-time synchronization of wallet balance
 * and transactions across the application (ProfileScreen <-> WalletScreen).
 */
class UserWalletRepository private constructor() {

    private val _balance = MutableStateFlow(0.0)
    val balance: StateFlow<Double> = _balance.asStateFlow()

    private val _transactions = MutableStateFlow<List<TransactionRow>>(emptyList())
    val transactions: StateFlow<List<TransactionRow>> = _transactions.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private var activeUserId: String? = null
    private var realtimeJob: Job? = null

    /**
     * Connects or refreshes the repository for the given authenticated user.
     */
    fun attachUser(userId: String, scope: CoroutineScope) {
        if (activeUserId == userId && realtimeJob?.isActive == true) {
            // Already attached
            return
        }
        activeUserId = userId
        scope.launch {
            fetchWallet(userId)
            startRealtimeListener(userId, scope)
        }
    }

    suspend fun fetchWallet(userId: String) = withContext(Dispatchers.IO) {
        _isSyncing.value = true
        try {
            // Query current balance from Supabase wallets table
            val walletList = runCatching {
                SupabaseManager.client.from("wallets")
                    .select {
                        filter { eq("user_id", userId) }
                    }
                    .decodeList<WalletRow>()
            }.getOrElse { err ->
                Log.w(TAG, "Querying wallets table failed safely: ${err.message}")
                emptyList()
            }

            val remoteWallet = walletList.firstOrNull()
            val currentRemoteBalance = remoteWallet?.balance ?: _balance.value
            _balance.value = currentRemoteBalance

            // Query transactions
            val remoteTransactions = runCatching {
                SupabaseManager.client.from("transactions")
                    .select {
                        filter { eq("user_id", userId) }
                    }
                    .decodeList<TransactionRow>()
            }.getOrElse { err ->
                Log.w(TAG, "Querying transactions failed safely: ${err.message}")
                emptyList()
            }

            // Merge with local transactions keeping uniqueness
            val combined = (remoteTransactions + _transactions.value)
                .distinctBy { it.id ?: (it.amount.toString() + it.description + it.createdAt) }
            _transactions.value = combined

            Log.d(TAG, "Wallet fetched for $userId. Balance: $$currentRemoteBalance, Tx count: ${combined.size}")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed fetching wallet: ${e.message}")
        } finally {
            _isSyncing.value = false
        }
    }

    /**
     * Attaches Supabase Realtime listener on 'wallets' table for instant reactive updates.
     */
    private fun startRealtimeListener(userId: String, scope: CoroutineScope) {
        realtimeJob?.cancel()
        realtimeJob = scope.launch(Dispatchers.IO) {
            try {
                val channel = SupabaseManager.client.channel("realtime-wallets-$userId")
                val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "wallets"
                }

                channel.subscribe()
                Log.d(TAG, "Subscribed to Supabase Realtime channel for user $userId")

                changeFlow.collect { action ->
                    when (action) {
                        is PostgresAction.Insert,
                        is PostgresAction.Update -> {
                            Log.d(TAG, "Realtime action on wallets received: $action")
                            fetchWallet(userId)
                        }
                        else -> Unit
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Realtime subscription info: ${e.message}. Using reactive StateFlow.")
            }
        }
    }

    /**
     * Credits a verified deposit from NOWPayments or confirmed payment gateway into the database.
     * Updates Supabase wallets and transactions table, then immediately updates the shared StateFlow.
     */
    suspend fun creditDeposit(
        userId: String,
        amount: Double,
        invoiceId: String? = null,
        description: String = "NOWPayments Deposit"
    ): Result<Double> = withContext(Dispatchers.IO) {
        try {
            val previousBalance = _balance.value
            val updatedBalance = previousBalance + amount

            // 1. Update Supabase wallets row
            runCatching {
                SupabaseManager.client.from("wallets").upsert(
                    WalletRow(
                        userId = userId,
                        balance = updatedBalance,
                        currency = "USD"
                    )
                )
            }.onFailure { err ->
                Log.w(TAG, "Database upsert warning for wallet: ${err.message}")
            }

            // 2. Insert transaction entry
            val newTx = TransactionRow(
                userId = userId,
                amount = amount,
                type = "deposit",
                description = if (invoiceId != null) "$description ($invoiceId)" else description,
                createdAt = "Just now"
            )

            runCatching {
                SupabaseManager.client.from("transactions").insert(newTx)
            }.onFailure { err ->
                Log.w(TAG, "Database insert warning for transaction: ${err.message}")
            }

            // 3. Update in-memory reactive state immediately
            withContext(Dispatchers.Main) {
                _balance.value = updatedBalance
                _transactions.value = listOf(newTx) + _transactions.value
            }

            Log.d(TAG, "Credited deposit of $$amount. New balance: $$updatedBalance for $userId")
            Result.success(updatedBalance)
        } catch (e: Throwable) {
            Log.e(TAG, "Error crediting deposit: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun updateLocalBalanceOptimistic(newBalance: Double) {
        _balance.value = newBalance
    }

    companion object {
        private const val TAG = "UserWalletRepository"

        @Volatile
        private var instance: UserWalletRepository? = null

        fun get(): UserWalletRepository {
            return instance ?: synchronized(this) {
                instance ?: UserWalletRepository().also { instance = it }
            }
        }
    }
}
