package com.kimizha.minigame.core

import kotlin.math.*

data class Vec(var x: Double, var y: Double) {
    operator fun plus(v: Vec) = Vec(x + v.x, y + v.y)
    operator fun minus(v: Vec) = Vec(x - v.x, y - v.y)
    operator fun times(s: Double) = Vec(x * s, y * s)
    fun dot(v: Vec) = x * v.x + y * v.y
    fun length() = hypot(x, y)
    fun unit(): Vec = length().let { if (it < 1e-9) Vec(0.0, -1.0) else this * (1.0 / it) }
    fun rotate(a: Double) = Vec(x * cos(a) - y * sin(a), x * sin(a) + y * cos(a))
}
enum class Kind { BLOCK, WALL, BREAKABLE, PADDLE, MOVING }
enum class Power(val label: String, val icon: String, val color: Int) {
    PLUS1("弹珠 +1", "+1", 0xFF53E3E8.toInt()), PLUS3("弹珠 +3", "+3", 0xFF76E89B.toInt()),
    PLUS5("弹珠 +5", "+5", 0xFFC7EB73.toInt()), DOUBLE("弹珠翻倍", "×2", 0xFFFFD36E.toInt()),
    SPLIT("碰撞分裂", "分", 0xFFFF84D5.toInt()), WIDE("加宽挡板", "宽", 0xFFFF916B.toInt()),
    MAGNET("磁力挡板", "磁", 0xFFB693FF.toInt()), LASER("激光挡板", "光", 0xFFFF647C.toInt())
}
/** Ordinary pixels have only an intact/destroyed state; durability belongs to special walls. */
data class Body(val id: Int, var center: Vec, val half: Vec, val kind: Kind,
                val color: Int = 0xFFE2EBF5.toInt(), var durability: Int = 0,
                val angle: Double = 0.0, val homeX: Double = center.x, var vx: Double = 0.0,
                var destroyed: Boolean = false) {
    val target get() = kind == Kind.BLOCK
    val breakable get() = target || kind == Kind.BREAKABLE
    val alive get() = !destroyed
}
data class Ball(val id: Int, var pos: Vec, var vel: Vec, var held: Boolean = false,
                var holdTime: Double = 0.0, var offset: Double = 0.0,
                var splitCharges: Int = 0, var idleTime: Double = 0.0)
data class Paddle(var x: Double = 360.0, var targetX: Double = x, var width: Double = Rules.PADDLE_WIDTH,
                  var wideTime: Double = 0.0, var magnetTime: Double = 0.0, var laserTime: Double = 0.0) {
    fun moveTo(value: Double) { if (value.isFinite()) targetX = value.coerceIn(width / 2, Rules.WIDTH - width / 2) }
    fun clamp() { x = x.coerceIn(width / 2, Rules.WIDTH - width / 2); moveTo(targetX) }
}
data class Pickup(val pos: Vec, val power: Power, var age: Double = 0.0)
data class Event(val type: String, val pos: Vec, val text: String = "", val amount: Int = 0, val color: Int = 0xFF53E3E8.toInt())
enum class Phase { READY, PLAYING, WON, LOST }
object Rules {
    const val WIDTH = 720.0
    const val HEIGHT = 1200.0
    const val TOP = 140.0
    const val BOTTOM = 1140.0
    const val PADDLE_Y = 1040.0
    const val PADDLE_WIDTH = 156.0
    const val PADDLE_HALF_HEIGHT = 12.0
    const val RADIUS = 6.0
    const val SPEED = 620.0
    const val STEP = 1.0 / 240.0
    const val MAX_BALLS = 150
    const val PICKUP_SPEED = 115.0
}
data class Progress(var unlocked: Int = 1, var last: Int = 1, var best: Int = 0) {
    fun complete(level: Int, score: Int) {
        unlocked = max(unlocked, (level + 1).coerceAtMost(30))
        last = (level + 1).coerceAtMost(30); best = max(best, score)
    }
    fun canPlay(level: Int) = level in 1..unlocked
}

/** Reused buckets and query buffer avoid per-ball sets and mutable Body hash keys. */
class SpatialGrid {
    private val cells = Array(12 * 20) { ArrayList<Body>() }
    private val marks = IntArray(2048)
    private var stamp = 0
    private val found = ArrayList<Body>()
    private fun col(x: Double) = floor(x / 64).toInt().coerceIn(0, 11)
    private fun row(y: Double) = floor(y / 64).toInt().coerceIn(0, 19)
    fun rebuild(bodies: List<Body>) {
        cells.forEach { it.clear() }
        for (b in bodies) if (b.alive) {
            val ex = abs(cos(b.angle)) * b.half.x + abs(sin(b.angle)) * b.half.y
            val ey = abs(sin(b.angle)) * b.half.x + abs(cos(b.angle)) * b.half.y
            for (y in row(b.center.y - ey)..row(b.center.y + ey))
                for (x in col(b.center.x - ex)..col(b.center.x + ex)) cells[y * 12 + x].add(b)
        }
    }
    fun query(p: Vec, q: Vec, radius: Double): List<Body> {
        found.clear()
        if (++stamp == Int.MAX_VALUE) { marks.fill(0); stamp = 1 }
        for (y in row(min(p.y, q.y) - radius)..row(max(p.y, q.y) + radius))
            for (x in col(min(p.x, q.x) - radius)..col(max(p.x, q.x) + radius))
                for (body in cells[y * 12 + x]) if (marks[body.id] != stamp) {
                    marks[body.id] = stamp; found.add(body)
                }
        return found
    }
}
