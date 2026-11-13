package com.kimizha.minigame.sudoku

import java.io.*
import java.util.zip.CRC32

/** Separate from the marble game's versioned save format and filenames. */
object SudokuCodec {
    private const val MAGIC = 0x53554431
    fun encode(game: SudokuGame): ByteArray {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { out ->
            out.writeInt(1); out.writeInt(game.puzzle.difficulty.ordinal); out.writeLong(game.puzzle.seed)
            game.puzzle.givens.forEach { out.writeByte(it) }; game.puzzle.solution.forEach { out.writeByte(it) }
            out.state(BoardState(game.values, game.notes, game.selected))
            out.writeBoolean(game.noteMode); out.writeLong(game.elapsedMillis); out.writeInt(game.hintsUsed)
            for (stack in listOf(game.undo, game.redo)) {
                out.writeInt(stack.size)
                for (move in stack) { out.state(move.before); out.state(move.after) }
            }
        }
        val payload = buffer.toByteArray()
        return ByteArrayOutputStream().also { dest ->
            DataOutputStream(dest).use { out ->
                out.writeInt(MAGIC); out.writeLong(CRC32().apply { update(payload) }.value); out.write(payload)
            }
        }.toByteArray()
    }
    fun decode(bytes: ByteArray): SudokuGame? = try {
        require(bytes.size in 200..250_000)
        val header = DataInputStream(ByteArrayInputStream(bytes)); require(header.readInt() == MAGIC)
        val crc = header.readLong(); val payload = header.readBytes()
        require(CRC32().apply { update(payload) }.value == crc)
        DataInputStream(ByteArrayInputStream(payload)).use { input ->
            require(input.readInt() == 1)
            val difficulty = Difficulty.entries[input.readInt()]; val seed = input.readLong()
            val givens = IntArray(81) { input.readUnsignedByte() }; val solution = IntArray(81) { input.readUnsignedByte() }
            val puzzle = SudokuPuzzle(givens, solution, difficulty, seed)
            require(SudokuSolver.countSolutions(givens) == 1)
            val game = SudokuGame(puzzle); val state = input.state(puzzle)
            state.values.copyInto(game.values); state.notes.copyInto(game.notes); game.selected = state.selected
            game.noteMode = input.readBoolean(); game.elapsedMillis = input.readLong().also { require(it >= 0) }
            game.hintsUsed = input.readInt().also { require(it in 0..1_000_000) }
            for (stack in listOf(game.undo, game.redo)) {
                val count = input.readInt(); require(count in 0..100)
                repeat(count) { stack.addLast(Move(input.state(puzzle), input.state(puzzle))) }
            }
            require(game.undo.size + game.redo.size <= 100)
            require(input.available() == 0)
            game
        }
    } catch (_: Exception) { null }
    private fun DataOutputStream.state(state: BoardState) {
        state.values.forEach { writeByte(it) }; state.notes.forEach { writeShort(it) }; writeInt(state.selected)
    }
    private fun DataInputStream.state(puzzle: SudokuPuzzle): BoardState {
        val values = IntArray(81) { readUnsignedByte() }; val notes = IntArray(81) { readUnsignedShort() }
        val selected = readInt(); require(selected in -1..80)
        require(values.indices.all { i ->
            values[i] in 0..9 && (puzzle.givens[i] == 0 || values[i] == puzzle.givens[i]) &&
                notes[i] in 0..SudokuSolver.ALL && (values[i] == 0 || notes[i] == 0)
        })
        return BoardState(values, notes, selected)
    }
}
