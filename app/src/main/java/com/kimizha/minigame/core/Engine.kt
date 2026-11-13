package com.kimizha.minigame.core

import java.util.Random
import kotlin.math.*

class Engine(var level: Int = 1, var infinite: Boolean = false, val seed: Long = level.toLong()) {
    var phase = Phase.READY
    var bodies = Levels.build(level, infinite, seed)
    val balls = mutableListOf<Ball>()
    val pickups = mutableListOf<Pickup>()
    val events = mutableListOf<Event>()
    val paddle = Paddle()
    var lives = 3
    var score = 0
    var serves = 0
    var elapsed = 0.0
    var nextId = 1
    var combo = 0
    var comboTime = 0.0
    var laserClock = 0.0
    var accumulator = 0.0
    private val grid = SpatialGrid()
    private var gridDirty = true
    private val moving get() = bodies.any { it.kind == Kind.MOVING }
    val remaining get() = bodies.count { it.target && it.alive }
    init { readyBall() }

    private fun readyBall() {
        balls.add(Ball(nextId++, Vec(paddle.x, heldY()), Vec(0.0, 0.0), held = true))
    }
    private fun heldY() = Rules.PADDLE_Y - Rules.PADDLE_HALF_HEIGHT - Rules.RADIUS - 1
    fun movePaddle(x: Double) = paddle.moveTo(x)
    fun launch(): Boolean {
        if (phase != Phase.READY && phase != Phase.PLAYING) return false
        val held = balls.filter { it.held }
        if (held.isEmpty()) return false
        if (phase == Phase.READY) { serves++; phase = Phase.PLAYING }
        held.forEachIndexed { index, ball ->
            ball.held = false; ball.holdTime = 0.0
            ball.vel = Vec(sin(.22 + index * .17), -abs(cos(.22 + index * .17))).unit() * Rules.SPEED
        }
        events.add(Event("shot", Vec(paddle.x, heldY())))
        return true
    }
    fun advance(delta: Double) {
        if (phase == Phase.WON || phase == Phase.LOST) return
        accumulator += delta.coerceIn(0.0, .08)
        while (accumulator >= Rules.STEP) {
            accumulator -= Rules.STEP; step(Rules.STEP)
            if (phase == Phase.WON || phase == Phase.LOST) { accumulator = 0.0; break }
        }
    }
    fun drainEvents(): List<Event> { val copy = events.toList(); events.clear(); return copy }
    fun invalidateGrid() { gridDirty = true }
    fun step(dt: Double) {
        if (phase == Phase.WON || phase == Phase.LOST) return
        val oldX = paddle.x
        paddle.width = if (paddle.wideTime > 0) 250.0 else Rules.PADDLE_WIDTH
        paddle.clamp()
        val displacement = (paddle.targetX - paddle.x).coerceIn(-2400 * dt, 2400 * dt)
        val paddleVx = displacement / dt
        val player = Body(-1, Vec(paddle.x, Rules.PADDLE_Y), Vec(paddle.width / 2, Rules.PADDLE_HALF_HEIGHT), Kind.PADDLE, vx = paddleVx)
        if (phase == Phase.READY) {
            paddle.x += displacement
            balls.forEach { it.pos = Vec(paddle.x + it.offset, heldY()) }
            return
        }
        elapsed += dt
        comboTime = max(0.0, comboTime - dt)
        if (comboTime == 0.0) combo = 0
        paddle.wideTime = max(0.0, paddle.wideTime - dt)
        paddle.magnetTime = max(0.0, paddle.magnetTime - dt)
        paddle.laserTime = max(0.0, paddle.laserTime - dt)
        for (o in bodies) if (o.kind == Kind.MOVING) o.vx = 63.0 * cos(elapsed * .7)
        if (gridDirty || moving) { grid.rebuild(bodies); gridDirty = false }
        val additions = mutableListOf<Ball>()
        val lost = mutableListOf<Ball>()
        for (b in balls) {
            if (b.held) {
                b.offset = b.offset.coerceIn(-paddle.width / 2 + 10, paddle.width / 2 - 10)
                b.pos = Vec(oldX + displacement + b.offset, heldY())
                b.holdTime -= dt
                if (b.holdTime <= 0 || paddle.magnetTime <= 0) {
                    b.held = false; b.vel = Physics.paddleBounce(b.pos.x + 12, oldX + displacement, paddle.width)
                }
                continue
            }
            b.idleTime += dt
            var left = dt
            var contacts = 0
            while (left > 1e-7 && contacts < 12 && !b.held) {
                var hit: Hit? = null
                fun consider(h: Hit?) { if (h != null && h.time >= 0 && h.time <= left && (hit == null || h.time < hit!!.time)) hit = h }
                if (b.vel.x < -1e-9) consider(Hit(max(0.0, (Rules.RADIUS - b.pos.x) / b.vel.x), Vec(1.0, 0.0)))
                if (b.vel.x > 1e-9) consider(Hit(max(0.0, (Rules.WIDTH - Rules.RADIUS - b.pos.x) / b.vel.x), Vec(-1.0, 0.0)))
                if (b.vel.y < -1e-9) consider(Hit(max(0.0, (Rules.TOP + Rules.RADIUS - b.pos.y) / b.vel.y), Vec(0.0, 1.0)))
                val end = b.pos + b.vel * left
                for (body in grid.query(b.pos, end, Rules.RADIUS + 1)) if (body.alive)
                    consider(Physics.sweep(b, body, left))
                // Paddle is one-way: a ball already below it cannot be rescued through its underside.
                player.center.x = oldX + paddleVx * (dt - left)
                if (b.vel.y > 0 && b.pos.y <= Rules.PADDLE_Y - Rules.PADDLE_HALF_HEIGHT + .01) {
                    val pHit = Physics.sweep(b, player, left)
                    if (pHit != null && pHit.normal.y < -.15) consider(pHit)
                }
                val h = hit
                if (h == null) { b.pos = end; left = 0.0; break }
                contacts++
                b.pos = b.pos + b.vel * h.time; left -= h.time
                val o = h.body
                if (o === player) {
                    val contactCenter = oldX + paddleVx * (dt - left)
                    b.vel = Physics.paddleBounce(b.pos.x, contactCenter, paddle.width)
                    b.pos.y = heldY()
                    if (paddle.magnetTime > 0) {
                        b.held = true; b.holdTime = 2.5
                        b.offset = (b.pos.x - contactCenter).coerceIn(-paddle.width / 2 + 10, paddle.width / 2 - 10)
                        b.vel = Vec(0.0, 0.0)
                    }
                } else {
                    b.vel = Physics.reflect(b.vel, h.normal, Vec(o?.vx ?: 0.0, 0.0)).unit() * Rules.SPEED
                    if (abs(b.vel.y) < Rules.SPEED * .10)
                        b.vel = Vec(b.vel.x, if (b.vel.y < 0) -Rules.SPEED * .10 else Rules.SPEED * .10).unit() * Rules.SPEED
                    b.pos = b.pos + h.normal * .2
                    if (o?.breakable == true) {
                        damage(o)
                        if (o.target && b.splitCharges > 0) {
                            b.splitCharges--
                            for (angle in doubleArrayOf(-.35, .35)) if (balls.size + additions.size < Rules.MAX_BALLS)
                                additions.add(newBall(b.pos + b.vel.unit() * 15.0, b.vel.rotate(angle)))
                        }
                    }
                }
                b.idleTime = 0.0
                events.add(Event("hit", b.pos.copy()))
            }
            if (!b.pos.x.isFinite() || !b.pos.y.isFinite() || !b.vel.x.isFinite() || !b.vel.y.isFinite() || contacts >= 12) {
                // Numerical recovery keeps the ball and life; it does not silently count a miss.
                b.pos = Vec(paddle.x, heldY() - 16); b.vel = Vec(.3, -1.0).unit() * Rules.SPEED
                events.add(Event("recover", b.pos.copy()))
            }
            if (b.pos.y > Rules.BOTTOM + Rules.RADIUS) lost.add(b)
            else if (b.idleTime > 18) { b.vel = Vec(b.vel.x + 60, -abs(b.vel.y) - 90).unit() * Rules.SPEED; b.idleTime = 0.0 }
        }
        balls.removeAll(lost.toSet())
        balls.addAll(additions.take((Rules.MAX_BALLS - balls.size).coerceAtLeast(0)))
        paddle.x = oldX + displacement; paddle.clamp()
        for (o in bodies) if (o.kind == Kind.MOVING) o.center.x = o.homeX + 90 * sin(elapsed * .7)
        updatePickups(dt, oldX, paddleVx)
        if (paddle.laserTime > 0) {
            laserClock -= dt
            if (laserClock <= 0) { fireLaser(); laserClock = .5 }
        }
        if (remaining == 0) {
            phase = Phase.WON; events.add(Event("win", Vec(360.0, 500.0))); balls.clear(); pickups.clear()
        } else if (balls.isEmpty()) {
            lives--; combo = 0; pickups.clear()
            paddle.wideTime = 0.0; paddle.magnetTime = 0.0; paddle.laserTime = 0.0
            paddle.width = Rules.PADDLE_WIDTH; paddle.clamp()
            if (lives > 0) { phase = Phase.READY; readyBall(); events.add(Event("life", Vec(paddle.x, 950.0), "剩余 $lives 条生命")) }
            else { phase = Phase.LOST; events.add(Event("life", Vec(paddle.x, 950.0), "生命已用尽")) }
        }
        if (events.size > 800) events.subList(0, events.size - 800).clear()
    }
    fun damage(body: Body) {
        if (!body.alive || !body.breakable) return
        if (body.kind == Kind.BREAKABLE) { body.durability--; if (body.durability > 0) return }
        body.destroyed = true; gridDirty = true
        score += if (body.target) 10 else 25
        combo++; comboTime = 2.5
        events.add(Event("break", body.center.copy(), if (combo >= 3) "$combo 连击" else "", combo, body.color))
        if (body.target) {
            val random = Random(seed xor (body.id.toLong() * 7919L))
            if (pickups.size < 80 && (random.nextDouble() < .17 || bodies.count { it.target && it.destroyed } == 1)) {
                val power = if (bodies.count { it.target && it.destroyed } == 1) Power.PLUS1 else Power.entries[random.nextInt(Power.entries.size)]
                pickups.add(Pickup(body.center.copy(), power))
            }
        }
    }
    private fun updatePickups(dt: Double, oldX: Double, vx: Double) {
        val caught = mutableListOf<Pickup>()
        for (p in pickups) {
            val oldY = p.pos.y; p.pos.y += Rules.PICKUP_SPEED * dt; p.age += dt
            val catchY = Rules.PADDLE_Y - Rules.PADDLE_HALF_HEIGHT - 16
            if (oldY <= catchY && p.pos.y >= catchY) {
                val t = (catchY - oldY) / Rules.PICKUP_SPEED
                if (abs(p.pos.x - (oldX + vx * t)) <= paddle.width / 2 + 16) caught.add(p)
            }
        }
        pickups.removeAll(caught.toSet())
        pickups.removeAll { it.pos.y > Rules.BOTTOM + 20 }
        caught.forEach { collect(it.power) }
    }
    fun collect(power: Power) {
        when (power) {
            Power.PLUS1 -> addBalls(1)
            Power.PLUS3 -> addBalls(3)
            Power.PLUS5 -> addBalls(5)
            Power.DOUBLE -> addBalls(balls.size)
            Power.SPLIT -> balls.forEach { it.splitCharges = 1 }
            Power.WIDE -> { paddle.wideTime = 16.0; paddle.width = 250.0; paddle.clamp() }
            Power.MAGNET -> paddle.magnetTime = 12.0
            Power.LASER -> { paddle.laserTime = 10.0; laserClock = 0.0 }
        }
        events.add(Event("power", Vec(paddle.x, 980.0), power.label, color = power.color))
    }
    private fun newBall(preferred: Vec, velocity: Vec): Ball {
        val safe = preferred.x in 10.0..710.0 && preferred.y in (Rules.TOP + 10)..(Rules.PADDLE_Y - 18) &&
            bodies.none { it.alive && Physics.overlaps(preferred, it) }
        return Ball(nextId++, if (safe) preferred.copy() else Vec(paddle.x, heldY() - 12), velocity.unit() * Rules.SPEED)
    }
    fun addBalls(count: Int) {
        val actual = count.coerceIn(0, Rules.MAX_BALLS - balls.size)
        repeat(actual) { index ->
            val angle = (((nextId * 47 + index * 29) % 121) - 60) * PI / 180
            val x = (paddle.x + (index % 7 - 3) * 8).coerceIn(10.0, 710.0)
            balls.add(newBall(Vec(x, heldY() - 12), Vec(sin(angle), -cos(angle))))
        }
    }
    private fun fireLaser() {
        for (x in doubleArrayOf(paddle.x - paddle.width / 2 + 15, paddle.x + paddle.width / 2 - 15)) {
            val obstacle = bodies.filter { it.alive && abs(x - it.center.x) <= abs(cos(it.angle)) * it.half.x + abs(sin(it.angle)) * it.half.y }
                .maxByOrNull { it.center.y }
            if (obstacle?.breakable == true) damage(obstacle)
            events.add(Event("laser", Vec(x, obstacle?.center?.y ?: Rules.TOP)))
        }
    }
}
