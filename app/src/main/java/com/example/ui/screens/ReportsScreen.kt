package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.example.data.ProductReturn
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
    val products by viewModel.allProducts.collectAsState()
    val expenses by viewModel.expensesListState.collectAsState()
    val returns by viewModel.allReturns.collectAsState()
    val shopName by viewModel.shopName.collectAsState()
    val panNumber by viewModel.panNumber.collectAsState()

    // Tab state: 0 = Returns Ledger, 1 = Sales & Export, 2 = Expenses & Bills
    var selectedTabState by remember { mutableStateOf(1) }

    // Dialog state for viewing a past transaction's receipt
    var selectedSaleReceipt by remember { mutableStateOf<String?>(null) }
    var selectedSaleId by remember { mutableStateOf<Int?>(null) }

    // Dialog state for logging new expense
    var showAddExpenseDialog by remember { mutableStateOf(false) }

    // Dialog state for logging product returns
    var showAddReturnDialog by remember { mutableStateOf(false) }
    var returnSearchQuery by remember { mutableStateOf("") }
    
    // Process Return Dialog state variables
    var selectedProductForReturn by remember { mutableStateOf<com.example.data.Product?>(null) }
    var returnQuantity by remember { mutableStateOf(1) }
    var returnRefundAmount by remember { mutableStateOf("") }
    var returnReason by remember { mutableStateOf("Defective") }
    var returnSaleIdInput by remember { mutableStateOf("") }
    var returnPaymentMode by remember { mutableStateOf("Cash") }
    var productSearchQuery by remember { mutableStateOf("") }
    var allowUnsoldReturnOverride by remember { mutableStateOf(false) }

    val invoiceNumberMap = remember(sales) {
        sales.sortedBy { it.timestamp }.mapIndexed { index, sale ->
            sale.id to (1001 + index)
        }.toMap()
    }

    val billToSaleIdMap = remember(sales) {
        sales.sortedBy { it.timestamp }.mapIndexed { index, sale ->
            (1001 + index) to sale.id
        }.toMap()
    }

    // Aggregate statistics
    val totalRefundedAmount = remember(returns) { returns.sumOf { it.refundAmount } }
    val totalRevenue = remember(sales, totalRefundedAmount) { (sales.sumOf { it.totalAmount } - totalRefundedAmount).coerceAtLeast(0.0) }
    val totalSalesProfit = remember(sales, totalRefundedAmount) { (sales.sumOf { it.totalProfit } - totalRefundedAmount).coerceAtLeast(0.0) }
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
    val filteredTransactions = remember(sales, saleItems, returns, searchQuery, invoiceNumberMap) {
        val query = searchQuery.trim().lowercase(Locale.getDefault())
        if (query.isBlank()) {
            val salesTx = sales.map { TransactionItem.SaleTx(it) }
            val returnsTx = returns.map { TransactionItem.ReturnTx(it) }
            (salesTx + returnsTx).sortedByDescending { it.timestamp }
        } else {
            val matchedSales = sales.filter { sale ->
                val sdf = SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.getDefault())
                val dateStr = sdf.format(Date(sale.timestamp)).lowercase(Locale.getDefault())
                
                val invoiceNo = invoiceNumberMap[sale.id] ?: (1000 + sale.id)
                val invoiceStr = invoiceNo.toString()
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
            }.map { TransactionItem.SaleTx(it) }
            
            val matchedReturns = returns.filter { ret ->
                val sdf = SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.getDefault())
                val dateStr = sdf.format(Date(ret.timestamp)).lowercase(Locale.getDefault())
                
                val nameStr = ret.productName.lowercase(Locale.getDefault())
                val barcodeStr = ret.barcode.lowercase(Locale.getDefault())
                val reasonStr = ret.reason.lowercase(Locale.getDefault())
                val userStr = ret.returnedBy.lowercase(Locale.getDefault())
                val amountStr = ret.refundAmount.toString()
                
                val saleIdStr = ret.saleId?.let { 
                    val invNo = if (it >= 1001) it else (1000 + it)
                    invNo.toString()
                } ?: ""
                
                nameStr.contains(query) ||
                barcodeStr.contains(query) ||
                reasonStr.contains(query) ||
                userStr.contains(query) ||
                amountStr.contains(query) ||
                dateStr.contains(query) ||
                (saleIdStr.isNotBlank() && saleIdStr.contains(query))
            }.map { TransactionItem.ReturnTx(it) }
            
            (matchedSales + matchedReturns).sortedByDescending { it.timestamp }
        }
    }

    val canView = loggedInUser?.isSuperAdmin == true || loggedInUser?.canViewReports == true
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
                                val authUser = viewModel.usersList.find { it.pin == unlockPin && it.isEnabled && (it.isSuperAdmin || it.canViewReports) }
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
            containerColor = Color(0xFF09090B),
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
                                    .background(Color(0xFF1E1E24)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QueryStats,
                                    contentDescription = "Reports",
                                    tint = Color(0xFFBD00FF),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Financial Reports",
                                    fontWeight = FontWeight.ExtraBold,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White
                                )
                                Text(
                                    text = "Shop Performance & Metrics",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    },
                    actions = {
                        val currentLanguage by viewModel.appLanguage.collectAsState()
                        val isEnglish = currentLanguage == "en"
                        Row(
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF1A1A1E))
                                .border(1.dp, Color(0xFFBD00FF).copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                                .padding(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(if (isEnglish) Color(0xFFFF9F1C) else Color.Transparent)
                                    .clickable { viewModel.setAppLanguage("en") }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "EN",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp,
                                    color = if (isEnglish) Color.Black else Color.Gray
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(if (!isEnglish) Color(0xFFFF9F1C) else Color.Transparent)
                                    .clickable { viewModel.setAppLanguage("ne") }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "नेपाली",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp,
                                    color = if (!isEnglish) Color.Black else Color.Gray
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF09090B)
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Color(0xFF09090B))
            ) {
                // Custom Tab Layout with Purple Underline
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF09090B))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    listOf("Returns", "Sales", "Expenses").forEachIndexed { index, tabTitle ->
                        val isSelected = selectedTabState == index
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null
                                ) {
                                    selectedTabState = index
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = tabTitle,
                                color = if (isSelected) Color.White else Color.Gray,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .height(3.dp)
                                    .fillMaxWidth(0.6f)
                                    .background(
                                        if (isSelected) Color(0xFFBD00FF) else Color.Transparent,
                                        shape = RoundedCornerShape(2.dp)
                                    )
                            )
                        }
                    }
                }

                if (selectedTabState == 1) {
                    // TAB 1: SALES PERFORMANCE & REPORT EXPORT
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
                                        com.example.util.t("overall_sales_summary", viewModel),
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
                                            Text(com.example.util.t("total_revenue", viewModel).uppercase(), fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                            Text("NPR ${com.example.util.tNum(String.format("%.2f", totalRevenue), viewModel)}", fontSize = 22.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.secondary)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(com.example.util.t("sales_profit", viewModel).uppercase(), fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                            Text("NPR ${com.example.util.tNum(String.format("%.2f", totalSalesProfit), viewModel)}", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color(0xFF4CAF50))
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
                                            Text(com.example.util.t("cost_of_goods", viewModel).uppercase(), fontSize = 10.sp, color = Color.Gray)
                                            Text("NPR ${com.example.util.tNum(String.format("%.2f", totalCostOfGoods), viewModel)}", fontWeight = FontWeight.Bold)
                                        }
                                        Column {
                                            Text(com.example.util.t("transactions", viewModel).uppercase(), fontSize = 10.sp, color = Color.Gray)
                                            Text("${com.example.util.tNum(transactionCount, viewModel)}", fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(com.example.util.t("avg_order_value", viewModel).uppercase(), fontSize = 10.sp, color = Color.Gray)
                                            Text("NPR ${com.example.util.tNum(String.format("%.2f", avgOrderValue), viewModel)}", fontWeight = FontWeight.Bold)
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
                                            com.example.util.t("report_export_center", viewModel),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Text(
                                        com.example.util.t("generate_audit_logs_desc", viewModel),
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
                                                val csv = generateSalesCsv(sales, invoiceNumberMap)
                                                com.example.util.ReceiptExporter.saveCsvReportToDownloads(context, csv, "Sales_Ledger")
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.GridOn, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(com.example.util.t("excel_csv", viewModel))
                                        }

                                        Button(
                                            onClick = {
                                                // Export PDF via Android Print Engine
                                                val reportStr = generateSalesSummaryReport(sales, totalRevenue, totalSalesProfit, totalCostOfGoods, transactionCount, shopName, invoiceNumberMap)
                                                com.example.util.ReceiptExporter.printReceipt(context, reportStr, "Store_Sales_Report")
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(com.example.util.t("print_pdf", viewModel))
                                        }
                                    }
                                }
                            }
                        }

                        // History Log Header
                        item {
                            Text(
                                text = com.example.util.t("sales_transaction_log", viewModel),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        item {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                label = { Text(com.example.util.t("search_transactions", viewModel)) },
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
                        } else if (filteredTransactions.isEmpty()) {
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
                            items(filteredTransactions, key = { tx ->
                                when (tx) {
                                    is TransactionItem.SaleTx -> "sale_${tx.sale.id}"
                                    is TransactionItem.ReturnTx -> "return_${tx.ret.id}"
                                }
                            }) { tx ->
                                when (tx) {
                                    is TransactionItem.SaleTx -> {
                                        val sale = tx.sale
                                        val sdf = remember { SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.getDefault()) }
                                        val dateStr = sdf.format(Date(sale.timestamp))
                                        val matchedItems = remember(sale.id, saleItems) {
                                            saleItems.filter { it.saleId == sale.id }
                                        }
                                        val itemsSummary = remember(matchedItems) {
                                            matchedItems.joinToString { "${it.productName} x${it.quantity}" }
                                        }
                                        val invoiceNo = invoiceNumberMap[sale.id] ?: (1000 + sale.id)

                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectedSaleId = sale.id
                                                    selectedSaleReceipt = buildFormattedReceiptForPastSale(sale, matchedItems, shopName, panNumber, invoiceNo)
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
                                                            text = "Sale #$invoiceNo",
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
                                                        text = "NPR ${com.example.util.tNum(String.format("%.2f", sale.totalAmount), viewModel)}",
                                                        fontWeight = FontWeight.Black,
                                                        color = MaterialTheme.colorScheme.secondary,
                                                        fontSize = 16.sp
                                                    )
                                                    Text(
                                                        text = "Profit: +NPR ${com.example.util.tNum(String.format("%.2f", sale.totalProfit), viewModel)}",
                                                        fontSize = 11.sp,
                                                        color = Color(0xFF4CAF50),
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    is TransactionItem.ReturnTx -> {
                                        val ret = tx.ret
                                        val sdf = remember { SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.getDefault()) }
                                        val dateStr = sdf.format(Date(ret.timestamp))

                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(16.dp),
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                Color(0xFFF28B82).copy(alpha = 0.22f)
                                            ),
                                            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(
                                                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                                            colors = listOf(
                                                                Color(0xFF1C1314),
                                                                Color(0xFF120E0F)
                                                            )
                                                        )
                                                    )
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(IntrinsicSize.Min)
                                                ) {
                                                    // Left side modern accent bar
                                                    Box(
                                                        modifier = Modifier
                                                            .width(6.dp)
                                                            .fillMaxHeight()
                                                            .background(
                                                                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                                                    colors = listOf(
                                                                        Color(0xFFFF453A),
                                                                        Color(0xFFE57373)
                                                                    )
                                                                )
                                                            )
                                                    )

                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(16.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.Top
                                                    ) {
                                                        Column(
                                                            modifier = Modifier.weight(1f),
                                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            // Header row: status badge and ID
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                            ) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .background(Color(0xFFFF453A).copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                                                ) {
                                                                    Row(
                                                                        verticalAlignment = Alignment.CenterVertically,
                                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                                    ) {
                                                                        Icon(
                                                                            imageVector = Icons.Default.Undo,
                                                                            contentDescription = "Returned",
                                                                            tint = Color(0xFFF28B82),
                                                                            modifier = Modifier.size(12.dp)
                                                                        )
                                                                        Text(
                                                                            text = "RETURNED",
                                                                            fontSize = 9.sp,
                                                                            fontWeight = FontWeight.Black,
                                                                            color = Color(0xFFF28B82),
                                                                            letterSpacing = 0.5.sp
                                                                        )
                                                                    }
                                                                }

                                                                val displayId = if (ret.saleId != null && ret.saleId > 0) {
                                                                    val invoiceNo = if (ret.saleId >= 1001) ret.saleId else ret.saleId + 1000
                                                                    "Invoice #$invoiceNo"
                                                                } else {
                                                                    "Direct Return"
                                                                }
                                                                Text(
                                                                    text = displayId,
                                                                    fontWeight = FontWeight.SemiBold,
                                                                    style = MaterialTheme.typography.labelMedium,
                                                                    color = Color.Gray
                                                                )
                                                            }

                                                            // Product Name
                                                            Text(
                                                                text = ret.productName,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 15.sp,
                                                                color = Color.White,
                                                                lineHeight = 20.sp
                                                            )

                                                            // Qty & Barcode badges
                                                            Row(
                                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                                                                        .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                                                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                                                ) {
                                                                    Text(
                                                                        text = "Qty: ${ret.quantity}",
                                                                        fontSize = 11.sp,
                                                                        fontWeight = FontWeight.SemiBold,
                                                                        color = Color.LightGray
                                                                    )
                                                                }

                                                                if (ret.barcode.isNotBlank()) {
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(6.dp))
                                                                            .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                                                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                                                    ) {
                                                                        Text(
                                                                            text = "Code: ${ret.barcode}",
                                                                            fontSize = 11.sp,
                                                                            fontWeight = FontWeight.Normal,
                                                                            color = Color.Gray
                                                                        )
                                                                    }
                                                                }
                                                            }

                                                            // Footer metadata: date & handler
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                            ) {
                                                                Text(
                                                                    text = dateStr,
                                                                    fontSize = 11.sp,
                                                                    color = Color.Gray
                                                                )

                                                                Row(
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.Default.Person,
                                                                        contentDescription = null,
                                                                        tint = Color.Gray,
                                                                        modifier = Modifier.size(11.dp)
                                                                    )
                                                                    Text(
                                                                        text = ret.returnedBy,
                                                                        fontSize = 11.sp,
                                                                        fontWeight = FontWeight.Medium,
                                                                        color = Color.LightGray
                                                                    )
                                                                }
                                                            }
                                                        }

                                                        Spacer(modifier = Modifier.width(12.dp))

                                                        // Right side: Refund amount & Reason pill
                                                        Column(
                                                            horizontalAlignment = Alignment.End,
                                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            Column(horizontalAlignment = Alignment.End) {
                                                                Text(
                                                                    text = "REFUNDED",
                                                                    fontSize = 9.sp,
                                                                    fontWeight = FontWeight.SemiBold,
                                                                    color = Color.Gray,
                                                                    letterSpacing = 0.5.sp
                                                                )
                                                                Text(
                                                                    text = "-NPR ${com.example.util.tNum(String.format("%.2f", ret.refundAmount), viewModel)}",
                                                                    fontWeight = FontWeight.Black,
                                                                    color = Color(0xFFFF453A),
                                                                    fontSize = 16.sp
                                                                )
                                                            }

                                                            Box(
                                                                modifier = Modifier
                                                                    .background(Color(0xFFFF453A).copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                                                    .border(1.dp, Color(0xFFFF453A).copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                                            ) {
                                                                Text(
                                                                    text = ret.reason,
                                                                    fontSize = 10.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = Color(0xFFF28B82)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item { Spacer(modifier = Modifier.height(40.dp)) }
                    }
                } else if (selectedTabState == 0) {
                    // TAB 0: PRODUCT RETURNS LEDGER
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item { Spacer(modifier = Modifier.height(4.dp)) }

                        // Summary Analytics Cards (Split Layout with glowing purple & orange highlights)
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Total Returns Card
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .border(1.dp, Color(0xFFBD00FF).copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131316))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.Start
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Undo,
                                            contentDescription = null,
                                            tint = Color(0xFFBD00FF),
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "TOTAL RETURNS",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.LightGray
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "${returns.size} Items",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White
                                        )
                                    }
                                }

                                // Total Refunded Card
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .border(1.dp, Color(0xFFFF9F1C).copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131316))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.Start
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Payments,
                                            contentDescription = null,
                                            tint = Color(0xFFFF9F1C),
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "TOTAL REFUNDED",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.LightGray
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "NPR ${com.example.util.tNum(String.format("%.2f", totalRefundedAmount), viewModel)}",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFFFF9F1C)
                                        )
                                    }
                                }
                            }
                        }

                        // Process Customer Return Action Button
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Button(
                                    onClick = { showAddReturnDialog = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFFF9F1C),
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .wrapContentWidth()
                                        .border(1.dp, Color(0xFFFF9F1C).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Undo,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = Color.Black
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Process Customer Return",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }

                        // Search Field for Returns
                        item {
                            OutlinedTextField(
                                value = returnSearchQuery,
                                onValueChange = { returnSearchQuery = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, Color(0xFFBD00FF).copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                                placeholder = {
                                    Text(
                                        text = "Search returns by product name or barcode…",
                                        color = Color.Gray,
                                        fontSize = 14.sp
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search icon",
                                        tint = Color(0xFFFF9F1C)
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFF131316),
                                    unfocusedContainerColor = Color(0xFF131316),
                                    focusedBorderColor = Color(0xFFBD00FF),
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.LightGray
                                )
                            )
                        }

                        val filteredReturns = returns.filter {
                            returnSearchQuery.isBlank() ||
                            it.productName.lowercase().contains(returnSearchQuery.lowercase()) ||
                            it.barcode.contains(returnSearchQuery)
                        }

                        if (filteredReturns.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.Undo,
                                            contentDescription = null,
                                            tint = Color.Gray.copy(alpha = 0.4f),
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "No return records found",
                                            color = Color.Gray,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        } else {
                            items(filteredReturns, key = { it.id }) { ret ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, Color(0xFFFF453A).copy(alpha = 0.22f), RoundedCornerShape(16.dp)),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(
                                                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                                    colors = listOf(
                                                        Color(0xFF1C1314),
                                                        Color(0xFF120E0F)
                                                    )
                                                )
                                            )
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            // Header Row: Product Name & Quantity Badge
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = ret.productName,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 16.sp,
                                                        color = Color.White,
                                                        lineHeight = 22.sp
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = "Barcode: ${ret.barcode.ifBlank { "N/A" }}",
                                                        fontSize = 12.sp,
                                                        color = Color.Gray
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Surface(
                                                    color = Color(0xFFFF453A).copy(alpha = 0.15f),
                                                    shape = RoundedCornerShape(8.dp),
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF453A).copy(alpha = 0.25f))
                                                ) {
                                                    Text(
                                                        text = "Qty: ${ret.quantity}",
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFFF28B82),
                                                        fontSize = 12.sp,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                    )
                                                }
                                            }

                                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                                            // Grid/Details Column
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        modifier = Modifier.weight(1f)
                                                     ) {
                                                         Icon(
                                                             imageVector = Icons.Default.Info,
                                                             contentDescription = null,
                                                             tint = Color(0xFFFF453A),
                                                             modifier = Modifier.size(14.dp)
                                                         )
                                                         Text(
                                                             text = "Reason: ${ret.reason}",
                                                             fontSize = 12.sp,
                                                             fontWeight = FontWeight.Medium,
                                                             color = Color.LightGray
                                                         )
                                                     }
                                                     Row(
                                                         verticalAlignment = Alignment.CenterVertically,
                                                         horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                     ) {
                                                         Icon(
                                                             imageVector = Icons.Default.Person,
                                                             contentDescription = null,
                                                             tint = Color.Gray,
                                                             modifier = Modifier.size(12.dp)
                                                         )
                                                         Text(
                                                             text = ret.returnedBy,
                                                             fontSize = 12.sp,
                                                             color = Color.LightGray,
                                                             fontWeight = FontWeight.Medium
                                                         )
                                                     }
                                                 }

                                                 Row(
                                                     modifier = Modifier.fillMaxWidth(),
                                                     horizontalArrangement = Arrangement.SpaceBetween,
                                                     verticalAlignment = Alignment.CenterVertically
                                                 ) {
                                                     val displayId = if (ret.saleId != null && ret.saleId > 0) {
                                                         val actualNo = if (ret.saleId >= 1001) ret.saleId else ret.saleId + 1000
                                                         "Invoice #$actualNo"
                                                     } else {
                                                         "Direct Return"
                                                     }
                                                     Text(
                                                         text = "Sale Ref: $displayId",
                                                         fontSize = 12.sp,
                                                         color = Color.Gray,
                                                         fontWeight = FontWeight.Normal
                                                     )
                                                     val sdf = remember { SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.getDefault()) }
                                                     Text(
                                                         text = sdf.format(Date(ret.timestamp)),
                                                         fontSize = 12.sp,
                                                         color = Color.Gray
                                                     )
                                                 }
                                             }

                                             HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                                             // Refund Amount Row
                                             Row(
                                                 modifier = Modifier.fillMaxWidth(),
                                                 horizontalArrangement = Arrangement.SpaceBetween,
                                                 verticalAlignment = Alignment.CenterVertically
                                             ) {
                                                 Text(
                                                     text = "REFUNDED AMOUNT",
                                                     fontSize = 11.sp,
                                                     fontWeight = FontWeight.Bold,
                                                     color = Color.Gray,
                                                     letterSpacing = 0.5.sp
                                                 )
                                                 Text(
                                                     text = "-NPR ${com.example.util.tNum(String.format("%.2f", ret.refundAmount), viewModel)}",
                                                     fontWeight = FontWeight.Black,
                                                     color = Color(0xFFFF453A),
                                                     fontSize = 16.sp
                                                 )
                                             }
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
                                        text = com.example.util.t("expense_bill_analytics", viewModel),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    // Net Income Highlight Box (Full Width)
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (netProfit >= 0) Color(0x154CAF50) else Color(0x15FF5252)
                                        ),
                                        shape = RoundedCornerShape(16.dp),
                                        border = androidx.compose.foundation.BorderStroke(
                                            width = 1.dp,
                                            color = if (netProfit >= 0) Color(0x334CAF50) else Color(0x33FF5252)
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = com.example.util.t("net_income", viewModel).uppercase(),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (netProfit >= 0) Color(0xFFA8E6CF) else Color(0xFFFFD3B6),
                                                letterSpacing = 1.sp
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            val incomeColor = if (netProfit >= 0) Color(0xFF4CAF50) else Color(0xFFFF5252)
                                            Text(
                                                text = "NPR ${com.example.util.tNum(String.format("%.2f", netProfit), viewModel)}",
                                                fontSize = 24.sp,
                                                fontWeight = FontWeight.Black,
                                                color = incomeColor
                                            )
                                        }
                                    }

                                    // Sub-metrics Side-by-Side (2 Columns instead of 3 prevents squishing!)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // Sales Margins
                                        Card(
                                            modifier = Modifier.weight(1f),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                            ),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = com.example.util.t("sales_margins", viewModel).uppercase(),
                                                    fontSize = 10.sp,
                                                    color = Color.Gray,
                                                    fontWeight = FontWeight.Bold,
                                                    letterSpacing = 0.5.sp
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "NPR ${com.example.util.tNum(String.format("%.2f", totalSalesProfit), viewModel)}",
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF4CAF50)
                                                )
                                            }
                                        }

                                        // Total Expenses
                                        Card(
                                            modifier = Modifier.weight(1f),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                            ),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = com.example.util.t("total_expenses", viewModel).uppercase(),
                                                    fontSize = 10.sp,
                                                    color = Color.Gray,
                                                    fontWeight = FontWeight.Bold,
                                                    letterSpacing = 0.5.sp
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "NPR ${com.example.util.tNum(String.format("%.2f", totalExpenses), viewModel)}",
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFFF5252)
                                                )
                                            }
                                        }
                                    }

                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )

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
                                            text = com.example.util.t("net_income_calc_desc", viewModel),
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
                            items(expenses, key = { it.id }) { expense ->
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
                                                text = "-NPR ${com.example.util.tNum(String.format("%.2f", expense.amount), viewModel)}",
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
            val selectedInvoiceNo = selectedSaleId?.let { invoiceNumberMap[it] } ?: 0
            AlertDialog(
                onDismissRequest = {
                    selectedSaleReceipt = null
                    selectedSaleId = null
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = "Receipt", tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Invoice No: #$selectedInvoiceNo", fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Receipt Copy",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .background(Color(0xFF1E1E24), RoundedCornerShape(8.dp))
                                .border(1.dp, Color.DarkGray, RoundedCornerShape(8.dp))
                        ) {
                            LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                                item {
                                    Text(
                                        text = selectedSaleReceipt ?: "",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                        
                        val matchedItemsForSelectedSale = remember(selectedSaleId, saleItems) {
                            saleItems.filter { it.saleId == selectedSaleId }
                        }
                        
                        if (matchedItemsForSelectedSale.isNotEmpty()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Text(
                                text = "Return & Restock Specific Item Only",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF9F1C)
                            )
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1E)),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF9F1C).copy(alpha = 0.3f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    matchedItemsForSelectedSale.forEach { item ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp, horizontal = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = item.productName,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = Color.White
                                                )
                                                Text(
                                                    text = "Qty Sold: ${item.quantity} | Price: NPR ${item.sellingPrice}",
                                                    fontSize = 11.sp,
                                                    color = Color.LightGray
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Button(
                                                onClick = {
                                                    val matchedProd = products.find { it.id == item.productId } ?: com.example.data.Product(
                                                        id = item.productId,
                                                        name = item.productName,
                                                        barcode = item.barcode,
                                                        category = "Stationery",
                                                        costPrice = item.costPrice,
                                                        sellingPrice = item.sellingPrice,
                                                        stockQuantity = 0
                                                    )
                                                    selectedProductForReturn = matchedProd
                                                    returnSaleIdInput = selectedInvoiceNo.toString()
                                                    val foundSale = sales.find { it.id == selectedSaleId }
                                                    returnPaymentMode = foundSale?.paymentMode ?: "Cash"
                                                    returnQuantity = 1
                                                    returnRefundAmount = item.sellingPrice.toString()
                                                    
                                                    // Close receipt and open return dialog
                                                    selectedSaleReceipt = null
                                                    selectedSaleId = null
                                                    showAddReturnDialog = true
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Color(0xFFFF9F1C),
                                                    contentColor = Color.Black
                                                ),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Undo,
                                                    contentDescription = "Return",
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Return Item", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.5f))
                                    }
                                }
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
                                    putExtra(Intent.EXTRA_SUBJECT, "$shopName - Sales Receipt #$selectedInvoiceNo")
                                    putExtra(Intent.EXTRA_TEXT, textToShare)
                                }
                                try {
                                    context.startActivity(Intent.createChooser(emailIntent, "Send Email..."))
                                } catch (e: Exception) {
                                    val fallbackIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, textToShare)
                                        putExtra(Intent.EXTRA_SUBJECT, "$shopName - Sales Receipt #$selectedInvoiceNo")
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
                                com.example.util.ReceiptExporter.printReceipt(context, textToShare, "Receipt_$selectedInvoiceNo")
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

        // --- Process Product Return Dialog ---
        if (showAddReturnDialog) {
            val enteredBillNo = returnSaleIdInput.toIntOrNull()
            val enteredSaleId = enteredBillNo?.let { billToSaleIdMap[it] }
            val enteredSale = enteredSaleId?.let { sId -> sales.find { it.id == sId } }
            val enteredSaleItem = enteredSaleId?.let { sId -> 
                saleItems.find { it.saleId == sId && it.productId == selectedProductForReturn?.id }
            }

            val matchingSalesForProduct = remember(selectedProductForReturn, saleItems) {
                if (selectedProductForReturn != null) {
                    saleItems.filter { 
                        it.productId == selectedProductForReturn!!.id || 
                        (selectedProductForReturn!!.barcode.isNotBlank() && it.barcode == selectedProductForReturn!!.barcode)
                    }
                } else {
                    emptyList()
                }
            }
            val isProductNeverSold = selectedProductForReturn != null && matchingSalesForProduct.isEmpty()

            var validationError: String? = null
            if (selectedProductForReturn != null) {
                if (enteredBillNo != null) {
                    if (enteredSale == null) {
                        validationError = "Invoice #$enteredBillNo does not exist."
                    } else if (enteredSaleItem == null) {
                        validationError = "This product was not sold in Invoice #$enteredBillNo."
                    } else {
                        // Calculate already returned quantity for this product in this sale
                        val alreadyReturnedQty = returns.filter { it.saleId == enteredSaleId && it.productId == selectedProductForReturn!!.id }.sumOf { it.quantity }
                        val remainingAvailableQty = (enteredSaleItem.quantity - alreadyReturnedQty).coerceAtLeast(0)

                        if (remainingAvailableQty <= 0) {
                            validationError = "This item has already been fully returned for Invoice #$enteredBillNo (originally purchased ${enteredSaleItem.quantity}, already returned $alreadyReturnedQty)."
                        } else if (returnQuantity > remainingAvailableQty) {
                            validationError = "Cannot return $returnQuantity units. Only $remainingAvailableQty units remaining for return in Invoice #$enteredBillNo (originally purchased ${enteredSaleItem.quantity}, already returned $alreadyReturnedQty)."
                        } else {
                            val refund = returnRefundAmount.toDoubleOrNull() ?: 0.0
                            val maxRefund = returnQuantity * enteredSaleItem.sellingPrice
                            if (refund > maxRefund) {
                                validationError = "Refund amount cannot exceed NPR $maxRefund (unit price NPR ${enteredSaleItem.sellingPrice} x quantity $returnQuantity)."
                            }
                        }
                    }
                } else if (isProductNeverSold && !allowUnsoldReturnOverride) {
                    validationError = "This product has not been sold in any past transaction. Returns are restricted for unsold items."
                }
            }
            
            AlertDialog(
                onDismissRequest = { 
                    showAddReturnDialog = false 
                    selectedProductForReturn = null
                    allowUnsoldReturnOverride = false
                },
                containerColor = Color(0xFF121214),
                title = {
                    Column {
                        Text(
                            text = "Process Product Return",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF9F1C),
                            fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "1. Select Product to Return",
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF00E5FF),
                            fontSize = 13.sp
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = productSearchQuery,
                            onValueChange = { productSearchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                            placeholder = { Text("Search product name, barcode, or bill number...", color = Color.Gray) },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF00E5FF)) },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF1A1A1E),
                                unfocusedContainerColor = Color(0xFF121214),
                                focusedBorderColor = Color(0xFF00E5FF),
                                unfocusedBorderColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.LightGray
                            )
                        )

                        val searchedBillNo = productSearchQuery.toIntOrNull()
                        val searchedSaleId = searchedBillNo?.let { billToSaleIdMap[it] }
                        val searchedSale = searchedSaleId?.let { sId -> sales.find { it.id == sId } }
                        val searchedSaleItems = searchedSaleId?.let { sId -> saleItems.filter { it.saleId == sId } } ?: emptyList()

                        val isSearchingBill = searchedSale != null && searchedSaleItems.isNotEmpty()

                        val filteredProducts = remember(products, productSearchQuery) {
                            if (productSearchQuery.isBlank()) emptyList() else {
                                products.filter { 
                                    it.name.lowercase().contains(productSearchQuery.lowercase()) || 
                                    it.barcode.contains(productSearchQuery) 
                                }.take(5)
                            }
                        }

                        if (selectedProductForReturn != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = selectedProductForReturn!!.name,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Selling Price: NPR ${selectedProductForReturn!!.sellingPrice}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    TextButton(
                                        onClick = { 
                                            selectedProductForReturn = null 
                                            allowUnsoldReturnOverride = false
                                        },
                                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Text("Change")
                                    }
                                }
                            }
                        } else {
                            if (isSearchingBill) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.15f))
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(
                                            text = "Items in Invoice #$searchedBillNo",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        searchedSaleItems.forEach { item ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        val matchedProd = products.find { it.id == item.productId } ?: com.example.data.Product(
                                                            id = item.productId,
                                                            name = item.productName,
                                                            barcode = item.barcode,
                                                            category = "Stationery",
                                                            costPrice = item.costPrice,
                                                            sellingPrice = item.sellingPrice,
                                                            stockQuantity = 0
                                                        )
                                                        selectedProductForReturn = matchedProd
                                                        returnSaleIdInput = searchedBillNo.toString()
                                                        returnPaymentMode = searchedSale.paymentMode
                                                        returnQuantity = 1
                                                        returnRefundAmount = item.sellingPrice.toString()
                                                        productSearchQuery = ""
                                                    }
                                                    .padding(12.dp),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(item.productName, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                                    Text("Qty Purchased: ${item.quantity} | Paid: NPR ${item.sellingPrice}", fontSize = 11.sp, color = Color.Gray)
                                                }
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = "Select",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                        }
                                    }
                                }
                            }

                            if (filteredProducts.isNotEmpty()) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                                    )
                                ) {
                                    Column {
                                        filteredProducts.forEach { prod ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        selectedProductForReturn = prod
                                                        returnRefundAmount = prod.sellingPrice.toString()
                                                        productSearchQuery = ""
                                                    }
                                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = prod.name,
                                                        fontWeight = FontWeight.SemiBold,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    if (prod.barcode.isNotBlank()) {
                                                        Text(
                                                            text = "Barcode: ${prod.barcode}",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                            fontSize = 11.sp,
                                                            modifier = Modifier.padding(top = 2.dp)
                                                        )
                                                    }
                                                }
                                                Surface(
                                                    color = MaterialTheme.colorScheme.primaryContainer,
                                                    shape = RoundedCornerShape(8.dp),
                                                    tonalElevation = 1.dp
                                                ) {
                                                    Text(
                                                        text = "NPR ${prod.sellingPrice}",
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                        }
                                    }
                                }
                            } else if (productSearchQuery.isNotBlank() && !isSearchingBill) {
                                Text(
                                    text = com.example.util.t("no_matching_products_bills", viewModel),
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }

                        if (selectedProductForReturn != null) {
                            Text(
                                text = com.example.util.t("qty_refund_details", viewModel),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.secondary,
                                letterSpacing = 0.5.sp
                            )
                            
                             Row(
                                 modifier = Modifier.fillMaxWidth(),
                                 horizontalArrangement = Arrangement.SpaceBetween,
                                 verticalAlignment = Alignment.CenterVertically
                             ) {
                                 Text("Quantity: ")
                                 Row(verticalAlignment = Alignment.CenterVertically) {
                                     IconButton(onClick = { 
                                         if (returnQuantity > 1) {
                                             returnQuantity--
                                             // auto calculate default refund amount based on originally paid unit price if verified, otherwise product sellingPrice
                                             val unitPrice = enteredSaleItem?.sellingPrice ?: selectedProductForReturn!!.sellingPrice
                                             returnRefundAmount = (unitPrice * returnQuantity).toString()
                                         }
                                     }) {
                                         Icon(Icons.Default.Remove, contentDescription = "Decrease")
                                     }
                                     Text(
                                         text = returnQuantity.toString(),
                                         fontWeight = FontWeight.Bold,
                                         modifier = Modifier.padding(horizontal = 8.dp)
                                     )
                                     IconButton(onClick = { 
                                         val alreadyReturnedQty = if (enteredSaleId != null && selectedProductForReturn != null) {
                                             returns.filter { it.saleId == enteredSaleId && it.productId == selectedProductForReturn!!.id }.sumOf { it.quantity }
                                         } else 0
                                         val remainingAvailableQty = if (enteredSaleItem != null) {
                                             (enteredSaleItem.quantity - alreadyReturnedQty).coerceAtLeast(0)
                                         } else Int.MAX_VALUE

                                         if (returnQuantity < remainingAvailableQty) {
                                             returnQuantity++
                                             val unitPrice = enteredSaleItem?.sellingPrice ?: selectedProductForReturn!!.sellingPrice
                                             returnRefundAmount = (unitPrice * returnQuantity).toString()
                                         }
                                     }) {
                                         Icon(Icons.Default.Add, contentDescription = "Increase")
                                     }
                                 }
                             }

                            OutlinedTextField(
                                value = returnRefundAmount,
                                onValueChange = { returnRefundAmount = it },
                                label = { Text("Refund Amount (NPR)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            OutlinedTextField(
                                value = returnReason,
                                onValueChange = { returnReason = it },
                                label = { Text("Reason for Return") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            OutlinedTextField(
                                value = returnSaleIdInput,
                                onValueChange = { returnSaleIdInput = it },
                                label = { Text("Original Sale Invoice ID (Optional)") },
                                placeholder = { Text("e.g. 1005") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            // If a bill number was entered, show verification status card
                            if (enteredBillNo != null) {
                                when {
                                    enteredSaleItem != null -> {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(
                                                containerColor = Color(0xFFE8F5E9)
                                            ),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Verified",
                                                    tint = Color(0xFF2E7D32)
                                                )
                                                Column {
                                                    Text(
                                                        text = "Verified from Sales Ledger",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp,
                                                        color = Color(0xFF2E7D32)
                                                    )
                                                    Text(
                                                        text = "Customer bought ${enteredSaleItem.quantity} unit(s) of this item at NPR ${enteredSaleItem.sellingPrice} each.",
                                                        fontSize = 11.sp,
                                                        color = Color(0xFF1B5E20)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    enteredSale != null -> {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(
                                                containerColor = Color(0xFFFFEBEE)
                                            ),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Error,
                                                    contentDescription = "Not Found",
                                                    tint = Color(0xFFC62828)
                                                )
                                                Column {
                                                    Text(
                                                        text = "Verification Failed",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp,
                                                        color = Color(0xFFC62828)
                                                    )
                                                    Text(
                                                        text = "Invoice #$enteredBillNo exists, but does not contain this product.",
                                                        fontSize = 11.sp,
                                                        color = Color(0xFFB71C1C)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    else -> {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(
                                                containerColor = Color(0xFFFFFDE7)
                                            ),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Warning,
                                                    contentDescription = "Invalid Bill No",
                                                    tint = Color(0xFFF57F17)
                                                )
                                                Column {
                                                    Text(
                                                        text = "Bill Not Found",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp,
                                                        color = Color(0xFFF57F17)
                                                    )
                                                    Text(
                                                        text = "Invoice #$enteredBillNo was not found in our system.",
                                                        fontSize = 11.sp,
                                                        color = Color(0xFFE65100)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                if (isProductNeverSold) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = Color(0xFFFFF3CD)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFC107))
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Warning,
                                                    contentDescription = "Warning",
                                                    tint = Color(0xFF856404)
                                                )
                                                Text(
                                                    text = "WARNING: Product Never Sold",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = Color(0xFF856404)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "This product has never been sold in any past transactions (checked by name/barcode). Returns are only permitted for sold items.",
                                                fontSize = 12.sp,
                                                color = Color(0xFF856404)
                                            )
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { allowUnsoldReturnOverride = !allowUnsoldReturnOverride }
                                            ) {
                                                Checkbox(
                                                    checked = allowUnsoldReturnOverride,
                                                    onCheckedChange = { allowUnsoldReturnOverride = it },
                                                    colors = CheckboxDefaults.colors(
                                                        checkedColor = Color(0xFFFF9F1C),
                                                        uncheckedColor = Color.Gray
                                                    )
                                                )
                                                Text(
                                                    text = "Override warning and process refund anyway",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                } else if (selectedProductForReturn != null) {
                                    val uniqueSaleIds = matchingSalesForProduct.map { it.saleId }.distinct()
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = Color(0xFFE3F2FD)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF90CAF9))
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Info,
                                                    contentDescription = "Info",
                                                    tint = Color(0xFF1565C0)
                                                )
                                                Text(
                                                    text = "Sold in Past Invoices",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = Color(0xFF1565C0)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "This product was sold in the following past invoices. Tap one to automatically link it and load pricing:",
                                                fontSize = 12.sp,
                                                color = Color(0xFF0D47A1)
                                            )
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                uniqueSaleIds.forEach { sId ->
                                                    val invoiceNo = invoiceNumberMap[sId] ?: (1001 + sId)
                                                    val matchingItem = matchingSalesForProduct.first { it.saleId == sId }
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(Color(0xFF1565C0))
                                                            .clickable {
                                                                returnSaleIdInput = invoiceNo.toString()
                                                                returnPaymentMode = sales.find { it.id == sId }?.paymentMode ?: "Cash"
                                                                returnRefundAmount = (matchingItem.sellingPrice * returnQuantity).toString()
                                                            }
                                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                                    ) {
                                                        Text(
                                                            text = "Invoice #$invoiceNo (NPR ${matchingItem.sellingPrice})",
                                                            color = Color.White,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (validationError != null) {
                                Text(
                                    text = validationError,
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                                )
                            }

                            Text(com.example.util.t("original_payment_mode", viewModel), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("Cash", "QR Payment", "Card").forEach { mode ->
                                    val selected = returnPaymentMode == mode
                                    ElevatedFilterChip(
                                        selected = selected,
                                        onClick = { returnPaymentMode = mode },
                                        label = { Text(mode) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            if (returnPaymentMode == "Cash") {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = "Guideline",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                        Column {
                                            Text(
                                                "CASH REFUND INSTRUCTION",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                            Text(
                                                "Customer originally purchased via CASH. Please refund NPR ${returnRefundAmount.ifBlank { "0.00" }} in physical cash to the customer from the cash drawer. Note down this payout in your physical log.",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                    }
                                }
                            } else {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = "Guideline",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Column {
                                            Text(
                                                "ELECTRONIC REFUND INSTRUCTION",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                "Original payment mode was $returnPaymentMode. Please process the refund or reversal through the QR gateway/merchant portal or card terminal.",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val prod = selectedProductForReturn
                            if (prod != null) {
                                val refund = returnRefundAmount.toDoubleOrNull() ?: (prod.sellingPrice * returnQuantity)
                                val originalInvoiceNo = returnSaleIdInput.toIntOrNull()
                                val dbSaleId = originalInvoiceNo?.let { billToSaleIdMap[it] } ?: originalInvoiceNo
                                
                                viewModel.logProductReturn(
                                    productId = prod.id,
                                    productName = prod.name,
                                    barcode = prod.barcode,
                                    quantity = returnQuantity,
                                    refundAmount = refund,
                                    reason = returnReason,
                                    saleId = dbSaleId
                                ) { success ->
                                    if (success) {
                                        showAddReturnDialog = false
                                        selectedProductForReturn = null
                                        returnQuantity = 1
                                        returnRefundAmount = ""
                                        returnReason = "Defective"
                                        returnSaleIdInput = ""
                                        allowUnsoldReturnOverride = false
                                    }
                                }
                            }
                        },
                        enabled = selectedProductForReturn != null && validationError == null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF9F1C),
                            contentColor = Color.Black,
                            disabledContainerColor = Color(0xFFFF9F1C).copy(alpha = 0.3f),
                            disabledContentColor = Color.Gray
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Confirm Return", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { 
                            showAddReturnDialog = false 
                            selectedProductForReturn = null
                            allowUnsoldReturnOverride = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E1035),
                            contentColor = Color(0xFFBD00FF)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel", fontWeight = FontWeight.Medium)
                    }
                }
            )
        }
    }
}

private fun buildFormattedReceiptForPastSale(sale: Sale, items: List<SaleItem>, shopName: String, panNumber: String, invoiceNo: Int): String {
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
    sb.append("Invoice: #$invoiceNo\n")
    sb.append("-------------------------------\n")
    sb.append(String.format("%-18s %3s %8s\n", "Item Description", "Qty", "Price"))
    sb.append("-------------------------------\n")
    for (item in items) {
        val nameTrunc = if (item.productName.length > 17) item.productName.substring(0, 15) + ".." else item.productName
        sb.append(String.format("%-18s %3d %8.2f\n", nameTrunc, item.quantity, item.sellingPrice * item.quantity))
        if (item.barcode.isNotBlank()) {
            sb.append(" [BC: ${item.barcode}]\n")
        }
    }
    sb.append("-------------------------------\n")
    sb.append(String.format("%-22s %8.2f\n", "TOTAL AMOUNT:", sale.totalAmount))
    sb.append("Payment Mode: ${sale.paymentMode}\n")
    sb.append("===============================\n")
    sb.append("     Thank you for your visit! \n")
    sb.append("===============================\n")
    return sb.toString()
}

private fun generateSalesCsv(sales: List<Sale>, invoiceNumberMap: Map<Int, Int>): String {
    val sb = StringBuilder()
    sb.append("Invoice ID,Date,Payment Mode,Total Amount (NPR),Net Profit (NPR)\n")
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    for (sale in sales) {
        val dateStr = sdf.format(Date(sale.timestamp))
        val invoiceNo = invoiceNumberMap[sale.id] ?: (1000 + sale.id)
        sb.append("$invoiceNo,$dateStr,${sale.paymentMode},${sale.totalAmount},${sale.totalProfit}\n")
    }
    return sb.toString()
}

private fun generateSalesSummaryReport(
    sales: List<Sale>,
    totalRevenue: Double,
    totalProfit: Double,
    totalCost: Double,
    transactionCount: Int,
    shopName: String,
    invoiceNumberMap: Map<Int, Int>
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
        val invoiceNo = invoiceNumberMap[sale.id] ?: (1000 + sale.id)
        sb.append(String.format("#%-7d %-12s %10.2f\n", invoiceNo, sale.paymentMode, sale.totalAmount))
    }
    sb.append("----------------------------------------\n")
    sb.append("      Thank you for choosing POS!       \n")
    sb.append("========================================\n")
    return sb.toString()
}

sealed class TransactionItem {
    abstract val timestamp: Long
    abstract val id: Int
    
    data class SaleTx(val sale: Sale) : TransactionItem() {
        override val timestamp: Long get() = sale.timestamp
        override val id: Int get() = sale.id
    }
    
    data class ReturnTx(val ret: ProductReturn) : TransactionItem() {
        override val timestamp: Long get() = ret.timestamp
        override val id: Int get() = ret.id
    }
}
