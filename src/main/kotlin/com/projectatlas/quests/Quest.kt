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
    data class VisitBiome(val biomeName: String) : QuestObjective()
    data class TravelDistance(val blocks: Int) : QuestObjective()
    data class SurviveTime(val seconds: Int) : QuestObjective()
    data class MineBlocks(val material: org.bukkit.Material, val count: Int) : QuestObjective()
    data class CraftItems(val material: org.bukkit.Material, val count: Int) : QuestObjective()
    data class ReachLocation(val x: Int, val z: Int, val radius: Int, val locationName: String) : QuestObjective()
    data class KillAnyMobs(val count: Int) : QuestObjective() // Kill any hostile mob
    data class FishItems(val count: Int) : QuestObjective()
    data class TameAnimals(val count: Int) : QuestObjective()
    data class TradeWithVillager(val count: Int) : QuestObjective()
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
            is QuestObjective.VisitBiome -> progress >= 1
            is QuestObjective.TravelDistance -> progress >= obj.blocks
            is QuestObjective.SurviveTime -> progress >= obj.seconds
            is QuestObjective.MineBlocks -> progress >= obj.count
            is QuestObjective.CraftItems -> progress >= obj.count
            is QuestObjective.ReachLocation -> progress >= 1
            is QuestObjective.KillAnyMobs -> progress >= obj.count
            is QuestObjective.FishItems -> progress >= obj.count
            is QuestObjective.TameAnimals -> progress >= obj.count
            is QuestObjective.TradeWithVillager -> progress >= obj.count
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
            is QuestObjective.VisitBiome -> 1
            is QuestObjective.TravelDistance -> obj.blocks
            is QuestObjective.SurviveTime -> obj.seconds
            is QuestObjective.MineBlocks -> obj.count
            is QuestObjective.CraftItems -> obj.count
            is QuestObjective.ReachLocation -> 1
            is QuestObjective.KillAnyMobs -> obj.count
            is QuestObjective.FishItems -> obj.count
            is QuestObjective.TameAnimals -> obj.count
            is QuestObjective.TradeWithVillager -> obj.count
        }
    }
}
