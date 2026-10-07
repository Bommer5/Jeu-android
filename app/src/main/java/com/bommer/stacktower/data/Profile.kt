package com.bommer.stacktower.data

import com.bommer.stacktower.game.BackgroundFx
import com.bommer.stacktower.game.BlockSkin
import com.bommer.stacktower.game.Booster
import com.bommer.stacktower.game.GameMode
import com.bommer.stacktower.game.Themes
import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
data class Settings(
    val sound: Boolean = true,
    val music: Boolean = true,
    val vibration: Boolean = true,
    val particles: Boolean = true,
    val screenShake: Boolean = true,
    val hints: Boolean = true,
)

/** Toute la progression du joueur, sérialisée en JSON dans DataStore. */
@Serializable
data class Profile(
    val coins: Int = 0,
    val xp: Int = 0,
    val gamesPlayed: Int = 0,
    val totalBlocks: Int = 0,
    val totalPerfects: Int = 0,
    val bestCombo: Int = 0,
    val playTimeSec: Long = 0,
    val bests: Map<String, Int> = emptyMap(),
    val dailyChallengeDay: Long = -1,
    val dailyChallengeBest: Int = 0,
    val ownedThemes: Set<String> = setOf(DEFAULT_THEME),
    val theme: String = DEFAULT_THEME,
    val ownedSkins: Set<String> = setOf(BlockSkin.CLASSIC.id),
    val skin: String = BlockSkin.CLASSIC.id,
    val ownedFx: Set<String> = setOf(BackgroundFx.NONE.id, BackgroundFx.STARS.id),
    val fx: String = BackgroundFx.STARS.id,
    val boosters: Map<String, Int> = mapOf(Booster.SHIELD.id to 1, Booster.SLOW.id to 1),
    val achievements: Set<String> = emptySet(),
    val adsRemoved: Boolean = false,
    val settings: Settings = Settings(),
    val dailyRewardDay: Long = -1,
    val dailyStreak: Int = 0,
    val wheelDay: Long = -1,
    val wheelFreeUsed: Boolean = false,
    val wheelAdSpins: Int = 0,
    val missionsDay: Long = -1,
    val missionProgress: Map<String, Int> = emptyMap(),
    val missionsClaimed: Set<String> = emptySet(),
    val tutorialDone: Boolean = false,
    val reviewAsked: Boolean = false,
    val lastMode: String = GameMode.CLASSIC.id,
) {
    val level: Int get() = Progression.levelFor(xp)
    val best: Int get() = bests.values.maxOrNull() ?: 0
    fun bestFor(mode: GameMode) = bests[mode.id] ?: 0
    fun boosterCount(b: Booster) = boosters[b.id] ?: 0

    companion object {
        const val DEFAULT_THEME = "aurora"
    }
}

/** Résultat d'une partie terminée. */
data class RunResult(
    val mode: GameMode,
    val score: Int,
    val perfects: Int,
    val bestCombo: Int,
    val coins: Int,
    val xp: Int,
    val playTimeSec: Int,
    val day: Long,
)

/** Effets d'une partie sur le profil (pour l'affichage). */
data class RunOutcome(
    val profile: Profile,
    val newBest: Boolean,
    val levelsGained: Int,
    val levelCoins: Int,
    val achievements: List<Achievement>,
)

object Progression {
    fun xpForLevel(level: Int): Int = 100 + (level - 1) * 60

    fun levelFor(xp: Int): Int {
        var lvl = 1
        var rest = xp
        while (rest >= xpForLevel(lvl)) {
            rest -= xpForLevel(lvl)
            lvl++
        }
        return lvl
    }

    /** Progression dans le niveau courant (0..1). */
    fun levelProgress(xp: Int): Float {
        var lvl = 1
        var rest = xp
        while (rest >= xpForLevel(lvl)) {
            rest -= xpForLevel(lvl)
            lvl++
        }
        return rest.toFloat() / xpForLevel(lvl)
    }

    fun levelReward(level: Int): Int = 40 + level * 10

    /** Récompense du jour [streak] (1..7, puis on reste à 7). */
    fun dailyReward(streak: Int): Int = DAILY_REWARDS[(streak.coerceIn(1, 7)) - 1]
    val DAILY_REWARDS = intArrayOf(20, 30, 45, 60, 80, 100, 250)

