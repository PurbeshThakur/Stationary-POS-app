package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.data.Product
import com.example.ui.InventoryViewModel
import com.example.util.BarcodeAnalyzer
import kotlinx.coroutines.delay
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    viewModel: InventoryViewModel,
    onNavigateToInventory: () -> Unit
) {
    val context = LocalContext.current
    val products by viewModel.allProducts.collectAsState()
    val cart by viewModel.cart.collectAsState()
    val lowStockList by viewModel.lowStockProducts.collectAsState()
    val checkoutReceipt by viewModel.checkoutReceipt.collectAsState()
    val shopName by viewModel.shopName.collectAsState()
    val customers by viewModel.customersListState.collectAsState()
    val currentRole by viewModel.currentUserRole.collectAsState()
    val loggedInUser by viewModel.loggedInUser.collectAsState()

    var showCameraScanner by remember { mutableStateOf(false) }
    var manualBarcode by remember { mutableStateOf("") }
    var scanMessage by remember { mutableStateOf<String?>(null) }
    var scanSuccess by remember { mutableStateOf(true) }

    // Checkout Details state
    var showCheckoutDialog by remember { mutableStateOf(false) }
    var selectedPaymentMode by remember { mutableStateOf("Cash") }
    var customerPhone by remember { mutableStateOf("") }
    var paymentTransactionId by remember { mutableStateOf("") }
    var selectedCustomerIdForCredit by remember { mutableStateOf<String?>(null) }
    var customerDropdownExpanded by remember { mutableStateOf(false) }

    // Discount states
    var discountInput by remember { mutableStateOf("") }
    var discountTypeIsPercentage by remember { mutableStateOf(false) } // false = NPR, true = %

    val cartTotal = remember(cart) { cart.entries.sumOf { it.key.sellingPrice * it.value } }
    val cartCost = remember(cart) { cart.entries.sumOf { it.key.costPrice * it.value } }
    val maxDiscount = remember(cartTotal, cartCost) { (cartTotal - cartCost).coerceAtLeast(0.0) }

    val discountAmount = remember(discountInput, discountTypeIsPercentage, cartTotal, cartCost) {
        val inputVal = discountInput.toDoubleOrNull() ?: 0.0
        val rawDiscount = if (discountTypeIsPercentage) {
            cartTotal * (inputVal / 100.0)
        } else {
            inputVal
        }
        rawDiscount.coerceIn(0.0, maxDiscount)
    }

    val isDiscountCapped = remember(discountInput, discountTypeIsPercentage, cartTotal, cartCost) {
        val inputVal = discountInput.toDoubleOrNull() ?: 0.0
        val rawDiscount = if (discountTypeIsPercentage) {
            cartTotal * (inputVal / 100.0)
        } else {
            inputVal
        }
        rawDiscount > maxDiscount
    }

    // QR Payment state
    var showQrPaymentDialog by remember { mutableStateOf(false) }
    var simulatedQrPaid by remember { mutableStateOf(false) }
    var simulatedTxId by remember { mutableStateOf("") }

    // Role switcher state
    var showRoleUnlockDialog by remember { mutableStateOf(false) }
    var roleUnlockPin by remember { mutableStateOf("") }
    var roleUnlockError by remember { mutableStateOf(false) }

    // Alert toast for scanning
    LaunchedEffect(scanMessage) {
        if (scanMessage != null) {
            delay(2500)
            scanMessage = null
        }
    }

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
                                .background(Color(0xFFBD00FF).copy(alpha = 0.15f))
                                .border(1.dp, Color(0xFFBD00FF).copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Store,
                                contentDescription = "Purbesh Stationary Logo",
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "PURBESH STATIONARY",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Text(
                                text = com.example.util.t("stationery_shop_inventory", viewModel),
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = Color(0xFF00E5FF)
                            )
                        }
                    }
                },
                actions = {
                    val loggedInUser by viewModel.loggedInUser.collectAsState()
                    var showProfileMenu by remember { mutableStateOf(false) }

                    com.example.util.LanguageToggle(
                        viewModel = viewModel,
                        modifier = Modifier.padding(end = 8.dp)
                    )

                    Box {
                        IconButton(
                            onClick = { showProfileMenu = true },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            val avatarColorVal = loggedInUser?.avatarColor ?: 0xFFBD00FF
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(avatarColorVal).copy(alpha = 0.2f), CircleShape)
                                    .border(1.5.dp, Color(avatarColorVal), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = loggedInUser?.fullName?.trim()?.firstOrNull()?.toString()?.uppercase() ?: "U",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showProfileMenu,
                            onDismissRequest = { showProfileMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(loggedInUser?.fullName ?: "Unknown User", fontWeight = FontWeight.Bold)
                                        Text(
                                            text = currentRole.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                },
                                onClick = {},
                                enabled = false
                            )
                            
                            Divider(modifier = Modifier.padding(vertical = 4.dp))
                            
                            if (currentRole == com.example.ui.UserRole.CASHIER) {
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Default.VerifiedUser, contentDescription = "Elevate", tint = MaterialTheme.colorScheme.primary) },
                                    text = { Text(com.example.util.t("elevate_to_admin", viewModel)) },
                                    onClick = {
                                        showProfileMenu = false
                                        showRoleUnlockDialog = true
                                    }
                                )
                            } else {
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Default.LockOpen, contentDescription = "Demote", tint = MaterialTheme.colorScheme.outline) },
                                    text = { Text(com.example.util.t("switch_to_cashier", viewModel)) },
                                    onClick = {
                                        showProfileMenu = false
                                        viewModel.setUserRole(com.example.ui.UserRole.CASHIER)
                                    }
                                )
                            }

                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Logout, contentDescription = "Log Out", tint = MaterialTheme.colorScheme.error) },
                                text = { Text(com.example.util.t("log_out_switch_profile", viewModel), color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showProfileMenu = false
                                    viewModel.logout()
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF09090B)
                )
            )
        },
        containerColor = Color(0xFF09090B)
    ) { paddingValues ->
        val canPerformSale = loggedInUser?.canPerformSale == true || currentRole == com.example.ui.UserRole.ADMIN

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF09090B))
                .padding(paddingValues)
        ) {
            if (!canPerformSale) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFFF007F).copy(alpha = 0.3f), RoundedCornerShape(24.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF13131A)),
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
                                    .background(Color(0xFFFF007F).copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                                    .border(1.dp, Color(0xFFFF007F), RoundedCornerShape(20.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Lock",
                                    tint = Color(0xFFFF007F),
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            Text(
                                text = com.example.util.t("pos_terminal_restricted", viewModel),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = com.example.util.t("pos_terminal_restricted_desc", viewModel),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )

                            var pinVal by remember { mutableStateOf("") }
                            var pinErr by remember { mutableStateOf(false) }

                            OutlinedTextField(
                                value = pinVal,
                                onValueChange = {
                                    pinVal = it
                                    pinErr = false
                                },
                                label = { Text(com.example.util.t("enter_admin_pin", viewModel), color = Color.White.copy(alpha = 0.6f)) },
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                isError = pinErr,
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFFBD00FF),
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                    focusedLabelColor = Color(0xFFBD00FF)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (pinErr) {
                                Text(
                                    text = com.example.util.t("incorrect_pin", viewModel),
                                    color = Color(0xFFFF007F),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            Button(
                                onClick = {
                                    val authUser = viewModel.usersList.find { it.pin == pinVal && it.isEnabled && (it.role == com.example.ui.UserRole.ADMIN || it.canPerformSale) }
                                    if (authUser != null) {
                                        viewModel.setUserRole(com.example.ui.UserRole.ADMIN)
                                        pinVal = ""
                                    } else {
                                        pinErr = true
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFBD00FF),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.VerifiedUser, contentDescription = "Verify")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(com.example.util.t("elevate_terminal", viewModel))
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }

                // Quick Statistics Cards Row
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Cart item count card
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF13131A).copy(alpha = 0.6f))
                                .border(
                                    width = 1.dp,
                                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                        listOf(Color(0xFF00E5FF).copy(alpha = 0.4f), Color.Transparent)
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .padding(12.dp)
                        ) {
                            Column {
                                Icon(
                                    imageVector = Icons.Default.ShoppingCart,
                                    contentDescription = "Cart Items",
                                    tint = Color(0xFF00E5FF)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = com.example.util.t("cart_items", viewModel),
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${cart.values.sum()}",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 20.sp,
                                    color = Color.White
                                )
                            }
                        }

                        // Low stock alert count card
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF13131A).copy(alpha = 0.6f))
                                .border(
                                    width = 1.dp,
                                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                        listOf(
                                            (if (lowStockList.isNotEmpty()) Color(0xFFFF007F) else Color(0xFFBD00FF)).copy(alpha = 0.4f),
                                            Color.Transparent
                                        )
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable { onNavigateToInventory() }
                                .padding(12.dp)
                        ) {
                            Column {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Low Stock Alert",
                                    tint = if (lowStockList.isNotEmpty()) Color(0xFFFF007F) else Color(0xFFBD00FF)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = com.example.util.t("stock_alert", viewModel),
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${lowStockList.size} Items",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp,
                                    color = Color.White
                                )
                            }
                        }

                        // Cart Value card
                        Box(
                            modifier = Modifier
                                .weight(1.2f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF13131A).copy(alpha = 0.6f))
                                .border(
                                    width = 1.dp,
                                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                        listOf(Color(0xFFBD00FF).copy(alpha = 0.4f), Color.Transparent)
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .padding(12.dp)
                        ) {
                            Column {
                                Icon(
                                    imageVector = Icons.Default.Payments,
                                    contentDescription = "Cart Total",
                                    tint = Color(0xFFBD00FF)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = com.example.util.t("cart_total", viewModel),
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "NPR ${com.example.util.tNum(String.format("%.2f", cartTotal), viewModel)}",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Low stock alarm banner
                if (lowStockList.isNotEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFFFF007F).copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                            color = Color(0xFFFF007F).copy(alpha = 0.15f),
                            onClick = onNavigateToInventory
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Error, contentDescription = "Alert", tint = Color(0xFFFF007F))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = com.example.util.tNum(String.format(com.example.util.t("low_stock_banner_alert", viewModel), lowStockList.size), viewModel),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                // Scanner Interface Section
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF13131A).copy(alpha = 0.5f))
                            .border(
                                width = 1.dp,
                                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                    listOf(Color(0xFFBD00FF).copy(alpha = 0.2f), Color(0xFF00E5FF).copy(alpha = 0.2f))
                                ),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = com.example.util.t("scan_stationery_barcode", viewModel),
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Button(
                                    onClick = { showCameraScanner = !showCameraScanner },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (showCameraScanner) Color(0xFFFF007F) else Color(0xFFBD00FF),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .border(
                                            width = 1.dp,
                                            color = if (showCameraScanner) Color(0xFFFF007F).copy(alpha = 0.5f) else Color(0xFFBD00FF).copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                ) {
                                    Icon(
                                        imageVector = if (showCameraScanner) Icons.Default.Close else Icons.Default.QrCodeScanner,
                                        contentDescription = "Toggle Scan",
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (showCameraScanner) com.example.util.t("close_camera", viewModel) else com.example.util.t("open_camera_scanner", viewModel),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Camera scanner preview
                            AnimatedVisibility(
                                visible = showCameraScanner,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    CameraScannerView(
                                        onBarcodeDetected = { code ->
                                            viewModel.scanBarcode(code) { success, msg ->
                                                scanSuccess = success
                                                scanMessage = msg
                                                if (success) {
                                                    showCameraScanner = false
                                                }
                                            }
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = com.example.util.t("align_product_barcode", viewModel),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.6f)
                                    )
                                }
                            }

                            if (!showCameraScanner) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        OutlinedTextField(
                                            value = manualBarcode,
                                            onValueChange = { manualBarcode = it },
                                            label = { Text(com.example.util.t("enter_barcode_or_name", viewModel)) },
                                            leadingIcon = { Icon(Icons.Default.QrCode, contentDescription = "Barcode", tint = Color(0xFF00E5FF)) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedBorderColor = Color(0xFFBD00FF),
                                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                                focusedLabelColor = Color(0xFFBD00FF),
                                                unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                                                cursorColor = Color(0xFFBD00FF)
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        
                                        val suggestions = remember(manualBarcode, products) {
                                            val query = manualBarcode.trim()
                                            if (query.length >= 2) {
                                                products.filter {
                                                    it.name.contains(query, ignoreCase = true) ||
                                                    it.barcode.contains(query, ignoreCase = true)
                                                }.take(5)
                                            } else {
                                                emptyList()
                                            }
                                        }
                                        
                                        if (suggestions.isNotEmpty()) {
                                            DropdownMenu(
                                                expanded = true,
                                                onDismissRequest = { /* Keep open */ },
                                                properties = androidx.compose.ui.window.PopupProperties(focusable = false),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                suggestions.forEach { prod ->
                                                    DropdownMenuItem(
                                                        text = {
                                                            Column {
                                                                Text(prod.name, fontWeight = FontWeight.Bold)
                                                                Text("Barcode: ${prod.barcode} | Stock: ${prod.stockQuantity} | Price: NPR ${prod.sellingPrice}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                            }
                                                        },
                                                        onClick = {
                                                            viewModel.scanBarcode(prod.barcode) { success, msg ->
                                                                scanSuccess = success
                                                                scanMessage = msg
                                                                if (success) manualBarcode = ""
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    
                                    Button(
                                        onClick = {
                                            if (manualBarcode.isNotBlank()) {
                                                viewModel.scanBarcode(manualBarcode) { success, msg ->
                                                    scanSuccess = success
                                                    scanMessage = msg
                                                    if (success) manualBarcode = ""
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFBD00FF),
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.height(56.dp)
                                    ) {
                                        Text(com.example.util.t("submit", viewModel), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // Cart Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${com.example.util.t("shopping_cart", viewModel)} (${com.example.util.tNum(cart.size, viewModel)} ${com.example.util.t("items", viewModel)})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        if (cart.isNotEmpty()) {
                            TextButton(onClick = { viewModel.clearCart() }) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = "Clear All", tint = Color(0xFFFF007F))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(com.example.util.t("clear_cart", viewModel), color = Color(0xFFFF007F))
                            }
                        }
                    }
                }

                // Cart items
                if (cart.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF13131A).copy(alpha = 0.4f))
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                                .padding(vertical = 32.dp, horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ShoppingCart,
                                    contentDescription = "Empty Cart",
                                    tint = Color.White.copy(alpha = 0.25f),
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = com.example.util.t("cart_is_empty", viewModel),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.5f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(cart.entries.toList(), key = { it.key.id }) { (product, quantity) ->
                        CartItemRow(
                            product = product,
                            quantity = quantity,
                            barcodeLabel = com.example.util.tNum(String.format(com.example.util.t("bar_prefix", viewModel), product.barcode), viewModel),
                            priceEachLabel = com.example.util.tNum(String.format(com.example.util.t("price_each", viewModel), product.sellingPrice), viewModel),
                            onQuantityChanged = { newQty -> viewModel.updateCartQuantity(product, newQty) },
                            onDelete = { viewModel.removeFromCart(product) }
                        )
                    }
                }
                    // Cart summary action
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        val finalPrice = cartTotal

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp)),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(com.example.util.t("subtotal", viewModel), style = MaterialTheme.typography.bodyMedium)
                                    Text("NPR ${com.example.util.tNum(String.format("%.2f", finalPrice), viewModel)}", fontWeight = FontWeight.Bold)
                                }
                                if (discountAmount > 0.0) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(com.example.util.t("discount_applied", viewModel), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        Text("- NPR ${com.example.util.tNum(String.format("%.2f", discountAmount), viewModel)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(com.example.util.t("tax_included", viewModel), style = MaterialTheme.typography.bodyMedium)
                                    Text("NPR ${com.example.util.tNum("0.00", viewModel)}", fontWeight = FontWeight.Bold)
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(com.example.util.t("total_pay", viewModel), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(
                                        "NPR ${com.example.util.tNum(String.format("%.2f", (finalPrice - discountAmount).coerceAtLeast(0.0)), viewModel)}",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = { showCheckoutDialog = true },
                                    enabled = (finalPrice - discountAmount) >= 1.0,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = "Pay")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(com.example.util.t("checkout_generate_invoice", viewModel), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(40.dp)) }
                }
            }

            // Floating alert banner for scan confirmations
            AnimatedVisibility(
                visible = scanMessage != null,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            ) {
                Surface(
                    color = if (scanSuccess) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(24.dp),
                    tonalElevation = 6.dp,
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (scanSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = "Status",
                            tint = if (scanSuccess) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = scanMessage ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (scanSuccess) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // --- Checkout Confirmation Dialog (Slide-Up Bottom Sheet) ---
    if (showCheckoutDialog) {
        val haptic = LocalHapticFeedback.current
        ModalBottomSheet(
            onDismissRequest = { 
                showCheckoutDialog = false 
                selectedCustomerIdForCredit = null
                customerPhone = ""
                paymentTransactionId = ""
                discountInput = ""
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            tonalElevation = 8.dp,
            dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = com.example.util.t("invoice_bill_summary", viewModel),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                // 1. Structured, semi-transparent Visual Bill Summary Panel detailing Subtotal & Discounts
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(com.example.util.t("subtotal", viewModel), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("NPR ${com.example.util.tNum(String.format("%.2f", cartTotal), viewModel)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                        
                        if (discountAmount > 0.0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(com.example.util.t("discount_applied", viewModel), style = MaterialTheme.typography.bodyMedium, color = Color(0xFFE53935), fontWeight = FontWeight.Bold)
                                Text("- NPR ${com.example.util.tNum(String.format("%.2f", discountAmount), viewModel)}", fontWeight = FontWeight.Bold, color = Color(0xFFE53935))
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(com.example.util.t("total_pay", viewModel), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                            Text(
                                "NPR ${com.example.util.tNum(String.format("%.2f", (cartTotal - discountAmount).coerceAtLeast(0.0)), viewModel)}",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Customer Profile Selector Dropdown
                Text(com.example.util.t("select_customer_account", viewModel), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedCustomerIdForCredit?.let { id ->
                            customers.find { it.id == id }?.name
                        } ?: com.example.util.t("select_registered_customer", viewModel),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(if (selectedPaymentMode == "Credit") com.example.util.t("customer_profile_required", viewModel) else com.example.util.t("customer_profile_optional", viewModel)) },
                        leadingIcon = { Icon(Icons.Default.AccountCircle, contentDescription = "Customer") },
                        trailingIcon = {
                            IconButton(onClick = { customerDropdownExpanded = !customerDropdownExpanded }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Expand")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { customerDropdownExpanded = !customerDropdownExpanded }
                    )

                    DropdownMenu(
                        expanded = customerDropdownExpanded,
                        onDismissRequest = { customerDropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        if (customers.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text(com.example.util.t("no_customers_store_credit", viewModel)) },
                                onClick = { customerDropdownExpanded = false }
                            )
                        } else {
                            customers.forEach { customer ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(customer.name, fontWeight = FontWeight.Bold)
                                            Text("Phone: ${customer.phone}" + if (!customer.address.isNullOrBlank()) " | Addr: ${customer.address}" else "", fontSize = 11.sp, color = Color.Gray)
                                        }
                                    },
                                    onClick = {
                                        selectedCustomerIdForCredit = customer.id
                                        customerPhone = customer.phone
                                        customerDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                if (selectedPaymentMode != "Online") {
                    OutlinedTextField(
                        value = customerPhone,
                        onValueChange = { customerPhone = it },
                        label = { Text(com.example.util.t("customer_mobile_whatsapp", viewModel)) },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = "Phone") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 2. High-contrast Segmented Buttons for Payment Mode Selector
                Text(com.example.util.t("select_payment_mode", viewModel), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                val paymentModes = remember(loggedInUser) {
                    val modes = mutableListOf("Cash", "Online")
                    if (loggedInUser?.isSuperAdmin == true || loggedInUser?.canManageStoreCredit == true) {
                        modes.add("Credit")
                    }
                    modes
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(4.dp)
                ) {
                    paymentModes.forEach { mode ->
                        val isSelected = selectedPaymentMode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else Color.Transparent
                                )
                                .clickable { 
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selectedPaymentMode = mode 
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val displayModeName = when (mode) {
                                "Cash" -> com.example.util.t("cash", viewModel)
                                "Online" -> com.example.util.t("online", viewModel)
                                "Credit" -> com.example.util.t("credit", viewModel)
                                else -> mode
                            }
                            Text(
                                text = displayModeName,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                if (selectedPaymentMode == "Online") {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = com.example.util.t("enter_online_payment_details", viewModel),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedTextField(
                        value = customerPhone,
                        onValueChange = { customerPhone = it },
                        label = { Text(com.example.util.t("payment_phone_required", viewModel)) },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = "Phone") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = paymentTransactionId,
                        onValueChange = { paymentTransactionId = it },
                        label = { Text(com.example.util.t("transaction_id_required", viewModel)) },
                        leadingIcon = { Icon(Icons.Default.Receipt, contentDescription = "Transaction ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (selectedPaymentMode == "Credit" && selectedCustomerIdForCredit == null) {
                    Text(
                        text = com.example.util.t("credit_mode_customer_warning", viewModel),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (selectedPaymentMode == "Online" && 
                    (customerPhone.isBlank() || paymentTransactionId.isBlank())) {
                    Text(
                        text = com.example.util.t("online_details_missing_warning", viewModel),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Discount Panel
                Text(com.example.util.t("apply_discount_optional", viewModel), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = discountInput,
                        onValueChange = { input ->
                            if (input.isEmpty() || input.all { it.isDigit() || it == '.' }) {
                                discountInput = input
                            }
                        },
                        label = { Text(if (discountTypeIsPercentage) com.example.util.t("discount_percentage", viewModel) else com.example.util.t("discount_amount", viewModel)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        TextButton(
                            onClick = { discountTypeIsPercentage = false },
                            colors = ButtonDefaults.textButtonColors(
                                containerColor = if (!discountTypeIsPercentage) MaterialTheme.colorScheme.primary else Color.Transparent,
                                contentColor = if (!discountTypeIsPercentage) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("NPR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            onClick = { discountTypeIsPercentage = true },
                            colors = ButtonDefaults.textButtonColors(
                                containerColor = if (discountTypeIsPercentage) MaterialTheme.colorScheme.primary else Color.Transparent,
                                contentColor = if (discountTypeIsPercentage) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("%", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (isDiscountCapped) {
                    Text(
                        text = com.example.util.tNum(String.format(com.example.util.t("discount_capped_warning", viewModel), maxDiscount, cartCost), viewModel),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))

                // 3. Prominent, modern, pill-shaped FAB to "Complete Sale & Print" with a haptic feel
                val canConfirm = (when (selectedPaymentMode) {
                    "Credit" -> selectedCustomerIdForCredit != null
                    "Online" -> customerPhone.isNotBlank() && paymentTransactionId.isNotBlank()
                    else -> true
                }) && (cartTotal - discountAmount) >= 1.0

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showCheckoutDialog = false
                            val finalPaymentMode = if (selectedPaymentMode == "Online") {
                                "Online (TxID: $paymentTransactionId)"
                            } else {
                                selectedPaymentMode
                            }
                            viewModel.performCheckout(finalPaymentMode, customerPhone, discountAmount, selectedCustomerIdForCredit)
                            customerPhone = ""
                            paymentTransactionId = ""
                            selectedCustomerIdForCredit = null
                            discountInput = ""
                        },
                        expanded = true,
                        icon = { Icon(Icons.Default.Print, contentDescription = "Print", tint = if (canConfirm) Color(0xFF1E020A) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)) },
                        text = { Text(com.example.util.t("complete_sale_print", viewModel), fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp, color = if (canConfirm) Color(0xFF1E020A) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)) },
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(54.dp),
                        shape = RoundedCornerShape(27.dp),
                        containerColor = if (canConfirm) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        contentColor = if (canConfirm) Color(0xFF1E020A) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }

                TextButton(
                    onClick = { 
                        showCheckoutDialog = false 
                        selectedCustomerIdForCredit = null
                        customerPhone = ""
                        paymentTransactionId = ""
                        discountInput = ""
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(com.example.util.t("cancel_transaction", viewModel), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    // --- Role Unlock Pin Dialog ---
    if (showRoleUnlockDialog) {
        AlertDialog(
            onDismissRequest = { showRoleUnlockDialog = false },
            title = { Text(com.example.util.t("enter_admin_pin", viewModel), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(com.example.util.t("enter_admin_pin_desc", viewModel))
                    OutlinedTextField(
                        value = roleUnlockPin,
                        onValueChange = {
                            roleUnlockPin = it
                            roleUnlockError = false
                        },
                        label = { Text(com.example.util.t("admin_pin_label", viewModel)) },
                        isError = roleUnlockError,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (roleUnlockError) {
                        Text(com.example.util.t("incorrect_admin_pin", viewModel), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val authAdmin = viewModel.usersList.find { it.pin == roleUnlockPin && it.isEnabled && it.role == com.example.ui.UserRole.ADMIN }
                        if (authAdmin != null) {
                            viewModel.setUserRole(com.example.ui.UserRole.ADMIN)
                            showRoleUnlockDialog = false
                            roleUnlockPin = ""
                        } else {
                            roleUnlockError = true
                        }
                    }
                ) {
                    Text(com.example.util.t("verify", viewModel))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRoleUnlockDialog = false }) {
                    Text(com.example.util.t("cancel", viewModel))
                }
            }
        )
    }

    // --- Receipt Invoice Visualizer & Sharing Dialog ---
    if (checkoutReceipt != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissReceipt() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = "Receipt", tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(com.example.util.t("bill_generated_success", viewModel), fontWeight = FontWeight.Bold)
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
                                text = checkoutReceipt ?: "",
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
                            val textToShare = checkoutReceipt ?: ""
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
                        Text(com.example.util.t("share_receipt_send", viewModel))
                    }

                    Button(
                        onClick = {
                            val textToShare = checkoutReceipt ?: ""
                            val whatsappIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, textToShare)
                                setPackage("com.whatsapp")
                            }
                            try {
                                context.startActivity(whatsappIntent)
                            } catch (e: Exception) {
                                // If WhatsApp is not installed, open system chooser
                                val chooser = Intent.createChooser(whatsappIntent, "Send in WhatsApp")
                                context.startActivity(chooser)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "WhatsApp", tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(com.example.util.t("share_via_whatsapp", viewModel), color = Color.White)
                    }

                    Button(
                        onClick = {
                            val textToShare = checkoutReceipt ?: ""
                            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                                data = android.net.Uri.parse("mailto:")
                                putExtra(Intent.EXTRA_SUBJECT, "Purbesh Stationary - Sales Receipt")
                                putExtra(Intent.EXTRA_TEXT, textToShare)
                            }
                            try {
                                context.startActivity(Intent.createChooser(emailIntent, "Send Email..."))
                            } catch (e: Exception) {
                                val fallbackIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, textToShare)
                                    putExtra(Intent.EXTRA_SUBJECT, "Purbesh Stationary - Sales Receipt")
                                }
                                context.startActivity(Intent.createChooser(fallbackIntent, "Share Receipt Via"))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Email, contentDescription = "Email", tint = MaterialTheme.colorScheme.onPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(com.example.util.t("send_via_email", viewModel))
                    }

                    Button(
                        onClick = {
                            val textToShare = checkoutReceipt ?: ""
                            com.example.util.ReceiptExporter.printReceipt(context, textToShare, "Receipt_Checkout")
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Print, contentDescription = "Print")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(com.example.util.t("print_bill", viewModel))
                    }

                    TextButton(
                        onClick = { viewModel.dismissReceipt() },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(com.example.util.t("close_new_bill", viewModel))
                    }
                }
            }
        )
    }
}

@Composable
fun CartItemRow(
    product: Product,
    quantity: Int,
    barcodeLabel: String,
    priceEachLabel: String,
    onQuantityChanged: (Int) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.name,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = product.category,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    Text(
                        text = barcodeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = priceEachLabel,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            // Quantity selector
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                IconButton(
                    onClick = { onQuantityChanged(quantity - 1) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Reduce",
                        modifier = Modifier.size(16.dp)
                    )
                }

                Text(
                    text = "$quantity",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                IconButton(
                    onClick = { onQuantityChanged(quantity + 1) },
                    modifier = Modifier.size(32.dp),
                    enabled = quantity < product.stockQuantity
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Increase",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Item",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun CameraScannerView(
    onBarcodeDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var hasCameraPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (hasCameraPermission) {
        val previewView = remember { PreviewView(context) }
        val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }

        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
        ) {
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, BarcodeAnalyzer { barcode ->
                            // Deliver result back to UI
                            previewView.post {
                                onBarcodeScannedThrottled(barcode, onBarcodeDetected)
                            }
                        })
                    }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(context))
        }

        DisposableEffect(Unit) {
            onDispose {
                cameraExecutor.shutdown()
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(Color.DarkGray, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                Icon(Icons.Default.Camera, contentDescription = "Camera Required", tint = Color.White, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Camera permission is required to scan barcodes.", color = Color.White, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Grant Permission")
                }
            }
        }
    }
}

// Throttle scan events to avoid rapid fire
private var lastScanTime = 0L
private fun onBarcodeScannedThrottled(barcode: String, onScanned: (String) -> Unit) {
    val currentTime = System.currentTimeMillis()
    if (currentTime - lastScanTime > 3000) { // 3 seconds cooldown
        lastScanTime = currentTime
        onScanned(barcode)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable FlowRowScope.() -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement,
        content = content
    )
}
