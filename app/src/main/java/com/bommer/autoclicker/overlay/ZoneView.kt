package com.bommer.autoclicker.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.bommer.autoclicker.Tap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Cadre de clic flottant. À l'arrêt, on le déplace en le faisant glisser et on le redimensionne
 * en tirant un coin. Pendant les clics, il devient « transparent au toucher » pour que les clics
 * atteignent l'application en dessous, et affiche une onde à chaque clic.
 */
@SuppressLint("ViewConstructor")
class ZoneView(
    context: Context,
    private val wm: WindowManager,
    val params: WindowManager.LayoutParams,
    private val onChanged: (x: Int, y: Int, w: Int, h: Int) -> Unit,
) : View(context) {

    private val accent = 0xFF6C8CFF.toInt()
    private val runningAccent = 0xFF5EE6C8.toInt()
    private val stroke = context.dp(2.5f)
    private val handleR = context.dp(9f)
    private val grab = context.dp(44f)
    private val minSize = context.dp(60f).toInt()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        pathEffect = DashPathEffect(floatArrayOf(context.dp(10f), context.dp(6f)), 0f)
    }
    private val corner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dp(5f)
        strokeCap = Paint.Cap.ROUND
    }
    private val handle = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCC0F1222.toInt() }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = context.dp(13f)
        isFakeBoldText = true
    }
    private val hint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xDDFFFFFF.toInt()
        textSize = context.dp(13f)
        textAlign = Paint.Align.CENTER
    }
    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private class Ripple(val x: Float, val y: Float, val t: Long)
    private val ripples = ArrayDeque<Ripple>()

    var running = false
        set(value) {
            field = value
            invalidate()
        }

    // Glisser / redimensionner
    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0
    private var startW = 0
    private var startH = 0
    private var dragLeft = false
    private var dragTop = false
    private var dragRight = false
    private var dragBottom = false

    fun showTaps(taps: List<Tap>) {
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        val now = SystemClock.uptimeMillis()
        taps.forEach { ripples.addLast(Ripple(it.x - loc[0], it.y - loc[1], now)) }
        while (ripples.size > 60) ripples.removeFirst()
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val color = if (running) runningAccent else accent
        val half = stroke / 2f
        val r = RectF(half, half, w - half, h - half)

        fill.color = (color and 0x00FFFFFF) or (if (running) 0x18000000 else 0x30000000)
        canvas.drawRoundRect(r, context.dp(10f), context.dp(10f), fill)
        border.color = color
        canvas.drawRoundRect(r, context.dp(10f), context.dp(10f), border)

        // Coins en équerre
        corner.color = color
        val l = min(context.dp(22f), min(w, h) / 3f)
        val i = corner.strokeWidth / 2f
        canvas.drawLine(i, i, i + l, i, corner); canvas.drawLine(i, i, i, i + l, corner)
        canvas.drawLine(w - i, i, w - i - l, i, corner); canvas.drawLine(w - i, i, w - i, i + l, corner)
        canvas.drawLine(i, h - i, i + l, h - i, corner); canvas.drawLine(i, h - i, i, h - i - l, corner)
        canvas.drawLine(w - i, h - i, w - i - l, h - i, corner); canvas.drawLine(w - i, h - i, w - i, h - i - l, corner)

        // Étiquette avec la taille
        val label = if (running) "Clics en cours · ${width}×${height}" else "Zone de clic · ${width}×${height}"
        val tw = text.measureText(label)
        val pad = context.dp(8f)
        val lh = context.dp(24f)
        canvas.drawRoundRect(RectF(pad, pad, pad + tw + pad * 2, pad + lh), lh / 2, lh / 2, labelBg)
        canvas.drawText(label, pad * 2, pad + lh / 2 + text.textSize / 3f, text)

        if (!running) {
            handle.color = color
            listOf(0f to 0f, w to 0f, 0f to h, w to h).forEach { (cx, cy) ->
                canvas.drawCircle(cx.coerceIn(handleR, w - handleR), cy.coerceIn(handleR, h - handleR), handleR, handle)
            }
            if (h > context.dp(90f)) {
                canvas.drawText("Glisse pour déplacer", w / 2, h / 2, hint)
                canvas.drawText("Tire un coin pour redimensionner", w / 2, h / 2 + hint.textSize * 1.4f, hint)
            }
        }

        // Ondes des clics
        val now = SystemClock.uptimeMillis()
        while (ripples.isNotEmpty() && now - ripples.first().t > RIPPLE_MS) ripples.removeFirst()
        for (rp in ripples) {
            val p = (now - rp.t) / RIPPLE_MS.toFloat()
            ripplePaint.color = runningAccent
            ripplePaint.alpha = ((1f - p) * 255).toInt()
            ripplePaint.strokeWidth = context.dp(2.5f)
            canvas.drawCircle(rp.x, rp.y, context.dp(6f) + context.dp(18f) * p, ripplePaint)
            dotPaint.color = 0xFFFFFFFF.toInt()
            dotPaint.alpha = ((1f - p) * 255).toInt()
            canvas.drawCircle(rp.x, rp.y, context.dp(3.5f), dotPaint)
        }
        if (ripples.isNotEmpty()) postInvalidateOnAnimation()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (running) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX
                downY = e.rawY
                startX = params.x
                startY = params.y
                startW = params.width
                startH = params.height
                // Coin le plus proche (sinon : déplacement)
                val nearLeft = e.x < grab
                val nearRight = e.x > width - grab
                val nearTop = e.y < grab
                val nearBottom = e.y > height - grab
                val onCorner = (nearLeft || nearRight) && (nearTop || nearBottom)
                dragLeft = onCorner && nearLeft
                dragRight = onCorner && nearRight && !nearLeft
                dragTop = onCorner && nearTop
                dragBottom = onCorner && nearBottom && !nearTop
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (e.rawX - downX).toInt()
                val dy = (e.rawY - downY).toInt()
                val (sw, sh) = screenSize(wm)
                if (!dragLeft && !dragRight && !dragTop && !dragBottom) {
                    params.x = (startX + dx).coerceIn(0, max(0, sw - params.width))
                    params.y = (startY + dy).coerceIn(0, max(0, sh - params.height))
                } else {
                    if (dragRight) params.width = (startW + dx).coerceIn(minSize, sw - startX)
                    if (dragBottom) params.height = (startH + dy).coerceIn(minSize, sh - startY)
                    if (dragLeft) {
                        val nx = (startX + dx).coerceIn(0, startX + startW - minSize)
                        params.width = startW + (startX - nx)
                        params.x = nx
                    }
                    if (dragTop) {
                        val ny = (startY + dy).coerceIn(0, startY + startH - minSize)
                        params.height = startH + (startY - ny)
                        params.y = ny
                    }
                }
                wm.updateViewLayout(this, params)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (abs(e.rawX - downX) > 0 || abs(e.rawY - downY) > 0) {
                    onChanged(params.x, params.y, params.width, params.height)
                }
                return true
            }
        }
        return false
    }

    companion object {
        private const val RIPPLE_MS = 450L
    }
}
