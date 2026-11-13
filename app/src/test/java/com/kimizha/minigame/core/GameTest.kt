package com.kimizha.minigame.core

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import kotlin.math.*

class GameTest {
    private fun playing(): Engine = Engine().apply { launch() }
    private fun block(id: Int = 0, x: Double = 360.0, y: Double = 500.0) = Body(id, Vec(x, y), Vec(9.0, 9.0), Kind.BLOCK, 0xFFFF637F.toInt())
    private fun ticks(e: Engine, n: Int) { repeat(n) { e.advance(Rules.STEP) } }

    @Test fun startsWithOneAttachedBallAndThreeLives() {
        val e = Engine(); assertEquals(3, e.lives); assertEquals(1, e.balls.size)
        assertTrue(e.balls.single().held); assertEquals(Phase.READY, e.phase)
        assertTrue(e.launch()); assertEquals(1, e.balls.size); assertFalse(e.balls.single().held)
        assertFalse(e.launch()); assertEquals(1, e.serves)
    }
    @Test fun paddleMovementIsSmoothAndClampedAtBothEdges() {
        val e = Engine(); e.movePaddle(-1000.0); ticks(e, 1)
        assertTrue(e.paddle.x < 360 && e.paddle.x > 100)
        ticks(e, 100); assertEquals(e.paddle.width / 2, e.paddle.x, 1e-9)
        assertEquals(e.paddle.x, e.balls.single().pos.x, 1e-9)
        e.movePaddle(5000.0); ticks(e, 150)
        assertEquals(Rules.WIDTH - e.paddle.width / 2, e.paddle.x, 1e-9)
        e.movePaddle(Double.NaN); assertTrue(e.paddle.targetX.isFinite())
    }
    @Test fun paddleCenterAndEdgesProduceBoundedUpwardAngles() {
        val center = Physics.paddleBounce(360.0, 360.0, 160.0)
        assertEquals(0.0, center.x, 1e-9); assertTrue(center.y < 0)
        val left = Physics.paddleBounce(280.0, 360.0, 160.0)
        val right = Physics.paddleBounce(440.0, 360.0, 160.0)
        assertTrue(left.x < 0 && right.x > 0)
        assertEquals(left.y, right.y, 1e-9)
        assertEquals(65.0, Math.toDegrees(atan2(right.x, -right.y)), 1e-8)
        assertTrue(right.y < -Rules.SPEED * .4)
    }
    @Test fun movingPaddleActuallyReflectsBallFromItsTop() {
        val e = playing(); val b = e.balls.single()
        b.pos = Vec(390.0, 1000.0); b.vel = Vec(0.0, Rules.SPEED)
        ticks(e, 15)
        assertTrue(b.vel.y < 0 && b.vel.x > 0)
        assertEquals(3, e.lives)
    }
    @Test fun paddleCannotSaveABallThroughItsUnderside() {
        val e = playing(); e.balls.single().apply { pos = Vec(360.0, 1080.0); vel = Vec(0.0, 620.0) }
        ticks(e, 40)
        assertEquals(2, e.lives); assertEquals(Phase.READY, e.phase)
    }
    @Test fun ordinaryPixelsAlwaysDisappearOnTheFirstHit() {
        for (level in 1..30) {
            val e = Engine(level); val b = e.bodies.first { it.target }
            assertEquals(0, b.durability)
            e.damage(b); assertTrue(b.destroyed); val score = e.score
            e.damage(b); assertEquals(score, e.score)
        }
    }
    @Test fun wallDurabilityIsIndependentOfPixelState() {
        val e = Engine(12); val wall = e.bodies.first { it.kind == Kind.BREAKABLE }
        e.damage(wall); assertEquals(2, wall.durability); assertTrue(wall.alive)
        e.damage(wall); assertTrue(wall.alive)
        e.damage(wall); assertFalse(wall.alive)
        val fixed = e.bodies.first { it.kind == Kind.WALL }; e.damage(fixed); assertTrue(fixed.alive)
    }
    @Test fun destroyedPixelDropsPickupAtItsOwnPositionAndItFalls() {
        val e = playing(); val target = e.bodies.first { it.target }
        e.damage(target); val p = e.pickups.single()
        assertEquals(target.center, p.pos); val y = p.pos.y
        ticks(e, 20)
        assertEquals(y + Rules.PICKUP_SPEED * Rules.STEP * 20, p.pos.y, 1e-6)
    }
    @Test fun onlyPaddleCatchesFallingPickupAndGrowthIsImmediate() {
        val e = playing()
        e.pickups.add(Pickup(Vec(360.0, 1011.8), Power.PLUS3))
        ticks(e, 1)
        assertEquals(4, e.balls.size); assertTrue(e.pickups.isEmpty())
        assertTrue(e.events.any { it.type == "power" && it.text == "弹珠 +3" })
    }
    @Test fun ballsDoNotCollectPickupsAndMissedPickupsLeaveScene() {
        val e = playing(); val b = e.balls.single()
        b.pos = Vec(90.0, 800.0); b.vel = Vec(0.0, -620.0)
        e.pickups.add(Pickup(Vec(90.0, 790.0), Power.PLUS5))
        ticks(e, 10); assertEquals(1, e.balls.size); assertEquals(1, e.pickups.size)
        e.pickups.single().pos.y = Rules.BOTTOM + 30
        ticks(e, 1); assertTrue(e.pickups.isEmpty())
    }
    @Test fun growthProducesDistinctSafeAnglesAndNeverExceeds150() {
        val e = playing(); e.collect(Power.PLUS1); assertEquals(2, e.balls.size)
        e.collect(Power.PLUS3); assertEquals(5, e.balls.size)
        e.collect(Power.PLUS5); assertEquals(10, e.balls.size)
        e.collect(Power.DOUBLE); assertEquals(20, e.balls.size)
        assertTrue(e.balls.map { it.vel }.distinct().size > 8)
        assertTrue(e.balls.all { ball -> e.bodies.none { it.alive && Physics.overlaps(ball.pos, it) } })
        repeat(20) { e.collect(Power.DOUBLE) }; assertEquals(150, e.balls.size)
    }
    @Test fun splitWaitsForBlockContactAndHasABoundedCharge() {
        val e = playing(); e.bodies = mutableListOf(block(), block(1, 100.0, 200.0)); e.invalidateGrid()
        val b = e.balls.single(); b.pos = Vec(360.0, 535.0); b.vel = Vec(0.0, -620.0)
        e.collect(Power.SPLIT); assertEquals(1, e.balls.size)
        ticks(e, 15)
        assertEquals(3, e.balls.size); assertEquals(0, b.splitCharges)
    }
    @Test fun wideningAndMagnetTimersExpireWithoutUnlimitedStacking() {
        val e = playing(); e.collect(Power.WIDE); e.collect(Power.MAGNET)
        e.collect(Power.WIDE); assertEquals(16.0, e.paddle.wideTime, 1e-8)
        val b = e.balls.single(); b.pos = Vec(360.0, 1000.0); b.vel = Vec(0.0, 620.0)
        ticks(e, 15); assertTrue(b.held)
        ticks(e, 650); assertFalse(b.held)
        e.paddle.wideTime = .001; ticks(e, 2)
        assertEquals(Rules.PADDLE_WIDTH, e.paddle.width, .001)
    }
    @Test fun laserPaddleClearsPixelAndStopsAtUnbreakableWalls() {
        val e = playing(); e.bodies = mutableListOf(block(0, 297.0, 500.0), block(1, 423.0, 500.0),
            Body(2, Vec(423.0, 800.0), Vec(20.0, 8.0), Kind.WALL))
        e.invalidateGrid(); e.collect(Power.LASER); ticks(e, 1)
        assertFalse(e.bodies[0].alive); assertTrue(e.bodies[1].alive)
        assertTrue(e.events.any { it.type == "laser" })
    }
    @Test fun aLostBallDoesNotCostALifeWhileAnotherRemains() {
        val e = playing(); e.addBalls(1)
        e.balls.first().pos.y = 1160.0
        ticks(e, 1); assertEquals(1, e.balls.size); assertEquals(3, e.lives)
    }
    @Test fun losingAllBallsCostsExactlyOneLifeAndThreeMissesFail() {
        val e = playing()
        for (life in 2 downTo 0) {
            e.balls.forEach { it.pos.y = 1160.0; it.held = false; it.vel = Vec(0.0, 620.0) }
            ticks(e, 1); assertEquals(life, e.lives)
            ticks(e, 20); assertEquals(life, e.lives)
            if (life > 0) { assertEquals(1, e.balls.size); assertEquals(Phase.READY, e.phase); e.launch() }
        }
        assertEquals(Phase.LOST, e.phase)
    }
    @Test fun actualPhysicsClearsFinalPixelAndWins() {
        val e = playing(); e.bodies = mutableListOf(block()); e.invalidateGrid()
        e.balls.single().apply { pos = Vec(360.0, 530.0); vel = Vec(0.0, -620.0) }
        ticks(e, 20); assertEquals(Phase.WON, e.phase); assertEquals(0, e.remaining)
        assertEquals(3, e.lives)
    }
    @Test fun thinWallCannotBeTunneledAtHighSpeed() {
        val ball = Ball(1, Vec(100.0, 400.0), Vec(10000.0, 0.0))
        val wall = Body(1, Vec(200.0, 400.0), Vec(2.0, 80.0), Kind.WALL)
        val hit = Physics.sweep(ball, wall, .03)!!
        assertEquals(.0092, hit.time, 1e-7); assertTrue(Physics.reflect(ball.vel, hit.normal).x < 0)
    }
    @Test fun angledAndMovingObstaclesHaveStableReflectionNormals() {
        val paddle = Body(1, Vec(200.0, 300.0), Vec(70.0, 10.0), Kind.PADDLE, angle = PI / 4)
        val ball = Ball(1, Vec(200.0, 180.0), Vec(0.0, 500.0))
        val hit = Physics.sweep(ball, paddle, .5)!!
        assertEquals(1.0, hit.normal.length(), 1e-9)
        assertTrue(abs(Physics.reflect(ball.vel, hit.normal).x) > 400)
        assertEquals(-300.0, Physics.reflect(Vec(500.0, 0.0), Vec(-1.0, 0.0), Vec(100.0, 0.0)).x, .001)
    }
    @Test fun thirtyDensePatternsAreUniqueAndMulticolored() {
        assertEquals(30, LevelPatterns.all.size)
        assertEquals(30, LevelPatterns.all.map { it.rows }.distinct().size)
        for (level in 1..30) {
            val all = Levels.build(level); val pixels = all.filter { it.target }
            assertTrue(pixels.size >= 100); assertTrue(pixels.map { it.color }.distinct().size >= 3)
            assertTrue(pixels.all { it.half == Vec(9.0, 9.0) && it.durability == 0 })
            assertTrue(all.all { it.id in 0..2047 && it.center.x - it.half.x >= 0 && it.center.x + it.half.x <= 720 })
            assertEquals(level >= 6, all.any { it.kind == Kind.WALL })
            assertEquals(level >= 11, all.any { it.kind == Kind.BREAKABLE })
            assertEquals(level >= 21, all.any { it.kind == Kind.PADDLE })
            assertEquals(level >= 26, all.any { it.kind == Kind.MOVING })
        }
    }
    @Test fun noPatternOrSeedEnclosesTargetsBehindFixedObstacles() {
        for (level in 1..30) for (seed in listOf(1L, 719L)) {
            val bodies = Levels.build(level, true, seed)
            assertEquals(bodies, Levels.build(level, true, seed))
            val fixed = bodies.filter { !it.breakable }
            val seen = hashSetOf<Pair<Int, Int>>()
            val pending = java.util.ArrayDeque<Pair<Int, Int>>(); pending.add(36 to 100)
            while (pending.isNotEmpty()) {
                val p = pending.removeFirst()
                if (p.first !in 1..70 || p.second !in 15..102 || p in seen) continue
                val point = Vec(p.first * 10.0, p.second * 10.0)
                if (fixed.any { Physics.overlaps(point, it, 7.0) }) continue
                seen.add(p)
                pending.add(p.first + 1 to p.second); pending.add(p.first - 1 to p.second)
                pending.add(p.first to p.second + 1); pending.add(p.first to p.second - 1)
            }
            assertTrue(bodies.filter { it.target }.all { (it.center.x.toInt() / 10 to it.center.y.toInt() / 10) in seen })
        }
    }
    @Test fun saveRestoresBallsPaddleEffectsLivesAndDestroyedPixelFromDisk() {
        val e = playing(); e.movePaddle(490.0); e.collect(Power.PLUS5); e.collect(Power.WIDE); e.collect(Power.LASER)
        ticks(e, 80); e.damage(e.bodies.first { it.target }); e.lives = 2
        val file = Files.createTempFile("paddle-save", ".dat")
        try {
            Files.write(file, SaveCodec.encode(SaveData(Progress(22, 15, 9000), e)))
            val loaded = SaveCodec.decode(Files.readAllBytes(file))!!; val r = loaded.engine!!
            assertEquals(22, loaded.progress.unlocked); assertEquals(e.balls, r.balls)
            assertEquals(e.bodies, r.bodies); assertEquals(e.paddle, r.paddle); assertEquals(2, r.lives)
            assertEquals(e.pickups, r.pickups); assertEquals(e.phase, r.phase)
            ticks(e, 30); ticks(r, 30)
            assertEquals(e.balls, r.balls); assertEquals(e.score, r.score)
        } finally { Files.deleteIfExists(file) }
    }
    @Test fun legacyV1SaveKeepsUnlockedLevelsButStartsFreshPaddleGame() {
        val bytes = javaClass.getResourceAsStream("/legacy-v1.dat")!!.use { it.readBytes() }
        val data = SaveCodec.decode(bytes)!!
        assertTrue(data.migrated); assertEquals(27, data.progress.unlocked); assertEquals(19000, data.progress.best)
        val e = data.engine!!; assertEquals(26, e.level); assertEquals(3, e.lives)
        assertEquals(1, e.balls.size); assertEquals(Phase.READY, e.phase)
        assertTrue(e.bodies.filter { it.target }.all { it.durability == 0 && !it.destroyed })
    }
    @Test fun corruptAndTruncatedSaveAreRejectedAndUnlockingIsMonotonic() {
        val bytes = SaveCodec.encode(SaveData(Progress(), Engine()))
        assertNull(SaveCodec.decode(bytes.copyOf(18)))
        bytes[bytes.size - 1] = (bytes.last().toInt() xor 1).toByte(); assertNull(SaveCodec.decode(bytes))
        val p = Progress(); p.complete(9, 1000); p.complete(1, 10)
        assertEquals(10, p.unlocked); assertEquals(1000, p.best); assertTrue(p.canPlay(10)); assertFalse(p.canPlay(11))
    }
    @Test fun full150BallSimulationRemainsFiniteAndBounded() {
        val e = Engine(30); e.launch(); e.addBalls(149)
        assertEquals(150, e.balls.size)
        repeat(720) {
            e.movePaddle(360 + 220 * sin(it / 80.0)); e.advance(Rules.STEP); e.drainEvents()
            assertTrue(e.balls.size <= 150)
            assertTrue(e.balls.all { b -> b.pos.x.isFinite() && b.pos.y.isFinite() && b.vel.x.isFinite() && b.vel.y.isFinite() })
        }
    }
}
