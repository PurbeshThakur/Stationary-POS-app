package com.example.util

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.InventoryViewModel

object TranslationHelper {
    private val translations = mapOf(
        "en" to mapOf(
            "app_title" to "Smart Retail POS",
            "pos_sell" to "POS Sell",
            "inventory" to "Inventory",
            "reports" to "Reports",
            "ai_advisor" to "AI Advisor",
            "cloud_sync" to "Cloud Sync",
            "admin" to "Admin",
            "log_in" to "Log In",
            "log_out" to "Log Out",
            "security_pin" to "Security PIN",
            "enter_pin" to "Enter 4-Digit Security PIN",
            "select_profile" to "Select User Profile",
            "subtitle" to "Smart Retail POS & Inventory System",
            "cart" to "Shopping Cart",
            "total" to "Total",
            "subtotal" to "Subtotal",
            "discount" to "Discount",
            "tax" to "Tax",
            "checkout" to "Checkout",
            "search_products" to "Search Products...",
            "items" to "Items",
            "product_name" to "Product Name",
            "barcode" to "Barcode",
            "price" to "Price",
            "stock" to "Stock",
            "add_to_cart" to "Add to Cart",
            "apply_discount" to "Apply Discount",
            "clear_cart" to "Clear Cart",
            "customer_details" to "Customer Details",
            "search_customers" to "Search customers...",
            "receipt" to "Receipt",
            "print_bill" to "Print Bill / Save as PDF",
            "add_product" to "Add New Product",
            "edit_product" to "Edit Product",
            "delete_product" to "Delete Product",
            "stock_alert" to "Stock Alert",
            "low_stock" to "Low Stock",
            "save" to "Save",
            "cancel" to "Cancel",
            "category" to "Category",
            "sales_report" to "Sales Report",
            "expenses" to "Expenses",
            "profit_loss" to "Profit & Loss",
            "revenue" to "Revenue",
            "net_profit" to "Net Profit",
            "profiles_access" to "Profiles & Access",
            "system_audit" to "System Audit Trails",
            "shop_config" to "Shop Configuration",
            "store_credit" to "Store Credit Accounts"
        ),
        "ne" to mapOf(
            "app_title" to "स्मार्ट रिटेल पीओएस",
            "pos_sell" to "बिक्री (POS)",
            "inventory" to "स्टक सूची",
            "reports" to "विवरण रिपोर्टहरू",
            "ai_advisor" to "एआई सल्लाहकार",
            "cloud_sync" to "क्लाउड सिंक",
            "admin" to "प्रशासक",
            "log_in" to "लग इन",
            "log_out" to "लग आउट",
            "security_pin" to "सुरक्षा पिन",
            "enter_pin" to "४-अङ्कको सुरक्षा पिन हाल्नुहोस्",
            "select_profile" to "प्रोफाइल चयन गर्नुहोस्",
            "subtitle" to "स्मार्ट रिटेल पीओएस र सूची प्रणाली",
            "cart" to "शपिङ कार्ट",
            "total" to "कुल जम्मा",
            "subtotal" to "उपकुल",
            "discount" to "छुट",
            "tax" to "कर (Tax)",
            "checkout" to "भुक्तानी गर्नुहोस्",
            "search_products" to "सामान खोज्नुहोस्...",
            "items" to "सामानहरू",
            "product_name" to "सामानको नाम",
            "barcode" to "बारकोड",
            "price" to "मूल्य",
            "stock" to "स्टक मात्रा",
            "add_to_cart" to "कार्टमा थप्नुहोस्",
            "apply_discount" to "छुट लागू गर्नुहोस्",
            "clear_cart" to "कार्ट खाली गर्नुहोस्",
            "customer_details" to "ग्राहकको विवरण",
            "search_customers" to "ग्राहक खोज्नुहोस्...",
            "receipt" to "रसिद",
            "print_bill" to "प्रिन्ट / PDF बचत गर्नुहोस्",
            "add_product" to "नयाँ सामान थप्नुहोस्",
            "edit_product" to "सामान सम्पादन गर्नुहोस्",
            "delete_product" to "सामान हटाउनुहोस्",
            "stock_alert" to "स्टक चेतावनी",
            "low_stock" to "कम स्टक",
            "save" to "बचत गर्नुहोस्",
            "cancel" to "रद्द गर्नुहोस्",
            "category" to "वर्ग (Category)",
            "sales_report" to "बिक्री रिपोर्ट",
            "expenses" to "खर्च विवरण",
            "profit_loss" to "नाफा र नोक्सान",
            "revenue" to "कुल आम्दानी",
            "net_profit" to "खुद नाफा",
            "profiles_access" to "प्रोफाइल र पहुँच",
            "system_audit" to "प्रणाली अडिट रेकर्ड",
            "shop_config" to "पसल व्यवस्थापन",
            "store_credit" to "स्टोर क्रेडिट खाताहरू"
        )
    )

    fun translate(key: String, lang: String): String {
        return translations[lang]?.get(key) ?: translations["en"]?.get(key) ?: key
    }
}

@Composable
fun t(key: String, viewModel: InventoryViewModel): String {
    val lang by viewModel.appLanguage.collectAsState()
    return TranslationHelper.translate(key, lang)
}

@Composable
fun LanguageToggle(
    viewModel: InventoryViewModel,
    modifier: Modifier = Modifier
) {
    val currentLanguage by viewModel.appLanguage.collectAsState()
    
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val isEnglish = currentLanguage == "en"
        // English option
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(if (isEnglish) MaterialTheme.colorScheme.primary else Color.Transparent)
                .clickable { viewModel.setAppLanguage("en") }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "EN",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.sp,
                color = if (isEnglish) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // Nepali option
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(if (!isEnglish) MaterialTheme.colorScheme.primary else Color.Transparent)
                .clickable { viewModel.setAppLanguage("ne") }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "नेपाली",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.sp,
                color = if (!isEnglish) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
