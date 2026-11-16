package com.p2p37mx.tvhelper.phone

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/** Talks to the TV app over the local Wi-Fi. */
object Net {
    @Volatile
    var base = ""

    private val pool = Executors.newFixedThreadPool(4)
    private val main = Handler(Looper.getMainLooper())

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    fun request(method: String, path: String, body: ByteArray? = null, type: String = "application/json", connectTimeout: Int = 4000): ByteArray {
        val c = URL(base + path).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = connectTimeout
            c.readTimeout = 60000
            c.useCaches = false
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", type)
                c.setFixedLengthStreamingMode(body.size)
                c.outputStream.use { it.write(body) }
            }
            val code = c.responseCode
            val stream = if (code < 400) c.inputStream else c.errorStream
            val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
            if (code >= 400) throw IOException("HTTP $code")
            return bytes
        } finally {
            c.disconnect()
        }
    }

    fun get(path: String) = request("GET", path)
    fun getJson(path: String) = JSONObject(String(get(path), Charsets.UTF_8))
    fun getArray(path: String) = JSONArray(String(get(path), Charsets.UTF_8))
    fun postJson(path: String, o: JSONObject) =
        JSONObject(String(request("POST", path, o.toString().toByteArray(Charsets.UTF_8)), Charsets.UTF_8))

    /** Run [work] in the background, then [done] on the main thread with the result or the error. */
    fun <T> async(work: () -> T, done: (T?, Exception?) -> Unit) {
        pool.execute {
            try {
                val r = work()
                main.post { done(r, null) }
            } catch (e: Exception) {
                main.post { done(null, e) }
            }
        }
    }
}
