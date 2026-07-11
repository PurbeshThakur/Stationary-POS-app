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

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class User(
    val username: String,
    val role: UserRole,
    val pin: String,
    val fullName: String,
    val avatarColor: Long,
    val isEnabled: Boolean = true,
    val canManageInventory: Boolean = true,
    val canViewReports: Boolean = true,
    val canPerformSale: Boolean = true,
    val canUseAiAdvisor: Boolean = true,
    val isSuperAdmin: Boolean = false
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class AuditLog(
    val timestamp: Long,
    val username: String,
    val fullName: String,
    val action: String,
    val details: String = ""
)

data class SmsAlert(
    val id: Int,
    val recipient: String,
    val message: String,
    val type: String, // "Sale Receipt" or "Offer Alert"
    val timestamp: Long
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class Expense(
    val id: String,
    val title: String,
    val category: String,
    val amount: Double,
    val timestamp: Long,
    val description: String = ""
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class Customer(
    val id: String,
    val name: String,
    val phone: String,
    val storeCredit: Double = 0.0
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

    private val moshi = com.squareup.moshi.Moshi.Builder()
        .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
        .build()

    private val userListAdapter = moshi.adapter<List<User>>(
        com.squareup.moshi.Types.newParameterizedType(List::class.java, User::class.java)
    )

    private val auditLogAdapter = moshi.adapter<List<AuditLog>>(
        com.squareup.moshi.Types.newParameterizedType(List::class.java, AuditLog::class.java)
    )

    private val expenseListAdapter = moshi.adapter<List<Expense>>(
        com.squareup.moshi.Types.newParameterizedType(List::class.java, Expense::class.java)
    )

    private val customerListAdapter = moshi.adapter<List<Customer>>(
        com.squareup.moshi.Types.newParameterizedType(List::class.java, Customer::class.java)
    )

    private val defaultUsers = listOf(
        User("admin", UserRole.ADMIN, "1234", "System Administrator", 0xFF6200EE, isSuperAdmin = true),
        User("purbesh", UserRole.ADMIN, "1111", "Purbesh (Admin)", 0xFF00C853, isSuperAdmin = true),
        User("staff", UserRole.CASHIER, "0000", "Stationery Cashier", 0xFFFFAB00, canViewReports = false, canManageInventory = false, canUseAiAdvisor = false),
        User("anjali", UserRole.CASHIER, "2222", "Anjali (Staff)", 0xFF00B0FF, canViewReports = false, canManageInventory = false, canUseAiAdvisor = false)
    )

    private val _usersListState = MutableStateFlow<List<User>>(emptyList())
    val usersListState: StateFlow<List<User>> = _usersListState.asStateFlow()

    private val _auditLogs = MutableStateFlow<List<AuditLog>>(emptyList())
    val auditLogs: StateFlow<List<AuditLog>> = _auditLogs.asStateFlow()

    private val _expensesListState = MutableStateFlow<List<Expense>>(emptyList())
    val expensesListState: StateFlow<List<Expense>> = _expensesListState.asStateFlow()

    private val _customersListState = MutableStateFlow<List<Customer>>(emptyList())
    val customersListState: StateFlow<List<Customer>> = _customersListState.asStateFlow()

    // Custom receipt settings
    private val _receiptHeaderNote = MutableStateFlow(sharedPrefs.getString("receipt_header_note", "Your Premium Writing Hub") ?: "Your Premium Writing Hub")
    val receiptHeaderNote: StateFlow<String> = _receiptHeaderNote.asStateFlow()

    private val _receiptFooterNote = MutableStateFlow(sharedPrefs.getString("receipt_footer_note", "Thank you for shopping!") ?: "Thank you for shopping!")
    val receiptFooterNote: StateFlow<String> = _receiptFooterNote.asStateFlow()

    private val _receiptShowBarcode = MutableStateFlow(sharedPrefs.getBoolean("receipt_show_barcode", true))
    val receiptShowBarcode: StateFlow<Boolean> = _receiptShowBarcode.asStateFlow()

    private val _receiptShowDiscountBreakdown = MutableStateFlow(sharedPrefs.getBoolean("receipt_show_discount_breakdown", true))
    val receiptShowDiscountBreakdown: StateFlow<Boolean> = _receiptShowDiscountBreakdown.asStateFlow()

    init {
        loadUsersAndLogs()
    }

    private fun loadUsersAndLogs() {
        val usersJson = sharedPrefs.getString("users_list_json", null)
        if (usersJson != null) {
            try {
                val list = userListAdapter.fromJson(usersJson)
                if (list != null) {
                    _usersListState.value = list
                } else {
                    _usersListState.value = defaultUsers
                }
            } catch (e: Exception) {
                Log.e("InventoryViewModel", "Error parsing users JSON", e)
                _usersListState.value = defaultUsers
            }
        } else {
            _usersListState.value = defaultUsers
            saveUsersList(defaultUsers)
        }

        val logsJson = sharedPrefs.getString("audit_logs_json", null)
        if (logsJson != null) {
            try {
                val list = auditLogAdapter.fromJson(logsJson)
                if (list != null) {
                    _auditLogs.value = list
                }
            } catch (e: Exception) {
                Log.e("InventoryViewModel", "Error parsing audit logs JSON", e)
            }
        }

        val expensesJson = sharedPrefs.getString("expenses_list_json", null)
        if (expensesJson != null) {
            try {
                val list = expenseListAdapter.fromJson(expensesJson)
                if (list != null) {
                    _expensesListState.value = list
                }
            } catch (e: Exception) {
                Log.e("InventoryViewModel", "Error parsing expenses JSON", e)
            }
        } else {
            // Default sample expenses
            val samples = listOf(
                Expense(id = UUID.randomUUID().toString(), title = "Office Tea & Coffee", category = "Food & Refreshments", amount = 120.0, timestamp = System.currentTimeMillis() - 86400000, description = "Weekly tea purchase for staff"),
                Expense(id = UUID.randomUUID().toString(), title = "Electricity Bill June", category = "Utilities", amount = 2400.0, timestamp = System.currentTimeMillis() - 172800000, description = "NEA payment")
            )
            _expensesListState.value = samples
            saveExpenses(samples)
        }

        val customersJson = sharedPrefs.getString("customers_list_json", null)
        if (customersJson != null) {
            try {
                val list = customerListAdapter.fromJson(customersJson)
                if (list != null) {
                    _customersListState.value = list
                }
            } catch (e: Exception) {
                Log.e("InventoryViewModel", "Error parsing customers JSON", e)
            }
        } else {
            // Default sample customers
            val samples = listOf(
                Customer(id = UUID.randomUUID().toString(), name = "Kiran Sharma", phone = "9841234567", storeCredit = 1500.0),
                Customer(id = UUID.randomUUID().toString(), name = "Ramesh Adhikari", phone = "9851122334", storeCredit = 0.0)
            )
            _customersListState.value = samples
            saveCustomers(samples)
        }
    }

    private fun saveUsersList(list: List<User>) {
        try {
            val json = userListAdapter.toJson(list)
            sharedPrefs.edit().putString("users_list_json", json).apply()
        } catch (e: Exception) {
            Log.e("InventoryViewModel", "Error saving users list", e)
        }
    }

    private fun saveAuditLogs(list: List<AuditLog>) {
        try {
            val json = auditLogAdapter.toJson(list)
            sharedPrefs.edit().putString("audit_logs_json", json).apply()
        } catch (e: Exception) {
            Log.e("InventoryViewModel", "Error saving audit logs", e)
        }
    }

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

    private val _shopName = MutableStateFlow<String>(sharedPrefs.getString("business_shop_name", "Purbesh Stationery") ?: "Purbesh Stationery")
    val shopName: StateFlow<String> = _shopName.asStateFlow()

    fun updateShopName(name: String) {
        sharedPrefs.edit().putString("business_shop_name", name).apply()
        _shopName.value = name
        logAction("Changed Shop Name", "New Name: $name")
    }

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

    val usersList: List<User> get() = _usersListState.value

    private val _loggedInUser = MutableStateFlow<User?>(null)
    val loggedInUser: StateFlow<User?> = _loggedInUser.asStateFlow()

    // User Role Access Control State
    private val _currentUserRole = MutableStateFlow(UserRole.CASHIER) // Default to cashier to enforce restriction initially
    val currentUserRole: StateFlow<UserRole> = _currentUserRole.asStateFlow()

    fun login(username: String, pin: String): String {
        val user = usersList.find { it.username.equals(username, ignoreCase = true) }
        if (user == null || user.pin != pin) {
            return "INVALID_PIN"
        }
        if (!user.isEnabled) {
            return "DISABLED"
        }
        _loggedInUser.value = user
        _currentUserRole.value = user.role
        logAction("Logged In", "Success")
        return "SUCCESS"
    }

    fun logout() {
        logAction("Logged Out", "Success")
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

    fun logAction(action: String, details: String = "") {
        val user = _loggedInUser.value ?: return
        val newLog = AuditLog(
            timestamp = System.currentTimeMillis(),
            username = user.username,
            fullName = user.fullName,
            action = action,
            details = details
        )
        val currentLogs = _auditLogs.value.toMutableList()
        currentLogs.add(0, newLog)
        val trimmed = if (currentLogs.size > 500) currentLogs.take(500) else currentLogs
        _auditLogs.value = trimmed
        saveAuditLogs(trimmed)
    }

    fun saveExpenses(list: List<Expense>) {
        try {
            val json = expenseListAdapter.toJson(list)
            sharedPrefs.edit().putString("expenses_list_json", json).apply()
        } catch (e: Exception) {
            Log.e("InventoryViewModel", "Error saving expenses", e)
        }
    }

    fun saveCustomers(list: List<Customer>) {
        try {
            val json = customerListAdapter.toJson(list)
            sharedPrefs.edit().putString("customers_list_json", json).apply()
        } catch (e: Exception) {
            Log.e("InventoryViewModel", "Error saving customers", e)
        }
    }

    fun updateReceiptSettings(header: String, footer: String, showBarcode: Boolean, showDiscount: Boolean) {
        sharedPrefs.edit()
            .putString("receipt_header_note", header)
            .putString("receipt_footer_note", footer)
            .putBoolean("receipt_show_barcode", showBarcode)
            .putBoolean("receipt_show_discount_breakdown", showDiscount)
            .apply()
        _receiptHeaderNote.value = header
        _receiptFooterNote.value = footer
        _receiptShowBarcode.value = showBarcode
        _receiptShowDiscountBreakdown.value = showDiscount
        logAction("Customized Receipt settings", "Updated header, footer, options")
    }

    fun addExpense(expense: Expense) {
        val list = _expensesListState.value.toMutableList()
        list.add(0, expense)
        _expensesListState.value = list
        saveExpenses(list)
        logAction("Added Expense", "Amount: NPR ${expense.amount}, Category: ${expense.category}")
    }

    fun deleteExpense(id: String) {
        val list = _expensesListState.value.toMutableList()
        list.removeAll { it.id == id }
        _expensesListState.value = list
        saveExpenses(list)
        logAction("Deleted Expense", "ID: $id")
    }

    fun addOrUpdateCustomer(customer: Customer) {
        val list = _customersListState.value.toMutableList()
        val index = list.indexOfFirst { it.id == customer.id || (it.phone == customer.phone && customer.phone.isNotBlank()) }
        if (index != -1) {
            list[index] = customer
            logAction("Updated Customer", "Name: ${customer.name}, Balance: NPR ${customer.storeCredit}")
        } else {
            list.add(customer)
            logAction("Added Customer Profile", "Name: ${customer.name}, Phone: ${customer.phone}")
        }
        _customersListState.value = list
        saveCustomers(list)
    }

    fun deleteCustomer(id: String) {
        val list = _customersListState.value.toMutableList()
        list.removeAll { it.id == id }
        _customersListState.value = list
        saveCustomers(list)
        logAction("Deleted Customer", "ID: $id")
    }

    fun adjustStoreCredit(customerId: String, amount: Double) {
        val list = _customersListState.value.toMutableList()
        val index = list.indexOfFirst { it.id == customerId }
        if (index != -1) {
            val updated = list[index].copy(storeCredit = (list[index].storeCredit + amount).coerceAtLeast(0.0))
            list[index] = updated
            _customersListState.value = list
            saveCustomers(list)
            logAction("Adjusted Customer Credit", "Customer: ${updated.name}, Change: $amount, New Balance: ${updated.storeCredit}")
        }
    }

    fun addOrUpdateUser(user: User) {
        val current = _usersListState.value.toMutableList()
        val index = current.indexOfFirst { it.username.equals(user.username, ignoreCase = true) }
        if (index != -1) {
            current[index] = user
            logAction("Updated User Profile", "User: ${user.username}, Role: ${user.role.label}")
        } else {
            current.add(user)
            logAction("Created User Profile", "User: ${user.username}, Role: ${user.role.label}")
        }
        _usersListState.value = current
        saveUsersList(current)

        // If the logged-in user edited themselves, update logged-in user state immediately
        val loggedIn = _loggedInUser.value
        if (loggedIn != null && loggedIn.username.equals(user.username, ignoreCase = true)) {
            _loggedInUser.value = user
            _currentUserRole.value = user.role
        }
    }

    fun deleteUser(username: String) {
        val current = _usersListState.value.toMutableList()
        val toRemove = current.find { it.username.equals(username, ignoreCase = true) }
        if (toRemove != null) {
            current.remove(toRemove)
            _usersListState.value = current
            saveUsersList(current)
            logAction("Deleted User Profile", "User: $username")
        }
    }

    fun clearAuditLogs() {
        _auditLogs.value = emptyList()
        saveAuditLogs(emptyList())
        logAction("Cleared System Audit Logs", "Done")
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
        val cleanedBarcode = barcode.trim().removeSurrounding("*").trim()
        viewModelScope.launch {
            // 1. Try exact match with cleaned barcode
            var product = repository.getProductByBarcode(cleanedBarcode)

            // 2. If not found, try case-insensitive and trimmed fallback across all products
            if (product == null) {
                val allProds = allProducts.value
                product = allProds.find { 
                    it.barcode.trim().removeSurrounding("*").trim().lowercase() == cleanedBarcode.lowercase() 
                }
            }

            // 3. If still not found, try matching as a substring (to handle scanner prefixes/suffixes or leading/trailing zeros)
            if (product == null) {
                val allProds = allProducts.value
                product = allProds.find {
                    val target = it.barcode.trim().removeSurrounding("*").trim().lowercase()
                    val scanned = cleanedBarcode.lowercase()
                    target.isNotEmpty() && (scanned.contains(target) || target.contains(scanned))
                }
            }

            // 4. Try exact match on original raw barcode just in case
            if (product == null) {
                product = repository.getProductByBarcode(barcode)
            }

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
    fun performCheckout(paymentMode: String, customerPhone: String, discountAmount: Double = 0.0, customerId: String? = null) {
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

            val actualTotalAmount = (totalAmount - discountAmount).coerceAtLeast(0.0)
            val actualTotalProfit = totalProfit - discountAmount

            val sale = Sale(
                timestamp = timestamp,
                totalAmount = actualTotalAmount,
                totalProfit = actualTotalProfit,
                paymentMode = paymentMode
            )

            val success = repository.executeSale(sale, saleItems)
            if (success) {
                // If checking out as store credit / debt, adjust the selected customer's credit / balance
                if (customerId != null && (paymentMode.contains("Store Credit", ignoreCase = true) || paymentMode.contains("Debt", ignoreCase = true) || paymentMode.contains("Split", ignoreCase = true))) {
                    var debtAmount = actualTotalAmount
                    if (paymentMode.contains("Split", ignoreCase = true)) {
                        // Extract Store Credit split amount if any
                        // Format: Split: Cash (NPR 500) + Store Credit (NPR 1000)
                        val regex = "Store Credit \\(NPR (\\d+(?:\\.\\d+)?)\\)".toRegex()
                        val match = regex.find(paymentMode)
                        if (match != null) {
                            debtAmount = match.groupValues[1].toDoubleOrNull() ?: actualTotalAmount
                        }
                    }
                    adjustStoreCredit(customerId, debtAmount)
                }

                logAction("Completed Sale", "Total: NPR ${String.format("%.2f", actualTotalAmount)} (Discount: NPR ${String.format("%.2f", discountAmount)}), Mode: $paymentMode")
                // Generate a formatted receipt for print/sharing
                val receipt = buildFormattedReceipt(sale, saleItems, customerPhone, discountAmount)
                _checkoutReceipt.value = receipt
                _cart.value = emptyMap() // clear cart on success

                // Trigger automatic SMS receipt
                if (customerPhone.isNotBlank()) {
                    val smsText = "Thank you for shopping at ${_shopName.value}! Invoice #${1000 + sale.id} generated. Total: NPR ${String.format("%.2f", actualTotalAmount)} (Discount: NPR ${String.format("%.2f", discountAmount)}). Mode: $paymentMode."
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

    private fun buildFormattedReceipt(sale: Sale, items: List<SaleItem>, phone: String, discountAmount: Double): String {
        val sdf = SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.getDefault())
        val dateStr = sdf.format(Date(sale.timestamp))
        val sb = StringBuilder()
        val shopHeader = _shopName.value.uppercase().padEnd(23).take(23)
        sb.append("===============================\n")
        sb.append("       $shopHeader      \n")
        sb.append("    ${_receiptHeaderNote.value}    \n")
        sb.append("===============================\n")
        sb.append("Date: $dateStr\n")
        if (phone.isNotBlank()) {
            sb.append("Customer Mob: $phone\n")
        }
        sb.append("-------------------------------\n")
        sb.append(String.format("%-18s %3s %8s\n", "Item Description", "Qty", "Price"))
        sb.append("-------------------------------\n")
        var subTotal = 0.0
        for (item in items) {
            val nameTrunc = if (item.productName.length > 17) item.productName.substring(0, 15) + ".." else item.productName
            val lineTotal = item.sellingPrice * item.quantity
            subTotal += lineTotal
            sb.append(String.format("%-18s %3d %8.2f\n", nameTrunc, item.quantity, lineTotal))
        }
        sb.append("-------------------------------\n")
        if (discountAmount > 0.0 && _receiptShowDiscountBreakdown.value) {
            sb.append(String.format("%-22s %8.2f\n", "SUBTOTAL:", subTotal))
            sb.append(String.format("%-22s %8.2f\n", "DISCOUNT:", discountAmount))
        }
        sb.append(String.format("%-22s %8.2f\n", "TOTAL AMOUNT:", sale.totalAmount))
        sb.append("Payment Mode: ${sale.paymentMode}\n")
        if (_receiptShowBarcode.value && items.isNotEmpty()) {
            sb.append("-------------------------------\n")
            sb.append("Barcode Ref: ${items.first().barcode}\n")
        }
        sb.append("===============================\n")
        sb.append("     ${_receiptFooterNote.value} \n")
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
            logAction("Added Product", "Name: $name, Barcode: $barcode, Qty: $stock")
        }
    }

    // Update Product in Inventory
    fun updateProductInInventory(product: Product) {
        viewModelScope.launch {
            repository.updateProduct(product)
            logAction("Updated Product", "Name: ${product.name}, Stock: ${product.stockQuantity}")
        }
    }

    // Delete Product from Inventory
    fun deleteProductFromInventory(product: Product) {
        viewModelScope.launch {
            repository.deleteProduct(product)
            logAction("Deleted Product", "Name: ${product.name}")
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
                backupTimestamp = System.currentTimeMillis(),
                storeName = _shopName.value
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
        sb.append("=== ${_shopName.value.uppercase()} CLOUD REPORT ===\n")
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
                backupTimestamp = System.currentTimeMillis(),
                storeName = _shopName.value
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
                        backupTimestamp = System.currentTimeMillis(),
                        storeName = _shopName.value
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
