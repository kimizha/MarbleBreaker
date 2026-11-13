package com.kimizha.minigame.core

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import kotlin.math.*

class GameTest {
    @Test fun fastBallCannotTunnelThroughThinWall() {
        val b = Ball(1, Vec(100.0, 400.0), Vec(10000.0, 0.0))
        val wall = Body(1, Vec(200.0, 400.0), Vec(2.0, 80.0), -1, Kind.WALL)
        val hit = Physics.sweep(b, wall, .03)!!
        assertEquals(.0091, hit.time, 1e-7)
        assertTrue(Physics.reflect(b.vel, hit.normal).x < 0)
    }
    @Test fun roundedCornerContactHasUnitNormal() {
        val b = Ball(1, Vec(100.0, 100.0), Vec(200.0, 200.0))
        val body = Body(2, Vec(200.0, 200.0), Vec(25.0, 25.0), 1, Kind.BLOCK)
        val h = Physics.sweep(b, body, 1.0)!!
        assertEquals(1.0, h.normal.length(), 1e-9)
        val v = Physics.reflect(b.vel, h.normal)
        assertTrue(v.x < 0 && v.y < 0)
        assertEquals(b.vel.length(), v.length(), 1e-6)
    }
    @Test fun angledPaddleAndMovingWallUseRelativeVelocity() {
        val paddle = Body(1, Vec(200.0, 300.0), Vec(70.0, 10.0), -1, Kind.PADDLE, PI / 4)
        val ball = Ball(1, Vec(200.0, 180.0), Vec(0.0, 500.0))
        val h = Physics.sweep(ball, paddle, .5)!!
        val reflected = Physics.reflect(ball.vel, h.normal)
        assertTrue(abs(reflected.x) > 400)
        assertEquals(-300.0, Physics.reflect(Vec(500.0, 0.0), Vec(-1.0, 0.0), Vec(100.0, 0.0)).x, .0001)
    }
    @Test fun shallowAimAlwaysPointsUpward() {
        for (x in -100..800 step 10) {
            val v = Physics.aim(Vec(360.0, 1080.0), Vec(x.toDouble(), 1080.0))
            assertTrue(v.y < -Rules.SPEED * .15)
        }
    }
    @Test fun blockHealthScoreAndIndestructibleWall() {
        val e = Engine()
        val block = Body(10, Vec(100.0, 200.0), Vec(30.0, 30.0), 3, Kind.BLOCK)
        e.hurt(block, 2); assertEquals(1, block.hp)
        e.hurt(block, 2); assertEquals(0, block.hp); val score = e.score
        e.hurt(block, 99); assertEquals(score, e.score)
        val wall = Body(20, Vec(200.0, 200.0), Vec(20.0, 20.0), -1, Kind.WALL)
        e.hurt(wall, 99); assertTrue(wall.alive)
    }
    @Test fun ballsLaunchSequentiallyAndPickupOnlyChangesNextStock() {
        val e = Engine()
        e.pickups.add(Pickup(Vec(360.0, 1030.0), Power.PLUS3))
        e.shoot(Vec(360.0, 100.0))
        e.step(.004)
        assertEquals(1, e.balls.size); assertEquals(2, e.queue)
        repeat(21) { e.step(.004) }
        assertEquals(6, e.stock)
        assertEquals(3, e.roundStock)
        assertTrue(e.balls.size <= 3)
    }
    @Test fun growthIsCappedAndPowerResetsNextRound() {
        val e = Engine(16); val b = Ball(4, Vec(100.0, 300.0), Vec(0.0, -880.0))
        repeat(20) { e.collect(Power.DOUBLE, b) }
        assertEquals(150, e.stock)
        e.collect(Power.PIERCE, b); assertEquals(8, b.pierce)
        e.shoot(Vec(360.0, 200.0)); e.step(Rules.STEP)
        assertEquals(0, e.balls.first().pierce)
    }
    @Test fun explosionDamageSlowAndSplitAreBounded() {
        val e = Engine(16)
        val b = Ball(3, Vec(360.0, 800.0), Vec(0.0, -880.0)); e.balls.add(b)
        e.collect(Power.EXPLODE, b); assertEquals(5, b.explosion)
        e.collect(Power.DAMAGE, b); assertEquals(12, b.damage)
        e.collect(Power.SLOW, b); assertEquals(572.0, b.vel.length(), .001)
        e.collect(Power.SLOW, b); assertEquals(572.0, b.vel.length(), .001)
        val extras = mutableListOf<Ball>()
        repeat(200) { e.collect(Power.SPLIT, b, additions = extras) }
        assertTrue(e.balls.size + extras.size <= Rules.MAX_BALLS)
        extras.clear(); e.queue = Rules.MAX_BALLS - e.balls.size
        e.collect(Power.SPLIT, b, additions = extras)
        assertTrue(extras.isEmpty())
    }
    @Test fun laserClearsOnlyOrdinaryBlocksInItsRow() {
        val e = Engine(16)
        e.bodies = mutableListOf(
            Body(1, Vec(100.0, 200.0), Vec(20.0, 20.0), 50, Kind.BLOCK),
            Body(2, Vec(200.0, 200.0), Vec(20.0, 20.0), 50, Kind.BLOCK),
            Body(3, Vec(300.0, 200.0), Vec(20.0, 20.0), 7, Kind.BREAKABLE),
            Body(4, Vec(100.0, 300.0), Vec(20.0, 20.0), 50, Kind.BLOCK))
        e.collect(Power.LASER, Ball(1, Vec(100.0, 200.0), Vec(0.0, -1.0)))
        assertEquals(1, e.remaining); assertEquals(7, e.bodies[2].hp)
    }
    @Test fun physicalShotCanActuallyWinALevel() {
        val e = Engine()
        e.bodies = mutableListOf(Body(0, Vec(360.0, 500.0), Vec(33.0, 27.0), 1, Kind.BLOCK))
        e.shoot(Vec(360.0, 200.0))
        repeat(500) { e.advance(Rules.STEP) }
        assertEquals(Phase.WON, e.phase)
        assertEquals(0, e.remaining)
        assertTrue(e.score > 0)
    }
    @Test fun timeoutRecoversEveryBallAndEndsTheRound() {
        val e = Engine(); e.shoot(Vec(360.0, 200.0)); e.queue = 0
        e.balls.add(Ball(100, Vec(360.0, 800.0), Vec(880.0, 0.0), age = 51.0))
        e.step(Rules.STEP)
        assertEquals(Phase.AIM, e.phase)
        assertTrue(e.balls.isEmpty())
        assertTrue(e.launcher.x in 20.0..700.0)
    }
    @Test fun failedRoundCanBeRestarted() {
        val e = Engine(); e.rounds = e.maxRounds - 1
        e.shoot(Vec(360.0, 300.0)); e.queue = 0; e.balls.clear()
        e.step(Rules.STEP)
        assertEquals(Phase.LOST, e.phase)
        assertEquals(Phase.AIM, Engine(e.level).phase)
    }
    @Test fun unlockingIsMonotonicAndCappedAtThirty() {
        val p = Progress(); assertFalse(p.canPlay(2))
        p.complete(1, 100); assertTrue(p.canPlay(2))
        p.complete(1, 10); assertEquals(100, p.best)
        p.complete(30, 500); assertEquals(30, p.unlocked)
        assertFalse(p.canPlay(31))
    }
    @Test fun allThirtyLevelsHaveValidTargetsAndProgressiveObstacles() {
        for (level in 1..30) {
            val bodies = Levels.build(level)
            assertTrue(bodies.any { it.target && it.hp > 0 })
            assertEquals(bodies.size, bodies.map { it.id }.distinct().size)
            assertTrue(bodies.all { it.center.x - it.half.x >= 0 && it.center.x + it.half.x <= 720 })
            assertEquals(level >= 6, bodies.any { it.kind == Kind.WALL })
            assertEquals(level >= 11, bodies.any { it.kind == Kind.BREAKABLE })
            assertEquals(level >= 21, bodies.any { it.kind == Kind.PADDLE })
            assertEquals(level >= 26, bodies.any { it.kind == Kind.MOVING })
        }
    }
    @Test fun randomMapsAreDeterministicAndHaveOpenRoutes() {
        for (seed in 1L..60L) {
            val a = Levels.build(30, true, seed); val b = Levels.build(30, true, seed)
            assertEquals(a, b)
            // Grid flood fill treats destructible targets as removable; no fixed enclosure may trap a target.
            val fixed = a.filter { !it.breakable }
            val seen = mutableSetOf<Pair<Int, Int>>()
            val queue = java.util.ArrayDeque<Pair<Int, Int>>()
            queue.add(18 to 53)
            while (queue.isNotEmpty()) {
                val p = queue.removeFirst()
                if (p.first !in 0..35 || p.second !in 6..54 || p in seen) continue
                val pos = Vec(p.first * 20.0 + 10, p.second * 20.0 + 10)
                val blocked = fixed.any {
                    val d = (pos - it.center).rotate(-it.angle)
                    abs(d.x) < it.half.x + 8 && abs(d.y) < it.half.y + 8
                }
                if (blocked) continue
                seen.add(p)
                queue.add(p.first + 1 to p.second); queue.add(p.first - 1 to p.second)
                queue.add(p.first to p.second + 1); queue.add(p.first to p.second - 1)
            }
            assertTrue(a.filter { it.target }.all { ((it.center.x / 20).toInt() to (it.center.y / 20).toInt()) in seen })
        }
    }
    @Test fun midFlightSaveRestoresPhysicsProgressAndEffectsFromDisk() {
        val e = Engine(27); e.stock = 17; e.shoot(Vec(480.0, 200.0))
        repeat(100) { e.advance(Rules.STEP) }
        e.collect(Power.EXPLODE, e.balls.first())
        e.pickups.add(Pickup(Vec(220.0, 500.0), Power.DOUBLE))
        val path = Files.createTempFile("marble-save", ".dat")
        try {
            Files.write(path, SaveCodec.encode(SaveData(Progress(28, 27, 9000), e)))
            val restored = SaveCodec.decode(Files.readAllBytes(path))!!
            val r = restored.engine!!
            assertEquals(28, restored.progress.unlocked)
            assertEquals(e.stock, r.stock); assertEquals(e.queue, r.queue); assertEquals(e.phase, r.phase)
            assertEquals(e.bodies, r.bodies); assertEquals(e.pickups, r.pickups)
            assertEquals(e.balls.map { it.pos }, r.balls.map { it.pos })
            assertEquals(5, r.balls.first().explosion)
            repeat(50) { e.advance(Rules.STEP); r.advance(Rules.STEP) }
            assertEquals(e.balls.size, r.balls.size)
            e.balls.zip(r.balls).forEach { (a, b) ->
                assertEquals(a.pos.x, b.pos.x, 1e-6); assertEquals(a.pos.y, b.pos.y, 1e-6)
            }
        } finally { Files.deleteIfExists(path) }
    }
    @Test fun corruptTruncatedAndUnknownSavesAreRejected() {
        val bytes = SaveCodec.encode(SaveData(Progress(), Engine()))
        assertNull(SaveCodec.decode(bytes.copyOf(15)))
        bytes[bytes.size - 1] = (bytes.last().toInt() xor 5).toByte()
        assertNull(SaveCodec.decode(bytes))
        assertNull(SaveCodec.decode(ByteArray(0)))
    }
}
