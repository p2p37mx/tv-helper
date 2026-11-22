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

    // ------------------------------------------------------------ YouTube ads: mute, then press Skip

    private val ytPackages = setOf(
        "com.google.android.youtube.tv", "com.google.android.youtube.tvkids",
        "com.google.android.youtube.tvmusic", "com.google.android.youtube",
        "com.google.android.apps.youtube.music", "com.google.android.apps.youtube.kids"
    )
    private val skipRe = Regex("(تخط|تخطّ|\\bskip\\b)", RegexOption.IGNORE_CASE)
    private val adRe = Regex(
        "(^\\s*(ad|إعلان)\\b|sponsored|مموّ?ل|إعلان\\s*[·•:\\d]|\\bad\\s*[·•]|\\bad \\d+ of|زيارة المعلن|visit advertiser|الإعلان)",
        RegexOption.IGNORE_CASE
    )
    private var ytMuted = false
    private var inYoutube = false
    private var lastScan = 0L
    private var lastSkip = 0L
    val ytWatcher by lazy { YtAdWatcher(this) }
    private var visualAd = false

    /** Called by the screenshot watcher when the Skip pill appears / goes away. */
    fun onVisualAd(on: Boolean) {
        h.post {
            visualAd = on
            if (on && !ytMuted && Store.ytAdSkip) setMute(true)
            if (!on && ytMuted) setMute(false)
        }
    }

    private val ytPoll: Runnable = object : Runnable {
        override fun run() {
            try { scanYoutube() } catch (_: Exception) {}
            if (inYoutube && Store.ytAdSkip) try { ytWatcher.tick() } catch (_: Throwable) {}
            if (inYoutube) h.postDelayed(this, 700) else { ytWatcher.reset(); visualAd = false }
        }
    }

    private fun walk(n: AccessibilityNodeInfo?, depth: Int, out: MutableList<AccessibilityNodeInfo>) {
        if (n == null || depth > 40 || out.size > 800) return
        out.add(n)
        for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1, out)
    }

    private fun textOf(n: AccessibilityNodeInfo) =
        listOfNotNull(n.text?.toString(), n.contentDescription?.toString()).joinToString(" ").trim()

    private fun setMute(on: Boolean) {
        val am = getSystemService(android.media.AudioManager::class.java) ?: return
        am.adjustStreamVolume(
            android.media.AudioManager.STREAM_MUSIC,
            if (on) android.media.AudioManager.ADJUST_MUTE else android.media.AudioManager.ADJUST_UNMUTE, 0
        )
        ytMuted = on
    }

    private fun scanYoutube() {
        lastScan = System.currentTimeMillis()
        val root = rootInActiveWindow
        val pkg = root?.packageName?.toString()
        if (!Store.ytAdSkip || pkg == null || pkg !in ytPackages) {
            if (pkg != null && pkg != packageName && pkg != "com.android.systemui") inYoutube = pkg in ytPackages
            if (ytMuted && (pkg == null || pkg !in ytPackages || !Store.ytAdSkip)) setMute(false)
            return
        }
        val nodes = ArrayList<AccessibilityNodeInfo>()
        walk(root, 0, nodes)
        var ad = false
        var skip: AccessibilityNodeInfo? = null
        for (n in nodes) {
            val t = textOf(n)
            if (t.isEmpty() || t.length > 80) continue
            if (skip == null && skipRe.containsMatchIn(t)) skip = n
            if (adRe.containsMatchIn(t)) ad = true
        }
        if (ad || skip != null || visualAd) {
            if (!ytMuted) setMute(true)
        } else if (ytMuted) {
            setMute(false)
        }
        val s = skip ?: return
        val now = System.currentTimeMillis()
        if (now - lastSkip < 900) return
        lastSkip = now
        var n: AccessibilityNodeInfo? = s
        while (n != null) {
            if (n.isClickable && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
            n = n.parent
        }
        s.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        s.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    /** Text of everything on the screen right now, to see what an app shows (used from the phone). */
    fun dump(): String {
        val root = rootInActiveWindow ?: return "ما اكو شاشة مقروءة"
        val nodes = ArrayList<AccessibilityNodeInfo>()
        walk(root, 0, nodes)
        val sb = StringBuilder("app: ${root.packageName}\n")
        for (n in nodes) {
            val t = textOf(n)
            if (t.isEmpty() && !n.isClickable) continue
            sb.append(n.className?.toString()?.substringAfterLast('.') ?: "?")
            if (n.isClickable) sb.append(" [click]")
            if (n.isFocused) sb.append(" [focus]")
            sb.append(" : ").append(t.take(100)).append('\n')
            if (sb.length > 12000) break
        }
        return sb.toString()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val evPkg = event.packageName?.toString()
        if (evPkg != null && evPkg in ytPackages && Store.ytAdSkip) {
            if (!inYoutube) {
                inYoutube = true
                h.removeCallbacks(ytPoll)
                h.post(ytPoll)
            } else if (System.currentTimeMillis() - lastScan > 250) {
                try { scanYoutube() } catch (_: Exception) {}
            }
        }
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in ytPackages && pkg != packageName && pkg != "com.android.systemui") {
            inYoutube = false
            if (ytMuted) setMute(false)
        }
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
