package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val barcode: String, // barcode associated with the product
    val category: String,
    val costPrice: Double,
    val sellingPrice: Double,
    val stockQuantity: Int,
    val minStockThreshold: Int = 5 // low stock alert threshold
)

@Entity(tableName = "sales")
data class Sale(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val totalAmount: Double,
    val totalProfit: Double,
    val paymentMode: String = "Cash",
    val soldBy: String = "Cashier/Staff",
    val customerPhone: String = ""
)

@Entity(tableName = "sale_items")
data class SaleItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val saleId: Int,
    val productId: Int,
    val productName: String,
    val barcode: String,
    val quantity: Int,
    val sellingPrice: Double,
    val costPrice: Double
)

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
    val storeCredit: Double = 0.0,
    val address: String = ""
)

