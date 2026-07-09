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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Product
import com.example.ui.InventoryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    viewModel: InventoryViewModel
) {
    val products by viewModel.allProducts.collectAsState()
    val lowStockList by viewModel.lowStockProducts.collectAsState()

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
                                imageVector = Icons.Default.Inventory2,
                                contentDescription = "Inventory",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                "Inventory Catalog",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Stock Levels & Pricing",
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

                    if (currentRole == com.example.ui.UserRole.ADMIN) {
                        IconButton(onClick = { showCampaignDialog = true }) {
                            Icon(Icons.Default.Campaign, contentDescription = "SMS Offer Campaign", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Product", tint = MaterialTheme.colorScheme.primary)
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
                                    .background(Color(loggedInUser?.avatarColor ?: 0xFF6200EE), CircleShape)
                                    .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
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
        },
        floatingActionButton = {
            val currentRole by viewModel.currentUserRole.collectAsState()
            if (currentRole == com.example.ui.UserRole.ADMIN) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Product")
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
            val currentRole by viewModel.currentUserRole.collectAsState()
            
            if (currentRole == com.example.ui.UserRole.CASHIER) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Read Only",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Column {
                            Text(
                                text = "Read-Only Mode (Cashier Mode)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "Catalog changes, pricing adjustments and stock additions require Admin PIN.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search stationery items, barcode or category...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Category filter chips
            Text("Filter by Category", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(6.dp))
            ScrollableTabRow(
                selectedTabIndex = categories.indexOf(selectedCategoryFilter).coerceAtLeast(0),
                edgePadding = 0.dp,
                divider = {},
                indicator = {}
            ) {
                categories.forEach { cat ->
                    val isSelected = selectedCategoryFilter == cat
                    Tab(
                        selected = isSelected,
                        onClick = { selectedCategoryFilter = cat },
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        SuggestionChip(
                            onClick = { selectedCategoryFilter = cat },
                            label = { Text(cat) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            ),
                            border = SuggestionChipDefaults.suggestionChipBorder(
                                enabled = true,
                                borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Summary counts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${filteredProducts.size} Items Listed",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (lowStockList.isNotEmpty()) {
                    Text(
                        text = "⚠️ ${lowStockList.size} Low Stock Warnings",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Product List
            if (filteredProducts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.MenuBook, contentDescription = "No Products", tint = Color.LightGray, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No stationery products match the search.", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filteredProducts) { product ->
                        val currentRole by viewModel.currentUserRole.collectAsState()
                        ProductItemCard(
                            product = product,
                            isAdmin = currentRole == com.example.ui.UserRole.ADMIN,
                            onEdit = { showEditDialog = product },
                            onDelete = { showDeleteConfirm = product }
                        )
                    }
                }
            }
        }
    }

    // --- Add Product Dialog ---
    if (showAddDialog) {
        ProductFormDialog(
            title = "Add New Stationery Item",
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
            title = "Edit Stationery Item",
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
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Delete Product?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete '${prodToDelete.name}' from catalog? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteProductFromInventory(prodToDelete)
                        showDeleteConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ProductItemCard(
    product: Product,
    isAdmin: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isLowStock = product.stockQuantity <= product.minStockThreshold

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isLowStock) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
        ),
        border = if (isLowStock) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = product.name,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isLowStock) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.error, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("LOW STOCK", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
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
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column {
                        Text("STOCK LEVEL", fontSize = 10.sp, color = Color.Gray)
                        Text(
                            text = "${product.stockQuantity} units",
                            fontWeight = FontWeight.Bold,
                            color = if (isLowStock) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Column {
                        Text("SELLING PRICE", fontSize = 10.sp, color = Color.Gray)
                        Text(
                            text = "NPR ${String.format("%.2f", product.sellingPrice)}",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Column {
                        Text("COST PRICE", fontSize = 10.sp, color = Color.Gray)
                        Text(
                            text = "NPR ${String.format("%.2f", product.costPrice)}",
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray
                        )
                    }
                }
            }

            if (isAdmin) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductFormDialog(
    title: String,
    product: Product? = null,
    productsList: List<Product> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (String, String, String, Double, Double, Int, Int) -> Unit
) {
    var name by remember { mutableStateOf(product?.name ?: "") }
    var barcode by remember { mutableStateOf(product?.barcode ?: "") }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (errorMessage != null) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = errorMessage!!,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Product Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = barcode,
                        onValueChange = { barcode = it },
                        label = { Text("Barcode Number") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
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
                                    label = { Text("Category") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor()
                                )
                                ExposedDropdownMenu(
                                    expanded = categoryExpanded,
                                    onDismissRequest = { categoryExpanded = false }
                                ) {
                                    dynamicCategories.forEach { cat ->
                                        DropdownMenuItem(
                                            text = { Text(cat) },
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
                                    Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add Custom Category", fontSize = 12.sp)
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = customCategoryText,
                                onValueChange = { 
                                    customCategoryText = it
                                    category = it
                                },
                                label = { Text("Custom Category Name") },
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(onClick = {
                                        isCustomCategory = false
                                        category = "Pens & Pencils"
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear custom")
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
                                    Icon(Icons.Default.List, contentDescription = "Select", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Select Existing Category", fontSize = 12.sp)
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
                            label = { Text("Cost Price (NPR)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = sellingPrice,
                            onValueChange = { sellingPrice = it },
                            label = { Text("Selling Price (NPR)") },
                            singleLine = true,
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
                            label = { Text("Stock Qty") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = minThreshold,
                            onValueChange = { minThreshold = it },
                            label = { Text("Min Alert Lvl") },
                            singleLine = true,
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
                        errorMessage = "Product name and barcode are required."
                    } else if (cpVal == null || spVal == null || stockVal == null || threshVal == null) {
                        errorMessage = "Please enter valid numbers for price, stock, and threshold."
                    } else {
                        onSave(name, barcode, category, cpVal, spVal, stockVal, threshVal)
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
