package com.projectatlas.siege

import com.projectatlas.AtlasPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.persistence.PersistentDataType

/**
 * Siege Equipment - Craftable items for siege warfare
 * Includes siege banners (trigger sieges) and defensive items
 */
object SiegeEquipment {
    
    // ═══════════════════════════════════════════════════════════════
    // SIEGE BANNER TIERS
    // ═══════════════════════════════════════════════════════════════
    
    enum class SiegeBannerTier(
        val displayName: String,
        val mobMultiplier: Double,
        val rewardMultiplier: Double,
        val color: NamedTextColor,
        val addMiniBoss: Boolean
    ) {
        BASIC("Siege Banner", 1.0, 1.0, NamedTextColor.RED, false),
        WAR("War Banner", 1.5, 2.0, NamedTextColor.DARK_RED, false),
        CHAOS("Chaos Banner", 2.0, 3.0, NamedTextColor.DARK_PURPLE, true);
        
        companion object {
            const val BANNER_TIER_KEY = "siege_banner_tier"
        }
    }
    
    // ═══════════════════════════════════════════════════════════════
    // ITEM CREATION
    // ═══════════════════════════════════════════════════════════════
    
    fun createSiegeBanner(plugin: AtlasPlugin, tier: SiegeBannerTier = SiegeBannerTier.BASIC): ItemStack {
        val item = ItemStack(Material.RED_BANNER)
        item.editMeta { meta ->
            meta.displayName(
                Component.text("⚔ ${tier.displayName}", tier.color)
                    .decorate(TextDecoration.BOLD)
            )
            
            val lore = mutableListOf(
                Component.empty(),
                Component.text("Right-click in city territory", NamedTextColor.GRAY),
                Component.text("to trigger a siege!", NamedTextColor.GRAY),
                Component.empty()
            )
            
            when (tier) {
                SiegeBannerTier.BASIC -> {
                    lore.add(Component.text("Standard siege difficulty", NamedTextColor.WHITE))
                    lore.add(Component.text("Reward: 1x", NamedTextColor.GOLD))
                }
                SiegeBannerTier.WAR -> {
                    lore.add(Component.text("Enhanced Siege (+50% mobs)", NamedTextColor.RED))
                    lore.add(Component.text("Reward: 2x", NamedTextColor.GOLD))
                }
                SiegeBannerTier.CHAOS -> {
                    lore.add(Component.text("Extreme Siege (+100% mobs)", NamedTextColor.DARK_PURPLE))
                    lore.add(Component.text("Mini-boss per wave!", NamedTextColor.LIGHT_PURPLE))
                    lore.add(Component.text("Reward: 3x", NamedTextColor.GOLD))
                }
            }
            
            lore.add(Component.empty())
            lore.add(Component.text("Consumable", NamedTextColor.DARK_GRAY).decorate(TextDecoration.ITALIC))
            
            meta.lore(lore)
            
            // Store tier in persistent data
            val key = NamespacedKey(plugin, SiegeBannerTier.BANNER_TIER_KEY)
            meta.persistentDataContainer.set(key, PersistentDataType.STRING, tier.name)
        }
        return item
    }
    
    fun createBarricadeBlock(plugin: AtlasPlugin): ItemStack {
        val item = ItemStack(Material.OAK_FENCE)
        item.editMeta { meta ->
            meta.displayName(
                Component.text("🛡️ Siege Barricade", NamedTextColor.AQUA)
                    .decorate(TextDecoration.BOLD)
            )
            meta.lore(listOf(
                Component.empty(),
                Component.text("Place in city to slow siege mobs", NamedTextColor.GRAY),
                Component.text("Applies Slowness II to enemies", NamedTextColor.BLUE),
                Component.empty(),
                Component.text("Destroyed after 3 hits", NamedTextColor.RED)
            ))
            
            val key = NamespacedKey(plugin, "siege_barricade")
            meta.persistentDataContainer.set(key, PersistentDataType.BOOLEAN, true)
        }
        return item
    }
    
    fun createSpikeTrap(plugin: AtlasPlugin): ItemStack {
        val item = ItemStack(Material.IRON_TRAPDOOR)
        item.editMeta { meta ->
            meta.displayName(
                Component.text("⚡ Spike Trap", NamedTextColor.GOLD)
                    .decorate(TextDecoration.BOLD)
            )
            meta.lore(listOf(
                Component.empty(),
                Component.text("Place in city territory", NamedTextColor.GRAY),
                Component.text("Deals 4 damage/sec to siege mobs", NamedTextColor.RED),
                Component.empty(),
                Component.text("Lasts 60 seconds", NamedTextColor.YELLOW)
            ))
            
            val key = NamespacedKey(plugin, "spike_trap")
            meta.persistentDataContainer.set(key, PersistentDataType.BOOLEAN, true)
        }
        return item
    }
    
    fun createArrowTurret(plugin: AtlasPlugin): ItemStack {
        val item = ItemStack(Material.DISPENSER)
        item.editMeta { meta ->
            meta.displayName(
                Component.text("🏹 Arrow Turret", NamedTextColor.GREEN)
                    .decorate(TextDecoration.BOLD)
            )
            meta.lore(listOf(
                Component.empty(),
                Component.text("Place in city territory", NamedTextColor.GRAY),
                Component.text("Auto-fires at siege mobs", NamedTextColor.WHITE),
                Component.text("5 damage every 2 seconds", NamedTextColor.RED),
                Component.empty(),
                Component.text("Requires arrows in inventory", NamedTextColor.YELLOW),
                Component.text("Lasts until siege ends", NamedTextColor.GRAY)
            ))
            
            val key = NamespacedKey(plugin, "arrow_turret")
            meta.persistentDataContainer.set(key, PersistentDataType.BOOLEAN, true)
        }
        return item
    }
    
