package com.kimizha.minigame.core

import java.util.Random
import kotlin.math.*

object Levels {
    const val COUNT = 30
    val names = listOf("初见星光", "双翼", "微笑", "阶梯", "星环", "绕行", "回声", "双岛", "门廊", "流线",
        "破壁", "解锁", "双门", "跃迁", "裂隙", "惊喜", "花火", "星阵", "光束", "涟漪",
        "折线", "交响", "棱镜", "星核", "银河", "流动", "潮汐", "巡航", "星海", "终章")
    fun build(level: Int, infinite: Boolean = false, seed: Long = level.toLong()): MutableList<Body> {
        val random = Random(seed)
        val result = mutableListOf<Body>()
        var id = 0
        val rows = if (infinite) 6 else (2 + level / 5).coerceAtMost(7)
        val difficulty = level.coerceAtMost(30)
        for (r in 0 until rows) for (c in 0..8) {
            val include = if (infinite) random.nextDouble() > .24 else when (level % 5) {
                1 -> c in 1..7 && (r + c) % 3 != 0
                2 -> abs(c - 4) >= r % 3
                3 -> (c + r) % 2 == 0 || r == rows - 1
                4 -> c in (r % 3)..(8 - r % 3)
                else -> c == 1 || c == 7 || r == 0 || r == rows - 1
            }
            if (!include) continue
            val hp = if (difficulty <= 5) 1 + (r + c) % 3
                else if (difficulty <= 15) 2 + (r * 3 + c + difficulty) % 8
                else 3 + (r * 5 + c * 2 + difficulty) % (difficulty - 5)
            result.add(Body(id++, Vec(52.0 + c * 77, 175.0 + r * 66), Vec(33.0, 27.0), hp, Kind.BLOCK))
        }
        // All barriers have broad side openings: never form a closed enclosure.
        if (level >= 6) {
            result.add(Body(id++, Vec(260.0, 720.0), Vec(85.0, 12.0), -1, Kind.WALL))
            if (level % 2 == 0) result.add(Body(id++, Vec(500.0, 820.0), Vec(12.0, 65.0), -1, Kind.WALL))
        }
        if (level >= 11) result.add(Body(id++, Vec(470.0, 710.0), Vec(72.0, 15.0), 3 + level / 3, Kind.BREAKABLE))
        if (level >= 21) result.add(Body(id++, Vec(200.0, 900.0), Vec(83.0, 10.0), -1, Kind.PADDLE, -PI / 5))
        if (level >= 26) result.add(Body(id, Vec(410.0, 930.0), Vec(60.0, 12.0), -1, Kind.MOVING))
        return result
    }
}
