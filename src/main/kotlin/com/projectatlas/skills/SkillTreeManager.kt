package com.projectatlas.skills

import com.projectatlas.AtlasPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.NamespacedKey
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * BALANCED SKILL TREE SYSTEM
 * 
 * Design Philosophy:
 * 1. Five Role Branches: Vanguard, Berserker, Scout, Artisan, Settler
 * 2. Balanced numbers - no overpowered stacking
 * 3. Meaningful trade-offs with exclusive keystones
 * 4. Clear level progression (30 total nodes, ~30 points at max level)
 * 5. Each branch has 6 nodes: 3 Minor (1pt), 2 Notable (2pt), 1 Keystone (3pt)
 * 
 * Total Skill Points: 1 per level (max level 50 = 50 points)
 * Points needed for FULL one branch: 1+1+1+2+2+3 = 10 points
 * Players can fully complete ~4-5 branches at max level
 */
class SkillTreeManager(private val plugin: AtlasPlugin) : Listener {

    private val viewportPositions = ConcurrentHashMap<UUID, Pair<Int, Int>>()
    private val skillTree: Map<String, SkillNode>
    private val gridLayout: Array<Array<String?>>
    
    private val menuKey = NamespacedKey(plugin, "skill_menu")
    
    companion object {
        const val GRID_SIZE = 15
        const val VIEWPORT_WIDTH = 9
        const val VIEWPORT_HEIGHT = 5
        const val SKILL_POINTS_PER_LEVEL = 1
    }
    
    init {
        skillTree = buildSkillTree()
        gridLayout = buildGridLayout()
        startPassiveEffectTask()
    }
    
    // ══════════════════════════════════════════════════════════════
    // EXPANDED SKILL TREE - 105 NODES (21 per branch)
    // Small incremental bonuses for balanced progression
    // Max 50 skill points = specialize in 2-3 branches
    // ══════════════════════════════════════════════════════════════
    
