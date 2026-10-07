package com.bommer.stacktower.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bommer.stacktower.data.Achievements
import com.bommer.stacktower.data.Missions
import com.bommer.stacktower.data.Profile
import com.bommer.stacktower.data.Progression
import com.bommer.stacktower.game.GameMode
import com.bommer.stacktower.ui.AppController
import com.bommer.stacktower.ui.Notice
import com.bommer.stacktower.ui.design.Buttons
import com.bommer.stacktower.ui.design.CoinIcon
import com.bommer.stacktower.ui.design.GameButton
import com.bommer.stacktower.ui.design.GlassCard
import com.bommer.stacktower.ui.design.LevelBadge
import com.bommer.stacktower.ui.design.Palette
import com.bommer.stacktower.ui.design.ProgressBar
import com.bommer.stacktower.ui.design.SegmentedTabs
import com.bommer.stacktower.ui.design.bodyStyle
import com.bommer.stacktower.ui.design.titleStyle
import java.time.LocalTime
import java.time.temporal.ChronoUnit

@Composable
fun ProgressScreen(
    c: AppController,
    profile: Profile,
    tab: Int,
    onTab: (Int) -> Unit,
    onBack: () -> Unit,
) {
    ScreenScaffold("Progression", profile.coins, onBack) {
        SegmentedTabs(listOf("Missions", "Succès", "Stats"), tab, onTab, Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        val pad = PaddingValues(bottom = if (profile.adsRemoved) 16.dp else 70.dp)
        when (tab) {
            0 -> MissionsTab(c, profile, pad)
            1 -> AchievementsTab(profile, pad)
            else -> StatsTab(profile, pad)
        }
    }
}

@Composable
private fun MissionsTab(c: AppController, profile0: Profile, pad: PaddingValues) {
    val today = c.today()
    val profile = Missions.ensureDay(profile0, today)
    val missions = Missions.forDay(today)
    val minutesLeft = ChronoUnit.MINUTES.between(LocalTime.now(), LocalTime.MAX).toInt()
    LazyColumn(contentPadding = pad, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(
                "Nouvelles missions dans ${minutesLeft / 60} h ${minutesLeft % 60} min",
                style = bodyStyle(14.sp, FontWeight.Bold, Palette.TextDim),
            )
        }
        items(missions, key = { it.id }) { m ->
            val progress = Missions.progress(profile, m)
            val done = progress >= m.target
            val claimed = m.id in profile.missionsClaimed
            GlassCard(Modifier.fillMaxWidth().alpha(if (claimed) 0.6f else 1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (done) Palette.Green else Color(0x33FFFFFF)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(if (claimed) Icons.Rounded.Check else Icons.Rounded.TaskAlt, null, tint = Color.White,
                            modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.title, style = bodyStyle(16.sp, FontWeight.ExtraBold))
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ProgressBar(progress.toFloat() / m.target, Modifier.weight(1f), if (done) Palette.Green else Palette.Blue)
                            Spacer(Modifier.width(8.dp))
                            Text("$progress/${m.target}", style = bodyStyle(13.sp, FontWeight.Black, Palette.TextDim))
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    if (done && !claimed) {
                        GameButton("+${m.reward}", {
                            c.update({ Missions.claim(Missions.ensureDay(it, today), m) }) { p ->
                                if (p != null) {
                                    c.sound.coin()
                                    c.notify(Notice.Kind.COINS, "Mission accomplie !", "+${m.reward} pièces")
                                }
                            }
                        }, Modifier.width(96.dp), Buttons.Gold, height = 44.dp, fontSize = 17.sp)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CoinIcon(18.dp)
                            Spacer(Modifier.width(4.dp))
                            Text(if (claimed) "Reçu" else "${m.reward}", style = bodyStyle(15.sp, FontWeight.Black, Palette.Gold))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AchievementsTab(profile: Profile, pad: PaddingValues) {
    val sorted = Achievements.all.sortedBy { it.id !in profile.achievements }
    LazyColumn(contentPadding = pad, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${profile.achievements.size} / ${Achievements.all.size} débloqués",
                    style = titleStyle(18.sp, shadow = false), modifier = Modifier.weight(1f),
                )
                ProgressBar(profile.achievements.size.toFloat() / Achievements.all.size, Modifier.width(120.dp), Palette.Gold)
            }
        }
        items(sorted, key = { it.id }) { a ->
            val unlocked = a.id in profile.achievements
            GlassCard(Modifier.fillMaxWidth().alpha(if (unlocked) 1f else 0.65f), padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (unlocked) Brush.linearGradient(listOf(Palette.Gold, Palette.Orange))
                                else Brush.linearGradient(listOf(Color(0x33FFFFFF), Color(0x22FFFFFF)))
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(if (unlocked) Icons.Rounded.EmojiEvents else Icons.Rounded.Lock, null, tint = Color.White,
                            modifier = Modifier.size(26.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.title, style = bodyStyle(16.sp, FontWeight.Black))
                        Text(a.description, style = bodyStyle(13.sp, FontWeight.SemiBold, Palette.TextDim))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CoinIcon(16.dp)
                        Spacer(Modifier.width(4.dp))
                        Text("${a.reward}", style = bodyStyle(14.sp, FontWeight.Black, Palette.Gold))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsTab(profile: Profile, pad: PaddingValues) {
    LazyColumn(contentPadding = pad, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LevelBadge(profile.level, Progression.levelProgress(profile.xp), 70.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Niveau ${profile.level}", style = titleStyle(24.sp, shadow = false))
                        val inLevel = (Progression.levelProgress(profile.xp) * Progression.xpForLevel(profile.level)).toInt()
                        Text("$inLevel / ${Progression.xpForLevel(profile.level)} XP",
                            style = bodyStyle(13.sp, FontWeight.Bold, Palette.TextDim))
                        Spacer(Modifier.height(6.dp))
                        ProgressBar(Progression.levelProgress(profile.xp), Modifier.fillMaxWidth(), Palette.Gold)
                        Text("Prochain niveau : +${Progression.levelReward(profile.level + 1)} pièces",
                            style = bodyStyle(12.sp, FontWeight.Bold, Palette.Gold))
                    }
                }
            }
        }
        item { SectionTitle("Records") }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                GameMode.entries.filter { it != GameMode.DAILY }.forEach { m ->
                    Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(m.icon(), null, tint = m.accent(), modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(m.title, style = bodyStyle(16.sp, FontWeight.ExtraBold), modifier = Modifier.weight(1f))
                        Text("${profile.bestFor(m)}", style = titleStyle(20.sp, shadow = false))
                    }
                }
            }
        }
        item { SectionTitle("Statistiques") }
        item {
            val hours = profile.playTimeSec / 3600
            val minutes = (profile.playTimeSec % 3600) / 60
            val stats = listOf(
                "Parties jouées" to "${profile.gamesPlayed}",
                "Blocs posés" to "${profile.totalBlocks}",
                "Parfaits" to "${profile.totalPerfects}",
                "Meilleur combo" to "${profile.bestCombo}",
                "Temps de jeu" to if (hours > 0) "${hours} h ${minutes} min" else "$minutes min",
                "Thèmes possédés" to "${profile.ownedThemes.size}",
                "Taux de parfaits" to if (profile.totalBlocks > 0) "${profile.totalPerfects * 100 / profile.totalBlocks} %" else "—",
            )
            GlassCard(Modifier.fillMaxWidth()) {
                stats.forEach { (k, v) ->
                    Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(k, style = bodyStyle(15.sp, FontWeight.SemiBold, Palette.TextDim), modifier = Modifier.weight(1f))
                        Text(v, style = bodyStyle(16.sp, FontWeight.Black))
                    }
                }
            }
        }
    }
}
