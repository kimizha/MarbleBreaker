package com.kimizha.minigame.sudoku

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.CancellationException

class SudokuTest {
    private fun game(seed: Long = 17) = SudokuGame(SudokuGenerator.generate(Difficulty.EASY, seed))
    @Test fun allDifficultiesGenerateValidUniquePuzzles() {
        for (difficulty in Difficulty.entries) for (seed in 1L..8L) {
            val p = SudokuGenerator.generate(difficulty, seed)
            assertTrue(SudokuSolver.isComplete(p.solution))
            assertEquals(1, SudokuSolver.countSolutions(p.givens))
            assertTrue(p.givens.count { it != 0 } in difficulty.clues..(difficulty.clues + 4))
            assertTrue(p.givens.indices.all { p.givens[it] == 0 || p.givens[it] == p.solution[it] })
        }
    }
    @Test fun seededGenerationIsRepeatableAndVariesAcrossSeeds() {
        val a = SudokuGenerator.generate(Difficulty.MEDIUM, 91)
        val b = SudokuGenerator.generate(Difficulty.MEDIUM, 91)
        val c = SudokuGenerator.generate(Difficulty.MEDIUM, 92)
        assertArrayEquals(a.givens, b.givens); assertArrayEquals(a.solution, b.solution)
        assertFalse(a.givens.contentEquals(c.givens))
    }
    @Test fun solverRejectsContradictionsAndCapsMultipleSolutions() {
        val input = IntArray(81); input[0] = 7; input[1] = 7
        assertEquals(0, SudokuSolver.countSolutions(input))
        assertEquals(2, SudokuSolver.countSolutions(IntArray(81)))
        assertEquals(0, SudokuSolver.countSolutions(IntArray(80)))
    }
    @Test fun generationCanBeCancelledWithoutFinishingTheWork() {
        Thread.currentThread().interrupt()
        try {
            SudokuGenerator.generate(Difficulty.HARD, 100)
            fail("generation should stop when interrupted")
        } catch (_: CancellationException) { /* expected */ }
        finally { Thread.interrupted() }
    }
    @Test fun givensCannotBeEditedOrErased() {
        val g = game(); val cell = g.puzzle.givens.indexOfFirst { it > 0 }; g.select(cell)
        assertFalse(g.enter(1)); assertFalse(g.erase()); assertTrue(g.undo.isEmpty())
        assertArrayEquals(g.puzzle.givens, g.values)
    }
    @Test fun selectingAndEnteringAValueOnlyChangesTheChosenEmptyCell() {
        val g = game(); val selected = g.selected
        assertTrue(g.enter(g.puzzle.solution[selected]))
        assertEquals(g.puzzle.solution[selected], g.values[selected])
        assertTrue(g.values.indices.filter { it != selected }.all { g.values[it] == g.puzzle.givens[it] })
        assertFalse(g.enter(0)); assertFalse(g.enter(10)); assertFalse(g.select(81)); assertFalse(g.select(-1))
    }
    @Test fun notesToggleWithoutFillingAndSurviveUndoRedo() {
        val g = game(); val cell = g.selected; val empty = g.emptyCount; g.noteMode = true
        assertTrue(g.enter(2)); assertTrue(g.enter(7))
        assertEquals((1 shl 1) or (1 shl 6), g.notes[cell]); assertEquals(empty, g.emptyCount)
        assertTrue(g.undoMove()); assertEquals(1 shl 1, g.notes[cell])
        assertTrue(g.redoMove()); assertEquals((1 shl 1) or (1 shl 6), g.notes[cell])
        assertTrue(g.enter(7)); assertEquals(1 shl 1, g.notes[cell])
    }
    @Test fun eraseClearsNotesAndEntriesAndUndoRestoresThem() {
        val g = game(); val cell = g.selected; g.noteMode = true; g.enter(3)
        assertTrue(g.erase()); assertEquals(0, g.notes[cell]); g.undoMove(); assertEquals(4, g.notes[cell])
        g.noteMode = false; g.enter(8); assertEquals(0, g.notes[cell])
        assertTrue(g.erase()); assertEquals(0, g.values[cell]); g.undoMove(); assertEquals(8, g.values[cell])
    }
    @Test fun conflictDetectionChecksRowsColumnsAndBoxes() {
        val g = game()
        for ((a, b) in listOf(0 to 8, 0 to 72, 0 to 10)) {
            g.values.fill(0); g.values[a] = 9; g.values[b] = 9
            val conflicts = g.conflicts(); assertTrue(conflicts[a]); assertTrue(conflicts[b])
            assertEquals(2, conflicts.count { it })
        }
    }
    @Test fun enteringNumberRemovesPeerNotesAndUndoRestoresThem() {
        val g = game(); val cell = g.selected
        val peer = g.values.indices.first { it != cell && g.puzzle.givens[it] == 0 && SudokuSolver.peers(cell, it) }
        g.select(peer); g.noteMode = true; g.enter(4)
        g.select(cell); g.noteMode = false; g.enter(4)
        assertEquals(0, g.notes[peer]); g.undoMove(); assertEquals(8, g.notes[peer])
    }
    @Test fun newMoveAfterUndoDiscardsRedoAndHistoryIsBounded() {
        val g = game(); g.enter(1); g.enter(2); g.undoMove(); g.enter(3)
        assertTrue(g.redo.isEmpty())
        repeat(150) { g.enter(1 + it % 9) }
        assertEquals(100, g.undo.size)
    }
    @Test fun hintRepairsWrongEntryAndCountsUsage() {
        val g = game(); val cell = g.selected; val answer = g.puzzle.solution[cell]
        g.enter(if (answer == 9) 1 else answer + 1)
        assertTrue(g.wrongCells()[cell]); assertEquals(cell, g.hint())
        assertEquals(answer, g.values[cell]); assertEquals(1, g.hintsUsed)
        g.undoMove(); assertTrue(g.wrongCells()[cell]); assertEquals(1, g.hintsUsed)
    }
    @Test fun fillingTheCorrectSolutionCompletesButIncorrectFullGridDoesNot() {
        val g = game(); g.values.fill(1); assertFalse(g.complete)
        g.puzzle.solution.copyInto(g.values); assertTrue(g.complete)
        assertEquals(-1, g.hint()); assertFalse(g.erase()); assertFalse(g.enter(3))
    }
    @Test fun hintsCanFinishAnEntirePuzzleWithoutChangingGivens() {
        val g = game(); val count = g.emptyCount
        repeat(count) { assertTrue(g.hint() >= 0) }
        assertTrue(g.complete); assertEquals(count, g.hintsUsed)
        assertTrue(g.puzzle.givens.indices.all { g.puzzle.givens[it] == 0 || g.values[it] == g.puzzle.givens[it] })
    }
    @Test fun saveRestoresBoardNotesTimerDifficultyAndUndoRedoFromDisk() {
        val g = game(); g.noteMode = true; g.enter(3); g.enter(6); g.undoMove()
        g.elapsedMillis = 123456; val file = Files.createTempFile("sudoku", ".dat")
        try {
            Files.write(file, SudokuCodec.encode(g)); val r = SudokuCodec.decode(Files.readAllBytes(file))!!
            assertArrayEquals(g.values, r.values); assertArrayEquals(g.notes, r.notes)
            assertArrayEquals(g.puzzle.givens, r.puzzle.givens); assertArrayEquals(g.puzzle.solution, r.puzzle.solution)
            assertEquals(g.selected, r.selected); assertEquals(g.puzzle.difficulty, r.puzzle.difficulty)
            assertEquals(123456L, r.elapsedMillis); assertTrue(r.noteMode)
            assertEquals(g.undo.size, r.undo.size); assertEquals(g.redo.size, r.redo.size)
            g.redoMove(); r.redoMove(); assertArrayEquals(g.notes, r.notes)
            g.undoMove(); r.undoMove(); assertArrayEquals(g.notes, r.notes)
        } finally { Files.deleteIfExists(file) }
    }
    @Test fun completedGameAndHintCountSurviveSave() {
        val g = game(); repeat(g.emptyCount) { g.hint() }; g.elapsedMillis = 45000
        val r = SudokuCodec.decode(SudokuCodec.encode(g))!!
        assertTrue(r.complete); assertEquals(g.hintsUsed, r.hintsUsed); assertEquals(45000L, r.elapsedMillis)
    }
    @Test fun malformedAndCorruptedSavesAreRejected() {
        val g = game(); val bytes = SudokuCodec.encode(g)
        assertNull(SudokuCodec.decode(bytes.copyOf(80)))
        bytes[bytes.size - 1] = (bytes.last().toInt() xor 1).toByte(); assertNull(SudokuCodec.decode(bytes))
        g.values[g.puzzle.givens.indexOfFirst { it > 0 }] = 0
        assertNull(SudokuCodec.decode(SudokuCodec.encode(g)))
    }
}
