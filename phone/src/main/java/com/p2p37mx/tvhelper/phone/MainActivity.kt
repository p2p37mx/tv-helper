package com.p2p37mx.tvhelper.phone

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.concurrent.thread

class MainActivity : Activity() {

    companion object {
        const val CORAL = "#FF5A4F"
        const val CORAL2 = "#FF8A5B"
        const val INK = "#1F2330"
        const val SOFT = "#F6F6F8"
        const val PICK_IMAGES = 41
        const val PORT = 8765
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var status: TextView
    private lateinit var changeBtn: TextView
    private lateinit var tabsBar: LinearLayout
    private lateinit var content: FrameLayout
    private val tabViews = ArrayList<TextView>()
    private var tab = 0
    private var connected = false
    private var remoteReady = true

    private val icons = HashMap<String, Bitmap>()
    private var nsd: NsdManager? = null
    private var discovery: NsdManager.DiscoveryListener? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun c(s: String) = Color.parseColor(s)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("phone", MODE_PRIVATE)
        buildShell()
        val host = prefs.getString("host", null)
        if (host != null) connect(host, silent = true) else showConnect()
    }

    override fun onDestroy() {
        stopDiscovery()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- shell

    private fun buildShell() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(c(SOFT))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(c(CORAL), c(CORAL2)))
            setPadding(dp(20), dp(18), dp(20), dp(18))
        }
        val titleRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        titleRow.addView(TextView(this).apply {
            text = "مساعد الشاشة"
            textSize = 24f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, -2, 1f))
        changeBtn = pill("تغيير الشاشة", "#33FFFFFF", Color.WHITE) { showConnect() }
        titleRow.addView(changeBtn)
        header.addView(titleRow)
        status = TextView(this).apply {
            textSize = 14f
            setTextColor(c("#EEFFFFFF"))
            setPadding(0, dp(6), 0, 0)
        }
        header.addView(status)
        root.addView(header)

        tabsBar = LinearLayout(this).apply {
            setBackgroundColor(Color.WHITE)
            elevation = dp(3).toFloat()
        }
        listOf("الريموت", "الصور", "التطبيقات", "الواجهة").forEachIndexed { i, name ->
            val t = TextView(this).apply {
                text = name
                textSize = 15f
                gravity = Gravity.CENTER
                setPadding(0, dp(14), 0, dp(14))
                setOnClickListener { showTab(i) }
            }
            tabViews.add(t)
            tabsBar.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(tabsBar)

        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun setConnectedUi(on: Boolean) {
        connected = on
        tabsBar.visibility = if (on) View.VISIBLE else View.GONE
        changeBtn.visibility = if (on) View.VISIBLE else View.GONE
    }

    private fun showTab(i: Int) {
        tab = i
        tabViews.forEachIndexed { idx, t ->
            val sel = idx == i
            t.setTextColor(if (sel) c(CORAL) else c("#777B88"))
            t.typeface = if (sel) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            t.background = if (sel) underline() else null
        }
        content.removeAllViews()
        when (i) {
            0 -> remotePage()
            1 -> photosPage()
            2 -> appsPage()
            else -> settingsPage()
        }
    }

    private fun underline() = android.graphics.drawable.LayerDrawable(
        arrayOf<android.graphics.drawable.Drawable>(GradientDrawable().apply { setColor(c(CORAL)) })
    ).apply { setLayerInset(0, dp(18), dp(44), dp(18), 0) }

    private fun page(): LinearLayout {
        val sv = ScrollView(this).apply { isFillViewport = true }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }
        sv.addView(col)
        content.addView(sv, FrameLayout.LayoutParams(-1, -1))
        return col
    }

    // ---------------------------------------------------------------- widgets

    private fun rounded(color: String, radius: Int = 16) = GradientDrawable().apply {
        cornerRadius = dp(radius).toFloat()
        setColor(c(color))
    }

    private fun pill(label: String, bg: String, fg: Int, onClick: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 13f
        setTextColor(fg)
        gravity = Gravity.CENTER
        setPadding(dp(14), dp(7), dp(14), dp(7))
        background = RippleDrawable(ColorStateList.valueOf(c("#33000000")), rounded(bg, 20), null)
        setOnClickListener { onClick() }
    }

    private fun button(label: String, bg: String = CORAL, fg: String = "#FFFFFF", size: Float = 16f, onClick: () -> Unit) =
        TextView(this).apply {
            text = label
            textSize = size
            setTextColor(c(fg))
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), dp(14), dp(12), dp(14))
            background = RippleDrawable(ColorStateList.valueOf(c("#33000000")), rounded(bg), null)
            setOnClickListener { onClick() }
        }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded("#FFFFFF", 18)
        elevation = dp(2).toFloat()
        setPadding(dp(16), dp(16), dp(16), dp(16))
    }

    private fun label(t: String, size: Float = 15f, color: String = INK, bold: Boolean = false) = TextView(this).apply {
        text = t
        textSize = size
        setTextColor(c(color))
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setLineSpacing(0f, 1.2f)
    }

    private fun gap(h: Int = 12) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(h)) }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()

    // ---------------------------------------------------------------- connect

    private fun showConnect() {
        setConnectedUi(false)
        status.text = "مو متصل"
        content.removeAllViews()
        val col = page()

        val box = card()
        box.addView(label("الشاشات اللي على نفس الواي فاي", 17f, bold = true))
        box.addView(gap(6))
        val searching = label("جاري البحث… تأكد إن تطبيق «مساعد الشاشة» منصّب على الشاشة ومفتوح مرة وحدة على الأقل.", 13f, "#777B88")
        box.addView(searching)
        box.addView(gap(10))
        val found = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(found)
        col.addView(box)
        col.addView(gap(16))

        val manual = card()
        manual.addView(label("أو اكتب عنوان الشاشة يدوي", 17f, bold = true))
        manual.addView(label("العنوان مكتوب جوه بواجهة الشاشة (مثال: 192.168.9.105)", 13f, "#777B88"))
        manual.addView(gap(10))
        val input = EditText(this).apply {
            hint = "192.168.9.105"
            inputType = InputType.TYPE_CLASS_PHONE
            setText(prefs.getString("host", "")?.substringBefore(":") ?: "")
            textDirection = View.TEXT_DIRECTION_LTR
        }
        manual.addView(input)
        manual.addView(gap(8))
        manual.addView(button("اتصال") {
            val ip = input.text.toString().trim()
            if (ip.isEmpty()) toast("اكتب العنوان") else connect(if (ip.contains(":")) ip else "$ip:$PORT", silent = false)
        })
        col.addView(manual)

        startDiscovery { name, host ->
            searching.text = "اضغط على الشاشة حتى تتصل بيها:"
            val tag = "$host"
            for (i in 0 until found.childCount) if (found.getChildAt(i).tag == tag) return@startDiscovery
            val b = button("📺  $name\n$host", "#FFF1EF", CORAL, 15f) { connect(host, silent = false) }
            b.tag = tag
            found.addView(b)
            found.addView(gap(8))
        }
    }

    private fun startDiscovery(onFound: (String, String) -> Unit) {
        stopDiscovery()
        val m = getSystemService(NsdManager::class.java) ?: return
        nsd = m
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(t: String) {}
            override fun onDiscoveryStopped(t: String) {}
            override fun onStartDiscoveryFailed(t: String, e: Int) {}
            override fun onStopDiscoveryFailed(t: String, e: Int) {}
            override fun onServiceLost(s: NsdServiceInfo) {}
            override fun onServiceFound(s: NsdServiceInfo) {
                try {
                    @Suppress("DEPRECATION")
                    m.resolveService(s, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(si: NsdServiceInfo, e: Int) {}
                        override fun onServiceResolved(si: NsdServiceInfo) {
                            @Suppress("DEPRECATION")
                            val ip = si.host?.hostAddress ?: return
                            if (ip.contains(":")) return // skip IPv6
                            val name = si.serviceName.removePrefix("TVHelper ").ifBlank { "الشاشة" }
                            runOnUiThread { onFound(name, "$ip:${si.port}") }
                        }
                    })
                } catch (_: Exception) {}
            }
        }
        try {
            m.discoverServices("_tvhelper._tcp", NsdManager.PROTOCOL_DNS_SD, l)
            discovery = l
        } catch (_: Exception) {}
    }

    private fun stopDiscovery() {
        try { discovery?.let { nsd?.stopServiceDiscovery(it) } } catch (_: Exception) {}
        discovery = null
    }

    private fun connect(host: String, silent: Boolean) {
        Net.base = "http://$host"
        status.text = "جاري الاتصال بـ $host …"
        Net.async({ Net.getJson("/info") }) { info, e ->
            if (info == null || info.optString("app") != "tvhelper") {
                if (!silent) toast("ما گدرت أتصل. تأكد إن الشاشة والتلفون على نفس الواي فاي")
                if (!connected) showConnect()
                status.text = "ما متصل بـ $host"
                return@async
            }
            stopDiscovery()
            prefs.edit().putString("host", host).apply()
            remoteReady = info.optBoolean("remote", false)
            setConnectedUi(true)
            status.text = "✅ متصل: ${info.optString("name")}  ($host)"
            showTab(tab)
        }
    }

    // ---------------------------------------------------------------- remote

    private fun remotePage() {
        val col = page()

        if (!remoteReady) {
            val warn = card().apply { background = rounded("#FFF4E5", 18) }
            warn.addView(label("⚠️ الأسهم وموافق والرجوع تحتاج تفعيل مرة وحدة بالشاشة:", 14f, "#8A5300", true))
            warn.addView(label("الإعدادات ← تفضيلات الجهاز ← إمكانية الوصول ← «مساعد الشاشة - الريموت» ← تشغيل.\nالصوت وفتح التطبيقات يشتغلون بدون تفعيل.", 13f, "#8A5300"))
            col.addView(warn)
            col.addView(gap(14))
        }

        val pad = card().apply { gravity = Gravity.CENTER_HORIZONTAL }
        val size = dp(78)
        fun key(t: String, k: String, bg: String = "#F1F2F6", fg: String = INK, w: Int = size, h: Int = size, r: Int = 39) =
            TextView(this).apply {
                text = t
                textSize = 22f
                gravity = Gravity.CENTER
                setTextColor(c(fg))
                typeface = Typeface.DEFAULT_BOLD
                background = RippleDrawable(ColorStateList.valueOf(c("#33FF5A4F")), rounded(bg, r), null)
                layoutParams = LinearLayout.LayoutParams(w, h).apply { setMargins(dp(6), dp(6), dp(6), dp(6)) }
                setOnClickListener { v -> v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); sendKey(k) }
            }
        fun spacer() = View(this).apply { layoutParams = LinearLayout.LayoutParams(size, size).apply { setMargins(dp(6), dp(6), dp(6), dp(6)) } }

        // The D-pad must keep left/right as on the remote even though the app is RTL
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        grid.addView(LinearLayout(this).apply { addView(spacer()); addView(key("▲", "up")); addView(spacer()) })
        grid.addView(LinearLayout(this).apply {
            addView(key("◀", "left")); addView(key("OK", "ok", CORAL, "#FFFFFF")); addView(key("▶", "right"))
        })
        grid.addView(LinearLayout(this).apply { addView(spacer()); addView(key("▼", "down")); addView(spacer()) })
        pad.addView(grid)
        col.addView(pad)
        col.addView(gap(14))

        fun rowOf(vararg items: Pair<String, String>) = LinearLayout(this).apply {
            items.forEachIndexed { i, (t, k) ->
                addView(button(t, "#FFFFFF", INK, 15f) { sendKey(k) }.apply {
                    elevation = dp(1).toFloat()
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { if (i > 0) marginStart = dp(10) })
            }
        }
        col.addView(rowOf("↩ رجوع" to "back", "⌂ الرئيسية" to "home", "★ واجهتي" to "myhome"))
        col.addView(gap(10))
        col.addView(rowOf("🔉 صوت −" to "voldown", "🔇 كتم" to "mute", "🔊 صوت +" to "volup"))
        col.addView(gap(10))
        col.addView(rowOf("⚙ إعدادات الشاشة" to "settings"))
        col.addView(gap(12))
        col.addView(label("تلميح: أزرار الصوت بالتلفون تغيّر صوت الشاشة وانت بهالصفحة.", 12f, "#777B88"))
    }

    private fun sendKey(k: String) {
        Net.async({ Net.getJson("/key?k=" + Net.enc(k)).optBoolean("ok") }) { ok, e ->
            when {
                e != null -> toast("ما گدرت أوصل للشاشة")
                ok == false -> toast("فعّل «مساعد الشاشة - الريموت» من إمكانية الوصول بالشاشة")
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (connected && tab == 0) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) { sendKey("volup"); return true }
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) { sendKey("voldown"); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    // ---------------------------------------------------------------- photos

    private var photoStatus: TextView? = null
    private var photoList: LinearLayout? = null

    private fun photosPage() {
        val col = page()
        col.addView(button("➕  إضافة صور من الاستوديو") {
            val i = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(Intent.createChooser(i, "اختار الصور"), PICK_IMAGES)
        })
        col.addView(gap(10))
        col.addView(button("▶  عرض كل الصور بالشاشة (سلايد شو)", "#FFFFFF", CORAL, 15f) {
            Net.async({ Net.get("/show?slideshow=1") }) { _, e -> if (e != null) toast("ما گدرت أوصل للشاشة") }
        })
        col.addView(gap(8))
        val st = label("الصور تطلع خلفية بواجهة الشاشة وتتبدل وحدها.", 13f, "#777B88")
        photoStatus = st
        col.addView(st)
        col.addView(gap(10))
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        photoList = list
        col.addView(list)
        loadPhotos()
    }

    private fun loadPhotos() {
        val list = photoList ?: return
        Net.async({ Net.getArray("/images") }) { arr, e ->
            if (arr == null) { toast("ما گدرت أجيب الصور"); return@async }
            list.removeAllViews()
            if (arr.length() == 0) list.addView(label("ماكو صور بعد. ضيف من الزر الفوق 👆", 14f, "#777B88"))
            for (i in arr.length() - 1 downTo 0) list.addView(photoRow(arr.getString(i)))
        }
    }

    private fun photoRow(name: String): View {
        val row = card().apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val img = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded("#EEEEEE", 10)
            clipToOutline = true
        }
        row.addView(img, LinearLayout.LayoutParams(dp(112), dp(63)))
        Net.async({ Net.get("/image?thumb=1&name=" + Net.enc(name)).let { BitmapFactory.decodeByteArray(it, 0, it.size) } }) { b, _ ->
            if (b != null) img.setImageBitmap(b)
        }
        row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        row.addView(pill("اعرض", "#FFF1EF", c(CORAL)) {
            Net.async({ Net.get("/show?name=" + Net.enc(name)) }) { _, e -> if (e != null) toast("ما گدرت أوصل للشاشة") }
        })
        row.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
        row.addView(pill("حذف", "#F1F2F6", c("#C0392B")) {
            AlertDialog.Builder(this)
                .setMessage("تحذف هالصورة من الشاشة؟")
                .setPositiveButton("حذف") { _, _ ->
                    Net.async({ Net.get("/images/delete?name=" + Net.enc(name)) }) { _, _ -> loadPhotos() }
                }
                .setNegativeButton("لا", null)
                .show()
        })
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(row)
            addView(gap(10))
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_IMAGES || resultCode != RESULT_OK || data == null) return
        val uris = ArrayList<Uri>()
        data.clipData?.let { cd -> for (i in 0 until cd.itemCount) uris.add(cd.getItemAt(i).uri) }
        if (uris.isEmpty()) data.data?.let { uris.add(it) }
        if (uris.isEmpty()) return
        upload(uris)
    }

    private fun upload(uris: List<Uri>) {
        thread {
            var ok = 0
            uris.forEachIndexed { i, uri ->
                runOnUiThread { photoStatus?.text = "جاري الرفع ${i + 1} من ${uris.size} …" }
                try {
                    val bytes = compress(uri)
                    val name = "img_${System.currentTimeMillis()}_$i.jpg"
                    Net.request("POST", "/upload?name=" + Net.enc(name), bytes, "image/jpeg")
                    ok++
                } catch (_: Exception) {}
            }
            runOnUiThread {
                photoStatus?.text = "✅ انرفعت $ok من ${uris.size} صورة"
                loadPhotos()
            }
        }
    }

    private fun compress(uri: Uri): ByteArray {
        val src = ImageDecoder.createSource(contentResolver, uri)
        val bmp = ImageDecoder.decodeBitmap(src) { d, info, _ ->
            val w = info.size.width
            val h = info.size.height
            val scale = minOf(1f, 1920f / maxOf(w, h))
            if (scale < 1f) d.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 88, out)
        bmp.recycle()
        return out.toByteArray()
    }

    // ---------------------------------------------------------------- apps

    private class AppItem(val pkg: String, val label: String, var hidden: Boolean)

    private val apps = ArrayList<AppItem>()
    private var appsList: LinearLayout? = null

    private fun appsPage() {
        val col = page()
        col.addView(label("رتب التطبيقات اللي تطلع بواجهة الشاشة. الأسهم تحرّك التطبيق، والمفتاح يظهره أو يخفيه. اضغط على اسم التطبيق حتى ينفتح بالشاشة.", 13f, "#777B88"))
        col.addView(gap(10))
        col.addView(button("💾  حفظ الترتيب بالشاشة") { saveApps() })
        col.addView(gap(12))
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        appsList = list
        col.addView(list)
        list.addView(label("جاري التحميل…", 14f, "#777B88"))
        Net.async({ Net.getArray("/apps") }) { arr, e ->
            if (arr == null) { toast("ما گدرت أجيب التطبيقات"); return@async }
            apps.clear()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                apps.add(AppItem(o.getString("pkg"), o.getString("label"), o.optBoolean("hidden")))
            }
            renderApps()
        }
    }

    private fun renderApps() {
        val list = appsList ?: return
        list.removeAllViews()
        apps.forEachIndexed { idx, app ->
            val row = card().apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                alpha = if (app.hidden) 0.5f else 1f
            }
            val icon = ImageView(this)
            row.addView(icon, LinearLayout.LayoutParams(dp(40), dp(40)))
            val cached = icons[app.pkg]
            if (cached != null) icon.setImageBitmap(cached) else {
                Net.async({ Net.get("/icon?pkg=" + Net.enc(app.pkg)).let { BitmapFactory.decodeByteArray(it, 0, it.size) } }) { b, _ ->
                    if (b != null) { icons[app.pkg] = b; icon.setImageBitmap(b) }
                }
            }
            row.addView(TextView(this).apply {
                text = "${idx + 1}. ${app.label}"
                textSize = 15f
                setTextColor(c(INK))
                maxLines = 2
                setPadding(dp(10), 0, dp(6), 0)
                setOnClickListener {
                    Net.async({ Net.getJson("/launch?pkg=" + Net.enc(app.pkg)) }) { r, e ->
                        toast(if (r?.optBoolean("ok") == true) "انفتح ${app.label} بالشاشة" else "ما انفتح")
                    }
                }
            }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(pill("▲", "#F1F2F6", c(INK)) { move(idx, -1) })
            row.addView(View(this), LinearLayout.LayoutParams(dp(6), 1))
            row.addView(pill("▼", "#F1F2F6", c(INK)) { move(idx, 1) })
            row.addView(View(this), LinearLayout.LayoutParams(dp(6), 1))
            row.addView(Switch(this).apply {
                isChecked = !app.hidden
                setOnCheckedChangeListener { _, on ->
                    app.hidden = !on
                    row.alpha = if (app.hidden) 0.5f else 1f
                }
            })
            list.addView(row)
            list.addView(gap(8))
        }
    }

    private fun move(idx: Int, d: Int) {
        val to = idx + d
        if (to < 0 || to >= apps.size) return
        val tmp = apps[idx]
        apps[idx] = apps[to]
        apps[to] = tmp
        renderApps()
    }

    private fun saveApps() {
        val o = JSONObject()
            .put("order", JSONArray(apps.map { it.pkg }))
            .put("hidden", JSONArray(apps.filter { it.hidden }.map { it.pkg }))
        Net.async({ Net.postJson("/apps/save", o) }) { r, e ->
            toast(if (e == null) "✅ انحفظ الترتيب بالشاشة" else "ما انحفظ، تأكد من الاتصال")
        }
    }

    // ---------------------------------------------------------------- home-screen settings

    private fun settingsPage() {
        val col = page()
        val box = card()
        box.addView(label("شكل واجهة الشاشة", 17f, bold = true))
        box.addView(gap(10))
        box.addView(label("عنوان يطلع فوق (اختياري)", 13f, "#777B88"))
        val title = EditText(this).apply { hint = "مثلاً: أهلاً وسهلاً" }
        box.addView(title)
        box.addView(gap(8))
        box.addView(label("كل كم ثانية تتبدل الخلفية", 13f, "#777B88"))
        val interval = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER }
        box.addView(interval)
        box.addView(gap(8))
        val clock = Switch(this).apply { text = "إظهار الساعة والتاريخ"; textSize = 15f }
        box.addView(clock)
        box.addView(gap(12))
        box.addView(button("💾  حفظ") {
            val o = JSONObject()
                .put("title", title.text.toString())
                .put("interval", interval.text.toString().toIntOrNull() ?: 15)
                .put("clock", clock.isChecked)
            Net.async({ Net.postJson("/settings", o) }) { _, e ->
                toast(if (e == null) "✅ انحفظ" else "ما انحفظ، تأكد من الاتصال")
            }
        })
        col.addView(box)
        col.addView(gap(16))

        val help = card()
        help.addView(label("واجهتي والواجهة الأصلية", 17f, bold = true))
        help.addView(gap(6))
        help.addView(
            label(
                "• «واجهتي» تنفتح من زر ★ بالريموت أو من تطبيق «مساعد الشاشة» بالشاشة.\n" +
                    "• الواجهة الأصلية مال الشاشة باقية مثل ما هي، وزر الرئيسية بالريموت يرجعك إلها.\n" +
                    "• إذا الشاشة سألتك تختار واجهة رئيسية، تكدر تختار «مساعد الشاشة» أو تبقى على الأصلية.",
                14f
            )
        )
        col.addView(help)

        Net.async({ Net.getJson("/settings") }) { s, _ ->
            if (s == null) return@async
            title.setText(s.optString("title"))
            interval.setText(s.optInt("interval", 15).toString())
            clock.isChecked = s.optBoolean("clock", true)
        }
    }
}
