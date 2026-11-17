package com.p2p37mx.tvhelper.tv

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Mosul weather from Open-Meteo (free, no key). */
object Weather {
    private const val URL_MOSUL =
        "https://api.open-meteo.com/v1/forecast?latitude=36.34&longitude=43.13" +
            "&current=temperature_2m,weather_code,is_day&timezone=auto"

    class Now(val temp: Int, val icon: String, val desc: String)

    fun fetch(): Now? = try {
        val c = URL(URL_MOSUL).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        val body = c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        c.disconnect()
        val cur = JSONObject(body).getJSONObject("current")
        val code = cur.optInt("weather_code", 0)
        val day = cur.optInt("is_day", 1) == 1
        val (icon, desc) = describe(code, day)
        Now(Math.round(cur.getDouble("temperature_2m")).toInt(), icon, desc)
    } catch (e: Throwable) {
        null
    }

    private fun describe(code: Int, day: Boolean): Pair<String, String> = when (code) {
        0 -> (if (day) "☀️" else "🌙") to "صافي"
        1, 2 -> (if (day) "⛅" else "☁️") to "غائم جزئياً"
        3 -> "☁️" to "غائم"
        45, 48 -> "🌫️" to "ضباب"
        in 51..57 -> "🌦️" to "رذاذ"
        in 61..67 -> "🌧️" to "مطر"
        in 71..77 -> "❄️" to "ثلج"
        in 80..82 -> "🌦️" to "زخات مطر"
        in 85..86 -> "🌨️" to "زخات ثلج"
        in 95..99 -> "⛈️" to "عاصفة رعدية"
        else -> "🌤️" to ""
    }
}

/** Short duas; a new one shows with every photo. */
object Duas {
    val list = listOf(
        "ربنا آتنا في الدنيا حسنة وفي الآخرة حسنة وقنا عذاب النار",
        "رب اشرح لي صدري ويسر لي أمري",
        "رب زدني علماً",
        "اللهم إني أسألك العفو والعافية",
        "يا مقلب القلوب ثبت قلبي على دينك",
        "حسبي الله لا إله إلا هو عليه توكلت",
        "رب اغفر لي ولوالدي",
        "ربنا هب لنا من أزواجنا وذرياتنا قرة أعين",
        "لا إله إلا أنت سبحانك إني كنت من الظالمين",
        "سبحان الله وبحمده سبحان الله العظيم",
        "اللهم صل وسلم على نبينا محمد",
        "رب اجعلني مقيم الصلاة ومن ذريتي",
        "اللهم أعني على ذكرك وشكرك وحسن عبادتك",
        "أستغفر الله العظيم وأتوب إليه",
        "لا حول ولا قوة إلا بالله",
        "اللهم احفظ أهلنا وبيتنا من كل سوء",
        "الحمد لله على كل حال",
        "ربنا لا تزغ قلوبنا بعد إذ هديتنا",
    )
    private var last = -1

    fun next(): String {
        var i: Int
        do { i = list.indices.random() } while (i == last && list.size > 1)
        last = i
        return list[i]
    }
}
