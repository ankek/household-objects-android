package dev.hho.android.data.network

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject

class BaseUrlInterceptor
    @Inject
    constructor(
        private val baseUrlResolver: InstanceBaseUrlResolver,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val configured =
                (runBlocking { baseUrlResolver.resolve() } as? BaseUrlResolution.Configured)
                    ?: throw NoInstanceUrlConfiguredException()

            val request = chain.request()
            val basePathSegments = configured.baseUrl.pathSegments.filterNot { it.isEmpty() }
            val requestPathSegments = request.url.pathSegments.filterNot { it.isEmpty() }

            val urlBuilder =
                request.url.newBuilder()
                    .scheme(configured.baseUrl.scheme)
                    .host(configured.baseUrl.host)
                    .port(configured.baseUrl.port)
                    .encodedPath("/")

            for (segment in basePathSegments + requestPathSegments) {
                urlBuilder.addPathSegment(segment)
            }

            return chain.proceed(request.newBuilder().url(urlBuilder.build()).build())
        }
    }

class NoInstanceUrlConfiguredException :
    IOException("No instance URL is configured yet — connect to an instance first")
