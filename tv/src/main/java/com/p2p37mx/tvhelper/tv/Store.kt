package com.p2p37mx.tvhelper.tv

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/** Everything the TV remembers: app order, hidden apps, images and home-screen settings. */
object Store {
    private lateinit var ctx: Context
    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<Runnable>()

    fun init(c: Context) {
        if (!::ctx.isInitialized) ctx = c.applicationContext
    }

    private val prefs: SharedPreferences
        get() = ctx.getSharedPreferences("tvhelper", Context.MODE_PRIVATE)

    var order: List<String>
        get() = (prefs.getString("order", "") ?: "").split(",").filter { it.isNotBlank() }
        set(v) = prefs.edit().putString("order", v.joinToString(",")).apply()

    var hidden: Set<String>
        get() = HashSet(prefs.getStringSet("hidden", emptySet()) ?: emptySet())
        set(v) = prefs.edit().putStringSet("hidden", HashSet(v)).apply()

    var title: String
        get() = prefs.getString("title", "") ?: ""
        set(v) = prefs.edit().putString("title", v).apply()

    /** Seconds between background images. */
    var interval: Int
        get() = prefs.getInt("interval", 15)
        set(v) = prefs.edit().putInt("interval", v.coerceIn(3, 3600)).apply()

    var showClock: Boolean
        get() = prefs.getBoolean("clock", true)
        set(v) = prefs.edit().putBoolean("clock", v).apply()

    val imagesDir: File
        get() = File(ctx.filesDir, "images").apply { mkdirs() }

    fun images(): List<File> =
        imagesDir.listFiles()?.filter { it.isFile }?.sortedBy { it.name } ?: emptyList()

    fun safeName(name: String): String {
        val n = File(name).name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return if (n.isBlank() || n.startsWith(".")) "img_${System.currentTimeMillis()}.jpg" else n
    }

    fun addListener(r: Runnable) { listeners.add(r) }
    fun removeListener(r: Runnable) { listeners.remove(r) }
    fun changed() { main.post { listeners.forEach { it.run() } } }
}
