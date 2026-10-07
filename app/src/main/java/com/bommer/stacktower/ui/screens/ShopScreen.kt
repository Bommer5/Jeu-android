package com.bommer.stacktower.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OndemandVideo
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bommer.stacktower.data.Profile
import com.bommer.stacktower.data.Progression
import com.bommer.stacktower.game.BackgroundFx
import com.bommer.stacktower.game.BlockSkin
import com.bommer.stacktower.game.Booster
import com.bommer.stacktower.game.GameTheme
import com.bommer.stacktower.game.Themes
import com.bommer.stacktower.monetization.BillingManager
import com.bommer.stacktower.ui.AppController
import com.bommer.stacktower.ui.GameRenderer
import com.bommer.stacktower.ui.Notice
import com.bommer.stacktower.ui.design.Buttons
import com.bommer.stacktower.ui.design.CoinIcon
import com.bommer.stacktower.ui.design.GameButton
import com.bommer.stacktower.ui.design.GlassCard
import com.bommer.stacktower.ui.design.Palette
import com.bommer.stacktower.ui.design.Pill
import com.bommer.stacktower.ui.design.SegmentedTabs
import com.bommer.stacktower.ui.design.bodyStyle
import com.bommer.stacktower.ui.design.bouncyClick
import com.bommer.stacktower.ui.design.titleStyle

enum class ShopTab(val title: String) { THEMES("Thèmes"), SKINS("Blocs"), FX("Décors"), BOOSTERS("Bonus"), COINS("Pièces") }

const val FREE_COINS_REWARD = 60

@Composable
fun ShopScreen(
    c: AppController,
    profile: Profile,
    tab: ShopTab,
    onTab: (ShopTab) -> Unit,
    rewardedReady: Boolean,
    renderer: GameRenderer,
    onBack: () -> Unit,
) {
    // Horloge pour animer les aperçus.
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time = (it - start) / 1e9f }
    }

    fun purchase(transform: (Profile) -> Profile?) {
        c.update(transform) { result ->
            if (result == null) {
                c.notify(Notice.Kind.ERROR, "Pas assez de pièces", "Gagne-en en jouant ou dans l'onglet Pièces")
            } else {
                c.sound.coin()
            }
        }
    }

    ScreenScaffold("Boutique", profile.coins, onBack, onCoins = { onTab(ShopTab.COINS) }) {
        SegmentedTabs(ShopTab.entries.map { it.title }, tab.ordinal, { onTab(ShopTab.entries[it]) }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        val bottomPad = PaddingValues(bottom = if (profile.adsRemoved) 16.dp else 70.dp)
        when (tab) {
            ShopTab.THEMES -> LazyVerticalGrid(
                GridCells.Fixed(2), contentPadding = bottomPad,
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(Themes.all, key = { it.id }) { t ->
                    CosmeticCard(
                        title = t.name, price = t.price, owned = t.id in profile.ownedThemes, equipped = t.id == profile.theme,
                        coins = profile.coins,
                        preview = { PreviewCanvas(renderer, t, BlockSkin.byId(profile.skin), BackgroundFx.byId(profile.fx), { time }) },
                        onClick = { purchase { Progression.buyTheme(it, t.id) } },
                    )
                }
            }

            ShopTab.SKINS -> LazyVerticalGrid(
                GridCells.Fixed(2), contentPadding = bottomPad,
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(BlockSkin.entries, key = { it.id }) { s ->
                    CosmeticCard(
                        title = s.title, price = s.price, owned = s.id in profile.ownedSkins, equipped = s.id == profile.skin,
                        coins = profile.coins,
                        preview = { PreviewCanvas(renderer, Themes.byId(profile.theme), s, BackgroundFx.NONE, { time }) },
                        onClick = { purchase { Progression.buySkin(it, s) } },
                    )
                }
            }

            ShopTab.FX -> LazyVerticalGrid(
                GridCells.Fixed(2), contentPadding = bottomPad,
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(BackgroundFx.entries, key = { it.id }) { f ->
                    CosmeticCard(
                        title = f.title, price = f.price, owned = f.id in profile.ownedFx, equipped = f.id == profile.fx,
                        coins = profile.coins,
                        preview = { PreviewCanvas(renderer, Themes.byId(profile.theme), BlockSkin.byId(profile.skin), f, { time }) },
                        onClick = { purchase { Progression.buyFx(it, f) } },
                    )
                }
            }

            ShopTab.BOOSTERS -> LazyColumn(contentPadding = bottomPad, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text(
                        "Active tes bonus sur l'écran d'accueil avant de lancer une partie.",
                        style = bodyStyle(14.sp, FontWeight.SemiBold, Palette.TextDim),
                    )
                }
                items(Booster.entries) { b ->
                    GlassCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Brush.linearGradient(listOf(Palette.Gold, Palette.Orange))),
                                contentAlignment = Alignment.Center,
                            ) { Icon(b.icon(), null, tint = Color.White, modifier = Modifier.size(32.dp)) }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(b.title, style = titleStyle(20.sp, shadow = false))
                                Text(b.description, style = bodyStyle(13.sp, FontWeight.SemiBold, Palette.TextDim))
                                Text("En stock : ${profile.boosterCount(b)}", style = bodyStyle(13.sp, FontWeight.Black, Palette.Gold))
                            }
                            Spacer(Modifier.width(8.dp))
                            PriceButton(b.price, profile.coins >= b.price) { purchase { Progression.buyBooster(it, b) } }
                        }
                    }
                }
            }

            ShopTab.COINS -> CoinsTab(c, profile, rewardedReady, bottomPad)
        }
    }
}

