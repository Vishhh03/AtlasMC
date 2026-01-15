package com.projectatlas.skills

import com.projectatlas.AtlasPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.enchantments.Enchantment
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
 * Skill Tree System - Reworked for practical gameplay.
 * 
 * Design Philosophy:
 * 1. Five Role Branches: Vanguard, Berserker, Scout, Artisan, Settler
 * 2. Every effect is implemented and functional
 * 3. Keystones are exclusive (meaningful choices)
 * 4. Clear progression paths
 */
class SkillTreeManager(private val plugin: AtlasPlugin) : Listener {

    private val viewportPositions = ConcurrentHashMap<UUID, Pair<Int, Int>>()
    private val skillTree: Map<String, SkillNode>
    private val gridLayout: Array<Array<String?>>
    
    companion object {
        const val GRID_SIZE = 15
        const val VIEWPORT_WIDTH = 9
        const val VIEWPORT_HEIGHT = 5
        const val SKILL_POINTS_PER_LEVEL = 1
    }
    
    init {
        skillTree = buildSkillTree()
        gridLayout = buildGridLayout()
        
        // Start passive effect task
        startPassiveEffectTask()
    }
    
    // ══════════════════════════════════════════════════════════════
    // SKILL TREE DEFINITION - Role-Based Branches
    // ══════════════════════════════════════════════════════════════
    