    fun applyRun(p: Profile, r: RunResult, bonusCoins: Int = 0): RunOutcome {
        val oldLevel = p.level
        val previousBest = p.bestFor(r.mode)
        var next = p.copy(
            coins = p.coins + r.coins + bonusCoins,
            xp = p.xp + r.xp,
            gamesPlayed = p.gamesPlayed + 1,
            totalBlocks = p.totalBlocks + r.score,
            totalPerfects = p.totalPerfects + r.perfects,
            bestCombo = maxOf(p.bestCombo, r.bestCombo),
            playTimeSec = p.playTimeSec + r.playTimeSec,
            bests = p.bests + (r.mode.id to maxOf(previousBest, r.score)),
        )
        if (r.mode == GameMode.DAILY) {
            val best = if (p.dailyChallengeDay == r.day) maxOf(p.dailyChallengeBest, r.score) else r.score
            next = next.copy(dailyChallengeDay = r.day, dailyChallengeBest = best)
        }
        next = Missions.track(next, r)

        val levelsGained = next.level - oldLevel
        var levelCoins = 0
        for (l in (oldLevel + 1)..next.level) levelCoins += levelReward(l)
        next = next.copy(coins = next.coins + levelCoins)

        val (withAch, unlocked) = Achievements.evaluate(next, r)
        return RunOutcome(withAch, r.score > previousBest && r.score > 0, levelsGained, levelCoins, unlocked)
    }

    /** Débite [price] pièces si possible. */
    fun spend(p: Profile, price: Int): Profile? = if (p.coins >= price) p.copy(coins = p.coins - price) else null

    fun buyTheme(p: Profile, id: String): Profile? {
        if (id in p.ownedThemes) return p.copy(theme = id)
        val price = Themes.byId(id).price
        return spend(p, price)?.let { it.copy(ownedThemes = it.ownedThemes + id, theme = id) }
    }

    fun buySkin(p: Profile, skin: BlockSkin): Profile? {
        if (skin.id in p.ownedSkins) return p.copy(skin = skin.id)
        return spend(p, skin.price)?.let { it.copy(ownedSkins = it.ownedSkins + skin.id, skin = skin.id) }
    }

    fun buyFx(p: Profile, fx: BackgroundFx): Profile? {
        if (fx.id in p.ownedFx) return p.copy(fx = fx.id)
        return spend(p, fx.price)?.let { it.copy(ownedFx = it.ownedFx + fx.id, fx = fx.id) }
    }

    fun buyBooster(p: Profile, b: Booster): Profile? =
        spend(p, b.price)?.let { it.copy(boosters = it.boosters + (b.id to it.boosterCount(b) + 1)) }

    fun consumeBoosters(p: Profile, used: Set<Booster>): Profile =
        p.copy(boosters = p.boosters.mapValues { (id, n) -> if (used.any { it.id == id }) maxOf(0, n - 1) else n })

    fun canClaimDaily(p: Profile, today: Long) = p.dailyRewardDay != today

    /** Série du cadeau quotidien si on le réclamait aujourd'hui. */
    fun nextStreak(p: Profile, today: Long) = if (p.dailyRewardDay == today - 1) p.dailyStreak % 7 + 1 else 1

    fun claimDaily(p: Profile, today: Long): Pair<Profile, Int> {
        if (!canClaimDaily(p, today)) return p to 0
        val streak = nextStreak(p, today)
        val reward = dailyReward(streak)
        var next = p.copy(coins = p.coins + reward, dailyRewardDay = today, dailyStreak = streak)
        if (streak == 7) next = next.copy(boosters = next.boosters + (Booster.SHIELD.id to next.boosterCount(Booster.SHIELD) + 1))
        return next to reward
    }
}

// --- Succès -----------------------------------------------------------------------------------

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val reward: Int,
    val check: (Profile, RunResult?) -> Boolean,
)

