package com.kimizha.minigame.core

import java.util.Random
import kotlin.math.PI

data class ObstacleSpec(val x: Double, val y: Double, val w: Double, val h: Double, val kind: Kind, val angle: Double = 0.0)
data class LevelSpec(val pattern: PixelPattern, val obstacles: List<ObstacleSpec>)
object Levels {
    const val COUNT = 30
    const val CELL = 20.0
    val palette = mapOf('r' to 0xFFFF637F.toInt(), 'p' to 0xFFFF9BCB.toInt(), 'o' to 0xFFFFAD68.toInt(),
        'y' to 0xFFFFE27A.toInt(), 'g' to 0xFF83E7A4.toInt(), 't' to 0xFF50DDE5.toInt(),
        'b' to 0xFF76A5FF.toInt(), 'v' to 0xFFB39AFF.toInt(), 'w' to 0xFFEFF5FD.toInt(), 'k' to 0xFF354B7D.toInt())
    val names = LevelPatterns.all.map { it.name }
    fun spec(level: Int, infinite: Boolean = false, seed: Long = level.toLong()): LevelSpec {
        val random = Random(seed)
        val pattern = LevelPatterns.all[if (infinite) random.nextInt(COUNT) else (level - 1).coerceIn(0, COUNT - 1)]
        val obstacles = mutableListOf<ObstacleSpec>()
        if (level >= 6) {
            // Side guides have open tops and bottoms; no unbreakable perimeter encloses a target.
            obstacles.add(ObstacleSpec(20.0, 415.0, 12.0, 210.0, Kind.WALL))
            obstacles.add(ObstacleSpec(700.0, 415.0, 12.0, 210.0, Kind.WALL))
            obstacles.add(ObstacleSpec(360.0, 770.0, 140.0, 16.0, Kind.WALL))
        }
        if (level >= 11) obstacles.add(ObstacleSpec(505.0, 790.0, 86.0, 18.0, Kind.BREAKABLE))
        if (level >= 21) {
            obstacles.add(ObstacleSpec(165.0, 850.0, 96.0, 14.0, Kind.PADDLE, -PI / 5))
            obstacles.add(ObstacleSpec(555.0, 870.0, 96.0, 14.0, Kind.PADDLE, PI / 5))
        }
        if (level >= 26) obstacles.add(ObstacleSpec(360.0, 935.0, 90.0, 16.0, Kind.MOVING))
        return LevelSpec(pattern, obstacles)
    }
    fun build(level: Int, infinite: Boolean = false, seed: Long = level.toLong()): MutableList<Body> {
        val spec = spec(level, infinite, seed)
        val bodies = mutableListOf<Body>()
        var id = 0
        for ((r, row) in spec.pattern.rows.withIndex()) for ((c, pixel) in row.withIndex()) {
            val color = palette[pixel] ?: continue
            bodies.add(Body(id++, Vec(50.0 + c * CELL, 180.0 + r * CELL), Vec(9.0, 9.0), Kind.BLOCK, color))
        }
        for (o in spec.obstacles) bodies.add(Body(id++, Vec(o.x, o.y), Vec(o.w / 2, o.h / 2), o.kind,
            color = if (o.kind == Kind.BREAKABLE) 0xFFC0CAD8.toInt() else 0xFFE2EBF5.toInt(),
            durability = if (o.kind == Kind.BREAKABLE) 3 else 0, angle = o.angle))
        return bodies
    }
}
