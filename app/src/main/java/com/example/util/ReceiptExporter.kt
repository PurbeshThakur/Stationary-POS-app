package com.example.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.MediaStore
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import java.io.File
import java.io.OutputStream

object ReceiptExporter {

    /**
     * Prints the receipt text by loading it into a headless WebView and executing the Android PrintManager flow.
     * This allows printing to a physical printer or downloading/saving as a PDF on the device.
     */
    fun printReceipt(context: Context, receiptText: String, invoiceName: String = "Ink_Paper_Receipt") {
        try {
            val webView = WebView(context)
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                    val printAdapter = webView.createPrintDocumentAdapter(invoiceName)
                    val jobName = "Ink & Paper - $invoiceName"
                    printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
                }
            }

            // Format receiptText nicely in HTML with a clean monospaced layout suitable for printing
            val htmlContent = """
                <html>
                <head>
                <meta charset="utf-8">
                <style>
                    body {
                        font-family: monospace;
                        white-space: pre-wrap;
                        font-size: 13px;
                        line-height: 1.4;
                        padding: 16px;
                        color: #000000;
                        background-color: #ffffff;
                    }
                </style>
                </head>
                <body>
                    ${receiptText.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>").replace(" ", "&nbsp;")}
                </body>
                </html>
            """.trimIndent()

            webView.loadDataWithBaseURL(null, htmlContent, "text/html", "utf-8", null)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to initialize printer: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Saves the receipt text file cleanly into the public Downloads directory.
     * Works seamlessly on all Android versions, bypassing storage permission prompts on Android 10+.
     */
    fun saveReceiptToDownloads(context: Context, receiptText: String, invoiceId: String): Boolean {
        val fileName = "Ink_Paper_Receipt_$invoiceId.txt"
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outputStream: OutputStream ->
                        outputStream.write(receiptText.toByteArray())
                    }
                    Toast.makeText(context, "Saved bill to Downloads: $fileName", Toast.LENGTH_LONG).show()
                    return true
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }
                val file = File(downloadsDir, fileName)
                file.writeText(receiptText)
                Toast.makeText(context, "Saved bill to: ${file.absolutePath}", Toast.LENGTH_LONG).show()
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to download bill: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
        return false
    }
}
