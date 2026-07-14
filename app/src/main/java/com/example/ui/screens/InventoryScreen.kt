package com.example.ui.screens

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Product
import com.example.ui.InventoryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@android.annotation.SuppressLint("MissingPermission")
@Composable
fun InventoryScreen(
    viewModel: InventoryViewModel
) {
    val products by viewModel.allProducts.collectAsState()
    val lowStockList by viewModel.lowStockProducts.collectAsState()
    val currentRole by viewModel.currentUserRole.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            sendLowStockNotification(context, lowStockList)
        } else {
            android.widget.Toast.makeText(context, "Notification permission is required for alerts.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("All") }

    // Dialog state
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf<Product?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<Product?>(null) }
    var showCampaignDialog by remember { mutableStateOf(false) }
    var promoPhone by remember { mutableStateOf("") }
    var promoDiscount by remember { mutableStateOf("20% Off") }
    var promoProduct by remember { mutableStateOf("") }
    var showLabelGeneratorForProduct by remember { mutableStateOf<Product?>(null) }

    // Dropdown options, dynamically constructed from defaults and actual product categories in the DB
    val categories = remember(products) {
        val defaultCats = listOf("Pens & Pencils", "Notebooks", "Art Supplies", "Office Stationery", "Adhesives & Tape", "Other")
        val dbCats = products.map { it.category }.filter { it.isNotBlank() }
        (listOf("All") + defaultCats + dbCats).distinct()
    }

    // Filtered products list
    val filteredProducts = remember(products, searchQuery, selectedCategoryFilter) {
        products.filter { prod ->
            val matchesSearch = prod.name.contains(searchQuery, ignoreCase = true) || 
                                prod.barcode.contains(searchQuery) ||
                                prod.category.contains(searchQuery, ignoreCase = true)
            val matchesCat = selectedCategoryFilter == "All" || prod.category == selectedCategoryFilter
            matchesSearch && matchesCat
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF020104), // Extreme deep pitch black
                        Color(0xFF0B061A), // Deep neon purple ambient core
                        Color(0xFF030206)  // Pitch black base
                    )
                )
            )
    ) {
        // Ambient glow spotlights (Behance / Dribbble SaaS aesthetic)
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFBD00FF).copy(alpha = 0.12f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.85f, size.height * 0.15f),
                    radius = size.width * 0.6f
                )
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF00E5FF).copy(alpha = 0.08f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.15f, size.height * 0.75f),
                    radius = size.width * 0.7f
                )
            )
        }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(Color(0xFFBD00FF), Color(0xFF00E5FF).copy(alpha = 0.5f))
                                        ),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .padding(1.dp)
                                    .background(Color(0xFF0A0911), RoundedCornerShape(9.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Inventory2,
                                    contentDescription = "Inventory Icon",
                                    tint = Color(0xFFBD00FF),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "PURBESH STATIONERY",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    letterSpacing = 1.sp,
                                    fontSize = 15.sp
                                )
                                Text(
                                    "Premium Inventory Catalog",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 10.sp,
                                    color = Color(0xFFB5AEC4),
                                    fontWeight = FontWeight.Medium
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

                        if (currentRole == com.example.ui.UserRole.ADMIN) {
                            IconButton(onClick = { showCampaignDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.Campaign,
                                    contentDescription = "SMS Offer Campaign",
                                    tint = Color(0xFF00E5FF)
                                )
                            }
                            IconButton(onClick = { showAddDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add Product",
                                    tint = Color(0xFFBD00FF)
                                )
                            }
                        }

                        Box {
                            IconButton(
                                onClick = { showProfileMenu = true },
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(Color(loggedInUser?.avatarColor ?: 0xFFBD00FF), CircleShape)
                                        .border(1.5.dp, Color(0xFF00E5FF), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = loggedInUser?.fullName?.take(1)?.uppercase() ?: "U",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showProfileMenu,
                                onDismissRequest = { showProfileMenu = false },
                                modifier = Modifier
                                    .background(Color(0xFF0C0A18))
                                    .border(1.dp, Color(0xFFBD00FF).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(loggedInUser?.fullName ?: "Unknown User", fontWeight = FontWeight.Bold, color = Color.White)
                                            Text(
                                                text = currentRole.label,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF00E5FF),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    },
                                    onClick = {},
                                    enabled = false
                                )
                                
                                Divider(modifier = Modifier.padding(vertical = 4.dp), color = Color(0xFF2F2A4A))

                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Default.Logout, contentDescription = "Log Out", tint = Color(0xFFFF007F)) },
                                    text = { Text("Log Out / Switch Profile", color = Color(0xFFFF007F)) },
                                    onClick = {
                                        showProfileMenu = false
                                        viewModel.logout()
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    ),
                    modifier = Modifier.border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xFF2F2A4A).copy(alpha = 0.2f))
                        ),
                        shape = androidx.compose.ui.graphics.RectangleShape
                    )
                )
            },
            floatingActionButton = {
                if (currentRole == com.example.ui.UserRole.ADMIN) {
                    Box(
                        modifier = Modifier
                            .padding(bottom = 16.dp)
                            .size(64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Ambient glow halo
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFFBD00FF).copy(alpha = 0.25f), CircleShape)
                        )
                        // Main button
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFFBD00FF), Color(0xFFFF007F))
                                    )
                                )
                                .clickable { showAddDialog = true }
                                .border(1.5.dp, Color(0xFF00E5FF), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Product",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
            ) {
                if (currentRole == com.example.ui.UserRole.CASHIER) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFFF007F).copy(alpha = 0.08f))
                            .border(1.dp, Color(0xFFFF007F).copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Read Only",
                                tint = Color(0xFFFF007F)
                            )
                            Column {
                                Text(
                                    text = "Read-Only Cashier Mode",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFFFF007F)
                                )
                                Text(
                                    text = "Pricing, stock additions and catalog edits require Admin permissions.",
                                    fontSize = 11.sp,
                                    color = Color(0xFFB5AEC4)
                                )
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Sleek glassmorphic search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search stationery catalog, barcode or category...", color = Color(0xFFB5AEC4).copy(alpha = 0.5f)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color(0xFFBD00FF)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color(0xFFFF007F))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White.copy(alpha = 0.9f),
                        focusedContainerColor = Color(0xFF131124).copy(alpha = 0.65f),
                        unfocusedContainerColor = Color(0xFF0F0E1C).copy(alpha = 0.4f),
                        focusedBorderColor = Color(0xFFBD00FF),
                        unfocusedBorderColor = Color(0xFF2F2A4A).copy(alpha = 0.7f),
                        cursorColor = Color(0xFFBD00FF)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            brush = Brush.horizontalGradient(
                                listOf(Color(0xFFBD00FF).copy(alpha = 0.15f), Color(0xFF00E5FF).copy(alpha = 0.1f))
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                )

                if (lowStockList.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFFFF007F).copy(alpha = 0.08f))
                            .border(
                                width = 1.dp,
                                brush = Brush.horizontalGradient(
                                    listOf(Color(0xFFFF007F).copy(alpha = 0.5f), Color(0xFFBD00FF).copy(alpha = 0.2f))
                                ),
                                shape = RoundedCornerShape(18.dp)
                            )
                            .padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(Color(0xFFFF007F).copy(alpha = 0.15f), CircleShape)
                                    .border(1.dp, Color(0xFFFF007F).copy(alpha = 0.3f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = "Alert",
                                    tint = Color(0xFFFF007F),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Low Stock Push Alerts",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFFFF007F)
                                )
                                Text(
                                    text = "${lowStockList.size} items are below safe thresholds. Trigger system alerts.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFB5AEC4)
                                )
                            }
                            val context = LocalContext.current
                            Button(
                                onClick = {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                        if (androidx.core.content.ContextCompat.checkSelfPermission(
                                                context,
                                                android.Manifest.permission.POST_NOTIFICATIONS
                                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                        ) {
                                            sendLowStockNotification(context, lowStockList)
                                        } else {
                                            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    } else {
                                        sendLowStockNotification(context, lowStockList)
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFF007F),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.height(34.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                            ) {
                                Text("Alert", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Premium Category Tab Row (Dribbble Glass style)
                Text(
                    text = "STATIONERY CATALOGS",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFBD00FF),
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                ScrollableTabRow(
                    selectedTabIndex = categories.indexOf(selectedCategoryFilter).coerceAtLeast(0),
                    edgePadding = 0.dp,
                    divider = {},
                    indicator = {},
                    containerColor = Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    categories.forEach { cat ->
                        val isSelected = selectedCategoryFilter == cat
                        Tab(
                            selected = isSelected,
                            onClick = { selectedCategoryFilter = cat },
                            modifier = Modifier.padding(bottom = 8.dp, end = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) {
                                            Brush.horizontalGradient(
                                                listOf(Color(0xFFBD00FF).copy(alpha = 0.35f), Color(0xFF00E5FF).copy(alpha = 0.15f))
                                            )
                                        } else {
                                            Brush.horizontalGradient(
                                                listOf(Color(0xFF131124).copy(alpha = 0.5f), Color(0xFF131124).copy(alpha = 0.2f))
                                            )
                                        }
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) Color(0xFFBD00FF) else Color(0xFF2F2A4A).copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = cat,
                                    color = if (isSelected) Color.White else Color(0xFFB5AEC4),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Summary metrics line
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${filteredProducts.size} stationery items cataloged",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFB5AEC4)
                    )
                    if (lowStockList.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(modifier = Modifier.size(6.dp).background(Color(0xFFFF007F), CircleShape))
                            Text(
                                text = "${lowStockList.size} alerts active",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF007F)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Product list
                if (filteredProducts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MenuBook,
                                contentDescription = "No Products",
                                tint = Color(0xFF2F2A4A),
                                modifier = Modifier.size(64.dp)
                            )
                            Text(
                                text = "No stationery items match filters",
                                color = Color(0xFFB5AEC4),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 90.dp)
                    ) {
                        items(filteredProducts, key = { it.id }) { product ->
                            ProductItemCard(
                                product = product,
                                isAdmin = currentRole == com.example.ui.UserRole.ADMIN,
                                viewModel = viewModel,
                                onEdit = { showEditDialog = product },
                                onDelete = { showDeleteConfirm = product },
                                onGenerateLabel = { showLabelGeneratorForProduct = product }
                            )
                        }
                    }
                }
            }
        }
    }

    // --- Add Product Dialog ---
    if (showAddDialog) {
        ProductFormDialog(
            viewModel = viewModel,
            title = com.example.util.t("add_new_stationery_item", viewModel),
            productsList = products,
            onDismiss = { showAddDialog = false },
            onSave = { name, barcode, cat, cp, sp, stock, threshold ->
                viewModel.addProductToInventory(name, barcode, cat, cp, sp, stock, threshold)
                showAddDialog = false
            }
        )
    }

    // --- Campaign Dialog ---
    if (showCampaignDialog) {
        val prodNames = products.map { it.name }
        var expandedProdDropdown by remember { mutableStateOf(false) }
        
        AlertDialog(
            onDismissRequest = { showCampaignDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Campaign, contentDescription = "Promo", tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Broadcast Offer Alert", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Blast high-conversion SMS Alerts to Purbesh Stationary clients detailing exclusive discounts.")
                    
                    OutlinedTextField(
                        value = promoPhone,
                        onValueChange = { promoPhone = it },
                        label = { Text("Client Mobile Number") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = "Phone") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = promoDiscount,
                        onValueChange = { promoDiscount = it },
                        label = { Text("Discount Amount (e.g., 25% Off)") },
                        leadingIcon = { Icon(Icons.Default.Percent, contentDescription = "Percent") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = promoProduct,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Target Stationery Product") },
                            leadingIcon = { Icon(Icons.Default.MenuBook, contentDescription = "Product") },
                            trailingIcon = {
                                IconButton(onClick = { expandedProdDropdown = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Expand")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(
                            expanded = expandedProdDropdown,
                            onDismissRequest = { expandedProdDropdown = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            prodNames.take(12).forEach { name ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        promoProduct = name
                                        expandedProdDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalMsg = "Purbesh Stationary Special Offer! 🎉 Get $promoDiscount on '$promoProduct' for a limited time. Visit us today to redeem!"
                        viewModel.sendSms(
                            recipient = if (promoPhone.isNotBlank()) promoPhone else "9800000000 (Subscribers)",
                            message = finalMsg,
                            type = "Offer Alert"
                        )
                        showCampaignDialog = false
                    },
                    enabled = promoProduct.isNotBlank()
                ) {
                    Text("Broadcast Promo")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCampaignDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- Edit Product Dialog ---
    if (showEditDialog != null) {
        val prodToEdit = showEditDialog!!
        ProductFormDialog(
            viewModel = viewModel,
            title = com.example.util.t("edit_stationery_item", viewModel),
            product = prodToEdit,
            productsList = products,
            onDismiss = { showEditDialog = null },
            onSave = { name, barcode, cat, cp, sp, stock, threshold ->
                viewModel.updateProductInInventory(
                    prodToEdit.copy(
                        name = name,
                        barcode = barcode,
                        category = cat,
                        costPrice = cp,
                        sellingPrice = sp,
                        stockQuantity = stock,
                        minStockThreshold = threshold
                    )
                )
                showEditDialog = null
            }
        )
    }

    // --- Delete Confirmation Dialog ---
    if (showDeleteConfirm != null) {
        val prodToDelete = showDeleteConfirm!!
        var enteredPin by remember { mutableStateOf("") }
        var pinError by remember { mutableStateOf(false) }
        val loggedInUser by viewModel.loggedInUser.collectAsState()

        AlertDialog(
            onDismissRequest = { 
                showDeleteConfirm = null 
                enteredPin = ""
                pinError = false
            },
            title = { Text("Verify PIN to Delete Product", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Are you sure you want to delete '${prodToDelete.name}' from catalog? This cannot be undone.")
                    
                    OutlinedTextField(
                        value = enteredPin,
                        onValueChange = {
                            if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                                enteredPin = it
                                pinError = false
                            }
                        },
                        label = { Text("Enter Login PIN") },
                        isError = pinError,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (pinError) {
                        Text(
                            text = "Incorrect PIN. Please try again.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val currentPin = loggedInUser?.pin
                        val isValid = if (currentPin != null) {
                            enteredPin == currentPin
                        } else {
                            viewModel.usersList.any { it.pin == enteredPin && it.isEnabled }
                        }
                        
                        if (isValid) {
                            viewModel.deleteProductFromInventory(prodToDelete)
                            showDeleteConfirm = null
                            enteredPin = ""
                            pinError = false
                        } else {
                            pinError = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    enabled = enteredPin.length == 4
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showDeleteConfirm = null 
                    enteredPin = ""
                    pinError = false
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- Barcode & QR Label Generator Dialog ---
    if (showLabelGeneratorForProduct != null) {
        LabelGeneratorDialog(
            product = showLabelGeneratorForProduct!!,
            onDismiss = { showLabelGeneratorForProduct = null }
        )
    }
}

@Composable
fun LabelGeneratorDialog(
    product: Product,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var labelType by remember { mutableStateOf("QR Code") }

    val qrMatrix = remember(product.barcode) {
        try {
            com.google.zxing.qrcode.QRCodeWriter().encode(
                product.barcode.ifBlank { "0000" },
                com.google.zxing.BarcodeFormat.QR_CODE,
                40,
                40
            )
        } catch (e: Exception) {
            null
        }
    }

    val barcodeMatrix = remember(product.barcode) {
        try {
            com.google.zxing.MultiFormatWriter().encode(
                product.barcode.ifBlank { "0000" },
                com.google.zxing.BarcodeFormat.CODE_128,
                120,
                1
            )
        } catch (e: Exception) {
            null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.QrCode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("In-App Label Generator", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Generate retail labels for ${product.name} to paste on shelving or packaging.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ElevatedFilterChip(
                        selected = labelType == "QR Code",
                        onClick = { labelType = "QR Code" },
                        label = { Text("QR Code Label") },
                        modifier = Modifier.weight(1f)
                    )
                    ElevatedFilterChip(
                        selected = labelType == "Barcode",
                        onClick = { labelType = "Barcode" },
                        label = { Text("1D Barcode") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Card(
                    modifier = Modifier
                        .width(220.dp)
                        .height(180.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "PURBESH STATIONERY",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.DarkGray
                        )
                        Text(
                            text = product.name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (labelType == "QR Code") {
                            androidx.compose.foundation.Canvas(
                                modifier = Modifier
                                    .size(72.dp)
                                    .border(1.dp, Color.Black)
                                    .padding(4.dp)
                             ) {
                                if (qrMatrix != null) {
                                    val matrixWidth = qrMatrix.width
                                    val matrixHeight = qrMatrix.height
                                    val cellWidth = size.width / matrixWidth
                                    val cellHeight = size.height / matrixHeight
                                    for (y in 0 until matrixHeight) {
                                        for (x in 0 until matrixWidth) {
                                            if (qrMatrix.get(x, y)) {
                                                drawRect(
                                                    color = Color.Black,
                                                    topLeft = androidx.compose.ui.geometry.Offset(x * cellWidth, y * cellHeight),
                                                    size = androidx.compose.ui.geometry.Size(cellWidth, cellHeight)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            androidx.compose.foundation.Canvas(
                                modifier = Modifier
                                    .width(160.dp)
                                    .height(55.dp)
                                    .border(0.5.dp, Color.LightGray)
                                    .padding(vertical = 4.dp, horizontal = 8.dp)
                            ) {
                                if (barcodeMatrix != null) {
                                    val matrixWidth = barcodeMatrix.width
                                    val cellWidth = size.width / matrixWidth
                                    for (x in 0 until matrixWidth) {
                                        if (barcodeMatrix.get(x, 0)) {
                                            drawRect(
                                                color = Color.Black,
                                                topLeft = androidx.compose.ui.geometry.Offset(x * cellWidth, 0f),
                                                size = androidx.compose.ui.geometry.Size(cellWidth, size.height)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Text(
                            text = "*${product.barcode}*",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.Black
                        )
                        Text(
                            text = "NPR ${String.format("%.2f", product.sellingPrice)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.Red
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val labelCodeHtml = if (labelType == "QR Code") {
                        com.example.util.ReceiptExporter.generateQrSvg(product.barcode)
                    } else {
                        com.example.util.ReceiptExporter.generateBarcodeSvg(product.barcode)
                    }

                    val htmlLabel = """
                        <html>
                        <head>
                        <meta charset="utf-8">
                        <style>
                            body {
                                font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif;
                                text-align: center;
                                padding: 12px;
                                margin: 0;
                                background-color: #ffffff;
                                color: #000000;
                            }
                            .label-container {
                                border: 2px solid #000000;
                                padding: 14px;
                                display: inline-block;
                                width: 260px;
                                border-radius: 8px;
                                background-color: #ffffff;
                            }
                            .title { 
                                font-weight: bold; 
                                font-size: 11px; 
                                letter-spacing: 1px;
                                margin-bottom: 4px; 
                                color: #555555;
                            }
                            .prod-name { 
                                font-size: 16px; 
                                font-weight: 900; 
                                margin-bottom: 8px; 
                                color: #000000;
                                white-space: nowrap;
                                overflow: hidden;
                                text-overflow: ellipsis;
                            }
                            .code-wrapper {
                                margin: 10px auto;
                                display: block;
                                text-align: center;
                            }
                            .code-wrapper svg {
                                display: inline-block;
                                max-width: 100%;
                                height: auto;
                            }
                            .barcode-num { 
                                font-family: monospace;
                                font-size: 11px; 
                                letter-spacing: 2px; 
                                margin-top: 4px; 
                                color: #333333;
                            }
                            .price { 
                                font-size: 18px; 
                                font-weight: bold; 
                                color: #e53935; 
                                margin-top: 8px; 
                            }
                        </style>
                        </head>
                        <body>
                            <div class="label-container">
                                <div class="title">PURBESH STATIONERY</div>
                                <div class="prod-name">${product.name}</div>
                                <div class="code-wrapper">
                                    $labelCodeHtml
                                </div>
                                <div class="barcode-num">${product.barcode}</div>
                                <div class="price">NPR ${String.format("%.2f", product.sellingPrice)}</div>
                            </div>
                        </body>
                        </html>
                    """.trimIndent()
                    com.example.util.ReceiptExporter.printHtml(context, htmlLabel, "Label_${product.barcode}")
                }
            ) {
                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Print / Export Label")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun BarcodeGraphic(
    barcode: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF2F2A4A).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.QrCode,
            contentDescription = null,
            tint = Color(0xFFBD00FF),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        // Styled barcode lines
        Row(
            modifier = Modifier.weight(1f, fill = false),
            horizontalArrangement = Arrangement.spacedBy(1.5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.width(2.dp).height(14.dp).background(Color.White.copy(alpha = 0.8f)))
            Box(modifier = Modifier.width(1.dp).height(14.dp).background(Color.White.copy(alpha = 0.8f)))
            Box(modifier = Modifier.width(3.dp).height(14.dp).background(Color.White.copy(alpha = 0.8f)))
            Box(modifier = Modifier.width(1.dp).height(14.dp).background(Color.White.copy(alpha = 0.2f)))
            Box(modifier = Modifier.width(2.dp).height(14.dp).background(Color.White.copy(alpha = 0.8f)))
            Box(modifier = Modifier.width(1.dp).height(14.dp).background(Color.White.copy(alpha = 0.8f)))
            Box(modifier = Modifier.width(4.dp).height(14.dp).background(Color.White.copy(alpha = 0.8f)))
            Box(modifier = Modifier.width(2.dp).height(14.dp).background(Color.White.copy(alpha = 0.2f)))
            Box(modifier = Modifier.width(1.dp).height(14.dp).background(Color.White.copy(alpha = 0.8f)))
            Box(modifier = Modifier.width(3.dp).height(14.dp).background(Color.White.copy(alpha = 0.8f)))
            Box(modifier = Modifier.width(2.dp).height(14.dp).background(Color.White.copy(alpha = 0.8f)))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = barcode,
            fontSize = 11.sp,
            color = Color(0xFFB5AEC4),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ProductItemCard(
    product: Product,
    isAdmin: Boolean,
    viewModel: InventoryViewModel,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onGenerateLabel: () -> Unit
) {
    val isLowStock = product.stockQuantity <= product.minStockThreshold

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF0F0E1C).copy(alpha = 0.65f))
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        if (isLowStock) Color(0xFFFF007F).copy(alpha = 0.5f) else Color(0xFFBD00FF).copy(alpha = 0.35f),
                        Color.Transparent
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header row: Title, alert, category, and action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = product.name,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isLowStock) {
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFFF007F).copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                                    .border(1.dp, Color(0xFFFF007F).copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "LOW STOCK",
                                    color = Color(0xFFFF007F),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Category pill
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF1E1A3A).copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .border(1.dp, Color(0xFF2F2A4A).copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = product.category,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFB5AEC4),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Apple/Linear styled horizontal control block
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(Color(0xFF141226).copy(alpha = 0.8f), RoundedCornerShape(10.dp))
                        .border(1.dp, Color(0xFF2F2A4A).copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(4.dp)
                ) {
                    IconButton(
                        onClick = onGenerateLabel,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Label QR generator",
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    if (isAdmin) {
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit item",
                                tint = Color(0xFFBD00FF),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete item",
                                tint = Color(0xFFFF007F),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Styled Simulated Barcode Graphic Representation
            BarcodeGraphic(barcode = product.barcode, modifier = Modifier.fillMaxWidth())

            // Stock Capacity Level Track Indicator
            val maxStockCap = (product.minStockThreshold * 3).coerceAtLeast(10)
            val levelRatio = (product.stockQuantity.toFloat() / maxStockCap.toFloat()).coerceIn(0f, 1f)
            val fillBrush = Brush.horizontalGradient(
                if (isLowStock) {
                    listOf(Color(0xFFFF007F), Color(0xFFFF5E97))
                } else {
                    listOf(Color(0xFFBD00FF), Color(0xFF00E5FF))
                }
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "STOCK CAPACITY INDICATOR",
                        fontSize = 9.sp,
                        color = Color(0xFFB5AEC4),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "${com.example.util.tNum(product.stockQuantity, viewModel)} / ${com.example.util.tNum(maxStockCap, viewModel)} units",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isLowStock) Color(0xFFFF007F) else Color(0xFF00E5FF)
                    )
                }
                // Glass track
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(Color(0xFF131124), RoundedCornerShape(3.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(levelRatio)
                            .background(fillBrush, RoundedCornerShape(3.dp))
                    )
                }
            }

            // Subtle divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0xFF2F2A4A).copy(alpha = 0.3f))
            )

            // Prices & profit markup stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Selling Price
                Column {
                    Text(
                        text = "SELLING PRICE",
                        fontSize = 9.sp,
                        color = Color(0xFFB5AEC4),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "NPR ${com.example.util.tNum(String.format("%.2f", product.sellingPrice), viewModel)}",
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        color = Color(0xFF00E5FF)
                    )
                }

                // Cost Price
                Column {
                    Text(
                        text = "COST PRICE",
                        fontSize = 9.sp,
                        color = Color(0xFFB5AEC4),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "NPR ${com.example.util.tNum(String.format("%.2f", product.costPrice), viewModel)}",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = Color(0xFFB5AEC4)
                    )
                }

                // Estimated Gross Markup Percentage
                val profitDiff = product.sellingPrice - product.costPrice
                val percentMarkup = if (product.costPrice > 0.0) (profitDiff / product.costPrice) * 100.0 else 0.0
                val markupColor = if (profitDiff >= 0.0) Color(0xFF00FFCC) else Color(0xFFFF007F)

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "EST. MARKUP",
                        fontSize = 9.sp,
                        color = Color(0xFFB5AEC4),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Box(
                        modifier = Modifier
                            .background(markupColor.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                            .border(1.dp, markupColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${if (profitDiff >= 0.0) "+" else ""}${com.example.util.tNum(String.format("%.1f", percentMarkup), viewModel)}%",
                            color = markupColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductFormDialog(
    viewModel: InventoryViewModel,
    title: String,
    product: Product? = null,
    productsList: List<Product> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (String, String, String, Double, Double, Int, Int) -> Unit
) {
    var name by remember { mutableStateOf(product?.name ?: "") }
    var barcode by remember { mutableStateOf(product?.barcode ?: "") }
    var showCameraScanner by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf(product?.category ?: "Pens & Pencils") }
    var costPrice by remember { mutableStateOf(product?.costPrice?.toString() ?: "") }
    var sellingPrice by remember { mutableStateOf(product?.sellingPrice?.toString() ?: "") }
    var stockQuantity by remember { mutableStateOf(product?.stockQuantity?.toString() ?: "") }
    var minThreshold by remember { mutableStateOf(product?.minStockThreshold?.toString() ?: "5") }

    val defaultCategories = listOf("Pens & Pencils", "Notebooks", "Art Supplies", "Office Stationery", "Adhesives & Tape", "Other")
    val dynamicCategories = remember(productsList) {
        val dbCats = productsList.map { it.category }.filter { it.isNotBlank() }
        (defaultCategories + dbCats).distinct()
    }

    var categoryExpanded by remember { mutableStateOf(false) }
    var isCustomCategory by remember { mutableStateOf(product != null && !defaultCategories.contains(product.category)) }
    var customCategoryText by remember { mutableStateOf(if (isCustomCategory) category else "") }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    val nameBarcodeErrorText = com.example.util.t("error_name_barcode_required", viewModel)
    val invalidNumbersErrorText = com.example.util.t("error_invalid_numbers", viewModel)

    val customTextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White.copy(alpha = 0.9f),
        focusedContainerColor = Color(0xFF131124).copy(alpha = 0.8f),
        unfocusedContainerColor = Color(0xFF0F0E1C).copy(alpha = 0.4f),
        focusedBorderColor = Color(0xFFBD00FF),
        unfocusedBorderColor = Color(0xFF2F2A4A).copy(alpha = 0.7f),
        cursorColor = Color(0xFFBD00FF),
        focusedLabelColor = Color(0xFFBD00FF),
        unfocusedLabelColor = Color(0xFFB5AEC4)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold, color = Color.White) },
        containerColor = Color(0xFF0D0B1C),
        textContentColor = Color.White,
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (errorMessage != null) {
                    item {
                        Surface(
                            color = Color(0xFFFF007F).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF007F)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = errorMessage!!,
                                color = Color(0xFFFF007F),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(10.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(com.example.util.t("product_name", viewModel)) },
                        singleLine = true,
                        colors = customTextFieldColors,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        OutlinedTextField(
                            value = barcode,
                            onValueChange = { barcode = it },
                            label = { Text(com.example.util.t("barcode_number", viewModel)) },
                            singleLine = true,
                            colors = customTextFieldColors,
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                            trailingIcon = {
                                IconButton(onClick = { showCameraScanner = !showCameraScanner }) {
                                    Icon(
                                        imageVector = if (showCameraScanner) Icons.Default.Close else Icons.Default.QrCodeScanner,
                                        contentDescription = com.example.util.t("scan_camera", viewModel),
                                        tint = if (showCameraScanner) Color(0xFFFF007F) else Color(0xFF00E5FF)
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Quick Generate Barcode / QR Number Options
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = com.example.util.t("generate", viewModel) + ":",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFB5AEC4),
                                fontWeight = FontWeight.Medium
                            )

                            AssistChip(
                                onClick = {
                                    // Generate a standard regional EAN-13-like numeric barcode (13-digit)
                                    val randomBarcode = (8901000000000L + (100000000..999999999).random()).toString()
                                    barcode = randomBarcode
                                },
                                label = { Text(com.example.util.t("one_d_barcode", viewModel), fontSize = 11.sp, color = Color(0xFFBD00FF)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.QrCode,
                                        contentDescription = com.example.util.t("one_d_barcode", viewModel),
                                        tint = Color(0xFFBD00FF),
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = Color(0xFFBD00FF).copy(alpha = 0.08f)
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = 1.dp,
                                    color = Color(0xFFBD00FF).copy(alpha = 0.4f)
                                )
                            )

                            AssistChip(
                                onClick = {
                                    // Generate a clean alphanumeric SKU for QR code format
                                    val prefix = if (name.isNotBlank()) {
                                        val formattedName = name.replace(Regex("[^a-zA-Z]"), "").uppercase()
                                        if (formattedName.length >= 3) formattedName.take(3) else "STN"
                                    } else {
                                        "STN"
                                    }
                                    val randomSuffix = (1000..9999).random()
                                    barcode = "$prefix-$randomSuffix"
                                },
                                label = { Text(com.example.util.t("qr_sku", viewModel), fontSize = 11.sp, color = Color(0xFF00E5FF)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.QrCodeScanner,
                                        contentDescription = com.example.util.t("qr_sku", viewModel),
                                        tint = Color(0xFF00E5FF),
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = Color(0xFF00E5FF).copy(alpha = 0.08f)
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = 1.dp,
                                    color = Color(0xFF00E5FF).copy(alpha = 0.4f)
                                )
                            )
                        }

                        AnimatedVisibility(visible = showCameraScanner) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = com.example.util.t("scan_instruction", viewModel),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFBD00FF)
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(2.dp, Color(0xFFBD00FF), RoundedCornerShape(12.dp))
                                ) {
                                    CameraScannerView(onBarcodeDetected = { scanned ->
                                        barcode = scanned
                                        showCameraScanner = false
                                    })
                                }
                                
                                // Quick Scan Simulator for devices without real camera / emulator
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = com.example.util.t("emulator_scan", viewModel),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFB5AEC4)
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Button(
                                            onClick = {
                                                val randomBarcode = (8901000000000L + (100000000..999999999).random()).toString()
                                                barcode = randomBarcode
                                                showCameraScanner = false
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1A3A), contentColor = Color.White),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text(com.example.util.t("random_barcode", viewModel), fontSize = 10.sp)
                                        }
                                        Button(
                                            onClick = {
                                                val stationeryItems = listOf("PEN-BLUE-01", "PENCIL-HB-02", "NOTEBOOK-A5", "GLUE-STICK", "ART-BRUSH-05")
                                                barcode = stationeryItems.random()
                                                showCameraScanner = false
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F0E1C), contentColor = Color.White),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text(com.example.util.t("sku_alphanumeric", viewModel), fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Category select or text entry
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (!isCustomCategory) {
                            ExposedDropdownMenuBox(
                                expanded = categoryExpanded,
                                onExpandedChange = { categoryExpanded = !categoryExpanded }
                            ) {
                                OutlinedTextField(
                                    value = category,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(com.example.util.t("category", viewModel)) },
                                    colors = customTextFieldColors,
                                    shape = RoundedCornerShape(12.dp),
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor()
                                )
                                ExposedDropdownMenu(
                                    expanded = categoryExpanded,
                                    onDismissRequest = { categoryExpanded = false },
                                    modifier = Modifier.background(Color(0xFF0D0B1C))
                                ) {
                                    dynamicCategories.forEach { cat ->
                                        DropdownMenuItem(
                                            text = { Text(cat, color = Color.White) },
                                            onClick = {
                                                category = cat
                                                categoryExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = { 
                                        isCustomCategory = true
                                        customCategoryText = ""
                                    }
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Add", tint = Color(0xFFBD00FF), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(com.example.util.t("add_custom_category", viewModel), fontSize = 12.sp, color = Color(0xFFBD00FF))
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = customCategoryText,
                                onValueChange = { 
                                    customCategoryText = it
                                    category = it
                                },
                                label = { Text(com.example.util.t("custom_category_name", viewModel)) },
                                singleLine = true,
                                colors = customTextFieldColors,
                                shape = RoundedCornerShape(12.dp),
                                trailingIcon = {
                                    IconButton(onClick = {
                                        isCustomCategory = false
                                        category = "Pens & Pencils"
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear custom", tint = Color(0xFFFF007F))
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = { 
                                        isCustomCategory = false
                                        category = "Pens & Pencils"
                                    }
                                ) {
                                    Icon(Icons.Default.List, contentDescription = "Select", tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(com.example.util.t("select_existing_category", viewModel), fontSize = 12.sp, color = Color(0xFF00E5FF))
                                }
                            }
                        }
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = costPrice,
                            onValueChange = { costPrice = it },
                            label = { Text(com.example.util.t("cost_price_npr", viewModel)) },
                            singleLine = true,
                            colors = customTextFieldColors,
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = sellingPrice,
                            onValueChange = { sellingPrice = it },
                            label = { Text(com.example.util.t("selling_price_npr", viewModel)) },
                            singleLine = true,
                            colors = customTextFieldColors,
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = stockQuantity,
                            onValueChange = { stockQuantity = it },
                            label = { Text(com.example.util.t("stock_qty", viewModel)) },
                            singleLine = true,
                            colors = customTextFieldColors,
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = minThreshold,
                            onValueChange = { minThreshold = it },
                            label = { Text(com.example.util.t("min_alert_lvl", viewModel)) },
                            singleLine = true,
                            colors = customTextFieldColors,
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cpVal = costPrice.toDoubleOrNull()
                    val spVal = sellingPrice.toDoubleOrNull()
                    val stockVal = stockQuantity.toIntOrNull()
                    val threshVal = minThreshold.toIntOrNull()

                    if (name.isBlank() || barcode.isBlank()) {
                        errorMessage = nameBarcodeErrorText
                    } else if (cpVal == null || spVal == null || stockVal == null || threshVal == null) {
                        errorMessage = invalidNumbersErrorText
                    } else {
                        onSave(name, barcode, category, cpVal, spVal, stockVal, threshVal)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFBD00FF),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(com.example.util.t("save", viewModel), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text(com.example.util.t("cancel", viewModel), color = Color(0xFFB5AEC4))
            }
        }
    )
}

@android.annotation.SuppressLint("MissingPermission")
private fun sendLowStockNotification(context: android.content.Context, lowStockList: List<com.example.data.Product>) {
    try {
        val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channelId = "low_stock_alerts_channel"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId, 
                "Low Stock Alerts", 
                android.app.NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }
        val messageText = lowStockList.take(3).joinToString { it.name } + if (lowStockList.size > 3) " and others" else ""
        val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ Purbesh Stationery Low Stock")
            .setContentText("$messageText are running critically low!")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        
        notificationManager.notify(101, builder.build())
        android.widget.Toast.makeText(context, "System push notification triggered!", android.widget.Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        android.util.Log.e("InventoryScreen", "Failed to send notification: ${e.message}")
    }
}

