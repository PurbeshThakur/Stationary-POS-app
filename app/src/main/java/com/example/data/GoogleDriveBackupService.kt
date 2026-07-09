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
data class GoogleDriveFile(
    val id: String,
    val name: String,
    val mimeType: String
)

@JsonClass(generateAdapter = true)
data class GoogleDriveSearchResponse(
    val files: List<GoogleDriveFile>
)

class GoogleDriveBackupService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val payloadAdapter = moshi.adapter(CloudBackupPayload::class.java)
    private val searchResponseAdapter = moshi.adapter(GoogleDriveSearchResponse::class.java)

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun findBackupFile(accessToken: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files?q=name='Purbesh_Stationery_Backup.json'+and+trashed=false&spaces=drive")
                .get()
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("GoogleDriveService", "Search failed: ${response.code} ${response.message}")
                    return@withContext null
                }
                val bodyStr = response.body?.string() ?: return@withContext null
                val searchRes = searchResponseAdapter.fromJson(bodyStr)
                return@withContext searchRes?.files?.firstOrNull()?.id
            }
        } catch (e: Exception) {
            Log.e("GoogleDriveService", "Exception in findBackupFile", e)
            null
        }
    }

    suspend fun createBackupFile(accessToken: String): String? = withContext(Dispatchers.IO) {
        try {
            val metadataJson = """
                {
                    "name": "Purbesh_Stationery_Backup.json",
                    "mimeType": "application/json"
                }
            """.trimIndent()

            val requestBody = metadataJson.toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files")
                .post(requestBody)
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("GoogleDriveService", "Create file failed: ${response.code} ${response.message}")
                    return@withContext null
                }
                val bodyStr = response.body?.string() ?: return@withContext null
                val file = moshi.adapter(GoogleDriveFile::class.java).fromJson(bodyStr)
                return@withContext file?.id
            }
        } catch (e: Exception) {
            Log.e("GoogleDriveService", "Exception in createBackupFile", e)
            null
        }
    }

    suspend fun uploadBackupData(accessToken: String, fileId: String, payload: CloudBackupPayload): Boolean = withContext(Dispatchers.IO) {
        try {
            val jsonBody = payloadAdapter.toJson(payload)
            val requestBody = jsonBody.toRequestBody(jsonMediaType)

            val request = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media")
                .patch(requestBody)
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Content-Type", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("GoogleDriveService", "Upload failed: ${response.code} ${response.message}")
                    return@withContext false
                }
                Log.d("GoogleDriveService", "Successfully updated file content on Google Drive")
                true
            }
        } catch (e: Exception) {
            Log.e("GoogleDriveService", "Exception in uploadBackupData", e)
            false
        }
    }

    suspend fun fetchBackupData(accessToken: String, fileId: String): CloudBackupPayload? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
                .get()
                .addHeader("Authorization", "Bearer $accessToken")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("GoogleDriveService", "Fetch failed: ${response.code} ${response.message}")
                    return@withContext null
                }
                val bodyStr = response.body?.string() ?: return@withContext null
                return@withContext payloadAdapter.fromJson(bodyStr)
            }
        } catch (e: Exception) {
            Log.e("GoogleDriveService", "Exception in fetchBackupData", e)
            null
        }
    }
}
