package com.projectatlas.skills

import com.projectatlas.AtlasPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Massive Skill Tree System - Path of Exile inspired!
 * Features interconnected nodes across multiple categories.
 */
class SkillTreeManager(private val plugin: AtlasPlugin) : Listener {

    // Player's current viewport position in the skill tree grid
    private val viewportPositions = ConcurrentHashMap<UUID, Pair<Int, Int>>() // X, Y offset
    
    // The complete skill tree - a 15x15 grid conceptually
    private val skillTree: Map<String, SkillNode>
    private val gridLayout: Array<Array<String?>> // 15x15 grid of node IDs
    
    // Constants
    companion object {
        const val GRID_SIZE = 15
        const val VIEWPORT_WIDTH = 9
        const val VIEWPORT_HEIGHT = 5 // 5 rows for nodes, 1 for controls
        const val SKILL_POINTS_PER_LEVEL = 1
    }
    
    init {
        skillTree = buildSkillTree()
        gridLayout = buildGridLayout()
    }
    
    // ══════════════════════════════════════════════════════════════
    // SKILL TREE DEFINITION - The Massive Web of Passives
    // ══════════════════════════════════════════════════════════════
    
    private fun buildSkillTree(): Map<String, SkillNode> {
        val nodes = mutableMapOf<String, SkillNode>()
        
        // ═══ STARTING NODE (CENTER) ═══
        nodes["origin"] = SkillNode(
            "origin", "Awakening", "Your journey begins here.",
            Material.NETHER_STAR, SkillCategory.MASTERY, NodeTier.NOTABLE,
            listOf("combat_start", "defense_start", "mobility_start", "mining_start", "archery_start", "dark_start", "utility_start"),
            SkillEffect.MaxHealth(plugin.configManager.skillHealthBonus), cost = 0 // Free starting node
        )
        
        // ═══════════════════════════════════════════════════════════
        // COMBAT BRANCH (Red) - Damage & Lifesteal
        // ═══════════════════════════════════════════════════════════
        nodes["combat_start"] = SkillNode(
            "combat_start", "Warrior's Path", "Begin the path of combat.",
            Material.IRON_SWORD, SkillCategory.COMBAT, NodeTier.MINOR,
            listOf("origin", "sword_1", "axe_1", "leech_1", "execute_1", "fireball_1"),
            SkillEffect.MeleeDamage(plugin.configManager.skillMeleeMult)
        )
        
        // Active Ability: Fireball
        nodes["fireball_1"] = SkillNode(
            "fireball_1", "Fireball", "Active: Shoot a fireball (Blaze Rod)",
            Material.BLAZE_ROD, SkillCategory.COMBAT, NodeTier.KEYSTONE,
            listOf("combat_start"),
            SkillEffect.ActiveFireball(plugin.configManager.fireballCooldownTicks), cost = 3
        )
        
        // Sword Branch
        nodes["sword_1"] = SkillNode(
            "sword_1", "Blade Adept", "+10% Sword Damage",
            Material.STONE_SWORD, SkillCategory.COMBAT, NodeTier.MINOR,
            listOf("combat_start", "sword_2"),
            SkillEffect.SwordDamage(plugin.configManager.skillSwordMult)
        )
        nodes["sword_2"] = SkillNode(
            "sword_2", "Blade Master", "+15% Sword Damage",
            Material.IRON_SWORD, SkillCategory.COMBAT, NodeTier.NOTABLE,
            listOf("sword_1", "sword_3", "crit_1"),
            SkillEffect.SwordDamage(1.15)
        )
        nodes["sword_3"] = SkillNode(
            "sword_3", "Sword Saint", "+25% Sword Damage",
            Material.DIAMOND_SWORD, SkillCategory.COMBAT, NodeTier.KEYSTONE,
            listOf("sword_2"),
            SkillEffect.SwordDamage(1.25), cost = 3
        )
        
        // Axe Branch
        nodes["axe_1"] = SkillNode(
            "axe_1", "Axe Training", "+10% Axe Damage",
            Material.STONE_AXE, SkillCategory.COMBAT, NodeTier.MINOR,
            listOf("combat_start", "axe_2"),
            SkillEffect.AxeDamage(plugin.configManager.skillAxeMult)
        )
        nodes["axe_2"] = SkillNode(
            "axe_2", "Cleaver", "+15% Axe Damage",
            Material.IRON_AXE, SkillCategory.COMBAT, NodeTier.NOTABLE,
            listOf("axe_1", "axe_3"),
            SkillEffect.AxeDamage(1.15)
        )
        nodes["axe_3"] = SkillNode(
            "axe_3", "Executioner", "+30% Axe Damage",
            Material.NETHERITE_AXE, SkillCategory.COMBAT, NodeTier.KEYSTONE,
            listOf("axe_2"),
            SkillEffect.AxeDamage(1.30), cost = 3
        )
        
        // Life Leech Branch (Diablo/PoE)
        nodes["leech_1"] = SkillNode(
            "leech_1", "Vampiric Strikes", "Leech 5% damage as health",
            Material.REDSTONE, SkillCategory.COMBAT, NodeTier.NOTABLE,
            listOf("combat_start", "leech_2"),
            SkillEffect.LifeLeech(plugin.configManager.skillLifeLeechBase)
        )
        nodes["leech_2"] = SkillNode(
            "leech_2", "Blood Drinker", "Leech 10% damage as health",
            Material.CRIMSON_FUNGUS, SkillCategory.COMBAT, NodeTier.KEYSTONE,
            listOf("leech_1"),
            SkillEffect.LifeLeech(0.10), cost = 3
        )
        
        // Execute Branch (Diablo)
        nodes["execute_1"] = SkillNode(
            "execute_1", "Finishing Blow", "+25% damage to enemies <30% HP",
            Material.WITHER_SKELETON_SKULL, SkillCategory.COMBAT, NodeTier.NOTABLE,
            listOf("combat_start", "execute_2"),
            SkillEffect.ExecuteDamage(0.30, 1.25)
        )
        nodes["execute_2"] = SkillNode(
            "execute_2", "Coup de Grace", "+50% damage to enemies <20% HP",
            Material.DRAGON_HEAD, SkillCategory.COMBAT, NodeTier.KEYSTONE,
            listOf("execute_1"),
            SkillEffect.ExecuteDamage(0.20, 1.50), cost = 3
        )
        
        // Crit Branch
        nodes["crit_1"] = SkillNode(
            "crit_1", "Precision", "+5% Critical Chance",
            Material.SPECTRAL_ARROW, SkillCategory.COMBAT, NodeTier.MINOR,
            listOf("sword_2", "crit_2"),
            SkillEffect.CritChance(plugin.configManager.skillCritChanceBase)
        )
        nodes["crit_2"] = SkillNode(
            "crit_2", "Deadly Aim", "+10% Critical Chance",
            Material.END_CRYSTAL, SkillCategory.COMBAT, NodeTier.NOTABLE,
            listOf("crit_1", "crit_mult_1"),
            SkillEffect.CritChance(0.10)
        )
        nodes["crit_mult_1"] = SkillNode(
            "crit_mult_1", "Devastating Crits", "+50% Critical Damage",
            Material.TNT, SkillCategory.COMBAT, NodeTier.KEYSTONE,
            listOf("crit_2"),
            SkillEffect.CritMultiplier(1.50), cost = 3
        )
        
        // ═══════════════════════════════════════════════════════════
        // DEFENSE BRANCH (Blue) - Tank & Survival
        // ═══════════════════════════════════════════════════════════
        nodes["defense_start"] = SkillNode(
            "defense_start", "Guardian's Path", "Begin the path of protection.",
            Material.IRON_CHESTPLATE, SkillCategory.DEFENSE, NodeTier.MINOR,
            listOf("origin", "health_1", "armor_1", "regen_1", "thorns_1", "dodge_1"),
            SkillEffect.MaxHealth(2.0)
        )
        
        // Health Branch
        nodes["health_1"] = SkillNode(
            "health_1", "Vitality", "+2 Max Health",
            Material.APPLE, SkillCategory.DEFENSE, NodeTier.MINOR,
            listOf("defense_start", "health_2"),
            SkillEffect.MaxHealth(2.0)
        )
        nodes["health_2"] = SkillNode(
            "health_2", "Constitution", "+4 Max Health",
            Material.GOLDEN_APPLE, SkillCategory.DEFENSE, NodeTier.NOTABLE,
            listOf("health_1", "health_3"),
            SkillEffect.MaxHealth(4.0)
        )
        nodes["health_3"] = SkillNode(
            "health_3", "Colossus", "+10 Max Health",
            Material.ENCHANTED_GOLDEN_APPLE, SkillCategory.DEFENSE, NodeTier.KEYSTONE,
            listOf("health_2"),
            SkillEffect.MaxHealth(10.0), cost = 3
        )
        
        // Armor Branch
        nodes["armor_1"] = SkillNode(
            "armor_1", "Iron Skin", "+2 Armor",
            Material.IRON_INGOT, SkillCategory.DEFENSE, NodeTier.MINOR,
            listOf("defense_start", "armor_2"),
            SkillEffect.Armor(2.0)
        )
        nodes["armor_2"] = SkillNode(
            "armor_2", "Steel Wall", "+4 Armor",
            Material.IRON_BLOCK, SkillCategory.DEFENSE, NodeTier.NOTABLE,
            listOf("armor_1", "fire_res_1", "poise_1"),
            SkillEffect.Armor(4.0)
        )
        
        // Thorns Branch (Diablo)
        nodes["thorns_1"] = SkillNode(
            "thorns_1", "Spiked Armor", "Reflect 15% damage to attackers",
            Material.CACTUS, SkillCategory.DEFENSE, NodeTier.NOTABLE,
            listOf("defense_start", "thorns_2"),
            SkillEffect.Thorns(0.15)
        )
        nodes["thorns_2"] = SkillNode(
            "thorns_2", "Iron Maiden", "Reflect 30% damage to attackers",
            Material.SWEET_BERRIES, SkillCategory.DEFENSE, NodeTier.KEYSTONE,
            listOf("thorns_1"),
            SkillEffect.Thorns(0.30), cost = 3
        )
        
        // Dodge Branch (Monster Hunter)
        nodes["dodge_1"] = SkillNode(
            "dodge_1", "Evasion", "10% chance to dodge attacks",
            Material.FEATHER, SkillCategory.DEFENSE, NodeTier.NOTABLE,
            listOf("defense_start", "dodge_2"),
            SkillEffect.DodgeChance(0.10)
        )
        nodes["dodge_2"] = SkillNode(
            "dodge_2", "Phantom", "20% chance to dodge attacks",
            Material.PHANTOM_MEMBRANE, SkillCategory.DEFENSE, NodeTier.KEYSTONE,
            listOf("dodge_1"),
            SkillEffect.DodgeChance(0.20), cost = 3
        )
        
        // Divine Blessing (Monster Hunter)
        nodes["divine_1"] = SkillNode(
            "divine_1", "Divine Blessing", "25% chance to halve damage",
            Material.TOTEM_OF_UNDYING, SkillCategory.DEFENSE, NodeTier.KEYSTONE,
            listOf("health_3"),
            SkillEffect.DivineBlessingChance(0.25), cost = 3
        )
        
        // Poise (Dark Souls)
        nodes["poise_1"] = SkillNode(
            "poise_1", "Unbreakable", "Immune to knockback",
            Material.ANVIL, SkillCategory.DEFENSE, NodeTier.NOTABLE,
            listOf("armor_2"),
            SkillEffect.Poise(true)
        )
        
        // Regen & Fire Res
        nodes["regen_1"] = SkillNode(
            "regen_1", "Natural Recovery", "Regeneration I",
            Material.GLISTERING_MELON_SLICE, SkillCategory.DEFENSE, NodeTier.NOTABLE,
            listOf("defense_start", "regen_2"),
            SkillEffect.Regeneration(0)
        )
        nodes["regen_2"] = SkillNode(
            "regen_2", "Rapid Healing", "Regeneration II",
            Material.GOLDEN_CARROT, SkillCategory.DEFENSE, NodeTier.KEYSTONE,
            listOf("regen_1"),
            SkillEffect.Regeneration(1), cost = 3
        )
        nodes["fire_res_1"] = SkillNode(
            "fire_res_1", "Flame Ward", "Fire Resistance",
            Material.MAGMA_BLOCK, SkillCategory.DEFENSE, NodeTier.NOTABLE,
            listOf("armor_2"),
            SkillEffect.FireResistance(0)
        )
        
        // Active Ability: Shield Wall
        nodes["shield_wall_1"] = SkillNode(
            "shield_wall_1", "Shield Wall", "Active: Raise a protective barrier",
            Material.SHIELD, SkillCategory.DEFENSE, NodeTier.KEYSTONE,
            listOf("defense_start"),
            SkillEffect.ActiveShieldWall(plugin.configManager.shieldWallDurationTicks, plugin.configManager.shieldWallCooldownTicks), cost = 3
        )
        
        // ═══════════════════════════════════════════════════════════
        // MOBILITY BRANCH (Cyan) - Speed, Jumps, Dashes
        // ═══════════════════════════════════════════════════════════
        nodes["mobility_start"] = SkillNode(
            "mobility_start", "Runner's Path", "Begin the path of speed.",
            Material.FEATHER, SkillCategory.MOBILITY, NodeTier.MINOR,
            listOf("origin", "speed_1", "jump_1", "dash_1", "nofall_1"),
            SkillEffect.MovementSpeed(plugin.configManager.skillMovementSpeedBonus * 2)
        )
        
        // Speed Branch
        nodes["speed_1"] = SkillNode(
            "speed_1", "Quickfoot", "+5% Movement Speed",
            Material.LEATHER_BOOTS, SkillCategory.MOBILITY, NodeTier.MINOR,
            listOf("mobility_start", "speed_2"),
            SkillEffect.MovementSpeed(0.01f)
        )
        nodes["speed_2"] = SkillNode(
            "speed_2", "Fleet Footed", "+10% Movement Speed",
            Material.CHAINMAIL_BOOTS, SkillCategory.MOBILITY, NodeTier.NOTABLE,
            listOf("speed_1", "speed_3"),
            SkillEffect.MovementSpeed(0.02f)
        )
        nodes["speed_3"] = SkillNode(
            "speed_3", "Windrunner", "+20% Movement Speed",
            Material.DIAMOND_BOOTS, SkillCategory.MOBILITY, NodeTier.KEYSTONE,
            listOf("speed_2"),
            SkillEffect.MovementSpeed(0.04f), cost = 3
        )
        
        // Jump Branch (Warframe)
        nodes["jump_1"] = SkillNode(
            "jump_1", "Spring Step", "Jump Boost I",
            Material.SLIME_BALL, SkillCategory.MOBILITY, NodeTier.NOTABLE,
            listOf("mobility_start", "jump_2"),
            SkillEffect.JumpBoost(0)
        )
        nodes["jump_2"] = SkillNode(
            "jump_2", "Double Jump", "Jump again in mid-air!",
            Material.SLIME_BLOCK, SkillCategory.MOBILITY, NodeTier.KEYSTONE,
            listOf("jump_1"),
            SkillEffect.DoubleJump(true), cost = 3
        )
        
        // Dash (Warframe/Hollow Knight)
        nodes["dash_1"] = SkillNode(
            "dash_1", "Shadow Step", "Dash forward",
            Material.ENDER_PEARL, SkillCategory.MOBILITY, NodeTier.KEYSTONE,
            listOf("mobility_start"),
            SkillEffect.Dash(plugin.configManager.dashCooldownTicks, 5.0), cost = 3
        )
        
        // No Fall Damage (Feather Falling Max)
        nodes["nofall_1"] = SkillNode(
            "nofall_1", "Featherfall", "Immune to fall damage",
            Material.ELYTRA, SkillCategory.MOBILITY, NodeTier.NOTABLE,
            listOf("mobility_start"),
            SkillEffect.NoFallDamage(true)
        )
        
        // ═══════════════════════════════════════════════════════════
        // MINING BRANCH (Orange) - Vein Mining, Auto-Smelt, Magnetism
        // ═══════════════════════════════════════════════════════════
        nodes["mining_start"] = SkillNode(
            "mining_start", "Miner's Path", "Begin the path of excavation.",
            Material.IRON_PICKAXE, SkillCategory.MINING, NodeTier.MINOR,
            listOf("origin", "haste_1", "fortune_1", "veinminer_1", "autosmelt_1", "magnet_1"),
            SkillEffect.MiningSpeed(0)
        )
        
        // Haste Branch
        nodes["haste_1"] = SkillNode(
            "haste_1", "Efficient Strikes", "Haste I",
            Material.GOLDEN_PICKAXE, SkillCategory.MINING, NodeTier.MINOR,
            listOf("mining_start", "haste_2"),
            SkillEffect.MiningSpeed(0)
        )
        nodes["haste_2"] = SkillNode(
            "haste_2", "Rapid Excavation", "Haste II",
            Material.DIAMOND_PICKAXE, SkillCategory.MINING, NodeTier.NOTABLE,
            listOf("haste_1", "haste_3"),
            SkillEffect.MiningSpeed(1)
        )
        nodes["haste_3"] = SkillNode(
            "haste_3", "Mining Fury", "Haste III",
            Material.NETHERITE_PICKAXE, SkillCategory.MINING, NodeTier.KEYSTONE,
            listOf("haste_2"),
            SkillEffect.MiningSpeed(2), cost = 3
        )
        
        // Vein Miner (Terraria/Modded MC)
        nodes["veinminer_1"] = SkillNode(
            "veinminer_1", "Vein Miner", "Mine entire ore veins at once!",
            Material.DIAMOND_ORE, SkillCategory.MINING, NodeTier.KEYSTONE,
            listOf("mining_start"),
            SkillEffect.VeinMiner(true), cost = 3
        )
        
        // Auto-Smelt (Terraria)
        nodes["autosmelt_1"] = SkillNode(
            "autosmelt_1", "Smelting Touch", "Ores drop smelted ingots",
            Material.BLAST_FURNACE, SkillCategory.MINING, NodeTier.KEYSTONE,
            listOf("mining_start"),
            SkillEffect.AutoSmelt(true), cost = 3
        )
        
        // Magnetism (Terraria)
        nodes["magnet_1"] = SkillNode(
            "magnet_1", "Item Magnet", "Auto-pickup items within 5 blocks",
            Material.LODESTONE, SkillCategory.MINING, NodeTier.NOTABLE,
            listOf("mining_start", "magnet_2"),
            SkillEffect.Magnetism(5.0)
        )
        nodes["magnet_2"] = SkillNode(
            "magnet_2", "Gravity Well", "Auto-pickup items within 10 blocks",
            Material.END_PORTAL_FRAME, SkillCategory.MINING, NodeTier.KEYSTONE,
            listOf("magnet_1"),
            SkillEffect.Magnetism(10.0), cost = 3
        )
        
        // Luck & Double Drops
        nodes["fortune_1"] = SkillNode(
            "fortune_1", "Lucky Strikes", "Luck I",
            Material.LAPIS_LAZULI, SkillCategory.MINING, NodeTier.NOTABLE,
            listOf("mining_start", "double_drop_1"),
            SkillEffect.LuckBonus(0)
        )
        nodes["double_drop_1"] = SkillNode(
            "double_drop_1", "Prospector", "25% chance for double drops",
            Material.GOLD_NUGGET, SkillCategory.MINING, NodeTier.KEYSTONE,
            listOf("fortune_1"),
            SkillEffect.DoubleDrop(0.25), cost = 3
        )
        
        // ═══════════════════════════════════════════════════════════
        // ARCHERY BRANCH (Green)
        // ═══════════════════════════════════════════════════════════
        nodes["archery_start"] = SkillNode(
            "archery_start", "Archer's Path", "Begin the path of the bow.",
            Material.BOW, SkillCategory.ARCHERY, NodeTier.MINOR,
            listOf("origin", "bow_1", "arrow_1"),
            SkillEffect.BowDamage(1.05)
        )
        nodes["bow_1"] = SkillNode(
            "bow_1", "Steady Aim", "+10% Bow Damage",
            Material.ARROW, SkillCategory.ARCHERY, NodeTier.MINOR,
            listOf("archery_start", "bow_2"),
            SkillEffect.BowDamage(1.10)
        )
        nodes["bow_2"] = SkillNode(
            "bow_2", "Marksman", "+20% Bow Damage",
            Material.SPECTRAL_ARROW, SkillCategory.ARCHERY, NodeTier.NOTABLE,
            listOf("bow_1", "bow_3"),
            SkillEffect.BowDamage(1.20)
        )
        nodes["bow_3"] = SkillNode(
            "bow_3", "Sniper", "+35% Bow Damage",
            Material.CROSSBOW, SkillCategory.ARCHERY, NodeTier.KEYSTONE,
            listOf("bow_2"),
            SkillEffect.BowDamage(1.35), cost = 3
        )
        nodes["arrow_1"] = SkillNode(
            "arrow_1", "Piercing Shots", "+5% Crit Chance with Bows",
            Material.TIPPED_ARROW, SkillCategory.ARCHERY, NodeTier.NOTABLE,
            listOf("archery_start"),
            SkillEffect.CritChance(0.05)
        )
        
        // ═══════════════════════════════════════════════════════════
        // DARK ARTS BRANCH (Purple) - Risk/Reward Mechanics
        // ═══════════════════════════════════════════════════════════
        nodes["dark_start"] = SkillNode(
            "dark_start", "Dark Pact", "Power at a price...",
            Material.WITHER_ROSE, SkillCategory.DARK_ARTS, NodeTier.MINOR,
            listOf("origin", "berserker_1", "sneak_1", "rampage_1"),
            SkillEffect.MeleeDamage(1.05)
        )
        
        // Berserker (Diablo)
        nodes["berserker_1"] = SkillNode(
            "berserker_1", "Berserker", "+30% damage when below 50% HP",
            Material.NETHERITE_SWORD, SkillCategory.DARK_ARTS, NodeTier.NOTABLE,
            listOf("dark_start", "berserker_2"),
            SkillEffect.BerserkerMode(0.50, 1.30)
        )
        nodes["berserker_2"] = SkillNode(
            "berserker_2", "Blood Rage", "+60% damage when below 30% HP",
            Material.DRAGON_BREATH, SkillCategory.DARK_ARTS, NodeTier.KEYSTONE,
            listOf("berserker_1"),
            SkillEffect.BerserkerMode(0.30, 1.60), cost = 3
        )
        
        // Sneak Attack (Skyrim)
        nodes["sneak_1"] = SkillNode(
            "sneak_1", "Assassin", "2x damage from behind",
            Material.NAME_TAG, SkillCategory.DARK_ARTS, NodeTier.NOTABLE,
            listOf("dark_start", "sneak_2"),
            SkillEffect.SneakDamage(2.0)
        )
        nodes["sneak_2"] = SkillNode(
            "sneak_2", "Shadow Strike", "3x damage from behind",
            Material.ENDER_EYE, SkillCategory.DARK_ARTS, NodeTier.KEYSTONE,
            listOf("sneak_1"),
            SkillEffect.SneakDamage(3.0), cost = 3
        )
        
        // Rampage (Diablo)
        nodes["rampage_1"] = SkillNode(
            "rampage_1", "Bloodlust", "Gain speed/damage stacks on kill",
            Material.BLAZE_POWDER, SkillCategory.DARK_ARTS, NodeTier.KEYSTONE,
            listOf("dark_start"),
            SkillEffect.Rampage(1, 5), cost = 3
        )
        
        // ═══════════════════════════════════════════════════════════
        // UTILITY BRANCH (Yellow) - Quality of Life
        // ═══════════════════════════════════════════════════════════
        nodes["utility_start"] = SkillNode(
            "utility_start", "Adventurer's Kit", "Quality of life improvements.",
            Material.BUNDLE, SkillCategory.UTILITY, NodeTier.MINOR,
            listOf("origin", "night_vision_1", "water_breathing_1", "soulbind_1", "lumberjack_1", "xp_bonus_1"),
            SkillEffect.XpBonus(plugin.configManager.skillXpBonusBase - 0.20) // 1.05 default
        )
        
        nodes["night_vision_1"] = SkillNode(
            "night_vision_1", "Dark Sight", "Permanent Night Vision",
            Material.ENDER_EYE, SkillCategory.UTILITY, NodeTier.NOTABLE,
            listOf("utility_start"),
            SkillEffect.NightVision(true)
        )
        nodes["water_breathing_1"] = SkillNode(
            "water_breathing_1", "Aquatic", "Permanent Water Breathing",
            Material.HEART_OF_THE_SEA, SkillCategory.UTILITY, NodeTier.NOTABLE,
            listOf("utility_start"),
            SkillEffect.WaterBreathing(true)
        )
        nodes["soulbind_1"] = SkillNode(
            "soulbind_1", "Soul Binding", "Keep 3 items on death",
            Material.SOUL_LANTERN, SkillCategory.UTILITY, NodeTier.KEYSTONE,
            listOf("utility_start"),
            SkillEffect.SoulBinding(3), cost = 3
        )
        nodes["lumberjack_1"] = SkillNode(
            "lumberjack_1", "Lumberjack", "Chop entire trees at once",
            Material.STONE_AXE, SkillCategory.UTILITY, NodeTier.NOTABLE,
            listOf("utility_start"),
            SkillEffect.Lumberjack(true)
        )
        nodes["xp_bonus_1"] = SkillNode(
            "xp_bonus_1", "Fast Learner", "+25% XP Gain",
            Material.EXPERIENCE_BOTTLE, SkillCategory.UTILITY, NodeTier.NOTABLE,
            listOf("utility_start"),
            SkillEffect.XpBonus(1.25)
        )
        
        // Active Ability: Healing Pulse
        nodes["healing_pulse_1"] = SkillNode(
            "healing_pulse_1", "Healing Pulse", "Active: Heal nearby allies",
            Material.BEACON, SkillCategory.UTILITY, NodeTier.KEYSTONE,
            listOf("utility_start"),
            SkillEffect.ActiveHealingPulse(plugin.configManager.healingPulseAmount, plugin.configManager.healingPulseCooldownTicks), cost = 3
        )
        
        return nodes
    }
    
