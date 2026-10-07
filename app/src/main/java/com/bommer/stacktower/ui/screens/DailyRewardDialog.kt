package com.bommer.stacktower.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.OndemandVideo
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bommer.stacktower.data.Profile
import com.bommer.stacktower.data.Progression
import com.bommer.stacktower.ui.AppController
import com.bommer.stacktower.ui.Notice
import com.bommer.stacktower.ui.design.Buttons
import com.bommer.stacktower.ui.design.CoinIcon
import com.bommer.stacktower.ui.design.GameButton
import com.bommer.stacktower.ui.design.GlassCard
import com.bommer.stacktower.ui.design.Palette
import com.bommer.stacktower.ui.design.Scrim
import com.bommer.stacktower.ui.design.absorbClicks
import com.bommer.stacktower.ui.design.bodyStyle
import com.bommer.stacktower.ui.design.titleStyle

/** Calendrier de récompenses sur 7 jours consécutifs. */
@Composable
fun DailyRewardDialog(c: AppController, profile: Profile, rewardedReady: Boolean, onClose: () -> Unit) {
    val today = c.today()
    val canClaim = Progression.canClaimDaily(profile, today)
    val dayIndex = if (canClaim) Progression.nextStreak(profile, today) else profile.dailyStreak
    // Récompense obtenue pendant cette ouverture (pour proposer de la doubler).
    var claimed by remember { mutableIntStateOf(0) }
    var doubled by remember { mutableIntStateOf(0) }

    Scrim(onDismiss = onClose) {
        GlassCard(Modifier.widthIn(max = 400.dp).padding(20.dp).absorbClicks()) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.CardGiftcard, null, tint = Palette.Gold, modifier = Modifier.size(52.dp))
                Text("Cadeau du jour", style = titleStyle(30.sp))
                Text(
                    "Reviens chaque jour : les récompenses augmentent !",
                    style = bodyStyle(14.sp, FontWeight.Bold, Palette.TextDim), textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(14.dp))
                for (row in 0 until 2) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val days = if (row == 0) 1..4 else 5..7
                        for (d in days) {
                            val past = d < dayIndex || (d == dayIndex && !canClaim)
                            val current = d == dayIndex && canClaim
                            DayCell(d, past, current, Modifier.weight(if (d == 7) 2f else 1f))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(10.dp))
                when {
                    canClaim -> GameButton("Récupérer", {
                        c.update({ Progression.claimDaily(it, today).first }) { p ->
                            if (p != null) {
                                val reward = Progression.dailyReward(p.dailyStreak)
                                claimed = reward
                                c.sound.coin()
                                c.notify(Notice.Kind.COINS, "+$reward pièces", "Jour ${p.dailyStreak} de ta série")
                            }
                        }
                    }, Modifier.fillMaxWidth(), Buttons.Gold, Icons.Rounded.CardGiftcard)

                    claimed > 0 && doubled == 0 && rewardedReady -> GameButton("Doubler (+$claimed)", {
                        c.showRewarded {
                            c.update({ it.copy(coins = it.coins + claimed) })
                            doubled = claimed
                            c.sound.coin()
                            c.notify(Notice.Kind.COINS, "+$claimed pièces bonus !")
                        }
                    }, Modifier.fillMaxWidth(), Buttons.Primary, Icons.Rounded.OndemandVideo)

                    else -> GameButton("Super !", onClose, Modifier.fillMaxWidth(), Buttons.Blue)
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: Int, past: Boolean, current: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(14.dp)
    val bg = when {
        current -> Brush.verticalGradient(listOf(Palette.Gold, Palette.Orange))
        past -> Brush.verticalGradient(listOf(Color(0x5532D47A), Color(0x3332D47A)))
        else -> Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x1AFFFFFF)))
    }
    Column(
        modifier
            .height(86.dp)
            .clip(shape)
            .background(bg)
            .border(if (current) 2.dp else 1.dp, if (current) Color.White else Palette.Stroke, shape)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Jour $day", style = bodyStyle(11.sp, FontWeight.Black, if (current) Color(0xFF3A2400) else Color.White))
        Box(contentAlignment = Alignment.Center) {
            if (past) {
                Icon(Icons.Rounded.Check, null, tint = Palette.Green, modifier = Modifier.size(28.dp))
            } else if (day == 7) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CoinIcon(24.dp)
                    Icon(Icons.Rounded.Shield, null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
            } else {
                CoinIcon(26.dp)
            }
        }
        Text(
            "${Progression.dailyReward(day)}",
            style = titleStyle(15.sp, if (current) Color(0xFF3A2400) else Palette.Gold, shadow = false),
        )
    }
}
