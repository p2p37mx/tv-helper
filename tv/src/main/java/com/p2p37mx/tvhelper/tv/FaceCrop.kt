package com.p2p37mx.tvhelper.tv

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
import android.media.FaceDetector
import kotlin.math.max

/** Picks which part of a photo to keep when it has to be cropped to fill the screen: the faces. */
object FaceCrop {

    /** Point to keep in view, as fractions (0..1) of the photo's width and height. */
    fun focus(bmp: Bitmap): PointF {
        val tall = bmp.height > bmp.width
        // no face found: people's heads are usually in the upper part of tall photos
        val fallback = PointF(0.5f, if (tall) 0.3f else 0.45f)
        return try {
            val w = (minOf(480, bmp.width) / 2) * 2 // detector needs an even width
            val h = (bmp.height * w.toFloat() / bmp.width).toInt()
            if (w < 32 || h < 32) return fallback
            val scaled = Bitmap.createScaledBitmap(bmp, w, h, true)
            val small = scaled.copy(Bitmap.Config.RGB_565, false)
            if (scaled !== bmp) scaled.recycle()
            val faces = arrayOfNulls<FaceDetector.Face>(6)
            val n = FaceDetector(w, h, faces.size).findFaces(small, faces)
            small.recycle()
            var sx = 0f
            var sy = 0f
            var cnt = 0
            val p = PointF()
            for (i in 0 until n) {
                val f = faces[i] ?: continue
                if (f.confidence() < 0.3f) continue
                f.getMidPoint(p)
                sx += p.x
                sy += p.y + f.eyesDistance() * 0.5f // middle of the face, a bit below the eyes
                cnt++
            }
            if (cnt == 0) fallback else PointF(sx / cnt / w, sy / cnt / h)
        } catch (e: Throwable) {
            fallback
        }
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
}
