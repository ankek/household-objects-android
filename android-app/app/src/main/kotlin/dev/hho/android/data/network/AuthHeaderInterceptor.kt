package dev.hho.android.data.network

import dev.hho.android.data.auth.DeviceTokenProvider
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class AuthHeaderInterceptor
    @Inject
    constructor(
        private val tokenProvider: DeviceTokenProvider,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val token = runBlocking { tokenProvider.tokenFor(request.url) }

            val authenticatedRequest =
                if (token.isNullOrBlank()) {
                    request
                } else {
                    request.newBuilder().header("Authorization", "Bearer $token").build()
                }

            val response = chain.proceed(authenticatedRequest)

            if (!token.isNullOrBlank() && response.code == 401) {
                runBlocking { tokenProvider.invalidate(request.url, token) }
            }

            return response
        }
    }
