package com.bommer.stacktower.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.StarRate
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bommer.stacktower.BuildConfig
import com.bommer.stacktower.data.Profile
import com.bommer.stacktower.data.Settings
import com.bommer.stacktower.ui.AppController
import com.bommer.stacktower.ui.Notice
import com.bommer.stacktower.ui.design.Buttons
import com.bommer.stacktower.ui.design.GameButton
import com.bommer.stacktower.ui.design.GlassCard
import com.bommer.stacktower.ui.design.Palette
import com.bommer.stacktower.ui.design.Scrim
import com.bommer.stacktower.ui.design.absorbClicks
import com.bommer.stacktower.ui.design.bodyStyle
import com.bommer.stacktower.ui.design.bouncyClick
import com.bommer.stacktower.ui.design.titleStyle

@Composable
fun SettingsScreen(c: AppController, profile: Profile, onBack: () -> Unit) {
    var confirmReset by remember { mutableStateOf(false) }
    val s = profile.settings
    fun set(transform: (Settings) -> Settings) = c.update({ it.copy(settings = transform(it.settings)) })

    Box {
        ScreenScaffold("Réglages", null, onBack) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = if (profile.adsRemoved) 16.dp else 70.dp)
            ) {
                SectionTitle("Son & sensations")
                GlassCard(Modifier.fillMaxWidth(), padding = 6.dp) {
                    ToggleRow(Icons.AutoMirrored.Rounded.VolumeUp, "Effets sonores", s.sound) { v -> set { it.copy(sound = v) } }
                    ToggleRow(Icons.Rounded.MusicNote, "Musique", s.music) { v -> set { it.copy(music = v) } }
                    ToggleRow(Icons.Rounded.Vibration, "Vibrations", s.vibration) { v -> set { it.copy(vibration = v) } }
                }
                SectionTitle("Affichage")
                GlassCard(Modifier.fillMaxWidth(), padding = 6.dp) {
                    ToggleRow(Icons.Rounded.AutoAwesome, "Particules", s.particles) { v -> set { it.copy(particles = v) } }
                    ToggleRow(Icons.Rounded.Waves, "Secousses d'écran", s.screenShake) { v -> set { it.copy(screenShake = v) } }
                    ToggleRow(Icons.Rounded.Lightbulb, "Revoir le tutoriel", !profile.tutorialDone) { v ->
                        c.update({ it.copy(tutorialDone = !v) })
                    }
                }
                SectionTitle("Application")
                GlassCard(Modifier.fillMaxWidth(), padding = 6.dp) {
                    ActionRow(Icons.Rounded.StarRate, "Noter Stack Tower") { c.rateApp() }
                    ActionRow(Icons.Rounded.Share, "Inviter un ami") { c.shareApp() }
                    ActionRow(Icons.Rounded.Restore, "Restaurer mes achats") { c.restorePurchases() }
                    if (c.consent.privacyOptionsRequired) {
                        ActionRow(Icons.Rounded.PrivacyTip, "Choix de confidentialité (pubs)") { c.showPrivacyOptions() }
                    }
                    ActionRow(Icons.Rounded.Policy, "Politique de confidentialité") { c.openUrl(AppController.PRIVACY_URL) }
                    ActionRow(Icons.Rounded.DeleteForever, "Réinitialiser la progression", Palette.Red) { confirmReset = true }
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    "Stack Tower ${BuildConfig.VERSION_NAME}",
                    style = bodyStyle(13.sp, FontWeight.Bold, Palette.TextDim),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (confirmReset) {
            Scrim(onDismiss = { confirmReset = false }) {
                GlassCard(Modifier.widthIn(max = 360.dp).padding(24.dp).absorbClicks()) {
                    Text("Tout effacer ?", style = titleStyle(28.sp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Pièces, niveaux, records, thèmes et succès seront perdus. Tes achats (zéro pub) sont conservés.",
                        style = bodyStyle(15.sp, FontWeight.SemiBold, Palette.TextDim),
                    )
                    Spacer(Modifier.height(18.dp))
                    GameButton("Effacer", {
                        c.update({ p -> Profile(adsRemoved = p.adsRemoved, settings = p.settings, tutorialDone = true) })
                        c.notify(Notice.Kind.INFO, "Progression réinitialisée")
                        confirmReset = false
                    }, Modifier.fillMaxWidth(), Buttons.Pink)
                    Spacer(Modifier.height(10.dp))
                    GameButton("Annuler", { confirmReset = false }, Modifier.fillMaxWidth(), Buttons.Ghost)
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(icon: ImageVector, label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .bouncyClick { onChange(!value) }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, style = bodyStyle(16.sp, FontWeight.ExtraBold), modifier = Modifier.weight(1f))
        Toggle(value)
    }
}

@Composable
private fun Toggle(on: Boolean) {
    val x by animateDpAsState(if (on) 22.dp else 0.dp, label = "toggle")
    val bg by animateColorAsState(if (on) Palette.Green else Color(0x55FFFFFF), label = "toggleBg")
    Box(
        Modifier
            .width(52.dp)
            .height(30.dp)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(3.dp)
    ) {
        Box(
            Modifier
                .offset(x = x)
                .size(24.dp)
                .background(Color.White, CircleShape)
        )
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, tint: Color = Color.White, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .bouncyClick { onClick() }
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, style = bodyStyle(16.sp, FontWeight.ExtraBold, tint))
    }
}
