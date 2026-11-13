package com.kimizha.minigame.core

import java.io.*
import java.util.zip.CRC32

data class SaveData(val progress: Progress, val engine: Engine?)
object SaveCodec {
    private const val MAGIC = 0x4D425231
    fun encode(data: SaveData): ByteArray {
        val raw = ByteArrayOutputStream()
        DataOutputStream(raw).use { o ->
            o.writeInt(1)
            o.writeInt(data.progress.unlocked); o.writeInt(data.progress.last); o.writeInt(data.progress.best)
            val e = data.engine
            o.writeBoolean(e != null)
            if (e != null) {
                o.writeInt(e.level); o.writeBoolean(e.infinite); o.writeLong(e.seed); o.writeInt(e.phase.ordinal)
                o.writeInt(e.stock); o.writeInt(e.roundStock); o.writeInt(e.score); o.writeInt(e.rounds)
                o.vec(e.launcher); o.vec(e.direction)
                o.writeDouble(e.elapsed); o.writeDouble(e.flight)
                o.writeInt(e.queue); o.writeDouble(e.shotClock); o.writeInt(e.nextId); o.writeDouble(e.firstReturn); o.writeInt(e.combo)
                o.writeBoolean(e.effectExplosion); o.writeBoolean(e.effectPierce); o.writeBoolean(e.effectDamage); o.writeBoolean(e.effectSlow)
                o.writeInt(e.bodies.size)
                for (b in e.bodies) {
                    o.writeInt(b.id); o.vec(b.center); o.vec(b.half); o.writeInt(b.hp); o.writeInt(b.kind.ordinal)
                    o.writeDouble(b.angle); o.writeDouble(b.homeX); o.writeDouble(b.vx)
                }
                o.writeInt(e.balls.size)
                for (b in e.balls) {
                    o.writeInt(b.id); o.vec(b.pos); o.vec(b.vel); o.writeDouble(b.age)
                    o.writeInt(b.pierce); o.writeInt(b.explosion); o.writeInt(b.damage); o.writeDouble(b.slow)
                    o.writeBoolean(b.split); o.writeInt(b.ignored); o.writeDouble(b.ignoreTime)
                }
                o.writeInt(e.pickups.size)
                for (p in e.pickups) { o.vec(p.pos); o.writeInt(p.power.ordinal); o.writeDouble(p.life) }
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
        require(bytes.size in 25..200_000)
        val i = DataInputStream(ByteArrayInputStream(bytes))
        require(i.readInt() == MAGIC)
        val checksum = i.readLong()
        val raw = i.readBytes()
        require(CRC32().apply { update(raw) }.value == checksum)
        DataInputStream(ByteArrayInputStream(raw)).use { d ->
            require(d.readInt() == 1)
            val progress = Progress(d.readInt().coerceIn(1, 30), d.readInt().coerceIn(1, 30), d.readInt().coerceAtLeast(0))
            val e = if (d.readBoolean()) {
                val level = d.readInt(); require(level in 1..10000)
                Engine(level, d.readBoolean(), d.readLong()).apply {
                    phase = Phase.entries[d.readInt()]
                    stock = d.readInt().also { require(it in 3..Rules.MAX_BALLS) }
                    roundStock = d.readInt().also { require(it in 3..Rules.MAX_BALLS) }
                    score = d.readInt(); rounds = d.readInt()
                    launcher = d.vec(); direction = d.vec()
                    elapsed = d.finite(); flight = d.finite()
                    queue = d.readInt().also { require(it in 0..Rules.MAX_BALLS) }
                    shotClock = d.finite(); nextId = d.readInt(); firstReturn = d.finite(); combo = d.readInt()
                    effectExplosion = d.readBoolean(); effectPierce = d.readBoolean(); effectDamage = d.readBoolean(); effectSlow = d.readBoolean()
                    bodies.clear()
                    repeat(d.count(200)) {
                        bodies.add(Body(d.readInt(), d.vec(), d.vec(), d.readInt(), Kind.entries[d.readInt()], d.finite(), d.finite(), d.finite()))
                    }
                    repeat(d.count(300)) {
                        balls.add(Ball(d.readInt(), d.vec(), d.vec(), d.finite(), d.readInt(), d.readInt(), d.readInt(),
                            d.finite(), d.readBoolean(), d.readInt(), d.finite()))
                    }
                    repeat(d.count(80)) { pickups.add(Pickup(d.vec(), Power.entries[d.readInt()], d.finite())) }
                }
            } else null
            SaveData(progress, e)
        }
    } catch (_: Exception) { null }
    private fun DataOutputStream.vec(v: Vec) { writeDouble(v.x); writeDouble(v.y) }
    private fun DataInputStream.finite() = readDouble().also { require(it.isFinite()) }
    private fun DataInputStream.vec() = Vec(finite(), finite())
    private fun DataInputStream.count(max: Int) = readInt().also { require(it in 0..max) }
}
