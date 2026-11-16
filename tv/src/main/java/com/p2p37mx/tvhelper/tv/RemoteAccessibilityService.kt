package com.p2p37mx.tvhelper.tv

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
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

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        ServerService.start(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun handle(k: String): Boolean = when (k) {
        "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
        "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
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