    private fun buildSkillTree(): Map<String, SkillNode> {
        val nodes = mutableMapOf<String, SkillNode>()
        
        // ═══ ORIGIN (Center) ═══
        nodes["origin"] = SkillNode(
            "origin", "Awakening", "Your journey begins here.",
            Material.NETHER_STAR, SkillCategory.CORE, NodeTier.NOTABLE,
            listOf("vanguard_start", "berserker_start", "scout_start", "artisan_start", "settler_start"),
            SkillEffect.MaxHealth(2.0), cost = 0
        )
        
        // ═══════════════════════════════════════════════════════════
        // VANGUARD BRANCH (Blue) - Tank / Defense
        // ═══════════════════════════════════════════════════════════
        nodes["vanguard_start"] = SkillNode(
            "vanguard_start", "Vanguard's Path", "The shield of your allies.",
            Material.IRON_CHESTPLATE, SkillCategory.VANGUARD, NodeTier.MINOR,
            listOf("origin", "v_health_1", "v_armor_1", "v_regen_1"),
            SkillEffect.MaxHealth(2.0)
        )
        
        // Health Branch
        nodes["v_health_1"] = SkillNode(
            "v_health_1", "Vitality", "+4 Max Health",
            Material.APPLE, SkillCategory.VANGUARD, NodeTier.MINOR,
            listOf("vanguard_start", "v_health_2"),
            SkillEffect.MaxHealth(4.0)
        )
        nodes["v_health_2"] = SkillNode(
            "v_health_2", "Constitution", "+6 Max Health",
            Material.GOLDEN_APPLE, SkillCategory.VANGUARD, NodeTier.NOTABLE,
            listOf("v_health_1", "v_colossus"),
            SkillEffect.MaxHealth(6.0)
        )
        nodes["v_colossus"] = SkillNode(
            "v_colossus", "Colossus", "+10 Max Health",
            Material.ENCHANTED_GOLDEN_APPLE, SkillCategory.VANGUARD, NodeTier.KEYSTONE,
            listOf("v_health_2"),
            SkillEffect.MaxHealth(10.0), cost = 3,
            exclusiveWith = listOf("b_executioner") // Can't be both Tank and Executioner
        )
        
        // Armor Branch
        nodes["v_armor_1"] = SkillNode(
            "v_armor_1", "Iron Skin", "+2 Armor",
            Material.IRON_INGOT, SkillCategory.VANGUARD, NodeTier.MINOR,
            listOf("vanguard_start", "v_armor_2"),
            SkillEffect.Armor(2.0)
        )
        nodes["v_armor_2"] = SkillNode(
            "v_armor_2", "Steel Wall", "+4 Armor, Fire Resistance",
            Material.IRON_BLOCK, SkillCategory.VANGUARD, NodeTier.NOTABLE,
            listOf("v_armor_1", "v_poise"),
            SkillEffect.Armor(4.0)
        )
        nodes["v_poise"] = SkillNode(
            "v_poise", "Unbreakable", "Immune to knockback",
            Material.ANVIL, SkillCategory.VANGUARD, NodeTier.KEYSTONE,
            listOf("v_armor_2"),
            SkillEffect.Poise(true), cost = 3
        )
        
        // Regen Branch
        nodes["v_regen_1"] = SkillNode(
            "v_regen_1", "Recovery", "Regeneration I",
            Material.GLISTERING_MELON_SLICE, SkillCategory.VANGUARD, NodeTier.NOTABLE,
            listOf("vanguard_start", "v_regen_2"),
            SkillEffect.Regeneration(0)
        )
        nodes["v_regen_2"] = SkillNode(
            "v_regen_2", "Rapid Healing", "Regeneration II",
            Material.GOLDEN_CARROT, SkillCategory.VANGUARD, NodeTier.KEYSTONE,
            listOf("v_regen_1"),
            SkillEffect.Regeneration(1), cost = 3
        )
        
        // Thorns
        nodes["v_thorns"] = SkillNode(
            "v_thorns", "Spiked Armor", "Reflect 20% damage",
            Material.CACTUS, SkillCategory.VANGUARD, NodeTier.NOTABLE,
            listOf("v_armor_1"),
            SkillEffect.Thorns(0.20)
        )
        
        // ═══════════════════════════════════════════════════════════
        // BERSERKER BRANCH (Red) - DPS / Combat
        // ═══════════════════════════════════════════════════════════
        nodes["berserker_start"] = SkillNode(
            "berserker_start", "Berserker's Path", "Embrace the fury of battle.",
            Material.IRON_SWORD, SkillCategory.BERSERKER, NodeTier.MINOR,
            listOf("origin", "b_melee_1", "b_crit_1", "b_leech_1"),
            SkillEffect.MeleeDamage(1.05)
        )
        
        // Melee Damage Branch
        nodes["b_melee_1"] = SkillNode(
            "b_melee_1", "Blade Adept", "+10% Melee Damage",
            Material.STONE_SWORD, SkillCategory.BERSERKER, NodeTier.MINOR,
            listOf("berserker_start", "b_melee_2"),
            SkillEffect.MeleeDamage(1.10)
        )
        nodes["b_melee_2"] = SkillNode(
            "b_melee_2", "Blade Master", "+15% Melee Damage",
            Material.DIAMOND_SWORD, SkillCategory.BERSERKER, NodeTier.NOTABLE,
            listOf("b_melee_1", "b_executioner"),
            SkillEffect.MeleeDamage(1.15)
        )
        nodes["b_executioner"] = SkillNode(
            "b_executioner", "Executioner", "+50% damage to enemies <25% HP",
            Material.NETHERITE_AXE, SkillCategory.BERSERKER, NodeTier.KEYSTONE,
            listOf("b_melee_2"),
            SkillEffect.ExecuteDamage(0.25, 1.50), cost = 3,
            exclusiveWith = listOf("v_colossus") // Can't be both Executioner and Tank
        )
        
        // Crit Branch
        nodes["b_crit_1"] = SkillNode(
            "b_crit_1", "Precision", "+10% Crit Chance",
            Material.SPECTRAL_ARROW, SkillCategory.BERSERKER, NodeTier.MINOR,
            listOf("berserker_start", "b_crit_2"),
            SkillEffect.CritChance(0.10)
        )
        nodes["b_crit_2"] = SkillNode(
            "b_crit_2", "Deadly Aim", "+50% Crit Damage",
            Material.TNT, SkillCategory.BERSERKER, NodeTier.KEYSTONE,
            listOf("b_crit_1"),
            SkillEffect.CritMultiplier(1.50), cost = 3
        )
        
        // Lifesteal Branch
        nodes["b_leech_1"] = SkillNode(
            "b_leech_1", "Vampiric Strikes", "Leech 5% damage as HP",
            Material.REDSTONE, SkillCategory.BERSERKER, NodeTier.NOTABLE,
            listOf("berserker_start", "b_leech_2"),
            SkillEffect.LifeLeech(0.05)
        )
        nodes["b_leech_2"] = SkillNode(
            "b_leech_2", "Blood Drinker", "Leech 10% damage as HP",
            Material.CRIMSON_FUNGUS, SkillCategory.BERSERKER, NodeTier.KEYSTONE,
            listOf("b_leech_1"),
            SkillEffect.LifeLeech(0.10), cost = 3
        )
        
        // Berserker Mode (Low HP = More Damage)
        nodes["b_rage"] = SkillNode(
            "b_rage", "Blood Rage", "+40% damage when below 30% HP",
            Material.DRAGON_BREATH, SkillCategory.BERSERKER, NodeTier.KEYSTONE,
            listOf("b_melee_1"),
            SkillEffect.BerserkerMode(0.30, 1.40), cost = 3
        )
        
        // ═══════════════════════════════════════════════════════════
        // SCOUT BRANCH (Cyan) - Mobility / Stealth
        // ═══════════════════════════════════════════════════════════
        nodes["scout_start"] = SkillNode(
            "scout_start", "Scout's Path", "Move unseen, strike fast.",
            Material.LEATHER_BOOTS, SkillCategory.SCOUT, NodeTier.MINOR,
            listOf("origin", "s_speed_1", "s_stealth_1", "s_dodge_1"),
            SkillEffect.MovementSpeed(0.02f)
        )
        
        // Speed Branch
        nodes["s_speed_1"] = SkillNode(
            "s_speed_1", "Quickfoot", "+5% Movement Speed",
            Material.FEATHER, SkillCategory.SCOUT, NodeTier.MINOR,
            listOf("scout_start", "s_speed_2"),
            SkillEffect.MovementSpeed(0.01f)
        )
        nodes["s_speed_2"] = SkillNode(
            "s_speed_2", "Windrunner", "+10% Movement Speed",
            Material.DIAMOND_BOOTS, SkillCategory.SCOUT, NodeTier.NOTABLE,
            listOf("s_speed_1", "s_double_jump"),
            SkillEffect.MovementSpeed(0.02f)
        )
        nodes["s_double_jump"] = SkillNode(
            "s_double_jump", "Double Jump", "Jump again in mid-air!",
            Material.SLIME_BLOCK, SkillCategory.SCOUT, NodeTier.KEYSTONE,
            listOf("s_speed_2"),
            SkillEffect.DoubleJump(true), cost = 3
        )
        
        // Stealth Branch
        nodes["s_stealth_1"] = SkillNode(
            "s_stealth_1", "Shadow Walker", "+50% Sneak Damage",
            Material.BLACK_DYE, SkillCategory.SCOUT, NodeTier.NOTABLE,
            listOf("scout_start", "s_stealth_2"),
            SkillEffect.SneakDamage(1.50)
        )
        nodes["s_stealth_2"] = SkillNode(
            "s_stealth_2", "Assassin", "2x Sneak Damage",
            Material.ENDER_EYE, SkillCategory.SCOUT, NodeTier.KEYSTONE,
            listOf("s_stealth_1"),
            SkillEffect.SneakDamage(2.0), cost = 3
        )
        
        // Dodge Branch
        nodes["s_dodge_1"] = SkillNode(
            "s_dodge_1", "Evasion", "15% chance to dodge attacks",
            Material.PHANTOM_MEMBRANE, SkillCategory.SCOUT, NodeTier.NOTABLE,
            listOf("scout_start", "s_nofall"),
            SkillEffect.DodgeChance(0.15)
        )
        nodes["s_nofall"] = SkillNode(
            "s_nofall", "Featherfall", "Immune to fall damage",
            Material.ELYTRA, SkillCategory.SCOUT, NodeTier.NOTABLE,
            listOf("s_dodge_1"),
            SkillEffect.NoFallDamage(true)
        )
        
        // Ocean/Swim
        nodes["s_swim"] = SkillNode(
            "s_swim", "Mariner", "Faster swim, water breathing",
            Material.TURTLE_HELMET, SkillCategory.SCOUT, NodeTier.NOTABLE,
            listOf("s_speed_1"),
            SkillEffect.SwimSpeed(1)
        )
        nodes["s_cold_resist"] = SkillNode(
            "s_cold_resist", "Polar Bear", "Immune to freezing water",
            Material.PACKED_ICE, SkillCategory.SCOUT, NodeTier.MINOR,
            listOf("s_swim"),
            SkillEffect.ColdResistance(true)
        )
        
        // ═══════════════════════════════════════════════════════════
        // ARTISAN BRANCH (Orange) - Mining / Gathering
        // ═══════════════════════════════════════════════════════════
        nodes["artisan_start"] = SkillNode(
            "artisan_start", "Artisan's Path", "Master the land's resources.",
            Material.IRON_PICKAXE, SkillCategory.ARTISAN, NodeTier.MINOR,
            listOf("origin", "a_haste_1", "a_fortune_1", "a_timber"),
            SkillEffect.MiningSpeed(0)
        )
        
        // Haste Branch
        nodes["a_haste_1"] = SkillNode(
            "a_haste_1", "Efficient Strikes", "Haste I",
            Material.GOLDEN_PICKAXE, SkillCategory.ARTISAN, NodeTier.MINOR,
            listOf("artisan_start", "a_haste_2"),
            SkillEffect.MiningSpeed(0)
        )
        nodes["a_haste_2"] = SkillNode(
            "a_haste_2", "Rapid Excavation", "Haste II",
            Material.DIAMOND_PICKAXE, SkillCategory.ARTISAN, NodeTier.NOTABLE,
            listOf("a_haste_1", "a_veinminer"),
            SkillEffect.MiningSpeed(1)
        )
        nodes["a_veinminer"] = SkillNode(
            "a_veinminer", "Vein Miner", "Mine entire ore veins!",
            Material.DIAMOND_ORE, SkillCategory.ARTISAN, NodeTier.KEYSTONE,
            listOf("a_haste_2"),
            SkillEffect.VeinMiner(true), cost = 3
        )
        
        // Fortune Branch
        nodes["a_fortune_1"] = SkillNode(
            "a_fortune_1", "Lucky Strikes", "Luck I",
            Material.LAPIS_LAZULI, SkillCategory.ARTISAN, NodeTier.NOTABLE,
            listOf("artisan_start", "a_double_drop"),
            SkillEffect.LuckBonus(0)
        )
        nodes["a_double_drop"] = SkillNode(
            "a_double_drop", "Prospector", "25% chance for double drops",
            Material.GOLD_NUGGET, SkillCategory.ARTISAN, NodeTier.KEYSTONE,
            listOf("a_fortune_1"),
            SkillEffect.DoubleDrop(0.25), cost = 3
        )
        
        // Timber
        nodes["a_timber"] = SkillNode(
            "a_timber", "Lumberjack", "Chop entire trees at once",
            Material.DIAMOND_AXE, SkillCategory.ARTISAN, NodeTier.NOTABLE,
            listOf("artisan_start", "a_autosmelt"),
            SkillEffect.Lumberjack(true)
        )
        nodes["a_autosmelt"] = SkillNode(
            "a_autosmelt", "Smelting Touch", "Ores drop smelted ingots",
            Material.BLAST_FURNACE, SkillCategory.ARTISAN, NodeTier.KEYSTONE,
            listOf("a_timber"),
            SkillEffect.AutoSmelt(true), cost = 3,
            exclusiveWith = listOf("a_veinminer") // Choose: Vein Mine OR Auto Smelt
        )
        
        // Magnet
        nodes["a_magnet"] = SkillNode(
            "a_magnet", "Item Magnet", "Auto-pickup items within 5 blocks",
            Material.LODESTONE, SkillCategory.ARTISAN, NodeTier.NOTABLE,
            listOf("a_haste_1"),
            SkillEffect.Magnetism(5.0)
        )
        
        // ═══════════════════════════════════════════════════════════
        // SETTLER BRANCH (Green) - City / Economy / Social
        // ═══════════════════════════════════════════════════════════
        nodes["settler_start"] = SkillNode(
            "settler_start", "Settler's Path", "Build, trade, and prosper.",
            Material.EMERALD_BLOCK, SkillCategory.SETTLER, NodeTier.MINOR,
            listOf("origin", "set_trade", "set_rest", "set_siege"),
            SkillEffect.XpBonus(1.10)
        )
        
        // Trade/Economy
        nodes["set_trade"] = SkillNode(
            "set_trade", "Silver Tongue", "10% discount on city costs",
            Material.EMERALD, SkillCategory.SETTLER, NodeTier.NOTABLE,
            listOf("settler_start", "set_bounty"),
            SkillEffect.Diplomacy(0.10)
        )
        nodes["set_bounty"] = SkillNode(
            "set_bounty", "Bounty Hunter", "+25% Quest Gold",
            Material.GOLD_INGOT, SkillCategory.SETTLER, NodeTier.KEYSTONE,
            listOf("set_trade"),
            SkillEffect.BountyHunter(1.25), cost = 3
        )
        
        // Rest/Survival
        nodes["set_rest"] = SkillNode(
            "set_rest", "Deep Sleep", "+50% Healing from Rest",
            Material.WHITE_BED, SkillCategory.SETTLER, NodeTier.NOTABLE,
            listOf("settler_start", "set_soulbind"),
            SkillEffect.RestfulSleep(1.50)
        )
        nodes["set_soulbind"] = SkillNode(
            "set_soulbind", "Soul Binding", "Keep 3 items on death",
            Material.SOUL_LANTERN, SkillCategory.SETTLER, NodeTier.KEYSTONE,
            listOf("set_rest"),
            SkillEffect.SoulBinding(3), cost = 3
        )
        
        // Siege Defense
        nodes["set_siege"] = SkillNode(
            "set_siege", "Gatekeeper", "+25% Damage vs Raiders",
            Material.CROSSBOW, SkillCategory.SETTLER, NodeTier.NOTABLE,
            listOf("settler_start"),
            SkillEffect.SiegeDefender(1.25)
        )
        
        // Night Vision (Utility)
        nodes["set_night"] = SkillNode(
            "set_night", "Dark Sight", "Permanent Night Vision",
            Material.ENDER_EYE, SkillCategory.SETTLER, NodeTier.NOTABLE,
            listOf("settler_start"),
            SkillEffect.NightVision(true)
        )
        
        return nodes
    }
    