    /**
     * Builds a 15x15 grid layout placing nodes spatially.
     * Center is [7][7] (origin node)
     */
    private fun buildGridLayout(): Array<Array<String?>> {
        val grid = Array(GRID_SIZE) { arrayOfNulls<String>(GRID_SIZE) }
        
        // Center - Origin
        grid[7][7] = "origin"
        
        // Combat (Top) - Row 5-6
        grid[7][5] = "combat_start"
        grid[8][5] = "fireball_1" 
        grid[6][4] = "sword_1"
        grid[5][3] = "sword_2"
        grid[4][2] = "sword_3"
        grid[5][4] = "crit_1"
        grid[4][4] = "crit_2"
        grid[8][4] = "axe_1"
        grid[9][3] = "axe_2"
        grid[10][2] = "axe_3"
        grid[9][4] = "knockback_1"
        grid[7][4] = "strength_1"
        grid[7][3] = "strength_2"
        
        // Defense (Left) - Column 3-5
        grid[5][7] = "defense_start"
        grid[5][6] = "shield_wall_1"
        grid[4][7] = "health_1"
        grid[3][7] = "health_2"
        grid[2][7] = "health_3"
        grid[4][8] = "armor_1"
        grid[3][8] = "armor_2"
        grid[2][8] = "fire_res_1"
        grid[4][6] = "regen_1"
        grid[3][6] = "regen_2"
        grid[5][8] = "survival_1"
        grid[4][9] = "survival_2"
        
        // Mobility (Right) - Column 9-11
        grid[9][7] = "mobility_start"
        grid[10][7] = "speed_1"
        grid[11][7] = "speed_2"
        grid[12][7] = "speed_3"
        grid[10][6] = "jump_1"
        grid[11][6] = "jump_2"
        
        // Mining (Bottom-Left) - Row 9-11
        grid[6][9] = "mining_start"
        grid[5][10] = "haste_1"
        grid[4][11] = "haste_2"
        grid[3][12] = "haste_3"
        grid[6][10] = "fortune_1"
        grid[6][11] = "fortune_2"
        grid[7][10] = "night_vision_1"
        grid[5][9] = "xp_bonus_1"
        
        // Archery (Bottom-Right) - Row 9-11
        grid[8][9] = "archery_start"
        grid[7][9] = "healing_pulse_1" // Near Utility Start? No utility_start is not placed in grid yet
        
        // Utility Start Fix - Place it
        grid[9][6] = "utility_start" // Close to mobility?
        grid[9][5] = "healing_pulse_1"
        grid[9][10] = "bow_1"
        grid[10][11] = "bow_2"
        grid[11][12] = "bow_3"
        grid[8][10] = "arrow_1"
        
        return grid
    }
    
