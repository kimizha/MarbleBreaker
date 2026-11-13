package com.kimizha.minigame.sudoku

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

class SudokuActivity : ComponentActivity() {
    private lateinit var game: SudokuView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false)
        game = SudokuView(this) { finish() }
        setContentView(game)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { game.back() }
        })
    }
    override fun onResume() { super.onResume(); if (::game.isInitialized) game.resumeForeground() }
    override fun onPause() { if (::game.isInitialized) game.suspendGame(); super.onPause() }
    override fun onDestroy() { if (::game.isInitialized) game.dispose(); super.onDestroy() }
}
