package com.projectatlas.skills

import org.bukkit.Material

/**
 * Represents a single node in the skill tree.
 * Inspired by Path of Exile, Diablo, Skyrim, Monster Hunter, and more.
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
    MASTERY("Mastery", "§d"), // Keystones
    DARK_ARTS("Dark Arts", "§5"), // Risk/Reward mechanics
    UTILITY("Utility", "§e") // Quality of life
}

enum class NodeTier(val displayName: String, val pointCost: Int) {
    MINOR("Minor", 1),      // Small bonuses
    NOTABLE("Notable", 2),   // Medium bonuses
    KEYSTONE("Keystone", 3)  // Major bonuses with trade-offs
}

/**
 * Defines what effect a skill node provides when unlocked.
 * Draws inspiration from multiple games for unique mechanics.
 */
sealed class SkillEffect {
    // ═══ BASIC STATS ═══
    data class MaxHealth(val bonus: Double) : SkillEffect()
    data class MovementSpeed(val bonus: Float) : SkillEffect()
    data class Armor(val bonus: Double) : SkillEffect()
    
    // ═══ DAMAGE (Path of Exile style) ═══
    data class MeleeDamage(val multiplier: Double) : SkillEffect()
    data class BowDamage(val multiplier: Double) : SkillEffect()
    data class AxeDamage(val multiplier: Double) : SkillEffect()
    data class SwordDamage(val multiplier: Double) : SkillEffect()
    data class CritChance(val bonus: Double) : SkillEffect()
    data class CritMultiplier(val bonus: Double) : SkillEffect() // Crit damage multiplier
    
    // ═══ LIFE/MANA (Diablo/PoE) ═══
    data class LifeLeech(val percent: Double) : SkillEffect() // Heal % of damage dealt
    data class LifeOnKill(val amount: Double) : SkillEffect() // Flat heal on kill
    data class Regeneration(val level: Int) : SkillEffect()
    
    // ═══ DEFENSE (Dark Souls/Monster Hunter) ═══
    data class Thorns(val percent: Double) : SkillEffect() // Reflect damage
    data class DodgeChance(val percent: Double) : SkillEffect() // Chance to avoid damage
    data class DivineBlessingChance(val percent: Double) : SkillEffect() // Chance to halve damage
    data class Poise(val enabled: Boolean) : SkillEffect() // Knockback resistance
    data class FireResistance(val level: Int) : SkillEffect()
    
    // ═══ EXECUTE/BERSERKER (Diablo) ═══
    data class ExecuteDamage(val thresholdPercent: Double, val bonusMultiplier: Double) : SkillEffect() // Bonus dmg when enemy <X% HP
    data class BerserkerMode(val hpThreshold: Double, val damageBonus: Double) : SkillEffect() // More damage when YOU are low HP
    data class Rampage(val stacksPerKill: Int, val maxStacks: Int) : SkillEffect() // Temporary buff on kill
    
    // ═══ STEALTH (Skyrim) ═══
    data class SneakDamage(val multiplier: Double) : SkillEffect() // Backstab damage
    data class ReducedAggroRange(val reduction: Double) : SkillEffect() // Mobs detect you later
    data class Invisibility(val durationTicks: Int) : SkillEffect() // Brief invis on sneak
    
    // ═══ MINING (Terraria/Modded MC) ═══
    data class MiningSpeed(val level: Int) : SkillEffect() // Haste
    data class VeinMiner(val enabled: Boolean) : SkillEffect() // Mine connected ores
    data class AutoSmelt(val enabled: Boolean) : SkillEffect() // Ores drop smelted form
    data class DoubleDrop(val chance: Double) : SkillEffect() // Chance for 2x drops
    data class Magnetism(val range: Double) : SkillEffect() // Auto-pickup range
    data class LuckBonus(val level: Int) : SkillEffect()
    
    // ═══ MOBILITY (Warframe/Movement Shooters) ═══
    data class JumpBoost(val level: Int) : SkillEffect()
    data class DoubleJump(val enabled: Boolean) : SkillEffect() // Second jump in air
    data class Dash(val cooldownTicks: Int, val distance: Double) : SkillEffect() // Blink forward
    data class NoFallDamage(val enabled: Boolean) : SkillEffect()
    data class WallClimb(val enabled: Boolean) : SkillEffect() // Climb walls briefly
    
    // ═══ UTILITY (Quality of Life) ═══
    data class NightVision(val enabled: Boolean) : SkillEffect()
    data class WaterBreathing(val enabled: Boolean) : SkillEffect()
    data class Saturation(val level: Int) : SkillEffect()
    data class XpBonus(val multiplier: Double) : SkillEffect()
    data class SoulBinding(val slots: Int) : SkillEffect() // Keep X items on death
    data class Lumberjack(val enabled: Boolean) : SkillEffect() // Chop whole trees
    
