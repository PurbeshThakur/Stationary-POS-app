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

    val cartTotal = cart.entries.sumOf { it.key.sellingPrice * it.value }
    val cartCost = cart.entries.sumOf { it.key.costPrice * it.value }
    val maxDiscount = (cartTotal - cartCost).coerceAtLeast(0.0)

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
                                .background(Color(0xFF4A4458)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Store,
                                contentDescription = "Purbesh Stationary Storefront",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                shopName,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Stationery Shop Inventory",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = Color(0xFF938F99)
                            )
                        }
                    }
                },
                actions = {
                    val currentRole by viewModel.currentUserRole.collectAsState()
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
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(loggedInUser?.avatarColor ?: 0xFF6200EE), CircleShape)
                                    .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = loggedInUser?.fullName?.take(1)?.uppercase() ?: "U",
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
                                    text = { Text("Elevate to Admin") },
                                    onClick = {
                                        showProfileMenu = false
                                        showRoleUnlockDialog = true
                                    }
                                )
                            } else {
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Default.LockOpen, contentDescription = "Demote", tint = MaterialTheme.colorScheme.outline) },
                                    text = { Text("Switch to Cashier Role") },
                                    onClick = {
                                        showProfileMenu = false
                                        viewModel.setUserRole(com.example.ui.UserRole.CASHIER)
                                    }
                                )
                            }

                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Logout, contentDescription = "Log Out", tint = MaterialTheme.colorScheme.error) },
                                text = { Text("Log Out / Switch Profile", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showProfileMenu = false
                                    viewModel.logout()
                                }
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
        val loggedInUser by viewModel.loggedInUser.collectAsState()
        val currentRole by viewModel.currentUserRole.collectAsState()
        val canPerformSale = loggedInUser?.canPerformSale == true || currentRole == com.example.ui.UserRole.ADMIN

        Box(
            modifier = Modifier
                .fillMaxSize()
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
                                text = "POS Terminal Restricted",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = "Your cashier profile does not have permission to perform sales. Please contact the administrator or enter an authorized PIN to elevate access.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
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
                                label = { Text("Enter Authorized Admin PIN") },
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                isError = pinErr,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (pinErr) {
                                Text(
                                    text = "Incorrect PIN. Try default '1111' or '1234'.",
                                    color = MaterialTheme.colorScheme.error,
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
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.VerifiedUser, contentDescription = "Verify")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Elevate Terminal")
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
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Icon(Icons.Default.ShoppingCart, contentDescription = "Cart Items", tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Cart Items", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
                                Text("${cart.values.sum()}", fontWeight = FontWeight.Black, fontSize = 22.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }

                        // Low stock alert count card
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onNavigateToInventory() },
                            colors = CardDefaults.cardColors(
                                containerColor = if (lowStockList.isNotEmpty()) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = "Low Stock Alert",
                                    tint = if (lowStockList.isNotEmpty()) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Stock Alerts", fontSize = 12.sp, color = if (lowStockList.isNotEmpty()) MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                Text("${lowStockList.size} Items", fontWeight = FontWeight.Black, fontSize = 22.sp, color = if (lowStockList.isNotEmpty()) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        // Cart Value card
                        val cartTotal = cart.entries.sumOf { it.key.sellingPrice * it.value }
                        Card(
                            modifier = Modifier.weight(1.2f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Icon(Icons.Default.Payments, contentDescription = "Cart Total", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Cart Total", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                                Text("NPR ${String.format("%.2f", cartTotal)}", fontWeight = FontWeight.Black, fontSize = 22.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
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
                                .clip(RoundedCornerShape(8.dp)),
                            color = MaterialTheme.colorScheme.errorContainer,
                            onClick = onNavigateToInventory
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Error, contentDescription = "Alert", tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Alert: ${lowStockList.size} stationery items are below minimum stock limits! Tap to view.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                // Scanner Interface Section
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Scan Stationery Barcode", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                
                                Button(
                                    onClick = { showCameraScanner = !showCameraScanner },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (showCameraScanner) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Icon(
                                        imageVector = if (showCameraScanner) Icons.Default.Close else Icons.Default.QrCodeScanner,
                                        contentDescription = "Toggle Scan"
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (showCameraScanner) "Close Camera" else "Open Camera Scanner")
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Camera scanner preview
                            AnimatedVisibility(
                                visible = showCameraScanner,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                                        "Align product barcode within camera view",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (!showCameraScanner) {
                                // Manual Input/Simulator helper
                                Text(
                                    "No camera? Use the quick scan simulator below or type a barcode:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = manualBarcode,
                                        onValueChange = { manualBarcode = it },
                                        label = { Text("Enter Barcode No.") },
                                        leadingIcon = { Icon(Icons.Default.QrCode, contentDescription = "Barcode") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    
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
                                        modifier = Modifier.height(56.dp)
                                    ) {
                                        Text("Submit")
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Simulator Row of quick products
                                Text(
                                    "⚡ Quick Scanner Simulator (Click product to scan):",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    products.take(6).forEach { prod ->
                                        AssistChip(
                                            onClick = {
                                                viewModel.scanBarcode(prod.barcode) { success, msg ->
                                                    scanSuccess = success
                                                    scanMessage = msg
                                                }
                                            },
                                            label = { Text("${prod.name} (${prod.barcode})", overflow = TextOverflow.Ellipsis, maxLines = 1) },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.Add,
                                                    contentDescription = "Scan",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        )
                                    }
                                    if (products.isEmpty()) {
                                        Text("No products in inventory yet.", fontSize = 11.sp)
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
                            text = "Shopping Cart (${cart.size} distinct items)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (cart.isNotEmpty()) {
                            TextButton(onClick = { viewModel.clearCart() }) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = "Clear All")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Clear Cart")
                            }
                        }
                    }
                }

                // Cart items
                if (cart.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.ShoppingCartCheckout,
                                contentDescription = "Empty Cart",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Cart is empty",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Text(
                                "Scan stationery barcode or search items to add",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(cart.entries.toList()) { (product, quantity) ->
                        CartItemRow(
                            product = product,
                            quantity = quantity,
                            onQuantityChanged = { newQty -> viewModel.updateCartQuantity(product, newQty) },
                            onDelete = { viewModel.removeFromCart(product) }
                        )
                    }

                    // Cart summary action
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        val finalPrice = cart.entries.sumOf { it.key.sellingPrice * it.value }

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
                                    Text("Subtotal", style = MaterialTheme.typography.bodyMedium)
                                    Text("NPR ${String.format("%.2f", finalPrice)}", fontWeight = FontWeight.Bold)
                                }
                                if (discountAmount > 0.0) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Discount Applied", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        Text("- NPR ${String.format("%.2f", discountAmount)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Tax (Included)", style = MaterialTheme.typography.bodyMedium)
                                    Text("NPR 0.00", fontWeight = FontWeight.Bold)
                                }
                                Divider(modifier = Modifier.padding(vertical = 8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Total Pay", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(
                                        "NPR ${String.format("%.2f", (finalPrice - discountAmount).coerceAtLeast(0.0))}",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = { showCheckoutDialog = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = "Pay")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Checkout & Generate Invoice", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(40.dp)) }
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
}

    // --- Checkout Confirmation Dialog ---
    if (showCheckoutDialog) {
        AlertDialog(
            onDismissRequest = { 
                showCheckoutDialog = false 
                selectedCustomerIdForCredit = null
                customerPhone = ""
                paymentTransactionId = ""
                discountInput = ""
            },
            title = { Text("Complete Transaction", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Select customer account and payment mode.")
                    
                    // Customer Profile Selector Dropdown
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedCustomerIdForCredit?.let { id ->
                                customers.find { it.id == id }?.name
                            } ?: "Select Registered Customer",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Customer Profile" + if (selectedPaymentMode == "Credit") " (Required) *" else " (Optional)") },
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
                                    text = { Text("No customers with store credit account") },
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
                            label = { Text("Customer Mobile (WhatsApp sharing)") },
                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = "Phone") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Text("Payment Mode", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("Cash", "Online", "Credit").forEach { mode ->
                            FilterChip(
                                selected = selectedPaymentMode == mode,
                                onClick = { selectedPaymentMode = mode },
                                label = { Text(mode, fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    if (selectedPaymentMode == "Online") {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Enter Online Payment Details",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        OutlinedTextField(
                            value = customerPhone,
                            onValueChange = { customerPhone = it },
                            label = { Text("Payment Phone Number (Required) *") },
                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = "Phone") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = paymentTransactionId,
                            onValueChange = { paymentTransactionId = it },
                            label = { Text("Transaction ID (Required) *") },
                            leadingIcon = { Icon(Icons.Default.Receipt, contentDescription = "Transaction ID") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (selectedPaymentMode == "Credit" && selectedCustomerIdForCredit == null) {
                        Text(
                            text = "⚠️ Please select a registered customer to use Credit payment mode.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (selectedPaymentMode == "Online" && 
                        (customerPhone.isBlank() || paymentTransactionId.isBlank())) {
                        Text(
                            text = "⚠️ Please enter Phone Number and Transaction ID to proceed.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text("Apply Discount (Optional)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
                            label = { Text(if (discountTypeIsPercentage) "Discount (%)" else "Discount (NPR)") },
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
                            text = "⚠️ Discount capped to NPR ${String.format("%.2f", maxDiscount)} to keep price above the cost of NPR ${String.format("%.2f", cartCost)}.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    if (discountAmount > 0.0) {
                        val originalTotal = cart.entries.sumOf { it.key.sellingPrice * it.value }
                        val finalDiscounted = (originalTotal - discountAmount).coerceAtLeast(0.0)
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Original Subtotal:", style = MaterialTheme.typography.bodySmall)
                                    Text("NPR ${String.format("%.2f", originalTotal)}", style = MaterialTheme.typography.bodySmall, textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Discount Applied:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Text("- NPR ${String.format("%.2f", discountAmount)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("New Total:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    Text("NPR ${String.format("%.2f", finalDiscounted)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                val canConfirm = when (selectedPaymentMode) {
                    "Credit" -> selectedCustomerIdForCredit != null
                    "Online" -> customerPhone.isNotBlank() && paymentTransactionId.isNotBlank()
                    else -> true
                }
                Button(
                    onClick = {
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
                    enabled = canConfirm
                ) {
                    Text("Confirm Checkout")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showCheckoutDialog = false 
                    selectedCustomerIdForCredit = null
                    customerPhone = ""
                    paymentTransactionId = ""
                    discountInput = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- Role Unlock Pin Dialog ---
    if (showRoleUnlockDialog) {
        AlertDialog(
            onDismissRequest = { showRoleUnlockDialog = false },
            title = { Text("Enter Admin PIN", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Please enter the Admin PIN to switch to Administrator mode.")
                    OutlinedTextField(
                        value = roleUnlockPin,
                        onValueChange = {
                            roleUnlockPin = it
                            roleUnlockError = false
                        },
                        label = { Text("Admin PIN") },
                        isError = roleUnlockError,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (roleUnlockError) {
                        Text("Incorrect PIN. Please try default '1234'.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
                    Text("Verify")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRoleUnlockDialog = false }) {
                    Text("Cancel")
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
                    Text("Bill Generated Successfully!", fontWeight = FontWeight.Bold)
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
                        Text("Share Receipt / Send")
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
                        Text("Send via WhatsApp", color = Color.White)
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
                        Text("Send via Email")
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
                        Text("Print Bill / Save as PDF")
                    }

                    TextButton(
                        onClick = { viewModel.dismissReceipt() },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Close / New Bill")
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
                        text = "Bar: ${product.barcode}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "NPR ${String.format("%.2f", product.sellingPrice)} each",
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
