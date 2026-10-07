package com.bommer.stacktower.game

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * Effets sonores synthétisés au démarrage (aucun fichier audio à fournir ni de licence à gérer).
 */
class SoundFx(context: Context) {

    private val pool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        ).build()

    private val place: Int
    private val fail: Int
    private val coin: Int
    private val click: Int
    private val levelUp: Int
    private val tick: Int
    private val perfect: IntArray
    private val musicPath: String
    private var music: MediaPlayer? = null

    var enabled = true

    /** Musique d'ambiance activée (réglage joueur). */
    var musicEnabled = false
        set(value) {
            field = value
            refreshMusic()
        }

    /** Faux quand l'activité est en arrière-plan. */
    var foreground = false
        set(value) {
            field = value
            refreshMusic()
        }

    init {
        val dir = File(context.cacheDir, "sfx").apply { mkdirs() }
        place = pool.load(write(dir, "place", tone(220.0, 0.09, decay = 40.0, noise = 0.25)), 1)
        fail = pool.load(write(dir, "fail", sweep(330.0, 90.0, 0.45)), 1)
        coin = pool.load(write(dir, "coin", chime(listOf(988.0, 1319.0), 0.08)), 1)
        click = pool.load(write(dir, "click", tone(660.0, 0.05, decay = 60.0)), 1)
        tick = pool.load(write(dir, "tick", tone(1200.0, 0.025, decay = 120.0, noise = 0.3)), 1)
        levelUp = pool.load(write(dir, "levelup", chime(listOf(523.25, 659.25, 783.99, 1046.5), 0.09)), 1)
        musicPath = write(dir, "music", ambientLoop())
        // Gamme pentatonique : la note monte avec le combo.
        val scale = doubleArrayOf(523.25, 587.33, 659.25, 783.99, 880.0, 1046.5, 1174.66, 1318.51, 1567.98, 1760.0)
        perfect = IntArray(scale.size) { i ->
            pool.load(write(dir, "perfect$i", tone(scale[i], 0.22, decay = 12.0, harmonics = true)), 1)
        }
    }

    fun place() = play(place, 0.7f)
    fun fail() = play(fail, 0.8f)
    fun coin() = play(coin, 0.6f)
    fun click() = play(click, 0.5f)
    fun tick() = play(tick, 0.35f)
    fun levelUp() = play(levelUp, 0.8f)
    fun perfect(combo: Int) = play(perfect[min(combo - 1, perfect.size - 1).coerceAtLeast(0)], 0.8f)

    private fun play(id: Int, vol: Float) {
        if (enabled) pool.play(id, vol, vol, 1, 0, 1f)
    }

    private fun refreshMusic() {
        val shouldPlay = musicEnabled && foreground
        if (shouldPlay) {
            val player = music ?: runCatching {
                MediaPlayer().apply {
                    setDataSource(musicPath)
                    isLooping = true
                    setVolume(0.35f, 0.35f)
                    prepare()
                }
            }.getOrNull()?.also { music = it }
            if (player != null && !player.isPlaying) player.start()
        } else {
            music?.let { if (it.isPlaying) it.pause() }
        }
    }

    fun release() {
        pool.release()
        music?.release()
        music = null
    }

    /** Boucle d'ambiance douce : nappe d'accords + arpège (Lam - Fa - Do - Sol). */
    private fun ambientLoop(): ShortArray {
        val chords = listOf(
            doubleArrayOf(220.0, 261.63, 329.63),
            doubleArrayOf(174.61, 220.0, 261.63),
            doubleArrayOf(261.63, 329.63, 392.0),
            doubleArrayOf(196.0, 246.94, 293.66),
        )
        val chordDur = 2.4
        val chordLen = (rate * chordDur).toInt()
        val out = DoubleArray(chordLen * chords.size)
        chords.forEachIndexed { c, notes ->
            val base = c * chordLen
            for (i in 0 until chordLen) {
                val t = i.toDouble() / rate
                val env = min(1.0, t / 0.6) * min(1.0, (chordDur - t) / 0.6)
                var s = 0.0
                for (f in notes) s += sin(2 * PI * f * t) + 0.3 * sin(2 * PI * f * 2 * t + 0.5)
                out[base + i] += s * env * 0.09
            }
            // Arpège : 8 notes par accord
            val step = chordLen / 8
            for (k in 0 until 8) {
                val f = notes[k % notes.size] * if (k >= 4) 4.0 else 2.0
                for (i in 0 until (rate * 0.35).toInt()) {
                    val j = base + k * step + i
                    if (j >= out.size) break
                    val t = i.toDouble() / rate
                    out[j] += sin(2 * PI * f * t) * exp(-t * 9.0) * min(1.0, t / 0.005) * 0.12
                }
            }
        }
        return ShortArray(out.size) { (out[it].coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort() }
    }

    // --- Synthèse -------------------------------------------------------------



    private fun tone(
        freq: Double, dur: Double, decay: Double, noise: Double = 0.0, harmonics: Boolean = false,
    ): ShortArray {
        val n = (rate * dur).toInt()
        val rnd = java.util.Random(7)
        return ShortArray(n) { i ->
            val t = i.toDouble() / rate
            var s = sin(2 * PI * freq * t)
            if (harmonics) s = 0.7 * s + 0.2 * sin(4 * PI * freq * t) + 0.1 * sin(6 * PI * freq * t)
            if (noise > 0) s = s * (1 - noise) + (rnd.nextDouble() * 2 - 1) * noise
            val attack = min(1.0, t / 0.004)
            (s * attack * exp(-t * decay) * 0.8 * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun sweep(from: Double, to: Double, dur: Double): ShortArray {
        val n = (rate * dur).toInt()
        var phase = 0.0
        return ShortArray(n) { i ->
            val p = i.toDouble() / n
            val f = from + (to - from) * p
            phase += 2 * PI * f / rate
            val sq = if (sin(phase) >= 0) 1.0 else -1.0
            ((0.4 * sin(phase) + 0.15 * sq) * (1 - p) * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun chime(freqs: List<Double>, step: Double): ShortArray {
        val parts = freqs.map { tone(it, 0.18, decay = 18.0, harmonics = true) }
        val offset = (rate * step).toInt()
        val out = ShortArray(offset * (freqs.size - 1) + parts.last().size)
        parts.forEachIndexed { k, part ->
            part.forEachIndexed { i, v ->
                val j = k * offset + i
                out[j] = (out[j] + v / 2).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }
        return out
    }

    private fun write(dir: File, name: String, pcm: ShortArray): String {
        val f = File(dir, "$name.wav")
        val data = ByteBuffer.allocate(44 + pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        data.put("RIFF".toByteArray()).putInt(36 + pcm.size * 2).put("WAVE".toByteArray())
        data.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
            .putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
        data.put("data".toByteArray()).putInt(pcm.size * 2)
        pcm.forEach { data.putShort(it) }
        FileOutputStream(f).use { it.write(data.array()) }
        return f.absolutePath
    }

    private companion object {
        const val rate = 22050
    }
}
