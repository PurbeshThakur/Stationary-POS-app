package com.example.data

import android.util.Log
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class CloudBackupPayload(
    val products: List<Product>,
    val sales: List<Sale>,
    val saleItems: List<SaleItem>,
    val formattedReport: String? = null,
    val backupTimestamp: Long = System.currentTimeMillis(),
    val storeName: String = "Purbesh Stationery"
)

class CloudBackupService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val payloadAdapter = moshi.adapter(CloudBackupPayload::class.java)

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Creates a new cloud storage vault with the initial payload.
     * Returns the generated Cloud Vault ID (blob ID) if successful, or null.
     */
    suspend fun createNewVault(payload: CloudBackupPayload): String? = withContext(Dispatchers.IO) {
        try {
            val jsonBody = payloadAdapter.toJson(payload)
            val requestBody = jsonBody.toRequestBody(jsonMediaType)

            val request = Request.Builder()
                .url("https://jsonblob.com/api/jsonBlob")
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("CloudBackupService", "Failed to create vault: ${response.code} ${response.message}")
                    return@withContext null
                }

                val locationUrl = response.header("Location") ?: response.header("location")
                if (locationUrl != null) {
                    val id = locationUrl.substringAfterLast("/")
                    Log.d("CloudBackupService", "Created cloud vault with ID: $id")
                    return@withContext id
                }
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e("CloudBackupService", "Exception in createNewVault", e)
            return@withContext null
        }
    }

    /**
     * Backs up (overwrites) existing cloud vault data.
     */
    suspend fun updateVault(vaultId: String, payload: CloudBackupPayload): Boolean = withContext(Dispatchers.IO) {
        try {
            val jsonBody = payloadAdapter.toJson(payload)
            val requestBody = jsonBody.toRequestBody(jsonMediaType)

            val request = Request.Builder()
                .url("https://jsonblob.com/api/jsonBlob/$vaultId")
                .put(requestBody)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("CloudBackupService", "Failed to update vault $vaultId: ${response.code}")
                    return@withContext false
                }
                Log.d("CloudBackupService", "Successfully updated cloud vault $vaultId")
                return@withContext true
            }
        } catch (e: Exception) {
            Log.e("CloudBackupService", "Exception in updateVault", e)
            return@withContext false
        }
    }

    /**
     * Restores (downloads) the cloud payload for a given vault ID.
     */
    suspend fun fetchVault(vaultId: String): CloudBackupPayload? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://jsonblob.com/api/jsonBlob/$vaultId")
                .get()
                .addHeader("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("CloudBackupService", "Failed to fetch vault $vaultId: ${response.code}")
                    return@withContext null
                }

                val bodyJson = response.body?.string() ?: return@withContext null
                return@withContext payloadAdapter.fromJson(bodyJson)
            }
        } catch (e: Exception) {
            Log.e("CloudBackupService", "Exception in fetchVault", e)
            return@withContext null
        }
    }
}
