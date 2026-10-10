package app.crayo.data

import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** All downloading goes through here. Calls stop right away when a search is cancelled. */
object Http {
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13; SM-G981B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    private val client = OkHttpClient.Builder()
        .dispatcher(Dispatcher().apply { maxRequests = 96; maxRequestsPerHost = 24 })
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .build()

    private suspend fun <T> call(url: String, referer: String?, read: (Response) -> T?): T? =
        suspendCancellableCoroutine { continuation ->
            val request = try {
                Request.Builder().url(url).header("User-Agent", USER_AGENT)
                    .apply { if (referer != null) header("Referer", referer) }.build()
            } catch (e: IllegalArgumentException) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resume(null)
                }

                override fun onResponse(call: Call, response: Response) {
                    val value = try {
                        response.use { if (it.isSuccessful) read(it) else null }
                    } catch (e: Exception) {
                        null
                    }
                    if (continuation.isActive) continuation.resume(value)
                }
            })
        }

    /** A web page as text, or null if it could not be loaded. */
    suspend fun text(url: String): String? = call(url, null) { it.body.string() }

    /** A picture's bytes, or null if the address is not a picture. */
    suspend fun image(url: String): ByteArray? {
        val host = try { URI(url).host } catch (e: Exception) { null } ?: return null
        return call(url, "https://$host/") { response ->
            if (response.header("Content-Type")?.startsWith("image/") == true) response.body.bytes() else null
        }
    }
}
