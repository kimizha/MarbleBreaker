package com.kimizha.minigame.sudoku

import java.util.Random
import java.util.ArrayDeque
import java.util.concurrent.CancellationException

enum class Difficulty(val label: String, val clues: Int) {
    EASY("简单", 42), MEDIUM("中等", 34), HARD("困难", 28)
}

class SudokuPuzzle(val givens: IntArray, val solution: IntArray, val difficulty: Difficulty, val seed: Long) {
    init {
        require(givens.size == 81 && solution.size == 81)
        require(SudokuSolver.isComplete(solution))
        require(givens.indices.all { givens[it] in 0..9 && (givens[it] == 0 || givens[it] == solution[it]) })
    }
}

/** Bit masks + minimum remaining values, with an early exit after two solutions. */
object SudokuSolver {
    const val ALL = 0x1FF
    fun peers(a: Int, b: Int): Boolean = a / 9 == b / 9 || a % 9 == b % 9 || (a / 27 == b / 27 && a % 9 / 3 == b % 9 / 3)
    fun isComplete(board: IntArray) = board.size == 81 && board.all { it in 1..9 } &&
        board.indices.all { a -> (a + 1 until 81).none { b -> board[a] == board[b] && peers(a, b) } }
    fun countSolutions(input: IntArray, limit: Int = 2): Int {
        require(limit > 0)
        if (input.size != 81 || input.any { it !in 0..9 }) return 0
        val board = input.copyOf(); val rows = IntArray(9); val cols = IntArray(9); val boxes = IntArray(9)
        for (i in board.indices) if (board[i] != 0) {
            val r = i / 9; val c = i % 9; val box = r / 3 * 3 + c / 3; val bit = 1 shl (board[i] - 1)
            if ((rows[r] or cols[c] or boxes[box]) and bit != 0) return 0
            rows[r] = rows[r] or bit; cols[c] = cols[c] or bit; boxes[box] = boxes[box] or bit
        }
        var total = 0
        fun search() {
            if (Thread.currentThread().isInterrupted) throw CancellationException()
            if (total >= limit) return
            var cell = -1; var candidates = 0; var minimum = 10
            for (i in board.indices) if (board[i] == 0) {
                val r = i / 9; val c = i % 9
                val mask = ALL and (rows[r] or cols[c] or boxes[r / 3 * 3 + c / 3]).inv()
                val count = Integer.bitCount(mask)
                if (count == 0) return
                if (count < minimum) { cell = i; candidates = mask; minimum = count; if (count == 1) break }
            }
            if (cell == -1) { total++; return }
            val r = cell / 9; val c = cell % 9; val box = r / 3 * 3 + c / 3
            while (candidates != 0 && total < limit) {
                val bit = candidates and -candidates; candidates = candidates xor bit
                board[cell] = Integer.numberOfTrailingZeros(bit) + 1
                rows[r] = rows[r] or bit; cols[c] = cols[c] or bit; boxes[box] = boxes[box] or bit
                search()
                rows[r] = rows[r] xor bit; cols[c] = cols[c] xor bit; boxes[box] = boxes[box] xor bit
                board[cell] = 0
            }
        }
        search(); return total
    }
}

object SudokuGenerator {
    fun generate(difficulty: Difficulty, seed: Long): SudokuPuzzle {
        val random = Random(seed)
        fun shuffled(range: IntRange) = range.toMutableList().also { java.util.Collections.shuffle(it, random) }
        var best: SudokuPuzzle? = null
        repeat(5) {
            val rows = shuffled(0..2).flatMap { band -> shuffled(0..2).map { band * 3 + it } }
            val cols = shuffled(0..2).flatMap { stack -> shuffled(0..2).map { stack * 3 + it } }
            val digits = shuffled(1..9)
            val solution = IntArray(81) { i -> digits[(rows[i / 9] * 3 + rows[i / 9] / 3 + cols[i % 9]) % 9] }
            val givens = solution.copyOf(); var count = 81
            for (i in shuffled(0..80)) {
                if (Thread.currentThread().isInterrupted) throw CancellationException()
                if (count <= difficulty.clues) break
                val old = givens[i]; givens[i] = 0
                if (SudokuSolver.countSolutions(givens) == 1) count-- else givens[i] = old
            }
            val puzzle = SudokuPuzzle(givens, solution, difficulty, seed)
            if (best == null || count < best!!.givens.count { it > 0 }) best = puzzle
            if (count == difficulty.clues) return puzzle
        }
        return best!!
    }
}

data class BoardState(val values: IntArray, val notes: IntArray, val selected: Int)
data class Move(val before: BoardState, val after: BoardState)
class SudokuGame(val puzzle: SudokuPuzzle) {
    val values = puzzle.givens.copyOf()
    val notes = IntArray(81)
    var selected = puzzle.givens.indexOfFirst { it == 0 }
    var noteMode = false
    var elapsedMillis = 0L
    var hintsUsed = 0
    val undo = ArrayDeque<Move>()
    val redo = ArrayDeque<Move>()
    val complete get() = values.contentEquals(puzzle.solution)
    val emptyCount get() = values.count { it == 0 }
    fun select(cell: Int): Boolean {
        if (cell !in 0..80) return false
        selected = cell; return true
    }
    fun editable(cell: Int = selected) = cell in 0..80 && puzzle.givens[cell] == 0 && !complete
    private fun snapshot() = BoardState(values.copyOf(), notes.copyOf(), selected)
    private fun restore(state: BoardState) {
        state.values.copyInto(values); state.notes.copyInto(notes); selected = state.selected
    }
    private fun change(action: () -> Unit): Boolean {
        val before = snapshot(); action(); val after = snapshot()
        if (before.values.contentEquals(after.values) && before.notes.contentEquals(after.notes)) return false
        undo.addLast(Move(before, after)); if (undo.size > 100) undo.removeFirst(); redo.clear(); return true
    }
    private fun fill(cell: Int, number: Int) {
        values[cell] = number; notes[cell] = 0
        val bit = 1 shl (number - 1)
        for (i in notes.indices) if (SudokuSolver.peers(cell, i)) notes[i] = notes[i] and bit.inv()
    }
    fun enter(number: Int): Boolean {
        if (number !in 1..9 || !editable()) return false
        if (noteMode && values[selected] != 0) return false
        return change {
            if (noteMode) notes[selected] = notes[selected] xor (1 shl (number - 1)) else fill(selected, number)
        }
    }
    fun erase(): Boolean = editable() && change { values[selected] = 0; notes[selected] = 0 }
    fun undoMove(): Boolean {
        val move = undo.pollLast() ?: return false
        restore(move.before); redo.addLast(move); return true
    }
    fun redoMove(): Boolean {
        val move = redo.pollLast() ?: return false
        restore(move.after); undo.addLast(move); return true
    }
    fun hint(): Int {
        if (complete) return -1
        val cell = if (editable() && values[selected] != puzzle.solution[selected]) selected
            else values.indices.firstOrNull { values[it] != puzzle.solution[it] } ?: return -1
        change { selected = cell; fill(cell, puzzle.solution[cell]) }
        hintsUsed++; return cell
    }
    fun conflicts(): BooleanArray {
        val result = BooleanArray(81)
        for (a in values.indices) if (values[a] > 0)
            for (b in a + 1 until 81) if (values[a] == values[b] && SudokuSolver.peers(a, b)) {
                result[a] = true; result[b] = true
            }
        return result
    }
    fun wrongCells() = BooleanArray(81) { values[it] != 0 && values[it] != puzzle.solution[it] }
}
