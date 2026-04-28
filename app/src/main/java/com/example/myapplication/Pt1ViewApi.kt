package com.example.myapplication

import android.content.Context
import com.google.gson.annotations.SerializedName
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// Data class for asset response
data class AssetResponse(
    @SerializedName("dbId") val dbId: String,
    @SerializedName("url") val url: String?,
    @SerializedName("actorType") val actorType: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("lifeCycle") val lifeCycle: String?
)

// Data class for alarm response
data class AlarmConfig(
    @SerializedName("name") val name: String?
)

data class AlarmResponse(
    @SerializedName("id") val id: String?,
    @SerializedName("alarmConfig") val alarmConfig: AlarmConfig?,
    @SerializedName("alarmType") val alarmType: String?,
    @SerializedName("severity") val severity: String?,
    @SerializedName("assetName") val assetName: String?,
    @SerializedName("description") val description: String?
)

// Retrofit API interface
interface Pt1ViewApi {
    @GET("services/api/views/tireConfigurations/currentAssets")
    suspend fun getAssetFromSensor(
        @Query("sensorName") sensorName: String
    ): Response<AssetResponse>

    @GET("services/api/alarms")
    suspend fun getAlarmsForAsset(
        @Query("assets") assetId: String,
        @Query("sortAscending") sortAscending: Boolean = false,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 25,
        @Query("returnTotalCount") returnTotalCount: Boolean = false
    ): Response<List<AlarmResponse>>
}

// API Client singleton
object ApiClient {
    private const val BASE_URL = "https://testing.pt1view.com/"
    private var currentUsername: String = ""
    private var currentPassword: String = ""

    // Initialize credentials from SharedPreferences
    fun init(context: Context) {
        val sharedPrefs = context.getSharedPreferences("LoginPrefs", Context.MODE_PRIVATE)
        currentUsername = sharedPrefs.getString("username", "") ?: ""
        currentPassword = sharedPrefs.getString("password", "") ?: ""
    }

    private val authInterceptor = Interceptor { chain ->
        val credential = Credentials.basic(currentUsername, currentPassword)
        val request = chain.request().newBuilder()
            .header("Authorization", credential)
            .build()
        chain.proceed(request)
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: Pt1ViewApi = retrofit.create(Pt1ViewApi::class.java)
}