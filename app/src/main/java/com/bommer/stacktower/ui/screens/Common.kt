package com.bommer.stacktower.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MonetizationOn
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bommer.stacktower.ui.AppController
import com.bommer.stacktower.ui.Notice
import com.bommer.stacktower.ui.design.CoinCounter
import com.bommer.stacktower.ui.design.IconBubble
import com.bommer.stacktower.ui.design.Palette
import com.bommer.stacktower.ui.design.bodyStyle
import com.bommer.stacktower.ui.design.bouncyClick
import com.bommer.stacktower.ui.design.titleStyle
import kotlinx.coroutines.delay

/** Gabarit des écrans secondaires : barre de titre + retour + pièces. */
@Composable
fun ScreenScaffold(
    title: String,
    coins: Int?,
    onBack: () -> Unit,
    onCoins: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xE60E1022))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBubble(Icons.AutoMirrored.Rounded.ArrowBack, onBack, size = 46.dp)
            Spacer(Modifier.width(14.dp))
            Text(title, style = titleStyle(30.sp), modifier = Modifier.weight(1f), maxLines = 1)
            if (coins != null) CoinCounter(coins, onPlus = onCoins)
        }
        content()
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = bodyStyle(13.sp, FontWeight.Black, Palette.TextDim),
        modifier = modifier.padding(top = 14.dp, bottom = 8.dp),
    )
}

/** Affiche les notifications (succès, niveau, achats…) en haut de l'écran. */
@Composable
fun NoticeHost(c: AppController) {
    val current = c.notices.firstOrNull()
    var visible by remember { mutableStateOf<Notice?>(null) }
    LaunchedEffect(current?.id) {
        if (current != null) {
            visible = current
            delay(2600)
            visible = null
            delay(300)
            c.dismiss(current)
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = 8.dp, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedVisibility(
            visible = visible != null,
            enter = slideInVertically { -it * 2 },
            exit = slideOutVertically { -it * 2 } + fadeOut(),
        ) {
            val n = visible ?: current ?: return@AnimatedVisibility
            val (icon, color) = when (n.kind) {
                Notice.Kind.ACHIEVEMENT -> Icons.Rounded.EmojiEvents to Palette.Gold
                Notice.Kind.LEVEL -> Icons.Rounded.Star to Palette.Purple
                Notice.Kind.COINS -> Icons.Rounded.MonetizationOn to Palette.Gold
                Notice.Kind.ERROR -> Icons.Rounded.ErrorOutline to Palette.Red
                Notice.Kind.INFO -> Icons.Rounded.Info to Palette.Blue
            }
            Row(
                Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xF2151833))
                    .border(2.dp, color.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                    .bouncyClick(sound = false) { visible = null }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(color.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, null, tint = color, modifier = Modifier.size(28.dp)) }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(n.title, style = titleStyle(19.sp, shadow = false))
                    if (n.subtitle.isNotEmpty()) Text(n.subtitle, style = bodyStyle(13.sp, FontWeight.Bold, Palette.TextDim))
                }
            }
        }
    }
}
