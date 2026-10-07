package com.bommer.stacktower.game

import android.content.Context
import android.media.AudioAttributes
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
    private val perfect: IntArray

    var enabled = true

    init {
        val dir = File(context.cacheDir, "sfx").apply { mkdirs() }
        place = pool.load(write(dir, "place", tone(220.0, 0.09, decay = 40.0, noise = 0.25)), 1)
        fail = pool.load(write(dir, "fail", sweep(330.0, 90.0, 0.45)), 1)
        coin = pool.load(write(dir, "coin", chime(listOf(988.0, 1319.0), 0.08)), 1)
        // Gamme pentatonique : la note monte avec le combo.
        val scale = doubleArrayOf(523.25, 587.33, 659.25, 783.99, 880.0, 1046.5, 1174.66, 1318.51, 1567.98, 1760.0)
        perfect = IntArray(scale.size) { i ->
            pool.load(write(dir, "perfect$i", tone(scale[i], 0.22, decay = 12.0, harmonics = true)), 1)
        }
    }

    fun place() = play(place, 0.7f)
    fun fail() = play(fail, 0.8f)
    fun coin() = play(coin, 0.6f)
    fun perfect(combo: Int) = play(perfect[min(combo - 1, perfect.size - 1).coerceAtLeast(0)], 0.8f)

    private fun play(id: Int, vol: Float) {
        if (enabled) pool.play(id, vol, vol, 1, 0, 1f)
    }

    fun release() = pool.release()

    // --- Synthèse -------------------------------------------------------------

    private val rate = 22050

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
}
