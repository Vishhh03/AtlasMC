package com.projectatlas.skills

import org.bukkit.Material

/**
 * Represents a single node in the skill tree.
 * Reworked for practical gameplay integration.
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
    val cost: Int = 1, // Skill points required
    val exclusiveWith: List<String> = emptyList() // Cannot have these if you have this
)

enum class SkillCategory(val displayName: String, val color: String) {
    // Core Roles
    VANGUARD("Vanguard", "§9"),      // Tank/Defense
    BERSERKER("Berserker", "§c"),    // DPS/Combat
    SCOUT("Scout", "§b"),            // Mobility/Stealth
    ARTISAN("Artisan", "§6"),        // Mining/Crafting
    SETTLER("Settler", "§2"),        // City/Social/Economy
    
    // Special
    CORE("Core", "§f"),              // Starting/Universal
    KEYSTONE("Keystone", "§d")       // Powerful exclusive nodes
}

enum class NodeTier(val displayName: String, val pointCost: Int) {
    MINOR("Minor", 1),      // Small bonuses
    NOTABLE("Notable", 2),  // Medium bonuses
    KEYSTONE("Keystone", 3) // Major bonuses, often exclusive
}

/**
 * Skill Effects - Only effects that ARE implemented in gameplay.
 */
sealed class SkillEffect {
    // ═══ STATS (Applied on join/unlock) ═══
    data class MaxHealth(val bonus: Double) : SkillEffect()
    data class MovementSpeed(val bonus: Float) : SkillEffect()
    data class Armor(val bonus: Double) : SkillEffect()
    
    // ═══ COMBAT (Applied in EntityDamageByEntityEvent) ═══
    data class MeleeDamage(val multiplier: Double) : SkillEffect()
    data class BowDamage(val multiplier: Double) : SkillEffect()
    data class CritChance(val percent: Double) : SkillEffect()
    data class CritMultiplier(val bonus: Double) : SkillEffect()
    data class LifeLeech(val percent: Double) : SkillEffect()
    data class LifeOnKill(val amount: Double) : SkillEffect()
    data class ExecuteDamage(val thresholdPercent: Double, val bonusMultiplier: Double) : SkillEffect()
    data class BerserkerMode(val hpThreshold: Double, val damageBonus: Double) : SkillEffect()
    data class SneakDamage(val multiplier: Double) : SkillEffect()
    data class Thorns(val percent: Double) : SkillEffect()
    data class SiegeDefender(val damageMultiplier: Double) : SkillEffect()
    
    // ═══ DEFENSE (Applied in EntityDamageEvent) ═══
    data class DodgeChance(val percent: Double) : SkillEffect()
    data class DivineBlessingChance(val percent: Double) : SkillEffect()
    data class Poise(val enabled: Boolean) : SkillEffect()
    data class FireResistance(val enabled: Boolean) : SkillEffect()
    data class ColdResistance(val enabled: Boolean) : SkillEffect()
    
    // ═══ REGENERATION (Applied in periodic task) ═══
    data class Regeneration(val level: Int) : SkillEffect()
    
    // ═══ MOBILITY (Applied in movement events/tasks) ═══
    data class JumpBoost(val level: Int) : SkillEffect()
    data class DoubleJump(val enabled: Boolean) : SkillEffect()
    data class NoFallDamage(val enabled: Boolean) : SkillEffect()
    data class SwimSpeed(val level: Int) : SkillEffect()
    
    // ═══ MINING (Applied in BlockBreakEvent) ═══
    data class MiningSpeed(val level: Int) : SkillEffect()
    data class VeinMiner(val enabled: Boolean) : SkillEffect()
    data class AutoSmelt(val enabled: Boolean) : SkillEffect()
    data class DoubleDrop(val chance: Double) : SkillEffect()
    data class LuckBonus(val level: Int) : SkillEffect()
    data class Lumberjack(val enabled: Boolean) : SkillEffect()
    
