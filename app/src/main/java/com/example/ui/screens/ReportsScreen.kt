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
import com.example.ui.Expense
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
    val expenses by viewModel.expensesListState.collectAsState()
    val shopName by viewModel.shopName.collectAsState()
    val panNumber by viewModel.panNumber.collectAsState()

    // Tab state: 0 = Sales & Export, 1 = Expenses & Bills
    var selectedTabState by remember { mutableStateOf(0) }

    // Dialog state for viewing a past transaction's receipt
    var selectedSaleReceipt by remember { mutableStateOf<String?>(null) }
    var selectedSaleId by remember { mutableStateOf<Int?>(null) }

    // Dialog state for logging new expense
    var showAddExpenseDialog by remember { mutableStateOf(false) }

    // Aggregate statistics
    val totalRevenue = remember(sales) { sales.sumOf { it.totalAmount } }
    val totalSalesProfit = remember(sales) { sales.sumOf { it.totalProfit } }
    val totalCostOfGoods = remember(totalRevenue, totalSalesProfit) { (totalRevenue - totalSalesProfit).coerceAtLeast(0.0) }
    val totalExpenses = remember(expenses) { expenses.sumOf { it.amount } }
    val netProfit = remember(totalSalesProfit, totalExpenses) { totalSalesProfit - totalExpenses }

    val transactionCount = remember(sales) { sales.size }
    val avgOrderValue = remember(totalRevenue, transactionCount) {
        if (transactionCount > 0) totalRevenue / transactionCount else 0.0
    }

    val currentRole by viewModel.currentUserRole.collectAsState()
    val loggedInUser by viewModel.loggedInUser.collectAsState()
    var unlockPin by remember { mutableStateOf("") }
    var unlockError by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    val filteredSales = remember(sales, saleItems, searchQuery) {
        if (searchQuery.isBlank()) {
            sales
        } else {
            val query = searchQuery.trim().lowercase(Locale.getDefault())
            sales.filter { sale ->
                val sdf = SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.getDefault())
                val dateStr = sdf.format(Date(sale.timestamp)).lowercase(Locale.getDefault())
                
                val invoiceStr = (1000 + sale.id).toString()
                val invoiceFormatted = "sale #$invoiceStr"
                val phoneStr = sale.customerPhone.lowercase(Locale.getDefault())
                val amountStr = sale.totalAmount.toString()
                val amountFormatted = "npr ${String.format("%.2f", sale.totalAmount)}"
                
                val matchesItems = saleItems.filter { it.saleId == sale.id }.any { item ->
                    item.productName.lowercase(Locale.getDefault()).contains(query) ||
                    item.barcode.lowercase(Locale.getDefault()).contains(query)
                }
                
                invoiceStr.contains(query) ||
                invoiceFormatted.contains(query) ||
                phoneStr.contains(query) ||
                amountStr.contains(query) ||
                amountFormatted.contains(query) ||
                dateStr.contains(query) ||
                matchesItems
            }
        }
    }

    val canView = loggedInUser?.canViewReports == true || currentRole == com.example.ui.UserRole.ADMIN
    if (!canView) {
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
                                val authUser = viewModel.usersList.find { it.pin == unlockPin && it.isEnabled && (it.role == com.example.ui.UserRole.ADMIN || it.canViewReports) }
                                if (authUser != null) {
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
                    actions = {
                        com.example.util.LanguageToggle(
                            viewModel = viewModel,
                            modifier = Modifier.padding(end = 16.dp)
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Custom Capsule Navigation Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ElevatedFilterChip(
                        selected = selectedTabState == 0,
                        onClick = { selectedTabState = 0 },
                        label = { Text("Sales & Exports") },
                        leadingIcon = { Icon(Icons.Default.TrendingUp, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.weight(1f)
                    )
                    ElevatedFilterChip(
                        selected = selectedTabState == 1,
                        onClick = { selectedTabState = 1 },
                        label = { Text("Expenses Tracker") },
                        leadingIcon = { Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (selectedTabState == 0) {
                    // TAB 0: SALES PERFORMANCE & REPORT EXPORT
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item { Spacer(modifier = Modifier.height(4.dp)) }

                        // High-Level Summary Card
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        "Overall Sales Summary",
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
                                            Text("NPR ${String.format("%.2f", totalRevenue)}", fontSize = 22.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.secondary)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("SALES PROFIT", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                            Text("NPR ${String.format("%.2f", totalSalesProfit)}", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color(0xFF4CAF50))
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))
                                    HorizontalDivider()
                                    Spacer(modifier = Modifier.height(16.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("COST OF GOODS", fontSize = 10.sp, color = Color.Gray)
                                            Text("NPR ${String.format("%.2f", totalCostOfGoods)}", fontWeight = FontWeight.Bold)
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

                        // Premium Report Export Center Card
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Default.CloudDownload, contentDescription = "Export", tint = MaterialTheme.colorScheme.primary)
                                        Text(
                                            "Report Export Center",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Text(
                                        "Generate instant audit logs and file reports directly from current store transaction metrics.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                // Export Excel / CSV
                                                val csv = generateSalesCsv(sales)
                                                com.example.util.ReceiptExporter.saveCsvReportToDownloads(context, csv, "Sales_Ledger")
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.GridOn, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Excel (CSV)")
                                        }

                                        Button(
                                            onClick = {
                                                // Export PDF via Android Print Engine
                                                val reportStr = generateSalesSummaryReport(sales, totalRevenue, totalSalesProfit, totalCostOfGoods, transactionCount, shopName)
                                                com.example.util.ReceiptExporter.printReceipt(context, reportStr, "Store_Sales_Report")
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Print (PDF)")
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

                        item {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                label = { Text("Search transactions...") },
                                placeholder = { Text("Invoice, phone, item name, amount, date/time...") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search"
                                    )
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Clear search"
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
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
                        } else if (filteredSales.isEmpty()) {
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 40.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = "No Matches", tint = Color.LightGray, modifier = Modifier.size(64.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("No matching transactions found.", color = Color.Gray)
                                    Text("Try checking your search keywords, invoice # or phone.", fontSize = 12.sp, color = Color.LightGray)
                                }
                            }
                        } else {
                            items(filteredSales) { sale ->
                                val sdf = remember { SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.getDefault()) }
                                val dateStr = sdf.format(Date(sale.timestamp))
                                val matchedItems = remember(sale.id, saleItems) {
                                    saleItems.filter { it.saleId == sale.id }
                                }
                                val itemsSummary = remember(matchedItems) {
                                    matchedItems.joinToString { "${it.productName} x${it.quantity}" }
                                }

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedSaleId = sale.id
                                            selectedSaleReceipt = buildFormattedReceiptForPastSale(sale, matchedItems, shopName, panNumber)
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
                                                    imageVector = when {
                                                        sale.paymentMode.contains("Card", ignoreCase = true) -> Icons.Default.CreditCard
                                                        sale.paymentMode.contains("Store Credit", ignoreCase = true) || sale.paymentMode.contains("Debt", ignoreCase = true) -> Icons.Default.AccountBalanceWallet
                                                        sale.paymentMode.contains("Split", ignoreCase = true) -> Icons.Default.CallSplit
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
                                            if (sale.customerPhone.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "Phone: ${sale.customerPhone}",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                                )
                                            }
                                            if (itemsSummary.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = itemsSummary,
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Sold By: ${sale.soldBy}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
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
                } else {
                    // TAB 1: EXPENSES & BILL TRACKER
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item { Spacer(modifier = Modifier.height(4.dp)) }

                        // Aggregated Metrics including Net Profit
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(
                                        "Expense & Bill Analytics",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("SALES MARGINS", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                            Text("NPR ${String.format("%.2f", totalSalesProfit)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("TOTAL EXPENSES", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                            Text("NPR ${String.format("%.2f", totalExpenses)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("NET INCOME", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                            val incomeColor = if (netProfit >= 0) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                                            Text("NPR ${String.format("%.2f", netProfit)}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = incomeColor)
                                        }
                                    }

                                    HorizontalDivider()

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Net Income is calculated as: Total Product Profit - Logged Expenditures (utilities, rent, snacks, salary, etc.).",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // Add Expenditure Button
                        item {
                            Button(
                                onClick = { showAddExpenseDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Record/Log New Expense")
                            }
                        }

                        // Expenses List Log
                        item {
                            Text(
                                text = "Logged Expense Ledger",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (expenses.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(48.dp))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("No expenses logged yet.", color = Color.Gray)
                                    }
                                }
                            }
                        } else {
                            items(expenses) { expense ->
                                val sdf = remember { SimpleDateFormat("dd MMM, yyyy", Locale.getDefault()) }
                                val dateStr = sdf.format(Date(expense.timestamp))

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Box(
                                                    modifier = Modifier
                                                        .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(expense.category.uppercase(), fontSize = 8.sp, color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                                                }
                                                Text(expense.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            }
                                            if (expense.description.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(expense.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(dateStr, fontSize = 10.sp, color = Color.Gray)
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(
                                                text = "-NPR ${String.format("%.2f", expense.amount)}",
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.error,
                                                fontSize = 14.sp
                                            )
                                            IconButton(
                                                onClick = { viewModel.deleteExpense(expense.id) },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item { Spacer(modifier = Modifier.height(40.dp)) }
                    }
                }
            }
        }

        // --- Add Expense Dialog ---
        if (showAddExpenseDialog) {
            var expTitle by remember { mutableStateOf("") }
            var expAmount by remember { mutableStateOf("") }
            var expCategory by remember { mutableStateOf("Utilities") }
            var expDesc by remember { mutableStateOf("") }
            val categoriesList = listOf("Utilities", "Rent & Space", "Food & Refreshments", "Stock Purchase", "Salaries & Wage", "Office & Supplies", "Others")
            var dropdownExpanded by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { showAddExpenseDialog = false },
                title = { Text("Record Shop Expenditure", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = expTitle,
                            onValueChange = { expTitle = it },
                            label = { Text("Expense Title / Payee") },
                            placeholder = { Text("e.g. NEA Electricity Bill") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = expAmount,
                            onValueChange = { expAmount = it },
                            label = { Text("Amount Paid (NPR)") },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = expCategory,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Category") },
                                trailingIcon = {
                                    IconButton(onClick = { dropdownExpanded = true }) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().clickable { dropdownExpanded = true }
                            )
                            DropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false },
                                modifier = Modifier.fillMaxWidth(0.8f)
                            ) {
                                categoriesList.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat) },
                                        onClick = {
                                            expCategory = cat
                                            dropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = expDesc,
                            onValueChange = { expDesc = it },
                            label = { Text("Description (Optional)") },
                            singleLine = false,
                            maxLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val amt = expAmount.toDoubleOrNull() ?: 0.0
                            if (expTitle.isNotBlank() && amt > 0.0) {
                                viewModel.addExpense(
                                    Expense(
                                        id = UUID.randomUUID().toString(),
                                        title = expTitle,
                                        category = expCategory,
                                        amount = amt,
                                        timestamp = System.currentTimeMillis(),
                                        description = expDesc
                                    )
                                )
                                showAddExpenseDialog = false
                            }
                        },
                        enabled = expTitle.isNotBlank() && expAmount.isNotBlank()
                    ) {
                        Text("Log Expense")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddExpenseDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
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
                                    putExtra(Intent.EXTRA_SUBJECT, "$shopName - Sales Receipt #${1000 + (selectedSaleId ?: 0)}")
                                    putExtra(Intent.EXTRA_TEXT, textToShare)
                                }
                                try {
                                    context.startActivity(Intent.createChooser(emailIntent, "Send Email..."))
                                } catch (e: Exception) {
                                    val fallbackIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, textToShare)
                                        putExtra(Intent.EXTRA_SUBJECT, "$shopName - Sales Receipt #${1000 + (selectedSaleId ?: 0)}")
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

private fun buildFormattedReceiptForPastSale(sale: Sale, items: List<SaleItem>, shopName: String, panNumber: String): String {
    val sdf = SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.getDefault())
    val dateStr = sdf.format(Date(sale.timestamp))
    val sb = StringBuilder()
    sb.append("===============================\n")
    sb.append("       ${shopName.uppercase()}      \n")
    if (panNumber.isNotBlank()) {
        sb.append("       PAN No: $panNumber      \n")
    }
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

private fun generateSalesCsv(sales: List<Sale>): String {
    val sb = StringBuilder()
    sb.append("Invoice ID,Date,Payment Mode,Total Amount (NPR),Net Profit (NPR)\n")
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    for (sale in sales) {
        val dateStr = sdf.format(Date(sale.timestamp))
        sb.append("${1000 + sale.id},$dateStr,${sale.paymentMode},${sale.totalAmount},${sale.totalProfit}\n")
    }
    return sb.toString()
}

private fun generateSalesSummaryReport(
    sales: List<Sale>,
    totalRevenue: Double,
    totalProfit: Double,
    totalCost: Double,
    transactionCount: Int,
    shopName: String
): String {
    val sdf = SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.getDefault())
    val dateStr = sdf.format(Date())
    val sb = StringBuilder()
    sb.append("========================================\n")
    sb.append("        ${shopName.uppercase()} REPORT  \n")
    sb.append("           OVERALL SALES PERFORMANCE    \n")
    sb.append("========================================\n")
    sb.append("Generated on: $dateStr\n")
    sb.append("----------------------------------------\n")
    sb.append(String.format("%-25s : NPR %10.2f\n", "TOTAL SALES REVENUE", totalRevenue))
    sb.append(String.format("%-25s : NPR %10.2f\n", "TOTAL COST OF GOODS", totalCost))
    sb.append(String.format("%-25s : NPR %10.2f\n", "TOTAL NET PROFIT", totalProfit))
    sb.append(String.format("%-25s : %12d\n", "TRANSACTIONS COUNT", transactionCount))
    sb.append("----------------------------------------\n\n")
    sb.append("RECENT TRANSACTION SUMMARY:\n")
    sb.append("----------------------------------------\n")
    sb.append(String.format("%-8s %-12s %10s\n", "ID", "Mode", "Total (NPR)"))
    sb.append("----------------------------------------\n")
    for (sale in sales.take(15)) {
        sb.append(String.format("#%-7d %-12s %10.2f\n", 1000 + sale.id, sale.paymentMode, sale.totalAmount))
    }
    sb.append("----------------------------------------\n")
    sb.append("      Thank you for choosing POS!       \n")
    sb.append("========================================\n")
    return sb.toString()
}
