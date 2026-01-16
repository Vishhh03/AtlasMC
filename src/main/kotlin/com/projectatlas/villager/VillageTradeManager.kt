

package com.projectatlas.villager

import com.projectatlas.AtlasPlugin
import com.projectatlas.city.City
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.entity.Villager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.MerchantRecipe
import org.bukkit.event.entity.VillagerAcquireTradeEvent
import java.util.ArrayList

class VillageTradeManager(private val plugin: AtlasPlugin) : Listener {

    @EventHandler
    fun onVillagerInteract(event: PlayerInteractEntityEvent) {
        val villager = event.rightClicked as? Villager ?: return
        val player = event.player
        
        // RECRUITMENT MECHANIC (Shift + Click)
        if (player.isSneaking) {
            event.isCancelled = true
            handleRecruitment(player, villager)
            return
        }
        
        // CITY REQUIREMENT - REMOVED for better UX (Wild villagers can trade)
        // val city = plugin.cityManager.getCityAt(villager.location.chunk)
        // if (city == null && !player.isOp) { ... }
        
        // RESTOCK LOGIC
        val lastRestockKey = org.bukkit.NamespacedKey(plugin, "atlas_last_restock")
        val container = villager.persistentDataContainer
        val lastRestock = container.get(lastRestockKey, org.bukkit.persistence.PersistentDataType.LONG) ?: 0L
        
        // Restock every 10 minutes
        if (System.currentTimeMillis() - lastRestock > 10 * 60 * 1000) {
            updateVillagerTrades(villager)
            container.set(lastRestockKey, org.bukkit.persistence.PersistentDataType.LONG, System.currentTimeMillis())
            // Visual feedback
            villager.world.playSound(villager.location, Sound.ENTITY_VILLAGER_WORK_FARMER, 1.0f, 1.0f)
        }
        
        if (shouldUpdateTrades(villager)) {
            updateVillagerTrades(villager)
        }
    }

    private fun handleRecruitment(player: Player, villager: Villager) {
        val profile = plugin.identityManager.getPlayer(player.uniqueId) ?: return
        if (profile.cityId == null) {
            player.sendMessage(Component.text("You must belong to a City to recruit citizens.", NamedTextColor.RED))
            return
        }
        
        val city = plugin.cityManager.getCity(profile.cityId!!) ?: return
        
        // Check if player has permission/Mayor
        // For now, any member can recruit if they pay the fee? Or just Mayor?
        // Let's strictly allow Mayors/Officers for now.
        if (city.mayor != player.uniqueId) {
             player.sendMessage(Component.text("Only the Mayor can recruit villagers.", NamedTextColor.RED))
             return
        }
        
        // Bribe: Requires 1 Emerald
        if (!player.inventory.contains(Material.EMERALD)) {
            player.sendMessage(Component.text("You need an Emerald to convince them to join!", NamedTextColor.GREEN))
            return
        }
        
        // Cost to Recruit
        val cost = 250.0
        if (plugin.economyManager.withdraw(player.uniqueId, cost)) {
            // Consume Emerald
            player.inventory.removeItem(ItemStack(Material.EMERALD, 1))
            
            // Teleport Villager to City Control Point (or Player's location if in city)
            val targetLoc = city.getControlPoint() ?: player.location
            
            villager.teleport(targetLoc)
            villager.customName(Component.text("Citizen of ${city.name}", NamedTextColor.AQUA))
            villager.isCustomNameVisible = true
            
            // Effect
            player.sendMessage(Component.text("Recruited villager to ${city.name}!", NamedTextColor.GREEN))
            player.playSound(player.location, Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f)
            villager.world.spawnParticle(org.bukkit.Particle.PORTAL, villager.location, 20)
        } else {
            player.sendMessage(Component.text("Recruitment costs ${cost}g.", NamedTextColor.RED))
        }
    }

    // -------------------------------------------------------------------------
    // TRADE OVERHAUL
    // -------------------------------------------------------------------------
    
    // Tag to mark villager as "Atlas Updated"
    private val updatedTag = "atlas_trades_updated"

    private fun shouldUpdateTrades(villager: Villager): Boolean {
        return !villager.scoreboardTags.contains(updatedTag)
    }

    private fun updateVillagerTrades(villager: Villager) {
        val currentRecipes = villager.recipes
        val modifiedRecipes = ArrayList<MerchantRecipe>()
        
        for (recipe in currentRecipes) {
            modifiedRecipes.add(convertRecipe(recipe))
        }
        
        // Inject Healing Item (30% chance per villager)
        // Only if not already present (check by logic or tag? checking item type is enough for now)
        if (Math.random() < 0.3) {
             val healingTrade = createHealingTrade()
             if (healingTrade != null) modifiedRecipes.add(healingTrade)
        }
        
        villager.recipes = modifiedRecipes
        villager.addScoreboardTag(updatedTag)
    }
    
    @EventHandler
    fun onTradeAcquire(event: VillagerAcquireTradeEvent) {
        // Intercept new trades being generated and convert them
        event.recipe = convertRecipe(event.recipe)
    }

    private fun convertRecipe(recipe: MerchantRecipe): MerchantRecipe {
        val newResult = convertItem(recipe.result)
        val newRecipe = MerchantRecipe(newResult, recipe.uses, recipe.maxUses, recipe.hasExperienceReward(), recipe.villagerExperience, recipe.priceMultiplier)
        
        recipe.ingredients.forEach { ingredient ->
            newRecipe.addIngredient(convertItem(ingredient))
        }
        return newRecipe
    }

    private fun convertItem(item: ItemStack): ItemStack {
        if (item.type == Material.EMERALD) {
            // CRITICAL ECONOMY CHANGE: 1 Emerald = 5 Gold Nuggets
            val amount = (item.amount * 5).coerceAtMost(64)
            return ItemStack(Material.GOLD_NUGGET, amount)
        }
        return item
    }

    private fun createHealingTrade(): MerchantRecipe? {
        // Randomly pick a Tier 1 or 2 healing item
        val itemType = if (Math.random() < 0.6) {
            com.projectatlas.survival.SurvivalManager.HealingItem.BANDAGE
        } else {
            com.projectatlas.survival.SurvivalManager.HealingItem.HERBAL_POULTICE
        }
        
        val healingItem = plugin.survivalManager.createHealingItem(itemType, 1)
        val price = if (itemType == com.projectatlas.survival.SurvivalManager.HealingItem.BANDAGE) 8 else 12 // Nuggets
        
        val recipe = MerchantRecipe(healingItem, 10) // 10 uses max
        recipe.addIngredient(ItemStack(Material.GOLD_NUGGET, price))
        return recipe
    }
    
    private fun createTrade(input: ItemStack, result: ItemStack, maxUses: Int = 3): MerchantRecipe {
        val recipe = MerchantRecipe(result, maxUses)
        recipe.addIngredient(input)
        return recipe
    }
}