object Achievements {
    val all: List<Achievement> = listOf(
        Achievement("first", "Premiers pas", "Joue ta première partie", 20) { p, _ -> p.gamesPlayed >= 1 },
        Achievement("s10", "Ça monte !", "Atteins 10 étages", 20) { p, _ -> p.best >= 10 },
        Achievement("s25", "Architecte", "Atteins 25 étages", 40) { p, _ -> p.best >= 25 },
        Achievement("s50", "Gratte-ciel", "Atteins 50 étages", 100) { p, _ -> p.best >= 50 },
        Achievement("s100", "Au-dessus des nuages", "Atteins 100 étages", 250) { p, _ -> p.best >= 100 },
        Achievement("s200", "Stratosphère", "Atteins 200 étages", 600) { p, _ -> p.best >= 200 },
        Achievement("c5", "Précision", "Fais 5 parfaits d'affilée", 30) { p, _ -> p.bestCombo >= 5 },
        Achievement("c10", "Chirurgien", "Fais 10 parfaits d'affilée", 80) { p, _ -> p.bestCombo >= 10 },
        Achievement("c25", "Machine", "Fais 25 parfaits d'affilée", 300) { p, _ -> p.bestCombo >= 25 },
        Achievement("p100", "Perfectionniste", "100 parfaits au total", 60) { p, _ -> p.totalPerfects >= 100 },
        Achievement("p1000", "Maître du parfait", "1 000 parfaits au total", 400) { p, _ -> p.totalPerfects >= 1000 },
        Achievement("g10", "Habitué", "Joue 10 parties", 30) { p, _ -> p.gamesPlayed >= 10 },
        Achievement("g50", "Accro", "Joue 50 parties", 100) { p, _ -> p.gamesPlayed >= 50 },
        Achievement("g250", "Légende", "Joue 250 parties", 400) { p, _ -> p.gamesPlayed >= 250 },
        Achievement("b1000", "Bâtisseur", "Pose 1 000 blocs", 80) { p, _ -> p.totalBlocks >= 1000 },
        Achievement("b10000", "Empire", "Pose 10 000 blocs", 500) { p, _ -> p.totalBlocks >= 10000 },
        Achievement("l5", "Niveau 5", "Atteins le niveau 5", 50) { p, _ -> p.level >= 5 },
        Achievement("l10", "Niveau 10", "Atteins le niveau 10", 120) { p, _ -> p.level >= 10 },
        Achievement("l25", "Niveau 25", "Atteins le niveau 25", 400) { p, _ -> p.level >= 25 },
        Achievement("chrono40", "Contre la montre", "40 étages en mode Chrono", 120) { p, _ -> p.bestFor(GameMode.CHRONO) >= 40 },
        Achievement("hard25", "Expert", "25 étages en mode Expert", 150) { p, _ -> p.bestFor(GameMode.HARD) >= 25 },
        Achievement("zen150", "Sérénité", "150 étages en mode Zen", 100) { p, _ -> p.bestFor(GameMode.ZEN) >= 150 },
        Achievement("daily", "Défi relevé", "Termine un défi du jour", 40) { _, r -> r?.mode == GameMode.DAILY },
        Achievement("themes3", "Décorateur", "Possède 3 thèmes", 60) { p, _ -> p.ownedThemes.size >= 3 },
        Achievement("themes8", "Collectionneur", "Possède 8 thèmes", 250) { p, _ -> p.ownedThemes.size >= 8 },
        Achievement("rich", "Fortune", "Possède 5 000 pièces", 200) { p, _ -> p.coins >= 5000 },
    )

    fun byId(id: String) = all.firstOrNull { it.id == id }

    /** Débloque les succès remplis et crédite leurs récompenses. */
    fun evaluate(p: Profile, run: RunResult? = null): Pair<Profile, List<Achievement>> {
        var cur = p
        val unlocked = ArrayList<Achievement>()
        // On boucle car une récompense peut débloquer « Fortune ».
        do {
            val fresh = all.filter { it.id !in cur.achievements && it.check(cur, run) }
            fresh.forEach { a ->
                cur = cur.copy(achievements = cur.achievements + a.id, coins = cur.coins + a.reward)
            }
            unlocked += fresh
        } while (fresh.isNotEmpty())
        return cur to unlocked
    }
}

// --- Missions du jour ---------------------------------------------------------------------------

enum class MissionType { GAMES, SCORE, PERFECTS, COMBO, BLOCKS, MODE_ZEN, MODE_DAILY }

data class Mission(val id: String, val type: MissionType, val target: Int, val reward: Int) {
    val title: String
        get() = when (type) {
            MissionType.GAMES -> "Joue $target parties"
            MissionType.SCORE -> "Atteins $target étages en une partie"
            MissionType.PERFECTS -> "Réussis $target parfaits"
            MissionType.COMBO -> "Fais un combo de $target"
            MissionType.BLOCKS -> "Pose $target blocs"
            MissionType.MODE_ZEN -> "Joue une partie en mode Zen"
            MissionType.MODE_DAILY -> "Joue le défi du jour"
        }
}

