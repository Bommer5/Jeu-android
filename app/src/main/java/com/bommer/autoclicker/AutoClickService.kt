package com.bommer.autoclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.bommer.autoclicker.overlay.PanelView
import com.bommer.autoclicker.overlay.TargetView
import com.bommer.autoclicker.overlay.ZoneView
import com.bommer.autoclicker.overlay.baseFlags
import com.bommer.autoclicker.overlay.dp
import com.bommer.autoclicker.overlay.overlayParams
import com.bommer.autoclicker.overlay.screenSize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.max
import kotlin.random.Random

/**
 * Service d'accessibilité qui affiche le panneau flottant, le cadre (ou la cible) et
 * envoie les clics avec [dispatchGesture].
 */
class AutoClickService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var wm: WindowManager
    private lateinit var config: ClickConfig
    private val handler = Handler(Looper.getMainLooper())
    private val planner = ClickPlanner()
    private val random = Random(SystemClock.uptimeMillis())

    private var panel: PanelView? = null
    private var zone: ZoneView? = null
    private var target: TargetView? = null

    private var clicks = 0L
    private var startedAt = 0L

    private val tick = object : Runnable {
        override fun run() {
            if (!_running.value) return
            performCycle()
            if (_running.value) handler.postDelayed(this, nextDelay())
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        config = ConfigStore.load(this)
        ConfigStore.prefs(this).registerOnSharedPreferenceChangeListener(this)
        instance = this
        _enabled.value = true
        showOverlays()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = stop()

    override fun onUnbind(intent: Intent?): Boolean {
        cleanup()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun cleanup() {
        if (instance !== this) return
        stop()
        removeOverlays()
        ConfigStore.prefs(this).unregisterOnSharedPreferenceChangeListener(this)
        instance = null
        _enabled.value = false
    }

    // --- Fenêtres flottantes ---------------------------------------------------------------------

    fun showOverlays() {
        if (panel == null) {
            val (sw, sh) = screenSize(wm)
            val params = overlayParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                ConfigStore.prefs(this).getInt("panelX", sw - dp(76f).toInt()),
                ConfigStore.prefs(this).getInt("panelY", sh / 4),
            )
            panel = PanelView(
                this, wm, params,
                onPlay = { if (_running.value) stop() else start() },
                onMode = {
                    ConfigStore.update(this) {
                        it.copy(mode = if (it.mode == ClickMode.ZONE) ClickMode.POINT else ClickMode.ZONE)
                    }
                },
                onSettings = {
                    stop()
                    startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
                onClose = { hideOverlays() },
                onMoved = { x, y -> ConfigStore.prefs(this).edit().putInt("panelX", x).putInt("panelY", y).apply() },
            ).also { wm.addView(it, params) }
        }
        applyMode()
        _visible.value = true
    }

    fun hideOverlays() {
        stop()
        removeOverlays()
        _visible.value = false
    }

    private fun removeOverlays() {
        listOfNotNull(panel, zone, target).forEach { runCatching { wm.removeView(it) } }
        panel = null
        zone = null
        target = null
    }

    private fun applyMode() {
        panel?.setMode(config.mode)
        if (config.mode == ClickMode.ZONE) {
            target?.let { runCatching { wm.removeView(it) } }
            target = null
            ensureZone()
        } else {
            zone?.let { runCatching { wm.removeView(it) } }
            zone = null
            ensureTarget()
        }
        // Le panneau reste au-dessus du cadre.
        panel?.let { p ->
            runCatching { wm.removeView(p); wm.addView(p, p.params) }
        }
    }

    private fun zoneRect(c: ClickConfig): IntArray {
        val (sw, sh) = screenSize(wm)
        return c.resolvedZone(sw, sh, dp(60f).toInt())
    }

    private fun ensureZone() {
        val (x, y, w, h) = zoneRect(config).let { listOf(it[0], it[1], it[2], it[3]) }
        val existing = zone
        if (existing != null) {
            val p = existing.params
            if (p.x != x || p.y != y || p.width != w || p.height != h) {
                p.x = x; p.y = y; p.width = w; p.height = h
                wm.updateViewLayout(existing, p)
            }
            return
        }
        val params = overlayParams(w, h, x, y, touchable = !_running.value)
        zone = ZoneView(this, wm, params) { nx, ny, nw, nh ->
            ConfigStore.update(this) { it.copy(zoneX = nx, zoneY = ny, zoneW = nw, zoneH = nh) }
        }.also { wm.addView(it, params) }
    }

    private fun ensureTarget() {
        val (sw, sh) = screenSize(wm)
        val size = dp(64f).toInt()
        val x = (if (config.pointX >= 0) config.pointX - size / 2 else (sw - size) / 2).coerceIn(0, sw - size)
        val y = (if (config.pointY >= 0) config.pointY - size / 2 else (sh - size) / 2).coerceIn(0, sh - size)
        val existing = target
        if (existing != null) {
            val p = existing.params
            if (p.x != x || p.y != y) {
                p.x = x; p.y = y
                wm.updateViewLayout(existing, p)
            }
            return
        }
        val params = overlayParams(size, size, x, y, touchable = !_running.value)
        target = TargetView(this, wm, params) { nx, ny ->
            ConfigStore.update(this) { it.copy(pointX = nx + size / 2, pointY = ny + size / 2) }
        }.also { wm.addView(it, params) }
    }

    /** Pendant les clics, le cadre et la cible laissent passer le toucher. */
    private fun setOverlaysTouchable(touchable: Boolean) {
        zone?.let { z ->
            z.running = !touchable
            z.params.flags = baseFlags(touchable)
            wm.updateViewLayout(z, z.params)
        }
        target?.let { t ->
            t.running = !touchable
            t.params.flags = baseFlags(touchable)
            wm.updateViewLayout(t, t.params)
        }
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        if (key == "panelX" || key == "panelY") return
        val old = config
        config = ConfigStore.load(this)
        if (panel == null) return
        if (old.mode != config.mode) {
            stop()
            applyMode()
        } else if (config.mode == ClickMode.ZONE) {
            ensureZone()
        } else {
            ensureTarget()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Rotation : on ramène le cadre et la cible dans l'écran.
        if (panel != null) handler.postDelayed({ if (config.mode == ClickMode.ZONE) ensureZone() else ensureTarget() }, 300)
    }

    // --- Clics -----------------------------------------------------------------------------------

    fun start() {
        if (_running.value || panel == null) return
        _running.value = true
        clicks = 0
        startedAt = SystemClock.elapsedRealtime()
        planner.reset()
        panel?.setRunning(true)
        panel?.setCount(0)
        setOverlaysTouchable(false)
        // Petit délai pour laisser les fenêtres devenir « traversables ».
        handler.postDelayed(tick, 150)
    }

    fun stop() {
        if (!_running.value) return
        _running.value = false
        handler.removeCallbacks(tick)
        panel?.setRunning(false)
        setOverlaysTouchable(true)
    }

    private fun nextDelay(): Long {
        var d = max(config.intervalMs, config.tapDurationMs + 10).toLong()
        if (config.humanize) d = (d * (0.75 + random.nextDouble() * 0.5)).toLong()
        return max(d, 1L)
    }

    private fun screenArea(v: android.view.View): Area {
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        return Area(loc[0].toFloat(), loc[1].toFloat(), (loc[0] + v.width).toFloat(), (loc[1] + v.height).toFloat())
    }

    private fun performCycle() {
        val taps: List<Tap> = when (config.mode) {
            ClickMode.POINT -> target?.centerOnScreen()?.let { listOf(Tap(it.first, it.second)) } ?: emptyList()
            ClickMode.ZONE -> {
                val z = zone ?: return
                val exclude = listOfNotNull(panel?.let { screenArea(it) })
                planner.next(screenArea(z), config.pattern, config.tapsPerCycle, dp(config.gridStepDp.toFloat()), exclude)
            }
        }
        if (taps.isEmpty()) return

        val builder = GestureDescription.Builder()
        val duration = config.tapDurationMs.toLong().coerceAtLeast(1)
        taps.take(GestureDescription.getMaxStrokeCount()).forEach { t ->
            val path = Path().apply { moveTo(t.x, t.y) }
            builder.addStroke(GestureDescription.StrokeDescription(path, 0, duration))
        }
        dispatchGesture(builder.build(), null, null)

        clicks += taps.size
        panel?.setCount(clicks)
        zone?.showTaps(taps)
        target?.pulse()

        val elapsed = (SystemClock.elapsedRealtime() - startedAt) / 1000
        val reason = when {
            config.stopAfterClicks > 0 && clicks >= config.stopAfterClicks -> "$clicks clics effectués"
            config.stopAfterSeconds > 0 && elapsed >= config.stopAfterSeconds -> "Durée atteinte ($elapsed s)"
            else -> null
        }
        if (reason != null) {
            stop()
            Toast.makeText(this, "Auto Clicker arrêté : $reason", Toast.LENGTH_SHORT).show()
        }
    }


    companion object {
        var instance: AutoClickService? = null
            private set

        private val _enabled = MutableStateFlow(false)
        /** Le service d'accessibilité est actif. */
        val enabled: StateFlow<Boolean> = _enabled

        private val _visible = MutableStateFlow(false)
        val overlaysVisible: StateFlow<Boolean> = _visible

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running
    }
}
