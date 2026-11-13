package com.kimizha.minigame.core

import java.util.Random
import kotlin.math.*

class Engine(var level: Int = 1, var infinite: Boolean = false, val seed: Long = level.toLong()) {
    var phase = Phase.AIM
    var bodies = Levels.build(level, infinite, seed)
    val balls = mutableListOf<Ball>()
    val pickups = mutableListOf<Pickup>()
    val events = mutableListOf<Event>()
    var stock = 3
    var roundStock = 3
    var score = 0
    var rounds = 0
    var launcher = Vec(360.0, Rules.BOTTOM - 12.0)
    var direction = Vec(0.0, -Rules.SPEED)
    var elapsed = 0.0
    var flight = 0.0
    var queue = 0
    var shotClock = 0.0
    var nextId = 1
    var firstReturn = -1.0
    var combo = 0
    var effectExplosion = false
    var effectPierce = false
    var effectDamage = false
    var effectSlow = false
    private val grid = SpatialGrid()
    private var accumulator = 0.0

    val remaining get() = bodies.count { it.target && it.alive }
    val maxRounds get() = if (infinite) 100 else 55 + level
    fun shoot(target: Vec): Boolean {
        if (phase != Phase.AIM) return false
        direction = Physics.aim(launcher, target)
        roundStock = stock; queue = roundStock; rounds++
        shotClock = 0.0; flight = 0.0; combo = 0; firstReturn = -1.0
        effectExplosion = false; effectPierce = false; effectDamage = false; effectSlow = false
        phase = Phase.FLIGHT
        return true
    }
    fun advance(delta: Double) {
        if (phase != Phase.FLIGHT) return
        accumulator += delta.coerceIn(0.0, 0.08)
        while (accumulator >= Rules.STEP && phase == Phase.FLIGHT) {
            step(Rules.STEP); accumulator -= Rules.STEP
        }
    }
    fun drainEvents(): List<Event> { val copy = events.toList(); events.clear(); return copy }
    private fun spawn() {
        val b = Ball(nextId++, launcher.copy(), direction.copy())
        b.explosion = if (effectExplosion) 5 else 0
        b.pierce = if (effectPierce) 8 else 0
        b.damage = if (effectDamage) 12 else 0
        if (effectSlow) { b.slow = 8.0; b.vel = b.vel * .65 }
        balls.add(b); events.add(Event("shot", launcher.copy()))
    }
    fun step(dt: Double) {
        if (phase != Phase.FLIGHT) return
        flight += dt; elapsed += dt
        shotClock -= dt
        while (queue > 0 && shotClock <= 0) { spawn(); queue--; shotClock += Rules.SHOT_INTERVAL }
        for (o in bodies) if (o.kind == Kind.MOVING) o.vx = 75.0 * cos(elapsed * .65)
        grid.rebuild(bodies)
        val spawned = mutableListOf<Ball>()
        // Snapshot permits bounded split creation without modifying the iteration list.
        for (b in balls.toList()) {
            b.previous = b.pos.copy()
            b.age += dt; b.ignoreTime -= dt
            if (b.slow > 0) { b.slow -= dt; if (b.slow <= 0) b.vel = b.vel.unit() * Rules.SPEED }
            var left = dt
            var contacts = 0
            while (left > 1e-7 && contacts++ < 10 && b.pos.y < Rules.BOTTOM) {
                var hit: Hit? = null
                fun consider(h: Hit?) { if (h != null && h.time >= 0 && h.time <= left && (hit == null || h.time < hit!!.time)) hit = h }
                val r = Rules.RADIUS
                if (b.vel.x < -1e-9) consider(Hit(max(0.0, (r - b.pos.x) / b.vel.x), Vec(1.0, 0.0)))
                if (b.vel.x > 1e-9) consider(Hit(max(0.0, (Rules.WIDTH - r - b.pos.x) / b.vel.x), Vec(-1.0, 0.0)))
                if (b.vel.y < -1e-9) consider(Hit(max(0.0, (Rules.TOP + r - b.pos.y) / b.vel.y), Vec(0.0, 1.0)))
                val end = b.pos + b.vel * left
                for (body in grid.query(b.pos, end, r + 1.0)) if (body.alive &&
                    !(b.ignored == body.id && b.ignoreTime > 0)) consider(Physics.sweep(b, body, left))
                val h = hit
                val old = b.pos.copy()
                val travel = h?.time ?: left
                b.pos = b.pos + b.vel * travel
                for (p in pickups.toList()) if (Physics.segmentDistance(p.pos, old, b.pos) < r + 18) {
                    pickups.remove(p); collect(p.power, b, p.pos, spawned)
                }
                if (h == null) { left = 0.0; break }
                left -= travel
                val o = h.body
                if (o != null && o.breakable) {
                    val damage = if (b.damage > 0) { b.damage--; 2 } else 1
                    hurt(o, damage)
                    if (b.explosion > 0) {
                        b.explosion--
                        events.add(Event("explosion", b.pos.copy()))
                        for (near in bodies) if (near.alive && near.breakable && near.id != o.id &&
                            (near.center - b.pos).length() < 125) hurt(near, damage)
                    }
                }
                if (o?.target == true && b.pierce > 0) {
                    b.pierce--; b.ignored = o.id; b.ignoreTime = .14
                    b.pos = b.pos + b.vel.unit() * .05
                } else {
                    b.vel = Physics.reflect(b.vel, h.normal, Vec(o?.vx ?: 0.0, 0.0))
                    val speed = Rules.SPEED * if (b.slow > 0) .65 else 1.0
                    b.vel = b.vel.unit() * speed
                    // A shallow bounce is nudged away from horizontal to avoid endless lanes.
                    if (abs(b.vel.y) < speed * .08) b.vel = Vec(b.vel.x, if (b.vel.y < 0) -speed * .08 else speed * .08).unit() * speed
                    b.pos = b.pos + h.normal * .15
                    events.add(Event("hit", b.pos.copy()))
                }
            }
            if (!b.pos.x.isFinite() || !b.pos.y.isFinite() || b.age > Rules.MAX_FLIGHT ||
                b.pos.y >= Rules.BOTTOM || contacts > 10) {
                if (firstReturn < 0) firstReturn = b.pos.x.takeIf { it.isFinite() }?.coerceIn(20.0, 700.0) ?: 360.0
                balls.remove(b)
            }
        }
        balls.addAll(spawned.take((Rules.MAX_BALLS - balls.size).coerceAtLeast(0)))
        for (o in bodies) if (o.kind == Kind.MOVING) o.center.x = o.homeX + 115.0 * sin(elapsed * .65)
        for (p in pickups) p.life += dt
        pickups.removeAll { it.life > 100.0 }
        if (remaining == 0) { phase = Phase.WON; balls.clear(); queue = 0; events.add(Event("win", Vec(360.0, 500.0))) }
        else if ((queue == 0 && balls.isEmpty()) || flight > Rules.MAX_FLIGHT + roundStock * Rules.SHOT_INTERVAL) finishRound()
        // Avoid unbounded event allocation if caller forgets to drain.
        if (events.size > 1200) events.subList(0, events.size - 1200).clear()
    }
    private fun finishRound() {
        queue = 0; balls.clear()
        launcher.x = if (firstReturn >= 0) firstReturn else launcher.x
        for (body in bodies) if (body.alive && body.breakable) body.center.y += 20.0
        for (p in pickups) p.pos.y += 20.0
        phase = if (rounds >= maxRounds || bodies.any { it.alive && it.target && it.center.y + it.half.y >= Rules.BOTTOM - 40 }) Phase.LOST else Phase.AIM
        accumulator = 0.0
        events.add(Event("round", launcher.copy()))
    }
    fun hurt(body: Body, damage: Int) {
        if (!body.alive || !body.breakable) return
        body.hp = max(0, body.hp - damage)
        score += damage * 2
        if (body.hp == 0) {
            score += 20; combo++
            events.add(Event("break", body.center.copy(), if (combo >= 3) "${combo} 连击" else "", combo))
            val rand = Random(seed xor (body.id.toLong() * 7919L))
            if (body.target && (combo == 1 || rand.nextDouble() < .36)) {
                val pool = if (level < 16 && !infinite) Power.entries.take(4) else Power.entries
                val power = if (combo == 1) Power.PLUS1 else pool[rand.nextInt(pool.size)]
                if (pickups.size < 80) pickups.add(Pickup(body.center.copy(), power))
            }
        }
    }
    fun collect(power: Power, ball: Ball, at: Vec = ball.pos, additions: MutableList<Ball> = mutableListOf()) {
        when (power) {
            Power.PLUS1 -> stock = min(Rules.MAX_BALLS, stock + 1)
            Power.PLUS3 -> stock = min(Rules.MAX_BALLS, stock + 3)
            Power.PLUS5 -> stock = min(Rules.MAX_BALLS, stock + 5)
            Power.DOUBLE -> stock = min(Rules.MAX_BALLS, stock * 2)
            Power.EXPLODE -> { effectExplosion = true; balls.forEach { it.explosion = 5 }; ball.explosion = 5 }
            Power.PIERCE -> { effectPierce = true; balls.forEach { it.pierce = 8 }; ball.pierce = 8 }
            Power.DAMAGE -> { effectDamage = true; balls.forEach { it.damage = 12 }; ball.damage = 12 }
            Power.SLOW -> {
                effectSlow = true
                (balls + ball).distinctBy { it.id }.forEach { it.slow = 8.0; it.vel = it.vel.unit() * (Rules.SPEED * .65) }
            }
            Power.SPLIT -> if (balls.size + additions.size + queue <= Rules.MAX_BALLS - 2) {
                for (angle in listOf(-.38, .38)) additions.add(Ball(nextId++, ball.pos.copy(), ball.vel.rotate(angle), age = ball.age))
            }
            Power.LASER -> {
                val row = bodies.filter { it.target && it.alive }.minByOrNull { abs(it.center.y - at.y) }?.center?.y
                if (row != null) {
                    bodies.filter { it.target && it.alive && abs(it.center.y - row) < 20 }.forEach { hurt(it, it.hp) }
                    events.add(Event("laser", Vec(at.x, row)))
                }
            }
        }
        events.add(Event("power", at.copy(), power.label))
    }
    /** Same narrow phase as the game; preview never changes game state. */
    fun preview(target: Vec): List<Vec> {
        val b = Ball(-1, launcher.copy(), Physics.aim(launcher, target))
        val points = mutableListOf(b.pos.copy())
        repeat(3) {
            var hit: Hit? = null
            fun consider(h: Hit?) { if (h != null && h.time > 1e-5 && (hit == null || h.time < hit!!.time)) hit = h }
            if (b.vel.x < 0) consider(Hit((Rules.RADIUS - b.pos.x) / b.vel.x, Vec(1.0, 0.0)))
            if (b.vel.x > 0) consider(Hit((Rules.WIDTH - Rules.RADIUS - b.pos.x) / b.vel.x, Vec(-1.0, 0.0)))
            if (b.vel.y < 0) consider(Hit((Rules.TOP + Rules.RADIUS - b.pos.y) / b.vel.y, Vec(0.0, 1.0)))
            for (body in bodies) if (body.alive) consider(Physics.sweep(b, body, 2.0))
            val h = hit
            if (h == null) { points.add(b.pos + b.vel * .5); return points }
            b.pos = b.pos + b.vel * h.time; points.add(b.pos.copy())
            b.vel = Physics.reflect(b.vel, h.normal); b.pos = b.pos + h.normal * .2
            if (h.body?.breakable == true) return points
        }
        return points
    }
}
