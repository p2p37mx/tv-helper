package com.p2p37mx.tvhelper.tv

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import kotlin.math.max

/** Picks which part of a photo to keep when it has to be cropped to fill the screen: the faces. */
object FaceCrop {

    /**
     * Point to keep in view, as fractions (0..1) of the photo. The phone finds the faces when
     * uploading and puts their position in the file name: img_..._f500_300.jpg (x=0.5, y=0.3).
     */
    fun focus(name: String, bw: Int, bh: Int): PointF {
        val m = Regex("_f(\\d{1,4})_(\\d{1,4})").find(name)
        if (m != null) {
            val x = m.groupValues[1].toFloat() / 1000f
            val y = m.groupValues[2].toFloat() / 1000f
            return PointF(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
        }
        // no face info: people's heads are usually in the upper part of tall photos
        return PointF(0.5f, if (bh > bw) 0.3f else 0.45f)
    }

    /** Fill a vw×vh view with the photo, keeping [focus] in view (a bit above the middle). */
    fun matrix(bw: Int, bh: Int, vw: Int, vh: Int, focus: PointF): Matrix {
        val scale = max(vw.toFloat() / bw, vh.toFloat() / bh)
        val sw = bw * scale
        val sh = bh * scale
        val dx = (vw / 2f - focus.x * sw).coerceIn(vw - sw, 0f)
        val dy = (vh * 0.42f - focus.y * sh).coerceIn(vh - sh, 0f)
        return Matrix().apply {
            setScale(scale, scale)
            postTranslate(dx, dy)
        }
    }

    /**
     * Lays a photo out on a screen-sized picture:
     *  - wide photos (about 16:9): fill the screen
     *  - other landscape photos (4:3 …) and [fitAlways]: the whole photo over a blurred copy of itself
     *  - tall photos: fill the screen, cropped around the faces
     */
    fun compose(bmp: Bitmap, name: String, vw: Int, vh: Int, fitAlways: Boolean = false): Bitmap {
        val out = Bitmap.createBitmap(vw, vh, Bitmap.Config.ARGB_8888)
        val cv = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val bw = bmp.width
        val bh = bmp.height
        val aspect = bw.toFloat() / bh
        val fill = !fitAlways && (aspect >= 1.6f || aspect < 1f)
        if (fill) {
            cv.drawBitmap(bmp, matrix(bw, bh, vw, vh, focus(name, bw, bh)), paint)
            return out
        }
        // blurred background: shrink a lot, then stretch back up
        val sw = 32
        val sh = (32 / aspect).toInt().coerceAtLeast(1)
        val tiny = Bitmap.createScaledBitmap(bmp, sw, sh, true)
        cv.drawBitmap(tiny, matrix(sw, sh, vw, vh, PointF(0.5f, 0.5f)), paint)
        tiny.recycle()
        cv.drawColor(Color.parseColor("#73000000"))
        // the whole photo, centred
        val scale = minOf(vw.toFloat() / bw, vh.toFloat() / bh)
        val m = Matrix().apply {
            setScale(scale, scale)
            postTranslate((vw - bw * scale) / 2f, (vh - bh * scale) / 2f)
        }
        cv.drawBitmap(bmp, m, paint)
        return out
    }
}
