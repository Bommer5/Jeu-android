package com.bommer.autoclicker.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.bommer.autoclicker.ClickMode
import com.bommer.autoclicker.R
import kotlin.math.abs
import kotlin.math.max

/** Panneau de contrôle flottant (déplaçable par sa poignée). */
@SuppressLint("ViewConstructor")
class PanelView(
    context: Context,
    private val wm: WindowManager,
    val params: WindowManager.LayoutParams,
    private val onPlay: () -> Unit,
    private val onMode: () -> Unit,
    private val onSettings: () -> Unit,
    private val onClose: () -> Unit,
    private val onMoved: (x: Int, y: Int) -> Unit,
) : LinearLayout(context) {

    private val play: ImageView
    private val mode: ImageView
    private val counter: TextView

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        val pad = context.dp(6f).toInt()
        setPadding(pad, pad, pad, pad)
        background = GradientDrawable().apply {
            cornerRadius = context.dp(26f)
            setColor(0xEE151933.toInt())
            setStroke(context.dp(1.5f).toInt(), 0x40FFFFFF)
        }
        elevation = context.dp(8f)

        addView(icon(R.drawable.ic_drag, 0x00000000, size = 28f, iconPad = 4f).also { grip ->
            grip.alpha = 0.6f
            grip.setOnTouchListener(DragListener())
        })
        play = icon(R.drawable.ic_play, 0xFF2FC56E.toInt()).also { it.setOnClickListener { onPlay() } }
        addView(play)
        counter = TextView(context).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 11f
            gravity = Gravity.CENTER
            text = "0"
            setPadding(0, pad / 2, 0, pad / 2)
        }
        addView(counter)
        mode = icon(R.drawable.ic_frame, 0xFF4C6FFF.toInt()).also { it.setOnClickListener { onMode() } }
        addView(mode)
        addView(icon(R.drawable.ic_tune, 0x33FFFFFF).also { it.setOnClickListener { onSettings() } })
        addView(icon(R.drawable.ic_close, 0x33FFFFFF).also { it.setOnClickListener { onClose() } })
    }

    private fun icon(res: Int, bg: Int, size: Float = 48f, iconPad: Float = 12f) = ImageView(context).apply {
        setImageResource(res)
        val s = context.dp(size).toInt()
        layoutParams = LayoutParams(s, s).apply { setMargins(0, context.dp(3f).toInt(), 0, context.dp(3f).toInt()) }
        val p = context.dp(iconPad).toInt()
        setPadding(p, p, p, p)
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(bg)
        }
        isClickable = true
    }

    fun setRunning(running: Boolean) {
        play.setImageResource(if (running) R.drawable.ic_pause else R.drawable.ic_play)
        (play.background as GradientDrawable).setColor(if (running) 0xFFFF5B5B.toInt() else 0xFF2FC56E.toInt())
        mode.alpha = if (running) 0.4f else 1f
        mode.isEnabled = !running
    }

    fun setMode(m: ClickMode) {
        mode.setImageResource(if (m == ClickMode.ZONE) R.drawable.ic_frame else R.drawable.ic_target)
    }

    fun setCount(n: Long) {
        counter.text = when {
            n >= 1_000_000 -> "%.1fM".format(n / 1_000_000f)
            n >= 10_000 -> "${n / 1000}k"
            else -> "$n"
        }
    }

    private inner class DragListener : OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: android.view.View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = params.x; startY = params.y
                }
                MotionEvent.ACTION_MOVE -> {
                    val (sw, sh) = screenSize(wm)
                    params.x = (startX + (e.rawX - downX).toInt()).coerceIn(0, max(0, sw - width))
                    params.y = (startY + (e.rawY - downY).toInt()).coerceIn(0, max(0, sh - height))
                    wm.updateViewLayout(this@PanelView, params)
                }
                MotionEvent.ACTION_UP -> if (abs(e.rawX - downX) + abs(e.rawY - downY) > 0) onMoved(params.x, params.y)
            }
            return true
        }
    }
}
