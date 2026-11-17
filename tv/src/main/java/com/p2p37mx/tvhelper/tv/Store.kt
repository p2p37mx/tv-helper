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

    /** When on, our home screen replaces the TV's original one (Home button + after power on). */
    var homeMode: Boolean
        get() = prefs.getBoolean("homeMode", true)
        set(v) = prefs.edit().putBoolean("homeMode", v).apply()

    /** Set when the user deliberately opens the original home screen from ours. */
    @Volatile
    var bypassOriginal = false

    /** Right after we open an app, ignore the original launcher flashing by for a few seconds. */
    @Volatile
    var suppressUntil = 0L

    var showDate: Boolean
        get() = prefs.getBoolean("date", true)
        set(v) = prefs.edit().putBoolean("date", v).apply()

    /** Size of a screen element in percent (100 = normal): clock, date, weather, dua, title, dock. */
    val sizeKeys = listOf("clock", "date", "weather", "dua", "title", "dock")
    fun size(key: String): Int = prefs.getInt("size_$key", 100).coerceIn(40, 250)
    fun setSize(key: String, v: Int) = prefs.edit().putInt("size_$key", v.coerceIn(40, 250)).apply()

    /** Our own screensaver: minutes without using the remote before photos show (0 = off). */
    var saverMinutes: Int
        get() = prefs.getInt("saver", 5)
        set(v) = prefs.edit().putInt("saver", v.coerceIn(0, 240)).apply()

    /** Last time the remote (or phone) was used. */
    @Volatile
    var lastActivity = System.currentTimeMillis()

    var showWeather: Boolean
        get() = prefs.getBoolean("weather", true)
        set(v) = prefs.edit().putBoolean("weather", v).apply()

    var showDua: Boolean
        get() = prefs.getBoolean("dua", true)
        set(v) = prefs.edit().putBoolean("dua", v).apply()

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
