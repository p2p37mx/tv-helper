package com.p2p37mx.tvhelper.tv

import android.content.Context
import dadb.AdbKeyPair
import dadb.Dadb
import java.io.File

/**
 * Talks to the TV's own ADB (Developer options → USB/ADB debugging) on 127.0.0.1:5555,
 * so the app can press real remote buttons (OK, arrows) inside any app, YouTube included.
 * The first time, the TV asks "Allow debugging?" → tick "Always allow" → OK.
 */
object Adb {
    @Volatile private var dadb: Dadb? = null
    @Volatile var status = "ما متصل"
        private set
    @Volatile private var lastTry = 0L

    val ready: Boolean get() = dadb != null

    @Synchronized
    fun connect(ctx: Context, force: Boolean = false): Dadb? {
        dadb?.let { return it }
        val now = System.currentTimeMillis()
        if (!force && now - lastTry < 60_000) return null // don't keep knocking every second
        lastTry = now
        return try {
            val dir = ctx.filesDir
            val priv = File(dir, "adbkey")
            val pub = File(dir, "adbkey.pub")
            if (!priv.exists() || !pub.exists()) AdbKeyPair.generate(priv, pub)
            val kp = AdbKeyPair.read(priv, pub)
            val d = Dadb.create("127.0.0.1", 5555, kp)
            val r = d.shell("echo ok")
            if (!r.output.contains("ok")) throw IllegalStateException(r.output)
            dadb = d
            status = "✅ متصل"
            d
        } catch (e: Throwable) {
            status = "ما متصل (${e.javaClass.simpleName}: ${e.message?.take(80)})"
            null
        }
    }

    /** Presses a key like the real remote. Must not be called on the main thread. */
    fun key(ctx: Context, code: Int): Boolean {
        val d = connect(ctx) ?: return false
        return try {
            d.shell("input keyevent $code"); true
        } catch (e: Throwable) {
            try { d.close() } catch (_: Throwable) {}
            dadb = null
            status = "انقطع، راح أعيد الاتصال"
            false
        }
    }
}
