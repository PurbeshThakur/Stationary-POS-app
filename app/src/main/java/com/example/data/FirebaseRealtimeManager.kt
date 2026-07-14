package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.database.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface FirebaseSyncState {
    object Idle : FirebaseSyncState
    object Connecting : FirebaseSyncState
    data class Connected(val databaseUrl: String) : FirebaseSyncState
    data class Error(val message: String) : FirebaseSyncState
}

class FirebaseRealtimeManager(
    private val context: Context,
    private val sharedPrefs: SharedPreferences
) {
    private val _syncState = MutableStateFlow<FirebaseSyncState>(FirebaseSyncState.Idle)
    val syncState: StateFlow<FirebaseSyncState> = _syncState.asStateFlow()

    private var firebaseApp: FirebaseApp? = null
    private var database: FirebaseDatabase? = null
    private var rootRef: DatabaseReference? = null
    private var valueListener: ValueEventListener? = null
    private var activeVaultId: String = ""

    init {
        // Automatically attempt to initialize if credentials exist and sync is enabled
        if (isSyncEnabled()) {
            initFirebase()
        }
    }

    fun isSyncEnabled(): Boolean {
        return sharedPrefs.getBoolean("firebase_sync_enabled", true)
    }

    fun setSyncEnabled(enabled: Boolean) {
        sharedPrefs.edit().putBoolean("firebase_sync_enabled", enabled).apply()
        if (enabled) {
            initFirebase()
        } else {
            disconnect()
        }
    }

    private fun getResString(name: String): String {
        var resId = context.resources.getIdentifier(name, "string", context.packageName)
        if (resId == 0) {
            resId = context.resources.getIdentifier(name, "string", "com.example")
        }
        return if (resId != 0) context.getString(resId) else ""
    }

    fun getDatabaseUrl(): String {
        val saved = sharedPrefs.getString("firebase_db_url", "") ?: ""
        if (saved.isNotBlank()) return saved
        return getResString("firebase_database_url")
    }

    fun setDatabaseUrl(url: String) {
        sharedPrefs.edit().putString("firebase_db_url", url.trim()).apply()
        if (isSyncEnabled()) {
            initFirebase()
        }
    }

    fun getApiKey(): String {
        val saved = sharedPrefs.getString("firebase_api_key", "") ?: ""
        if (saved.isNotBlank()) return saved
        return getResString("google_api_key")
    }

    fun setApiKey(apiKey: String) {
        sharedPrefs.edit().putString("firebase_api_key", apiKey.trim()).apply()
        if (isSyncEnabled()) {
            initFirebase()
        }
    }

    fun getProjectId(): String {
        val saved = sharedPrefs.getString("firebase_project_id", "") ?: ""
        if (saved.isNotBlank()) return saved
        return getResString("project_id")
    }

    fun setProjectId(projectId: String) {
        sharedPrefs.edit().putString("firebase_project_id", projectId.trim()).apply()
        if (isSyncEnabled()) {
            initFirebase()
        }
    }

    fun getAppId(): String {
        val saved = sharedPrefs.getString("firebase_app_id", "") ?: ""
        if (saved.isNotBlank()) return saved
        return getResString("google_app_id")
    }

    fun setAppId(appId: String) {
        sharedPrefs.edit().putString("firebase_app_id", appId.trim()).apply()
        if (isSyncEnabled()) {
            initFirebase()
        }
    }

    @Synchronized
    fun initFirebase(): Boolean {
        val url = getDatabaseUrl()
        if (url.isBlank()) {
            _syncState.value = FirebaseSyncState.Error("Database URL is empty")
            return false
        }

        _syncState.value = FirebaseSyncState.Connecting
        try {
            val apiKey = getApiKey().ifBlank { "dummy-api-key-for-init-purposes" }
            val projectId = getProjectId().ifBlank { "dummy-project-id" }
            val appId = getAppId().ifBlank { "1:123456789012:android:0000000000000000" }

            val options = FirebaseOptions.Builder()
                .setApiKey(apiKey)
                .setApplicationId(appId)
                .setDatabaseUrl(url)
                .setProjectId(projectId)
                .build()

            val existingApps = FirebaseApp.getApps(context)
            firebaseApp = if (existingApps.isEmpty()) {
                FirebaseApp.initializeApp(context, options)
            } else {
                existingApps.first()
            }

            database = FirebaseDatabase.getInstance(firebaseApp!!, url)
            _syncState.value = FirebaseSyncState.Connected(url)
            Log.d("FirebaseRealtimeManager", "Firebase Realtime DB successfully initialized: $url")
            return true
        } catch (e: Exception) {
            Log.e("FirebaseRealtimeManager", "Error initializing Firebase app", e)
            _syncState.value = FirebaseSyncState.Error(e.localizedMessage ?: "Initialization error")
            return false
        }
    }

    fun disconnect() {
        stopListening()
        firebaseApp = null
        database = null
        rootRef = null
        _syncState.value = FirebaseSyncState.Idle
    }

    /**
     * Start listening to realtime database node changes under /vaults/<vaultId>
     */
    fun startListening(
        vaultId: String,
        scope: CoroutineScope,
        repository: StationeryRepository,
        onSyncUpdate: (String) -> Unit,
        onMetadataSync: (List<User>?, List<AuditLog>?, List<Expense>?, List<Customer>?, String?, String?) -> Unit = { _, _, _, _, _, _ -> }
    ) {
        if (vaultId.isBlank()) return
        val db = database ?: return
        
        stopListening()
        activeVaultId = vaultId
        rootRef = db.getReference("vaults/$vaultId")

        valueListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                scope.launch(Dispatchers.IO) {
                    try {
                        Log.d("FirebaseRealtimeManager", "Realtime update received from Firebase Vault: $vaultId")
                        val productsList = mutableListOf<Product>()
                        val salesList = mutableListOf<Sale>()
                        val saleItemsList = mutableListOf<SaleItem>()

                        // Parse Products
                        snapshot.child("products").children.forEach { child ->
                            val map = child.value as? Map<String, Any> ?: return@forEach
                            productsList.add(parseProduct(map))
                        }

                        // Parse Sales
                        snapshot.child("sales").children.forEach { child ->
                            val map = child.value as? Map<String, Any> ?: return@forEach
                            salesList.add(parseSale(map))
                        }

                        // Parse Sale Items
                        snapshot.child("sale_items").children.forEach { child ->
                            val map = child.value as? Map<String, Any> ?: return@forEach
                            saleItemsList.add(parseSaleItem(map))
                        }

                        // Parse Product Returns
                        val returnsList = mutableListOf<ProductReturn>()
                        if (snapshot.child("product_returns").exists()) {
                            snapshot.child("product_returns").children.forEach { child ->
                                val map = child.value as? Map<String, Any> ?: return@forEach
                                returnsList.add(parseProductReturn(map))
                            }
                        }

                        // Parse Deleted Products list to prevent re-uploading deleted products
                        val deletedBarcodes = mutableSetOf<String>()
                        if (snapshot.child("deleted_products").exists()) {
                            snapshot.child("deleted_products").children.forEach { child ->
                                if (child.value == true) {
                                    deletedBarcodes.add(child.key ?: "")
                                }
                            }
                        }

                        // Local current values for sync reconciliation
                        var localProducts = repository.productDao.getAllProductsList()
                        val localSales = repository.saleDao.getAllSalesList()
                        val localSaleItems = repository.saleDao.getAllSaleItemsList()
                        val localReturns = repository.productReturnDao.getAllReturnsList()

                        // Sync deletions: If a product was deleted in the cloud, delete it locally
                        val localProductsToDelete = localProducts.filter {
                            val key = it.barcode.ifBlank { "prod_" + it.id }
                            key in deletedBarcodes
                        }
                        if (localProductsToDelete.isNotEmpty()) {
                            Log.d("FirebaseRealtimeManager", "Sync: Deleting ${localProductsToDelete.size} products locally that were deleted in the cloud...")
                            localProductsToDelete.forEach { prod ->
                                repository.deleteProduct(prod)
                            }
                            localProducts = localProducts.filter { it !in localProductsToDelete }
                        }

                        repository.mergeAndSync(
                            localProducts = localProducts,
                            localSales = localSales,
                            localSaleItems = localSaleItems,
                            cloudProducts = productsList.filter { 
                                val key = it.barcode.ifBlank { "prod_" + it.id }
                                key !in deletedBarcodes 
                            },
                            cloudSales = salesList,
                            cloudSaleItems = saleItemsList
                        )

                        // Reconcile and Sync Returns
                        val localReturnKeys = localReturns.map { "${it.timestamp}_${it.productId}" }.toSet()
                        val newCloudReturns = returnsList.filter { "${it.timestamp}_${it.productId}" !in localReturnKeys }
                        if (newCloudReturns.isNotEmpty()) {
                            repository.insertReturns(newCloudReturns)
                        }

                        // Bidirectional Sync: Upload any local-only products to the cloud (excluding deleted ones)
                        val cloudBarcodes = productsList.map { it.barcode.ifBlank { "prod_" + it.id } }.toSet()
                        val localOnlyProducts = localProducts.filter { 
                            val key = it.barcode.ifBlank { "prod_" + it.id }
                            it.barcode.isNotBlank() && key !in cloudBarcodes && key !in deletedBarcodes 
                        }
                        if (localOnlyProducts.isNotEmpty()) {
                            Log.d("FirebaseRealtimeManager", "Sync: Found ${localOnlyProducts.size} local-only products. Uploading to cloud...")
                            localOnlyProducts.forEach { prod ->
                                pushProduct(vaultId, prod)
                            }
                        }

                        // Bidirectional Sync: Upload any local-only sales and their items to the cloud
                        val cloudSalesTimestamps = salesList.map { it.timestamp }.toSet()
                        val localOnlySales = localSales.filter { it.timestamp !in cloudSalesTimestamps }
                        if (localOnlySales.isNotEmpty()) {
                            Log.d("FirebaseRealtimeManager", "Sync: Found ${localOnlySales.size} local-only sales. Uploading to cloud...")
                            localOnlySales.forEach { sale ->
                                val items = localSaleItems.filter { it.saleId == sale.id }
                                pushSale(vaultId, sale, items)
                            }
                        }

                        // Bidirectional Sync: Upload any local-only returns to the cloud
                        val cloudReturnKeys = returnsList.map { "${it.timestamp}_${it.productId}" }.toSet()
                        val localOnlyReturns = localReturns.filter { "${it.timestamp}_${it.productId}" !in cloudReturnKeys }
                        if (localOnlyReturns.isNotEmpty()) {
                            Log.d("FirebaseRealtimeManager", "Sync: Found ${localOnlyReturns.size} local-only returns. Uploading to cloud...")
                            localOnlyReturns.forEach { ret ->
                                pushReturn(vaultId, ret)
                            }
                        }

                        // Parse Admin, Report, and other component metadata safely

                        val usersList = if (snapshot.child("users").exists()) mutableListOf<User>() else null
                        if (usersList != null) {
                            snapshot.child("users").children.forEach { child ->
                                val map = child.value as? Map<String, Any> ?: return@forEach
                                usersList.add(parseUser(map))
                            }
                        }

                        val auditLogsList: List<AuditLog>? = null // Ignored to conserve bandwidth

                        val expensesList = if (snapshot.child("expenses").exists()) mutableListOf<Expense>() else null
                        if (expensesList != null) {
                            snapshot.child("expenses").children.forEach { child ->
                                val map = child.value as? Map<String, Any> ?: return@forEach
                                expensesList.add(parseExpense(map))
                            }
                        }

                        val customersList = if (snapshot.child("customers").exists()) mutableListOf<Customer>() else null
                        if (customersList != null) {
                            snapshot.child("customers").children.forEach { child ->
                                val map = child.value as? Map<String, Any> ?: return@forEach
                                customersList.add(parseCustomer(map))
                            }
                        }

                        var shopName: String? = null
                        var panNumber: String? = null
                        if (snapshot.child("business_info").exists()) {
                            val map = snapshot.child("business_info").value as? Map<String, Any>
                            if (map != null) {
                                shopName = map["shopName"] as? String
                                panNumber = map["panNumber"] as? String
                            }
                        }

                        withContext(Dispatchers.Main) {
                            onSyncUpdate("Synced ${productsList.size} products, ${salesList.size} sales, ${returnsList.size} returns via Firebase Realtime!")
                            onMetadataSync(usersList, auditLogsList, expensesList, customersList, shopName, panNumber)
                        }
                    } catch (e: Exception) {
                        Log.e("FirebaseRealtimeManager", "Error parsing / merging realtime data", e)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseRealtimeManager", "Realtime listener cancelled: ${error.message}")
            }
        }

        rootRef?.addValueEventListener(valueListener!!)
        Log.d("FirebaseRealtimeManager", "Realtime Listener attached under /vaults/$vaultId")
    }

    fun stopListening() {
        valueListener?.let {
            rootRef?.removeEventListener(it)
        }
        valueListener = null
        rootRef = null
    }

    /**
     * Push a product to Firebase Realtime Database
     */
    fun pushProduct(vaultId: String, product: Product) {
        if (!isSyncEnabled() || vaultId.isBlank()) return
        val db = database ?: return
        scopeIO {
            try {
                val barcode = product.barcode.ifBlank { "prod_" + product.id }
                val updates = hashMapOf<String, Any?>()
                updates["vaults/$vaultId/products/$barcode"] = serializeProduct(product)
                updates["vaults/$vaultId/deleted_products/$barcode"] = null
                
                db.getReference().updateChildren(updates)
                Log.d("FirebaseRealtimeManager", "Pushed product to Firebase atomically: ${product.name}")
            } catch (e: Exception) {
                Log.e("FirebaseRealtimeManager", "Failed to push product", e)
            }
        }
    }

    /**
     * Delete a product from Firebase Realtime Database
     */
    fun deleteProduct(vaultId: String, product: Product) {
        if (!isSyncEnabled() || vaultId.isBlank()) return
        val db = database ?: return
        scopeIO {
            try {
                val barcode = product.barcode.ifBlank { "prod_" + product.id }
                val updates = hashMapOf<String, Any?>()
                updates["vaults/$vaultId/products/$barcode"] = null
                updates["vaults/$vaultId/deleted_products/$barcode"] = true
                
                db.getReference().updateChildren(updates)
                Log.d("FirebaseRealtimeManager", "Deleted product from Firebase atomically: ${product.name}")
            } catch (e: Exception) {
                Log.e("FirebaseRealtimeManager", "Failed to delete product", e)
            }
        }
    }

    /**
     * Push a sale and its line items to Firebase Realtime Database
     */
    fun pushSale(vaultId: String, sale: Sale, items: List<SaleItem>) {
        if (!isSyncEnabled() || vaultId.isBlank()) return
        val db = database ?: return
        scopeIO {
            try {
                // Store sales under unique timestamps or IDs
                val saleKey = sale.timestamp.toString()
                db.getReference("vaults/$vaultId/sales/$saleKey").setValue(serializeSale(sale))
                
                items.forEach { item ->
                    val itemKey = "${sale.timestamp}_${item.productId}"
                    db.getReference("vaults/$vaultId/sale_items/$itemKey").setValue(serializeSaleItem(item))
                }
                
                // Update stock levels for modified items
                items.forEach { item ->
                    // Since stock quantity is reduced, we should push the updated product
                    // But we'll let the viewmodel trigger that or handle it dynamically
                }
                Log.d("FirebaseRealtimeManager", "Pushed sale to Firebase: ${sale.totalAmount} (items: ${items.size})")
            } catch (e: Exception) {
                Log.e("FirebaseRealtimeManager", "Failed to push sale", e)
            }
        }
    }

    /**
     * Complete full database backup upload to Firebase Realtime Database
     */
    fun pushAllData(
        vaultId: String,
        products: List<Product>,
        sales: List<Sale>,
        saleItems: List<SaleItem>,
        productReturns: List<ProductReturn> = emptyList(),
        users: List<User> = emptyList(),
        auditLogs: List<AuditLog> = emptyList(),
        expenses: List<Expense> = emptyList(),
        customers: List<Customer> = emptyList(),
        shopName: String = "",
        panNumber: String = "",
        onComplete: (Boolean) -> Unit
    ) {
        if (!isSyncEnabled() || vaultId.isBlank()) {
            onComplete(false)
            return
        }
        val db = database
        if (db == null) {
            onComplete(false)
            return
        }
        scopeIO {
            try {
                val updates = hashMapOf<String, Any>()
                
                // Prepare Products
                val prodsMap = hashMapOf<String, Any>()
                products.forEach {
                    val key = it.barcode.ifBlank { "prod_" + it.id }
                    prodsMap[key] = serializeProduct(it)
                }
                updates["vaults/$vaultId/products"] = prodsMap

                // Prepare Sales
                val salesMap = hashMapOf<String, Any>()
                sales.forEach {
                    salesMap[it.timestamp.toString()] = serializeSale(it)
                }
                updates["vaults/$vaultId/sales"] = salesMap

                // Prepare Sale Items
                val itemsMap = hashMapOf<String, Any>()
                saleItems.forEach {
                    val key = "${it.saleId}_${it.productId}_${it.id}"
                    itemsMap[key] = serializeSaleItem(it)
                }
                updates["vaults/$vaultId/sale_items"] = itemsMap

                // Prepare Product Returns
                if (productReturns.isNotEmpty()) {
                    val returnsMap = hashMapOf<String, Any>()
                    productReturns.forEach {
                        val key = "${it.timestamp}_${it.productId}"
                        returnsMap[key] = serializeProductReturn(it)
                    }
                    updates["vaults/$vaultId/product_returns"] = returnsMap
                }


                // Prepare Users
                if (users.isNotEmpty()) {
                    val usersMap = hashMapOf<String, Any>()
                    users.forEach {
                        usersMap[it.username.replace(".", "_")] = serializeUser(it)
                    }
                    updates["vaults/$vaultId/users"] = usersMap
                }

                // Prepare Audit Logs (Ignored to conserve bandwidth)
                /*
                if (auditLogs.isNotEmpty()) {
                    val logsMap = hashMapOf<String, Any>()
                    auditLogs.forEach {
                        logsMap[it.timestamp.toString()] = serializeAuditLog(it)
                    }
                    updates["vaults/$vaultId/audit_logs"] = logsMap
                }
                */

                // Prepare Expenses
                if (expenses.isNotEmpty()) {
                    val expensesMap = hashMapOf<String, Any>()
                    expenses.forEach {
                        expensesMap[it.id] = serializeExpense(it)
                    }
                    updates["vaults/$vaultId/expenses"] = expensesMap
                }

                // Prepare Customers
                if (customers.isNotEmpty()) {
                    val customersMap = hashMapOf<String, Any>()
                    customers.forEach {
                        customersMap[it.id] = serializeCustomer(it)
                    }
                    updates["vaults/$vaultId/customers"] = customersMap
                }

                // Prepare Business Info
                if (shopName.isNotBlank() || panNumber.isNotBlank()) {
                    updates["vaults/$vaultId/business_info"] = mapOf(
                        "shopName" to shopName,
                        "panNumber" to panNumber
                    )
                }

                db.reference.updateChildren(updates)
                    .addOnSuccessListener {
                        onComplete(true)
                    }
                    .addOnFailureListener {
                        onComplete(false)
                    }
            } catch (e: Exception) {
                Log.e("FirebaseRealtimeManager", "Failed to push all data", e)
                onComplete(false)
            }
        }
    }

    /**
     * Push full users list to Firebase Realtime Database
     */
    fun pushUsersList(vaultId: String, users: List<User>) {
        if (!isSyncEnabled() || vaultId.isBlank()) return
        val db = database ?: return
        scopeIO {
            try {
                val map = hashMapOf<String, Any>()
                users.forEach {
                    map[it.username.replace(".", "_")] = serializeUser(it)
                }
                db.getReference("vaults/$vaultId/users").setValue(map)
                Log.d("FirebaseRealtimeManager", "Pushed users list to Firebase: ${users.size} users")
            } catch (e: Exception) {
                Log.e("FirebaseRealtimeManager", "Failed to push users list", e)
            }
        }
    }

    /**
     * Push full audit logs list to Firebase Realtime Database
     */
    fun pushAuditLogs(vaultId: String, logs: List<AuditLog>) {
        // Ignored to conserve bandwidth
        Log.d("FirebaseRealtimeManager", "Ignoring pushAuditLogs to conserve bandwidth")
    }

    /**
     * Push full expenses list to Firebase Realtime Database
     */
    fun pushExpenses(vaultId: String, expenses: List<Expense>) {
        if (!isSyncEnabled() || vaultId.isBlank()) return
        val db = database ?: return
        scopeIO {
            try {
                val map = hashMapOf<String, Any>()
                expenses.forEach {
                    map[it.id] = serializeExpense(it)
                }
                db.getReference("vaults/$vaultId/expenses").setValue(map)
                Log.d("FirebaseRealtimeManager", "Pushed expenses to Firebase: ${expenses.size} expenses")
            } catch (e: Exception) {
                Log.e("FirebaseRealtimeManager", "Failed to push expenses", e)
            }
        }
    }

    /**
     * Push full customers list to Firebase Realtime Database
     */
    fun pushCustomers(vaultId: String, customers: List<Customer>) {
        if (!isSyncEnabled() || vaultId.isBlank()) return
        val db = database ?: return
        scopeIO {
            try {
                val map = hashMapOf<String, Any>()
                customers.forEach {
                    map[it.id] = serializeCustomer(it)
                }
                db.getReference("vaults/$vaultId/customers").setValue(map)
                Log.d("FirebaseRealtimeManager", "Pushed customers to Firebase: ${customers.size} customers")
            } catch (e: Exception) {
                Log.e("FirebaseRealtimeManager", "Failed to push customers", e)
            }
        }
    }

    /**
     * Push shop metadata to Firebase Realtime Database
     */
    fun pushBusinessInfo(vaultId: String, shopName: String, panNumber: String) {
        if (!isSyncEnabled() || vaultId.isBlank()) return
        val db = database ?: return
        scopeIO {
            try {
                db.getReference("vaults/$vaultId/business_info").setValue(mapOf(
                    "shopName" to shopName,
                    "panNumber" to panNumber
                ))
                Log.d("FirebaseRealtimeManager", "Pushed business info to Firebase: $shopName")
            } catch (e: Exception) {
                Log.e("FirebaseRealtimeManager", "Failed to push business info", e)
            }
        }
    }

    private fun serializeUser(u: User): Map<String, Any> {
        return mapOf(
            "username" to u.username,
            "role" to u.role.name,
            "pin" to u.pin,
            "fullName" to u.fullName,
            "avatarColor" to u.avatarColor,
            "isEnabled" to u.isEnabled,
            "canManageInventory" to u.canManageInventory,
            "canViewReports" to u.canViewReports,
            "canPerformSale" to u.canPerformSale,
            "canUseAiAdvisor" to u.canUseAiAdvisor,
            "canManageStoreCredit" to u.canManageStoreCredit,
            "isSuperAdmin" to u.isSuperAdmin
        )
    }

    private fun serializeAuditLog(log: AuditLog): Map<String, Any> {
        return mapOf(
            "timestamp" to log.timestamp,
            "username" to log.username,
            "fullName" to log.fullName,
            "action" to log.action,
            "details" to log.details
        )
    }

    private fun serializeExpense(exp: Expense): Map<String, Any> {
        return mapOf(
            "id" to exp.id,
            "title" to exp.title,
            "category" to exp.category,
            "amount" to exp.amount,
            "timestamp" to exp.timestamp,
            "description" to exp.description
        )
    }

    private fun serializeCustomer(c: Customer): Map<String, Any> {
        return mapOf(
            "id" to c.id,
            "name" to c.name,
            "phone" to c.phone,
            "storeCredit" to c.storeCredit,
            "address" to c.address
        )
    }

    private fun parseUser(map: Map<String, Any>): User {
        val username = map["username"] as? String ?: ""
        val roleStr = map["role"] as? String ?: UserRole.CASHIER.name
        val role = try { UserRole.valueOf(roleStr) } catch(e: Exception) { UserRole.CASHIER }
        val pin = map["pin"] as? String ?: ""
        val fullName = map["fullName"] as? String ?: ""
        val avatarColor = (map["avatarColor"] as? Number)?.toLong() ?: 0L
        val isEnabled = map["isEnabled"] as? Boolean ?: true
        val canManageInventory = map["canManageInventory"] as? Boolean ?: true
        val canViewReports = map["canViewReports"] as? Boolean ?: true
        val canPerformSale = map["canPerformSale"] as? Boolean ?: true
        val canUseAiAdvisor = map["canUseAiAdvisor"] as? Boolean ?: true
        val canManageStoreCredit = map["canManageStoreCredit"] as? Boolean ?: true
        val isSuperAdmin = map["isSuperAdmin"] as? Boolean ?: false
        return User(
            username = username,
            role = role,
            pin = pin,
            fullName = fullName,
            avatarColor = avatarColor,
            isEnabled = isEnabled,
            canManageInventory = canManageInventory,
            canViewReports = canViewReports,
            canPerformSale = canPerformSale,
            canUseAiAdvisor = canUseAiAdvisor,
            canManageStoreCredit = canManageStoreCredit,
            isSuperAdmin = isSuperAdmin
        )
    }

    private fun parseAuditLog(map: Map<String, Any>): AuditLog {
        val timestamp = (map["timestamp"] as? Number)?.toLong() ?: 0L
        val username = map["username"] as? String ?: ""
        val fullName = map["fullName"] as? String ?: ""
        val action = map["action"] as? String ?: ""
        val details = map["details"] as? String ?: ""
        return AuditLog(timestamp, username, fullName, action, details)
    }

    private fun parseExpense(map: Map<String, Any>): Expense {
        val id = map["id"] as? String ?: ""
        val title = map["title"] as? String ?: ""
        val category = map["category"] as? String ?: ""
        val amount = (map["amount"] as? Number)?.toDouble() ?: 0.0
        val timestamp = (map["timestamp"] as? Number)?.toLong() ?: 0L
        val description = map["description"] as? String ?: ""
        return Expense(id, title, category, amount, timestamp, description)
    }

    private fun parseCustomer(map: Map<String, Any>): Customer {
        val id = map["id"] as? String ?: ""
        val name = map["name"] as? String ?: ""
        val phone = map["phone"] as? String ?: ""
        val storeCredit = (map["storeCredit"] as? Number)?.toDouble() ?: 0.0
        val address = map["address"] as? String ?: ""
        return Customer(id, name, phone, storeCredit, address)
    }

    private fun scopeIO(block: suspend () -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            block()
        }
    }

    // --- Dynamic Serialization Helpers ---
    private fun serializeProduct(p: Product): Map<String, Any> {
        return mapOf(
            "id" to p.id,
            "name" to p.name,
            "barcode" to p.barcode,
            "category" to p.category,
            "costPrice" to p.costPrice,
            "sellingPrice" to p.sellingPrice,
            "stockQuantity" to p.stockQuantity,
            "minStockThreshold" to p.minStockThreshold
        )
    }

    private fun serializeSale(s: Sale): Map<String, Any> {
        return mapOf(
            "id" to s.id,
            "timestamp" to s.timestamp,
            "totalAmount" to s.totalAmount,
            "totalProfit" to s.totalProfit,
            "paymentMode" to s.paymentMode,
            "soldBy" to s.soldBy,
            "customerPhone" to s.customerPhone
        )
    }

    private fun serializeSaleItem(item: SaleItem): Map<String, Any> {
        return mapOf(
            "id" to item.id,
            "saleId" to item.saleId,
            "productId" to item.productId,
            "productName" to item.productName,
            "barcode" to item.barcode,
            "quantity" to item.quantity,
            "sellingPrice" to item.sellingPrice,
            "costPrice" to item.costPrice
        )
    }

    // --- Dynamic Deserialization Helpers ---
    private fun parseProduct(map: Map<String, Any>): Product {
        val id = (map["id"] as? Number)?.toInt() ?: 0
        val name = map["name"] as? String ?: ""
        val barcode = map["barcode"] as? String ?: ""
        val category = map["category"] as? String ?: ""
        val costPrice = (map["costPrice"] as? Number)?.toDouble() ?: 0.0
        val sellingPrice = (map["sellingPrice"] as? Number)?.toDouble() ?: 0.0
        val stockQuantity = (map["stockQuantity"] as? Number)?.toInt() ?: 0
        val minStockThreshold = (map["minStockThreshold"] as? Number)?.toInt() ?: 5
        return Product(id, name, barcode, category, costPrice, sellingPrice, stockQuantity, minStockThreshold)
    }

    private fun parseSale(map: Map<String, Any>): Sale {
        val id = (map["id"] as? Number)?.toInt() ?: 0
        val timestamp = (map["timestamp"] as? Number)?.toLong() ?: 0L
        val totalAmount = (map["totalAmount"] as? Number)?.toDouble() ?: 0.0
        val totalProfit = (map["totalProfit"] as? Number)?.toDouble() ?: 0.0
        val paymentMode = map["paymentMode"] as? String ?: "Cash"
        val soldBy = map["soldBy"] as? String ?: "Cashier/Staff"
        val customerPhone = map["customerPhone"] as? String ?: ""
        return Sale(id, timestamp, totalAmount, totalProfit, paymentMode, soldBy, customerPhone)
    }

    private fun parseSaleItem(map: Map<String, Any>): SaleItem {
        val id = (map["id"] as? Number)?.toInt() ?: 0
        val saleId = (map["saleId"] as? Number)?.toInt() ?: 0
        val productId = (map["productId"] as? Number)?.toInt() ?: 0
        val productName = map["productName"] as? String ?: ""
        val barcode = map["barcode"] as? String ?: ""
        val quantity = (map["quantity"] as? Number)?.toInt() ?: 0
        val sellingPrice = (map["sellingPrice"] as? Number)?.toDouble() ?: 0.0
        val costPrice = (map["costPrice"] as? Number)?.toDouble() ?: 0.0
        return SaleItem(id, saleId, productId, productName, barcode, quantity, sellingPrice, costPrice)
    }

    fun pushReturn(vaultId: String, productReturn: ProductReturn) {
        if (!isSyncEnabled() || vaultId.isBlank()) return
        val db = database ?: return
        scopeIO {
            try {
                val returnKey = "${productReturn.timestamp}_${productReturn.productId}"
                db.getReference("vaults/$vaultId/product_returns/$returnKey").setValue(serializeProductReturn(productReturn))
                Log.d("FirebaseRealtimeManager", "Pushed return to Firebase: ${productReturn.productName}")
            } catch (e: Exception) {
                Log.e("FirebaseRealtimeManager", "Failed to push return", e)
            }
        }
    }

    private fun serializeProductReturn(ret: ProductReturn): Map<String, Any> {
        return mapOf(
            "id" to ret.id,
            "saleId" to (ret.saleId ?: 0),
            "productId" to ret.productId,
            "productName" to ret.productName,
            "barcode" to ret.barcode,
            "quantity" to ret.quantity,
            "refundAmount" to ret.refundAmount,
            "timestamp" to ret.timestamp,
            "reason" to ret.reason,
            "returnedBy" to ret.returnedBy
        )
    }

    private fun parseProductReturn(map: Map<String, Any>): ProductReturn {
        val id = (map["id"] as? Number)?.toInt() ?: 0
        val saleId = (map["saleId"] as? Number)?.toInt()
        val productId = (map["productId"] as? Number)?.toInt() ?: 0
        val productName = map["productName"] as? String ?: ""
        val barcode = map["barcode"] as? String ?: ""
        val quantity = (map["quantity"] as? Number)?.toInt() ?: 0
        val refundAmount = (map["refundAmount"] as? Number)?.toDouble() ?: 0.0
        val timestamp = (map["timestamp"] as? Number)?.toLong() ?: 0L
        val reason = map["reason"] as? String ?: "Defective/Change of mind"
        val returnedBy = map["returnedBy"] as? String ?: "Staff"
        return ProductReturn(
            id = id,
            saleId = saleId,
            productId = productId,
            productName = productName,
            barcode = barcode,
            quantity = quantity,
            refundAmount = refundAmount,
            timestamp = timestamp,
            reason = reason,
            returnedBy = returnedBy
        )
    }

    fun getDatabaseInstance(): FirebaseDatabase? {
        if (database == null) {
            initFirebase()
        }
        return database
    }
}
