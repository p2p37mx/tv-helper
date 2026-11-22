package com.p2p37mx.tvhelper.tv

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

/** All the commands the phone app can send. */
object Api {
    const val VERSION = 1

    fun json(o: Any, code: Int = 200) =
        Res(code, "application/json; charset=utf-8", o.toString().toByteArray(Charsets.UTF_8))

    fun ok(v: Boolean = true) = json(JSONObject().put("ok", v))
    fun error(code: Int, msg: String) = json(JSONObject().put("ok", false).put("error", msg), code)

    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }

    /** Context to start activities from: the accessibility service is allowed to start them from the background. */
    fun launchCtx(c: Context): Context = RemoteAccessibilityService.instance ?: c

    fun route(ctx: Context, r: Req): Res {
        Store.init(ctx)
        return when (r.path) {
            "/info" -> json(
                JSONObject()
                    .put("app", "tvhelper")
                    .put("version", VERSION)
                    .put("name", Build.MODEL)
                    .put("remote", RemoteAccessibilityService.instance != null)
            )

            "/apps" -> {
                val hidden = Store.hidden
                val arr = JSONArray()
                AppsRepo.ordered(ctx).forEach {
                    arr.put(JSONObject().put("pkg", it.pkg).put("label", it.label).put("hidden", it.pkg in hidden))
                }
                json(arr)
            }

            "/icon" -> {
                val png = AppsRepo.iconPng(ctx, r.q("pkg")) ?: return error(404, "no app")
                Res(200, "image/png", png)
            }

            "/apps/save" -> {
                val o = JSONObject(String(r.body, Charsets.UTF_8))
                Store.order = o.getJSONArray("order").strings()
                Store.hidden = o.getJSONArray("hidden").strings().toSet()
                Store.changed()
                ok()
            }

            "/launch" -> ok(AppsRepo.launch(launchCtx(ctx), r.q("pkg")))

            "/images" -> {
                val arr = JSONArray()
                Store.images().forEach { arr.put(it.name) }
                json(arr)
            }

            "/image" -> {
                val f = File(Store.imagesDir, Store.safeName(r.q("name")))
                if (!f.exists()) return error(404, "no image")
                if (r.q("thumb") == "1") Res(200, "image/jpeg", thumb(f)) else Res(200, "image/jpeg", f.readBytes())
            }

            "/upload" -> {
                if (r.body.isEmpty()) return error(400, "empty")
                val name = Store.safeName(r.q("name"))
                val tmp = File(Store.imagesDir, ".$name.part")
                tmp.writeBytes(r.body)
                tmp.renameTo(File(Store.imagesDir, name))
                Store.changed()
                json(JSONObject().put("ok", true).put("name", name))
            }

            "/images/delete" -> {
                val f = File(Store.imagesDir, Store.safeName(r.q("name")))
                val done = f.delete()
                Store.changed()
                ok(done)
            }

            "/show" -> {
                val i = Intent(ctx, ViewerActivity::class.java)
                    .putExtra("name", r.q("name"))
                    .putExtra("slideshow", r.q("slideshow") == "1")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                ok(start(launchCtx(ctx), i))
            }

            "/key" -> { Store.lastActivity = System.currentTimeMillis(); ok(Remote.key(ctx, r.q("k"))) }

            "/dump" -> json(JSONObject().put("text", RemoteAccessibilityService.instance?.let { svc ->
                var t = ""
                val latch = java.util.concurrent.CountDownLatch(1)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    try { t = svc.dump() } catch (e: Exception) { t = e.toString() } finally { latch.countDown() }
                }
                latch.await(3, java.util.concurrent.TimeUnit.SECONDS)
                t
            } ?: "خدمة إمكانية الوصول مو مفعّلة"))

            "/saver" -> ok(start(launchCtx(ctx), Intent(ctx, SaverActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)))

            "/crash" -> {
                val f = File(ctx.filesDir, "crash.txt")
                if (r.q("clear") == "1") f.delete()
                json(JSONObject().put("crash", if (f.exists()) f.readText() else ""))
            }

            "/settings" -> {
                if (r.method == "POST" && r.body.isNotEmpty()) {
                    val o = JSONObject(String(r.body, Charsets.UTF_8))
                    if (o.has("title")) Store.title = o.getString("title")
                    if (o.has("interval")) Store.interval = o.getInt("interval")
                    if (o.has("clock")) Store.showClock = o.getBoolean("clock")
                    if (o.has("home")) Store.homeMode = o.getBoolean("home")
                    if (o.has("weather")) Store.showWeather = o.getBoolean("weather")
                    if (o.has("dua")) Store.showDua = o.getBoolean("dua")
                    if (o.has("date")) Store.showDate = o.getBoolean("date")
                    if (o.has("saver")) Store.saverMinutes = o.getInt("saver")
                    if (o.has("ytAds")) Store.ytAdSkip = o.getBoolean("ytAds")
                    o.optJSONObject("sizes")?.let { sz ->
                        Store.sizeKeys.forEach { k -> if (sz.has(k)) Store.setSize(k, sz.getInt(k)) }
                    }
                    Store.changed()
                }
                json(
                    JSONObject()
                        .put("title", Store.title)
                        .put("interval", Store.interval)
                        .put("clock", Store.showClock)
                        .put("home", Store.homeMode)
                        .put("weather", Store.showWeather)
                        .put("dua", Store.showDua)
                        .put("date", Store.showDate)
                        .put("saver", Store.saverMinutes)
                        .put("ytAds", Store.ytAdSkip)
                        .put("sizes", JSONObject().also { sz -> Store.sizeKeys.forEach { k -> sz.put(k, Store.size(k)) } })
                        .put("remote", RemoteAccessibilityService.instance != null)
                )
            }

            else -> error(404, "not found")
        }
    }

    private fun start(c: Context, i: Intent): Boolean = try {
        c.startActivity(i); true
    } catch (e: Exception) {
        false
    }

    private fun thumb(f: File): ByteArray {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var s = 1
        while (o.outWidth / (s * 2) >= 360) s *= 2
        val bmp = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
            ?: return f.readBytes()
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
        bmp.recycle()
        return out.toByteArray()
    }
}
