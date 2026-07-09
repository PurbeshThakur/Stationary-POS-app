package com.example.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

sealed interface AiTipsState {
    object Idle : AiTipsState
    object Loading : AiTipsState
    data class Success(val tips: String) : AiTipsState
    data class Error(val message: String) : AiTipsState
}

enum class UserRole(val label: String) {
    ADMIN("Admin"),
    CASHIER("Cashier/Staff")
}

data class User(
    val username: String,
    val role: UserRole,
    val pin: String,
    val fullName: String,
    val avatarColor: Long
)

data class SmsAlert(
    val id: Int,
    val recipient: String,
    val message: String,
    val type: String, // "Sale Receipt" or "Offer Alert"
    val timestamp: Long
)

sealed interface CloudBackupState {
    object Idle : CloudBackupState
    object Syncing : CloudBackupState
    data class Success(val message: String) : CloudBackupState
    data class Error(val message: String) : CloudBackupState
}

class InventoryViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application, viewModelScope)
    private val repository = StationeryRepository(database.productDao(), database.saleDao())
    private val geminiService = GeminiService()
    private val cloudService = CloudBackupService()
    private val googleDriveService = GoogleDriveBackupService()
    private val sharedPrefs = application.getSharedPreferences("stationery_backup_prefs", Context.MODE_PRIVATE)

    private val _cloudBackupState = MutableStateFlow<CloudBackupState>(CloudBackupState.Idle)
    val cloudBackupState: StateFlow<CloudBackupState> = _cloudBackupState.asStateFlow()

    private val _googleDriveBackupState = MutableStateFlow<CloudBackupState>(CloudBackupState.Idle)
    val googleDriveBackupState: StateFlow<CloudBackupState> = _googleDriveBackupState.asStateFlow()

    private val _googleAccountEmail = MutableStateFlow<String>(sharedPrefs.getString("google_account_email", "") ?: "")
    val googleAccountEmail: StateFlow<String> = _googleAccountEmail.asStateFlow()

    private val _googleAccountName = MutableStateFlow<String>(sharedPrefs.getString("google_account_name", "") ?: "")
    val googleAccountName: StateFlow<String> = _googleAccountName.asStateFlow()

    private val _googleDriveFileId = MutableStateFlow<String>(sharedPrefs.getString("google_drive_file_id", "") ?: "")
    val googleDriveFileId: StateFlow<String> = _googleDriveFileId.asStateFlow()

    private val _cloudVaultId = MutableStateFlow<String>(sharedPrefs.getString("cloud_vault_id", "") ?: "")
    val cloudVaultId: StateFlow<String> = _cloudVaultId.asStateFlow()

    private val _autoBackupEnabled = MutableStateFlow<Boolean>(sharedPrefs.getBoolean("auto_backup_enabled", false))
    val autoBackupEnabled: StateFlow<Boolean> = _autoBackupEnabled.asStateFlow()

    fun setGoogleAccount(email: String?, displayName: String?) {
        sharedPrefs.edit()
            .putString("google_account_email", email ?: "")
            .putString("google_account_name", displayName ?: "")
            .apply()
        _googleAccountEmail.value = email ?: ""
        _googleAccountName.value = displayName ?: ""
        if (email.isNullOrBlank()) {
            setGoogleDriveFileId("")
        }
    }

    fun setGoogleDriveFileId(fileId: String) {
        sharedPrefs.edit().putString("google_drive_file_id", fileId).apply()
        _googleDriveFileId.value = fileId
    }

    fun resetGoogleDriveBackupState() {
        _googleDriveBackupState.value = CloudBackupState.Idle
    }

    fun setCloudVaultId(vaultId: String) {
        sharedPrefs.edit().putString("cloud_vault_id", vaultId).apply()
        _cloudVaultId.value = vaultId
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        sharedPrefs.edit().putBoolean("auto_backup_enabled", enabled).apply()
        _autoBackupEnabled.value = enabled
    }

    fun resetCloudBackupState() {
        _cloudBackupState.value = CloudBackupState.Idle
    }

    val usersList = listOf(
        User("admin", UserRole.ADMIN, "1234", "System Administrator", 0xFF6200EE),
        User("purbesh", UserRole.ADMIN, "1111", "Purbesh (Admin)", 0xFF00C853),
        User("staff", UserRole.CASHIER, "0000", "Stationery Cashier", 0xFFFFAB00),
        User("anjali", UserRole.CASHIER, "2222", "Anjali (Staff)", 0xFF00B0FF)
    )

    private val _loggedInUser = MutableStateFlow<User?>(null)
    val loggedInUser: StateFlow<User?> = _loggedInUser.asStateFlow()

    // User Role Access Control State
    private val _currentUserRole = MutableStateFlow(UserRole.CASHIER) // Default to cashier to enforce restriction initially
    val currentUserRole: StateFlow<UserRole> = _currentUserRole.asStateFlow()

    fun login(username: String, pin: String): Boolean {
        val user = usersList.find { it.username.equals(username, ignoreCase = true) && it.pin == pin }
        return if (user != null) {
            _loggedInUser.value = user
            _currentUserRole.value = user.role
            true
        } else {
            false
        }
    }

    fun logout() {
        _loggedInUser.value = null
    }

    fun setUserRole(role: UserRole) {
        _currentUserRole.value = role
        // Also update logged-in user role if elevated
        val current = _loggedInUser.value
        if (current != null && current.role != role) {
            _loggedInUser.value = current.copy(role = role)
        }
    }

    // SMS Logs
    private val _smsLogs = MutableStateFlow<List<SmsAlert>>(emptyList())
    val smsLogs: StateFlow<List<SmsAlert>> = _smsLogs.asStateFlow()

    // Floating SMS toast preview helper
    private val _lastSentSms = MutableStateFlow<SmsAlert?>(null)
    val lastSentSms: StateFlow<SmsAlert?> = _lastSentSms.asStateFlow()

    fun clearLastSentSms() {
        _lastSentSms.value = null
    }

    fun sendSms(recipient: String, message: String, type: String) {
        val newAlert = SmsAlert(
            id = _smsLogs.value.size + 1,
            recipient = recipient,
            message = message,
            type = type,
            timestamp = System.currentTimeMillis()
        )
        _smsLogs.value = listOf(newAlert) + _smsLogs.value
        _lastSentSms.value = newAlert
    }

    // UI state for inventory
    val allProducts: StateFlow<List<Product>> = repository.allProducts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSales: StateFlow<List<Sale>> = repository.allSales
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSaleItems: StateFlow<List<SaleItem>> = repository.allSaleItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Low stock items threshold alert
    val lowStockProducts: StateFlow<List<Product>> = allProducts
        .map { products ->
            products.filter { it.stockQuantity <= it.minStockThreshold }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Point of Sale Cart State: Map of Product to Quantity
    private val _cart = MutableStateFlow<Map<Product, Int>>(emptyMap())
    val cart: StateFlow<Map<Product, Int>> = _cart.asStateFlow()

    // Scanned or manual code search state
    val searchBarcodeQuery = MutableStateFlow("")

    // Success receipt for sharing/printing
    private val _checkoutReceipt = MutableStateFlow<String?>(null)
    val checkoutReceipt: StateFlow<String?> = _checkoutReceipt.asStateFlow()

    // AI advice tips state
    private val _aiTipsState = MutableStateFlow<AiTipsState>(AiTipsState.Idle)
    val aiTipsState: StateFlow<AiTipsState> = _aiTipsState.asStateFlow()

    // POS Cart Actions
    fun addToCart(product: Product, quantity: Int = 1) {
        if (product.stockQuantity <= 0) return
        val current = _cart.value.toMutableMap()
        val currentQty = current[product] ?: 0
        val targetQty = (currentQty + quantity).coerceAtMost(product.stockQuantity)
        current[product] = targetQty
        _cart.value = current
    }

    fun updateCartQuantity(product: Product, quantity: Int) {
        val current = _cart.value.toMutableMap()
        if (quantity <= 0) {
            current.remove(product)
        } else {
            current[product] = quantity.coerceAtMost(product.stockQuantity)
        }
        _cart.value = current
    }

    fun removeFromCart(product: Product) {
        val current = _cart.value.toMutableMap()
        current.remove(product)
        _cart.value = current
    }

    fun clearCart() {
        _cart.value = emptyMap()
        _checkoutReceipt.value = null
    }

    fun dismissReceipt() {
        _checkoutReceipt.value = null
    }

    /**
     * Scan a barcode and add it directly to cart if found
     */
    fun scanBarcode(barcode: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val product = repository.getProductByBarcode(barcode)
            if (product != null) {
                if (product.stockQuantity > 0) {
                    addToCart(product, 1)
                    onResult(true, "Added ${product.name} to cart")
                } else {
                    onResult(false, "${product.name} is out of stock")
                }
            } else {
                onResult(false, "No stationery item found with barcode: $barcode")
            }
        }
    }

    /**
     * Complete POS checkout transaction
     */
    fun performCheckout(paymentMode: String, customerPhone: String) {
        val cartItems = _cart.value
        if (cartItems.isEmpty()) return

        viewModelScope.launch {
            val timestamp = System.currentTimeMillis()
            var totalAmount = 0.0
            var totalProfit = 0.0

            val saleItems = cartItems.map { (product, quantity) ->
                val lineTotal = product.sellingPrice * quantity
                val lineCost = product.costPrice * quantity
                val lineProfit = lineTotal - lineCost
                
                totalAmount += lineTotal
                totalProfit += lineProfit

                SaleItem(
                    saleId = 0, // Set by repository transaction
                    productId = product.id,
                    productName = product.name,
                    barcode = product.barcode,
                    quantity = quantity,
                    sellingPrice = product.sellingPrice,
                    costPrice = product.costPrice
                )
            }

            val sale = Sale(
                timestamp = timestamp,
                totalAmount = totalAmount,
                totalProfit = totalProfit,
                paymentMode = paymentMode
            )

            val success = repository.executeSale(sale, saleItems)
            if (success) {
                // Generate a formatted receipt for print/sharing
                val receipt = buildFormattedReceipt(sale, saleItems, customerPhone)
                _checkoutReceipt.value = receipt
                _cart.value = emptyMap() // clear cart on success

                // Trigger automatic SMS receipt
                if (customerPhone.isNotBlank()) {
                    val smsText = "Thank you for shopping at Purbesh Stationary! Invoice #${1000 + sale.id} generated. Total: NPR ${String.format("%.2f", totalAmount)}. Mode: $paymentMode."
                    sendSms(customerPhone, smsText, "Sale Receipt")
                }

                // Automatic Cloud Sync & Backup on successful transaction (if enabled)
                if (_autoBackupEnabled.value) {
                    if (_cloudVaultId.value.isNotBlank()) {
                        backupToCloud()
                    }
                    if (_googleAccountEmail.value.isNotBlank()) {
                        backupToGoogleDriveBackground()
                    }
                }
            }
        }
    }

    private fun buildFormattedReceipt(sale: Sale, items: List<SaleItem>, phone: String): String {
        val sdf = SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.getDefault())
        val dateStr = sdf.format(Date(sale.timestamp))
        val sb = StringBuilder()
        sb.append("===============================\n")
        sb.append("       PURBESH STATIONARY      \n")
        sb.append("    Your Premium Writing Hub    \n")
        sb.append("===============================\n")
        sb.append("Date: $dateStr\n")
        if (phone.isNotBlank()) {
            sb.append("Customer Mob: $phone\n")
        }
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

    // Add Product to Inventory
    fun addProductToInventory(name: String, barcode: String, category: String, costPrice: Double, sellingPrice: Double, stock: Int, threshold: Int) {
        viewModelScope.launch {
            val product = Product(
                name = name,
                barcode = barcode,
                category = category,
                costPrice = costPrice,
                sellingPrice = sellingPrice,
                stockQuantity = stock,
                minStockThreshold = threshold
            )
            repository.insertProduct(product)
        }
    }

    // Update Product in Inventory
    fun updateProductInInventory(product: Product) {
        viewModelScope.launch {
            repository.updateProduct(product)
        }
    }

    // Delete Product from Inventory
    fun deleteProductFromInventory(product: Product) {
        viewModelScope.launch {
            repository.deleteProduct(product)
        }
    }

    // Call Gemini to get business growth recommendations
    fun loadAiSalesAdvice() {
        viewModelScope.launch {
            _aiTipsState.value = AiTipsState.Loading
            try {
                val products = allProducts.value
                val sales = allSales.value

                // Aggregate shop stats
                val totalProds = products.size
                val totalSalesCount = sales.size
                val totalRevenue = sales.sumOf { it.totalAmount }
                val totalProfit = sales.sumOf { it.totalProfit }

                val lowStockList = products.filter { it.stockQuantity <= it.minStockThreshold }
                val lowStockCount = lowStockList.size
                val lowStockNames = lowStockList.take(5).joinToString(", ") { "${it.name} (${it.stockQuantity} left)" }

                // Top Categories calculation
                val categoryMap = mutableMapOf<String, Int>()
                // Group products to estimate which is popular or count from sales
                val saleItemsList = allSaleItems.value
                for (item in saleItemsList) {
                    val prod = products.find { it.id == item.productId }
                    val cat = prod?.category ?: "Stationery"
                    categoryMap[cat] = (categoryMap[cat] ?: 0) + item.quantity
                }
                val topCats = categoryMap.entries.sortedByDescending { it.value }.take(3).joinToString(", ") { "${it.key} (${it.value} sold)" }
                val topCatsStr = if (topCats.isBlank()) "Pens, Notebooks, Art Supplies" else topCats

                val advice = geminiService.getSalesIncreaseTips(
                    totalProducts = totalProds,
                    totalSalesCount = totalSalesCount,
                    totalRevenue = totalRevenue,
                    totalProfit = totalProfit,
                    lowStockCount = lowStockCount,
                    lowStockNames = if (lowStockNames.isBlank()) "None" else lowStockNames,
                    topCategories = topCatsStr
                )
                _aiTipsState.value = AiTipsState.Success(advice)
            } catch (e: Exception) {
                e.printStackTrace()
                _aiTipsState.value = AiTipsState.Error(e.localizedMessage ?: "Failed to generate tips.")
            }
        }
    }

    /**
     * Uploads the entire DB (products, sales, items) + formatting report to the Cloud
     */
    fun backupToCloud(customReport: String? = null) {
        viewModelScope.launch {
            _cloudBackupState.value = CloudBackupState.Syncing
            val products = allProducts.value
            val sales = allSales.value
            val saleItems = allSaleItems.value

            val payload = CloudBackupPayload(
                products = products,
                sales = sales,
                saleItems = saleItems,
                formattedReport = customReport ?: buildBackupReportString(sales, saleItems),
                backupTimestamp = System.currentTimeMillis()
            )

            val currentId = _cloudVaultId.value
            if (currentId.isBlank()) {
                val newId = cloudService.createNewVault(payload)
                if (newId != null) {
                    setCloudVaultId(newId)
                    _cloudBackupState.value = CloudBackupState.Success("Cloud Storage Active! Saved under Vault ID: $newId")
                } else {
                    _cloudBackupState.value = CloudBackupState.Error("Cloud connection failed. Check your internet connectivity.")
                }
            } else {
                val success = cloudService.updateVault(currentId, payload)
                if (success) {
                    _cloudBackupState.value = CloudBackupState.Success("Data safely synchronized to Cloud Vault: $currentId")
                } else {
                    _cloudBackupState.value = CloudBackupState.Error("Failed to update Vault $currentId. Retrying or resetting ID may help.")
                }
            }
        }
    }

    /**
     * Restores (downloads) the database content of the given Cloud Vault ID
     */
    fun restoreFromCloud(vaultId: String) {
        if (vaultId.isBlank()) {
            _cloudBackupState.value = CloudBackupState.Error("Vault ID cannot be empty.")
            return
        }

        viewModelScope.launch {
            _cloudBackupState.value = CloudBackupState.Syncing
            val payload = cloudService.fetchVault(vaultId)
            if (payload != null) {
                try {
                    repository.restoreDatabase(
                        products = payload.products,
                        sales = payload.sales,
                        saleItems = payload.saleItems
                    )
                    setCloudVaultId(vaultId)
                    _cloudBackupState.value = CloudBackupState.Success("Database successfully restored! Loaded ${payload.products.size} products & ${payload.sales.size} sales.")
                } catch (e: Exception) {
                    Log.e("InventoryViewModel", "Local DB restore failed", e)
                    _cloudBackupState.value = CloudBackupState.Error("Restore retrieved but failed to write locally.")
                }
            } else {
                _cloudBackupState.value = CloudBackupState.Error("No backup found under Vault ID: $vaultId")
            }
        }
    }

    private fun buildBackupReportString(sales: List<Sale>, saleItems: List<SaleItem>): String {
        val totalRevenue = sales.sumOf { it.totalAmount }
        val totalProfit = sales.sumOf { it.totalProfit }
        val sb = StringBuilder()
        sb.append("=== PURBESH STATIONARY CLOUD REPORT ===\n")
        sb.append("Auto Sync Backup Timestamp: ${SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(Date())}\n")
        sb.append("Total Distinct Items Sold: ${saleItems.sumOf { it.quantity }}\n")
        sb.append("Total Revenue: NPR ${String.format("%.2f", totalRevenue)}\n")
        sb.append("Total Net Profit: NPR ${String.format("%.2f", totalProfit)}\n")
        sb.append("========================================\n")
        return sb.toString()
    }

    /**
     * Backs up the local database to the user's connected Google Drive account
     */
    fun backupToGoogleDrive(accessToken: String, customReport: String? = null) {
        viewModelScope.launch {
            _googleDriveBackupState.value = CloudBackupState.Syncing
            val products = allProducts.value
            val sales = allSales.value
            val saleItems = allSaleItems.value

            val payload = CloudBackupPayload(
                products = products,
                sales = sales,
                saleItems = saleItems,
                formattedReport = customReport ?: buildBackupReportString(sales, saleItems),
                backupTimestamp = System.currentTimeMillis()
            )

            var fileId = _googleDriveFileId.value
            if (fileId.isBlank()) {
                fileId = googleDriveService.findBackupFile(accessToken) ?: ""
            }

            if (fileId.isBlank()) {
                val newId = googleDriveService.createBackupFile(accessToken)
                if (newId != null) {
                    setGoogleDriveFileId(newId)
                    fileId = newId
                } else {
                    _googleDriveBackupState.value = CloudBackupState.Error("Could not create backup file on Google Drive.")
                    return@launch
                }
            }

            val success = googleDriveService.uploadBackupData(accessToken, fileId, payload)
            if (success) {
                _googleDriveBackupState.value = CloudBackupState.Success("Database successfully backed up to your Google Drive!")
            } else {
                _googleDriveBackupState.value = CloudBackupState.Error("Google Drive upload failed. Retrying may help.")
            }
        }
    }

    /**
     * Restores (downloads) database content from the connected Google Drive file
     */
    fun restoreFromGoogleDrive(accessToken: String) {
        viewModelScope.launch {
            _googleDriveBackupState.value = CloudBackupState.Syncing
            var fileId = _googleDriveFileId.value
            if (fileId.isBlank()) {
                fileId = googleDriveService.findBackupFile(accessToken) ?: ""
            }

            if (fileId.isBlank()) {
                _googleDriveBackupState.value = CloudBackupState.Error("No backup file 'Purbesh_Stationery_Backup.json' found on your Google Drive.")
                return@launch
            }

            val payload = googleDriveService.fetchBackupData(accessToken, fileId)
            if (payload != null) {
                try {
                    repository.restoreDatabase(
                        products = payload.products,
                        sales = payload.sales,
                        saleItems = payload.saleItems
                    )
                    setGoogleDriveFileId(fileId)
                    _googleDriveBackupState.value = CloudBackupState.Success("Database successfully restored from Google Drive! Loaded ${payload.products.size} products & ${payload.sales.size} sales.")
                } catch (e: Exception) {
                    Log.e("InventoryViewModel", "Google Drive restore failed", e)
                    _googleDriveBackupState.value = CloudBackupState.Error("Drive backup retrieved but failed to restore locally.")
                }
            } else {
                _googleDriveBackupState.value = CloudBackupState.Error("Failed to fetch backup file from Google Drive.")
            }
        }
    }

    /**
     * Triggers Google Drive auto-backup in the background silently
     */
    fun backupToGoogleDriveBackground() {
        val email = _googleAccountEmail.value
        if (email.isBlank()) return
        viewModelScope.launch {
            try {
                val token = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.google.android.gms.auth.GoogleAuthUtil.getToken(
                        getApplication(),
                        email,
                        "oauth2:https://www.googleapis.com/auth/drive.file"
                    )
                }
                if (token != null) {
                    val products = allProducts.value
                    val sales = allSales.value
                    val saleItems = allSaleItems.value

                    val payload = CloudBackupPayload(
                        products = products,
                        sales = sales,
                        saleItems = saleItems,
                        formattedReport = buildBackupReportString(sales, saleItems),
                        backupTimestamp = System.currentTimeMillis()
                    )

                    var fileId = _googleDriveFileId.value
                    if (fileId.isBlank()) {
                        fileId = googleDriveService.findBackupFile(token) ?: ""
                    }
                    if (fileId.isBlank()) {
                        fileId = googleDriveService.createBackupFile(token) ?: ""
                        if (fileId.isNotBlank()) {
                            setGoogleDriveFileId(fileId)
                        }
                    }
                    if (fileId.isNotBlank()) {
                        googleDriveService.uploadBackupData(token, fileId, payload)
                        Log.d("InventoryViewModel", "Seamless Google Drive background auto-backup completed.")
                    }
                }
            } catch (e: Exception) {
                Log.e("InventoryViewModel", "Google Drive background auto-backup error", e)
            }
        }
    }
}
