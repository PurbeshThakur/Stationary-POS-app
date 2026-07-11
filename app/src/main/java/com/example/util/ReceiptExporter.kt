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
     * Prints pre-formatted HTML content directly to a headless WebView and executing the Android PrintManager flow.
     */
    fun printHtml(context: Context, htmlContent: String, jobName: String = "Label") {
        try {
            val webView = WebView(context)
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                    val printAdapter = webView.createPrintDocumentAdapter(jobName)
                    printManager.print("Ink & Paper - $jobName", printAdapter, PrintAttributes.Builder().build())
                }
            }
            webView.loadDataWithBaseURL(null, htmlContent, "text/html", "utf-8", null)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to initialize printer: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Generates a beautiful, realistic vector SVG representation of a QR Code.
     */
    fun generateQrSvg(barcode: String): String {
        val cleanCode = if (barcode.isBlank()) "0000" else barcode
        val size = 200
        return try {
            val writer = com.google.zxing.qrcode.QRCodeWriter()
            val bitMatrix = writer.encode(cleanCode, com.google.zxing.BarcodeFormat.QR_CODE, size, size)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val sb = java.lang.StringBuilder()
            sb.append("<svg width=\"$width\" height=\"$height\" viewBox=\"0 0 $width $height\" xmlns=\"http://www.w3.org/2000/svg\">\n")
            sb.append("  <rect width=\"100%\" height=\"100%\" fill=\"#ffffff\"/>\n")
            sb.append("  <path d=\"")
            for (y in 0 until height) {
                var startX = -1
                for (x in 0 until width) {
                    if (bitMatrix.get(x, y)) {
                        if (startX == -1) {
                            startX = x
                        }
                    } else {
                        if (startX != -1) {
                            sb.append("M$startX,${y}h${x - startX}v1h-${x - startX}z ")
                            startX = -1
                        }
                    }
                }
                if (startX != -1) {
                    sb.append("M$startX,${y}h${width - startX}v1h-${width - startX}z ")
                }
            }
            sb.append("\" fill=\"#000000\"/>\n")
            sb.append("</svg>")
            sb.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            "<svg width=\"100\" height=\"100\" viewBox=\"0 0 100 100\" xmlns=\"http://www.w3.org/2000/svg\"><rect width=\"100\" height=\"100\" fill=\"#ffcccc\"/><text x=\"10\" y=\"50\" fill=\"red\">QR Error</text></svg>"
        }
    }

    /**
     * Generates a clean, crisp vector SVG representation of a 1D Barcode.
     */
    fun generateBarcodeSvg(barcode: String): String {
        val cleanCode = if (barcode.isBlank()) "000000" else barcode
        val width = 250
        val height = 65
        return try {
            val writer = com.google.zxing.MultiFormatWriter()
            val bitMatrix = writer.encode(cleanCode, com.google.zxing.BarcodeFormat.CODE_128, width, height)
            val matrixWidth = bitMatrix.width
            val matrixHeight = bitMatrix.height
            val sb = java.lang.StringBuilder()
            sb.append("<svg width=\"$matrixWidth\" height=\"$matrixHeight\" viewBox=\"0 0 $matrixWidth $matrixHeight\" xmlns=\"http://www.w3.org/2000/svg\">\n")
            sb.append("  <rect width=\"100%\" height=\"100%\" fill=\"#ffffff\"/>\n")
            sb.append("  <path d=\"")
            for (y in 0 until matrixHeight) {
                var startX = -1
                for (x in 0 until matrixWidth) {
                    if (bitMatrix.get(x, y)) {
                        if (startX == -1) {
                            startX = x
                        }
                    } else {
                        if (startX != -1) {
                            sb.append("M$startX,${y}h${x - startX}v1h-${x - startX}z ")
                            startX = -1
                        }
                    }
                }
                if (startX != -1) {
                    sb.append("M$startX,${y}h${matrixWidth - startX}v1h-${matrixWidth - startX}z ")
                }
            }
            sb.append("\" fill=\"#000000\"/>\n")
            sb.append("</svg>")
            sb.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            "<svg width=\"200\" height=\"50\" viewBox=\"0 0 200 50\" xmlns=\"http://www.w3.org/2000/svg\"><rect width=\"200\" height=\"50\" fill=\"#ffcccc\"/><text x=\"10\" y=\"30\" fill=\"red\">Barcode Error</text></svg>"
        }
    }

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

    /**
     * Saves CSV report to downloads
     */
    fun saveCsvReportToDownloads(context: Context, csvText: String, reportName: String): Boolean {
        val fileName = "${reportName}_Report_${System.currentTimeMillis() / 1000}.csv"
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outputStream: OutputStream ->
                        outputStream.write(csvText.toByteArray())
                    }
                    Toast.makeText(context, "Exported CSV to Downloads: $fileName", Toast.LENGTH_LONG).show()
                    return true
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }
                val file = File(downloadsDir, fileName)
                file.writeText(csvText)
                Toast.makeText(context, "Exported CSV to: ${file.absolutePath}", Toast.LENGTH_LONG).show()
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to export report: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
        return false
    }
}
