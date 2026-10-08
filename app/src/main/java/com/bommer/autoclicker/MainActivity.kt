package com.bommer.autoclicker

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.bommer.autoclicker.overlay.dp
import com.bommer.autoclicker.overlay.screenSize
import kotlin.math.roundToInt

private val Accent = Color(0xFF6C8CFF)
private val Mint = Color(0xFF5EE6C8)
private val Card = Color(0xFF181C36)
private val Dim = Color(0xFFA9AFD6)

/** Valeurs proposées par les curseurs. */
private val INTERVALS = listOf(1, 5, 10, 20, 33, 50, 75, 100, 150, 200, 300, 500, 750, 1000, 1500, 2000, 3000, 5000, 10000)
private val STOP_CLICKS = listOf(0, 10, 25, 50, 100, 250, 500, 1000, 2500, 5000, 10000, 50000)
private val STOP_SECONDS = listOf(0, 10, 30, 60, 120, 300, 600, 1800, 3600, 7200)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, secondary = Mint, background = Color(0xFF0F1222))) {
                SettingsScreen()
            }
        }
    }
}

@Composable
private fun SettingsScreen() {
    val ctx = LocalContext.current
    var cfg by remember { mutableStateOf(ConfigStore.load(ctx)) }
    LifecycleResumeEffect(Unit) {
        cfg = ConfigStore.load(ctx)
        onPauseOrDispose { }
    }
    fun update(transform: (ClickConfig) -> ClickConfig) {
        cfg = transform(cfg)
        ConfigStore.save(ctx, cfg)
    }

    val enabled by AutoClickService.enabled.collectAsState()
    val visible by AutoClickService.overlaysVisible.collectAsState()
    val running by AutoClickService.running.collectAsState()
    var disclosure by remember { mutableStateOf(false) }

    val wm = remember { ctx.getSystemService(android.view.WindowManager::class.java) }
    val (sw, sh) = remember { screenSize(wm) }
    val minPx = remember { ctx.dp(60f).toInt() }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF151A3D), Color(0xFF0F1222))))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Auto Clicker", fontSize = 32.sp, fontWeight = FontWeight.Black, color = Color.White)
        Text("Clics automatiques dans un cadre ou sur un point", fontSize = 15.sp, color = Dim)
        Spacer(Modifier.height(16.dp))

        // --- État du service -------------------------------------------------------------------
        Section {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(if (enabled) Mint else Color(0xFFFF5B5B))
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    when {
                        !enabled -> "Service désactivé"
                        running -> "Clics en cours…"
                        else -> "Service actif"
                    },
                    fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White,
                )
            }
            Spacer(Modifier.height(8.dp))
            if (!enabled) {
                Text(
                    "Pour cliquer à ta place dans les autres applis, Auto Clicker a besoin du service d'accessibilité Android.",
                    color = Dim, fontSize = 14.sp,
                )
                Spacer(Modifier.height(12.dp))
                Button({ disclosure = true }, Modifier.fillMaxWidth()) { Text("Activer le service", fontWeight = FontWeight.Bold) }
            } else {
                Text(
                    "Utilise le panneau flottant : ▶ démarre / arrête, le bouton bleu change de mode.",
                    color = Dim, fontSize = 14.sp,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    {
                        val s = AutoClickService.instance
                        if (visible) s?.hideOverlays() else s?.showOverlays()
                        if (!visible) moveTaskToBack(ctx)
                    },
                    Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = if (visible) Color(0xFF3A3F66) else Accent),
                ) { Text(if (visible) "Masquer le panneau" else "Afficher le panneau", fontWeight = FontWeight.Bold) }
            }
        }

        // --- Mode ----------------------------------------------------------------------------------
        Title("Mode")
        Choice(
            listOf("Cadre (zone)" to (cfg.mode == ClickMode.ZONE), "Point unique" to (cfg.mode == ClickMode.POINT)),
        ) { i -> update { it.copy(mode = if (i == 0) ClickMode.ZONE else ClickMode.POINT) } }

        if (cfg.mode == ClickMode.ZONE) {
            val zone = cfg.resolvedZone(sw, sh, minPx)
            Title("Cadre de clic")
            Section {
                Text(
                    "Les clics tombent partout à l'intérieur du cadre. Tu peux aussi le déplacer et tirer ses coins directement à l'écran.",
                    color = Dim, fontSize = 14.sp,
                )
                Spacer(Modifier.height(10.dp))
                LabeledSlider("Largeur", "${zone[2]} px · ${(zone[2] * 100f / sw).roundToInt()} %",
                    zone[2].toFloat(), minPx.toFloat()..sw.toFloat()) { v ->
                    val w = v.roundToInt()
                    val cx = zone[0] + zone[2] / 2
                    update { it.copy(zoneW = w, zoneX = (cx - w / 2).coerceIn(0, sw - w), zoneY = zone[1], zoneH = zone[3]) }
                }
                LabeledSlider("Hauteur", "${zone[3]} px · ${(zone[3] * 100f / sh).roundToInt()} %",
                    zone[3].toFloat(), minPx.toFloat()..sh.toFloat()) { v ->
                    val h = v.roundToInt()
                    val cy = zone[1] + zone[3] / 2
                    update { it.copy(zoneH = h, zoneY = (cy - h / 2).coerceIn(0, sh - h), zoneX = zone[0], zoneW = zone[2]) }
                }
                Text("Tailles rapides", color = Dim, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Plein écran", Modifier.weight(1f)) { update { it.copy(zoneX = 0, zoneY = 0, zoneW = sw, zoneH = sh) } }
                    Chip("Haut", Modifier.weight(1f)) { update { it.copy(zoneX = 0, zoneY = 0, zoneW = sw, zoneH = sh / 2) } }
                    Chip("Bas", Modifier.weight(1f)) { update { it.copy(zoneX = 0, zoneY = sh / 2, zoneW = sw, zoneH = sh / 2) } }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Petit carré", Modifier.weight(1f)) {
                        val s = sw / 3
                        update { it.copy(zoneX = (sw - s) / 2, zoneY = (sh - s) / 2, zoneW = s, zoneH = s) }
                    }
                    Chip("Centrer", Modifier.weight(1f)) {
                        update { it.copy(zoneX = (sw - zone[2]) / 2, zoneY = (sh - zone[3]) / 2, zoneW = zone[2], zoneH = zone[3]) }
                    }
                    Chip("Par défaut", Modifier.weight(1f)) { update { it.copy(zoneX = -1, zoneY = -1, zoneW = -1, zoneH = -1) } }
                }
            }

            Title("Répartition des clics")
            Choice(
                listOf("Aléatoire" to (cfg.pattern == ZonePattern.RANDOM), "Balayage (grille)" to (cfg.pattern == ZonePattern.GRID)),
            ) { i -> update { it.copy(pattern = if (i == 0) ZonePattern.RANDOM else ZonePattern.GRID) } }
            Spacer(Modifier.height(8.dp))
            Section {
                Text(
                    if (cfg.pattern == ZonePattern.RANDOM) "Chaque clic tombe à un endroit au hasard dans le cadre."
                    else "Le cadre est parcouru ligne par ligne : chaque case de la grille est cliquée, puis on recommence.",
                    color = Dim, fontSize = 14.sp,
                )
                if (cfg.pattern == ZonePattern.GRID) {
                    Spacer(Modifier.height(8.dp))
                    LabeledSlider("Espacement de la grille", "${cfg.gridStepDp} dp", cfg.gridStepDp.toFloat(), 8f..200f) { v ->
                        update { it.copy(gridStepDp = v.roundToInt()) }
                    }
                }
                LabeledSlider(
                    "Clics simultanés", "${cfg.tapsPerCycle} doigt${if (cfg.tapsPerCycle > 1) "s" else ""}",
                    cfg.tapsPerCycle.toFloat(), 1f..ClickPlanner.MAX_TAPS.toFloat(), steps = ClickPlanner.MAX_TAPS - 2,
                ) { v -> update { it.copy(tapsPerCycle = v.roundToInt()) } }
            }
        } else {
            Title("Point unique")
            Section {
                Text("Fais glisser la cible bleue à l'écran à l'endroit où cliquer.", color = Dim, fontSize = 14.sp)
            }
        }

        // --- Vitesse ---------------------------------------------------------------------------------
        Title("Vitesse")
        Section {
            val idx = INTERVALS.indexOfFirst { it >= cfg.intervalMs }.let { if (it < 0) INTERVALS.lastIndex else it }
            val perSecond = 1000f / maxOf(cfg.intervalMs, cfg.tapDurationMs + 10) * cfg.tapsPerCycle.coerceAtLeast(1)
            LabeledSlider(
                "Intervalle entre les clics", "${cfg.intervalMs} ms · ≈ ${"%.1f".format(perSecond)} clics/s",
                idx.toFloat(), 0f..INTERVALS.lastIndex.toFloat(), steps = INTERVALS.size - 2,
            ) { v -> update { it.copy(intervalMs = INTERVALS[v.roundToInt()]) } }
            LabeledSlider("Durée d'appui", "${cfg.tapDurationMs} ms", cfg.tapDurationMs.toFloat(), 1f..500f) { v ->
                update { it.copy(tapDurationMs = v.roundToInt()) }
            }
            Toggle("Rythme irrégulier (±25 %)", "Imite un humain en variant l'intervalle", cfg.humanize) { on ->
                update { it.copy(humanize = on) }
            }
        }

        // --- Arrêt automatique -------------------------------------------------------------------------
        Title("Arrêt automatique")
        Section {
            val ci = STOP_CLICKS.indexOf(cfg.stopAfterClicks).coerceAtLeast(0)
            LabeledSlider(
                "Après un nombre de clics", if (cfg.stopAfterClicks == 0) "Jamais" else "${cfg.stopAfterClicks} clics",
                ci.toFloat(), 0f..STOP_CLICKS.lastIndex.toFloat(), steps = STOP_CLICKS.size - 2,
            ) { v -> update { it.copy(stopAfterClicks = STOP_CLICKS[v.roundToInt()]) } }
            val si = STOP_SECONDS.indexOf(cfg.stopAfterSeconds).coerceAtLeast(0)
            LabeledSlider(
                "Après une durée", formatDuration(cfg.stopAfterSeconds),
                si.toFloat(), 0f..STOP_SECONDS.lastIndex.toFloat(), steps = STOP_SECONDS.size - 2,
            ) { v -> update { it.copy(stopAfterSeconds = STOP_SECONDS[v.roundToInt()]) } }
        }

        // --- Aide --------------------------------------------------------------------------------------
        Title("Comment ça marche")
        Section {
            listOf(
                "1. Active le service d'accessibilité (une seule fois).",
                "2. Le panneau flottant apparaît par-dessus tes applis.",
                "3. Place et redimensionne le cadre (ou la cible).",
                "4. Appuie sur ▶ : les clics démarrent. Appuie sur ⏸ pour arrêter.",
                "Le panneau lui-même n'est jamais cliqué, même s'il est dans le cadre.",
            ).forEach { Text(it, color = Dim, fontSize = 14.sp, modifier = Modifier.padding(vertical = 3.dp)) }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (disclosure) {
        AlertDialog(
            onDismissRequest = { disclosure = false },
            title = { Text("Service d'accessibilité") },
            text = {
                Text(
                    "Auto Clicker utilise l'API d'accessibilité uniquement pour effectuer les clics que tu configures et " +
                        "afficher le panneau flottant. Elle ne lit pas le contenu de l'écran et aucune donnée n'est collectée " +
                        "ni partagée.\n\nDans l'écran suivant, choisis « Auto Clicker » puis active-le."
                )
            },
            confirmButton = {
                TextButton({
                    disclosure = false
                    ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("Continuer") }
            },
            dismissButton = { TextButton({ disclosure = false }) { Text("Annuler") } },
        )
    }
}

private fun moveTaskToBack(ctx: android.content.Context) {
    (ctx as? ComponentActivity)?.moveTaskToBack(true)
}

private fun formatDuration(s: Int) = when {
    s == 0 -> "Jamais"
    s < 60 -> "$s s"
    s < 3600 -> "${s / 60} min"
    else -> "${s / 3600} h"
}

@Composable
private fun Title(text: String) {
    Text(
        text.uppercase(), color = Dim, fontSize = 13.sp, fontWeight = FontWeight.Black,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun Section(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Card)
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(20.dp))
            .padding(16.dp),
        content = content,
    )
}

@Composable
private fun Choice(options: List<Pair<String, Boolean>>, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(Card)
            .padding(4.dp)
    ) {
        options.forEachIndexed { i, (label, selected) ->
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) Accent else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Chip(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x226C8CFF))
            .border(1.dp, Color(0x556C8CFF), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: String,
    current: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(value, color = Mint, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
    Slider(
        value = current.coerceIn(range.start, range.endInclusive),
        onValueChange = onChange,
        valueRange = range,
        steps = steps,
        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Accent),
    )
}

@Composable
private fun Toggle(label: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!on) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(sub, color = Dim, fontSize = 13.sp)
        }
        Switch(on, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Accent))
    }
}
