package com.p2p37mx.tvhelper.tv

import android.content.Context
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import kotlin.concurrent.thread

class Req(val method: String, val path: String, val query: Map<String, String>, val body: ByteArray) {
    fun q(k: String) = query[k] ?: ""
}

class Res(val code: Int, val type: String, val body: ByteArray)

/** Tiny HTTP server so the phone app can talk to the TV on the local Wi-Fi. */
class HttpServer(private val ctx: Context, private val port: Int) {
    @Volatile private var running = false
    private var server: ServerSocket? = null

    fun start() {
        if (running) return
        running = true
        thread(name = "tvhelper-http", isDaemon = true) {
            try {
                val ss = ServerSocket()
                ss.reuseAddress = true
                ss.bind(InetSocketAddress(port))
                server = ss
                while (running) {
                    val s = ss.accept()
                    thread(isDaemon = true) { handle(s) }
                }
            } catch (e: Exception) {
                running = false
            }
        }
    }

    fun stop() {
        running = false
        try { server?.close() } catch (_: Exception) {}
    }

    private fun handle(sock: Socket) {
        try {
            sock.soTimeout = 60000
            val input = BufferedInputStream(sock.getInputStream())
            val line = readLine(input) ?: return
            val parts = line.split(" ")
            if (parts.size < 2) return
            val headers = HashMap<String, String>()
            while (true) {
                val h = readLine(input) ?: break
                if (h.isEmpty()) break
                val idx = h.indexOf(':')
                if (idx > 0) headers[h.substring(0, idx).trim().lowercase()] = h.substring(idx + 1).trim()
            }
            val len = (headers["content-length"]?.toIntOrNull() ?: 0).coerceIn(0, 40 * 1024 * 1024)
            val body = ByteArray(len)
            var read = 0
            while (read < len) {
                val n = input.read(body, read, len - read)
                if (n < 0) break
                read += n
            }
            val full = parts[1]
            val qi = full.indexOf('?')
            val path = if (qi >= 0) full.substring(0, qi) else full
            val q = HashMap<String, String>()
            if (qi >= 0) {
                full.substring(qi + 1).split("&").forEach { kv ->
                    val e = kv.indexOf('=')
                    if (e > 0) q[URLDecoder.decode(kv.substring(0, e), "UTF-8")] =
                        URLDecoder.decode(kv.substring(e + 1), "UTF-8")
                }
            }
            val res = try {
                Api.route(ctx, Req(parts[0].uppercase(), path, q, body))
            } catch (e: Exception) {
                Api.error(500, e.toString())
            }
            val out = BufferedOutputStream(sock.getOutputStream())
            val head = "HTTP/1.1 ${res.code} OK\r\n" +
                "Content-Type: ${res.type}\r\n" +
                "Content-Length: ${res.body.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n"
            out.write(head.toByteArray(Charsets.UTF_8))
            out.write(res.body)
            out.flush()
        } catch (_: Exception) {
        } finally {
            try { sock.close() } catch (_: Exception) {}
        }
    }

    private fun readLine(i: InputStream): String? {
        val sb = ByteArrayOutputStream()
        while (true) {
            val c = i.read()
            if (c < 0) return if (sb.size() == 0) null else sb.toString("UTF-8")
            if (c == '\n'.code) break
            if (c != '\r'.code) sb.write(c)
            if (sb.size() > 16384) break
        }
        return sb.toString("UTF-8")
    }
}
