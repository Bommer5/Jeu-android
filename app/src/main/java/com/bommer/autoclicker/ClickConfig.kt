package com.bommer.autoclicker

import android.content.Context
import android.content.SharedPreferences

/** ZONE = cadre rectangulaire, FREEFORM = contour tracé au doigt, POINT = cible unique. */
enum class ClickMode { ZONE, FREEFORM, POINT }

/** Répartition des clics dans le cadre. */
enum class ZonePattern { RANDOM, GRID }

/**
 * Réglages du clicker. Les positions sont en pixels écran ; -1 = position par défaut (centrée).
 */
data class ClickConfig(
    val mode: ClickMode = ClickMode.ZONE,
    val pattern: ZonePattern = ZonePattern.RANDOM,
    val intervalMs: Int = 100,
    val humanize: Boolean = false,
    val tapsPerCycle: Int = 1,
    val gridStepDp: Int = 48,
    val tapDurationMs: Int = 20,
    val stopAfterClicks: Int = 0,
    val stopAfterSeconds: Int = 0,
    val zoneX: Int = -1,
    val zoneY: Int = -1,
    val zoneW: Int = -1,
    val zoneH: Int = -1,
    val pointX: Int = -1,
    val pointY: Int = -1,
    /** Contour libre sérialisé (voir [Polygon.serialize]). */
    val shape: String = "",
    /** Arrêt d'urgence : un appui sur un bouton de volume arrête les clics. */
    val stopOnVolume: Boolean = true,
    /** Arrêt d'urgence : secouer le téléphone arrête les clics. */
    val stopOnShake: Boolean = true,
)

/** Persistance partagée entre l'écran de réglages et le service. */
object ConfigStore {
    const val PREFS = "clicker"

    fun prefs(ctx: Context): SharedPreferences = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(ctx: Context): ClickConfig {
        val p = prefs(ctx)
        val d = ClickConfig()
        return ClickConfig(
            mode = runCatching { ClickMode.valueOf(p.getString("mode", d.mode.name)!!) }.getOrDefault(d.mode),
            pattern = runCatching { ZonePattern.valueOf(p.getString("pattern", d.pattern.name)!!) }.getOrDefault(d.pattern),
            intervalMs = p.getInt("intervalMs", d.intervalMs),
            humanize = p.getBoolean("humanize", d.humanize),
            tapsPerCycle = p.getInt("tapsPerCycle", d.tapsPerCycle),
            gridStepDp = p.getInt("gridStepDp", d.gridStepDp),
            tapDurationMs = p.getInt("tapDurationMs", d.tapDurationMs),
            stopAfterClicks = p.getInt("stopAfterClicks", d.stopAfterClicks),
            stopAfterSeconds = p.getInt("stopAfterSeconds", d.stopAfterSeconds),
            zoneX = p.getInt("zoneX", d.zoneX),
            zoneY = p.getInt("zoneY", d.zoneY),
            zoneW = p.getInt("zoneW", d.zoneW),
            zoneH = p.getInt("zoneH", d.zoneH),
            pointX = p.getInt("pointX", d.pointX),
            pointY = p.getInt("pointY", d.pointY),
            shape = p.getString("shape", d.shape) ?: "",
            stopOnVolume = p.getBoolean("stopOnVolume", d.stopOnVolume),
            stopOnShake = p.getBoolean("stopOnShake", d.stopOnShake),
        )
    }

    fun save(ctx: Context, c: ClickConfig) {
        prefs(ctx).edit()
            .putString("mode", c.mode.name)
            .putString("pattern", c.pattern.name)
            .putInt("intervalMs", c.intervalMs)
            .putBoolean("humanize", c.humanize)
            .putInt("tapsPerCycle", c.tapsPerCycle)
            .putInt("gridStepDp", c.gridStepDp)
            .putInt("tapDurationMs", c.tapDurationMs)
            .putInt("stopAfterClicks", c.stopAfterClicks)
            .putInt("stopAfterSeconds", c.stopAfterSeconds)
            .putInt("zoneX", c.zoneX)
            .putInt("zoneY", c.zoneY)
            .putInt("zoneW", c.zoneW)
            .putInt("zoneH", c.zoneH)
            .putInt("pointX", c.pointX)
            .putInt("pointY", c.pointY)
            .putString("shape", c.shape)
            .putBoolean("stopOnVolume", c.stopOnVolume)
            .putBoolean("stopOnShake", c.stopOnShake)
            .apply()
    }

    fun update(ctx: Context, transform: (ClickConfig) -> ClickConfig) = save(ctx, transform(load(ctx)))
}

/** Rectangle effectif du cadre [x, y, largeur, hauteur], borné à l'écran (valeurs par défaut si non défini). */
fun ClickConfig.resolvedZone(screenW: Int, screenH: Int, minPx: Int): IntArray {
    val w = (if (zoneW > 0) zoneW else (screenW * 0.7f).toInt()).coerceIn(minPx, screenW)
    val h = (if (zoneH > 0) zoneH else (screenH * 0.35f).toInt()).coerceIn(minPx, screenH)
    val x = (if (zoneX >= 0) zoneX else (screenW - w) / 2).coerceIn(0, screenW - w)
    val y = (if (zoneY >= 0) zoneY else (screenH - h) / 2).coerceIn(0, screenH - h)
    return intArrayOf(x, y, w, h)
}
