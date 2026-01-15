package com.projectatlas.visual

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack

/**
 * Custom Item Manager - Creates items with CustomModelData for resource pack visuals.
 * 
 * These items use vanilla materials but display custom 3D models when the
 * resource pack is loaded. No client mods required!
 */
object CustomItemManager {

    // ═══════════════════════════════════════════════════════════════
    // CUSTOM MODEL DATA REGISTRY
    // ═══════════════════════════════════════════════════════════════

    object ModelData {
        // Era 0 Items (1000-1999)
        const val HOLLOW_KNIGHT_BLADE = 1001
        const val AWAKENING_MEDAL = 1002
        const val SURVIVAL_KIT = 1003

        // Era 1 Items (2000-2999)
        const val TAX_COLLECTOR_AXE = 2001
        const val SETTLER_BADGE = 2002
        const val CITY_CHARTER = 2003
        const val TRADE_PERMIT = 2004

        // Era 2 Items (3000-3999)
        const val WARDEN_FLAME_SWORD = 3001
        const val EXPLORER_COMPASS = 3002
        const val DUNGEON_KEY = 3003
        const val NETHER_PASS = 3004

        // Era 3 Items (4000-4999)
        const val ENDER_SENTINEL_SCYTHE = 4001
        const val ASCENDANT_CROWN = 4002
        const val END_GATEWAY_KEY = 4003
        const val ALLIANCE_SEAL = 4004

        // Era 4 Items (5000-5999)
        const val DRAGON_SLAYER = 5001
        const val LEGEND_CROWN = 5002
        const val WORLD_SHARD = 5003

        // Relics (9000-9099) - Already defined in RelicManager
        const val PHOENIX_FEATHER = 9000
        const val VOID_SHARD = 9001
        const val TITANS_GRIP = 9002
        const val STORM_CALLER = 9003
        const val FROST_HEART = 9004
        const val SHADOW_CLOAK = 9005
        const val SUNFIRE_AMULET = 9006
        const val NATURES_BLESSING = 9007

        // GUI Icons (10000-10999)
        const val GUI_LOCKED = 10001
        const val GUI_UNLOCKED = 10002
        const val GUI_ARROW_RIGHT = 10003
        const val GUI_ARROW_LEFT = 10004
        const val GUI_GOLD_COIN = 10005
        const val GUI_XP_ORB = 10006
        const val GUI_SKILL_POINT = 10007

        // City Items (11000-11999)
        const val CITY_CORE = 11001
        const val CITY_WALL_PIECE = 11002
        const val CITY_TURRET = 11003
        const val CITY_BANNER = 11004
    }

    // ═══════════════════════════════════════════════════════════════
    // ERA BOSS DROPS
    // ═══════════════════════════════════════════════════════════════

    fun createHollowKnightBlade(): ItemStack {
        return createCustomWeapon(
            material = Material.NETHERITE_SWORD,
            modelData = ModelData.HOLLOW_KNIGHT_BLADE,
            name = "Hollow Knight's Blade",
            nameColor = NamedTextColor.GRAY,
            lore = listOf(
                Component.text("Forged in darkness", NamedTextColor.DARK_GRAY),
                Component.empty(),
                Component.text("Era 0 Boss Drop", NamedTextColor.GRAY)
            ),
            damage = 9.0,
            attackSpeed = 1.6
        )
    }

    fun createTaxCollectorAxe(): ItemStack {
        return createCustomWeapon(
            material = Material.IRON_AXE,
            modelData = ModelData.TAX_COLLECTOR_AXE,
            name = "Tax Collector's Axe",
            nameColor = NamedTextColor.GREEN,
            lore = listOf(
                Component.text("\"Your gold or your head!\"", NamedTextColor.DARK_GREEN),
                Component.empty(),
                Component.text("Era 1 Boss Drop", NamedTextColor.GRAY),
                Component.text("+5% Gold from Quests", NamedTextColor.GOLD)
            ),
            damage = 11.0,
            attackSpeed = 0.9
        )
    }

    fun createWardenFlameSword(): ItemStack {
        return createCustomWeapon(
            material = Material.NETHERITE_SWORD,
            modelData = ModelData.WARDEN_FLAME_SWORD,
            name = "Warden's Flame",
            nameColor = NamedTextColor.RED,
            lore = listOf(
                Component.text("Burns with eternal fire", NamedTextColor.GOLD),
                Component.empty(),
                Component.text("Era 2 Boss Drop", NamedTextColor.GRAY),
                Component.text("Fire Aspect II", NamedTextColor.RED)
            ),
            damage = 10.0,
            attackSpeed = 1.6,
            enchantments = mapOf(Enchantment.FIRE_ASPECT to 2)
        )
    }

    fun createEnderSentinelScythe(): ItemStack {
        return createCustomWeapon(
            material = Material.NETHERITE_SWORD,
            modelData = ModelData.ENDER_SENTINEL_SCYTHE,
            name = "Ender Sentinel's Scythe",
            nameColor = NamedTextColor.DARK_PURPLE,
            lore = listOf(
                Component.text("Cuts through dimensions", NamedTextColor.LIGHT_PURPLE),
                Component.empty(),
                Component.text("Era 3 Boss Drop", NamedTextColor.GRAY),
                Component.text("Sweeping Edge III", NamedTextColor.AQUA)
            ),
            damage = 11.0,
            attackSpeed = 1.4,
            enchantments = mapOf(Enchantment.SWEEPING_EDGE to 3)
        )
    }

