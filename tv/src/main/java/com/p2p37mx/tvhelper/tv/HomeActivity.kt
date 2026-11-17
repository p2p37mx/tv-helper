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
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/**
 * Our own TV home screen: background photos (cropped around faces), clock,
 * and an iPhone-style dock of app icons that slides away when the remote is idle.
 */
class HomeActivity : Activity() {

    companion object {
        const val IDLE_MS = 8000L
    }

    private lateinit var root: FrameLayout
    private lateinit var bgA: ImageView
    private lateinit var bgB: ImageView
    private var showingA = true
    private lateinit var bottomShade: View
    private lateinit var clockBox: LinearLayout
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var title: TextView
    private lateinit var dockWrap: FrameLayout
    private lateinit var row: LinearLayout

    private val h = Handler(Looper.getMainLooper())
    private var lastImages: List<String> = emptyList()
    private var focusedKey: String? = null
    private var dockShown = true

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
    private val idle = Runnable { hideDock() }

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
        showDock()
        wake()
    }

    override fun onPause() {
        Store.removeListener(listener)
        h.removeCallbacksAndMessages(null)
        super.onPause()
    }

    // ------------------------------------------------------------ layout

    private fun buildUi() {
        root = FrameLayout(this)
        root.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor("#141824"), Color.parseColor("#2A1830"), Color.parseColor("#3B1E22"))
        )

        bgA = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; alpha = 0f }
        bgB = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; alpha = 0f }
        root.addView(bgA, FrameLayout.LayoutParams(-1, -1))
        root.addView(bgB, FrameLayout.LayoutParams(-1, -1))

        bottomShade = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(Color.parseColor("#B3000000"), Color.parseColor("#33000000"), Color.TRANSPARENT)
            )
        }
        root.addView(bottomShade, FrameLayout.LayoutParams(-1, dp(260), Gravity.BOTTOM))
        val topShade = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#80000000"), Color.TRANSPARENT)
            )
        }
        root.addView(topShade, FrameLayout.LayoutParams(-1, dp(180), Gravity.TOP))

        clockBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        clock = text(60f, true)
        date = text(19f, false).apply { alpha = 0.85f }
        clockBox.addView(clock)
        clockBox.addView(date)
        root.addView(clockBox, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply {
            setMargins(dp(56), dp(32), dp(56), 0)
        })

        title = text(32f, true).apply { gravity = Gravity.CENTER }
        root.addView(title, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = dp(48)
        })

        // The dock: frosted rounded bar at the bottom, like an iPhone
        dockWrap = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
        }
        val scroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = false
            clipChildren = false
            clipToPadding = false
            setPadding(dp(14), dp(12), dp(14), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(30).toFloat()
                setColor(Color.parseColor("#4D101418"))
                setStroke(dp(1), Color.parseColor("#33FFFFFF"))
            }
        }
        row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
            // room above the icons so the focused (bigger) one isn't cut off
            setPadding(0, dp(18), 0, dp(4))
        }
        scroller.addView(row)
        dockWrap.addView(scroller, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL))
        root.addView(dockWrap, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            setMargins(dp(48), 0, dp(48), dp(28))
        })

        root.clipChildren = false
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

    // ------------------------------------------------------------ dock

    private fun buildRow() {
        row.removeAllViews()
        var toFocus: View? = null
        for (app in AppsRepo.visible(this)) {
            val v = tile(app.pkg, app.label, AppsRepo.icon(this, app.activity), null) {
                AppsRepo.launch(this, app.pkg)
            }
            row.addView(v)
            if (app.pkg == focusedKey) toFocus = v
        }
        row.addView(tile("#photos", "صوري", null, android.R.drawable.ic_menu_gallery) {
            if (Store.images().isNotEmpty()) {
                startActivity(Intent(this, ViewerActivity::class.java).putExtra("slideshow", true))
            }
        })
        row.addView(tile("#original", "الأصلية", null, android.R.drawable.ic_menu_revert) {
            openOriginalHome()
        })
        row.addView(tile("#settings", "الإعدادات", null, android.R.drawable.ic_menu_preferences) {
            try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (_: Exception) {}
        })
        (toFocus ?: row.getChildAt(0))?.requestFocus()
    }

    /** One app: a rounded-square icon with its name under it. [sysIcon] draws a white glyph on a coloured square. */
    private fun tile(key: String, label: String, icon: Drawable?, sysIcon: Int?, onClick: () -> Unit): View {
        val size = dp(64)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            isFocusable = true
            isClickable = true
            clipChildren = false
            setPadding(dp(6), dp(4), dp(6), dp(4))
            layoutParams = LinearLayout.LayoutParams(dp(92), -2)
        }
        val iconView = ImageView(this).apply {
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: android.graphics.Outline) {
                    o.setRoundRect(0, 0, v.width, v.height, dp(16).toFloat())
                }
            }
            clipToOutline = true
            if (sysIcon != null) {
                setImageResource(sysIcon)
                setColorFilter(Color.WHITE)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(dp(14), dp(14), dp(14), dp(14))
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(Color.parseColor("#FF5A4F"), Color.parseColor("#FF8A5B"))
                )
            } else {
                setImageDrawable(icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
        }
        // white ring around the focused icon (instead of a big zoom that could get cut off)
        val ring = FrameLayout(this).apply {
            setPadding(dp(3), dp(3), dp(3), dp(3))
            clipChildren = false
        }
        ring.addView(iconView, FrameLayout.LayoutParams(size, size))
        box.addView(ring, LinearLayout.LayoutParams(-2, -2))
        val name = text(12f, false).apply {
            text = label
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            alpha = 0.85f
            setPadding(0, dp(6), 0, 0)
        }
        box.addView(name, LinearLayout.LayoutParams(-1, -2))

        box.setOnFocusChangeListener { _, has ->
            ring.background = if (has) GradientDrawable().apply {
                cornerRadius = dp(19).toFloat()
                setStroke(dp(3), Color.WHITE)
            } else null
            ring.animate().scaleX(if (has) 1.08f else 1f).scaleY(if (has) 1.08f else 1f)
                .setDuration(160).start()
            name.alpha = if (has) 1f else 0.75f
            name.typeface = if (has) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            if (has) {
                focusedKey = key
                wake() // focus moved (also when moved from the phone)
            }
        }
        box.setOnClickListener { onClick() }
        return box
    }

    private fun wake() {
        h.removeCallbacks(idle)
        h.postDelayed(idle, IDLE_MS)
        if (!dockShown) showDock()
    }

    private fun showDock() {
        dockShown = true
        dockWrap.animate().translationY(0f).alpha(1f).setDuration(350)
            .setInterpolator(DecelerateInterpolator()).start()
        bottomShade.animate().alpha(1f).setDuration(350).start()
        if (row.findFocus() == null) row.getChildAt(0)?.requestFocus()
    }

    private fun hideDock() {
        dockShown = false
        val dist = (dockWrap.height + dp(60)).toFloat()
        dockWrap.animate().translationY(dist).alpha(0f).setDuration(600)
            .setInterpolator(DecelerateInterpolator()).start()
        bottomShade.animate().alpha(0f).setDuration(600).start()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val k = event.keyCode
        val isVolume = k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN || k == KeyEvent.KEYCODE_VOLUME_MUTE
        if (!isVolume && event.action == KeyEvent.ACTION_DOWN) {
            val wasHidden = !dockShown
            wake()
            // the first press only brings the dock back, it doesn't open anything
            if (wasHidden && k != KeyEvent.KEYCODE_BACK) return true
        }
        return super.dispatchKeyEvent(event)
    }

    /** Opens the TV's original home screen without bouncing back to ours. */
    private fun openOriginalHome() {
        val pkg = RemoteAccessibilityService.instance?.originalHomePackage() ?: run {
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            packageManager.queryIntentActivities(i, 0).map { it.activityInfo.packageName }
                .firstOrNull { it != packageName && it != "android" && !it.contains("settings") }
        } ?: return
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(pkg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        Store.bypassOriginal = true
        try { startActivity(i) } catch (_: Exception) { Store.bypassOriginal = false }
    }

    // ------------------------------------------------------------ background photos

    private fun nextImage() {
        val files = Store.images()
        if (files.isEmpty()) return
        val f = Shuffle.next(files) ?: return
        val w = resources.displayMetrics.widthPixels.coerceAtLeast(1280)
        thread {
            try {
                val bmp = decodeScaled(f, w) ?: return@thread
                val vw = root.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
                val vh = root.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
                val pic = FaceCrop.compose(bmp, f.name, vw, vh)
                bmp.recycle()
                runOnUiThread { try { showBg(pic) } catch (_: Throwable) {} }
            } catch (_: Throwable) {}
        }
    }

    private fun showBg(pic: Bitmap) {
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
        while (o.outWidth / (s * 2) >= maxW) s *= 2
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
    } catch (e: Throwable) {
        null
    }
}
