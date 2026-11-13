package com.kimizha.minigame.core

import kotlin.math.*

data class Vec(var x: Double, var y: Double) {
    operator fun plus(v: Vec) = Vec(x + v.x, y + v.y)
    operator fun minus(v: Vec) = Vec(x - v.x, y - v.y)
    operator fun times(s: Double) = Vec(x * s, y * s)
    fun dot(v: Vec) = x * v.x + y * v.y
    fun length() = hypot(x, y)
    fun unit(): Vec { val n = length(); return if (n < 1e-9) Vec(0.0, -1.0) else this * (1.0 / n) }
    fun rotate(a: Double) = Vec(x * cos(a) - y * sin(a), x * sin(a) + y * cos(a))
}
enum class Kind { BLOCK, WALL, BREAKABLE, PADDLE, MOVING }
enum class Power(val label: String, val icon: String) {
    PLUS1("弹珠 +1", "+1"), PLUS3("弹珠 +3", "+3"), PLUS5("弹珠 +5", "+5"),
    DOUBLE("弹珠翻倍", "×2"), EXPLODE("爆炸弹珠", "爆"), PIERCE("穿透弹珠", "穿"),
    SPLIT("分裂弹珠", "分"), LASER("激光清除", "光"), SLOW("减速", "慢"), DAMAGE("双倍伤害", "伤")
}
data class Body(val id: Int, var center: Vec, val half: Vec, var hp: Int, val kind: Kind,
                val angle: Double = 0.0, val homeX: Double = center.x, var vx: Double = 0.0) {
    val target get() = kind == Kind.BLOCK
    val breakable get() = kind == Kind.BLOCK || kind == Kind.BREAKABLE
    val alive get() = !breakable || hp > 0
}
data class Ball(val id: Int, var pos: Vec, var vel: Vec, var age: Double = 0.0,
                var pierce: Int = 0, var explosion: Int = 0, var damage: Int = 0,
                var slow: Double = 0.0, var split: Boolean = false,
                var ignored: Int = -1, var ignoreTime: Double = 0.0) {
    var previous = pos.copy()
}
data class Pickup(val pos: Vec, val power: Power, var life: Double = 0.0)
data class Event(val type: String, val pos: Vec, val text: String = "", val amount: Int = 0)
enum class Phase { AIM, FLIGHT, WON, LOST }
object Rules {
    const val WIDTH = 720.0
    const val HEIGHT = 1200.0
    const val BOTTOM = 1090.0
    const val TOP = 106.0
    const val RADIUS = 7.0
    const val SPEED = 880.0
    const val STEP = 1.0 / 240.0
    const val MAX_BALLS = 150
    const val MAX_FLIGHT = 50.0
    const val SHOT_INTERVAL = 0.045
}
data class Progress(var unlocked: Int = 1, var last: Int = 1, var best: Int = 0) {
    fun complete(level: Int, score: Int) {
        unlocked = max(unlocked, (level + 1).coerceAtMost(30))
        last = (level + 1).coerceAtMost(30); best = max(best, score)
    }
    fun canPlay(level: Int) = level in 1..unlocked
}

/** Small uniform grid. Rotated obstacles use enclosing AABBs; narrow phase remains exact. */
class SpatialGrid {
    private val cells = Array(10 * 17) { mutableListOf<Body>() }
    private fun col(x: Double) = floor(x / 80).toInt().coerceIn(0, 9)
    private fun row(y: Double) = floor(y / 80).toInt().coerceIn(0, 16)
    fun rebuild(bodies: List<Body>) {
        cells.forEach { it.clear() }
        for (b in bodies) if (b.alive) {
            val ex = abs(cos(b.angle)) * b.half.x + abs(sin(b.angle)) * b.half.y
            val ey = abs(sin(b.angle)) * b.half.x + abs(cos(b.angle)) * b.half.y
            for (y in row(b.center.y - ey)..row(b.center.y + ey))
                for (x in col(b.center.x - ex)..col(b.center.x + ex)) cells[y * 10 + x].add(b)
        }
    }
    fun query(p: Vec, q: Vec, radius: Double): Set<Body> {
        val found = linkedSetOf<Body>()
        for (y in row(min(p.y, q.y) - radius)..row(max(p.y, q.y) + radius))
            for (x in col(min(p.x, q.x) - radius)..col(max(p.x, q.x) + radius))
                found.addAll(cells[y * 10 + x])
        return found
    }
}
