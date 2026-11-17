package com.p2p37mx.tvhelper.tv

import android.app.Application
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Date

class TvApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // keep the last crash so the phone app can show it (Settings → last TV error)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                File(filesDir, "crash.txt").writeText("${Date()}  thread=${t.name}\n$sw")
            } catch (_: Throwable) {}
            previous?.uncaughtException(t, e)
        }
        Store.init(this)
        ServerService.start(this)
    }
}
