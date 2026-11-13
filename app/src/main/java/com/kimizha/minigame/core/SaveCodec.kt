package com.kimizha.minigame.core

import java.io.*
import java.util.zip.CRC32

data class SaveData(val progress: Progress, val engine: Engine?, val migrated: Boolean = false)
object SaveCodec {
    private const val MAGIC = 0x4D425231
    private const val VERSION = 2
    fun encode(data: SaveData): ByteArray {
        val raw = ByteArrayOutputStream()
        DataOutputStream(raw).use { o ->
            o.writeInt(VERSION)
            o.writeInt(data.progress.unlocked); o.writeInt(data.progress.last); o.writeInt(data.progress.best)
            val e = data.engine; o.writeBoolean(e != null)
            if (e != null) {
                o.writeInt(e.level); o.writeBoolean(e.infinite); o.writeLong(e.seed); o.writeInt(e.phase.ordinal)
                o.writeInt(e.lives); o.writeInt(e.score); o.writeInt(e.serves); o.writeDouble(e.elapsed)
                o.writeInt(e.nextId); o.writeInt(e.combo); o.writeDouble(e.comboTime)
                o.writeDouble(e.laserClock); o.writeDouble(e.accumulator)
                with(e.paddle) {
                    o.writeDouble(x); o.writeDouble(targetX); o.writeDouble(width)
                    o.writeDouble(wideTime); o.writeDouble(magnetTime); o.writeDouble(laserTime)
                }
                o.writeInt(e.bodies.size)
                for (b in e.bodies) {
                    o.writeInt(b.id); o.vec(b.center); o.vec(b.half); o.writeInt(b.kind.ordinal)
                    o.writeInt(b.color); o.writeInt(b.durability); o.writeDouble(b.angle)
                    o.writeDouble(b.homeX); o.writeDouble(b.vx); o.writeBoolean(b.destroyed)
                }
                o.writeInt(e.balls.size)
                for (b in e.balls) {
                    o.writeInt(b.id); o.vec(b.pos); o.vec(b.vel); o.writeBoolean(b.held)
                    o.writeDouble(b.holdTime); o.writeDouble(b.offset); o.writeInt(b.splitCharges); o.writeDouble(b.idleTime)
                }
                o.writeInt(e.pickups.size)
                for (p in e.pickups) { o.vec(p.pos); o.writeInt(p.power.ordinal); o.writeDouble(p.age) }
            }
        }
        val payload = raw.toByteArray()
        return ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { o ->
                o.writeInt(MAGIC); o.writeLong(CRC32().apply { update(payload) }.value); o.write(payload)
            }
        }.toByteArray()
    }
    fun decode(bytes: ByteArray): SaveData? = try {
        require(bytes.size in 29..200_000)
        val input = DataInputStream(ByteArrayInputStream(bytes))
        require(input.readInt() == MAGIC)
        val checksum = input.readLong(); val raw = input.readBytes()
        require(CRC32().apply { update(raw) }.value == checksum)
        DataInputStream(ByteArrayInputStream(raw)).use { d ->
            val version = d.readInt(); require(version in 1..VERSION)
            val progress = Progress(d.readInt().coerceIn(1, 30), d.readInt().coerceIn(1, 30), d.readInt().coerceAtLeast(0))
            val hasGame = d.readBoolean()
            // Preserve unlocks/best score; v1 launch queues and HP layouts cannot become paddle games.
            if (version == 1) {
                val fresh = if (hasGame) {
                    val level = d.count(10000); require(level >= 1)
                    val infinite = d.readBoolean(); val seed = d.readLong()
                    Engine(if (infinite) level else level.coerceAtMost(30), infinite, seed)
                } else null
                return@use SaveData(progress, fresh, migrated = true)
            }
            val e = if (hasGame) {
                val level = d.count(10000); require(level >= 1)
                Engine(level, d.readBoolean(), d.readLong()).apply {
                    phase = Phase.entries[d.readInt()]
                    lives = d.count(3); require(lives > 0 || phase == Phase.LOST)
                    score = d.count(Int.MAX_VALUE); serves = d.count(Int.MAX_VALUE); elapsed = d.nonnegative()
                    nextId = d.count(Int.MAX_VALUE); combo = d.count(Int.MAX_VALUE); comboTime = d.nonnegative()
                    laserClock = d.finite(); accumulator = d.finite().also { require(it >= 0 && it < Rules.STEP + 1e-8) }
                    paddle.x = d.finite(); paddle.targetX = d.finite()
                    paddle.width = d.finite().also { require(it in Rules.PADDLE_WIDTH..250.0) }
                    paddle.wideTime = d.nonnegative(); paddle.magnetTime = d.nonnegative(); paddle.laserTime = d.nonnegative(); paddle.clamp()
                    bodies.clear(); balls.clear()
                    repeat(d.count(1500)) {
                        val id = d.count(2047); val center = d.vec(); val half = d.vec()
                        require(half.x in 1.0..400.0 && half.y in 1.0..500.0)
                        bodies.add(Body(id, center, half, Kind.entries[d.readInt()], d.readInt(), d.count(20),
                            d.finite(), d.finite(), d.finite(), d.readBoolean()))
                    }
                    require(bodies.map { it.id }.distinct().size == bodies.size)
                    repeat(d.count(Rules.MAX_BALLS)) {
                        balls.add(Ball(d.count(Int.MAX_VALUE), d.vec(), d.vec(), d.readBoolean(), d.finite(),
                            d.finite(), d.count(1), d.nonnegative()))
                    }
                    repeat(d.count(80)) { pickups.add(Pickup(d.vec(), Power.entries[d.readInt()], d.nonnegative())) }
                    invalidateGrid()
                }
            } else null
            require(d.available() == 0)
            SaveData(progress, e)
        }
    } catch (_: Exception) { null }
    private fun DataOutputStream.vec(v: Vec) { writeDouble(v.x); writeDouble(v.y) }
    private fun DataInputStream.finite() = readDouble().also { require(it.isFinite()) }
    private fun DataInputStream.nonnegative() = finite().also { require(it >= 0) }
    private fun DataInputStream.vec() = Vec(finite(), finite())
    private fun DataInputStream.count(max: Int) = readInt().also { require(it in 0..max) }
}
