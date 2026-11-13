package com.kimizha.minigame.core

import kotlin.math.*

data class Hit(val time: Double, val normal: Vec, val body: Body? = null)

/** Continuous circle vs oriented rectangle, including rounded corner contacts. */
object Physics {
    fun sweep(ball: Ball, body: Body, maxTime: Double): Hit? {
        val p = (ball.pos - body.center).rotate(-body.angle)
        val v = (ball.vel - Vec(body.vx, 0.0)).rotate(-body.angle)
        val h = body.half
        val r = Rules.RADIUS
        var best: Hit? = null
        fun accept(t: Double, n: Vec) {
            if (t >= -1e-9 && t <= maxTime && v.dot(n) < -1e-7 &&
                (best == null || t < best!!.time)) best = Hit(max(0.0, t), n)
        }
        // Penetration recovery is also needed when a moving obstacle reaches a ball.
        val nearest = Vec(p.x.coerceIn(-h.x, h.x), p.y.coerceIn(-h.y, h.y))
        val delta = p - nearest
        if (delta.length() < r - 0.001) {
            val n = if (delta.length() > 1e-8) delta.unit() else {
                if (h.x - abs(p.x) < h.y - abs(p.y)) Vec(if (p.x < 0) -1.0 else 1.0, 0.0)
                else Vec(0.0, if (p.y < 0) -1.0 else 1.0)
            }
            accept(0.0, n)
        }
        if (abs(v.x) > 1e-9) for (s in listOf(-1.0, 1.0)) {
            val t = (s * (h.x + r) - p.x) / v.x
            if (abs(p.y + v.y * t) <= h.y) accept(t, Vec(s, 0.0))
        }
        if (abs(v.y) > 1e-9) for (s in listOf(-1.0, 1.0)) {
            val t = (s * (h.y + r) - p.y) / v.y
            if (abs(p.x + v.x * t) <= h.x) accept(t, Vec(0.0, s))
        }
        val a = v.dot(v)
        if (a > 1e-9) for (sx in listOf(-1.0, 1.0)) for (sy in listOf(-1.0, 1.0)) {
            val d = p - Vec(sx * h.x, sy * h.y)
            val b = 2 * d.dot(v)
            val c = d.dot(d) - r * r
            val disc = b * b - 4 * a * c
            if (disc >= 0) {
                val t = (-b - sqrt(disc)) / (2 * a)
                val at = p + v * t
                if (at.x * sx >= h.x && at.y * sy >= h.y)
                    accept(t, (at - Vec(sx * h.x, sy * h.y)).unit())
            }
        }
        return best?.let { Hit(it.time, it.normal.rotate(body.angle), body) }
    }
    fun reflect(velocity: Vec, normal: Vec, obstacleVelocity: Vec = Vec(0.0, 0.0)): Vec {
        val relative = velocity - obstacleVelocity
        return relative - normal * (2 * relative.dot(normal)) + obstacleVelocity
    }
    fun aim(from: Vec, to: Vec): Vec {
        val delta = to - from
        val dx = delta.x
        val dy = -max(abs(delta.y), abs(dx) * 0.22 + 24.0)
        return Vec(dx, dy).unit() * Rules.SPEED
    }
    fun segmentDistance(point: Vec, a: Vec, b: Vec): Double {
        val d = b - a
        val t = if (d.dot(d) < 1e-9) 0.0 else ((point - a).dot(d) / d.dot(d)).coerceIn(0.0, 1.0)
        return (point - (a + d * t)).length()
    }
}
