package com.spot2ytm.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

class HttpException(val code: Int, val body: String) : IOException("HTTP $code")

/**
 * Keeps cookies in memory for the app's lifetime, like a browser tab would.
 * YouTube throttles cookie-less request bursts much harder than a normal session.
 */
private class MemoryCookieJar : CookieJar {
    private val cookies = mutableMapOf<String, Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { this.cookies["${it.domain}|${it.path}|${it.name}"] = it }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        cookies.values.removeAll { it.expiresAt < now }
        return cookies.values.filter { it.matches(url) }
    }
}

object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS) // hard cap so a throttled request can't hang the app
        .cookieJar(MemoryCookieJar())
        .addInterceptor { chain ->
            val original = chain.request()
            val builder = original.newBuilder()
            if (original.header("User-Agent") == null) builder.header("User-Agent", DESKTOP_USER_AGENT)
            if (original.header("Accept-Language") == null) builder.header("Accept-Language", "en-US,en;q=0.9")
            chain.proceed(builder.build())
        }
        .build()

    /** Returns the body, throwing [HttpException] on non-2xx. Cancelling the coroutine cancels the request. */
    suspend fun execute(request: Request): String = client.newCall(request).await().use { it.bodyOrThrow() }

    /** Follows redirects and returns the final URL (used to expand spotify.link short links). */
    suspend fun resolveFinalUrl(url: String): String =
        client.newCall(Request.Builder().url(url).build()).await().use { it.request.url.toString() }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) = cont.resume(response) { _, value, _ -> value.close() }
            override fun onFailure(call: Call, e: IOException) {
                if (!cont.isCancelled) cont.resumeWithException(e)
            }
        })
    }

    private suspend fun Response.bodyOrThrow(): String = withContext(Dispatchers.IO) {
        val text = body?.string().orEmpty()
        if (!isSuccessful) throw HttpException(code, text)
        text
    }
}
