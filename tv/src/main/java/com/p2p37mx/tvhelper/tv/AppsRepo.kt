package com.p2p37mx.tvhelper.tv

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import java.io.ByteArrayOutputStream

data class AppInfo(val pkg: String, val label: String, val activity: ActivityInfo)

object AppsRepo {

    fun all(c: Context): List<AppInfo> {
        val pm = c.packageManager
        val map = LinkedHashMap<String, AppInfo>()
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val i = Intent(Intent.ACTION_MAIN).addCategory(cat)
            for (ri in pm.queryIntentActivities(i, 0)) {
                val p = ri.activityInfo.packageName
                if (p == c.packageName || map.containsKey(p)) continue
                map[p] = AppInfo(p, ri.loadLabel(pm).toString(), ri.activityInfo)
            }
        }
        return map.values.toList()
    }

    /** Saved order first, then any new apps alphabetically. */
    fun ordered(c: Context): List<AppInfo> {
        val apps = all(c)
        val order = Store.order
        val byPkg = apps.associateBy { it.pkg }
        val res = ArrayList<AppInfo>()
        order.forEach { p -> byPkg[p]?.let { res.add(it) } }
        apps.filter { it.pkg !in order }.sortedBy { it.label.lowercase() }.forEach { res.add(it) }
        return res
    }

    fun visible(c: Context): List<AppInfo> {
        val hidden = Store.hidden
        return ordered(c).filter { it.pkg !in hidden }
    }

    fun launch(c: Context, pkg: String): Boolean {
        val pm = c.packageManager
        val i = pm.getLeanbackLaunchIntentForPackage(pkg)
            ?: pm.getLaunchIntentForPackage(pkg)
            ?: return false
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return try {
            c.startActivity(i); true
        } catch (e: Exception) {
            false
        }
    }

    fun banner(c: Context, a: ActivityInfo): Drawable? {
        val pm = c.packageManager
        return a.loadBanner(pm) ?: a.applicationInfo.loadBanner(pm)
    }

    fun icon(c: Context, a: ActivityInfo): Drawable = a.loadIcon(c.packageManager)

    fun iconPng(c: Context, pkg: String): ByteArray? {
        val app = all(c).firstOrNull { it.pkg == pkg } ?: return null
        val d = icon(c, app.activity)
        return toPng(d, 144, 144)
    }

    fun toPng(d: Drawable, w: Int, h: Int): ByteArray {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        d.setBounds(0, 0, w, h)
        d.draw(cv)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        bmp.recycle()
        return out.toByteArray()
    }
}
