package com.ubuntu.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/** অ্যাপ আইকনের আকার: Original / Squircle / Circle / Rounded / Drop */
object IconShapes {
    const val ORIGINAL = 0
    const val SQUIRCLE = 1
    const val CIRCLE = 2
    const val ROUNDED = 3
    const val DROP = 4
    val NAMES = listOf("Original", "Squircle", "Circle", "Rounded", "Drop")

    fun path(shape: Int, s: Float): Path {
        val p = Path()
        when (shape) {
            CIRCLE -> p.addOval(RectF(0f, 0f, s, s), Path.Direction.CW)
            ROUNDED -> p.addRoundRect(RectF(0f, 0f, s, s), s * 0.24f, s * 0.24f, Path.Direction.CW)
            DROP -> {
                val r = s / 2f
                val sm = s * 0.10f
                p.addRoundRect(
                    RectF(0f, 0f, s, s),
                    floatArrayOf(r, r, r, r, sm, sm, r, r),
                    Path.Direction.CW
                )
            }
            else -> {
                val n = 4.0
                val steps = 120
                for (i in 0..steps) {
                    val t = 2.0 * Math.PI * i / steps
                    val ct = cos(t)
                    val st = sin(t)
                    val x = s / 2f + (s / 2f * sign(ct) * abs(ct).pow(2.0 / n)).toFloat()
                    val y = s / 2f + (s / 2f * sign(st) * abs(st).pow(2.0 / n)).toFloat()
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                p.close()
            }
        }
        return p
    }

    /** আইকনকে বেছে নেওয়া আকারে কেটে নতুন Drawable বানায়। */
    fun apply(ctx: Context, raw: Drawable, shape: Int, size: Int): Drawable {
        if (shape == ORIGINAL) return raw
        return try {
            val layer = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val lc = Canvas(layer)
            if (raw is AdaptiveIconDrawable) {
                val o = (size * 0.25f).toInt()
                raw.background?.let { it.setBounds(-o, -o, size + o, size + o); it.draw(lc) }
                raw.foreground?.let { it.setBounds(-o, -o, size + o, size + o); it.draw(lc) }
            } else {
                lc.drawColor(0xFFF2F2F2.toInt())
                val pad = (size * 0.14f).toInt()
                raw.setBounds(pad, pad, size - pad, size - pad)
                raw.draw(lc)
            }
            val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.shader = BitmapShader(layer, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            Canvas(out).drawPath(path(shape, size.toFloat()), paint)
            BitmapDrawable(ctx.resources, out)
        } catch (e: Exception) {
            raw
        }
    }
}