    // ═══════════════════════════════════════════════════════════════
    // RECIPE REGISTRATION
    // ═══════════════════════════════════════════════════════════════
    
    fun registerRecipes(plugin: AtlasPlugin) {
        // Basic Siege Banner: 3 Red Wool + 1 Stick + 1 Blaze Powder
        val basicBanner = createSiegeBanner(plugin, SiegeBannerTier.BASIC)
        val basicRecipe = ShapedRecipe(NamespacedKey(plugin, "siege_banner_basic"), basicBanner)
        basicRecipe.shape("WWW", " S ", " B ")
        basicRecipe.setIngredient('W', Material.RED_WOOL)
        basicRecipe.setIngredient('S', Material.STICK)
        basicRecipe.setIngredient('B', Material.BLAZE_POWDER)
        plugin.server.addRecipe(basicRecipe)
        
        // War Banner: Siege Banner + 3 Iron Blocks + 1 Ghast Tear
        val warBanner = createSiegeBanner(plugin, SiegeBannerTier.WAR)
        val warRecipe = ShapedRecipe(NamespacedKey(plugin, "siege_banner_war"), warBanner)
        warRecipe.shape("IBI", " G ", "   ")
        warRecipe.setIngredient('I', Material.IRON_BLOCK)
        warRecipe.setIngredient('B', Material.RED_BANNER)
        warRecipe.setIngredient('G', Material.GHAST_TEAR)
        plugin.server.addRecipe(warRecipe)
        
        // Chaos Banner: War Banner + Dragon Breath + 2 End Crystals
        val chaosBanner = createSiegeBanner(plugin, SiegeBannerTier.CHAOS)
        val chaosRecipe = ShapedRecipe(NamespacedKey(plugin, "siege_banner_chaos"), chaosBanner)
        chaosRecipe.shape("EBE", " D ", "   ")
        chaosRecipe.setIngredient('E', Material.END_CRYSTAL)
        chaosRecipe.setIngredient('B', Material.RED_BANNER)
        chaosRecipe.setIngredient('D', Material.DRAGON_BREATH)
        plugin.server.addRecipe(chaosRecipe)
        
        // Barricade Block: 4 Oak Fence + 4 Iron Nuggets + 1 Chain
        val barricade = createBarricadeBlock(plugin)
        val barricadeRecipe = ShapedRecipe(NamespacedKey(plugin, "siege_barricade"), barricade)
        barricadeRecipe.shape("NFN", "FCF", "NFN")
        barricadeRecipe.setIngredient('N', Material.IRON_NUGGET)
        barricadeRecipe.setIngredient('F', Material.OAK_FENCE)
        barricadeRecipe.setIngredient('C', Material.CHAIN)
        plugin.server.addRecipe(barricadeRecipe)
        
        // Spike Trap: 4 Iron Trapdoor + 4 Iron Sword + 1 Redstone
        val spikeTrap = createSpikeTrap(plugin)
        val spikeRecipe = ShapedRecipe(NamespacedKey(plugin, "spike_trap"), spikeTrap)
        spikeRecipe.shape("STS", "TRT", "STS")
        spikeRecipe.setIngredient('S', Material.IRON_SWORD)
        spikeRecipe.setIngredient('T', Material.IRON_TRAPDOOR)
        spikeRecipe.setIngredient('R', Material.REDSTONE)
        plugin.server.addRecipe(spikeRecipe)
        
        // Arrow Turret: 1 Dispenser + 4 Iron Block + 2 Bow + 1 Redstone Block + 1 Observer
        val arrowTurret = createArrowTurret(plugin)
        val turretRecipe = ShapedRecipe(NamespacedKey(plugin, "arrow_turret"), arrowTurret)
        turretRecipe.shape("BDB", "IRI", "IOI")
        turretRecipe.setIngredient('B', Material.BOW)
        turretRecipe.setIngredient('D', Material.DISPENSER)
        turretRecipe.setIngredient('I', Material.IRON_BLOCK)
        turretRecipe.setIngredient('R', Material.REDSTONE_BLOCK)
        turretRecipe.setIngredient('O', Material.OBSERVER)
        plugin.server.addRecipe(turretRecipe)
        
        plugin.logger.info("Registered 6 siege equipment recipes!")
    }
    
    // ═══════════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════════
    
    fun getSiegeBannerTier(plugin: AtlasPlugin, item: ItemStack): SiegeBannerTier? {
        if (item.type != Material.RED_BANNER) return null
        val meta = item.itemMeta ?: return null
        
        val key = NamespacedKey(plugin, SiegeBannerTier.BANNER_TIER_KEY)
        val tierName = meta.persistentDataContainer.get(key, PersistentDataType.STRING) ?: return null
        
        return try {
            SiegeBannerTier.valueOf(tierName)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
    
    fun isSiegeBarricade(plugin: AtlasPlugin, item: ItemStack): Boolean {
        val meta = item.itemMeta ?: return false
        val key = NamespacedKey(plugin, "siege_barricade")
        return meta.persistentDataContainer.has(key, PersistentDataType.BOOLEAN)
    }
    
    fun isSpikeTrap(plugin: AtlasPlugin, item: ItemStack): Boolean {
        val meta = item.itemMeta ?: return false
        val key = NamespacedKey(plugin, "spike_trap")
        return meta.persistentDataContainer.has(key, PersistentDataType.BOOLEAN)
    }
    
    fun isArrowTurret(plugin: AtlasPlugin, item: ItemStack): Boolean {
        val meta = item.itemMeta ?: return false
        val key = NamespacedKey(plugin, "arrow_turret")
        return meta.persistentDataContainer.has(key, PersistentDataType.BOOLEAN)
    }
}
