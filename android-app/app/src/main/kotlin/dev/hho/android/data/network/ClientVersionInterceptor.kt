package dev.hho.android.data.network

import dev.hho.android.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class ClientVersionInterceptor
    @Inject
    constructor() : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request().newBuilder().header(HEADER_NAME, BuildConfig.HHO_CLIENT_VERSION).build()
            return chain.proceed(request)
        }

        companion object {
            const val HEADER_NAME = "X-HHO-Client-Version"
        }
    }
