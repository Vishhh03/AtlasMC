

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
        
        // STANDARD TRADING (Normal Click -> Vanilla GUI)
        // We only modify the trades once, when the player opens it, if needed.
        // Actually, best to do it on 'VillagerAcquireTradeEvent' or check periodically.
        // For existing villagers, we force update them when a player opens them.
        
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
        val newTrades = ArrayList<MerchantRecipe>()
        val profession = villager.profession
        
        val level = villager.villagerLevel
        
        // ECONOMY STANDARD:
        // 1 Emerald = 5 Gold Nuggets
        // All "Buy" trades (Item -> Emerald) become (Item -> Gold Nugget)
        // All "Sell" trades (Emerald -> Item) become (Gold Nugget -> Item)

        when (profession) {
            Villager.Profession.FARMER -> {
                // Buy Crops
                newTrades.add(createTrade(ItemStack(Material.WHEAT, 20), ItemStack(Material.GOLD_NUGGET, 1)))
                newTrades.add(createTrade(ItemStack(Material.POTATO, 15), ItemStack(Material.GOLD_NUGGET, 1)))
                newTrades.add(createTrade(ItemStack(Material.CARROT, 15), ItemStack(Material.GOLD_NUGGET, 1)))
                // Sell Food
                newTrades.add(createTrade(ItemStack(Material.GOLD_NUGGET, 3), ItemStack(Material.BREAD, 6)))
                newTrades.add(createTrade(ItemStack(Material.GOLD_NUGGET, 5), ItemStack(Material.PUMPKIN_PIE, 4)))
            }
            Villager.Profession.LIBRARIAN -> {
                newTrades.add(createTrade(ItemStack(Material.PAPER, 24), ItemStack(Material.GOLD_NUGGET, 1)))
                newTrades.add(createTrade(ItemStack(Material.GOLD_INGOT, 5), ItemStack(Material.EXPERIENCE_BOTTLE, 1)))
                // TODO: Custom Enchanted Books here later
            }
            Villager.Profession.ARMORER -> {
                newTrades.add(createTrade(ItemStack(Material.COAL, 15), ItemStack(Material.GOLD_NUGGET, 1)))
                newTrades.add(createTrade(ItemStack(Material.GOLD_INGOT, 10), ItemStack(Material.IRON_CHESTPLATE, 1)))
                newTrades.add(createTrade(ItemStack(Material.GOLD_BLOCK, 8), ItemStack(Material.DIAMOND_CHESTPLATE, 1)))
            }
            // Add other professions as needed...
            else -> {
                // Default fallback: Buy generic items for gold
                newTrades.add(createTrade(ItemStack(Material.ROTTEN_FLESH, 32), ItemStack(Material.GOLD_NUGGET, 1)))
            }
        }

        villager.recipes = newTrades
        villager.addScoreboardTag(updatedTag)
    }
    
    private fun createTrade(input: ItemStack, result: ItemStack, maxUses: Int = 10): MerchantRecipe {
        val recipe = MerchantRecipe(result, maxUses)
        recipe.addIngredient(input)
        return recipe
    }
}
