package moe.comico.reader

import kotlin.math.pow
import kotlin.math.roundToLong

data class LevelProgress(val level: Int, val done: Long, val step: Long)

// Comico uses a 500 XP starting requirement, increasing by 20% per level.
fun levelProgress(exp: Long): LevelProgress {
    val xp = exp.coerceAtLeast(0)
    fun threshold(level: Int) = (500 * (1.2.pow(level - 1) - 1) / 0.2).roundToLong()
    var level = 1
    while (level < 100 && xp >= threshold(level + 1)) level++
    if (level == 100) return LevelProgress(level, 1, 1)
    return LevelProgress(level, xp - threshold(level), (500 * 1.2.pow(level - 1)).roundToLong())
}
