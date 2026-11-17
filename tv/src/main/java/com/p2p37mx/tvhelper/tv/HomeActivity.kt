package com.p2p37mx.tvhelper.tv

import android.app.Activity
import android.content.Intent
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
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Our own TV home screen: the photo stage (photos, clock, weather, dua)
 * plus an iPhone-style dock of app icons that slides away when the remote is idle.
 */
class HomeActivity : Activity() {

    companion object {
        const val IDLE_MS = 8000L
    }

    private lateinit var stage: Stage
    private lateinit var dockWrap: FrameLayout
    private lateinit var row: LinearLayout

    private val h = Handler(Looper.getMainLooper())
    private var focusedKey: String? = null
    private var dockShown = true
    private var dockScale = 100

    private val listener = Runnable { buildRow() }
    private val idle = Runnable { hideDock() }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun sdp(v: Int) = dp(v) * dockScale / 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        ServerService.start(this)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        Store.addListener(listener)
        stage.start()
        buildRow()
        showDock()
        wake()
    }

    override fun onPause() {
        Store.removeListener(listener)
        stage.stop()
        h.removeCallbacksAndMessages(null)
        super.onPause()
    }

    private fun buildUi() {
        stage = Stage(this)
        stage.clipChildren = false

        dockWrap = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
        }
        val scroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            setPadding(dp(10), dp(8), dp(10), dp(6))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(Color.parseColor("#4D101418"))
                setStroke(dp(1), Color.parseColor("#33FFFFFF"))
            }
        }
        row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
            setPadding(0, dp(2), 0, 0)
        }
        scroller.addView(row)
        dockWrap.addView(scroller, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL))
        stage.addView(dockWrap, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            setMargins(dp(48), 0, dp(48), dp(28))
        })
        setContentView(stage)
    }

    // ------------------------------------------------------------ dock

    private fun buildRow() {
        dockScale = Store.size("dock")
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
        val size = sdp(56)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            isFocusable = true
            isClickable = true
            clipChildren = false
            setPadding(dp(6), dp(4), dp(6), dp(4))
            layoutParams = LinearLayout.LayoutParams(sdp(80), -2)
        }
        val radius = sdp(14).toFloat()
        val iconView = ImageView(this).apply {
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: android.graphics.Outline) {
                    o.setRoundRect(0, 0, v.width, v.height, radius)
                }
            }
            clipToOutline = true
            if (sysIcon != null) {
                setImageResource(sysIcon)
                setColorFilter(Color.WHITE)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                val p = sdp(12)
                setPadding(p, p, p, p)
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(Color.parseColor("#FF5A4F"), Color.parseColor("#FF8A5B"))
                )
            } else {
                setImageDrawable(icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
        }
        // white ring around the focused icon
        val ring = FrameLayout(this).apply { setPadding(dp(3), dp(3), dp(3), dp(3)) }
        ring.addView(iconView, FrameLayout.LayoutParams(size, size))
        box.addView(ring, LinearLayout.LayoutParams(-2, -2))
        val name = TextView(this).apply {
            text = label
            textSize = 12f * dockScale / 100f
            setTextColor(Color.WHITE)
            setShadowLayer(6f, 0f, 2f, Color.parseColor("#AA000000"))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            alpha = 0.8f
            setPadding(0, dp(6), 0, 0)
        }
        box.addView(name, LinearLayout.LayoutParams(-1, -2))

        box.setOnFocusChangeListener { _, has ->
            ring.background = if (has) GradientDrawable().apply {
                cornerRadius = radius + dp(3)
                setStroke(dp(2), Color.WHITE)
            } else null
            name.alpha = if (has) 1f else 0.8f
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
        stage.bottomShade.animate().alpha(1f).setDuration(350).start()
        if (row.findFocus() == null) row.getChildAt(0)?.requestFocus()
    }

    private fun hideDock() {
        dockShown = false
        val dist = (dockWrap.height + dp(60)).toFloat()
        dockWrap.animate().translationY(dist).alpha(0f).setDuration(600)
            .setInterpolator(DecelerateInterpolator()).start()
        stage.bottomShade.animate().alpha(0f).setDuration(600).start()
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
}
