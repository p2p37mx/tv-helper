package com.p2p37mx.tvhelper.tv

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Lets the phone press Back / Home / arrows / OK on the TV.
 * The user turns it on once: Settings → Device Preferences → Accessibility.
 */
class RemoteAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: RemoteAccessibilityService? = null
    }

    private val h = Handler(Looper.getMainLooper())
    private var homePkgs: Set<String> = emptySet()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Store.init(this)
        ServerService.start(this)
        homePkgs = findHomePackages()
        h.removeCallbacks(saverCheck)
        h.postDelayed(saverCheck, 20000)
        // After power on the original home screen is usually already showing: replace it.
        h.postDelayed({
            val fg = rootInActiveWindow?.packageName?.toString()
            if (Store.homeMode && fg != null && fg in homePkgs) openOurHome()
        }, 1500)
    }

    /** Our own screensaver: photos after some minutes without the remote, unless sound is playing. */
    private val saverCheck: Runnable = object : Runnable {
        override fun run() {
            try { maybeStartSaver() } catch (_: Exception) {}
            h.postDelayed(this, 20000)
        }
    }

    private fun maybeStartSaver() {
        val mins = Store.saverMinutes
        if (mins <= 0 || SaverActivity.showing) return
        if (System.currentTimeMillis() - Store.lastActivity < mins * 60_000L) return
        val pm = getSystemService(android.os.PowerManager::class.java)
        if (pm != null && !pm.isInteractive) return
        val am = getSystemService(android.media.AudioManager::class.java)
        if (am != null && am.isMusicActive) return // something is playing (video, music)
        startActivity(Intent(this, SaverActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private val checkHome = Runnable {
        val fg = try { rootInActiveWindow?.packageName?.toString() } catch (e: Exception) { null }
        if (fg != null && fg in homePkgs && Store.homeMode && !Store.bypassOriginal &&
            System.currentTimeMillis() > Store.suppressUntil
        ) openOurHome()
    }

    /** The TV's original home screen apps (TCL launcher etc.), not ours. */
    private fun findHomePackages(): Set<String> {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.queryIntentActivities(i, 0)
            .map { it.activityInfo.packageName }
            .filter {
                it != packageName && it != "android" && !it.contains("settings") &&
                    !it.contains("setupwizard") && !it.contains("provision")
            }
            .toSet()
    }

    fun originalHomePackage(): String? {
        if (homePkgs.isEmpty()) homePkgs = findHomePackages()
        return homePkgs.firstOrNull()
    }

    fun openOurHome(): Boolean {
        val i = Intent(this, HomeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return try { startActivity(i); true } catch (e: Exception) { false }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        Store.lastActivity = System.currentTimeMillis()
        if (event.keyCode != KeyEvent.KEYCODE_HOME || !Store.homeMode) return false
        if (event.action == KeyEvent.ACTION_UP) {
            Store.bypassOriginal = false
            openOurHome()
        }
        return true
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg != packageName) Store.lastActivity = System.currentTimeMillis()
        if (pkg == packageName || pkg == "com.android.systemui") return
        if (homePkgs.isEmpty()) homePkgs = findHomePackages()
        if (pkg in homePkgs) {
            if (Store.homeMode && !Store.bypassOriginal && System.currentTimeMillis() > Store.suppressUntil) {
                // wait a moment and make sure the original home screen is REALLY what's showing,
                // not just passing by while an app opens
                h.removeCallbacks(checkHome)
                h.postDelayed(checkHome, 900)
            }
        } else {
            h.removeCallbacks(checkHome)
            // the user went into some app: next time Home goes back to our screen
            Store.bypassOriginal = false
        }
    }
    override fun onInterrupt() {}

    fun handle(k: String): Boolean = when (k) {
        "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
        "home" -> if (Store.homeMode) openOurHome() else performGlobalAction(GLOBAL_ACTION_HOME)
        "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
        "notifications" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        "up" -> move(View.FOCUS_UP, 16)
        "down" -> move(View.FOCUS_DOWN, 17)
        "left" -> move(View.FOCUS_LEFT, 18)
        "right" -> move(View.FOCUS_RIGHT, 19)
        "ok" -> click()
        else -> false
    }

    private fun focused(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
    }

    /** globalDpad = GLOBAL_ACTION_DPAD_* constant, only on Android 13+. */
    private fun move(dir: Int, globalDpad: Int): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && performGlobalAction(globalDpad)) return true
        val f = focused()
        if (f == null) {
            val root = rootInActiveWindow ?: return false
            val first = firstFocusable(root) ?: return false
            return first.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        }
        val next = f.focusSearch(dir) ?: return false
        return next.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
    }

    private fun firstFocusable(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (n.isFocusable && n.isVisibleToUser) return n
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            firstFocusable(c)?.let { return it }
        }
        return null
    }

    private fun click(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && performGlobalAction(20)) return true // GLOBAL_ACTION_DPAD_CENTER
        var n = focused()
        while (n != null) {
            if (n.isClickable && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            n = n.parent
        }
        return false
    }
}
