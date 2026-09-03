package com.example.ui.wallet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepositBottomSheet(
    walletViewModel: WalletViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusManager = LocalFocusManager.current
    val uiState by walletViewModel.uiState.collectAsState()

    // Strictly presets: $25, $50, $100 ($10 is strictly removed per requirement)
    val presets = listOf(25, 50, 100)

    var selectedPreset by remember { mutableStateOf<Int?>(50) }
    var customAmountText by remember { mutableStateOf("50") }

    val currentAmount: Double = customAmountText.toDoubleOrNull() ?: 0.0

    val successState = uiState as? WalletUiState.Success
    val activeInvoice = successState?.activeInvoice
    val isGenerating = successState?.isGeneratingInvoice == true

    ModalBottomSheet(
        onDismissRequest = {
            walletViewModel.cancelActiveInvoice()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = Color(0xFF1E293B),
        dragHandle = null,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Add Funds",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            textDirection = TextDirection.Content
                        ),
                        color = Color.White
                    )
                    Text(
                        text = "Real payment checkout powered by NOWPayments",
                        style = MaterialTheme.typography.bodySmall.copy(
                            textDirection = TextDirection.Content
                        ),
                        color = Color(0xFF94A3B8)
                    )
                }
                IconButton(
                    onClick = {
                        walletViewModel.cancelActiveInvoice()
                        onDismiss()
                    },
                    modifier = Modifier.testTag("close_deposit_sheet_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color(0xFF94A3B8)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Error banner if any
            if (successState?.errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0x33EF4444)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                ) {
                    Text(
                        text = successState.errorMessage,
                        color = Color(0xFFFCA5A5),
                        style = MaterialTheme.typography.bodySmall.copy(
                            textDirection = TextDirection.Content
                        ),
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            if (activeInvoice != null) {
                // Active NOWPayments invoice checkout view
                ActiveInvoiceView(
                    invoice = activeInvoice,
                    amount = currentAmount,
                    statusText = successState.paymentStatusText ?: "Awaiting payment...",
                    onVerifyAndCredit = {
                        walletViewModel.confirmAndCreditPayment(activeInvoice.id, currentAmount) {
                            onDismiss()
                        }
                    },
                    onReopenUrl = {
                        walletViewModel.initiateNowPaymentsDeposit(context, currentAmount)
                    },
                    onCancel = {
                        walletViewModel.cancelActiveInvoice()
                    }
                )
            } else {
                // Preset Buttons Section: strictly $25, $50, $100
                Text(
                    text = "SELECT PRESET AMOUNT",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        textDirection = TextDirection.Content
                    ),
                    color = Color(0xFF818CF8)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    presets.forEach { preset ->
                        val isSelected = selectedPreset == preset
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) Color(0xFF4F46E5) else Color(0xFF0F172A)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) Color(0xFF818CF8) else Color(0xFF334155),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .testTag("preset_$preset")
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Button(
                                    onClick = {
                                        selectedPreset = preset
                                        customAmountText = preset.toString()
                                        focusManager.clearFocus()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.Transparent,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "$$preset",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        style = TextStyle(textDirection = TextDirection.Content),
                                        color = if (isSelected) Color.White else Color(0xFFCBD5E1)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Custom Amount Input Field with explicit textDirection
                Text(
                    text = "OR ENTER CUSTOM AMOUNT",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        textDirection = TextDirection.Content
                    ),
                    color = Color(0xFF818CF8)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = customAmountText,
                    onValueChange = { input ->
                        val filtered = input.filter { it.isDigit() || it == '.' }
                        customAmountText = filtered
                        val parsed = filtered.toIntOrNull()
                        selectedPreset = if (parsed in presets) parsed else null
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.AttachMoney,
                            contentDescription = "Dollar Sign",
                            tint = Color(0xFF818CF8)
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    ),
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 16.sp,
                        textDirection = TextDirection.Content
                    ),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF6366F1),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedContainerColor = Color(0xFF0F172A),
                        unfocusedContainerColor = Color(0xFF0F172A),
                        cursorColor = Color(0xFF6366F1)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("custom_amount_input")
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Encrypted checkout via Chrome Custom Tabs",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        style = TextStyle(textDirection = TextDirection.Content)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Real Payment Gateway Trigger Button (NOWPayments)
                Button(
                    onClick = {
                        if (currentAmount > 0.0) {
                            focusManager.clearFocus()
                            walletViewModel.initiateNowPaymentsDeposit(context, currentAmount)
                        }
                    },
                    enabled = currentAmount > 0.0 && !isGenerating,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6366F1),
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFF334155),
                        disabledContentColor = Color(0xFF64748B)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("confirm_deposit_button")
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Generating Invoice...",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            style = TextStyle(textDirection = TextDirection.Content)
                        )
                    } else {
                        Text(
                            text = if (currentAmount > 0.0) {
                                "Deposit $${String.format(Locale.US, "%.2f", currentAmount)} via NOWPayments"
                            } else {
                                "Enter a valid amount"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            style = TextStyle(textDirection = TextDirection.Content)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveInvoiceView(
    invoice: com.example.data.payment.NowPaymentsInvoiceResponse,
    amount: Double,
    statusText: String,
    onVerifyAndCredit: () -> Unit,
    onReopenUrl: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(Color(0xFF312E81), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = Color(0xFF818CF8),
                    strokeWidth = 3.dp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Checkout in Progress",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    textDirection = TextDirection.Content
                ),
                color = Color.White
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Invoice #${invoice.id.takeLast(12)}",
                style = MaterialTheme.typography.bodySmall.copy(
                    textDirection = TextDirection.Content
                ),
                color = Color(0xFF94A3B8)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "$${String.format(Locale.US, "%.2f", amount)} USD",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp,
                    textDirection = TextDirection.Content
                ),
                color = Color(0xFF38BDF8)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall.copy(
                    textDirection = TextDirection.Content
                ),
                color = Color(0xFFCBD5E1)
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Action: Verify & Credit Balance
            Button(
                onClick = onVerifyAndCredit,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("verify_payment_button")
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Verify & Complete Deposit",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    style = TextStyle(textDirection = TextDirection.Content)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onReopenUrl,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF818CF8)),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInBrowser,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Reopen",
                        fontSize = 13.sp,
                        style = TextStyle(textDirection = TextDirection.Content)
                    )
                }

                OutlinedButton(
                    onClick = onCancel,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Cancel",
                        fontSize = 13.sp,
                        style = TextStyle(textDirection = TextDirection.Content)
                    )
                }
            }
        }
    }
}
