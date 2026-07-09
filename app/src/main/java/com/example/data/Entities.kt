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
    val paymentMode: String = "Cash"
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