    fun createDragonSlayer(): ItemStack {
        return createCustomWeapon(
            material = Material.NETHERITE_SWORD,
            modelData = ModelData.DRAGON_SLAYER,
            name = "Dragon Slayer",
            nameColor = NamedTextColor.GOLD,
            lore = listOf(
                Component.text("The legendary blade", NamedTextColor.YELLOW),
                Component.text("used to fell the Ender Dragon", NamedTextColor.YELLOW),
                Component.empty(),
                Component.text("Era 4 Legendary", NamedTextColor.GOLD),
                Component.text("Sharpness V", NamedTextColor.RED),
                Component.text("Unbreaking III", NamedTextColor.AQUA)
            ),
            damage = 12.0,
            attackSpeed = 1.6,
            enchantments = mapOf(
                Enchantment.SHARPNESS to 5,
                Enchantment.UNBREAKING to 3
            )
        )
    }

    // ═══════════════════════════════════════════════════════════════
    // ERA KEY FRAGMENTS
    // ═══════════════════════════════════════════════════════════════

    fun createEraKeyFragment(era: Int): ItemStack {
        val (name, color, modelData) = when (era) {
            0 -> Triple("Hollow Key Fragment", NamedTextColor.GRAY, ModelData.AWAKENING_MEDAL)
            1 -> Triple("Settler's Key Fragment", NamedTextColor.GREEN, ModelData.SETTLER_BADGE)
            2 -> Triple("Explorer's Key Fragment", NamedTextColor.RED, ModelData.EXPLORER_COMPASS)
            3 -> Triple("Ascendant Key Fragment", NamedTextColor.LIGHT_PURPLE, ModelData.ASCENDANT_CROWN)
            else -> Triple("Legend's Key Fragment", NamedTextColor.GOLD, ModelData.LEGEND_CROWN)
        }

        return ItemStack(Material.GOLD_NUGGET).apply {
            editMeta { meta ->
                meta.displayName(Component.text(name, color, TextDecoration.BOLD))
                meta.lore(listOf(
                    Component.text("Proof of defeating the Era $era Boss", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("A symbol of your city's triumph", NamedTextColor.YELLOW)
                ))
                meta.setCustomModelData(modelData)
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // GUI ITEMS
    // ═══════════════════════════════════════════════════════════════

    fun createGuiLocked(): ItemStack {
        return ItemStack(Material.BARRIER).apply {
            editMeta { meta ->
                meta.displayName(Component.text("LOCKED", NamedTextColor.RED, TextDecoration.BOLD))
                meta.setCustomModelData(ModelData.GUI_LOCKED)
            }
        }
    }

    fun createGuiUnlocked(): ItemStack {
        return ItemStack(Material.LIME_DYE).apply {
            editMeta { meta ->
                meta.displayName(Component.text("UNLOCKED", NamedTextColor.GREEN, TextDecoration.BOLD))
                meta.setCustomModelData(ModelData.GUI_UNLOCKED)
            }
        }
    }

    fun createGuiGoldCoin(amount: Int = 1): ItemStack {
        return ItemStack(Material.GOLD_NUGGET, amount.coerceIn(1, 64)).apply {
            editMeta { meta ->
                meta.displayName(Component.text("Gold", NamedTextColor.GOLD))
                meta.setCustomModelData(ModelData.GUI_GOLD_COIN)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════

    private fun createCustomWeapon(
        material: Material,
        modelData: Int,
        name: String,
        nameColor: NamedTextColor,
        lore: List<Component>,
        damage: Double = 7.0,
        attackSpeed: Double = 1.6,
        enchantments: Map<Enchantment, Int> = emptyMap()
    ): ItemStack {
        return ItemStack(material).apply {
            editMeta { meta ->
                meta.displayName(Component.text(name, nameColor, TextDecoration.BOLD))
                meta.lore(lore)
                meta.setCustomModelData(modelData)
                meta.isUnbreakable = true
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE)
                
                // Apply enchantments
                enchantments.forEach { (enchant, level) ->
                    meta.addEnchant(enchant, level, true)
                }
                if (enchantments.isNotEmpty()) {
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
                }
            }
        }
    }

    /**
     * Create a generic custom item.
     */
    fun createCustomItem(
        material: Material,
        modelData: Int,
        name: String,
        nameColor: NamedTextColor = NamedTextColor.WHITE,
        lore: List<Component> = emptyList(),
        glowing: Boolean = false
    ): ItemStack {
        return ItemStack(material).apply {
            editMeta { meta ->
                meta.displayName(Component.text(name, nameColor, TextDecoration.BOLD))
                if (lore.isNotEmpty()) meta.lore(lore)
                meta.setCustomModelData(modelData)
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
                
                if (glowing) {
                    meta.addEnchant(Enchantment.UNBREAKING, 1, true)
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
                }
            }
        }
    }

    /**
     * Check if an item is a custom Atlas item.
     */
    fun isCustomItem(item: ItemStack?): Boolean {
        if (item == null || !item.hasItemMeta()) return false
        val meta = item.itemMeta ?: return false
        return meta.hasCustomModelData() && meta.customModelData >= 1000
    }

    /**
     * Get the custom model data from an item.
     */
    fun getModelData(item: ItemStack?): Int {
        if (item == null || !item.hasItemMeta()) return 0
        val meta = item.itemMeta ?: return 0
        return if (meta.hasCustomModelData()) meta.customModelData else 0
    }
}
