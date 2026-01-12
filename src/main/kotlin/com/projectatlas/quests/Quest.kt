package com.projectatlas.quests

import org.bukkit.entity.EntityType

/**
 * Quest data model
 */
data class Quest(
    val id: String,
    val name: String,
    val description: String,
    val difficulty: Difficulty,
    val objective: QuestObjective,
    val timeLimitSeconds: Int? = null, // null = no time limit
    val reward: Double = 0.0,
    val hint: String = "Check your surroundings."
)

enum class Difficulty(val displayName: String, val healthMultiplier: Double, val mobCountMultiplier: Double) {
    EASY("Easy", 1.0, 1.0),
    MEDIUM("Medium", 1.5, 1.5),
    HARD("Hard", 2.0, 2.0),
    NIGHTMARE("Nightmare", 3.0, 3.0)
}

sealed class QuestObjective {
    data class KillMobs(val mobType: EntityType, val count: Int) : QuestObjective()
    data class KillHorde(val waveCount: Int, val mobsPerWave: Int) : QuestObjective()
    data class FetchItem(val material: org.bukkit.Material, val count: Int) : QuestObjective()
    data class FindNPC(val npcName: String) : QuestObjective()
}

/**
 * Tracks a player's active quest progress
 */
data class ActiveQuest(
    val quest: Quest,
    val startTime: Long = System.currentTimeMillis(),
    var progress: Int = 0 // e.g., kills made or items collected
) {
    fun isComplete(): Boolean {
        return when (val obj = quest.objective) {
            is QuestObjective.KillMobs -> progress >= obj.count
            is QuestObjective.KillHorde -> progress >= (obj.waveCount * obj.mobsPerWave)
            is QuestObjective.FetchItem -> progress >= obj.count
            is QuestObjective.FindNPC -> progress >= 1
        }
    }
    
    fun isExpired(): Boolean {
        val limit = quest.timeLimitSeconds ?: return false
        val elapsed = (System.currentTimeMillis() - startTime) / 1000
        return elapsed >= limit
    }
    
    fun getRemainingSeconds(): Int? {
        val limit = quest.timeLimitSeconds ?: return null
        val elapsed = ((System.currentTimeMillis() - startTime) / 1000).toInt()
        return (limit - elapsed).coerceAtLeast(0)
    }
    
    fun getTargetCount(): Int {
        return when (val obj = quest.objective) {
            is QuestObjective.KillMobs -> obj.count
            is QuestObjective.KillHorde -> obj.waveCount * obj.mobsPerWave
            is QuestObjective.FetchItem -> obj.count
            is QuestObjective.FindNPC -> 1
        }
    }
}
