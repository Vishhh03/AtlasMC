package com.projectatlas.skills

import org.bukkit.Material

/**
 * Represents a single node in the skill tree.
 * Inspired by Path of Exile's passive skill tree.
 */
data class SkillNode(
    val id: String,
    val name: String,
    val description: String,
    val icon: Material,
    val category: SkillCategory,
    val tier: NodeTier,
    val connections: List<String>, // IDs of connected nodes
    val effect: SkillEffect,
    val cost: Int = 1 // Skill points required
)

enum class SkillCategory(val displayName: String, val color: String) {
    COMBAT("Combat", "§c"),
    DEFENSE("Defense", "§9"),
    MOBILITY("Mobility", "§b"),
    MINING("Mining", "§6"),
    ARCHERY("Archery", "§a"),
    SURVIVAL("Survival", "§2"),
    MASTERY("Mastery", "§d") // Keystones
}

enum class NodeTier(val displayName: String, val pointCost: Int) {
    MINOR("Minor", 1),      // Small bonuses
    NOTABLE("Notable", 2),   // Medium bonuses
    KEYSTONE("Keystone", 3)  // Major bonuses with trade-offs
}

/**
 * Defines what effect a skill node provides when unlocked.
 */
sealed class SkillEffect {
    // Stat Bonuses
    data class MaxHealth(val bonus: Double) : SkillEffect()
    data class MovementSpeed(val bonus: Float) : SkillEffect()
    data class AttackDamage(val multiplier: Double) : SkillEffect()
    data class MiningSpeed(val level: Int) : SkillEffect() // Haste level
    data class Armor(val bonus: Double) : SkillEffect()
    data class Regeneration(val level: Int) : SkillEffect()
    data class BowDamage(val multiplier: Double) : SkillEffect()
    data class AxeDamage(val multiplier: Double) : SkillEffect()
    data class SwordDamage(val multiplier: Double) : SkillEffect()
    data class CritChance(val bonus: Double) : SkillEffect()
    data class Knockback(val level: Int) : SkillEffect()
    data class FireResistance(val level: Int) : SkillEffect()
    data class NightVision(val enabled: Boolean) : SkillEffect()
    data class WaterBreathing(val enabled: Boolean) : SkillEffect()
    data class JumpBoost(val level: Int) : SkillEffect()
    data class Saturation(val level: Int) : SkillEffect()
    data class LuckBonus(val level: Int) : SkillEffect()
    data class XpBonus(val multiplier: Double) : SkillEffect()
    
    // Compound (for toString in GUI)
    override fun toString(): String = when (this) {
        is MaxHealth -> "+${bonus.toInt()} Max Health"
        is MovementSpeed -> "+${(bonus * 100).toInt()}% Movement Speed"
        is AttackDamage -> "+${((multiplier - 1) * 100).toInt()}% Attack Damage"
        is MiningSpeed -> "Haste $level"
        is Armor -> "+${bonus.toInt()} Armor"
        is Regeneration -> "Regeneration $level"
        is BowDamage -> "+${((multiplier - 1) * 100).toInt()}% Bow Damage"
        is AxeDamage -> "+${((multiplier - 1) * 100).toInt()}% Axe Damage"
        is SwordDamage -> "+${((multiplier - 1) * 100).toInt()}% Sword Damage"
        is CritChance -> "+${(bonus * 100).toInt()}% Critical Chance"
        is Knockback -> "Knockback $level"
        is FireResistance -> "Fire Resistance $level"
        is NightVision -> "Night Vision"
        is WaterBreathing -> "Water Breathing"
        is JumpBoost -> "Jump Boost $level"
        is Saturation -> "Saturation $level"
        is LuckBonus -> "Luck $level"
        is XpBonus -> "+${((multiplier - 1) * 100).toInt()}% XP Gain"
    }
}
