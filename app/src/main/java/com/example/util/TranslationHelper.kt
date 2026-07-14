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
            "store_credit" to "Store Credit Accounts",
            // Product Form Dialog Translations
            "add_new_stationery_item" to "Add New Stationery Item",
            "edit_stationery_item" to "Edit Stationery Item",
            "barcode_number" to "Barcode Number",
            "generate" to "Generate",
            "one_d_barcode" to "1D Barcode",
            "qr_sku" to "QR Code SKU",
            "scan_camera" to "Scan product barcode or QR code with camera",
            "emulator_scan" to "Emulator? Simulate Scan:",
            "random_barcode" to "Random Barcode",
            "sku_alphanumeric" to "SKU Alphanumeric",
            "add_custom_category" to "Add Custom Category",
            "custom_category_name" to "Custom Category Name",
            "select_existing_category" to "Select Existing Category",
            "cost_price_npr" to "Cost Price (NPR)",
            "selling_price_npr" to "Selling Price (NPR)",
            "stock_qty" to "Stock Qty",
            "min_alert_lvl" to "Min Alert Lvl",
            "error_name_barcode_required" to "Product name and barcode are required.",
            "error_invalid_numbers" to "Please enter valid numbers for price, stock, and threshold.",
            // ReportsScreen Translations
            "financial_reports" to "Financial Reports",
            "shop_performance_metrics" to "Shop Performance & Metrics",
            "sales" to "Sales",
            "returns" to "Returns",
            "overall_sales_summary" to "Overall Sales Summary",
            "total_revenue" to "Total Revenue",
            "sales_profit" to "Sales Profit",
            "cost_of_goods" to "Cost of Goods",
            "transactions" to "Transactions",
            "avg_order_value" to "Avg Order Value",
            "report_export_center" to "Report Export Center",
            "generate_audit_logs_desc" to "Generate instant audit logs and file reports directly from current store transaction metrics.",
            "excel_csv" to "Excel (CSV)",
            "print_pdf" to "Print (PDF)",
            "expense_bill_analytics" to "Expense & Bill Analytics",
            "sales_margins" to "Sales Margins",
            "total_expenses" to "Total Expenses",
            "net_income" to "Net Income",
            "net_income_calc_desc" to "Net Income is calculated as: Total Product Profit - Logged Expenditures (utilities, rent, snacks, salary, etc.).",
            "process_product_return" to "Process Product Return",
            "select_product_to_return" to "1. Select Product to Return",
            "qty_refund_details" to "2. Quantity & Refund Details",
            "original_payment_mode" to "3. Original Payment Mode",
            "search_product_or_bill" to "Search product name, barcode, or bill number...",
            "no_matching_products_bills" to "No matching products or bills found.",
            "quantity_label" to "Quantity: ",
            "refund_amount_label" to "Refund Amount (NPR)",
            "reason_for_return" to "Reason for Return",
            "original_invoice_id" to "Original Sale Invoice ID (Optional)",
            "confirm_return" to "Confirm Return",
            "returns_refunds_analytics" to "Returns & Refunds Analytics",
            "total_returns" to "Total Returns",
            "total_refunded" to "Total Refunded",
            "process_customer_return" to "Process Customer Return",
            "search_returns_placeholder" to "Search returns by product name or barcode...",
            "no_return_records_found" to "No return records found.",
            "barcode" to "Barcode",
            "qty" to "Qty",
            "reason" to "Reason",
            "returned_by" to "Returned By",
            "sale_id" to "Sale ID",
            "refund_amount" to "Refund Amount",
            "sales_transaction_log" to "Sales Transaction Log",
            "search_transactions" to "Search transactions...",
            "cash" to "Cash",
            "pos_terminal_restricted" to "POS Terminal Restricted",
            "pos_terminal_restricted_desc" to "Your cashier profile does not have permission to perform sales. Please contact the administrator or enter an authorized PIN to elevate access.",
            "enter_admin_pin" to "Enter Authorized Admin PIN",
            "incorrect_pin" to "Incorrect PIN. Try default '1111' or '1234'.",
            "simulated_scan" to "Simulated Scan",
            "checkout_details" to "Checkout Details",
            "customer_profile_optional" to "Customer Profile (Optional)",
            "customer_profile_required" to "Customer Profile (Required) *",
            "select_payment_mode" to "Select Payment Mode",
            "discount_label" to "Discount (NPR or %)",
            "capped_profit_warning" to "Discount capped to protect item profit margin!",
            "confirm_and_checkout" to "Confirm & Checkout",
            "checkout_success" to "Checkout Successful!",
            "receipt_copied" to "Receipt copied to clipboard!",
            "share_via_whatsapp" to "Share via WhatsApp",
            "share_via_email" to "Share via Email",
            "print_receipt" to "Print Receipt",
            "close" to "Close",
            "elevate_to_admin" to "Elevate to Admin",
            "switch_to_cashier" to "Switch to Cashier Role",
            "log_out_switch_profile" to "Log Out / Switch Profile",
            "scan_barcode" to "Scan Barcode",
            "instant_scanner" to "Instant Scanner",
            "scan_placeholder" to "Enter barcode manually or tap Scan",
            "add_item" to "Add Item",
            "quick_scan" to "Quick Scan",
            "empty_cart" to "Your cart is empty. Add stationery products from the catalog above or scan their barcodes to begin.",
            "total_due" to "Total Due",
            "cashier" to "Cashier",
            "stationery_shop_inventory" to "Stationery Shop Inventory",
            "scan_stationery_barcode" to "Scan Stationery Barcode",
            "close_camera" to "Close Camera",
            "open_camera_scanner" to "Open Camera Scanner",
            "align_product_barcode" to "Align product barcode within camera view",
            "enter_barcode_or_name" to "Enter Barcode / Product Name",
            "submit" to "Submit",
            "shopping_cart" to "Shopping Cart",
            "cart_is_empty" to "Cart is empty. Scan stationery barcode or search items to add.",
            "discount_applied" to "Discount Applied",
            "tax_included" to "Tax (Included)",
            "total_pay" to "Total Pay",
            "checkout_generate_invoice" to "Checkout & Generate Invoice",
            "elevate_terminal" to "Elevate Terminal",
            "cart_items" to "Cart Items",
            "cart_total" to "Cart Total",
            "low_stock_banner_alert" to "Alert: %d stationery items are below minimum stock limits! Tap to view.",
            "invoice_bill_summary" to "Invoice Bill Summary",
            "select_customer_account" to "Select Customer Account",
            "select_registered_customer" to "Select Registered Customer",
            "no_customers_store_credit" to "No customers with store credit account",
            "customer_mobile_whatsapp" to "Customer Mobile (WhatsApp sharing)",
            "online" to "Online",
            "credit" to "Credit",
            "enter_online_payment_details" to "Enter Online Payment Details",
            "payment_phone_required" to "Payment Phone Number (Required) *",
            "transaction_id_required" to "Transaction ID (Required) *",
            "credit_mode_customer_warning" to "⚠️ Please select a registered customer to use Credit payment mode.",
            "online_details_missing_warning" to "⚠️ Please enter Phone Number and Transaction ID to proceed.",
            "apply_discount_optional" to "Apply Discount (Optional)",
            "discount_percentage" to "Discount (%)",
            "discount_amount" to "Discount (NPR)",
            "discount_capped_warning" to "⚠️ Discount capped to NPR %.2f to keep price above the cost of NPR %.2f.",
            "complete_sale_print" to "Complete Sale & Print",
            "cancel_transaction" to "Cancel Transaction",
            "enter_admin_pin_desc" to "Please enter the Admin PIN to switch to Administrator mode.",
            "admin_pin_label" to "Admin PIN",
            "incorrect_admin_pin" to "Incorrect PIN. Please try default '1234'.",
            "verify" to "Verify",
            "bill_generated_success" to "Bill Generated Successfully!",
            "share_receipt_send" to "Share Receipt / Send",
            "send_via_email" to "Send via Email",
            "close_new_bill" to "Close / New Bill",
            "bar_prefix" to "Bar: %s",
            "price_each" to "NPR %.2f each",
            "customer_profile_required" to "Customer Profile (Required) *",
            "customer_profile_optional" to "Customer Profile (Optional)",
            // CloudSyncScreen Translations
            "cloud_vault" to "Cloud Vault",
            "storage_reports_backups" to "Storage, Reports & Backups",
            "vault_connected_active" to "Vault Connected & Active",
            "connected_to_vault" to "Connected to Vault",
            "cloud_connection_setup" to "Cloud Connection Setup",
            "cloud_vault_desc" to "To sync stationery data or restore previous states across instances, create a new Cloud Storage Vault, or enter an existing Vault ID.",
            "cloud_vault_id" to "Cloud Vault ID",
            "connect_id" to "Connect ID",
            "new_vault" to "New Vault",
            "copy_vault_id" to "Copy Vault ID",
            "disconnect" to "Disconnect",
            "synchronize_backups" to "Synchronize & Backups",
            "auto_sync_on_checkouts" to "Auto-Sync on Checkouts",
            "sync_inventory_desc" to "Syncs inventory after each successful bill",
            "cloud_vault_id_set" to "Cloud Vault ID set!",
            // SuperAdminScreen Translations
            "super_admin_terminal" to "Super Admin Terminal",
            "security_permissions_hub" to "Security & Permissions Hub",
            "granular_access_enforcement" to "Granular Access Enforcement",
            "super_admin_desc" to "Super Admins can define custom role capabilities, configure password pins, and disable staff access instantly.",
            "assigned_capabilities" to "Assigned Capabilities:",
            "inventory_access" to "Inventory Access",
            "reports_analytics" to "Reports & Analytics",
            "pos_sell_terminal" to "POS Sell Terminal",
            "gemini_ai_advisor" to "Gemini AI Advisor",
            "super_admin_powers" to "Super Admin Powers",
            "admin_role" to "Admin",
            "cashier_staff_role" to "Cashier/Staff",
            "active" to "Active",
            "you" to "You"
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
            "store_credit" to "स्टोर क्रेडिट खाताहरू",
            // Product Form Dialog Translations (NE)
            "add_new_stationery_item" to "नयाँ स्टेशनरी सामान थप्नुहोस्",
            "edit_stationery_item" to "स्टेशनरी सामान सम्पादन गर्नुहोस्",
            "barcode_number" to "बारकोड नम्बर",
            "generate" to "उत्पादन गर्नुहोस्",
            "one_d_barcode" to "१D बारकोड",
            "qr_sku" to "QR कोड SKU",
            "scan_camera" to "क्यामेरा मार्फत सामानको बारकोड वा QR कोड स्क्यान गर्नुहोस्",
            "emulator_scan" to "इम्युलेटर? स्क्यान सिमुलेट गर्नुहोस्:",
            "random_barcode" to "रैंडम बारकोड",
            "sku_alphanumeric" to "अल्फान्यूमेरिक SKU",
            "add_custom_category" to "कस्टम वर्ग थप्नुहोस्",
            "custom_category_name" to "कस्टम वर्गको नाम",
            "select_existing_category" to "अवस्थित वर्ग चयन गर्नुहोस्",
            "cost_price_npr" to "लागत मूल्य (NPR)",
            "selling_price_npr" to "बिक्री मूल्य (NPR)",
            "stock_qty" to "स्टक मात्रा",
            "min_alert_lvl" to "न्यूनतम चेतावनी स्तर",
            "error_name_barcode_required" to "सामानको नाम र बारकोड आवश्यक छ।",
            "error_invalid_numbers" to "कृपया मूल्य, स्टक र चेतावनी स्तरका लागि मान्य नम्बरहरू हाल्नुहोस्।",
            // ReportsScreen Translations (NE)
            "financial_reports" to "वित्तीय विवरण",
            "shop_performance_metrics" to "पसलको कार्यसम्पादन र सूचकहरू",
            "sales" to "बिक्री",
            "returns" to "फिर्ता विवरण",
            "overall_sales_summary" to "समग्र बिक्री सारांश",
            "total_revenue" to "कुल राजस्व",
            "sales_profit" to "बिक्री नाफा",
            "cost_of_goods" to "सामानको लागत",
            "transactions" to "कारोबार संख्या",
            "avg_order_value" to "औसत अर्डर मूल्य",
            "report_export_center" to "रिपोर्ट निर्यात केन्द्र",
            "generate_audit_logs_desc" to "स्टोरको हालको कारोबारबाट अडिट रेकर्ड र फाइल रिपोर्टहरू सिर्जना गर्नुहोस्।",
            "excel_csv" to "एक्सेल (CSV)",
            "print_pdf" to "प्रिन्ट (PDF)",
            "expense_bill_analytics" to "खर्च र बिल विश्लेषण",
            "sales_margins" to "बिक्री मार्जिन",
            "total_expenses" to "कुल खर्च",
            "net_income" to "खुद आम्दानी",
            "net_income_calc_desc" to "खुद आम्दानी यसरी गणना गरिन्छ: कुल उत्पादन नाफा - रेकर्ड गरिएको खर्च (बिजुली, भाडा, खाजा, तलब, आदि)।",
            "process_product_return" to "सामान फिर्ता प्रक्रिया",
            "select_product_to_return" to "१. फिर्ता गर्ने सामान छान्नुहोस्",
            "qty_refund_details" to "२. परिमाण र फिर्ता रकम विवरण",
            "original_payment_mode" to "३. भुक्तानीको तरिका",
            "search_product_or_bill" to "सामानको नाम, बारकोड वा बिल नम्बर खोज्नुहोस्...",
            "no_matching_products_bills" to "कुनै सामान वा बिल भेटिएन।",
            "quantity_label" to "परिमाण: ",
            "refund_amount_label" to "फिर्ता रकम (NPR)",
            "reason_for_return" to "फिर्ता गर्नुको कारण",
            "original_invoice_id" to "मूल बिक्री बिल नम्बर (वैकल्पिक)",
            "confirm_return" to "फिर्ता पुष्टि गर्नुहोस्",
            "returns_refunds_analytics" to "फिर्ता र फिर्ता भुक्तानी विश्लेषण",
            "total_returns" to "कुल फिर्ता सामान",
            "total_refunded" to "कुल फिर्ता रकम",
            "process_customer_return" to "ग्राहक फिर्ता प्रक्रिया",
            "search_returns_placeholder" to "सामानको नाम वा बारकोडद्वारा फिर्ता खोज्नुहोस्...",
            "no_return_records_found" to "कुनै फिर्ता रेकर्ड फेला परेन।",
            "barcode" to "बारकोड",
            "qty" to "परिमाण",
            "reason" to "कारण",
            "returned_by" to "फिर्ता गर्ने",
            "sale_id" to "बिक्री नम्बर",
            "refund_amount" to "फिर्ता रकम",
            "sales_transaction_log" to "बिक्री कारोबार लग",
            "search_transactions" to "कारोबार खोज्नुहोस्...",
            "cash" to "नगद",
            "pos_terminal_restricted" to "पीओएस टर्मिनल प्रतिबन्धित",
            "pos_terminal_restricted_desc" to "तपाईको क्यासियर प्रोफाइलसँग बिक्री गर्ने अनुमति छैन। कृपया प्रशासकलाई सम्पर्क गर्नुहोस् वा पहुँच बढाउन अधिकृत पिन प्रविष्ट गर्नुहोस्।",
            "enter_admin_pin" to "प्रशासकको पिन प्रविष्ट गर्नुहोस्",
            "incorrect_pin" to "गलत पिन। पूर्वनिर्धारित '1111' वा '1234' प्रयास गर्नुहोस्।",
            "simulated_scan" to "सिमुलेटेड स्क्यान",
            "checkout_details" to "चेकआउट विवरण",
            "customer_profile_optional" to "ग्राहक प्रोफाइल (वैकल्पिक)",
            "customer_profile_required" to "ग्राहक प्रोफाइल (आवश्यक) *",
            "select_payment_mode" to "भुक्तानीको तरिका चयन गर्नुहोस्",
            "discount_label" to "छुट (NPR वा %)",
            "capped_profit_warning" to "सामान नाफा मार्जिन सुरक्षित गर्न छुट सीमित गरिएको छ!",
            "confirm_and_checkout" to "पुष्टि गर्नुहोस् र भुक्तानी गर्नुहोस्",
            "checkout_success" to "भुक्तानी सफल भयो!",
            "receipt_copied" to "रसिद क्लिपबोर्डमा प्रतिलिपि गरियो!",
            "share_via_whatsapp" to "व्हाट्सएप मार्फत साझा गर्नुहोस्",
            "share_via_email" to "इमेल मार्फत साझा गर्नुहोस्",
            "print_receipt" to "रसिद प्रिन्ट गर्नुहोस्",
            "close" to "बन्द गर्नुहोस्",
            "elevate_to_admin" to "प्रशासक बन्नुहोस्",
            "switch_to_cashier" to "क्यासियर भूमिकामा स्विच गर्नुहोस्",
            "log_out_switch_profile" to "लगआउट / प्रोफाइल स्विच गर्नुहोस्",
            "scan_barcode" to "बारकोड स्क्यान गर्नुहोस्",
            "instant_scanner" to "तुरुन्त स्क्यानर",
            "scan_placeholder" to "बारकोड म्यानुअल रूपमा प्रविष्ट गर्नुहोस् वा स्क्यान थिच्नुहोस्",
            "add_item" to "सामान थप्नुहोस्",
            "quick_scan" to "द्रुत स्क्यान",
            "empty_cart" to "तपाईको कार्ट खाली छ। माथिको क्याटलगबाट स्टेशनरी सामानहरू थप्नुहोस् वा सुरु गर्न तिनीहरूको बारकोडहरू स्क्यान गर्नुहोस्।",
            "total_due" to "तिर्नुपर्ने कुल रकम",
            "cashier" to "क्यासियर",
            "stationery_shop_inventory" to "स्टेशनरी पसल सूची",
            "scan_stationery_barcode" to "स्टेशनरी बारकोड स्क्यान गर्नुहोस्",
            "close_camera" to "क्यामेरा बन्द गर्नुहोस्",
            "open_camera_scanner" to "क्यामेरा स्क्यानर खोल्नुहोस्",
            "align_product_barcode" to "क्यामेरा दृश्य भित्र सामानको बारकोड मिलाउनुहोस्",
            "enter_barcode_or_name" to "बारकोड वा सामानको नाम हाल्नुहोस्",
            "submit" to "बुझाउनुहोस्",
            "shopping_cart" to "शपिङ कार्ट",
            "cart_is_empty" to "कार्ट खाली छ। थप्नको लागि स्टेशनरी बारकोड स्क्यान गर्नुहोस् वा सामान खोज्नुहोस्।",
            "discount_applied" to "छुट लागू गरियो",
            "tax_included" to "कर (समावेश)",
            "total_pay" to "कुल भुक्तानी",
            "checkout_generate_invoice" to "भुक्तानी गर्नुहोस् र बिल निकाल्नुहोस्",
            "elevate_terminal" to "टर्मिनल उन्नत गर्नुहोस्",
            "cart_items" to "कार्टका सामानहरू",
            "cart_total" to "कार्ट जम्मा",
            "low_stock_banner_alert" to "चेतावनी: %d स्टेशनरी सामानहरू न्यूनतम स्टक सीमा भन्दा कम छन्! हेर्नको लागि ट्याप गर्नुहोस्।",
            "invoice_bill_summary" to "बीजक बिल सारांश",
            "select_customer_account" to "ग्राहक खाता चयन गर्नुहोस्",
            "select_registered_customer" to "दर्ता गरिएको ग्राहक चयन गर्नुहोस्",
            "no_customers_store_credit" to "स्टोर क्रेडिट खाता भएका कुनै ग्राहकहरू छैनन्",
            "customer_mobile_whatsapp" to "ग्राहक मोबाइल (व्हाट्सएप साझेदारी)",
            "online" to "अनलाइन",
            "credit" to "उधारो (क्रेडिट)",
            "enter_online_payment_details" to "अनलाइन भुक्तानी विवरणहरू प्रविष्ट गर्नुहोस्",
            "payment_phone_required" to "भुक्तानी फोन नम्बर (अनिवार्य) *",
            "transaction_id_required" to "कारोबार आईडी (अनिवार्य) *",
            "credit_mode_customer_warning" to "⚠️ कृपया क्रेडिट भुक्तानी मोड प्रयोग गर्न दर्ता गरिएको ग्राहक चयन गर्नुहोस्।",
            "online_details_missing_warning" to "⚠️ कृपया अगाडि बढ्नको लागि फोन नम्बर र कारोबार आईडी प्रविष्ट गर्नुहोस्।",
            "apply_discount_optional" to "छुट लागू गर्नुहोस् (वैकल्पिक)",
            "discount_percentage" to "छुट (%)",
            "discount_amount" to "छुट (NPR)",
            "discount_capped_warning" to "⚠️ मूल्यलाई लागत मूल्य NPR %.2f भन्दा माथि राख्नको लागि छुट NPR %.2f मा सीमित गरिएको छ।",
            "complete_sale_print" to "बिक्री पूरा गर्नुहोस् र प्रिन्ट गर्नुहोस्",
            "cancel_transaction" to "कारोबार रद्द गर्नुहोस्",
            "enter_admin_pin_desc" to "कृपया प्रशासक मोडमा स्विच गर्न प्रशासकको पिन प्रविष्ट गर्नुहोस्।",
            "admin_pin_label" to "प्रशासकको पिन",
            "incorrect_admin_pin" to "गलत पिन। कृपया पूर्वनिर्धारित '1234' प्रयास गर्नुहोस्।",
            "verify" to "प्रमाणित गर्नुहोस्",
            "bill_generated_success" to "बिल सफलतापूर्वक सिर्जना भयो!",
            "share_receipt_send" to "रसिद साझा गर्नुहोस् / पठाउनुहोस्",
            "send_via_email" to "इमेल मार्फत पठाउनुहोस्",
            "close_new_bill" to "बन्द गर्नुहोस् / नयाँ बिल",
            "bar_prefix" to "बारकोड: %s",
            "price_each" to "प्रति इकाई: NPR %.2f",
            "customer_profile_required" to "ग्राहक विवरण (अनिवार्य) *",
            "customer_profile_optional" to "ग्राहक विवरण (वैकल्पिक)",
            // CloudSyncScreen Translations (NE)
            "cloud_vault" to "क्लाउड भल्ट",
            "storage_reports_backups" to "भण्डारण, रिपोर्ट र ब्याकअपहरू",
            "vault_connected_active" to "भल्ट जोडिएको र सक्रिय छ",
            "connected_to_vault" to "भल्टसँग जोडिएको",
            "cloud_connection_setup" to "क्लाउड जडान सेटअप",
            "cloud_vault_desc" to "स्टेशनरी डेटा सिंक गर्न वा ब्याकअप रिस्टोर गर्न, नयाँ क्लाउड स्टोरेज भल्ट बनाउनुहोस् वा अवस्थित भल्ट आईडी हाल्नुहोस्।",
            "cloud_vault_id" to "क्लाउड भल्ट आईडी",
            "connect_id" to "आईडी जडान गर्नुहोस्",
            "new_vault" to "नयाँ भल्ट",
            "copy_vault_id" to "भल्ट आईडी प्रतिलिपि गर्नुहोस्",
            "disconnect" to "जडान विच्छेद गर्नुहोस्",
            "synchronize_backups" to "सिंक्रोनाइज र ब्याकअप",
            "auto_sync_on_checkouts" to "चेकआउट गर्दा स्वचालित सिंक",
            "sync_inventory_desc" to "प्रत्येक सफल बिल पछि सूची सिंक गर्दछ",
            "cloud_vault_id_set" to "क्लाउड भल्ट आईडी सेट भयो!",
            // SuperAdminScreen Translations (NE)
            "super_admin_terminal" to "सुपर एडमिन टर्मिनल",
            "security_permissions_hub" to "सुरक्षा र अनुमति केन्द्र",
            "granular_access_enforcement" to "विस्तृत पहुँच नियन्त्रण",
            "super_admin_desc" to "सुपर एडमिनहरूले भूमिकाहरू परिभाषित गर्न, पासवर्ड पिनहरू सेट गर्न र स्टाफ पहुँच तत्काल बन्द गर्न सक्छन्।",
            "assigned_capabilities" to "तोकिएका सुविधाहरू:",
            "inventory_access" to "सूची पहुँच (Inventory)",
            "reports_analytics" to "रिपोर्ट र विश्लेषण",
            "pos_sell_terminal" to "बिक्री टर्मिनल (POS)",
            "gemini_ai_advisor" to "जेमिनी एआई सल्लाहकार",
            "super_admin_powers" to "सुपर एडमिन अधिकारहरू",
            "admin_role" to "प्रशासक",
            "cashier_staff_role" to "क्यासियर / कर्मचारी",
            "active" to "सक्रिय",
            "you" to "तपाईं"
        )
    )

    fun translate(key: String, lang: String): String {
        val raw = translations[lang]?.get(key) ?: translations["en"]?.get(key) ?: key
        return translateDigitsInternal(raw, lang)
    }
}

private fun translateDigitsInternal(text: String, lang: String): String {
    if (lang != "ne") return text
    return text.map { char ->
        when (char) {
            '0' -> '०'
            '1' -> '१'
            '2' -> '२'
            '3' -> '३'
            '4' -> '४'
            '5' -> '५'
            '6' -> '६'
            '7' -> '७'
            '8' -> '८'
            '9' -> '९'
            else -> char
        }
    }.joinToString("")
}

fun String.translateDigits(lang: String): String {
    return translateDigitsInternal(this, lang)
}

@Composable
fun tNum(value: Any, viewModel: com.example.ui.InventoryViewModel): String {
    val lang by viewModel.appLanguage.collectAsState()
    return value.toString().translateDigits(lang)
}

@Composable
fun String.tNum(viewModel: com.example.ui.InventoryViewModel): String {
    val lang by viewModel.appLanguage.collectAsState()
    return this.translateDigits(lang)
}

@Composable
fun Number.tNum(viewModel: com.example.ui.InventoryViewModel): String {
    val lang by viewModel.appLanguage.collectAsState()
    return this.toString().translateDigits(lang)
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
