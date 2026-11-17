package com.p2p37mx.tvhelper.tv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/**
 * The photo background with clock, date, weather, dua and title on top.
 * Used by the home screen (with the app dock over it) and by the screensaver.
 */
class Stage(context: Context) : FrameLayout(context) {

    private val bgA = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP; alpha = 0f }
    private val bgB = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP; alpha = 0f }
    private var showingA = true
    val bottomShade = View(context)
    private val clockBox = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val clock = text(true)
    private val date = text(false).apply { alpha = 0.85f }
    private val sideBox = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val duaView = text(true).apply { setLineSpacing(0f, 1.15f) }
    private val weatherView = text(true)
    private val title = text(true).apply { gravity = Gravity.CENTER }

    private val h = Handler(Looper.getMainLooper())
    private var lastImages: List<String> = emptyList()
    private var lastWeather = 0L
    private var weatherText = ""
    private var running = false

    private val listener = Runnable { refresh() }
    private val tick = object : Runnable {
        override fun run() {
            updateClock(); h.postDelayed(this, 1000)
        }
    }
    private val slide = object : Runnable {
        override fun run() {
            nextImage(); h.postDelayed(this, Store.interval * 1000L)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    init {
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor("#141824"), Color.parseColor("#2A1830"), Color.parseColor("#3B1E22"))
        )
        addView(bgA, LayoutParams(-1, -1))
        addView(bgB, LayoutParams(-1, -1))

        bottomShade.background = GradientDrawable(
            GradientDrawable.Orientation.BOTTOM_TOP,
            intArrayOf(Color.parseColor("#B3000000"), Color.parseColor("#33000000"), Color.TRANSPARENT)
        )
        addView(bottomShade, LayoutParams(-1, dp(260), Gravity.BOTTOM))
        val topShade = View(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#80000000"), Color.TRANSPARENT)
            )
        }
        addView(topShade, LayoutParams(-1, dp(200), Gravity.TOP))

        clockBox.addView(clock)
        clockBox.addView(date)
        addView(clockBox, LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
            setMargins(dp(56), dp(32), dp(56), 0)
        })

        weatherView.setPadding(0, dp(8), 0, 0)
        sideBox.addView(duaView)
        sideBox.addView(weatherView)
        addView(sideBox, LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply {
            setMargins(dp(56), dp(36), dp(56), 0)
        })

        addView(title, LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = dp(48)
        })
    }

    private fun text(bold: Boolean) = TextView(context).apply {
        setTextColor(Color.WHITE)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setShadowLayer(8f, 0f, 2f, Color.parseColor("#AA000000"))
    }

    fun start() {
        if (running) return
        running = true
        Store.addListener(listener)
        refresh()
        h.post(tick)
        h.post(slide)
    }

    fun stop() {
        running = false
        Store.removeListener(listener)
        h.removeCallbacksAndMessages(null)
    }

    fun refresh() {
        // sizes are percentages chosen from the phone (100 = normal)
        clock.textSize = 60f * Store.size("clock") / 100f
        date.textSize = 19f * Store.size("date") / 100f
        duaView.textSize = 19f * Store.size("dua") / 100f
        duaView.maxWidth = dp(460) * Store.size("dua") / 100
        weatherView.textSize = 30f * Store.size("weather") / 100f
        title.textSize = 32f * Store.size("title") / 100f

        title.text = Store.title
        title.visibility = if (Store.title.isBlank()) View.GONE else View.VISIBLE
        clock.visibility = if (Store.showClock) View.VISIBLE else View.GONE
        date.visibility = if (Store.showDate) View.VISIBLE else View.GONE
        duaView.visibility = if (Store.showDua) View.VISIBLE else View.GONE
        if (Store.showDua && duaView.text.isNullOrEmpty()) duaView.text = Duas.next()
        weatherView.visibility = if (Store.showWeather && weatherText.isNotEmpty()) View.VISIBLE else View.GONE
        if (Store.showWeather) loadWeather()
        updateClock()

        val names = Store.images().map { it.name }
        if (names != lastImages) {
            lastImages = names
            if (names.isEmpty()) {
                bgA.animate().alpha(0f).duration = 600
                bgB.animate().alpha(0f).duration = 600
            } else if (running) {
                h.removeCallbacks(slide)
                h.post(slide)
            }
        }
    }

    private fun updateClock() {
        val now = Date()
        clock.text = SimpleDateFormat("h:mm", Locale.getDefault()).format(now)
        date.text = SimpleDateFormat("EEEE، d MMMM", Locale("ar")).format(now)
    }

    private fun loadWeather() {
        val now = System.currentTimeMillis()
        if (now - lastWeather < 30 * 60 * 1000L) return
        lastWeather = now
        thread {
            val w = Weather.fetch()
            h.post {
                if (w == null) { lastWeather = 0L; return@post } // try again with the next photo
                weatherText = "${w.icon} ${w.temp}°  ${w.desc}"
                weatherView.text = weatherText
                weatherView.visibility = if (Store.showWeather) View.VISIBLE else View.GONE
            }
        }
    }

    private fun nextImage() {
        val f = Shuffle.next(Store.images()) ?: return
        val vw = width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val vh = height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        thread {
            try {
                val bmp = decodeScaled(f, 1920) ?: return@thread
                val pic = FaceCrop.compose(bmp, f.name, vw, vh)
                bmp.recycle()
                h.post { if (running) showBg(pic) }
            } catch (_: Throwable) {}
        }
    }

    private fun showBg(pic: Bitmap) {
        if (Store.showDua) {
            duaView.animate().alpha(0f).setDuration(500).withEndAction {
                duaView.text = Duas.next()
                duaView.animate().alpha(1f).setDuration(700).start()
            }.start()
        }
        if (Store.showWeather) loadWeather()
        val inV = if (showingA) bgB else bgA
        val outV = if (showingA) bgA else bgB
        showingA = !showingA
        inV.setImageBitmap(pic)
        inV.alpha = 0f
        inV.animate().alpha(1f).setDuration(1200).start()
        outV.animate().alpha(0f).setDuration(1200).start()
    }

    private fun decodeScaled(f: File, maxW: Int): Bitmap? = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var s = 1
        while (o.outWidth / (s * 2) >= maxW && o.outHeight / (s * 2) >= 1080) s *= 2
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
    } catch (e: Throwable) {
        null
    }
}
