package com.loy.mingclaw.core.network.internal

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class AuthInterceptor @Inject constructor() : Interceptor {
    @Volatile
    var apiKey: String = ""

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = if (apiKey.isNotBlank()) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $apiKey")
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}