    // ══════════════════════════════════════════════════════════════
    // SKILL POINT MANAGEMENT
    // ══════════════════════════════════════════════════════════════
    
    fun getAvailableSkillPoints(player: Player): Int {
        val profile = plugin.identityManager.getPlayer(player.uniqueId) ?: return 0
        val totalPoints = profile.level * SKILL_POINTS_PER_LEVEL
        val usedPoints = getUnlockedNodes(player).sumOf { skillTree[it]?.cost ?: 0 }
        return totalPoints - usedPoints
    }
    
    fun getUnlockedNodes(player: Player): Set<String> {
        val profile = plugin.identityManager.getPlayer(player.uniqueId) ?: return setOf("origin")
        // Stored as comma-separated in a field - handle null for existing players
        val nodeStr = profile.unlockedSkillNodes
        return if (nodeStr.isNullOrBlank()) setOf("origin") else nodeStr.split(",").toSet()
    }
    
    fun unlockNode(player: Player, nodeId: String): Boolean {
        val node = skillTree[nodeId] ?: return false
        val profile = plugin.identityManager.getPlayer(player.uniqueId) ?: return false
        val unlocked = getUnlockedNodes(player).toMutableSet()
        
        // Already unlocked?
        if (nodeId in unlocked) {
            player.sendMessage(Component.text("Already unlocked!", NamedTextColor.YELLOW))
            return false
        }
        
        // Check if connected to an unlocked node
        val isConnected = node.connections.any { it in unlocked }
        if (!isConnected) {
            player.sendMessage(Component.text("Must be connected to an unlocked node!", NamedTextColor.RED))
            return false
        }
        
        // Check skill points
        if (getAvailableSkillPoints(player) < node.cost) {
            player.sendMessage(Component.text("Not enough skill points! (Need ${node.cost})", NamedTextColor.RED))
            return false
        }
        
        // Unlock!
        unlocked.add(nodeId)
        profile.unlockedSkillNodes = unlocked.joinToString(",")
        plugin.identityManager.saveProfile(player.uniqueId)
        
        // Apply effects
        applyAllSkillEffects(player)
        
        player.sendMessage(Component.text("✓ Unlocked: ${node.name}", NamedTextColor.GREEN))
        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f)
        
