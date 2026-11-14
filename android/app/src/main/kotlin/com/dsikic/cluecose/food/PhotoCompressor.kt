//
//  PhotoCompressor.kt
//  Cluecose
//
//  Downscales a meal photo to ~720 px on the longest side and re-encodes it
//  as a JPEG, for (a) compact storage in the app files dir and (b) inlining
//  as a `data:image/jpeg;base64,…` part in the vision-model request.
//
//  Mirrors iOS `FoodLLMClient.downscaleBase64JPEG` (720 px, quality 0.8).
//  Photos are INPUT to a vision model only — Cluecose never generates or
//  synthesizes images.
//

package com.dsikic.cluecose.food

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream

/**
 * Decode → downscale (longest side ≤ [maxSide]) → JPEG ([jpegQuality]) photo
 * helper. Returns the re-encoded JPEG bytes, or null when the input is not a
 * decodable image (the caller simply skips it — the text still goes out).
 */
object PhotoCompressor {

    /** Longest-side target for stored/inlined photos (pixels). */
    const val maxSide = 720

    /** JPEG quality for stored/inlined photos (0..100). */
    const val jpegQuality = 80

    /** Decode + downscale + re-encode as JPEG bytes. Null on failure. */
    fun toJpegBytes(data: ByteArray): ByteArray? {
        val src = BitmapFactory.decodeByteArray(data, 0, data.size) ?: return null
        val w = src.width
        val h = src.height
        if (w <= 0 || h <= 0) {
            src.recycle()
            return null
        }
        val out = ByteArrayOutputStream()
        if (maxOf(w, h) <= maxSide) {
            // Already small enough: just re-encode to a normalized JPEG.
            src.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)
            src.recycle()
        } else {
            val scale = maxSide.toFloat() / maxOf(w, h)
            val nw = (w * scale).toInt().coerceAtLeast(1)
            val nh = (h * scale).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(src, nw, nh, true)
            if (scaled !== src) src.recycle()
            scaled.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)
            scaled.recycle()
        }
        return out.toByteArray()
    }

    /** [toJpegBytes] → base64 (no line wrapping) for a data-URL. */
    fun base64Jpeg(data: ByteArray): String? {
        val bytes = toJpegBytes(data) ?: return null
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