@Composable
private fun CoinsTab(c: AppController, profile: Profile, rewardedReady: Boolean, pad: PaddingValues) {
    val products by c.billing.products.collectAsState()
    LazyColumn(contentPadding = pad, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            GlassCard(Modifier.fillMaxWidth(), color = Color(0xE61B4D3A)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.OndemandVideo, null, tint = Palette.Green, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Pièces gratuites", style = titleStyle(20.sp, shadow = false))
                        Text("Regarde une courte vidéo", style = bodyStyle(13.sp, FontWeight.SemiBold, Palette.TextDim))
                    }
                    GameButton(
                        "+$FREE_COINS_REWARD", {
                            c.showRewarded {
                                c.update({ it.copy(coins = it.coins + FREE_COINS_REWARD) })
                                c.sound.coin()
                                c.notify(Notice.Kind.COINS, "+$FREE_COINS_REWARD pièces !")
                            }
                        },
                        Modifier.width(110.dp), Buttons.Primary, enabled = rewardedReady, height = 48.dp, fontSize = 18.sp,
                    )
                }
            }
        }
        items(BillingManager.COIN_PACKS) { pack ->
            val price = products[pack.productId]?.oneTimePurchaseOfferDetails?.formattedPrice
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CoinStack(pack.coins)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${pack.coins}", style = titleStyle(24.sp, Palette.Gold, shadow = false))
                            if (pack.bestValue) {
                                Spacer(Modifier.width(8.dp))
                                Pill("MEILLEURE OFFRE", Palette.Pink)
                            }
                        }
                        Text(pack.label, style = bodyStyle(13.sp, FontWeight.SemiBold, Palette.TextDim))
                    }
                    GameButton(price ?: "…", { c.buy(pack.productId) }, Modifier.width(110.dp), Buttons.Gold,
                        height = 48.dp, fontSize = 17.sp)
                }
            }
        }
        if (!profile.adsRemoved) {
            item {
                val price = products[BillingManager.REMOVE_ADS]?.oneTimePurchaseOfferDetails?.formattedPrice
                GlassCard(Modifier.fillMaxWidth(), color = Color(0xE64A1B3A)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Block, null, tint = Palette.Pink, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Zéro pub", style = titleStyle(20.sp, shadow = false))
                            Text("Plus de bannières ni de pubs entre les parties. Pour toujours.",
                                style = bodyStyle(13.sp, FontWeight.SemiBold, Palette.TextDim))
                        }
                        GameButton(price ?: "…", { c.buy(BillingManager.REMOVE_ADS) }, Modifier.width(110.dp), Buttons.Pink,
                            height = 48.dp, fontSize = 17.sp)
                    }
                }
            }
        }
        item {
            Text(
                "Restaurer mes achats",
                style = bodyStyle(14.sp, FontWeight.ExtraBold, Palette.TextDim),
                modifier = Modifier
                    .fillMaxWidth()
                    .bouncyClick { c.restorePurchases() }
                    .padding(12.dp),
            )
        }
    }
}

@Composable
private fun CoinStack(amount: Int) {
    val n = when {
        amount >= 5000 -> 4
        amount >= 1500 -> 3
        else -> 2
    }
    Box(Modifier.size(52.dp)) {
        repeat(n) { i ->
            Box(Modifier.padding(start = (i * 7).dp, top = ((n - 1 - i) * 6).dp)) { CoinIcon(30.dp) }
        }
    }
}

@Composable
private fun PreviewCanvas(renderer: GameRenderer, theme: GameTheme, skin: BlockSkin, fx: BackgroundFx, time: () -> Float) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1.1f)
    ) {
        with(renderer) { renderPreview(theme, skin, fx, time()) }
    }
}

@Composable
private fun CosmeticCard(
    title: String,
    price: Int,
    owned: Boolean,
    equipped: Boolean,
    coins: Int,
    preview: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .clip(shape)
            .background(Palette.Panel)
            .border(if (equipped) 3.dp else 1.5.dp, if (equipped) Palette.Gold else Palette.Stroke, shape)
            .bouncyClick { onClick() }
    ) {
        Box {
            preview()
            if (equipped) {
                Pill("ÉQUIPÉ", Palette.Gold, Color(0xFF3A2400), Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    Icon(Icons.Rounded.Check, null, tint = Color(0xFF3A2400), modifier = Modifier.size(14.dp))
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = titleStyle(17.sp, shadow = false), modifier = Modifier.weight(1f), maxLines = 1)
            when {
                equipped -> Unit
                owned -> Text("Équiper", style = bodyStyle(13.sp, FontWeight.Black, Palette.Green))
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    if (coins < price) Icon(Icons.Rounded.Lock, null, tint = Palette.TextDim, modifier = Modifier.size(14.dp))
                    CoinIcon(16.dp)
                    Spacer(Modifier.width(4.dp))
                    Text("$price", style = bodyStyle(14.sp, FontWeight.Black, if (coins >= price) Palette.Gold else Palette.Red))
                }
            }
        }
    }
}

@Composable
private fun PriceButton(price: Int, affordable: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (affordable) Palette.Gold else Color(0x55FFFFFF))
            .bouncyClick { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoinIcon(18.dp)
        Spacer(Modifier.width(6.dp))
        Text("$price", style = titleStyle(17.sp, if (affordable) Color(0xFF3A2400) else Color.White, shadow = false))
    }
}