        return true
    }
    
    // ══════════════════════════════════════════════════════════════
    // APPLY SKILL EFFECTS
    // ══════════════════════════════════════════════════════════════
    
    fun applyAllSkillEffects(player: Player) {
        val unlocked = getUnlockedNodes(player)
        
        // Reset to defaults
        player.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.baseValue = 20.0
        player.getAttribute(Attribute.GENERIC_ARMOR)?.baseValue = 0.0
        player.walkSpeed = 0.2f
        
        // Clear passive potion effects
        listOf(PotionEffectType.REGENERATION, PotionEffectType.HASTE, PotionEffectType.SPEED,
               PotionEffectType.JUMP_BOOST, PotionEffectType.NIGHT_VISION, PotionEffectType.WATER_BREATHING,
               PotionEffectType.FIRE_RESISTANCE, PotionEffectType.LUCK, PotionEffectType.SATURATION)
            .forEach { player.removePotionEffect(it) }
        
        // Accumulate bonuses
        var bonusHealth = 0.0
        var bonusArmor = 0.0
        var bonusSpeed = 0f
        var regenLevel = -1
        var hasteLevel = -1
        var jumpLevel = -1
        var luckLevel = -1
        var hasNightVision = false
        var hasWaterBreathing = false
        var hasFireRes = false
        var satLevel = -1
        
        for (nodeId in unlocked) {
            val node = skillTree[nodeId] ?: continue
            when (val effect = node.effect) {
                is SkillEffect.MaxHealth -> bonusHealth += effect.bonus
                is SkillEffect.Armor -> bonusArmor += effect.bonus
                is SkillEffect.MovementSpeed -> bonusSpeed += effect.bonus
                is SkillEffect.Regeneration -> regenLevel = maxOf(regenLevel, effect.level)
                is SkillEffect.MiningSpeed -> hasteLevel = maxOf(hasteLevel, effect.level)
                is SkillEffect.JumpBoost -> jumpLevel = maxOf(jumpLevel, effect.level)
                is SkillEffect.LuckBonus -> luckLevel = maxOf(luckLevel, effect.level)
                is SkillEffect.NightVision -> hasNightVision = effect.enabled
                is SkillEffect.WaterBreathing -> hasWaterBreathing = effect.enabled
                is SkillEffect.FireResistance -> hasFireRes = true
                is SkillEffect.Saturation -> satLevel = maxOf(satLevel, effect.level)
                else -> { /* Damage bonuses handled elsewhere */ }
            }
        }
        
        // Apply accumulated bonuses
        player.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.baseValue = 20.0 + bonusHealth
        player.getAttribute(Attribute.GENERIC_ARMOR)?.baseValue = bonusArmor
        player.walkSpeed = (0.2f + bonusSpeed).coerceIn(0.1f, 1.0f)
        
        // Apply potion effects
        if (regenLevel >= 0) player.addPotionEffect(PotionEffect(PotionEffectType.REGENERATION, PotionEffect.INFINITE_DURATION, regenLevel, false, false))
        if (hasteLevel >= 0) player.addPotionEffect(PotionEffect(PotionEffectType.HASTE, PotionEffect.INFINITE_DURATION, hasteLevel, false, false))
        if (jumpLevel >= 0) player.addPotionEffect(PotionEffect(PotionEffectType.JUMP_BOOST, PotionEffect.INFINITE_DURATION, jumpLevel, false, false))
        if (luckLevel >= 0) player.addPotionEffect(PotionEffect(PotionEffectType.LUCK, PotionEffect.INFINITE_DURATION, luckLevel, false, false))
        if (satLevel >= 0) player.addPotionEffect(PotionEffect(PotionEffectType.SATURATION, PotionEffect.INFINITE_DURATION, satLevel, false, false))
        if (hasNightVision) player.addPotionEffect(PotionEffect(PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION, 0, false, false))
        if (hasWaterBreathing) player.addPotionEffect(PotionEffect(PotionEffectType.WATER_BREATHING, PotionEffect.INFINITE_DURATION, 0, false, false))
        if (hasFireRes) player.addPotionEffect(PotionEffect(PotionEffectType.FIRE_RESISTANCE, PotionEffect.INFINITE_DURATION, 0, false, false))
    }
    
    // ══════════════════════════════════════════════════════════════
    // BEAUTIFUL GUI - Visual Skill Tree
    // ══════════════════════════════════════════════════════════════
    
    fun openSkillTree(player: Player) {
        val pos = viewportPositions.getOrPut(player.uniqueId) { Pair(4, 4) } // Start centered
        renderSkillTreeGUI(player, pos.first, pos.second)
    }
    
    private fun renderSkillTreeGUI(player: Player, viewX: Int, viewY: Int) {
        val inv = Bukkit.createInventory(null, 54, Component.text("✦ Skill Tree ✦", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
        val unlocked = getUnlockedNodes(player)
        
        // Render 9x5 viewport of the grid (rows 0-4 of inventory = rows 0-44)
        for (row in 0 until VIEWPORT_HEIGHT) {
            for (col in 0 until VIEWPORT_WIDTH) {
                val gridX = viewX + col
                val gridY = viewY + row
                val slot = row * 9 + col
                
                if (gridX < 0 || gridX >= GRID_SIZE || gridY < 0 || gridY >= GRID_SIZE) {
                    inv.setItem(slot, createBorderItem())
                    continue
                }
                
                val nodeId = gridLayout[gridX][gridY]
                if (nodeId == null) {
                    // Check if this is a connection between nodes
                    inv.setItem(slot, createConnectionItem(gridX, gridY, unlocked))
                } else {
                    val node = skillTree[nodeId]!!
                    inv.setItem(slot, createNodeItem(node, nodeId in unlocked, canUnlock(nodeId, unlocked)))
                }
            }
        }
        
        // Control row (row 5, slots 45-53)
        inv.setItem(45, createNavItem(Material.ARROW, "◀ Scroll Left", "nav_left"))
        inv.setItem(46, createNavItem(Material.ARROW, "▲ Scroll Up", "nav_up"))
        inv.setItem(47, createNavItem(Material.ARROW, "▼ Scroll Down", "nav_down"))
        inv.setItem(48, createNavItem(Material.ARROW, "▶ Scroll Right", "nav_right"))
        
        // Info
        inv.setItem(49, createInfoItem(player))
        
        // Center button
        inv.setItem(52, createNavItem(Material.COMPASS, "⌂ Center View", "nav_center"))
        inv.setItem(53, createNavItem(Material.BARRIER, "✕ Close", "close"))
        
        player.openInventory(inv)
    }
    
    private fun createNodeItem(node: SkillNode, isUnlocked: Boolean, canUnlock: Boolean): ItemStack {
        val item = ItemStack(node.icon)
        val meta = item.itemMeta ?: return item
        
        val tierColor = when (node.tier) {
            NodeTier.MINOR -> NamedTextColor.GRAY
            NodeTier.NOTABLE -> NamedTextColor.YELLOW
            NodeTier.KEYSTONE -> NamedTextColor.LIGHT_PURPLE
        }
        
        val statusColor = when {
            isUnlocked -> NamedTextColor.GREEN
            canUnlock -> NamedTextColor.AQUA
            else -> NamedTextColor.DARK_GRAY
        }
        
        meta.displayName(Component.text(node.name, tierColor, TextDecoration.BOLD))
        
        val lore = mutableListOf<Component>()
        lore.add(Component.text(node.category.displayName, NamedTextColor.GRAY))
        lore.add(Component.empty())
        lore.add(Component.text(node.description, NamedTextColor.WHITE))
        lore.add(Component.text(node.effect.toString(), NamedTextColor.GREEN))
        lore.add(Component.empty())
        lore.add(Component.text("Cost: ${node.cost} point(s)", NamedTextColor.GOLD))
        lore.add(Component.empty())
        
        when {
            isUnlocked -> {
                lore.add(Component.text("✓ UNLOCKED", NamedTextColor.GREEN, TextDecoration.BOLD))
                meta.addEnchant(Enchantment.UNBREAKING, 1, true)
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
            }
            canUnlock -> lore.add(Component.text("► Click to Unlock", NamedTextColor.AQUA))
            else -> lore.add(Component.text("✕ Locked (Not Connected)", NamedTextColor.DARK_GRAY))
        }
        
        meta.lore(lore)
        item.itemMeta = meta
        return item
    }
    
    private fun createConnectionItem(x: Int, y: Int, unlocked: Set<String>): ItemStack {
        // Check if adjacent nodes are unlocked to show connection lines
        val hasUnlockedNeighbor = listOf(
            gridLayout.getOrNull(x-1)?.getOrNull(y),
            gridLayout.getOrNull(x+1)?.getOrNull(y),
            gridLayout.getOrNull(x)?.getOrNull(y-1),
            gridLayout.getOrNull(x)?.getOrNull(y+1)
        ).any { it != null && it in unlocked }
        
        val material = if (hasUnlockedNeighbor) Material.LIGHT_GRAY_STAINED_GLASS_PANE else Material.BLACK_STAINED_GLASS_PANE
        val item = ItemStack(material)
        val meta = item.itemMeta
        meta?.displayName(Component.text(" "))
        item.itemMeta = meta
        return item
    }
    
    private fun createBorderItem(): ItemStack {
        val item = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
        val meta = item.itemMeta
        meta?.displayName(Component.text("Edge of Tree", NamedTextColor.DARK_GRAY))
        item.itemMeta = meta
        return item
    }
    
    private fun createNavItem(material: Material, name: String, action: String): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta
        meta?.displayName(Component.text(name, NamedTextColor.YELLOW))
        meta?.lore(listOf(Component.text("action:$action", NamedTextColor.DARK_GRAY)))
        item.itemMeta = meta
        return item
    }
    
    private fun createInfoItem(player: Player): ItemStack {
        val item = ItemStack(Material.BOOK)
        val meta = item.itemMeta
        meta?.displayName(Component.text("Skill Points", NamedTextColor.GOLD, TextDecoration.BOLD))
        meta?.lore(listOf(
            Component.text("Available: ${getAvailableSkillPoints(player)}", NamedTextColor.GREEN),
            Component.text("Level: ${plugin.identityManager.getPlayer(player.uniqueId)?.level ?: 1}", NamedTextColor.AQUA),
            Component.text("Unlocked: ${getUnlockedNodes(player).size} nodes", NamedTextColor.GRAY)
        ))
        item.itemMeta = meta
        return item
    }
    
    private fun canUnlock(nodeId: String, unlocked: Set<String>): Boolean {
        val node = skillTree[nodeId] ?: return false
        return node.connections.any { it in unlocked }
    }
    
    // ══════════════════════════════════════════════════════════════
    // EVENT HANDLERS
    // ══════════════════════════════════════════════════════════════
    
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val title = event.view.title()
        if (title !is net.kyori.adventure.text.TextComponent) return
        if (!title.content().contains("Skill Tree")) return
        
        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        val item = event.currentItem ?: return
        val meta = item.itemMeta ?: return
        
        // Check for navigation
        val lore = meta.lore()?.firstOrNull()
        if (lore != null) {
            val loreText = (lore as? net.kyori.adventure.text.TextComponent)?.content() ?: ""
            if (loreText.startsWith("action:")) {
                val action = loreText.removePrefix("action:")
                handleNavigation(player, action)
                return
            }
        }
        
        // Try to unlock node
        val clickedSlot = event.rawSlot
        if (clickedSlot < 45) { // Node area
            val pos = viewportPositions[player.uniqueId] ?: Pair(4, 4)
            val row = clickedSlot / 9
            val col = clickedSlot % 9
            val gridX = pos.first + col
            val gridY = pos.second + row
            
            val nodeId = gridLayout.getOrNull(gridX)?.getOrNull(gridY)
            if (nodeId != null) {
                unlockNode(player, nodeId)
                renderSkillTreeGUI(player, pos.first, pos.second) // Refresh
            }
        }
    }
    
    private fun handleNavigation(player: Player, action: String) {
        val pos = viewportPositions[player.uniqueId] ?: Pair(4, 4)
        var (x, y) = pos
        
        when (action) {
            "nav_left" -> x = (x - 1).coerceAtLeast(0)
            "nav_right" -> x = (x + 1).coerceAtMost(GRID_SIZE - VIEWPORT_WIDTH)
            "nav_up" -> y = (y - 1).coerceAtLeast(0)
            "nav_down" -> y = (y + 1).coerceAtMost(GRID_SIZE - VIEWPORT_HEIGHT)
            "nav_center" -> { x = 4; y = 4 }
            "close" -> { player.closeInventory(); return }
        }
        
        viewportPositions[player.uniqueId] = Pair(x, y)
        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.5f, 1f)
        renderSkillTreeGUI(player, x, y)
    }
    
    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        // Apply skill effects on join
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            applyAllSkillEffects(event.player)
        }, 20L)
    }
    
    // Get damage multiplier for weapons
    fun getSwordDamageMultiplier(player: Player): Double {
        return getUnlockedNodes(player)
            .mapNotNull { skillTree[it]?.effect as? SkillEffect.SwordDamage }
            .fold(1.0) { acc, e -> acc * e.multiplier }
    }
    
    fun getAxeDamageMultiplier(player: Player): Double {
        return getUnlockedNodes(player)
            .mapNotNull { skillTree[it]?.effect as? SkillEffect.AxeDamage }
            .fold(1.0) { acc, e -> acc * e.multiplier }
    }
    
    fun getBowDamageMultiplier(player: Player): Double {
        return getUnlockedNodes(player)
            .mapNotNull { skillTree[it]?.effect as? SkillEffect.BowDamage }
            .fold(1.0) { acc, e -> acc * e.multiplier }
    }
}
