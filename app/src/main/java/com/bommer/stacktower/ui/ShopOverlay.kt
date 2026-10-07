package com.bommer.stacktower.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bommer.stacktower.data.PlayerData
import com.bommer.stacktower.game.GameTheme
import com.bommer.stacktower.game.Themes
import com.bommer.stacktower.monetization.BillingManager

@Composable
fun ShopOverlay(
    player: PlayerData,
    rewardedReady: Boolean,
    removeAdsPrice: String?,
    coinPackPrice: String?,
    onBuyTheme: (GameTheme) -> Unit,
    onSelectTheme: (GameTheme) -> Unit,
    onFreeCoins: () -> Unit,
    onBuyCoins: () -> Unit,
    onRemoveAds: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xF0101225))
            .padding(horizontal = 16.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "←  Retour",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier
                    .pointerInput(Unit) { detectTapGestures { onBack() } }
                    .padding(8.dp),
            )
            CoinChip(player.coins)
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(bottom = 70.dp),
        ) {
            item { SectionTitle("Pièces & bonus") }
            if (rewardedReady) {
                item {
                    ShopRow("Vidéo : +$FREE_COINS_REWARD pièces", "GRATUIT", Color(0xFF00C853), onFreeCoins)
                }
            }
            item {
                ShopRow(
                    "Pack de ${BillingManager.COIN_PACK_AMOUNT} pièces",
                    coinPackPrice ?: "—",
                    Color(0xFFFFC107),
                    onBuyCoins,
                )
            }
            if (!player.adsRemoved) {
                item {
                    ShopRow("Supprimer les pubs", removeAdsPrice ?: "—", Color(0xFFE91E63), onRemoveAds)
                }
            }

            item { SectionTitle("Thèmes") }
            items(Themes.all, key = { it.id }) { theme ->
                val owned = theme.id in player.ownedThemes
                val selected = theme.id == player.selectedTheme
                ThemeCard(
                    theme = theme,
                    owned = owned,
                    selected = selected,
                    affordable = player.coins >= theme.price,
                    onClick = { if (owned) onSelectTheme(theme) else onBuyTheme(theme) },
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = Color(0xFFB0B4D8),
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun ShopRow(title: String, action: String, accent: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0x22FFFFFF), RoundedCornerShape(18.dp))
            .pointerInput(onClick) { detectTapGestures { onClick() } }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text(
            action,
            color = Color.Black,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(accent, RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ThemeCard(
    theme: GameTheme,
    owned: Boolean,
    selected: Boolean,
    affordable: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.horizontalGradient(listOf(Color(theme.bgTop), Color(theme.bgBottom))))
            .border(
                width = if (selected) 3.dp else 0.dp,
                color = if (selected) Color.White else Color.Transparent,
                shape = RoundedCornerShape(18.dp),
            )
            .pointerInput(onClick) { detectTapGestures { onClick() } }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Aperçu : mini-tour aux couleurs du thème.
        Canvas(Modifier.size(width = 56.dp, height = 48.dp)) {
            val rows = 5
            val h = size.height / rows
            for (i in 0 until rows) {
                val w = size.width * (1f - i * 0.12f)
                drawRoundRect(
                    GameRenderer.blockColor(theme, i * 2),
                    topLeft = Offset((size.width - w) / 2f, size.height - (i + 1) * h),
                    size = Size(w, h * 0.9f),
                    cornerRadius = CornerRadius(h * 0.2f),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        val light = theme.id == "candy" || theme.id == "mono"
        Text(
            theme.name,
            color = if (light) Color(0xFF222222) else Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier
                .background(Color(0x99000000), RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            when {
                selected -> Text("✓ Équipé", color = Color.White, fontWeight = FontWeight.Bold)
                owned -> Text("Équiper", color = Color.White, fontWeight = FontWeight.Bold)
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Coin(14)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${theme.price}",
                        color = if (affordable) Color(0xFFFFD54F) else Color(0xFFFF8A80),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
