package com.example.ui

import android.app.Application
import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

sealed interface AiTipsState {
    object Idle : AiTipsState
    object Loading : AiTipsState
    data class Success(val tips: String) : AiTipsState
    data class Error(val message: String) : AiTipsState
}

typealias UserRole = com.example.data.UserRole
typealias User = com.example.data.User
typealias AuditLog = com.example.data.AuditLog
typealias Expense = com.example.data.Expense
typealias Customer = com.example.data.Customer

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
    private val sharedPrefs = application.getSharedPreferences("stationery_backup_prefs", Context.MODE_PRIVATE)

    private val _cloudVaultId = MutableStateFlow<String>(sharedPrefs.getString("cloud_vault_id", "") ?: "")
    val cloudVaultId: StateFlow<String> = _cloudVaultId.asStateFlow()

    val firebaseRealtimeManager = com.example.data.FirebaseRealtimeManager(application, sharedPrefs)

    private var textToSpeech: TextToSpeech? = null

    private val _appUpdateRequired = MutableStateFlow<String?>(null)
    val appUpdateRequired: StateFlow<String?> = _appUpdateRequired.asStateFlow()

    fun startFirebaseSync() {
        if (firebaseRealtimeManager.isSyncEnabled() && _cloudVaultId.value.isNotBlank()) {
            firebaseRealtimeManager.startListening(
                vaultId = _cloudVaultId.value,
                scope = viewModelScope,
                repository = repository,
                onSyncUpdate = { msg ->
                    Log.d("InventoryViewModel", "Firebase Sync Update: $msg")
                },
                onMetadataSync = { users, logs, expenses, customers, shopName, panNumber ->
                    viewModelScope.launch(Dispatchers.Main) {
                        if (users != null && users.isNotEmpty()) {
                            _usersListState.value = users
                            saveUsersList(users)
                            
                            // Sync current logged-in user permissions/role if updated in cloud
                            _loggedInUser.value?.let { loggedIn ->
                                users.find { u -> u.username.equals(loggedIn.username, ignoreCase = true) }?.let { updatedUser ->
                                    _loggedInUser.value = updatedUser
                                    _currentUserRole.value = updatedUser.role
                                }
                            }
                        }
                        if (logs != null && logs.isNotEmpty()) {
                            _auditLogs.value = logs
                            saveAuditLogs(logs)
                        }
                        if (expenses != null && expenses.isNotEmpty()) {
                            _expensesListState.value = expenses
                            saveExpenses(expenses)
                        }
                        if (customers != null && customers.isNotEmpty()) {
                            _customersListState.value = customers
                            saveCustomers(customers)
                        }
                        if (shopName != null && shopName.isNotBlank() && shopName != _shopName.value) {
                            sharedPrefs.edit().putString("business_shop_name", shopName).apply()
                            _shopName.value = shopName
                        }
                        if (panNumber != null && panNumber.isNotBlank() && panNumber != _panNumber.value) {
                            sharedPrefs.edit().putString("business_pan_number", panNumber).apply()
                            _panNumber.value = panNumber
                        }
                    }
                }
            )
        } else {
            firebaseRealtimeManager.stopListening()
        }
    }

    fun triggerFirebaseFullUpload(onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val products = repository.productDao.getAllProductsList()
            val sales = repository.saleDao.getAllSalesList()
            val saleItems = repository.saleDao.getAllSaleItemsList()
            firebaseRealtimeManager.pushAllData(
                vaultId = _cloudVaultId.value,
                products = products,
                sales = sales,
                saleItems = saleItems,
                users = _usersListState.value,
                auditLogs = _auditLogs.value,
                expenses = _expensesListState.value,
                customers = _customersListState.value,
                shopName = _shopName.value,
                panNumber = _panNumber.value,
                onComplete = onComplete
            )
        }
    }

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

    private val _appLanguage = MutableStateFlow(sharedPrefs.getString("app_language", "en") ?: "en")
    val appLanguage: StateFlow<String> = _appLanguage.asStateFlow()

    fun setAppLanguage(lang: String) {
        _appLanguage.value = lang
        sharedPrefs.edit().putString("app_language", lang).apply()
    }

    init {
        try {
            textToSpeech = TextToSpeech(application) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    textToSpeech?.language = Locale("ne", "NP")
                }
            }
        } catch (e: Exception) {
            Log.e("InventoryViewModel", "Error initializing TextToSpeech", e)
        }

        loadUsersAndLogs()
        populateDbIfEmpty()
        
        // Ensure default cloud vault ID exists for immediate sync
        if (_cloudVaultId.value.isBlank()) {
            sharedPrefs.edit().putString("cloud_vault_id", "default_shop_vault").apply()
            _cloudVaultId.value = "default_shop_vault"
        }
        
        startRealtimeSyncLoop()
        startFirebaseSync()
        checkAppVersion()
    }

    private fun populateDbIfEmpty() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentProducts = repository.productDao.getAllProductsList()
                if (currentProducts.isEmpty()) {
                    val sampleProducts = listOf(
                        Product(
                            name = "Pilot V5 Blue Liquid Ink Pen",
                            barcode = "89012341",
                            category = "Pens & Pencils",
                            costPrice = 40.0,
                            sellingPrice = 60.0,
                            stockQuantity = 25,
                            minStockThreshold = 8
                        ),
                        Product(
                            name = "Classmate A4 Notebook (120 Pages)",
                            barcode = "89012342",
                            category = "Notebooks",
                            costPrice = 35.0,
                            sellingPrice = 50.0,
                            stockQuantity = 3,
                            minStockThreshold = 5
                        ),
                        Product(
                            name = "Natraj HB Wooden Pencil (Pack of 10)",
                            barcode = "89012343",
                            category = "Pens & Pencils",
                            costPrice = 30.0,
                            sellingPrice = 45.0,
                            stockQuantity = 15,
                            minStockThreshold = 4
                        ),
                        Product(
                            name = "Parker Vector Rollerball Pen",
                            barcode = "89012344",
                            category = "Pens & Pencils",
                            costPrice = 220.0,
                            sellingPrice = 350.0,
                            stockQuantity = 8,
                            minStockThreshold = 2
                        ),
                        Product(
                            name = "Camel Watercolor Cake Set (12 Colors)",
                            barcode = "89012345",
                            category = "Art Supplies",
                            costPrice = 110.0,
                            sellingPrice = 160.0,
                            stockQuantity = 2,
                            minStockThreshold = 5
                        ),
                        Product(
                            name = "Fevicol MR Squeezy Adhesive Glue (50g)",
                            barcode = "89012346",
                            category = "Adhesives & Tape",
                            costPrice = 15.0,
                            sellingPrice = 25.0,
                            stockQuantity = 45,
                            minStockThreshold = 10
                        ),
                        Product(
                            name = "Cello Gripper Black Ball Pen",
                            barcode = "89012347",
                            category = "Pens & Pencils",
                            costPrice = 6.0,
                            sellingPrice = 10.0,
                            stockQuantity = 110,
                            minStockThreshold = 20
                        ),
                        Product(
                            name = "Kangaro Heavy Duty Stapler No.10",
                            barcode = "89012348",
                            category = "Office Stationery",
                            costPrice = 80.0,
                            sellingPrice = 120.0,
                            stockQuantity = 12,
                            minStockThreshold = 3
                        )
                    )
                    repository.insertProducts(sampleProducts)
                    Log.d("InventoryViewModel", "Database successfully prepopulated with ${sampleProducts.size} stationery items.")
                }
            } catch (e: Exception) {
                Log.e("InventoryViewModel", "Error populating database", e)
            }
        }
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
            val vaultId = _cloudVaultId.value
            if (vaultId.isNotBlank()) {
                firebaseRealtimeManager.pushUsersList(vaultId, list)
            }
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

    private val _autoBackupEnabled = MutableStateFlow<Boolean>(sharedPrefs.getBoolean("auto_backup_enabled", true))
    val autoBackupEnabled: StateFlow<Boolean> = _autoBackupEnabled.asStateFlow()

    private val _shopName = MutableStateFlow<String>(sharedPrefs.getString("business_shop_name", "Purbesh Stationery") ?: "Purbesh Stationery")
    val shopName: StateFlow<String> = _shopName.asStateFlow()

    private val _panNumber = MutableStateFlow<String>(sharedPrefs.getString("business_pan_number", "") ?: "")
    val panNumber: StateFlow<String> = _panNumber.asStateFlow()

    fun updateShopName(name: String) {
        sharedPrefs.edit().putString("business_shop_name", name).apply()
        _shopName.value = name
        logAction("Changed Shop Name", "New Name: $name")
        triggerAutoCloudSync()
        val vaultId = _cloudVaultId.value
        if (vaultId.isNotBlank()) {
            firebaseRealtimeManager.pushBusinessInfo(vaultId, name, _panNumber.value)
        }
    }

    fun updatePanNumber(pan: String) {
        sharedPrefs.edit().putString("business_pan_number", pan).apply()
        _panNumber.value = pan
        logAction("Changed PAN Number", "New PAN: $pan")
        triggerAutoCloudSync()
        val vaultId = _cloudVaultId.value
        if (vaultId.isNotBlank()) {
            firebaseRealtimeManager.pushBusinessInfo(vaultId, _shopName.value, pan)
        }
    }

    fun setCloudVaultId(vaultId: String) {
        sharedPrefs.edit().putString("cloud_vault_id", vaultId).apply()
        _cloudVaultId.value = vaultId
        if (vaultId.isNotBlank()) {
            viewModelScope.launch {
                try {
                    pullAndMergeWithCloud(vaultId)
                } catch (e: java.lang.Exception) {
                    Log.e("InventoryViewModel", "Error doing initial pull for Vault ID", e)
                }
            }
        }
        startRealtimeSyncLoop()
        startFirebaseSync()
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
        triggerAutoCloudSync()
    }

    fun saveExpenses(list: List<Expense>) {
        try {
            val json = expenseListAdapter.toJson(list)
            sharedPrefs.edit().putString("expenses_list_json", json).apply()
            val vaultId = _cloudVaultId.value
            if (vaultId.isNotBlank()) {
                firebaseRealtimeManager.pushExpenses(vaultId, list)
            }
        } catch (e: Exception) {
            Log.e("InventoryViewModel", "Error saving expenses", e)
        }
    }

    fun saveCustomers(list: List<Customer>) {
        try {
            val json = customerListAdapter.toJson(list)
            sharedPrefs.edit().putString("customers_list_json", json).apply()
            val vaultId = _cloudVaultId.value
            if (vaultId.isNotBlank()) {
                firebaseRealtimeManager.pushCustomers(vaultId, list)
            }
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
        triggerAutoCloudSync()
    }

    fun deleteExpense(id: String) {
        val list = _expensesListState.value.toMutableList()
        list.removeAll { it.id == id }
        _expensesListState.value = list
        saveExpenses(list)
        logAction("Deleted Expense", "ID: $id")
        triggerAutoCloudSync()
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
        triggerAutoCloudSync()
    }

    fun deleteCustomer(id: String) {
        val list = _customersListState.value.toMutableList()
        list.removeAll { it.id == id }
        _customersListState.value = list
        saveCustomers(list)
        logAction("Deleted Customer", "ID: $id")
        triggerAutoCloudSync()
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
            triggerAutoCloudSync()
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
        triggerAutoCloudSync()
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

            val maxDiscount = totalProfit.coerceAtLeast(0.0)
            val safeDiscount = discountAmount.coerceAtMost(maxDiscount)

            val actualTotalAmount = (totalAmount - safeDiscount).coerceAtLeast(0.0)
            val actualTotalProfit = totalProfit - safeDiscount

            val activeUser = _loggedInUser.value?.fullName ?: "Cashier/Staff"
            val sale = Sale(
                timestamp = timestamp,
                totalAmount = actualTotalAmount,
                totalProfit = actualTotalProfit,
                paymentMode = paymentMode,
                soldBy = activeUser,
                customerPhone = customerPhone
            )

            val success = repository.executeSale(sale, saleItems)
            if (success) {
                // If checking out as store credit / debt, adjust the selected customer's credit / balance
                if (customerId != null && (paymentMode.equals("Credit", ignoreCase = true) || paymentMode.contains("Credit", ignoreCase = true) || paymentMode.contains("Store Credit", ignoreCase = true) || paymentMode.contains("Debt", ignoreCase = true) || paymentMode.contains("Split", ignoreCase = true))) {
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

                logAction("Completed Sale", "Total: NPR ${String.format("%.2f", actualTotalAmount)} (Discount: NPR ${String.format("%.2f", safeDiscount)}), Mode: $paymentMode")
                // Generate a formatted receipt for print/sharing
                val receipt = buildFormattedReceipt(sale, saleItems, customerPhone, safeDiscount)
                _checkoutReceipt.value = receipt
                _cart.value = emptyMap() // clear cart on success

                // Announce total price in Nepali
                announceTotalInNepali(actualTotalAmount)

                // Trigger automatic SMS receipt
                if (customerPhone.isNotBlank()) {
                    val smsText = "Thank you for shopping at ${_shopName.value}! Invoice #${1000 + sale.id} generated. Total: NPR ${String.format("%.2f", actualTotalAmount)} (Discount: NPR ${String.format("%.2f", safeDiscount)}). Mode: $paymentMode."
                    sendSms(customerPhone, smsText, "Sale Receipt")
                }

                // Real-time Cloud Sync & Backup on successful transaction
                if (_cloudVaultId.value.isNotBlank()) {
                    backupToCloud()
                }

                // Push to Firebase Realtime Database
                firebaseRealtimeManager.pushSale(_cloudVaultId.value, sale, saleItems)
                // Push updated product stock quantities to Firebase
                saleItems.forEach { item ->
                    viewModelScope.launch {
                        val updatedProd = repository.getProductById(item.productId)
                        if (updatedProd != null) {
                            firebaseRealtimeManager.pushProduct(_cloudVaultId.value, updatedProd)
                        }
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
        if (_panNumber.value.isNotBlank()) {
            sb.append("       PAN No: ${_panNumber.value}      \n")
        }
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
            val rowId = repository.insertProduct(product)
            val savedProduct = product.copy(id = rowId.toInt())
            firebaseRealtimeManager.pushProduct(_cloudVaultId.value, savedProduct)
            logAction("Added Product", "Name: $name, Barcode: $barcode, Qty: $stock")
            triggerAutoCloudSync()
        }
    }

    // Update Product in Inventory
    fun updateProductInInventory(product: Product) {
        viewModelScope.launch {
            repository.updateProduct(product)
            firebaseRealtimeManager.pushProduct(_cloudVaultId.value, product)
            logAction("Updated Product", "Name: ${product.name}, Stock: ${product.stockQuantity}")
            triggerAutoCloudSync()
        }
    }

    // Delete Product from Inventory
    fun deleteProductFromInventory(product: Product) {
        viewModelScope.launch {
            repository.deleteProduct(product)
            firebaseRealtimeManager.deleteProduct(_cloudVaultId.value, product)
            logAction("Deleted Product", "Name: ${product.name}")
            triggerAutoCloudSync()
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

    private var syncJob: kotlinx.coroutines.Job? = null

    fun startRealtimeSyncLoop() {
        syncJob?.cancel()
        syncJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                try {
                    val vaultId = _cloudVaultId.value
                    if (vaultId.isNotBlank()) {
                        pullAndMergeWithCloud(vaultId)
                    }
                } catch (e: Exception) {
                    Log.e("InventoryViewModel", "Error in real-time sync loop", e)
                }
                kotlinx.coroutines.delay(6000) // Poll every 6 seconds
            }
        }
    }

    suspend fun pullAndMergeWithCloud(vaultId: String) = withContext(Dispatchers.IO) {
        val payload = cloudService.fetchVault(vaultId)
        if (payload != null) {
            val lastSync = sharedPrefs.getLong("last_sync_timestamp", 0L)
            if (payload.backupTimestamp > lastSync) {
                try {
                    repository.mergeAndSync(
                        localProducts = allProducts.value,
                        localSales = allSales.value,
                        localSaleItems = allSaleItems.value,
                        cloudProducts = payload.products,
                        cloudSales = payload.sales,
                        cloudSaleItems = payload.saleItems
                    )
                    payload.users?.let {
                        _usersListState.value = it
                        saveUsersList(it)
                        
                        // Sync current logged-in user permissions/role if updated in cloud
                        _loggedInUser.value?.let { loggedIn ->
                            it.find { u -> u.username.equals(loggedIn.username, ignoreCase = true) }?.let { updatedUser ->
                                _loggedInUser.value = updatedUser
                                _currentUserRole.value = updatedUser.role
                            }
                        }
                    }
                    payload.auditLogs?.let {
                        _auditLogs.value = it
                        saveAuditLogs(it)
                    }
                    payload.expenses?.let {
                        _expensesListState.value = it
                        saveExpenses(it)
                    }
                    payload.customers?.let {
                        _customersListState.value = it
                        saveCustomers(it)
                    }
                    
                    sharedPrefs.edit().putLong("last_sync_timestamp", payload.backupTimestamp).apply()
                    Log.d("InventoryViewModel", "Real-time sync: Database merged with cloud vault $vaultId")
                } catch (e: Exception) {
                    Log.e("InventoryViewModel", "Failed to merge cloud database on real-time sync", e)
                }
            }
        }
    }

    fun triggerAutoCloudSync() {
        if (_cloudVaultId.value.isNotBlank()) {
            viewModelScope.launch {
                kotlinx.coroutines.delay(300)
                backupToCloud()
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
            val timestampToSave = System.currentTimeMillis()

            val payload = CloudBackupPayload(
                products = products,
                sales = sales,
                saleItems = saleItems,
                formattedReport = customReport ?: buildBackupReportString(sales, saleItems),
                backupTimestamp = timestampToSave,
                storeName = _shopName.value,
                panNumber = _panNumber.value,
                users = _usersListState.value,
                auditLogs = _auditLogs.value,
                expenses = _expensesListState.value,
                customers = _customersListState.value
            )

            val currentId = _cloudVaultId.value
            if (currentId.isBlank()) {
                val newId = cloudService.createNewVault(payload)
                if (newId != null) {
                    setCloudVaultId(newId)
                    sharedPrefs.edit().putLong("last_sync_timestamp", timestampToSave).apply()
                    _cloudBackupState.value = CloudBackupState.Success("Cloud Storage Active! Saved under Vault ID: $newId")
                } else {
                    _cloudBackupState.value = CloudBackupState.Error("Cloud connection failed. Check your internet connectivity.")
                }
            } else {
                val success = cloudService.updateVault(currentId, payload)
                if (success) {
                    sharedPrefs.edit().putLong("last_sync_timestamp", timestampToSave).apply()
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
                    payload.users?.let {
                        _usersListState.value = it
                        saveUsersList(it)
                    }
                    payload.auditLogs?.let {
                        _auditLogs.value = it
                        saveAuditLogs(it)
                    }
                    payload.expenses?.let {
                        _expensesListState.value = it
                        saveExpenses(it)
                    }
                    payload.customers?.let {
                        _customersListState.value = it
                        saveCustomers(it)
                    }
                    
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

    override fun onCleared() {
        super.onCleared()
        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        } catch (e: Exception) {
            Log.e("InventoryViewModel", "Error shutting down TextToSpeech", e)
        }
    }

    fun announceTotalInNepali(amount: Double) {
        val rupees = amount.toInt()
        val paise = ((amount - rupees) * 100).toInt()
        val speechText = if (paise > 0) {
            "कुल जम्मा ${convertToNepaliDigits(rupees.toString())} रुपैयाँ र ${convertToNepaliDigits(paise.toString())} पैसा भयो।"
        } else {
            "कुल जम्मा ${convertToNepaliDigits(rupees.toString())} रुपैयाँ भयो।"
        }
        
        textToSpeech?.let { tts ->
            try {
                tts.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, "POS_TOTAL")
            } catch (e: Exception) {
                Log.e("TTS", "Failed to speak total amount", e)
            }
        }
    }

    private fun convertToNepaliDigits(input: String): String {
        val nepaliDigits = mapOf(
            '0' to '०', '1' to '१', '2' to '२', '3' to '३', '4' to '४',
            '5' to '५', '6' to '६', '7' to '७', '8' to '८', '9' to '९'
        )
        return input.map { nepaliDigits[it] ?: it }.joinToString("")
    }

    fun dismissUpdateDialog() {
        _appUpdateRequired.value = null
    }

    fun checkAppVersion() {
        viewModelScope.launch {
            try {
                val db = firebaseRealtimeManager.getDatabaseInstance() ?: return@launch
                val ref = db.getReference("current_version")
                ref.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val rawValue = snapshot.value
                        val remoteVersion = when (rawValue) {
                            is String -> rawValue.trim()
                            is Number -> rawValue.toString()
                            null -> null
                            else -> rawValue.toString().trim()
                        }
                        if (remoteVersion != null) {
                            val currentVersion = try {
                                val packageInfo = getApplication<Application>().packageManager.getPackageInfo(getApplication<Application>().packageName, 0)
                                packageInfo.versionName ?: "1.0.0"
                            } catch (e: Exception) {
                                "1.0.0"
                            }
                            Log.d("InventoryViewModel", "Version Check: App version = $currentVersion, Firebase version = $remoteVersion")
                            if (isUpdateRequired(currentVersion, remoteVersion)) {
                                _appUpdateRequired.value = remoteVersion
                            }
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e("FirebaseVersionCheck", "Version check cancelled: ${error.message}")
                    }
                })
            } catch (e: Exception) {
                Log.e("FirebaseVersionCheck", "Error checking version", e)
            }
        }
    }

    private fun isUpdateRequired(current: String, remote: String): Boolean {
        val currentClean = current.trim().removePrefix("v").removePrefix("V")
        val remoteClean = remote.trim().removePrefix("v").removePrefix("V")
        
        if (currentClean == remoteClean) return false
        
        val currentParts = currentClean.split("[^0-9]".toRegex()).filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }
        val remoteParts = remoteClean.split("[^0-9]".toRegex()).filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }
        
        val maxLength = maxOf(currentParts.size, remoteParts.size)
        for (i in 0 until maxLength) {
            val currVal = currentParts.getOrElse(i) { 0 }
            val remoteVal = remoteParts.getOrElse(i) { 0 }
            if (remoteVal > currVal) return true
            if (currVal > remoteVal) return false
        }
        return false
    }
}
