package com.kimizha.minigame

import android.content.Context
import android.util.AtomicFile
import com.kimizha.minigame.core.*
import java.io.File

class LocalStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "game-v1.dat"))
    private val prefs = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
    var sound: Boolean
        get() = prefs.getBoolean("sound", true)
        set(v) { prefs.edit().putBoolean("sound", v).apply() }
    var music: Boolean
        get() = prefs.getBoolean("music", false)
        set(v) { prefs.edit().putBoolean("music", v).apply() }
    var vibration: Boolean
        get() = prefs.getBoolean("vibration", true)
        set(v) { prefs.edit().putBoolean("vibration", v).apply() }
    var lowPerformance: Boolean
        get() = prefs.getBoolean("lowPerformance", false)
        set(v) { prefs.edit().putBoolean("lowPerformance", v).apply() }
    private val progressFile = AtomicFile(File(context.filesDir, "progress-v1.dat"))
    fun load(): SaveData {
        val saved = runCatching { SaveCodec.decode(file.readFully()) }.getOrNull()
        val backup = runCatching { SaveCodec.decode(progressFile.readFully()) }.getOrNull()
        if (saved == null) return backup ?: SaveData(Progress(), null)
        if (backup != null) {
            saved.progress.unlocked = maxOf(saved.progress.unlocked, backup.progress.unlocked)
            saved.progress.best = maxOf(saved.progress.best, backup.progress.best)
        }
        return saved
    }
    fun save(progress: Progress, engine: Engine?): Boolean {
        // Independent monotonic progress file protects completed levels from a damaged run snapshot.
        val progressOk = atomicWrite(progressFile, SaveCodec.encode(SaveData(progress, null)))
        return atomicWrite(file, SaveCodec.encode(SaveData(progress, engine))) && progressOk
    }
    private fun atomicWrite(target: AtomicFile, bytes: ByteArray): Boolean {
        val stream = runCatching { target.startWrite() }.getOrNull() ?: return false
        return try { stream.write(bytes); target.finishWrite(stream); true }
        catch (_: Exception) { target.failWrite(stream); false }
    }
}
