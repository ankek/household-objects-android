package dev.hho.android.di

import dev.hho.android.data.network.AuthHeaderInterceptor
import dev.hho.android.data.network.BaseUrlInterceptor
import dev.hho.android.data.network.ClientVersionInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(
        baseUrlInterceptor: BaseUrlInterceptor,
        authHeaderInterceptor: AuthHeaderInterceptor,
        clientVersionInterceptor: ClientVersionInterceptor,
    ): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(baseUrlInterceptor)
            .addInterceptor(authHeaderInterceptor)
            .addInterceptor(clientVersionInterceptor)
            .build()

    @Provides
    @Singleton
    @ProbeHttpClient
    fun provideProbeOkHttpClient(
        sharedOkHttpClient: OkHttpClient,
        clientVersionInterceptor: ClientVersionInterceptor,
    ): OkHttpClient {
        val builder = sharedOkHttpClient.newBuilder()
        builder.interceptors().clear()
        builder.interceptors().add(clientVersionInterceptor)
        return builder.build()
    }
}
