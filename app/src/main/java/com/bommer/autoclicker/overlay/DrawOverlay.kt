package com.bommer.autoclicker.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.bommer.autoclicker.Polygon
import com.bommer.autoclicker.Tap

/** Remplit un tracé fermé + bordure en pointillés. */
fun Canvas.drawShape(points: List<Tap>, offsetX: Float, offsetY: Float, color: Int, fillAlpha: Int, ctx: Context) {
    if (points.size < 2) return
    val path = Path()
    path.moveTo(points[0].x - offsetX, points[0].y - offsetY)
    for (i in 1 until points.size) path.lineTo(points[i].x - offsetX, points[i].y - offsetY)
    path.close()
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = (color and 0x00FFFFFF) or (fillAlpha shl 24) }
    drawPath(path, fill)
    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = ctx.dp(3f)
        strokeJoin = Paint.Join.ROUND
        pathEffect = DashPathEffect(floatArrayOf(ctx.dp(10f), ctx.dp(6f)), 0f)
    }
    drawPath(path, border)
}

/**
 * Écran plein de dessin : le joueur trace au doigt le contour de la zone à cliquer.
 * Le tracé se ferme automatiquement au lever du doigt ; on peut recommencer autant de fois que voulu.
 */
@SuppressLint("ViewConstructor")
class DrawOverlay(
    context: Context,
    initial: List<Tap>,
    private val onDone: (List<Tap>?) -> Unit,
) : FrameLayout(context) {

    private val raw = ArrayList<Tap>(initial)
    private var drawing = false
    private val accent = 0xFF6C8CFF.toInt()

    private val canvasView = object : View(context) {
        private val live = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            style = Paint.Style.STROKE
            strokeWidth = context.dp(4f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(0x66000000)
            val loc = IntArray(2)
            getLocationOnScreen(loc)
            if (drawing) {
                val path = Path()
                raw.forEachIndexed { i, p ->
                    if (i == 0) path.moveTo(p.x - loc[0], p.y - loc[1]) else path.lineTo(p.x - loc[0], p.y - loc[1])
                }
                canvas.drawPath(path, live)
            } else {
                canvas.drawShape(raw, loc[0].toFloat(), loc[1].toFloat(), accent, 0x40, context)
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    raw.clear()
                    drawing = true
                    raw += Tap(e.rawX, e.rawY)
                }
                MotionEvent.ACTION_MOVE -> {
                    for (h in 0 until e.historySize) {
                        // Coordonnées écran : position de la vue + coordonnées locales historiques
                        raw += Tap(e.getHistoricalX(h) + (e.rawX - e.x), e.getHistoricalY(h) + (e.rawY - e.y))
                    }
                    raw += Tap(e.rawX, e.rawY)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    drawing = false
                    val simplified = Polygon.simplify(raw, context.dp(5f))
                    raw.clear()
                    raw += simplified
                    updateValidate()
                }
            }
            invalidate()
            return true
        }
    }

    private val validate: TextView

    init {
        addView(canvasView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        val pad = context.dp(14f).toInt()
        val banner = TextView(context).apply {
            text = "Trace au doigt le contour de la zone à cliquer"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
            background = pill(0xEE151933.toInt())
        }
        addView(banner, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = context.dp(56f).toInt()
        })

        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        bar.addView(button("Annuler", 0xFF3A3F66.toInt()) { onDone(null) })
        bar.addView(button("Effacer", 0xFF3A3F66.toInt()) {
            raw.clear()
            updateValidate()
            canvasView.invalidate()
        })
        validate = button("Valider", 0xFF2FC56E.toInt()) {
            if (Polygon(raw).isValid) onDone(ArrayList(raw))
        }
        bar.addView(validate)
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
            bottomMargin = context.dp(56f).toInt()
        })
        updateValidate()
    }

    private fun updateValidate() {
        val ok = Polygon(raw).isValid
        validate.alpha = if (ok) 1f else 0.4f
    }

    private fun pill(color: Int) = GradientDrawable().apply {
        cornerRadius = context.dp(24f)
        setColor(color)
    }

    private fun button(label: String, color: Int, onClick: () -> Unit) = TextView(context).apply {
        text = label
        setTextColor(0xFFFFFFFF.toInt())
        textSize = 16f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        gravity = Gravity.CENTER
        val h = context.dp(14f).toInt()
        setPadding(h * 2, h, h * 2, h)
        background = pill(color)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            val m = context.dp(6f).toInt()
            setMargins(m, 0, m, 0)
        }
        setOnClickListener { onClick() }
    }
}
