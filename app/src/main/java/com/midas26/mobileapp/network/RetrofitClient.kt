package com.midas26.mobileapp.network

import com.midas26.mobileapp.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 401 응답을 "로그인이 만료됐다"로 보고 강제 로그아웃할지 판단한다.
 *
 * 모든 401 을 만료로 처리하면 안 되는 경우:
 *  - 로그인 요청의 401 은 "비밀번호가 틀렸다"는 뜻이다. (로그인 화면, 프로필의 현재 비밀번호 확인)
 *  - 토큰 없이 보낸 요청의 401 은 만료가 아니라 애초에 로그인 상태가 아닌 것이다.
 */
internal fun shouldForceLogout(responseCode: Int, requestPath: String, hadToken: Boolean): Boolean {
    if (responseCode != 401) return false
    if (!hadToken) return false
    if (requestPath.trimEnd('/').endsWith("api/auth/login")) return false
    return true
}

object RetrofitClient {

    private const val BASE_URL = "http://10.0.2.2:8080/"

    // 음성 업로드는 서버가 응답 전에 음성 인식까지 수행하므로 기본값(10초)으로는 부족하다.
    private const val CONNECT_TIMEOUT_SECONDS = 10L
    private const val READ_WRITE_TIMEOUT_SECONDS = 60L

    private var tokenProvider: (() -> String?)? = null
    private var onUnauthorized: (() -> Unit)? = null

    fun init(tokenProvider: () -> String?, onUnauthorized: (() -> Unit)? = null) {
        this.tokenProvider = tokenProvider
        this.onUnauthorized = onUnauthorized
    }

    // 요청·응답 본문에는 비밀번호와 토큰이 들어 있으므로 디버그 빌드에서만 기록한다.
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    private val authInterceptor = okhttp3.Interceptor { chain ->
        val token = tokenProvider?.invoke()
        val hasToken = !token.isNullOrBlank()
        val request = if (hasToken) {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        val response = chain.proceed(request)
        if (shouldForceLogout(response.code, request.url.encodedPath, hasToken)) {
            onUnauthorized?.invoke()
        }
        response
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(READ_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .build()

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val instance: AuthApiService by lazy {
        retrofit.create(AuthApiService::class.java)
    }

    val voiceChat: VoiceChatApiService by lazy {
        retrofit.create(VoiceChatApiService::class.java)
    }

    val location: LocationApiService by lazy {
        retrofit.create(LocationApiService::class.java)
    }

    val analysis: AnalysisApiService by lazy {
        retrofit.create(AnalysisApiService::class.java)
    }
}
