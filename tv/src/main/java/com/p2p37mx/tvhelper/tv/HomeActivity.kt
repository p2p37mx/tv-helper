package com.p2p37mx.tvhelper.tv

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/** Our own TV home screen: background photos, clock, and apps in the order chosen from the phone. */
class HomeActivity : Activity() {

    private lateinit var root: FrameLayout
    private lateinit var bgA: ImageView
    private lateinit var bgB: ImageView
    private var showingA = true
    private lateinit var clockBox: LinearLayout
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var title: TextView
    private lateinit var focusLabel: TextView
    private lateinit var info: TextView
    private lateinit var row: LinearLayout

    private val h = Handler(Looper.getMainLooper())
    private var imgIndex = -1
    private var lastImages: List<String> = emptyList()
    private var focusedKey: String? = null

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        ServerService.start(this)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        Store.addListener(listener)
        refresh()
        h.post(tick)
        h.removeCallbacks(slide)
        h.post(slide)
    }

    override fun onPause() {
        Store.removeListener(listener)
        h.removeCallbacksAndMessages(null)
        super.onPause()
    }

    private fun buildUi() {
        root = FrameLayout(this)
        root.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor("#141824"), Color.parseColor("#2A1830"), Color.parseColor("#3B1E22"))
        )
        val match = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        bgA = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; alpha = 0f }
        bgB = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; alpha = 0f }
        root.addView(bgA, match)
        root.addView(bgB, FrameLayout.LayoutParams(match))

        // dark fades so text stays readable on any photo
        val bottomShade = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(Color.parseColor("#E6000000"), Color.parseColor("#66000000"), Color.TRANSPARENT)
            )
        }
        root.addView(bottomShade, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(380), Gravity.BOTTOM))
        val topShade = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#99000000"), Color.TRANSPARENT)
            )
        }
        root.addView(topShade, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(200), Gravity.TOP))

        clockBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        clock = text(64f, true)
        date = text(20f, false).apply { alpha = 0.85f }
        clockBox.addView(clock)
        clockBox.addView(date)
        root.addView(clockBox, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply {
            setMargins(dp(56), dp(36), dp(56), 0)
        })

        title = text(34f, true).apply { gravity = Gravity.CENTER }
        root.addView(title, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = dp(52)
        })

        val bottom = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        focusLabel = text(24f, true).apply { setPadding(dp(58), 0, dp(58), 0) }
        bottom.addView(focusLabel)

        val scroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            setPadding(dp(46), 0, dp(46), 0)
        }
        row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        scroller.addView(row)
        bottom.addView(scroller)

        info = text(13f, false).apply { alpha = 0.7f; setPadding(dp(58), dp(4), dp(58), dp(18)) }
        bottom.addView(info)

        root.addView(bottom, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, -2, Gravity.BOTTOM))
        setContentView(root)
    }

    private fun text(sp: Float, bold: Boolean) = TextView(this).apply {
        textSize = sp
        setTextColor(Color.WHITE)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setShadowLayer(8f, 0f, 2f, Color.parseColor("#AA000000"))
    }

    private fun refresh() {
        title.text = Store.title
        title.visibility = if (Store.title.isBlank()) View.GONE else View.VISIBLE
        clockBox.visibility = if (Store.showClock) View.VISIBLE else View.GONE
        updateClock()
        buildRow()
        val ip = localIp()
        val acc = RemoteAccessibilityService.instance != null
        info.text = buildString {
            append("عنوان الشاشة: ").append(ip ?: "غير متصل").append(":").append(ServerService.PORT)
            if (!acc) append("   •   للريموت فعّل «مساعد الشاشة - الريموت» من الإعدادات ← إمكانية الوصول")
        }
        val names = Store.images().map { it.name }
        if (names != lastImages) {
            lastImages = names
            if (names.isEmpty()) {
                bgA.animate().alpha(0f).duration = 600
                bgB.animate().alpha(0f).duration = 600
            } else {
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

    private fun buildRow() {
        row.removeAllViews()
        var toFocus: View? = null
        for (app in AppsRepo.visible(this)) {
            val banner = AppsRepo.banner(this, app.activity)
            val v = tile(app.pkg, app.label, banner, if (banner == null) AppsRepo.icon(this, app.activity) else null) {
                AppsRepo.launch(this, app.pkg)
            }
            row.addView(v)
            if (app.pkg == focusedKey) toFocus = v
        }
        row.addView(tile("#photos", "صوري", null, getDrawable(android.R.drawable.ic_menu_gallery)) {
            if (Store.images().isNotEmpty()) {
                startActivity(Intent(this, ViewerActivity::class.java).putExtra("slideshow", true))
            }
        })
        row.addView(tile("#settings", "إعدادات الشاشة", null, getDrawable(android.R.drawable.ic_menu_preferences)) {
            try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (_: Exception) {}
        })
        (toFocus ?: row.getChildAt(0))?.requestFocus()
    }

    private fun tileBg(focused: Boolean) = GradientDrawable().apply {
        cornerRadius = dp(14).toFloat()
        setColor(if (focused) Color.parseColor("#55FFFFFF") else Color.parseColor("#33FFFFFF"))
        setStroke(dp(3), if (focused) Color.WHITE else Color.TRANSPARENT)
    }

    private fun tile(key: String, label: String, banner: Drawable?, icon: Drawable?, onClick: () -> Unit): View {
        val box = FrameLayout(this).apply {
            isFocusable = true
            isClickable = true
            background = tileBg(false)
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
            layoutParams = LinearLayout.LayoutParams(dp(208), dp(117)).apply { setMargins(dp(10), dp(16), dp(10), dp(16)) }
        }
        if (banner != null) {
            box.addView(ImageView(this).apply {
                setImageDrawable(banner)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }, FrameLayout.LayoutParams(-1, -1))
        } else {
            val inner = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }
            inner.addView(ImageView(this).apply { setImageDrawable(icon) }, LinearLayout.LayoutParams(dp(56), dp(56)))
            inner.addView(text(14f, false).apply {
                text = label
                maxLines = 1
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(6), dp(6), 0)
            })
            box.addView(inner, FrameLayout.LayoutParams(-1, -1))
        }
        box.setOnFocusChangeListener { v, has ->
            v.background = tileBg(has)
            v.animate().scaleX(if (has) 1.12f else 1f).scaleY(if (has) 1.12f else 1f).setDuration(150).start()
            v.translationZ = if (has) dp(8).toFloat() else 0f
            if (has) {
                focusedKey = key
                focusLabel.text = label
            }
        }
        box.setOnClickListener { onClick() }
        return box
    }

    private fun nextImage() {
        val files = Store.images()
        if (files.isEmpty()) return
        imgIndex = (imgIndex + 1) % files.size
        val f = files[imgIndex]
        val w = resources.displayMetrics.widthPixels.coerceAtLeast(1280)
        thread {
            val bmp = decodeScaled(f, w) ?: return@thread
            runOnUiThread { showBg(bmp) }
        }
    }

    private fun showBg(bmp: Bitmap) {
        val inV = if (showingA) bgB else bgA
        val outV = if (showingA) bgA else bgB
        showingA = !showingA
        inV.setImageBitmap(bmp)
        inV.alpha = 0f
        inV.animate().alpha(1f).setDuration(1200).start()
        outV.animate().alpha(0f).setDuration(1200).start()
    }

    private fun decodeScaled(f: File, maxW: Int): Bitmap? = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var s = 1
        while (o.outWidth / (s * 2) >= maxW) s *= 2
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
    } catch (e: Throwable) {
        null
    }

    private fun localIp(): String? = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress
    } catch (e: Exception) {
        null
    }
}
