package com.projectatlas.city

import com.projectatlas.AtlasPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

class WonderManager(private val plugin: AtlasPlugin) : Listener {

    fun contribute(player: Player, city: City, wonder: CityWonder) {
        if (city.completedWonders.contains(wonder)) {
            player.sendMessage(Component.text("This wonder is already built!", NamedTextColor.RED))
            return
        }
        
        // Check player inventory for required items
        val progressMap: MutableMap<Material, Int> = city.wonderProgress.computeIfAbsent(wonder) { mutableMapOf<Material, Int>() }
        val requirements = wonder.requirements
        
        var contributed = false
        
        for ((mat, requiredTotal) in requirements) {
            val current = progressMap.getOrDefault(mat, 0)
            if (current >= requiredTotal) continue
            
            // Scan inventory
            val amountNeeded = requiredTotal - current
            if (player.inventory.contains(mat)) {
                // Take as much as possible
                val taken = removeItems(player, mat, amountNeeded)
                if (taken > 0) {
                    progressMap[mat] = current + taken
                    contributed = true
                    player.sendMessage(Component.text("Contributed $taken ${mat.name} to ${wonder.displayName}", NamedTextColor.GREEN))
                    player.playSound(player.location, Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f)
                }
            }
        }
        
        if (contributed) {
             plugin.cityManager.saveCity(city)
             checkCompletion(city, wonder)
        } else {
             player.sendMessage(Component.text("You don't have any required materials for this wonder.", NamedTextColor.RED))
             viewProgress(player, city, wonder)
        }
    }
    
    private fun removeItems(player: Player, mat: Material, amount: Int): Int {
        var removed = 0
        val contents = player.inventory.contents
        for (i in contents.indices) {
            val item = contents[i]
            if (item != null && item.type == mat) {
                val take = Math.min(item.amount, amount - removed)
                item.amount -= take
                removed += take
                if (item.amount <= 0) player.inventory.setItem(i, null)
                else player.inventory.setItem(i, item) // Update count
                
                if (removed >= amount) break
            }
        }
        return removed
    }
    
    fun viewProgress(player: Player, city: City, wonder: CityWonder) {
        player.sendMessage(Component.text("--- ${wonder.displayName} Progress ---", NamedTextColor.GOLD))
        val progressMap = city.wonderProgress[wonder] ?: emptyMap()
        
        var allDone = true
        wonder.requirements.forEach { (mat, required) ->
            val current = progressMap.getOrDefault(mat, 0)
            val color = if (current >= required) NamedTextColor.GREEN else NamedTextColor.YELLOW
            player.sendMessage(Component.text("${mat.name}: $current / $required", color))
            if (current < required) allDone = false
        }
        
        if (allDone && !city.completedWonders.contains(wonder)) {
             checkCompletion(city, wonder)
        }
    }
    
    private fun checkCompletion(city: City, wonder: CityWonder) {
        val progressMap = city.wonderProgress[wonder] ?: return
        val allMet = wonder.requirements.all { (mat, required) ->
            progressMap.getOrDefault(mat, 0) >= required
        }
        
        if (allMet) {
            city.completedWonders.add(wonder)
            city.wonderProgress.remove(wonder) // Cleanup
            plugin.cityManager.saveCity(city)
            
            // Broadcast
            plugin.server.broadcast(Component.text("--------------------------------", NamedTextColor.GOLD))
            plugin.server.broadcast(Component.text("CITY NEWS: ${city.name} has completed ${wonder.displayName}!", NamedTextColor.YELLOW))
            plugin.server.broadcast(Component.text("They now enjoy: ${wonder.buffDescription}", NamedTextColor.AQUA))
            plugin.server.broadcast(Component.text("--------------------------------", NamedTextColor.GOLD))
            
            city.members.mapNotNull { plugin.server.getPlayer(it) }.forEach { 
                it.playSound(it.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.5f)
                applyBuffs(it, city) // Apply immediately
            }
        }
    }
    
    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        val profile = plugin.identityManager.getPlayer(player.uniqueId) ?: return
        if (profile.cityId != null) {
            val city = plugin.cityManager.getCity(profile.cityId!!) ?: return
            applyBuffs(player, city)
        }
    }
    
    fun applyBuffs(player: Player, city: City) {
        // War Academy - Health Boost
        if (city.completedWonders.contains(CityWonder.WAR_ACADEMY)) {
             // 4 extra hearts
             val attribute = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH)
             if (attribute != null && attribute.baseValue < 24.0) {
                 attribute.baseValue = 24.0
             }
        }
    }
    
    @EventHandler
    fun onXpGain(event: org.bukkit.event.player.PlayerExpChangeEvent) {
        val player = event.player
        val profile = plugin.identityManager.getPlayer(player.uniqueId) ?: return
        val cityId = profile.cityId ?: return
        val city = plugin.cityManager.getCity(cityId) ?: return
        
        if (city.completedWonders.contains(CityWonder.GREAT_LIBRARY)) {
            val bonus = (event.amount * 0.2).toInt()
            if (bonus > 0) {
                event.amount += bonus
                // No message to avoid spam
            }
        }
    }
    
    @EventHandler
    fun onMove(event: org.bukkit.event.player.PlayerMoveEvent) {
        // Optimization: Run infrequently or check chunk change
        if (event.from.chunk == event.to.chunk) return
        
        val player = event.player
        val city = plugin.cityManager.getCityAt(player.chunk)
        
        if (city != null && city.completedWonders.contains(CityWonder.INDUSTRIAL_FORGE)) {
            // Apply Haste II if member
             if (city.members.contains(player.uniqueId)) {
                 player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 200, 1, false, false))
             }
        }
    }
}
