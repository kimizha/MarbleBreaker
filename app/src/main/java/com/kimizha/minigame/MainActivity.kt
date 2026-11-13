package com.kimizha.minigame

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.window.OnBackInvokedDispatcher

class MainActivity : Activity() {
    private lateinit var game: GameView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false)
        game = GameView(this)
        setContentView(game)
        if (Build.VERSION.SDK_INT >= 33)
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { game.back() }
    }
    @Deprecated("Legacy Android back navigation")
    override fun onBackPressed() { game.back() }
    override fun onPause() { game.suspendGame(); super.onPause() }
    override fun onResume() { super.onResume(); if (::game.isInitialized) game.resumeForeground() }
    override fun onDestroy() { if (::game.isInitialized) game.dispose(); super.onDestroy() }
}