    private fun buildSkillTree(): Map<String, SkillNode> {
        val nodes = mutableMapOf<String, SkillNode>()
        
        // ═══ ORIGIN ═══
        nodes["origin"] = SkillNode(
            "origin", "Awakening", "Your journey begins. Unlocks all 5 branches.",
            Material.NETHER_STAR, SkillCategory.CORE, NodeTier.NOTABLE,
            listOf("van_hp1", "ber_dmg1", "sco_spd1", "art_haste1", "set_eco1"),
            SkillEffect.MaxHealth(2.0), cost = 0
        )
        
        // ═══════════════════════════════════════════════════════════
        // VANGUARD (21 nodes) - Tank / Defense / Sustain
        // ═══════════════════════════════════════════════════════════
        
        // Health Path (7 nodes)
        nodes["van_hp1"] = node("van_hp1", "Toughness I", "+2 HP", Material.APPLE, SkillCategory.VANGUARD, SkillEffect.MaxHealth(2.0), "origin", "van_hp2", "van_arm1")
        nodes["van_hp2"] = node("van_hp2", "Toughness II", "+2 HP", Material.APPLE, SkillCategory.VANGUARD, SkillEffect.MaxHealth(2.0), "van_hp1", "van_hp3")
        nodes["van_hp3"] = node("van_hp3", "Vitality I", "+3 HP", Material.GOLDEN_APPLE, SkillCategory.VANGUARD, SkillEffect.MaxHealth(3.0), "van_hp2", "van_hp4", cost = 2)
        nodes["van_hp4"] = node("van_hp4", "Vitality II", "+3 HP", Material.GOLDEN_APPLE, SkillCategory.VANGUARD, SkillEffect.MaxHealth(3.0), "van_hp3", "van_hp5", cost = 2)
        nodes["van_hp5"] = node("van_hp5", "Endurance I", "+4 HP", Material.GOLDEN_APPLE, SkillCategory.VANGUARD, SkillEffect.MaxHealth(4.0), "van_hp4", "van_hp6", cost = 2)
        nodes["van_hp6"] = node("van_hp6", "Endurance II", "+4 HP", Material.ENCHANTED_GOLDEN_APPLE, SkillCategory.VANGUARD, SkillEffect.MaxHealth(4.0), "van_hp5", "van_colossus", cost = 2)
        nodes["van_colossus"] = SkillNode("van_colossus", "Colossus", "+6 HP, Knockback Immune", Material.ENCHANTED_GOLDEN_APPLE, SkillCategory.VANGUARD, NodeTier.KEYSTONE, listOf("van_hp6"), SkillEffect.Poise(true), 3, listOf("ber_execute"))
        
        // Armor Path (7 nodes)
        nodes["van_arm1"] = node("van_arm1", "Iron Skin I", "+1 Armor", Material.IRON_NUGGET, SkillCategory.VANGUARD, SkillEffect.Armor(1.0), "van_hp1", "van_arm2", "van_thorn1")
        nodes["van_arm2"] = node("van_arm2", "Iron Skin II", "+1 Armor", Material.IRON_NUGGET, SkillCategory.VANGUARD, SkillEffect.Armor(1.0), "van_arm1", "van_arm3")
        nodes["van_arm3"] = node("van_arm3", "Steel Wall I", "+2 Armor", Material.IRON_INGOT, SkillCategory.VANGUARD, SkillEffect.Armor(2.0), "van_arm2", "van_arm4", cost = 2)
        nodes["van_arm4"] = node("van_arm4", "Steel Wall II", "+2 Armor", Material.IRON_BLOCK, SkillCategory.VANGUARD, SkillEffect.Armor(2.0), "van_arm3", "van_fortress", cost = 2)
        nodes["van_fortress"] = SkillNode("van_fortress", "Fortress", "+3 Armor, Fire Resist", Material.NETHERITE_INGOT, SkillCategory.VANGUARD, NodeTier.KEYSTONE, listOf("van_arm4"), SkillEffect.FireResistance(true), 3)
        
        // Thorns/Regen Path (7 nodes)
        nodes["van_thorn1"] = node("van_thorn1", "Spikes I", "Reflect 5% dmg", Material.CACTUS, SkillCategory.VANGUARD, SkillEffect.Thorns(0.05), "van_arm1", "van_thorn2")
        nodes["van_thorn2"] = node("van_thorn2", "Spikes II", "Reflect 5% dmg", Material.CACTUS, SkillCategory.VANGUARD, SkillEffect.Thorns(0.05), "van_thorn1", "van_thorn3", cost = 2)
        nodes["van_thorn3"] = node("van_thorn3", "Retribution", "Reflect 8% dmg", Material.SWEET_BERRIES, SkillCategory.VANGUARD, SkillEffect.Thorns(0.08), "van_thorn2", "van_regen1", cost = 2)
        nodes["van_regen1"] = node("van_regen1", "Recovery I", "Regen I", Material.GLISTERING_MELON_SLICE, SkillCategory.VANGUARD, SkillEffect.Regeneration(0), "van_thorn3", "van_regen2")
        nodes["van_regen2"] = node("van_regen2", "Recovery II", "Regen II", Material.GOLDEN_CARROT, SkillCategory.VANGUARD, SkillEffect.Regeneration(1), "van_regen1", "van_undying", cost = 2)
        nodes["van_undying"] = SkillNode("van_undying", "Undying", "Regen II + 10% Dodge", Material.TOTEM_OF_UNDYING, SkillCategory.VANGUARD, NodeTier.KEYSTONE, listOf("van_regen2"), SkillEffect.DodgeChance(0.10), 3)
        
        // ═══════════════════════════════════════════════════════════
        // BERSERKER (21 nodes) - Damage / Crit / Lifesteal
        // ═══════════════════════════════════════════════════════════
        
        // Melee Damage Path
        nodes["ber_dmg1"] = node("ber_dmg1", "Strength I", "+3% Melee", Material.WOODEN_SWORD, SkillCategory.BERSERKER, SkillEffect.MeleeDamage(1.03), "origin", "ber_dmg2", "ber_crit1")
        nodes["ber_dmg2"] = node("ber_dmg2", "Strength II", "+3% Melee", Material.STONE_SWORD, SkillCategory.BERSERKER, SkillEffect.MeleeDamage(1.03), "ber_dmg1", "ber_dmg3")
        nodes["ber_dmg3"] = node("ber_dmg3", "Power I", "+5% Melee", Material.IRON_SWORD, SkillCategory.BERSERKER, SkillEffect.MeleeDamage(1.05), "ber_dmg2", "ber_dmg4", cost = 2)
        nodes["ber_dmg4"] = node("ber_dmg4", "Power II", "+5% Melee", Material.IRON_SWORD, SkillCategory.BERSERKER, SkillEffect.MeleeDamage(1.05), "ber_dmg3", "ber_dmg5", cost = 2)
        nodes["ber_dmg5"] = node("ber_dmg5", "Brutality I", "+6% Melee", Material.DIAMOND_SWORD, SkillCategory.BERSERKER, SkillEffect.MeleeDamage(1.06), "ber_dmg4", "ber_dmg6", cost = 2)
        nodes["ber_dmg6"] = node("ber_dmg6", "Brutality II", "+6% Melee", Material.DIAMOND_SWORD, SkillCategory.BERSERKER, SkillEffect.MeleeDamage(1.06), "ber_dmg5", "ber_execute", cost = 2)
        nodes["ber_execute"] = SkillNode("ber_execute", "Executioner", "+25% to low HP foes", Material.NETHERITE_AXE, SkillCategory.BERSERKER, NodeTier.KEYSTONE, listOf("ber_dmg6"), SkillEffect.ExecuteDamage(0.25, 1.25), 3, listOf("van_colossus"))
        
        // Crit Path
        nodes["ber_crit1"] = node("ber_crit1", "Precision I", "+4% Crit", Material.FLINT, SkillCategory.BERSERKER, SkillEffect.CritChance(0.04), "ber_dmg1", "ber_crit2", "ber_leech1")
        nodes["ber_crit2"] = node("ber_crit2", "Precision II", "+4% Crit", Material.FLINT, SkillCategory.BERSERKER, SkillEffect.CritChance(0.04), "ber_crit1", "ber_crit3")
        nodes["ber_crit3"] = node("ber_crit3", "Deadly I", "+5% Crit", Material.SPECTRAL_ARROW, SkillCategory.BERSERKER, SkillEffect.CritChance(0.05), "ber_crit2", "ber_critdmg1", cost = 2)
        nodes["ber_critdmg1"] = node("ber_critdmg1", "Savage I", "+10% Crit Dmg", Material.TNT, SkillCategory.BERSERKER, SkillEffect.CritMultiplier(1.10), "ber_crit3", "ber_critdmg2", cost = 2)
        nodes["ber_critdmg2"] = node("ber_critdmg2", "Savage II", "+15% Crit Dmg", Material.TNT, SkillCategory.BERSERKER, SkillEffect.CritMultiplier(1.15), "ber_critdmg1", "ber_assassin", cost = 2)
        nodes["ber_assassin"] = SkillNode("ber_assassin", "Assassin", "+20% Crit Dmg, +5% Crit", Material.WITHER_SKELETON_SKULL, SkillCategory.BERSERKER, NodeTier.KEYSTONE, listOf("ber_critdmg2"), SkillEffect.CritChance(0.05), 3)
        
        // Lifesteal Path
        nodes["ber_leech1"] = node("ber_leech1", "Vampiric I", "2% Lifesteal", Material.REDSTONE, SkillCategory.BERSERKER, SkillEffect.LifeLeech(0.02), "ber_crit1", "ber_leech2")
        nodes["ber_leech2"] = node("ber_leech2", "Vampiric II", "2% Lifesteal", Material.REDSTONE, SkillCategory.BERSERKER, SkillEffect.LifeLeech(0.02), "ber_leech1", "ber_leech3", cost = 2)
        nodes["ber_leech3"] = node("ber_leech3", "Blood Drinker", "3% Lifesteal", Material.CRIMSON_FUNGUS, SkillCategory.BERSERKER, SkillEffect.LifeLeech(0.03), "ber_leech2", "ber_rage", cost = 2)
        nodes["ber_rage"] = SkillNode("ber_rage", "Blood Rage", "+20% dmg when <30% HP", Material.DRAGON_BREATH, SkillCategory.BERSERKER, NodeTier.KEYSTONE, listOf("ber_leech3"), SkillEffect.BerserkerMode(0.30, 1.20), 3)
        
        // ═══════════════════════════════════════════════════════════
        // SCOUT (21 nodes) - Speed / Dodge / Stealth
        // ═══════════════════════════════════════════════════════════
        
        // Speed Path
        nodes["sco_spd1"] = node("sco_spd1", "Quickfoot I", "+2% Speed", Material.LEATHER_BOOTS, SkillCategory.SCOUT, SkillEffect.MovementSpeed(0.002f), "origin", "sco_spd2", "sco_dodge1")
        nodes["sco_spd2"] = node("sco_spd2", "Quickfoot II", "+2% Speed", Material.LEATHER_BOOTS, SkillCategory.SCOUT, SkillEffect.MovementSpeed(0.002f), "sco_spd1", "sco_spd3")
        nodes["sco_spd3"] = node("sco_spd3", "Runner I", "+3% Speed", Material.CHAINMAIL_BOOTS, SkillCategory.SCOUT, SkillEffect.MovementSpeed(0.003f), "sco_spd2", "sco_spd4", cost = 2)
        nodes["sco_spd4"] = node("sco_spd4", "Runner II", "+3% Speed", Material.IRON_BOOTS, SkillCategory.SCOUT, SkillEffect.MovementSpeed(0.003f), "sco_spd3", "sco_spd5", cost = 2)
        nodes["sco_spd5"] = node("sco_spd5", "Windrunner I", "+4% Speed", Material.DIAMOND_BOOTS, SkillCategory.SCOUT, SkillEffect.MovementSpeed(0.004f), "sco_spd4", "sco_spd6", cost = 2)
        nodes["sco_spd6"] = node("sco_spd6", "Windrunner II", "+4% Speed", Material.GOLDEN_BOOTS, SkillCategory.SCOUT, SkillEffect.MovementSpeed(0.004f), "sco_spd5", "sco_djump", cost = 2)
        nodes["sco_djump"] = SkillNode("sco_djump", "Double Jump", "Jump in mid-air!", Material.SLIME_BLOCK, SkillCategory.SCOUT, NodeTier.KEYSTONE, listOf("sco_spd6"), SkillEffect.DoubleJump(true), 3, listOf("art_vein"))
        
        // Dodge/Defense Path
        nodes["sco_dodge1"] = node("sco_dodge1", "Evasion I", "+4% Dodge", Material.PHANTOM_MEMBRANE, SkillCategory.SCOUT, SkillEffect.DodgeChance(0.04), "sco_spd1", "sco_dodge2", "sco_sneak1")
        nodes["sco_dodge2"] = node("sco_dodge2", "Evasion II", "+4% Dodge", Material.PHANTOM_MEMBRANE, SkillCategory.SCOUT, SkillEffect.DodgeChance(0.04), "sco_dodge1", "sco_dodge3")
        nodes["sco_dodge3"] = node("sco_dodge3", "Agility I", "+5% Dodge", Material.RABBIT_FOOT, SkillCategory.SCOUT, SkillEffect.DodgeChance(0.05), "sco_dodge2", "sco_nofall", cost = 2)
        nodes["sco_nofall"] = node("sco_nofall", "Featherfall", "No Fall Damage", Material.FEATHER, SkillCategory.SCOUT, SkillEffect.NoFallDamage(true), "sco_dodge3", "sco_acrobat", cost = 2)
        nodes["sco_acrobat"] = SkillNode("sco_acrobat", "Acrobat", "+8% Dodge, Jump Boost", Material.ELYTRA, SkillCategory.SCOUT, NodeTier.KEYSTONE, listOf("sco_nofall"), SkillEffect.JumpBoost(1), 3)
        
        // Stealth Path
        nodes["sco_sneak1"] = node("sco_sneak1", "Shadow I", "+8% Sneak Dmg", Material.BLACK_DYE, SkillCategory.SCOUT, SkillEffect.SneakDamage(1.08), "sco_dodge1", "sco_sneak2")
        nodes["sco_sneak2"] = node("sco_sneak2", "Shadow II", "+8% Sneak Dmg", Material.BLACK_DYE, SkillCategory.SCOUT, SkillEffect.SneakDamage(1.08), "sco_sneak1", "sco_sneak3", cost = 2)
        nodes["sco_sneak3"] = node("sco_sneak3", "Backstab I", "+12% Sneak Dmg", Material.INK_SAC, SkillCategory.SCOUT, SkillEffect.SneakDamage(1.12), "sco_sneak2", "sco_sneak4", cost = 2)
        nodes["sco_sneak4"] = node("sco_sneak4", "Backstab II", "+12% Sneak Dmg", Material.ENDER_EYE, SkillCategory.SCOUT, SkillEffect.SneakDamage(1.12), "sco_sneak3", "sco_ninja", cost = 2)
        nodes["sco_ninja"] = SkillNode("sco_ninja", "Ninja", "+20% Sneak, Night Vision", Material.WITHER_ROSE, SkillCategory.SCOUT, NodeTier.KEYSTONE, listOf("sco_sneak4"), SkillEffect.NightVision(true), 3)
        
        // ═══════════════════════════════════════════════════════════
        // ARTISAN (21 nodes) - Mining / Gathering / Utility
        // ═══════════════════════════════════════════════════════════
        
        // Haste Path
        nodes["art_haste1"] = node("art_haste1", "Miner I", "Haste I", Material.WOODEN_PICKAXE, SkillCategory.ARTISAN, SkillEffect.MiningSpeed(0), "origin", "art_haste2", "art_luck1")
        nodes["art_haste2"] = node("art_haste2", "Miner II", "Haste I", Material.STONE_PICKAXE, SkillCategory.ARTISAN, SkillEffect.MiningSpeed(0), "art_haste1", "art_haste3")
        nodes["art_haste3"] = node("art_haste3", "Excavator I", "Haste II", Material.IRON_PICKAXE, SkillCategory.ARTISAN, SkillEffect.MiningSpeed(1), "art_haste2", "art_haste4", cost = 2)
        nodes["art_haste4"] = node("art_haste4", "Excavator II", "Haste II", Material.DIAMOND_PICKAXE, SkillCategory.ARTISAN, SkillEffect.MiningSpeed(1), "art_haste3", "art_haste5", cost = 2)
        nodes["art_haste5"] = node("art_haste5", "Tunneler I", "Haste III", Material.DIAMOND_PICKAXE, SkillCategory.ARTISAN, SkillEffect.MiningSpeed(2), "art_haste4", "art_haste6", cost = 2)
        nodes["art_haste6"] = node("art_haste6", "Tunneler II", "Haste III", Material.NETHERITE_PICKAXE, SkillCategory.ARTISAN, SkillEffect.MiningSpeed(2), "art_haste5", "art_vein", cost = 2)
        nodes["art_vein"] = SkillNode("art_vein", "Vein Miner", "Mine ore veins!", Material.DIAMOND_ORE, SkillCategory.ARTISAN, NodeTier.KEYSTONE, listOf("art_haste6"), SkillEffect.VeinMiner(true), 3, listOf("sco_djump"))
        
        // Luck/Fortune Path
        nodes["art_luck1"] = node("art_luck1", "Lucky I", "Luck I", Material.LAPIS_LAZULI, SkillCategory.ARTISAN, SkillEffect.LuckBonus(0), "art_haste1", "art_luck2", "art_timber1")
        nodes["art_luck2"] = node("art_luck2", "Lucky II", "Luck I", Material.LAPIS_LAZULI, SkillCategory.ARTISAN, SkillEffect.LuckBonus(0), "art_luck1", "art_luck3")
        nodes["art_luck3"] = node("art_luck3", "Fortune I", "10% Double Drop", Material.GOLD_NUGGET, SkillCategory.ARTISAN, SkillEffect.DoubleDrop(0.10), "art_luck2", "art_luck4", cost = 2)
        nodes["art_luck4"] = node("art_luck4", "Fortune II", "10% Double Drop", Material.GOLD_INGOT, SkillCategory.ARTISAN, SkillEffect.DoubleDrop(0.10), "art_luck3", "art_prospector", cost = 2)
        nodes["art_prospector"] = SkillNode("art_prospector", "Prospector", "15% Double, Luck II", Material.GOLD_BLOCK, SkillCategory.ARTISAN, NodeTier.KEYSTONE, listOf("art_luck4"), SkillEffect.DoubleDrop(0.15), 3)
        
        // Utility Path
        nodes["art_timber1"] = node("art_timber1", "Woodsman I", "Lumberjack", Material.WOODEN_AXE, SkillCategory.ARTISAN, SkillEffect.Lumberjack(true), "art_luck1", "art_timber2")
        nodes["art_timber2"] = node("art_timber2", "Woodsman II", "+2 HP (hardy)", Material.IRON_AXE, SkillCategory.ARTISAN, SkillEffect.MaxHealth(2.0), "art_timber1", "art_magnet1", cost = 2)
        nodes["art_magnet1"] = node("art_magnet1", "Magnet I", "3 block pickup", Material.IRON_INGOT, SkillCategory.ARTISAN, SkillEffect.Magnetism(3.0), "art_timber2", "art_magnet2", cost = 2)
        nodes["art_magnet2"] = node("art_magnet2", "Magnet II", "5 block pickup", Material.LODESTONE, SkillCategory.ARTISAN, SkillEffect.Magnetism(5.0), "art_magnet1", "art_autosmelt", cost = 2)
        nodes["art_autosmelt"] = SkillNode("art_autosmelt", "Smelting Touch", "Ores auto-smelt", Material.BLAST_FURNACE, SkillCategory.ARTISAN, NodeTier.KEYSTONE, listOf("art_magnet2"), SkillEffect.AutoSmelt(true), 3)
        
        // ═══════════════════════════════════════════════════════════
        // SETTLER (21 nodes) - Economy / Social / Utility
        // ═══════════════════════════════════════════════════════════
        
        // Economy Path
        nodes["set_eco1"] = node("set_eco1", "Trader I", "2% Discount", Material.EMERALD, SkillCategory.SETTLER, SkillEffect.Diplomacy(0.02), "origin", "set_eco2", "set_rest1")
        nodes["set_eco2"] = node("set_eco2", "Trader II", "2% Discount", Material.EMERALD, SkillCategory.SETTLER, SkillEffect.Diplomacy(0.02), "set_eco1", "set_eco3")
        nodes["set_eco3"] = node("set_eco3", "Merchant I", "3% Discount", Material.EMERALD_BLOCK, SkillCategory.SETTLER, SkillEffect.Diplomacy(0.03), "set_eco2", "set_eco4", cost = 2)
        nodes["set_eco4"] = node("set_eco4", "Merchant II", "3% Discount", Material.EMERALD_BLOCK, SkillCategory.SETTLER, SkillEffect.Diplomacy(0.03), "set_eco3", "set_bounty1", cost = 2)
        nodes["set_bounty1"] = node("set_bounty1", "Hunter I", "+5% Quest Gold", Material.GOLD_NUGGET, SkillCategory.SETTLER, SkillEffect.BountyHunter(1.05), "set_eco4", "set_bounty2", cost = 2)
        nodes["set_bounty2"] = node("set_bounty2", "Hunter II", "+8% Quest Gold", Material.GOLD_INGOT, SkillCategory.SETTLER, SkillEffect.BountyHunter(1.08), "set_bounty1", "set_tycoon", cost = 2)
        nodes["set_tycoon"] = SkillNode("set_tycoon", "Tycoon", "+12% Gold, 5% Discount", Material.GOLD_BLOCK, SkillCategory.SETTLER, NodeTier.KEYSTONE, listOf("set_bounty2"), SkillEffect.BountyHunter(1.12), 3)
        
        // Rest/Survival Path
        nodes["set_rest1"] = node("set_rest1", "Rested I", "+10% Sleep Heal", Material.WHITE_BED, SkillCategory.SETTLER, SkillEffect.RestfulSleep(1.10), "set_eco1", "set_rest2", "set_xp1")
        nodes["set_rest2"] = node("set_rest2", "Rested II", "+10% Sleep Heal", Material.RED_BED, SkillCategory.SETTLER, SkillEffect.RestfulSleep(1.10), "set_rest1", "set_rest3")
        nodes["set_rest3"] = node("set_rest3", "Comfort I", "+15% Sleep Heal", Material.CYAN_BED, SkillCategory.SETTLER, SkillEffect.RestfulSleep(1.15), "set_rest2", "set_night", cost = 2)
        nodes["set_night"] = node("set_night", "Night Owl", "Night Vision", Material.ENDER_EYE, SkillCategory.SETTLER, SkillEffect.NightVision(true), "set_rest3", "set_aqua", cost = 2)
        nodes["set_aqua"] = node("set_aqua", "Mariner", "Water Breathing", Material.TURTLE_HELMET, SkillCategory.SETTLER, SkillEffect.WaterBreathing(true), "set_night", "set_survivor", cost = 2)
        nodes["set_survivor"] = SkillNode("set_survivor", "Survivor", "+4 HP, Cold Resist", Material.CAMPFIRE, SkillCategory.SETTLER, NodeTier.KEYSTONE, listOf("set_aqua"), SkillEffect.ColdResistance(true), 3)
        
        // XP/Utility Path
        nodes["set_xp1"] = node("set_xp1", "Learner I", "+3% XP", Material.EXPERIENCE_BOTTLE, SkillCategory.SETTLER, SkillEffect.XpBonus(1.03), "set_rest1", "set_xp2")
        nodes["set_xp2"] = node("set_xp2", "Learner II", "+3% XP", Material.EXPERIENCE_BOTTLE, SkillCategory.SETTLER, SkillEffect.XpBonus(1.03), "set_xp1", "set_xp3", cost = 2)
        nodes["set_xp3"] = node("set_xp3", "Scholar I", "+5% XP", Material.ENCHANTING_TABLE, SkillCategory.SETTLER, SkillEffect.XpBonus(1.05), "set_xp2", "set_soul1", cost = 2)
        nodes["set_soul1"] = node("set_soul1", "Soul Bind I", "Keep 1 item on death", Material.SOUL_LANTERN, SkillCategory.SETTLER, SkillEffect.SoulBinding(1), "set_xp3", "set_soul2", cost = 2)
        nodes["set_soul2"] = node("set_soul2", "Soul Bind II", "Keep 2 items", Material.SOUL_LANTERN, SkillCategory.SETTLER, SkillEffect.SoulBinding(2), "set_soul1", "set_immortal", cost = 2)
        nodes["set_immortal"] = SkillNode("set_immortal", "Immortal Legacy", "Keep 4 items, +5% XP", Material.BEACON, SkillCategory.SETTLER, NodeTier.KEYSTONE, listOf("set_soul2"), SkillEffect.SoulBinding(4), 3)
        
        return nodes
    }
    
