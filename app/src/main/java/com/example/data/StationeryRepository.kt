package com.example.data

import kotlinx.coroutines.flow.Flow

class StationeryRepository(
    private val productDao: ProductDao,
    private val saleDao: SaleDao
) {
    val allProducts: Flow<List<Product>> = productDao.getAllProducts()
    val allSales: Flow<List<Sale>> = saleDao.getAllSales()
    val allSaleItems: Flow<List<SaleItem>> = saleDao.getAllSaleItems()

    suspend fun getProductByBarcode(barcode: String): Product? {
        return productDao.getProductByBarcode(barcode)
    }

    suspend fun getProductById(id: Int): Product? {
        return productDao.getProductById(id)
    }

    suspend fun insertProduct(product: Product): Long {
        return productDao.insertProduct(product)
    }

    suspend fun insertProducts(products: List<Product>) {
        productDao.insertProducts(products)
    }

    suspend fun updateProduct(product: Product) {
        productDao.updateProduct(product)
    }

    suspend fun deleteProduct(product: Product) {
        productDao.deleteProduct(product)
    }

    suspend fun getSaleItemsForSale(saleId: Int): List<SaleItem> {
        return saleDao.getSaleItemsForSale(saleId)
    }

    suspend fun clearAllData() {
        productDao.deleteAllProducts()
        saleDao.deleteAllSales()
        saleDao.deleteAllSaleItems()
    }

    suspend fun restoreDatabase(products: List<Product>, sales: List<Sale>, saleItems: List<SaleItem>) {
        productDao.deleteAllProducts()
        saleDao.deleteAllSales()
        saleDao.deleteAllSaleItems()

        productDao.insertProducts(products)
        saleDao.insertSales(sales)
        saleDao.insertSaleItems(saleItems)
    }

    /**
     * Executes a sale:
     * 1. Inserts the main Sale record.
     * 2. Sets the returned sale ID on all SaleItems and inserts them.
     * 3. For each SaleItem, updates the stock quantity of the respective Product.
     */
    suspend fun executeSale(sale: Sale, items: List<SaleItem>): Boolean {
        if (items.isEmpty()) return false
        
        try {
            // Insert Sale
            val saleId = saleDao.insertSale(sale).toInt()
            
            // Prepare items with actual sale ID
            val itemsWithSaleId = items.map { item ->
                item.copy(saleId = saleId)
            }
            saleDao.insertSaleItems(itemsWithSaleId)
            
            // Update stock levels
            for (item in itemsWithSaleId) {
                val product = productDao.getProductById(item.productId)
                if (product != null) {
                    val newStock = (product.stockQuantity - item.quantity).coerceAtLeast(0)
                    productDao.updateStock(product.id, newStock)
                }
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
