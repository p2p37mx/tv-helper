package com.p2p37mx.tvhelper.tv

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

/**
 * YouTube TV doesn't let other apps read its text, so we LOOK at the screen instead:
 * every second we take a screenshot and search the bottom-right corner for the white
 * "Skip / التخطي" pill (it is white when it has the focus). While it's there: mute.
 * Then press OK (through ADB if it's connected, otherwise through accessibility).
 */
class YtAdWatcher(private val svc: AccessibilityService) {

    companion object {
        const val W = 640
        const val H = 360
    }

    private val exec = Executors.newSingleThreadExecutor()
    @Volatile private var busy = false
    @Volatile private var lastShotAt = 0L
    private var seenCount = 0
    private var goneCount = 0
    private var pressCount = 0
    private var lastPress = 0L

    @Volatile var adActive = false
        private set
    @Volatile var info = "لسه ما صار فحص"
        private set
    @Volatile var debugJpeg: ByteArray? = null
        private set

    /** Called often while YouTube is in front; takes at most one screenshot per ~1.1 s. */
    fun tick(onDone: (() -> Unit)? = null) {
        if (Build.VERSION.SDK_INT < 30) { info = "يحتاج أندرويد 11 أو أحدث"; return }
        val now = System.currentTimeMillis()
        if (busy || now - lastShotAt < 1100) return
        busy = true
        lastShotAt = now
        try {
            svc.takeScreenshot(Display.DEFAULT_DISPLAY, exec, object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    try {
                        val hb = result.hardwareBuffer
                        val hw = Bitmap.wrapHardwareBuffer(hb, result.colorSpace)
                        hb.close()
                        if (hw != null) {
                            val soft = hw.copy(Bitmap.Config.ARGB_8888, false)
                            hw.recycle()
                            val small = Bitmap.createScaledBitmap(soft, W, H, true)
                            soft.recycle()
                            analyse(small)
                            small.recycle()
                        } else info = "ما گدرت أقرا الصورة"
                    } catch (e: Throwable) {
                        info = "خطأ: $e"
                    } finally {
                        busy = false
                        onDone?.invoke()
                    }
                }

                override fun onFailure(errorCode: Int) {
                    info = if (errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS)
                        "ما عنده إذن تصوير الشاشة: طفّي «مساعد الشاشة - الريموت» من إمكانية الوصول وشغّله مرة ثانية"
                    else "فشل تصوير الشاشة (رمز $errorCode)"
                    busy = false
                    onDone?.invoke()
                }
            })
        } catch (e: Throwable) {
            info = "فشل تصوير الشاشة: $e"
            busy = false
            onDone?.invoke()
        }
    }

    fun reset() {
        seenCount = 0; goneCount = 0; pressCount = 0
        adActive = false
    }

    private fun bright(c: Int): Boolean {
        val r = Color.red(c); val g = Color.green(c); val b = Color.blue(c)
        return r >= 185 && g >= 185 && b >= 185 && max(r, max(g, b)) - min(r, min(g, b)) <= 45
    }

    /** Finds the white pill in the bottom-right corner. Returns its box or null. */
    private fun findPill(bmp: Bitmap): Rect? {
        val x0 = (W * 0.60).toInt(); val x1 = W - 2
        val y0 = (H * 0.74).toInt(); val y1 = H - 2
        val rw = x1 - x0; val rh = y1 - y0
        val px = IntArray(rw * rh)
        bmp.getPixels(px, 0, rw, x0, y0, rw, rh)
        val mask = BooleanArray(px.size) { bright(px[it]) }
        val seen = BooleanArray(px.size)
        val stack = IntArray(px.size)
        var best: Rect? = null
        var bestScore = 0
        for (start in px.indices) {
            if (!mask[start] || seen[start]) continue
            // flood fill one bright blob
            var sp = 0; stack[sp++] = start; seen[start] = true
            var count = 0; var minX = rw; var maxX = 0; var minY = rh; var maxY = 0
            var touchesEdge = false
            while (sp > 0) {
                val i = stack[--sp]; count++
                val x = i % rw; val y = i / rw
                if (x < minX) minX = x; if (x > maxX) maxX = x
                if (y < minY) minY = y; if (y > maxY) maxY = y
                if (x == 0 || y == 0 || x == rw - 1 || y == rh - 1) touchesEdge = true
                if (x > 0) { val j = i - 1; if (mask[j] && !seen[j]) { seen[j] = true; stack[sp++] = j } }
                if (x < rw - 1) { val j = i + 1; if (mask[j] && !seen[j]) { seen[j] = true; stack[sp++] = j } }
                if (y > 0) { val j = i - rw; if (mask[j] && !seen[j]) { seen[j] = true; stack[sp++] = j } }
                if (y < rh - 1) { val j = i + rw; if (mask[j] && !seen[j]) { seen[j] = true; stack[sp++] = j } }
            }
            if (touchesEdge) continue
            val bw = maxX - minX + 1; val bh = maxY - minY + 1
            // pill size on a 640×360 screen: ~45-150 px wide, ~12-34 px tall, wider than tall
            if (bw !in 40..160 || bh !in 11..36 || bw < bh * 2) continue
            val fill = count.toFloat() / (bw * bh)
            if (fill < 0.50f || fill > 0.97f) continue
            // there must be some dark text/icon inside
            var dark = 0
            for (y in minY..maxY) for (x in minX..maxX) {
                val c = px[y * rw + x]
                if ((Color.red(c) + Color.green(c) + Color.blue(c)) / 3 < 130) dark++
            }
            val darkRatio = dark.toFloat() / (bw * bh)
            if (darkRatio < 0.02f || darkRatio > 0.40f) continue
            if (count > bestScore) {
                bestScore = count
                best = Rect(x0 + minX, y0 + minY, x0 + maxX, y0 + maxY)
            }
        }
        return best
    }

    private fun analyse(bmp: Bitmap) {
        val pill = findPill(bmp)
        if (pill != null) { seenCount++; goneCount = 0 } else { goneCount++; seenCount = 0 }
        val was = adActive
        if (seenCount >= 2) adActive = true
        if (goneCount >= 2) { adActive = false; pressCount = 0 }

        var pressed = ""
        val now = System.currentTimeMillis()
        if (adActive && pill != null && now - lastPress > 2000) {
            lastPress = now
            pressCount++
            pressed = press()
        }
        info = buildString {
            append(if (pill != null) "✅ لكيت زر التخطي" else "ما اكو زر تخطي")
            append(if (adActive) " • إعلان (الصوت مكتوم)" else "")
            if (pressed.isNotEmpty()) append(" • ضغطت: ").append(pressed)
            append("\nADB: ").append(Adb.status)
        }
        if (was != adActive) RemoteAccessibilityService.instance?.onVisualAd(adActive)

        // debug picture: the screen with the search area (yellow) and the found pill (red)
        try {
            val dbg = bmp.copy(Bitmap.Config.ARGB_8888, true)
            val cv = Canvas(dbg)
            val p = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 2f }
            p.color = Color.YELLOW
            cv.drawRect((W * 0.60f), (H * 0.74f), W - 2f, H - 2f, p)
            if (pill != null) { p.color = Color.RED; p.strokeWidth = 3f; cv.drawRect(pill, p) }
            val out = java.io.ByteArrayOutputStream()
            dbg.compress(Bitmap.CompressFormat.JPEG, 80, out)
            dbg.recycle()
            debugJpeg = out.toByteArray()
        } catch (_: Throwable) {}
    }

    /** Presses OK on the focused Skip pill. */
    private fun press(): String {
        if (Adb.ready && Adb.key(svc, 23)) return "OK عبر ADB"
        // without ADB: ask the focused element to click (works only if YouTube passes it on)
        return try {
            val root = svc.rootInActiveWindow
            val f = root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                ?: root?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
            val ok = f?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
            if (ok) "نقرة عبر إمكانية الوصول" else "ما گدرت أضغط (يحتاج ADB)"
        } catch (e: Exception) {
            "ما گدرت أضغط"
        }
    }
}