    // Helper to create nodes more concisely
    private fun node(id: String, name: String, desc: String, icon: Material, cat: SkillCategory, effect: SkillEffect, vararg connections: String, cost: Int = 1): SkillNode {
        return SkillNode(id, name, desc, icon, cat, if (cost >= 3) NodeTier.KEYSTONE else if (cost >= 2) NodeTier.NOTABLE else NodeTier.MINOR, connections.toList(), effect, cost)
    }

    
    /**
     * EXPANDED Grid Layout - 15x15 with Origin at center [7][7]
     * 106 nodes total - each branch radiates outward
     */
    private fun buildGridLayout(): Array<Array<String?>> {
        val grid = Array(GRID_SIZE) { arrayOfNulls<String>(GRID_SIZE) }
        
        // Origin (Center)
        grid[7][7] = "origin"
        
        // ═══ VANGUARD (Top-Left) - Health/Armor/Regen paths ═══
        // Health path (diagonal up-left)
        grid[6][6] = "van_hp1"; grid[5][5] = "van_hp2"; grid[4][4] = "van_hp3"
        grid[3][3] = "van_hp4"; grid[2][2] = "van_hp5"; grid[1][1] = "van_hp6"; grid[0][0] = "van_colossus"
        // Armor path (horizontal left from hp1)
        grid[6][5] = "van_arm1"; grid[6][4] = "van_arm2"; grid[5][3] = "van_arm3"; grid[4][2] = "van_arm4"; grid[3][1] = "van_fortress"
        // Thorns/Regen path (branch from arm1)
        grid[5][4] = "van_thorn1"; grid[4][3] = "van_thorn2"; grid[3][2] = "van_thorn3"
        grid[2][3] = "van_regen1"; grid[1][4] = "van_regen2"; grid[0][5] = "van_undying"
        
        // ═══ BERSERKER (Top-Right) - Damage/Crit/Leech paths ═══
        // Damage path (diagonal up-right)
        grid[8][6] = "ber_dmg1"; grid[9][5] = "ber_dmg2"; grid[10][4] = "ber_dmg3"
        grid[11][3] = "ber_dmg4"; grid[12][2] = "ber_dmg5"; grid[13][1] = "ber_dmg6"; grid[14][0] = "ber_execute"
        // Crit path (branch from dmg1)
        grid[8][5] = "ber_crit1"; grid[9][4] = "ber_crit2"; grid[10][3] = "ber_crit3"
        grid[11][2] = "ber_critdmg1"; grid[12][1] = "ber_critdmg2"; grid[13][0] = "ber_assassin"
        // Leech path (branch from crit1)
        grid[9][6] = "ber_leech1"; grid[10][6] = "ber_leech2"; grid[11][5] = "ber_leech3"; grid[12][4] = "ber_rage"
        
        // ═══ SCOUT (Right) - Speed/Dodge/Stealth paths ═══
        // Speed path (horizontal right)
        grid[8][7] = "sco_spd1"; grid[9][7] = "sco_spd2"; grid[10][7] = "sco_spd3"
        grid[11][7] = "sco_spd4"; grid[12][7] = "sco_spd5"; grid[13][7] = "sco_spd6"; grid[14][7] = "sco_djump"
        // Dodge path (branch down from spd1)
        grid[8][8] = "sco_dodge1"; grid[9][8] = "sco_dodge2"; grid[10][8] = "sco_dodge3"; grid[11][8] = "sco_nofall"; grid[12][8] = "sco_acrobat"
        // Stealth path (branch from dodge1)
        grid[9][9] = "sco_sneak1"; grid[10][9] = "sco_sneak2"; grid[11][9] = "sco_sneak3"; grid[12][9] = "sco_sneak4"; grid[13][9] = "sco_ninja"
        
        // ═══ ARTISAN (Bottom-Left) - Haste/Luck/Utility paths ═══
        // Haste path (diagonal down-left)
        grid[6][8] = "art_haste1"; grid[5][9] = "art_haste2"; grid[4][10] = "art_haste3"
        grid[3][11] = "art_haste4"; grid[2][12] = "art_haste5"; grid[1][13] = "art_haste6"; grid[0][14] = "art_vein"
        // Luck path (branch from haste1)
        grid[6][9] = "art_luck1"; grid[5][10] = "art_luck2"; grid[4][11] = "art_luck3"; grid[3][12] = "art_luck4"; grid[2][13] = "art_prospector"
        // Timber/Utility path (branch from luck1)
        grid[5][8] = "art_timber1"; grid[4][9] = "art_timber2"; grid[3][10] = "art_magnet1"; grid[2][11] = "art_magnet2"; grid[1][12] = "art_autosmelt"
        
        // ═══ SETTLER (Bottom-Right) - Economy/Rest/XP paths ═══
        // Economy path (diagonal down-right)
        grid[8][8] = "set_eco1"; grid[9][9] = "set_eco2"; grid[10][10] = "set_eco3"
        grid[11][11] = "set_eco4"; grid[12][12] = "set_bounty1"; grid[13][13] = "set_bounty2"; grid[14][14] = "set_tycoon"
        // Rest path (branch from eco1)
        grid[8][9] = "set_rest1"; grid[9][10] = "set_rest2"; grid[10][11] = "set_rest3"
        grid[11][12] = "set_night"; grid[12][13] = "set_aqua"; grid[13][14] = "set_survivor"
        // XP path (branch from rest1)
        grid[8][10] = "set_xp1"; grid[9][11] = "set_xp2"; grid[10][12] = "set_xp3"
        grid[11][13] = "set_soul1"; grid[12][14] = "set_soul2"; grid[13][12] = "set_immortal"
        
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
        
        // Has prerequisite? (origin is always unlockable)
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
        
        var healthBonus = 0.0
        var speedBonus = 0.0f
        var armorBonus = 0.0
        
        unlocked.forEach { nodeId ->
            when (val effect = skillTree[nodeId]?.effect) {
                is SkillEffect.MaxHealth -> healthBonus += effect.bonus
                is SkillEffect.MovementSpeed -> speedBonus += effect.bonus
                is SkillEffect.Armor -> armorBonus += effect.bonus
                is SkillEffect.Poise -> healthBonus += 10.0 // Colossus also gives HP
                else -> {} // Other effects handled in events/helpers
            }
        }
        
        // Apply Attribute Bonuses
        player.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.baseValue = 20.0 + healthBonus
        player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED)?.baseValue = 0.1 + speedBonus
        player.getAttribute(Attribute.GENERIC_ARMOR)?.baseValue = armorBonus
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
                is SkillEffect.LuckBonus -> {
                    player.addPotionEffect(PotionEffect(PotionEffectType.LUCK, 140, effect.level, false, false, true))
                }
                else -> {}
            }
        }
    }
    
    // ══════════════════════════════════════════════════════════════
    // HELPER METHODS FOR OTHER MANAGERS (Combat, Mining, etc.)
    // ══════════════════════════════════════════════════════════════
    
    // Combat
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
            .sumOf { it.percent }.coerceAtMost(0.50) // Cap at 50%
    }
    
    fun getCritMultiplier(player: Player): Double {
        return getUnlockedNodes(player).mapNotNull { skillTree[it]?.effect as? SkillEffect.CritMultiplier }
            .fold(1.5) { acc, e -> acc + (e.bonus - 1.0) }.coerceAtMost(2.5) // Cap at 2.5x
    }
    
    fun getLifeLeechPercent(player: Player): Double {
        return getUnlockedNodes(player).mapNotNull { skillTree[it]?.effect as? SkillEffect.LifeLeech }
            .sumOf { it.percent }.coerceAtMost(0.10) // Cap at 10%
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
            .sumOf { it.percent }.coerceAtMost(0.25) // Cap at 25%
    }
    
    fun getSiegeDamageMultiplier(player: Player): Double {
        return getUnlockedNodes(player).mapNotNull { skillTree[it]?.effect as? SkillEffect.SiegeDefender }
            .fold(1.0) { acc, e -> acc * e.damageMultiplier }
    }
    
    // Defense
    fun getDodgeChance(player: Player): Double {
        return getUnlockedNodes(player).mapNotNull { skillTree[it]?.effect as? SkillEffect.DodgeChance }
            .sumOf { it.percent }.coerceAtMost(0.30) // Cap at 30%
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
    
    // Mining
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
            .sumOf { it.chance }.coerceAtMost(0.50) // Cap at 50%
    }
    
    fun getMagnetRange(player: Player): Double {
        return getUnlockedNodes(player).mapNotNull { skillTree[it]?.effect as? SkillEffect.Magnetism }
            .maxOfOrNull { it.range } ?: 0.0
    }
    
    // Economy/Settler
    fun getRestMultiplier(player: Player): Double {
        return getUnlockedNodes(player).mapNotNull { skillTree[it]?.effect as? SkillEffect.RestfulSleep }
            .fold(1.0) { acc, e -> acc * e.multiplier }
    }
    
    fun getTradeDiscount(player: Player): Double {
        return getUnlockedNodes(player).mapNotNull { skillTree[it]?.effect as? SkillEffect.Diplomacy }
            .sumOf { it.discountPercent }.coerceAtMost(0.20) // Cap at 20%
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
    
    fun applyAllSkillEffects(player: Player) {
        applySkillEffects(player)
    }
    
    fun getSwimSpeedLevel(player: Player): Int {
        return getUnlockedNodes(player).mapNotNull { skillTree[it]?.effect as? SkillEffect.SwimSpeed }
            .maxOfOrNull { it.level } ?: -1
    }
    
    fun hasWaterBreathing(player: Player): Boolean {
        return getUnlockedNodes(player).any { skillTree[it]?.effect is SkillEffect.WaterBreathing } ||
               getSwimSpeedLevel(player) >= 0
    }
    
    // ══════════════════════════════════════════════════════════════
    // GUI - POLISHED Skill Tree Display
    // ══════════════════════════════════════════════════════════════
    
    fun openSkillTree(player: Player) {
        val inv = Bukkit.createInventory(null, 54, Component.text("✦ SKILL TREE ✦", NamedTextColor.GOLD, TextDecoration.BOLD))
        
        val viewport = viewportPositions.getOrPut(player.uniqueId) { 7 to 7 }
        val unlocked = getUnlockedNodes(player)
        val availablePoints = getAvailableSkillPoints(player)
        val profile = plugin.identityManager.getPlayer(player.uniqueId)
        
        // Fill with category-colored gradient background
        for (row in 0 until VIEWPORT_HEIGHT) {
            for (col in 0 until VIEWPORT_WIDTH) {
                val gridX = viewport.first - 4 + col
                val gridY = viewport.second - 2 + row
                
                // Determine background color based on position
                val bgMaterial = getBackgroundMaterial(gridX, gridY)
                inv.setItem(row * 9 + col, ItemStack(bgMaterial).apply {
                    editMeta { it.displayName(Component.text(" ")) }
                })
            }
        }
        
        // Place nodes on top of backgrounds
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
        
        // ═══ ENHANCED CONTROL BAR (Bottom Row) ═══
        // Quick Jump to Branches
        inv.setItem(45, createBranchJumpItem(Material.BLUE_CONCRETE, "⚔ VANGUARD", NamedTextColor.BLUE, "Tank & Defense", 3, 3))
        inv.setItem(46, createBranchJumpItem(Material.RED_CONCRETE, "⚔ BERSERKER", NamedTextColor.RED, "Damage & Combat", 11, 3))
        inv.setItem(47, createBranchJumpItem(Material.CYAN_CONCRETE, "⚔ SCOUT", NamedTextColor.AQUA, "Speed & Stealth", 11, 7))
        inv.setItem(48, createBranchJumpItem(Material.ORANGE_CONCRETE, "⚔ ARTISAN", NamedTextColor.GOLD, "Mining & Gathering", 3, 11))
        inv.setItem(49, createBranchJumpItem(Material.LIME_CONCRETE, "⚔ SETTLER", NamedTextColor.GREEN, "Economy & Utility", 11, 11))
        
        // Center Button
        inv.setItem(50, ItemStack(Material.COMPASS).apply {
            editMeta { meta ->
                meta.displayName(Component.text("⌂ CENTER", NamedTextColor.YELLOW, TextDecoration.BOLD))
                meta.persistentDataContainer.set(menuKey, PersistentDataType.STRING, "nav:⌂ Center")
            }
        })
        
        // Stats
        val pointColor = if (availablePoints > 0) NamedTextColor.GOLD else NamedTextColor.GRAY
        inv.setItem(51, ItemStack(Material.NETHER_STAR).apply {
            editMeta { meta ->
                meta.displayName(Component.text("✦ ${availablePoints} Skill Points", pointColor, TextDecoration.BOLD))
                meta.lore(listOf(
                    Component.text("Level ${profile?.level ?: 1}", NamedTextColor.GREEN),
                    Component.text("Unlocked: ${unlocked.size}/${skillTree.size}", NamedTextColor.GRAY)
                ))
            }
        })
        
        // Navigation Arrows
        inv.setItem(52, createNavItem(Material.ARROW, "◀▲▼▶ Navigate", NamedTextColor.WHITE))
        
        // RESET BUTTON
        inv.setItem(53, ItemStack(Material.TNT).apply {
            editMeta { meta ->
                meta.displayName(Component.text("⚠ RESET SKILLS", NamedTextColor.RED, TextDecoration.BOLD))
                meta.lore(listOf(
                    Component.text("Click to refund all points!", NamedTextColor.GRAY),
                    Component.text("You will lose all effects.", NamedTextColor.RED)
                ))
                meta.persistentDataContainer.set(menuKey, PersistentDataType.STRING, "reset:init")
            }
        })
        
        player.openInventory(inv)
    }
    
    private fun openResetConfirmation(player: Player) {
        val inv = Bukkit.createInventory(null, 9, Component.text("⚠ CONFIRM RESET ⚠", NamedTextColor.RED, TextDecoration.BOLD))
        
        val cancelItem = ItemStack(Material.RED_STAINED_GLASS_PANE).apply {
            editMeta {
                it.displayName(Component.text("CANCEL", NamedTextColor.RED))
                it.persistentDataContainer.set(menuKey, PersistentDataType.STRING, "reset:cancel")
            }
        }
        
        val confirmItem = ItemStack(Material.TNT).apply {
            editMeta {
                it.displayName(Component.text("⚠ CONFIRM RESET", NamedTextColor.DARK_RED, TextDecoration.BOLD))
                it.lore(listOf(Component.text("THIS CANNOT BE UNDONE!", NamedTextColor.RED)))
                it.persistentDataContainer.set(menuKey, PersistentDataType.STRING, "reset:confirm")
            }
        }
        
        for (i in 0..3) inv.setItem(i, cancelItem)
        inv.setItem(4, confirmItem)
        for (i in 5..8) inv.setItem(i, cancelItem)
        
        player.openInventory(inv)
    }
    
    fun resetSkills(player: Player) {
         
        val profile = plugin.identityManager.getPlayer(player.uniqueId) ?: return
        profile.unlockedSkillNodes = "" // Clear nodes
        plugin.identityManager.saveProfile(player.uniqueId)
        
        // Reset Attributes
        player.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.baseValue = 20.0
        player.health = 20.0
        player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED)?.baseValue = 0.1
        player.getAttribute(Attribute.GENERIC_ARMOR)?.baseValue = 0.0
        player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE)?.baseValue = 1.0
        // player.getAttribute(Attribute.GENERIC_LUCK)?.baseValue = 0.0
        // player.getAttribute(Attribute.GENERIC_SCALE)?.baseValue = 1.0
        // player.getAttribute(Attribute.GENERIC_JUMP_STRENGTH)?.baseValue = 0.42
        
        // Clear status effects
        player.activePotionEffects.forEach { player.removePotionEffect(it.type) }
        
        
        // Visuals
        player.playSound(player.location, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.2f)
        player.sendMessage(Component.text("Skill Tree Reset! Points refunded.", NamedTextColor.GREEN, TextDecoration.BOLD))
        
        openSkillTree(player)
    }
    
    // Get background glass color based on grid position (which branch area)
    private fun getBackgroundMaterial(x: Int, y: Int): Material {
        // Origin area
        if (x in 6..8 && y in 6..8) return Material.WHITE_STAINED_GLASS_PANE
        
        // Vanguard (top-left) - Blue
        if (x <= 6 && y <= 6) return Material.BLUE_STAINED_GLASS_PANE
        
        // Berserker (top-right) - Red
        if (x >= 8 && y <= 6) return Material.RED_STAINED_GLASS_PANE
        
        // Scout (right) - Cyan
        if (x >= 8 && y in 6..8) return Material.CYAN_STAINED_GLASS_PANE
        
        // Artisan (bottom-left) - Orange
        if (x <= 6 && y >= 8) return Material.ORANGE_STAINED_GLASS_PANE
        
        // Settler (bottom-right) - Lime
        if (x >= 8 && y >= 8) return Material.LIME_STAINED_GLASS_PANE
        
        // Default - gray for transition areas
        return Material.GRAY_STAINED_GLASS_PANE
    }
    
    // Create quick-jump button to a branch
    private fun createBranchJumpItem(material: Material, name: String, color: NamedTextColor, desc: String, targetX: Int, targetY: Int): ItemStack {
        return ItemStack(material).apply {
            editMeta { meta ->
                meta.displayName(Component.text(name, color, TextDecoration.BOLD))
                meta.lore(listOf(
                    Component.text(desc, NamedTextColor.GRAY),
                    Component.text("Click to jump!", NamedTextColor.YELLOW)
                ))
                meta.persistentDataContainer.set(menuKey, PersistentDataType.STRING, "jump:$targetX:$targetY")
            }
        }
    }

    
    private fun createNodeItem(node: SkillNode, unlocked: Boolean, canUnlock: Boolean, allUnlocked: Set<String>): ItemStack {
        val color = when {
            unlocked -> NamedTextColor.GREEN
            canUnlock -> NamedTextColor.YELLOW
            else -> NamedTextColor.DARK_GRAY
        }
        
        val material = when {
            unlocked -> node.icon
            canUnlock -> node.icon
            else -> Material.BARRIER
        }
        
        return ItemStack(material).apply {
            editMeta { meta ->
                meta.displayName(Component.text(node.name, color, TextDecoration.BOLD))
                
                val lore = mutableListOf<Component>()
                lore.add(Component.text(node.description, NamedTextColor.GRAY))
                lore.add(Component.empty())
                lore.add(Component.text("Category: ${node.category.name}", getCategoryColor(node.category)))
                lore.add(Component.text("Tier: ${node.tier.name}", NamedTextColor.WHITE))
                lore.add(Component.text("Cost: ${node.cost} point(s)", NamedTextColor.AQUA))
                
                if (node.exclusiveWith.isNotEmpty()) {
                    val exclusiveNames = node.exclusiveWith.mapNotNull { skillTree[it]?.name }
                    lore.add(Component.empty())
                    lore.add(Component.text("⚠ Cannot combine with:", NamedTextColor.RED))
                    exclusiveNames.forEach { name ->
                        lore.add(Component.text("  • $name", NamedTextColor.DARK_RED))
                    }
                }
                
                lore.add(Component.empty())
                when {
                    unlocked -> lore.add(Component.text("✔ UNLOCKED", NamedTextColor.GREEN))
                    canUnlock -> lore.add(Component.text("▶ CLICK TO UNLOCK", NamedTextColor.YELLOW))
                    else -> lore.add(Component.text("✖ Locked (need connected node)", NamedTextColor.RED))
                }
                
                meta.lore(lore)
                
                if (unlocked) {
                    meta.addEnchant(org.bukkit.enchantments.Enchantment.MENDING, 1, true)
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
                }
                
                // Mark as menu item
                meta.persistentDataContainer.set(menuKey, PersistentDataType.STRING, node.id)
            }
        }
    }
    
    private fun getCategoryColor(category: SkillCategory): NamedTextColor {
        return when (category) {
            SkillCategory.VANGUARD -> NamedTextColor.BLUE
            SkillCategory.BERSERKER -> NamedTextColor.RED
            SkillCategory.SCOUT -> NamedTextColor.AQUA
            SkillCategory.ARTISAN -> NamedTextColor.GOLD
            SkillCategory.SETTLER -> NamedTextColor.GREEN
            SkillCategory.CORE -> NamedTextColor.WHITE
            SkillCategory.KEYSTONE -> NamedTextColor.LIGHT_PURPLE
        }
    }
    
    private fun createNavItem(material: Material, name: String, color: NamedTextColor): ItemStack {
        return ItemStack(material).apply {
            editMeta { meta ->
                meta.displayName(Component.text(name, color))
                meta.persistentDataContainer.set(menuKey, PersistentDataType.STRING, "nav:$name")
            }
        }
    }
    
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val title = event.view.title()
        val isSkillTree = title == Component.text("✦ SKILL TREE ✦", NamedTextColor.GOLD, TextDecoration.BOLD)
        val isResetMenu = title == Component.text("⚠ CONFIRM RESET ⚠", NamedTextColor.RED, TextDecoration.BOLD)
        
        if (!isSkillTree && !isResetMenu) return
        
        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        val meta = event.currentItem?.itemMeta ?: return
        
        val action = meta.persistentDataContainer.get(menuKey, PersistentDataType.STRING) ?: return
        
        // Check for Reset Actions
        if (action.startsWith("reset:")) {
            when (action) {
                "reset:init" -> openResetConfirmation(player)
                "reset:confirm" -> resetSkills(player)
                "reset:cancel" -> openSkillTree(player)
            }
            return
        }
        
        // Handle Reset Confirmation Menu specifically (title check)
        if (title == Component.text("⚠ CONFIRM RESET ⚠", NamedTextColor.RED, TextDecoration.BOLD)) {
            event.isCancelled = true
            // The item interactions are handled by the action string above,
            // but this ensures we don't accidentally move items if persistent data fails.
            return
        }

        when {
            action.startsWith("nav:") -> {
                when (action) {
                    "nav:◀ West" -> scrollViewport(player, -1, 0)
                    "nav:▶ East" -> scrollViewport(player, 1, 0)
                    "nav:▲ North" -> scrollViewport(player, 0, -1)
                    "nav:▼ South" -> scrollViewport(player, 0, 1)
                    "nav:⌂ Center" -> {
                        viewportPositions[player.uniqueId] = 7 to 7
                        openSkillTree(player)
                    }
                    "nav:◀▲▼▶ Navigate" -> {
                        player.sendMessage(Component.text("Use the branch buttons below, or scroll with your mouse!", NamedTextColor.YELLOW))
                    }
                }
            }
            action.startsWith("jump:") -> {
                // Quick jump to branch: "jump:x:y"
                val parts = action.split(":")
                if (parts.size == 3) {
                    val x = parts[1].toIntOrNull() ?: 7
                    val y = parts[2].toIntOrNull() ?: 7
                    viewportPositions[player.uniqueId] = x to y
                    player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.5f, 1.2f)
                    openSkillTree(player)
                }
            }
            else -> {
                // Try to unlock node
                if (unlockNode(player, action)) {
                    openSkillTree(player) // Refresh
                } else {
                    player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
                }
            }
        }
    }
    
    private fun scrollViewport(player: Player, dx: Int, dy: Int) {
        val current = viewportPositions.getOrPut(player.uniqueId) { 7 to 7 }
        val newX = (current.first + dx).coerceIn(4, 10)
        val newY = (current.second + dy).coerceIn(4, 10)
        viewportPositions[player.uniqueId] = newX to newY
        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.5f, 1.0f)
        openSkillTree(player)
    }
    
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        // Apply skills when player joins
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            applySkillEffects(event.player)
        }, 20L)
    }
}
