package com.loy.mingclaw.core.network.di

import android.content.Context
import android.content.pm.ApplicationInfo
import com.loy.mingclaw.core.common.llm.CloudLlm
import com.loy.mingclaw.core.model.llm.LlmProvider
import com.loy.mingclaw.core.network.LlmService
import com.loy.mingclaw.core.network.api.LlmApi
import com.loy.mingclaw.core.network.internal.AuthInterceptor
import com.loy.mingclaw.core.network.internal.CloudLlmProvider
import com.loy.mingclaw.core.network.internal.LlmServiceImpl
import com.loy.mingclaw.core.network.internal.SseParser
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class NetworkModule {

    @Binds
    @Singleton
    abstract fun bindLlmService(impl: LlmServiceImpl): LlmService

    @Binds
    @Singleton
    @CloudLlm
    abstract fun bindCloudLlmProvider(impl: CloudLlmProvider): LlmProvider

    companion object {
        @Provides
        @Singleton
        fun provideJson(): Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        @Provides
        @Singleton
        fun provideAuthInterceptor(): AuthInterceptor = AuthInterceptor()

        @Provides
        @Singleton
        fun provideOkHttpClient(
            authInterceptor: AuthInterceptor,
            @ApplicationContext context: Context,
        ): OkHttpClient {
            val isDebug = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            return OkHttpClient.Builder()
                .addInterceptor(authInterceptor)
                .addInterceptor(HttpLoggingInterceptor().apply {
                    level = if (isDebug) HttpLoggingInterceptor.Level.BODY
                           else HttpLoggingInterceptor.Level.NONE
                })
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS) // Longer for LLM responses
                .build()
        }

        @Provides
        @Singleton
        fun provideRetrofit(okHttpClient: OkHttpClient, json: Json, authInterceptor: AuthInterceptor): Retrofit {
            return Retrofit.Builder()
                .baseUrl(authInterceptor.baseUrl)
                .client(okHttpClient)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
        }

        @Provides
        @Singleton
        fun provideLlmApi(retrofit: Retrofit): LlmApi = retrofit.create(LlmApi::class.java)

        @Provides
        @Singleton
        fun provideSseParser(json: Json): SseParser = SseParser(json)

        @Provides
        @Singleton
        fun provideDefaultLlmProvider(@CloudLlm cloud: LlmProvider): LlmProvider = cloud
    }
}
