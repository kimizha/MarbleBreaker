package com.kimizha.minigame.sudoku

import android.content.Context
import android.util.AtomicFile
import java.io.File

class SudokuStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "sudoku-v1.dat"))
    fun load(): SudokuGame? = runCatching { SudokuCodec.decode(file.readFully()) }.getOrNull()
    fun save(game: SudokuGame): Boolean {
        val bytes = SudokuCodec.encode(game)
        val stream = runCatching { file.startWrite() }.getOrNull() ?: return false
        return try { stream.write(bytes); file.finishWrite(stream); true }
        catch (_: Exception) { file.failWrite(stream); false }
    }
}
