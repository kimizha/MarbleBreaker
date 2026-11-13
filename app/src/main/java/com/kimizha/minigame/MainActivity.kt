package com.kimizha.minigame

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

class MainActivity : ComponentActivity() {
    private lateinit var game: GameView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false)
        game = GameView(this)
        setContentView(game)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { game.back() }
        })
    }
    override fun onPause() { game.suspendGame(); super.onPause() }
    override fun onResume() { super.onResume(); if (::game.isInitialized) game.resumeForeground() }
    override fun onDestroy() { if (::game.isInitialized) game.dispose(); super.onDestroy() }
}
