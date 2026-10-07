package com.bommer.stacktower

import com.bommer.stacktower.data.Achievements
import com.bommer.stacktower.data.Missions
import com.bommer.stacktower.data.Profile
import com.bommer.stacktower.data.Progression
import com.bommer.stacktower.data.RunResult
import com.bommer.stacktower.data.Wheel
import com.bommer.stacktower.game.BlockSkin
import com.bommer.stacktower.game.Booster
import com.bommer.stacktower.game.GameMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ProfileTest {

    private fun run(score: Int, mode: GameMode = GameMode.CLASSIC, day: Long = 100) =
        RunResult(mode, score, perfects = 5, bestCombo = 6, coins = 10, xp = score + 10, playTimeSec = 30, day = day)

    @Test
    fun levelsFollowXpCurve() {
        assertEquals(1, Progression.levelFor(0))
        assertEquals(2, Progression.levelFor(100))
        assertEquals(3, Progression.levelFor(100 + 160))
    }

    @Test
    fun runUpdatesStatsBestAndAchievements() {
        val out = Progression.applyRun(Profile(), run(30))
        val p = out.profile
        assertEquals(30, p.bestFor(GameMode.CLASSIC))
        assertEquals(1, p.gamesPlayed)
        assertTrue(out.newBest)
        val ids = out.achievements.map { it.id }
        assertTrue("first" in ids && "s10" in ids && "s25" in ids && "c5" in ids)
        // Pièces : partie + récompenses de succès + niveau
        val achCoins = out.achievements.sumOf { it.reward }
        assertEquals(10 + achCoins + out.levelCoins, p.coins)
    }

    @Test
    fun achievementsAreGrantedOnce() {
        val p = Progression.applyRun(Profile(), run(12)).profile
        val again = Achievements.evaluate(p).second
        assertTrue(again.isEmpty())
    }

    @Test
    fun shopRefusesWhenTooPoor() {
        assertNull(Progression.buySkin(Profile(coins = 10), BlockSkin.GEM))
        val p = Progression.buySkin(Profile(coins = 5000), BlockSkin.GEM)!!
        assertEquals(5000 - BlockSkin.GEM.price, p.coins)
        assertEquals(BlockSkin.GEM.id, p.skin)
    }

    @Test
    fun dailyStreakCyclesAndResets() {
        var p = Profile()
        var reward: Int
        for (d in 1L..7L) {
            val r = Progression.claimDaily(p, d)
            p = r.first; reward = r.second
            assertEquals(Progression.dailyReward(d.toInt()), reward)
        }
        assertEquals(0, Progression.claimDaily(p, 7).second)
        assertEquals(1, Progression.claimDaily(p, 9).first.dailyStreak)
    }

    @Test
    fun missionsProgressAndClaim() {
        val day = 42L
        val missions = Missions.forDay(day)
        assertEquals(3, missions.size)
        var p = Profile()
        repeat(10) { p = Progression.applyRun(p, run(50, GameMode.ZEN, day)).profile }
        p = Progression.applyRun(p, run(50, GameMode.DAILY, day)).profile
        for (m in missions) {
            val before = p.coins
            p = Missions.claim(p, m)!!
            assertEquals(before + m.reward, p.coins)
            assertNull(Missions.claim(p, m))
        }
    }

    @Test
    fun wheelGivesPrizeAndLimitsSpins() {
        var p = Profile()
        val day = 5L
        assertTrue(Wheel.hasFreeSpin(p, day))
        val i = Wheel.pick(Random(3))
        p = Wheel.grant(p, day, i, viaAd = false)
        assertTrue(!Wheel.hasFreeSpin(p, day))
        assertEquals(Wheel.MAX_AD_SPINS, Wheel.adSpinsLeft(p, day))
        p = Wheel.grant(p, day, 0, viaAd = true)
        assertEquals(Wheel.MAX_AD_SPINS - 1, Wheel.adSpinsLeft(p, day))
        assertTrue(Wheel.hasFreeSpin(p, day + 1))
    }

    @Test
    fun boostersAreConsumed() {
        val p = Profile(boosters = mapOf(Booster.SHIELD.id to 2, Booster.SLOW.id to 1))
        val q = Progression.consumeBoosters(p, setOf(Booster.SHIELD))
        assertEquals(1, q.boosterCount(Booster.SHIELD))
        assertEquals(1, q.boosterCount(Booster.SLOW))
    }
}
