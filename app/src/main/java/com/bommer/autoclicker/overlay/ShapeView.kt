package com.bommer.autoclicker.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.view.View
import com.bommer.autoclicker.Polygon
import com.bommer.autoclicker.Tap

/** Affiche le contour libre (plein écran, jamais touchable) et une onde à chaque clic. */
@SuppressLint("ViewConstructor")
class ShapeView(context: Context) : View(context) {

    var polygon: Polygon? = null
        set(value) {
            field = value
            invalidate()
        }

    var running = false
        set(value) {
            field = value
            invalidate()
        }

    private class Ripple(val x: Float, val y: Float, val t: Long)
    private val ripples = ArrayDeque<Ripple>()
    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dp(2.5f)
        color = 0xFF5EE6C8.toInt()
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = context.dp(13f)
        isFakeBoldText = true
        setShadowLayer(context.dp(3f), 0f, 0f, 0xFF000000.toInt())
    }

    fun showTaps(taps: List<Tap>) {
        val now = SystemClock.uptimeMillis()
        taps.forEach { ripples.addLast(Ripple(it.x, it.y, now)) }
        while (ripples.size > 60) ripples.removeFirst()
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        val ox = loc[0].toFloat()
        val oy = loc[1].toFloat()
        polygon?.let { p ->
            val color = if (running) 0xFF5EE6C8.toInt() else 0xFF6C8CFF.toInt()
            canvas.drawShape(p.points, ox, oy, color, if (running) 0x18 else 0x30, context)
            val text = if (running) "Clics en cours" else "Contour libre · ✎ pour redessiner"
            canvas.drawText(text, p.bounds.left - ox, p.bounds.top - oy - context.dp(8f), label)
        }
        val now = SystemClock.uptimeMillis()
        while (ripples.isNotEmpty() && now - ripples.first().t > 450) ripples.removeFirst()
        for (r in ripples) {
            val f = (now - r.t) / 450f
            ripplePaint.alpha = ((1f - f) * 255).toInt()
            canvas.drawCircle(r.x - ox, r.y - oy, context.dp(6f) + context.dp(18f) * f, ripplePaint)
            dot.alpha = ((1f - f) * 255).toInt()
            canvas.drawCircle(r.x - ox, r.y - oy, context.dp(3.5f), dot)
        }
        if (ripples.isNotEmpty()) postInvalidateOnAnimation()
    }
}
