package com.example.data

import kotlinx.coroutines.flow.Flow

class StationeryRepository(
    val productDao: ProductDao,
    val saleDao: SaleDao
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

    suspend fun mergeAndSync(
        localProducts: List<Product>,
        localSales: List<Sale>,
        localSaleItems: List<SaleItem>,
        cloudProducts: List<Product>,
        cloudSales: List<Sale>,
        cloudSaleItems: List<SaleItem>
    ) {
        // 1. Find all cloud sales that are not present locally (by timestamp)
        val localTimestamps = localSales.map { it.timestamp }.toSet()
        val newCloudSales = cloudSales.filter { it.timestamp !in localTimestamps }

        // 2. Find all local sales that are not present in the cloud (by timestamp)
        val cloudTimestamps = cloudSales.map { it.timestamp }.toSet()
        val localSalesNotInCloud = localSales.filter { it.timestamp !in cloudTimestamps }

        // 3. Calculate total quantities of each product sold in local sales that are not yet in the cloud.
        val localUnsyncedQuantities = mutableMapOf<String, Int>()
        for (localSale in localSalesNotInCloud) {
            val items = localSaleItems.filter { it.saleId == localSale.id }
            for (item in items) {
                if (item.barcode.isNotBlank()) {
                    localUnsyncedQuantities[item.barcode] = (localUnsyncedQuantities[item.barcode] ?: 0) + item.quantity
                }
            }
        }

        // 4. Merge Products first so they exist when inserting sale items
        for (cloudProduct in cloudProducts) {
            val localProduct = localProducts.find { it.barcode == cloudProduct.barcode }
            val unsyncedSold = localUnsyncedQuantities[cloudProduct.barcode] ?: 0
            val reconciledStock = (cloudProduct.stockQuantity - unsyncedSold).coerceAtLeast(0)

            if (localProduct == null) {
                productDao.insertProduct(
                    Product(
                        name = cloudProduct.name,
                        barcode = cloudProduct.barcode,
                        category = cloudProduct.category,
                        costPrice = cloudProduct.costPrice,
                        sellingPrice = cloudProduct.sellingPrice,
                        stockQuantity = reconciledStock,
                        minStockThreshold = cloudProduct.minStockThreshold
                    )
                )
            } else {
                productDao.insertProduct(
                    localProduct.copy(
                        name = cloudProduct.name,
                        category = cloudProduct.category,
                        costPrice = cloudProduct.costPrice,
                        sellingPrice = cloudProduct.sellingPrice,
                        stockQuantity = reconciledStock,
                        minStockThreshold = cloudProduct.minStockThreshold
                    )
                )
            }
        }

        // 5. For each new cloud sale, insert it locally and its sale items
        for (cloudSale in newCloudSales) {
            val newLocalSaleId = saleDao.insertSale(
                Sale(
                    timestamp = cloudSale.timestamp,
                    totalAmount = cloudSale.totalAmount,
                    totalProfit = cloudSale.totalProfit,
                    paymentMode = cloudSale.paymentMode,
                    soldBy = cloudSale.soldBy,
                    customerPhone = cloudSale.customerPhone
                )
            ).toInt()

            val matchingCloudItems = cloudSaleItems.filter { it.saleId == cloudSale.id }
            val itemsToInsert = matchingCloudItems.map { cloudItem ->
                val matchedProd = productDao.getProductByBarcode(cloudItem.barcode)
                val resolvedProductId = matchedProd?.id ?: 0
                SaleItem(
                    saleId = newLocalSaleId,
                    productId = resolvedProductId,
                    productName = cloudItem.productName,
                    barcode = cloudItem.barcode,
                    quantity = cloudItem.quantity,
                    sellingPrice = cloudItem.sellingPrice,
                    costPrice = cloudItem.costPrice
                )
            }
            saleDao.insertSaleItems(itemsToInsert)
        }
    }
}
