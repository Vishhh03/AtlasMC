package com.projectatlas.survival

import com.projectatlas.AtlasPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityRegainHealthEvent
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.bukkit.event.player.PlayerBedLeaveEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

/**
 * Hardcore Survival Manager
 * - Nerfs food healing
 * - Implements rest/sleep healing system
 * - Adds custom healing items
 */
class SurvivalManager(private val plugin: AtlasPlugin) : Listener {

    private val healingItemKey = NamespacedKey(plugin, "healing_item")
    
    // Bed quality ratings for healing
    enum class BedQuality(val healAmount: Double, val displayName: String) {
        BASIC(4.0, "Rough Rest"),      // Wool beds
        COMFORT(6.0, "Restful Sleep"), // Dyed beds
        LUXURY(10.0, "Rejuvenating Slumber") // Special beds (future)
    }
    
    // Custom healing item types
    enum class HealingItem(val healAmount: Double, val displayName: String, val material: Material) {
        BANDAGE(4.0, "Bandage", Material.PAPER),
        HEALING_SALVE(8.0, "Healing Salve", Material.HONEYCOMB),
        MEDICAL_KIT(14.0, "Medical Kit", Material.SHULKER_BOX),
        HERBAL_REMEDY(6.0, "Herbal Remedy", Material.FERN)
    }

    init {
        registerRecipes()
    }

    private fun registerRecipes() {
        // Bandage: 2 Paper, 1 Wool
        val bandageKey = NamespacedKey(plugin, "recipe_bandage")
        if (plugin.server.getRecipe(bandageKey) == null) {
            val recipe = org.bukkit.inventory.ShapelessRecipe(bandageKey, createHealingItem(HealingItem.BANDAGE))
            recipe.addIngredient(2, Material.PAPER)
            recipe.addIngredient(1, Material.WHITE_WOOL)
            plugin.server.addRecipe(recipe)
        }

        // Healing Salve: Honeycomb, Bowl, Sugar
        val salveKey = NamespacedKey(plugin, "recipe_salve")
        if (plugin.server.getRecipe(salveKey) == null) {
            val recipe = org.bukkit.inventory.ShapelessRecipe(salveKey, createHealingItem(HealingItem.HEALING_SALVE))
            recipe.addIngredient(1, Material.HONEYCOMB)
            recipe.addIngredient(1, Material.BOWL)
            recipe.addIngredient(1, Material.SUGAR)
            plugin.server.addRecipe(recipe)
        }

        // Herbal Remedy: Fern, Red Tulip, Dandelion
        val remedyKey = NamespacedKey(plugin, "recipe_remedy")
        if (plugin.server.getRecipe(remedyKey) == null) {
             val recipe = org.bukkit.inventory.ShapelessRecipe(remedyKey, createHealingItem(HealingItem.HERBAL_REMEDY))
             recipe.addIngredient(1, Material.FERN)
             recipe.addIngredient(1, Material.RED_TULIP)
             recipe.addIngredient(1, Material.DANDELION)
             plugin.server.addRecipe(recipe)
        }

        // Medical Kit: Complex
        val medkitKey = NamespacedKey(plugin, "recipe_medkit")
        if (plugin.server.getRecipe(medkitKey) == null) {
            val recipe = org.bukkit.inventory.ShapedRecipe(medkitKey, createHealingItem(HealingItem.MEDICAL_KIT))
            recipe.shape("PGP", "ICI", "PRP")
            recipe.setIngredient('P', Material.PAPER)
            recipe.setIngredient('G', Material.GOLDEN_APPLE)
            recipe.setIngredient('I', Material.IRON_INGOT)
            recipe.setIngredient('C', Material.CHEST)
            recipe.setIngredient('R', Material.RED_DYE)
            plugin.server.addRecipe(recipe)
        }
    }
    
    // ══════════════════════════════════════════════════════════════
    // NERF NATURAL HEALING
    // ══════════════════════════════════════════════════════════════
    
    @EventHandler
    fun onHealthRegen(event: EntityRegainHealthEvent) {
        if (event.entity !is Player) return
        
        // Nerf saturation-based healing (from full hunger bar)
        if (event.regainReason == EntityRegainHealthEvent.RegainReason.SATIATED) {
            // Reduce natural regen by 75% - makes food less OP for healing
            event.amount = event.amount * 0.25
        }
        
        // Nerf eating-based healing
        if (event.regainReason == EntityRegainHealthEvent.RegainReason.EATING) {
            event.amount = event.amount * 0.5
        }
    }
    
    @EventHandler
    fun onFoodChange(event: FoodLevelChangeEvent) {
        // Optional: Could reduce food restoration here too
        // For now, food still fills hunger but doesn't heal as much
    }
    
    // ══════════════════════════════════════════════════════════════
    // REST/SLEEP HEALING SYSTEM
    // ══════════════════════════════════════════════════════════════
    