    // ═══ COMPANION (Summoner) ═══
    data class SummonWolf(val count: Int) : SkillEffect() // Summon wolf companions
    data class SummonGolem(val enabled: Boolean) : SkillEffect() // Iron golem ally
    
    // ═══ AURAS (PoE Party Play) ═══
    data class AuraRegeneration(val radius: Double, val level: Int) : SkillEffect() // Regen nearby allies
    data class AuraSpeed(val radius: Double, val bonus: Float) : SkillEffect() // Speed nearby allies
    data class AuraStrength(val radius: Double, val bonus: Double) : SkillEffect() // Damage boost to allies
    
    // ═══ ACTIVE ABILITIES (Class Replacement) ═══
    data class ActiveFireball(val cooldownTicks: Int) : SkillEffect()
    data class ActiveShieldWall(val durationTicks: Int, val cooldownTicks: Int) : SkillEffect()
    data class ActiveHealingPulse(val amount: Double, val cooldownTicks: Int) : SkillEffect()
    
    // Compound (for toString in GUI)
    override fun toString(): String = when (this) {
        is MaxHealth -> "+${bonus.toInt()} Max Health"
        is MovementSpeed -> "+${(bonus * 100).toInt()}% Movement Speed"
        is Armor -> "+${bonus.toInt()} Armor"
        is MeleeDamage -> "+${((multiplier - 1) * 100).toInt()}% Melee Damage"
        is BowDamage -> "+${((multiplier - 1) * 100).toInt()}% Bow Damage"
        is AxeDamage -> "+${((multiplier - 1) * 100).toInt()}% Axe Damage"
        is SwordDamage -> "+${((multiplier - 1) * 100).toInt()}% Sword Damage"
        is CritChance -> "+${(bonus * 100).toInt()}% Critical Chance"
        is CritMultiplier -> "+${((bonus - 1) * 100).toInt()}% Critical Damage"
        is LifeLeech -> "Leech ${(percent * 100).toInt()}% damage as health"
        is LifeOnKill -> "+${amount.toInt()} HP on kill"
        is Regeneration -> "Regeneration ${level + 1}"
        is Thorns -> "Reflect ${(percent * 100).toInt()}% damage to attackers"
        is DodgeChance -> "${(percent * 100).toInt()}% chance to dodge attacks"
        is DivineBlessingChance -> "${(percent * 100).toInt()}% chance to halve damage"
        is Poise -> "Immune to knockback"
        is FireResistance -> "Fire Resistance"
        is ExecuteDamage -> "+${((bonusMultiplier - 1) * 100).toInt()}% damage to enemies below ${(thresholdPercent * 100).toInt()}% HP"
        is BerserkerMode -> "+${((damageBonus - 1) * 100).toInt()}% damage when below ${(hpThreshold * 100).toInt()}% HP"
        is Rampage -> "Gain speed/damage stacks on kill (max $maxStacks)"
        is SneakDamage -> "${multiplier.toInt()}x damage from behind"
        is ReducedAggroRange -> "Mobs detect you ${(reduction * 100).toInt()}% slower"
        is Invisibility -> "Brief invisibility when sneaking"
        is MiningSpeed -> "Haste ${level + 1}"
        is VeinMiner -> "Mine entire ore veins at once"
        is AutoSmelt -> "Ores drop smelted ingots"
        is DoubleDrop -> "${(chance * 100).toInt()}% chance for double drops"
        is Magnetism -> "Auto-pickup items within ${range.toInt()} blocks"
        is LuckBonus -> "Luck ${level + 1}"
        is JumpBoost -> "Jump Boost ${level + 1}"
        is DoubleJump -> "Double jump in mid-air"
        is Dash -> "Dash forward (${cooldownTicks / 20}s cooldown)"
        is NoFallDamage -> "Immune to fall damage"
        is WallClimb -> "Briefly climb walls"
        is NightVision -> "Permanent Night Vision"
        is WaterBreathing -> "Permanent Water Breathing"
        is Saturation -> "Reduced hunger drain"
        is XpBonus -> "+${((multiplier - 1) * 100).toInt()}% XP gain"
        is SoulBinding -> "Keep $slots items on death"
        is Lumberjack -> "Chop entire trees at once"
        is SummonWolf -> "Summon $count wolf companion(s)"
        is SummonGolem -> "Summon an iron golem ally"
        is AuraRegeneration -> "Allies within ${radius.toInt()}m gain Regen"
        is AuraSpeed -> "Allies within ${radius.toInt()}m gain Speed"
        is AuraStrength -> "Allies within ${radius.toInt()}m deal +${((bonus - 1) * 100).toInt()}% damage"
        is ActiveFireball -> "Unlock Fireball Ability (Blaze Rod)"
        is ActiveShieldWall -> "Unlock Shield Wall Ability (Sword)"
        is ActiveHealingPulse -> "Unlock Healing Pulse Ability (Golden Apple)"
    }
}
