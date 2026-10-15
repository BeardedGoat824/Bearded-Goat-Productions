package com.spot2ytm.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

class HttpException(val code: Int, val body: String) : IOException("HTTP $code")

object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val original = chain.request()
            val builder = original.newBuilder()
            if (original.header("User-Agent") == null) builder.header("User-Agent", DESKTOP_USER_AGENT)
            if (original.header("Accept-Language") == null) builder.header("Accept-Language", "en-US,en;q=0.9")
            chain.proceed(builder.build())
        }
        .build()

    /** Runs the request off the main thread and returns the body, throwing [HttpException] on non-2xx. */
    suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response -> response.bodyOrThrow() }
    }

    /** Follows redirects and returns the final URL (used to expand spotify.link short links). */
    suspend fun resolveFinalUrl(url: String): String = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).build()).execute().use { it.request.url.toString() }
    }

    private fun Response.bodyOrThrow(): String {
        val text = body?.string().orEmpty()
        if (!isSuccessful) throw HttpException(code, text)
        return text
    }
}
