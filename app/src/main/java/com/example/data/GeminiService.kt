package com.example.data

import com.example.BuildConfig
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// --- Gemini API Request / Response Models (using Moshi) ---

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class Part(
    val text: String
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    val candidates: List<Candidate>? = null
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: Content? = null
)

// --- Retrofit API Service ---

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object GeminiRetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }
}

class GeminiService {
    suspend fun getSalesIncreaseTips(
        totalProducts: Int,
        totalSalesCount: Int,
        totalRevenue: Double,
        totalProfit: Double,
        lowStockCount: Int,
        lowStockNames: String,
        topCategories: String
    ): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "Please configure your GEMINI_API_KEY in the AI Studio Secrets panel to get tailored retail stationery sales recommendations."
        }

        val prompt = """
            You are an expert retail stationery store consultant.
            Here is the current performance data of our stationery shop:
            - Total Distinct Products: $totalProducts
            - Total Sales Transactions: $totalSalesCount
            - Total Revenue Generated: $$totalRevenue
            - Total Net Profit: $$totalProfit
            - Items running out of stock (Low Stock Alert): $lowStockCount ($lowStockNames)
            - Top Selling Stationery Categories: $topCategories

            Provide:
            1. 3 highly specific, creative and actionable marketing tips to increase stationery sales (e.g. bundle packs, themed desk setups, social media stationery aesthetics, school/college prep promotions).
            2. Direct inventory restocking and pricing suggestions based on our low-stock items.
            3. Keep the response clean, concise, formatted in readable bullet points with bold headers, and extremely practical for a local stationery merchant.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(
                Content(parts = listOf(Part(text = prompt)))
            ),
            generationConfig = GenerationConfig(temperature = 0.7f),
            systemInstruction = Content(
                parts = listOf(Part(text = "You are a professional stationery retail consultant. Speak with local shop merchants warmly and clearly."))
            )
        )

        try {
            val response = GeminiRetrofitClient.service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text 
                ?: "No suggestions generated at this time. Please check your inventory or sales data and try again."
        } catch (e: Exception) {
            e.printStackTrace()
            "Error loading AI suggestions: ${e.localizedMessage ?: "Network error"}. Please make sure your API key is correctly configured and try again."
        }
    }
}
