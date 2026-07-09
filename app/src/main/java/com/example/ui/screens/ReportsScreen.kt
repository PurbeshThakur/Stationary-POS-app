package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Sale
import com.example.data.SaleItem
import com.example.ui.InventoryViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: InventoryViewModel
) {
    val context = LocalContext.current
    val sales by viewModel.allSales.collectAsState()
    val saleItems by viewModel.allSaleItems.collectAsState()

    // Dialog state for viewing a past transaction's receipt
    var selectedSaleReceipt by remember { mutableStateOf<String?>(null) }
    var selectedSaleId by remember { mutableStateOf<Int?>(null) }

    // Aggregate statistics
    val totalRevenue = remember(sales) { sales.sumOf { it.totalAmount } }
    val totalProfit = remember(sales) { sales.sumOf { it.totalProfit } }
    val totalCost = remember(totalRevenue, totalProfit) { (totalRevenue - totalProfit).coerceAtLeast(0.0) }
    val transactionCount = remember(sales) { sales.size }
    val avgOrderValue = remember(totalRevenue, transactionCount) {
        if (transactionCount > 0) totalRevenue / transactionCount else 0.0
    }

    val currentRole by viewModel.currentUserRole.collectAsState()
    var unlockPin by remember { mutableStateOf("") }
    var unlockError by remember { mutableStateOf(false) }

    if (currentRole == com.example.ui.UserRole.CASHIER) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Access Restricted", fontWeight = FontWeight.Bold) }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(20.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Lock",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Text(
                            text = "Admin Authorization Required",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "Sensitive financial summary screens, profit metrics, and transaction logs are restricted to Administrator access.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = unlockPin,
                            onValueChange = {
                                unlockPin = it
                                unlockError = false
                            },
                            label = { Text("Enter Admin PIN") },
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            ),
                            isError = unlockError,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (unlockError) {
                            Text(
                                text = "Incorrect PIN. Try default '1234'.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Button(
                            onClick = {
                                if (unlockPin == "1234") {
                                    viewModel.setUserRole(com.example.ui.UserRole.ADMIN)
                                    unlockPin = ""
                                } else {
                                    unlockError = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = "Verify")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Unlock Reports")
                        }
                    }
                }
            }
        }
    } else {
        Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF4A4458)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.QueryStats,
                                contentDescription = "Reports",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                "Financial Reports",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Shop Performance & Metrics",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = Color(0xFF938F99)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // High-Level Summary Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Overall Shop Summary",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(horizontalAlignment = Alignment.Start) {
                                Text("TOTAL REVENUE", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                Text("NPR ${String.format("%.2f", totalRevenue)}", fontSize = 24.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.secondary)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("NET PROFIT", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                Text("NPR ${String.format("%.2f", totalProfit)}", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color(0xFF4CAF50))
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("COST OF GOODS", fontSize = 10.sp, color = Color.Gray)
                                Text("NPR ${String.format("%.2f", totalCost)}", fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text("TRANSACTIONS", fontSize = 10.sp, color = Color.Gray)
                                Text("$transactionCount", fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("AVG ORDER VALUE", fontSize = 10.sp, color = Color.Gray)
                                Text("NPR ${String.format("%.2f", avgOrderValue)}", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // History Log Header
            item {
                Text(
                    text = "Sales Transaction Log",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (sales.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.QueryStats, contentDescription = "No Sales", tint = Color.LightGray, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No sales transactions yet.", color = Color.Gray)
                        Text("Go to POS Dashboard to start selling items!", fontSize = 12.sp, color = Color.LightGray)
                    }
                }
            } else {
                items(sales) { sale ->
                    val sdf = remember { SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.getDefault()) }
                    val dateStr = sdf.format(Date(sale.timestamp))

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // Dynamically compile and show receipt
                                val matchedItems = saleItems.filter { it.saleId == sale.id }
                                selectedSaleId = sale.id
                                selectedSaleReceipt = buildFormattedReceiptForPastSale(sale, matchedItems)
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = when (sale.paymentMode) {
                                            "UPI" -> Icons.Default.QrCode
                                            "Card" -> Icons.Default.CreditCard
                                            else -> Icons.Default.Payments
                                        },
                                        contentDescription = "Mode",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Sale #${1000 + sale.id}",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(sale.paymentMode, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = dateStr,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "NPR ${String.format("%.2f", sale.totalAmount)}",
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "Profit: +NPR ${String.format("%.2f", sale.totalProfit)}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF4CAF50),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(40.dp)) }
        }
    }

    // --- Past Sale Receipt Re-print / Share Dialog ---
    if (selectedSaleReceipt != null) {
        AlertDialog(
            onDismissRequest = {
                selectedSaleReceipt = null
                selectedSaleId = null
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = "Receipt", tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Invoice No: #${1000 + (selectedSaleId ?: 0)}", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .background(Color(0xFFF9F9F9))
                        .border(1.dp, Color.LightGray, RoundedCornerShape(4.dp))
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                        item {
                            Text(
                                text = selectedSaleReceipt ?: "",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = Color.Black
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val textToShare = selectedSaleReceipt ?: ""
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, textToShare)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Receipt Via"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share Receipt / Send")
                    }

                    Button(
                        onClick = {
                            val textToShare = selectedSaleReceipt ?: ""
                            val whatsappIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, textToShare)
                                setPackage("com.whatsapp")
                            }
                            try {
                                context.startActivity(whatsappIntent)
                            } catch (e: Exception) {
                                val chooser = Intent.createChooser(whatsappIntent, "Send in WhatsApp")
                                context.startActivity(chooser)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "WhatsApp", tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Send via WhatsApp", color = Color.White)
                    }

                    Button(
                        onClick = {
                            val textToShare = selectedSaleReceipt ?: ""
                            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                                data = android.net.Uri.parse("mailto:")
                                putExtra(Intent.EXTRA_SUBJECT, "Purbesh Stationary - Sales Receipt #${1000 + (selectedSaleId ?: 0)}")
                                putExtra(Intent.EXTRA_TEXT, textToShare)
                            }
                            try {
                                context.startActivity(Intent.createChooser(emailIntent, "Send Email..."))
                            } catch (e: Exception) {
                                val fallbackIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, textToShare)
                                    putExtra(Intent.EXTRA_SUBJECT, "Purbesh Stationary - Sales Receipt #${1000 + (selectedSaleId ?: 0)}")
                                }
                                context.startActivity(Intent.createChooser(fallbackIntent, "Share Receipt Via"))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Email, contentDescription = "Email", tint = MaterialTheme.colorScheme.onPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Send via Email")
                    }

                    Button(
                        onClick = {
                            val textToShare = selectedSaleReceipt ?: ""
                            com.example.util.ReceiptExporter.printReceipt(context, textToShare, "Receipt_${1000 + (selectedSaleId ?: 0)}")
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Print, contentDescription = "Print")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Print Bill / Save as PDF")
                    }

                    Button(
                        onClick = {
                            val textToShare = selectedSaleReceipt ?: ""
                            val saleInvoiceId = "${1000 + (selectedSaleId ?: 0)}"
                            com.example.util.ReceiptExporter.saveReceiptToDownloads(context, textToShare, saleInvoiceId)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Download")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Download Bill (.txt)")
                    }

                    TextButton(
                        onClick = {
                            selectedSaleReceipt = null
                            selectedSaleId = null
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Dismiss")
                    }
                }
            }
        )
    }
    }
}

private fun buildFormattedReceiptForPastSale(sale: Sale, items: List<SaleItem>): String {
    val sdf = SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.getDefault())
    val dateStr = sdf.format(Date(sale.timestamp))
    val sb = StringBuilder()
    sb.append("===============================\n")
    sb.append("       PURBESH STATIONARY      \n")
    sb.append("    Your Premium Writing Hub    \n")
    sb.append("===============================\n")
    sb.append("Date: $dateStr\n")
    sb.append("Invoice: #${1000 + sale.id}\n")
    sb.append("-------------------------------\n")
    sb.append(String.format("%-18s %3s %8s\n", "Item Description", "Qty", "Price"))
    sb.append("-------------------------------\n")
    for (item in items) {
        val nameTrunc = if (item.productName.length > 17) item.productName.substring(0, 15) + ".." else item.productName
        sb.append(String.format("%-18s %3d %8.2f\n", nameTrunc, item.quantity, item.sellingPrice * item.quantity))
    }
    sb.append("-------------------------------\n")
    sb.append(String.format("%-22s %8.2f\n", "TOTAL AMOUNT:", sale.totalAmount))
    sb.append("Payment Mode: ${sale.paymentMode}\n")
    sb.append("===============================\n")
    sb.append("     Thank you for your visit! \n")
    sb.append("===============================\n")
    return sb.toString()
}