    @EventHandler
    fun onBedLeave(event: PlayerBedLeaveEvent) {
        val player = event.player
        val bed = event.bed
        
        // Only heal if they actually slept (night skip)
        // Check if it's now daytime
        val world = player.world
        if (world.time > 1000) return // Didn't actually sleep through the night
        
        // Determine bed quality based on material
        val quality = when (bed.type) {
            Material.WHITE_BED -> BedQuality.BASIC
            Material.RED_BED, Material.BLUE_BED, Material.GREEN_BED,
            Material.YELLOW_BED, Material.ORANGE_BED, Material.PURPLE_BED,
            Material.PINK_BED, Material.CYAN_BED, Material.LIME_BED,
            Material.LIGHT_BLUE_BED, Material.MAGENTA_BED, Material.BROWN_BED,
            Material.GRAY_BED, Material.LIGHT_GRAY_BED -> BedQuality.COMFORT
            Material.BLACK_BED -> BedQuality.LUXURY // Black beds are "Luxury"
            else -> BedQuality.BASIC
        }
        
        // Apply healing
        val currentHealth = player.health
        val maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
        val newHealth = (currentHealth + quality.healAmount).coerceAtMost(maxHealth)
        
        player.health = newHealth
        
        // Clear some negative effects
        player.removePotionEffect(PotionEffectType.POISON)
        player.removePotionEffect(PotionEffectType.HUNGER)
        player.removePotionEffect(PotionEffectType.WEAKNESS)
        
        // Apply well-rested buff
        player.addPotionEffect(PotionEffect(PotionEffectType.REGENERATION, 200, 0, false, false)) // 10s regen
        
        // Notify
        player.sendMessage(Component.text("☽ ${quality.displayName} ☽", NamedTextColor.AQUA, TextDecoration.ITALIC))
        player.sendMessage(Component.text("  Restored ${quality.healAmount.toInt()} health", NamedTextColor.GREEN))
        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f)
    }
    
    // ══════════════════════════════════════════════════════════════
    // CUSTOM HEALING ITEMS
    // ══════════════════════════════════════════════════════════════
    
    fun createHealingItem(type: HealingItem, amount: Int = 1): ItemStack {
        val item = ItemStack(type.material, amount)
        val meta = item.itemMeta ?: return item
        
        meta.displayName(Component.text(type.displayName, NamedTextColor.RED, TextDecoration.BOLD))
        meta.lore(listOf(
            Component.text("Right-click to use", NamedTextColor.GRAY),
            Component.empty(),
            Component.text("Restores ${type.healAmount.toInt()} HP", NamedTextColor.GREEN),
            Component.empty(),
            Component.text("Healing Item", NamedTextColor.DARK_PURPLE, TextDecoration.ITALIC)
        ))
        
        meta.persistentDataContainer.set(healingItemKey, PersistentDataType.STRING, type.name)
        
        item.itemMeta = meta
        return item
    }
    
    @EventHandler
    fun onItemUse(event: PlayerInteractEvent) {
        val item = event.item ?: return
        val meta = item.itemMeta ?: return
        
        val healingType = meta.persistentDataContainer.get(healingItemKey, PersistentDataType.STRING) ?: return
        
        // It's a healing item!
        event.isCancelled = true
        
        val player = event.player
        val type = try { HealingItem.valueOf(healingType) } catch (e: Exception) { return }
        
        val currentHealth = player.health
        val maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
        
        // Already at full health?
        if (currentHealth >= maxHealth) {
            player.sendMessage(Component.text("You're already at full health!", NamedTextColor.YELLOW))
            return
        }
        
        // Apply healing
        val newHealth = (currentHealth + type.healAmount).coerceAtMost(maxHealth)
        player.health = newHealth
        
        // Consume item
        if (item.amount > 1) {
            item.amount = item.amount - 1
        } else {
            player.inventory.setItemInMainHand(ItemStack(Material.AIR))
        }
        
        // Effects
        player.sendMessage(Component.text("✚ Used ${type.displayName} (+${type.healAmount.toInt()} HP)", NamedTextColor.GREEN))
        player.playSound(player.location, Sound.ENTITY_GENERIC_DRINK, 1f, 1f)
        
        // Healing salve and medical kit also clear debuffs
        if (type == HealingItem.HEALING_SALVE || type == HealingItem.MEDICAL_KIT) {
            player.removePotionEffect(PotionEffectType.POISON)
            player.removePotionEffect(PotionEffectType.WITHER)
            player.sendMessage(Component.text("  Negative effects cleared!", NamedTextColor.AQUA))
        }
    }
    
    // ══════════════════════════════════════════════════════════════
    // GIVE COMMANDS (for /atlas survival)
    // ══════════════════════════════════════════════════════════════
    
    fun giveHealingItem(player: Player, typeName: String, amount: Int = 1): Boolean {
        val type = try { 
            HealingItem.valueOf(typeName.uppercase()) 
        } catch (e: Exception) { 
            player.sendMessage(Component.text("Unknown healing item: $typeName", NamedTextColor.RED))
            player.sendMessage(Component.text("Available: ${HealingItem.entries.joinToString { it.name }}", NamedTextColor.GRAY))
            return false 
        }
        
        val item = createHealingItem(type, amount)
        player.inventory.addItem(item)
        player.sendMessage(Component.text("Received $amount x ${type.displayName}", NamedTextColor.GREEN))
        return true
    }
}
