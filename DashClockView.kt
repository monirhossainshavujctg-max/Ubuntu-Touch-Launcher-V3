package com.ubuntu.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.format.DateFormat
import android.util.AttributeSet
import android.view.View
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** হোম স্ক্রিনের কমলা রিং ঘড়ি (সময় + তারিখ) এবং নিচে অ্যানালগ ঘড়ি। */
class DashClockView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : View(ctx, attrs) {

    private val d = resources.displayMetrics.density
    private val rect = RectF()

    private val ringP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = 0xFFE06000.toInt()
    }
    private val gapP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.WHITE; strokeCap = Paint.Cap.ROUND
    }
    private val timeP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
    }
    private val dateP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCCFFFFFF.toInt(); textAlign = Paint.Align.CENTER
    }
    private val tickP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCCFFFFFF.toInt(); strokeCap = Paint.Cap.ROUND
    }
    private val handP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; strokeCap = Paint.Cap.ROUND; strokeWidth = 3.5f * d
    }
    private val secP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE53935.toInt(); strokeCap = Paint.Cap.ROUND; strokeWidth = 1.6f * d
    }
    private val dotP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE53935.toInt() }

    private val ticker = object : Runnable {
        override fun run() {
            invalidate()
            postDelayed(this, 1000)
        }
    }

    private fun diameter(w: Int): Float = min(w * 0.66f, 270f * d)

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val w = MeasureSpec.getSize(widthSpec)
        val dia = diameter(w)
        val h = (16 * d + dia + 36 * d + 2f * dia * 0.36f + 16 * d).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onDraw(c: Canvas) {
        val dia = diameter(width)
        val cx = width / 2f
        val cy = 16 * d + dia / 2f
        val cal = Calendar.getInstance()
        val sec = cal.get(Calendar.SECOND)

        // রিং: কমলা, সেকেন্ডের সাথে ঘোরা ছোট সাদা অংশ
        val sw = dia * 0.035f
        ringP.strokeWidth = sw
        gapP.strokeWidth = sw
        rect.set(cx - dia / 2 + sw / 2, cy - dia / 2 + sw / 2, cx + dia / 2 - sw / 2, cy + dia / 2 - sw / 2)
        c.drawOval(rect, ringP)
        c.drawArc(rect, sec * 6f - 90f - 6f, 12f, false, gapP)

        // সময় ও তারিখ
        val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
        timeP.textSize = dia * 0.27f
        val fm = timeP.fontMetrics
        c.drawText(
            DateFormat.format(pattern, cal).toString(),
            cx, cy - (fm.ascent + fm.descent) / 2f - dia * 0.03f, timeP
        )
        dateP.textSize = dia * 0.058f
        c.drawText(
            DateFormat.format("EEE, d MMMM yyyy", cal).toString().uppercase(),
            cx, cy + dia * 0.2f, dateP
        )

        // অ্যানালগ ঘড়ি
        val r = dia * 0.36f
        val ay = cy + dia / 2f + 36 * d + r
        for (i in 0 until 12) {
            val a = Math.toRadians(i * 30.0 - 90.0)
            val major = i % 3 == 0
            val len = if (major) r * 0.17f else r * 0.10f
            tickP.strokeWidth = if (major) 3.2f * d else 2f * d
            val r2 = r - len
            c.drawLine(
                cx + (cos(a) * r2).toFloat(), ay + (sin(a) * r2).toFloat(),
                cx + (cos(a) * r).toFloat(), ay + (sin(a) * r).toFloat(), tickP
            )
        }
        val minutes = cal.get(Calendar.MINUTE) + sec / 60f
        val hours = (cal.get(Calendar.HOUR) % 12) + minutes / 60f
        hand(c, cx, ay, hours * 30f, r * 0.50f, handP)
        hand(c, cx, ay, minutes * 6f, r * 0.74f, handP)
        hand(c, cx, ay, sec * 6f, r * 0.82f, secP)
        c.drawCircle(cx, ay, 5f * d, dotP)
    }

    private fun hand(c: Canvas, cx: Float, cy: Float, deg: Float, len: Float, p: Paint) {
        val a = Math.toRadians(deg - 90.0)
        c.drawLine(cx, cy, cx + (cos(a) * len).toFloat(), cy + (sin(a) * len).toFloat(), p)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        start()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) start() else stop()
    }

    private fun start() {
        removeCallbacks(ticker)
        post(ticker)
    }

    private fun stop() {
        removeCallbacks(ticker)
    }
}