object Missions {
    fun forDay(day: Long): List<Mission> {
        val rnd = Random(day * 7919 + 17)
        val types = MissionType.entries.shuffled(rnd).take(3)
        return types.mapIndexed { i, t ->
            val tier = rnd.nextInt(3)
            val (target, reward) = when (t) {
                MissionType.GAMES -> listOf(3 to 40, 5 to 60, 8 to 90)[tier]
                MissionType.SCORE -> listOf(15 to 40, 25 to 70, 40 to 120)[tier]
                MissionType.PERFECTS -> listOf(10 to 40, 20 to 70, 35 to 110)[tier]
                MissionType.COMBO -> listOf(4 to 40, 6 to 70, 9 to 120)[tier]
                MissionType.BLOCKS -> listOf(60 to 40, 120 to 70, 200 to 110)[tier]
                MissionType.MODE_ZEN -> 1 to 40
                MissionType.MODE_DAILY -> 1 to 50
            }
            Mission("${day}_$i", t, target, reward)
        }
    }

    fun progress(p: Profile, m: Mission) = minOf(m.target, p.missionProgress[m.id] ?: 0)

    /** Remet à zéro si on a changé de jour. */
    fun ensureDay(p: Profile, day: Long): Profile =
        if (p.missionsDay == day) p else p.copy(missionsDay = day, missionProgress = emptyMap(), missionsClaimed = emptySet())

    fun track(p0: Profile, r: RunResult): Profile {
        val p = ensureDay(p0, r.day)
        val progress = p.missionProgress.toMutableMap()
        for (m in forDay(r.day)) {
            val cur = progress[m.id] ?: 0
            progress[m.id] = when (m.type) {
                MissionType.GAMES -> cur + 1
                MissionType.SCORE -> maxOf(cur, r.score)
                MissionType.PERFECTS -> cur + r.perfects
                MissionType.COMBO -> maxOf(cur, r.bestCombo)
                MissionType.BLOCKS -> cur + r.score
                MissionType.MODE_ZEN -> if (r.mode == GameMode.ZEN) cur + 1 else cur
                MissionType.MODE_DAILY -> if (r.mode == GameMode.DAILY) cur + 1 else cur
            }
        }
        return p.copy(missionProgress = progress)
    }

    fun claim(p: Profile, m: Mission): Profile? {
        if (m.id in p.missionsClaimed || progress(p, m) < m.target) return null
        return p.copy(coins = p.coins + m.reward, missionsClaimed = p.missionsClaimed + m.id)
    }
}

// --- Roue de la fortune -------------------------------------------------------------------------

data class WheelPrize(val label: String, val coins: Int = 0, val booster: Booster? = null, val weight: Int, val color: Long)

object Wheel {
    const val MAX_AD_SPINS = 3

    val prizes = listOf(
        WheelPrize("25", coins = 25, weight = 22, color = 0xFF5B6CFF),
        WheelPrize("Ralenti", booster = Booster.SLOW, weight = 12, color = 0xFF00BFA5),
        WheelPrize("50", coins = 50, weight = 20, color = 0xFFFF6D8A),
        WheelPrize("Bouclier", booster = Booster.SHIELD, weight = 10, color = 0xFFFFB300),
        WheelPrize("100", coins = 100, weight = 14, color = 0xFF7C4DFF),
        WheelPrize("Départ", booster = Booster.HEAD_START, weight = 10, color = 0xFF29B6F6),
        WheelPrize("250", coins = 250, weight = 9, color = 0xFFFF7043),
        WheelPrize("1000", coins = 1000, weight = 3, color = 0xFFFFD54F),
    )

    fun pick(random: Random): Int {
        var r = random.nextInt(prizes.sumOf { it.weight })
        prizes.forEachIndexed { i, p ->
            r -= p.weight
            if (r < 0) return i
        }
        return 0
    }

    fun ensureDay(p: Profile, day: Long) =
        if (p.wheelDay == day) p else p.copy(wheelDay = day, wheelFreeUsed = false, wheelAdSpins = 0)

    fun hasFreeSpin(p: Profile, day: Long) = p.wheelDay != day || !p.wheelFreeUsed
    fun adSpinsLeft(p: Profile, day: Long) = if (p.wheelDay != day) MAX_AD_SPINS else MAX_AD_SPINS - p.wheelAdSpins

    fun grant(p0: Profile, day: Long, index: Int, viaAd: Boolean): Profile {
        val p = ensureDay(p0, day)
        val prize = prizes[index]
        var next = if (viaAd) p.copy(wheelAdSpins = p.wheelAdSpins + 1) else p.copy(wheelFreeUsed = true)
        next = next.copy(coins = next.coins + prize.coins)
        prize.booster?.let { b -> next = next.copy(boosters = next.boosters + (b.id to next.boosterCount(b) + 1)) }
        return next
    }
}