    /**
     * Grid Layout - 15x15 with Origin at center [7][7]
     */
    private fun buildGridLayout(): Array<Array<String?>> {
        val grid = Array(GRID_SIZE) { arrayOfNulls<String>(GRID_SIZE) }
        
        // Origin
        grid[7][7] = "origin"
        
        // Vanguard (Top-Left)
        grid[5][5] = "vanguard_start"
        grid[4][4] = "v_health_1"
        grid[3][3] = "v_health_2"
        grid[2][2] = "v_colossus"
        grid[4][6] = "v_armor_1"
        grid[3][7] = "v_armor_2"
        grid[2][8] = "v_poise"
        grid[6][4] = "v_regen_1"
        grid[6][3] = "v_regen_2"
        grid[5][6] = "v_thorns"
        
        // Berserker (Top-Right)
        grid[9][5] = "berserker_start"
        grid[10][4] = "b_melee_1"
        grid[11][3] = "b_melee_2"
        grid[12][2] = "b_executioner"
        grid[10][6] = "b_crit_1"
        grid[11][7] = "b_crit_2"
        grid[8][4] = "b_leech_1"
        grid[8][3] = "b_leech_2"
        grid[9][4] = "b_rage"
        
        // Scout (Right)
        grid[11][7] = "scout_start"
        grid[12][7] = "s_speed_1"
        grid[13][7] = "s_speed_2"
        grid[14][7] = "s_double_jump"
        grid[12][6] = "s_stealth_1"
        grid[13][5] = "s_stealth_2"
        grid[11][8] = "s_dodge_1"
        grid[12][9] = "s_nofall"
        grid[12][8] = "s_swim"
        grid[13][8] = "s_cold_resist"
        
        // Artisan (Bottom-Left)
        grid[5][9] = "artisan_start"
        grid[4][10] = "a_haste_1"
        grid[3][11] = "a_haste_2"
        grid[2][12] = "a_veinminer"
        grid[6][10] = "a_fortune_1"
        grid[6][11] = "a_double_drop"
        grid[4][8] = "a_timber"
        grid[3][9] = "a_autosmelt"
        grid[5][10] = "a_magnet"
        
        // Settler (Bottom-Right)
        grid[9][9] = "settler_start"
        grid[10][10] = "set_trade"
        grid[11][11] = "set_bounty"
        grid[8][10] = "set_rest"
        grid[7][11] = "set_soulbind"
        grid[10][8] = "set_siege"
        grid[8][8] = "set_night"
        
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
        val profile = plugin.identityManager.getPlayer(player.uniqueId) ?: return emptySet()
        return profile.unlockedSkillNodes?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
    }
    
