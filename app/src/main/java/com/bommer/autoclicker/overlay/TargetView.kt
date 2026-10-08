package com.bommer.autoclicker.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.max

/** Cible déplaçable pour le mode « point unique ». */
@SuppressLint("ViewConstructor")
class TargetView(
    context: Context,
    private val wm: WindowManager,
    val params: WindowManager.LayoutParams,
    private val onChanged: (x: Int, y: Int) -> Unit,
) : View(context) {

    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dp(3f)
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private var pulseAt = 0L
    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0

    var running = false
        set(value) {
            field = value
            invalidate()
        }

    fun pulse() {
        pulseAt = SystemClock.uptimeMillis()
        postInvalidateOnAnimation()
    }

    /** Centre de la cible en coordonnées écran. */
    fun centerOnScreen(): Pair<Float, Float> {
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        return loc[0] + width / 2f to loc[1] + height / 2f
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = width / 2f - ring.strokeWidth * 2
        val color = if (running) 0xFF5EE6C8.toInt() else 0xFF6C8CFF.toInt()
        fill.color = (color and 0x00FFFFFF) or 0x40000000
        canvas.drawCircle(cx, cy, r, fill)
        ring.color = color
        canvas.drawCircle(cx, cy, r, ring)
        canvas.drawLine(cx - r * 0.5f, cy, cx + r * 0.5f, cy, ring)
        canvas.drawLine(cx, cy - r * 0.5f, cx, cy + r * 0.5f, ring)
        val p = (SystemClock.uptimeMillis() - pulseAt) / 350f
        if (p < 1f) {
            ring.alpha = ((1f - p) * 255).toInt()
            canvas.drawCircle(cx, cy, r * (0.3f + 0.7f * p), ring)
            ring.alpha = 255
            postInvalidateOnAnimation()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (running) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX; downY = e.rawY; startX = params.x; startY = params.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val (sw, sh) = screenSize(wm)
                params.x = (startX + (e.rawX - downX).toInt()).coerceIn(0, max(0, sw - params.width))
                params.y = (startY + (e.rawY - downY).toInt()).coerceIn(0, max(0, sh - params.height))
                wm.updateViewLayout(this, params)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                onChanged(params.x, params.y)
                return true
            }
        }
        return false
    }
}