    // ═══ UTILITY (Applied in various events) ═══
    data class NightVision(val enabled: Boolean) : SkillEffect()
    data class WaterBreathing(val enabled: Boolean) : SkillEffect()
    data class Magnetism(val range: Double) : SkillEffect()
    data class XpBonus(val multiplier: Double) : SkillEffect()
    data class SoulBinding(val slots: Int) : SkillEffect()
    
    // ═══ SETTLER/ECONOMY (Applied in specific managers) ═══
    data class RestfulSleep(val multiplier: Double) : SkillEffect()
    data class Diplomacy(val discountPercent: Double) : SkillEffect()
    data class BountyHunter(val goldMultiplier: Double) : SkillEffect()
    
    // Display
    override fun toString(): String = when (this) {
        is MaxHealth -> "+${bonus.toInt()} Max Health"
        is MovementSpeed -> "+${(bonus * 100).toInt()}% Movement Speed"
        is Armor -> "+${bonus.toInt()} Armor"
        is MeleeDamage -> "+${((multiplier - 1) * 100).toInt()}% Melee Damage"
        is BowDamage -> "+${((multiplier - 1) * 100).toInt()}% Bow Damage"
        is CritChance -> "+${(percent * 100).toInt()}% Critical Chance"
        is CritMultiplier -> "+${((bonus - 1) * 100).toInt()}% Critical Damage"
        is LifeLeech -> "Leech ${(percent * 100).toInt()}% damage as health"
        is LifeOnKill -> "+${amount.toInt()} HP on kill"
        is ExecuteDamage -> "+${((bonusMultiplier - 1) * 100).toInt()}% damage to enemies below ${(thresholdPercent * 100).toInt()}% HP"
        is BerserkerMode -> "+${((damageBonus - 1) * 100).toInt()}% damage when below ${(hpThreshold * 100).toInt()}% HP"
        is SneakDamage -> "${multiplier.toInt()}x damage from behind"
        is Thorns -> "Reflect ${(percent * 100).toInt()}% damage to attackers"
        is SiegeDefender -> "+${((damageMultiplier - 1) * 100).toInt()}% Damage vs Raiders"
        is DodgeChance -> "${(percent * 100).toInt()}% chance to dodge attacks"
        is DivineBlessingChance -> "${(percent * 100).toInt()}% chance to halve damage"
        is Poise -> "Immune to knockback"
        is FireResistance -> "Fire Resistance"
        is ColdResistance -> "Cold Resistance (No Hypothermia)"
        is Regeneration -> "Regeneration ${level + 1}"
        is JumpBoost -> "Jump Boost ${level + 1}"
        is DoubleJump -> "Double jump in mid-air"
        is NoFallDamage -> "Immune to fall damage"
        is SwimSpeed -> "Swim Speed ${level + 1}"
        is MiningSpeed -> "Haste ${level + 1}"
        is VeinMiner -> "Mine entire ore veins at once"
        is AutoSmelt -> "Ores drop smelted ingots"
        is DoubleDrop -> "${(chance * 100).toInt()}% chance for double drops"
        is LuckBonus -> "Luck ${level + 1}"
        is Lumberjack -> "Chop entire trees at once"
        is NightVision -> "Permanent Night Vision"
        is WaterBreathing -> "Permanent Water Breathing"
        is Magnetism -> "Auto-pickup items within ${range.toInt()} blocks"
        is XpBonus -> "+${((multiplier - 1) * 100).toInt()}% XP gain"
        is SoulBinding -> "Keep $slots items on death"
        is RestfulSleep -> "+${((multiplier - 1) * 100).toInt()}% Healing from Sleep"
        is Diplomacy -> "${(discountPercent * 100).toInt()}% Discount on City Costs"
        is BountyHunter -> "+${((goldMultiplier - 1) * 100).toInt()}% Quest Gold Reward"
    }
}