    fun canUnlock(player: Player, nodeId: String): Boolean {
        val node = skillTree[nodeId] ?: return false
        val unlocked = getUnlockedNodes(player)
        
        // Already unlocked?
        if (unlocked.contains(nodeId)) return false
        
        // Enough points?
        if (getAvailableSkillPoints(player) < node.cost) return false
        
        // Has prerequisite?
        val hasConnection = node.connections.any { unlocked.contains(it) } || nodeId == "origin"
        if (!hasConnection) return false
        
        // Check exclusivity
        if (node.exclusiveWith.any { unlocked.contains(it) }) return false
        
        return true
    }
    
    fun unlockNode(player: Player, nodeId: String): Boolean {
        if (!canUnlock(player, nodeId)) return false
        
        val profile = plugin.identityManager.getPlayer(player.uniqueId) ?: return false
        
        // Add to comma-separated string
        val current = profile.unlockedSkillNodes ?: ""
        profile.unlockedSkillNodes = if (current.isBlank()) nodeId else "$current,$nodeId"
        
        // Apply effect immediately
        applySkillEffects(player)
        
        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f)
        player.sendMessage(Component.text("✓ Unlocked: ${skillTree[nodeId]?.name}", NamedTextColor.GREEN))
        
        return true
    }
    
    // ══════════════════════════════════════════════════════════════
    // EFFECT APPLICATION
    // ══════════════════════════════════════════════════════════════
    
    fun applySkillEffects(player: Player) {
        val unlocked = getUnlockedNodes(player)
        
        // Calculate totals
        var healthBonus = 0.0
        var speedBonus = 0.0f
        var armorBonus = 0.0
        var regenLevel = -1
        
        unlocked.forEach { nodeId ->
            when (val effect = skillTree[nodeId]?.effect) {
                is SkillEffect.MaxHealth -> healthBonus += effect.bonus
                is SkillEffect.MovementSpeed -> speedBonus += effect.bonus
                is SkillEffect.Armor -> armorBonus += effect.bonus
                is SkillEffect.Regeneration -> if (effect.level > regenLevel) regenLevel = effect.level
                else -> {} // Other effects handled in events
            }
        }
        
        // Apply Health
        player.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.baseValue = 20.0 + healthBonus
        
        // Apply Speed
        player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED)?.baseValue = 0.1 + speedBonus
        
        // Apply Armor
        player.getAttribute(Attribute.GENERIC_ARMOR)?.baseValue = armorBonus
        
        // Regen is handled in passive task
    }
    
    private fun startPassiveEffectTask() {
        plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            plugin.server.onlinePlayers.forEach { player ->
                applyPassiveEffects(player)
            }
        }, 100L, 100L) // Every 5 seconds
    }
    
    private fun applyPassiveEffects(player: Player) {
        val unlocked = getUnlockedNodes(player)
        
        unlocked.forEach { nodeId ->
            when (val effect = skillTree[nodeId]?.effect) {
                is SkillEffect.Regeneration -> {
                    player.addPotionEffect(PotionEffect(PotionEffectType.REGENERATION, 140, effect.level, false, false, true))
                }
                is SkillEffect.NightVision -> {
                    player.addPotionEffect(PotionEffect(PotionEffectType.NIGHT_VISION, 400, 0, false, false, true))
                }
                is SkillEffect.MiningSpeed -> {
                    player.addPotionEffect(PotionEffect(PotionEffectType.HASTE, 140, effect.level, false, false, true))
                }
                is SkillEffect.JumpBoost -> {
                    player.addPotionEffect(PotionEffect(PotionEffectType.JUMP_BOOST, 140, effect.level, false, false, true))
                }
                is SkillEffect.SwimSpeed -> {
                    if (player.isInWater) {
                        player.addPotionEffect(PotionEffect(PotionEffectType.DOLPHINS_GRACE, 140, effect.level, false, false, true))
                        player.addPotionEffect(PotionEffect(PotionEffectType.WATER_BREATHING, 140, 0, false, false, true))
                    }
                }
                is SkillEffect.FireResistance -> {
                    player.addPotionEffect(PotionEffect(PotionEffectType.FIRE_RESISTANCE, 140, 0, false, false, true))
                }
                else -> {}
            }
        }
    }
    
    // ══════════════════════════════════════════════════════════════
    // HELPER METHODS FOR OTHER MANAGERS
    // ══════════════════════════════════════════════════════════════
    
    // Combat Helpers
    fun getMeleeDamageMultiplier(player: Player): Double {
        return getUnlockedNodes(player).mapNotNull { skillTree[it]?.effect as? SkillEffect.MeleeDamage }
            .fold(1.0) { acc, e -> acc * e.multiplier }
    }
    
    fun getBowDamageMultiplier(player: Player): Double {
        return getUnlockedNodes(player).mapNotNull { skillTree[it]?.effect as? SkillEffect.BowDamage }
            .fold(1.0) { acc, e -> acc * e.multiplier }
    }
    
    fun getCritChance(player: Player): Double {
        return getUnlockedNodes(player).mapNotNull { skillTree[it]?.effect as? SkillEffect.CritChance }
            .sumOf { it.percent }
    }
    
    fun getCritMultiplier(player: Player): Double {
        return getUnlockedNodes(player).mapNotNull { skillTree[it]?.effect as? SkillEffect.CritMultiplier }
            .fold(1.5) { acc, e -> acc + (e.bonus - 1.0) } // Base 1.5x + bonuses
    }
    
    fun getLifeLeechPercent(player: Player): Double {
        return getUnlockedNodes(player).mapNotNull { skillTree[it]?.effect as? SkillEffect.LifeLeech }
            .sumOf { it.percent }
    }
    
    fun getExecuteDamage(player: Player): Pair<Double, Double>? {
        return getUnlockedNodes(player).mapNotNull { skillTree[it]?.effect as? SkillEffect.ExecuteDamage }
            .maxByOrNull { it.bonusMultiplier }?.let { it.thresholdPercent to it.bonusMultiplier }
    }
    
    fun getBerserkerBonus(player: Player): Pair<Double, Double>? {
        return getUnlockedNodes(player).mapNotNull { skillTree[it]?.effect as? SkillEffect.BerserkerMode }
            .maxByOrNull { it.damageBonus }?.let { it.hpThreshold to it.damageBonus }
    }
    
    fun getSneakDamageMultiplier(player: Player): Double {
        return getUnlockedNodes(player).mapNotNull { skillTree[it]?.effect as? SkillEffect.SneakDamage }
            .maxOfOrNull { it.multiplier } ?: 1.0
    }
    
    fun getThornsPercent(player: Player): Double {
        return getUnlockedNodes(player).mapNotNull { skillTree[it]?.effect as? SkillEffect.Thorns }
            .sumOf { it.percent }
    }
    
    fun getSiegeDamageMultiplier(player: Player): Double {
        return getUnlockedNodes(player).mapNotNull { skillTree[it]?.effect as? SkillEffect.SiegeDefender }
            .fold(1.0) { acc, e -> acc * e.damageMultiplier }
    }
    
    // Defense Helpers
    fun getDodgeChance(player: Player): Double {
        return getUnlockedNodes(player).mapNotNull { skillTree[it]?.effect as? SkillEffect.DodgeChance }
            .sumOf { it.percent }
    }
    
    fun hasPoise(player: Player): Boolean {
        return getUnlockedNodes(player).any { skillTree[it]?.effect is SkillEffect.Poise }
    }
    
    fun hasNoFallDamage(player: Player): Boolean {
        return getUnlockedNodes(player).any { skillTree[it]?.effect is SkillEffect.NoFallDamage }
    }
    
    fun hasColdResistance(player: Player): Boolean {
        return getUnlockedNodes(player).any { skillTree[it]?.effect is SkillEffect.ColdResistance }
    }
    
    fun hasDoubleJump(player: Player): Boolean {
        return getUnlockedNodes(player).any { skillTree[it]?.effect is SkillEffect.DoubleJump }
    }
    
    // Mining Helpers
    fun hasVeinMiner(player: Player): Boolean {
        return getUnlockedNodes(player).any { skillTree[it]?.effect is SkillEffect.VeinMiner }
    }
    
    fun hasAutoSmelt(player: Player): Boolean {
        return getUnlockedNodes(player).any { skillTree[it]?.effect is SkillEffect.AutoSmelt }
    }
    
    fun hasLumberjack(player: Player): Boolean {
        return getUnlockedNodes(player).any { skillTree[it]?.effect is SkillEffect.Lumberjack }
    }
    
    fun getDoubleDropChance(player: Player): Double {
        return getUnlockedNodes(player).mapNotNull { skillTree[it]?.effect as? SkillEffect.DoubleDrop }
            .sumOf { it.chance }
    }
    
    fun getMagnetRange(player: Player): Double {
        return getUnlockedNodes(player).mapNotNull { skillTree[it]?.effect as? SkillEffect.Magnetism }
            .maxOfOrNull { it.range } ?: 0.0
    }
    
    // Economy/Settler Helpers
    fun getRestMultiplier(player: Player): Double {
        return getUnlockedNodes(player).mapNotNull { skillTree[it]?.effect as? SkillEffect.RestfulSleep }
            .fold(1.0) { acc, e -> acc * e.multiplier }
    }
    
    fun getTradeDiscount(player: Player): Double {
        return getUnlockedNodes(player).mapNotNull { skillTree[it]?.effect as? SkillEffect.Diplomacy }
            .sumOf { it.discountPercent }
    }
    
    fun getBountyMultiplier(player: Player): Double {
        return getUnlockedNodes(player).mapNotNull { skillTree[it]?.effect as? SkillEffect.BountyHunter }
            .fold(1.0) { acc, e -> acc * e.goldMultiplier }
    }
    
    fun getSoulBindSlots(player: Player): Int {
        return getUnlockedNodes(player).mapNotNull { skillTree[it]?.effect as? SkillEffect.SoulBinding }
            .maxOfOrNull { it.slots } ?: 0
    }
    
    fun getXpMultiplier(player: Player): Double {
        return getUnlockedNodes(player).mapNotNull { skillTree[it]?.effect as? SkillEffect.XpBonus }
            .fold(1.0) { acc, e -> acc * e.multiplier }
    }
    
    // Alias for external callers
    fun applyAllSkillEffects(player: Player) {
        applySkillEffects(player)
    }
    
    fun getSwimSpeedLevel(player: Player): Int {
        return getUnlockedNodes(player).mapNotNull { skillTree[it]?.effect as? SkillEffect.SwimSpeed }
            .maxOfOrNull { it.level } ?: -1
    }
    
    fun hasWaterBreathing(player: Player): Boolean {
        return getUnlockedNodes(player).any { skillTree[it]?.effect is SkillEffect.WaterBreathing } ||
               getSwimSpeedLevel(player) >= 0 // SwimSpeed also gives water breathing
    }
    
    // ══════════════════════════════════════════════════════════════
    // GUI - Skill Tree Display
    // ══════════════════════════════════════════════════════════════
    
    fun openSkillTree(player: Player) {
        val inv = Bukkit.createInventory(null, 54, Component.text("✦ SKILL TREE ✦", NamedTextColor.GOLD, TextDecoration.BOLD))
        
        val viewport = viewportPositions.getOrPut(player.uniqueId) { 7 to 7 }
        val unlocked = getUnlockedNodes(player)
        val availablePoints = getAvailableSkillPoints(player)
        val profile = plugin.identityManager.getPlayer(player.uniqueId)
        
        // Fill viewport with gradient background
        for (i in 0 until 45) {
            inv.setItem(i, ItemStack(Material.BLACK_STAINED_GLASS_PANE).apply {
                editMeta { it.displayName(Component.text(" ")) }
            })
        }
        
        // Place nodes
        for (row in 0 until VIEWPORT_HEIGHT) {
            for (col in 0 until VIEWPORT_WIDTH) {
                val gridX = viewport.first - 4 + col
                val gridY = viewport.second - 2 + row
                
                if (gridX in 0 until GRID_SIZE && gridY in 0 until GRID_SIZE) {
                    val nodeId = gridLayout[gridX][gridY]
                    if (nodeId != null) {
                        val node = skillTree[nodeId]
                        if (node != null) {
                            val isUnlocked = unlocked.contains(nodeId)
                            val canUnlockNode = canUnlock(player, nodeId)
                            inv.setItem(row * 9 + col, createNodeItem(node, isUnlocked, canUnlockNode, unlocked))
                        }
                    }
                }
            }
        }
        
        // ═══ CONTROL BAR (Bottom Row) ═══
        // Navigation
        inv.setItem(45, createNavItem(Material.RED_CONCRETE, "◀ West", NamedTextColor.RED))
        inv.setItem(46, createNavItem(Material.LIME_CONCRETE, "▲ North", NamedTextColor.GREEN))
        inv.setItem(47, createNavItem(Material.LIME_CONCRETE, "▼ South", NamedTextColor.GREEN))
        inv.setItem(48, createNavItem(Material.RED_CONCRETE, "▶ East", NamedTextColor.RED))
        inv.setItem(49, createNavItem(Material.YELLOW_CONCRETE, "⌂ Center", NamedTextColor.YELLOW))
        
        // Stats Display
        inv.setItem(50, ItemStack(Material.EXPERIENCE_BOTTLE).apply {
            editMeta { meta ->
                meta.displayName(Component.text("⬢ Level ${profile?.level ?: 1}", NamedTextColor.GREEN, TextDecoration.BOLD))
                meta.lore(listOf(
                    Component.text("XP: ${profile?.currentXp ?: 0}", NamedTextColor.GRAY)
                ))
            }
        })
        
        // Skill Points
        val pointColor = if (availablePoints > 0) NamedTextColor.GOLD else NamedTextColor.GRAY
        inv.setItem(51, ItemStack(Material.NETHER_STAR).apply {
            editMeta { meta ->
                meta.displayName(Component.text("✦ $availablePoints Skill Points", pointColor, TextDecoration.BOLD))
                meta.lore(listOf(
                    Component.empty(),
                    Component.text("Earn points by leveling up!", NamedTextColor.YELLOW),
                    Component.text("1 point per level", NamedTextColor.GRAY)
                ))
                if (availablePoints > 0) {
                    meta.addEnchant(Enchantment.UNBREAKING, 1, true)
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
                }
            }
        })
        
        // Progress
        val totalNodes = skillTree.size
        val unlockedCount = unlocked.size
        val progressPercent = ((unlockedCount.toDouble() / totalNodes) * 100).toInt()
        inv.setItem(52, ItemStack(Material.FILLED_MAP).apply {
            editMeta { meta ->
                meta.displayName(Component.text("📊 Progress: $progressPercent%", NamedTextColor.AQUA, TextDecoration.BOLD))
                meta.lore(listOf(
                    Component.text("Nodes Unlocked: $unlockedCount / $totalNodes", NamedTextColor.GRAY),
                    Component.empty(),
                    createProgressBar(progressPercent)
                ))
            }
        })
        
        // Legend
        inv.setItem(53, ItemStack(Material.BOOK).apply {
            editMeta { meta ->
                meta.displayName(Component.text("📖 Legend", NamedTextColor.WHITE, TextDecoration.BOLD))
                meta.lore(listOf(
                    Component.empty(),
                    Component.text("⬢ Vanguard", NamedTextColor.BLUE).append(Component.text(" - Tank/Defense", NamedTextColor.GRAY)),
                    Component.text("⬢ Berserker", NamedTextColor.RED).append(Component.text(" - DPS/Combat", NamedTextColor.GRAY)),
                    Component.text("⬢ Scout", NamedTextColor.AQUA).append(Component.text(" - Mobility/Stealth", NamedTextColor.GRAY)),
                    Component.text("⬢ Artisan", NamedTextColor.GOLD).append(Component.text(" - Mining/Gathering", NamedTextColor.GRAY)),
                    Component.text("⬢ Settler", NamedTextColor.GREEN).append(Component.text(" - Economy/City", NamedTextColor.GRAY)),
                    Component.empty(),
                    Component.text("✓ Green", NamedTextColor.GREEN).append(Component.text(" = Unlocked", NamedTextColor.GRAY)),
                    Component.text("○ Yellow", NamedTextColor.YELLOW).append(Component.text(" = Available", NamedTextColor.GRAY)),
                    Component.text("✗ Gray", NamedTextColor.DARK_GRAY).append(Component.text(" = Locked", NamedTextColor.GRAY))
                ))
            }
        })
        
        player.openInventory(inv)
    }
    
    private fun createProgressBar(percent: Int): Component {
        val filled = percent / 10
        val empty = 10 - filled
        return Component.text("[")
            .append(Component.text("▮".repeat(filled), NamedTextColor.GREEN))
            .append(Component.text("▯".repeat(empty), NamedTextColor.DARK_GRAY))
            .append(Component.text("]"))
    }
    
    private fun createNodeItem(node: SkillNode, isUnlocked: Boolean, canUnlock: Boolean, allUnlocked: Set<String>): ItemStack {
        val item = ItemStack(node.icon)
        item.editMeta { meta ->
            // Category color
            val categoryColor = when (node.category) {
                SkillCategory.VANGUARD -> NamedTextColor.BLUE
                SkillCategory.BERSERKER -> NamedTextColor.RED
                SkillCategory.SCOUT -> NamedTextColor.AQUA
                SkillCategory.ARTISAN -> NamedTextColor.GOLD
                SkillCategory.SETTLER -> NamedTextColor.GREEN
                SkillCategory.CORE -> NamedTextColor.WHITE
                SkillCategory.KEYSTONE -> NamedTextColor.LIGHT_PURPLE
            }
            
            val statusColor = when {
                isUnlocked -> NamedTextColor.GREEN
                canUnlock -> NamedTextColor.YELLOW
                else -> NamedTextColor.DARK_GRAY
            }
            
            val statusIcon = when {
                isUnlocked -> "✓ "
                canUnlock -> "○ "
                else -> "✗ "
            }
            
            meta.displayName(Component.text(statusIcon, statusColor)
                .append(Component.text(node.name, categoryColor, TextDecoration.BOLD)))
            
            val lore = mutableListOf<Component>()
            
            // Tier badge
            val tierColor = when (node.tier) {
                NodeTier.MINOR -> NamedTextColor.GRAY
                NodeTier.NOTABLE -> NamedTextColor.BLUE
                NodeTier.KEYSTONE -> NamedTextColor.LIGHT_PURPLE
            }
            lore.add(Component.text("【${node.tier.displayName}】", tierColor))
            lore.add(Component.text(node.category.displayName, categoryColor))
            lore.add(Component.empty())
            
            // Effect (Main attraction)
            lore.add(Component.text("▸ ", NamedTextColor.WHITE).append(Component.text(node.effect.toString(), NamedTextColor.WHITE)))
            lore.add(Component.empty())
            
            // Connections
            val connectedNames = node.connections.mapNotNull { skillTree[it]?.name }
            if (connectedNames.isNotEmpty()) {
                lore.add(Component.text("Connects to:", NamedTextColor.DARK_GRAY))
                connectedNames.take(3).forEach { name ->
                    val isConnUnlocked = node.connections.any { allUnlocked.contains(it) && skillTree[it]?.name == name }
                    val connColor = if (isConnUnlocked) NamedTextColor.GREEN else NamedTextColor.GRAY
                    lore.add(Component.text("  ├ $name", connColor))
                }
                if (connectedNames.size > 3) {
                    lore.add(Component.text("  └ ...and ${connectedNames.size - 3} more", NamedTextColor.DARK_GRAY))
                }
                lore.add(Component.empty())
            }
            
            // Status
            when {
                isUnlocked -> {
                    lore.add(Component.text("══════════════════", NamedTextColor.GREEN))
                    lore.add(Component.text("   ✓ UNLOCKED", NamedTextColor.GREEN, TextDecoration.BOLD))
                    lore.add(Component.text("══════════════════", NamedTextColor.GREEN))
                }
                canUnlock -> {
                    lore.add(Component.text("══════════════════", NamedTextColor.YELLOW))
                    lore.add(Component.text("   ⚡ CLICK TO UNLOCK", NamedTextColor.YELLOW, TextDecoration.BOLD))
                    lore.add(Component.text("   Cost: ${node.cost} point${if (node.cost > 1) "s" else ""}", NamedTextColor.GOLD))
                    lore.add(Component.text("══════════════════", NamedTextColor.YELLOW))
                }
                else -> {
                    lore.add(Component.text("══════════════════", NamedTextColor.DARK_GRAY))
                    lore.add(Component.text("   ✗ LOCKED", NamedTextColor.RED))
                    if (node.exclusiveWith.isNotEmpty()) {
                        val exclusiveNames = node.exclusiveWith.mapNotNull { skillTree[it]?.name }
                        lore.add(Component.text("   Exclusive with:", NamedTextColor.DARK_RED))
                        exclusiveNames.forEach { lore.add(Component.text("   • $it", NamedTextColor.RED)) }
                    }
                    lore.add(Component.text("══════════════════", NamedTextColor.DARK_GRAY))
                }
            }
            
            meta.lore(lore)
            
            if (isUnlocked) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true)
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
        }
        return item
    }
    
    private fun createNavItem(material: Material, name: String, color: NamedTextColor): ItemStack {
        return ItemStack(material).apply {
            editMeta { meta ->
                meta.displayName(Component.text(name, color, TextDecoration.BOLD))
                meta.lore(listOf(Component.text("Click to navigate", NamedTextColor.GRAY)))
            }
        }
    }
    
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val title = event.view.title()
        if (title != Component.text("✦ SKILL TREE ✦", NamedTextColor.GOLD, TextDecoration.BOLD)) return
        
        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        val slot = event.rawSlot
        
        // Control buttons
        when (slot) {
            45 -> scrollViewport(player, -1, 0)
            46 -> scrollViewport(player, 0, -1)
            47 -> scrollViewport(player, 0, 1)
            48 -> scrollViewport(player, 1, 0)
            49 -> { viewportPositions[player.uniqueId] = 7 to 7; openSkillTree(player) }
            else -> {
                // Node click
                if (slot < 45) {
                    val row = slot / 9
                    val col = slot % 9
                    val viewport = viewportPositions[player.uniqueId] ?: (7 to 7)
                    val gridX = viewport.first - 4 + col
                    val gridY = viewport.second - 2 + row
                    
                    if (gridX in 0 until GRID_SIZE && gridY in 0 until GRID_SIZE) {
                        val nodeId = gridLayout[gridX][gridY]
                        if (nodeId != null && unlockNode(player, nodeId)) {
                            openSkillTree(player) // Refresh
                        }
                    }
                }
            }
        }
    }
    
    private fun scrollViewport(player: Player, dx: Int, dy: Int) {
        val current = viewportPositions.getOrPut(player.uniqueId) { 7 to 7 }
        val newX = (current.first + dx).coerceIn(0, GRID_SIZE - 1)
        val newY = (current.second + dy).coerceIn(0, GRID_SIZE - 1)
        viewportPositions[player.uniqueId] = newX to newY
        openSkillTree(player)
    }
    
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            applySkillEffects(event.player)
        }, 20L)
    }
}
