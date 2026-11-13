package com.kimizha.minigame

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.*
import kotlin.math.*

/** Original PCM synthesis. No downloaded assets, network or third-party music. */
class Audio(context: Context, private val store: LocalStore) {
    private val pool = SoundPool.Builder().setMaxStreams(8).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val ids = mutableMapOf<String, Int>()
    private val loaded = mutableSetOf<Int>()
    private var musicStream = 0
    private var foreground = true
    private var lastHit = 0L
    init {
        pool.setOnLoadCompleteListener { _, id, status ->
            if (status == 0) { loaded.add(id); updateMusic() }
        }
        val tones = mapOf("shot" to 730.0, "hit" to 420.0, "break" to 1050.0,
            "power" to 1320.0, "combo" to 1580.0, "win" to 660.0, "music" to 220.0)
        for ((name, frequency) in tones) {
            val path = File(context.cacheDir, "original-$name.wav")
            path.writeBytes(synthesize(name, frequency))
            ids[name] = pool.load(path.path, 1)
        }
    }
    fun play(name: String) {
        if (!store.sound || !foreground) return
        val now = System.nanoTime()
        if (name == "hit" && now - lastHit < 35_000_000) return
        if (name == "hit") lastHit = now
        val id = ids[name] ?: return
        if (id in loaded) pool.play(id, .32f, .32f, 1, 0, 1f)
    }
    fun active(value: Boolean) { foreground = value; if (!value) pool.autoPause() else pool.autoResume(); updateMusic() }
    fun updateMusic() {
        val id = ids["music"]
        if (store.music && foreground && id != null && id in loaded) {
            if (musicStream == 0) musicStream = pool.play(id, .12f, .12f, 0, -1, 1f)
        } else if (musicStream != 0) { pool.stop(musicStream); musicStream = 0 }
    }
    fun release() = pool.release()
    private fun synthesize(name: String, base: Double): ByteArray {
        val rate = 22050
        val duration = when (name) { "music" -> 2.4; "win" -> .65; "power" -> .20; "break" -> .12; else -> .055 }
        val count = (rate * duration).toInt()
        val samples = ByteArray(count * 2)
        val notes = doubleArrayOf(1.0, 1.25, 1.5, 2.0, 1.5, 1.25)
        for (n in 0 until count) {
            val t = n.toDouble() / rate
            val music = name == "music" || name == "win"
            val length = if (name == "music") .4 else .108
            val note = if (music) notes[(t / length).toInt().coerceAtMost(5)] else 1.0
            val envelope = if (music) exp(-8 * (t % length)) * min(1.0, (t % length) * 90)
                else exp(-t / (duration * .22)) * min(1.0, t * 600)
            val frequency = base * note * if (name == "break") (1.0 + t * 2) else 1.0
            val signal = (sin(2 * PI * frequency * t) + .22 * sin(4 * PI * frequency * t)) * envelope
            val sample = (signal * 11000 * min(1.0, (duration - t) * 100)).toInt().coerceIn(-32767, 32767)
            samples[n * 2] = sample.toByte(); samples[n * 2 + 1] = (sample shr 8).toByte()
        }
        val out = ByteArrayOutputStream()
        fun intLE(value: Int, width: Int) { repeat(width) { out.write(value shr (it * 8) and 255) } }
        out.write("RIFF".toByteArray()); intLE(36 + samples.size, 4); out.write("WAVEfmt ".toByteArray())
        intLE(16, 4); intLE(1, 2); intLE(1, 2); intLE(rate, 4); intLE(rate * 2, 4); intLE(2, 2); intLE(16, 2)
        out.write("data".toByteArray()); intLE(samples.size, 4); out.write(samples)
        return out.toByteArray()
    }
}
