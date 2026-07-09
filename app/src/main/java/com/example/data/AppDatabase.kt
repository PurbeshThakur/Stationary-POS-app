package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [Product::class, Sale::class, SaleItem::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun saleDao(): SaleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "stationery_inventory_db"
                )
                .addCallback(AppDatabaseCallback(scope))
                .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class AppDatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    populateDatabase(database.productDao())
                }
            }
        }

        suspend fun populateDatabase(productDao: ProductDao) {
            // Add sample stationery products
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
                    stockQuantity = 3, // Trigger low stock alert!
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
                    stockQuantity = 2, // Trigger low stock alert!
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

            for (p in sampleProducts) {
                productDao.insertProduct(p)
            }
        }
    }
}
