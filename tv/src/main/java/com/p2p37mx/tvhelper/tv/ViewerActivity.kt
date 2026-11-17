package com.p2p37mx.tvhelper.tv

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.widget.ImageView
import kotlin.concurrent.thread

/** Shows one photo full screen. Left/right to browse; slideshow mode moves on by itself. */
class ViewerActivity : Activity() {
    private lateinit var img: ImageView
    private var index = 0
    private var slideshow = false
    private val h = Handler(Looper.getMainLooper())
    private val auto = object : Runnable {
        override fun run() {
            step(1); h.postDelayed(this, Store.interval * 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        img = ImageView(this).apply {
            setBackgroundColor(Color.BLACK)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        setContentView(img)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent) {
        val files = Store.images()
        val name = i.getStringExtra("name") ?: ""
        index = files.indexOfFirst { it.name == name }.coerceAtLeast(0)
        slideshow = i.getBooleanExtra("slideshow", false)
        show()
        h.removeCallbacks(auto)
        if (slideshow) h.postDelayed(auto, Store.interval * 1000L)
    }

    override fun onPause() {
        h.removeCallbacks(auto)
        super.onPause()
    }

    private fun step(d: Int) {
        val n = Store.images().size
        if (n == 0) return
        index = ((index + d) % n + n) % n
        show()
    }

    private fun show() {
        val files = Store.images()
        if (files.isEmpty()) { finish(); return }
        val f = files[index.coerceIn(0, files.size - 1)]
        thread {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, o)
            var s = 1
            while (o.outWidth / (s * 2) >= 1920) s *= 2
            val bmp = try {
                BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
            } catch (e: Throwable) { null } ?: return@thread
            val dm = resources.displayMetrics
            val pic = try {
                FaceCrop.compose(bmp, f.name, dm.widthPixels, dm.heightPixels, fitAlways = true)
            } catch (e: Throwable) { bmp }
            runOnUiThread {
                img.alpha = 0f
                img.setImageBitmap(pic)
                img.animate().alpha(1f).setDuration(400).start()
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> { step(1); return true }
            KeyEvent.KEYCODE_DPAD_LEFT -> { step(-1); return true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                slideshow = !slideshow
                h.removeCallbacks(auto)
                if (slideshow) h.postDelayed(auto, Store.interval * 1000L)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
