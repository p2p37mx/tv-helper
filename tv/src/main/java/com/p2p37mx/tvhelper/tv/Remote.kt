package com.p2p37mx.tvhelper.tv

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object Remote {
    private val main = Handler(Looper.getMainLooper())

    fun key(ctx: Context, k: String): Boolean {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        when (k) {
            "volup" -> {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI); return true
            }
            "voldown" -> {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI); return true
            }
            "mute" -> {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI); return true
            }
            "myhome" -> {
                val i = Intent(ctx, HomeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                return try { Api.launchCtx(ctx).startActivity(i); true } catch (e: Exception) { false }
            }
            "dreamsettings" -> {
                val c = Api.launchCtx(ctx)
                for (a in listOf(android.provider.Settings.ACTION_DREAM_SETTINGS, android.provider.Settings.ACTION_DISPLAY_SETTINGS, android.provider.Settings.ACTION_SETTINGS)) {
                    try { c.startActivity(Intent(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true } catch (_: Exception) {}
                }
                return false
            }
            "settings" -> {
                val i = Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                return try { Api.launchCtx(ctx).startActivity(i); true } catch (e: Exception) { false }
            }
        }
        // with ADB connected, press real remote keys (works inside every app, YouTube included)
        val code = mapOf("up" to 19, "down" to 20, "left" to 21, "right" to 22, "ok" to 23, "back" to 4)[k]
        if (code != null && Adb.ready && Adb.key(ctx, code)) return true
        val s = RemoteAccessibilityService.instance ?: return false
        return onMain { s.handle(k) } ?: false
    }

    private fun <T> onMain(block: () -> T): T? {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        val latch = CountDownLatch(1)
        var result: T? = null
        main.post {
            try { result = block() } catch (_: Exception) {} finally { latch.countDown() }
        }
        latch.await(3, TimeUnit.SECONDS)
        return result
    }
}
