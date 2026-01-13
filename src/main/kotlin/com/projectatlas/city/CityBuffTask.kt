package com.projectatlas.city

import com.projectatlas.AtlasPlugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

class CityBuffTask(private val plugin: AtlasPlugin) : Runnable {
    override fun run() {
        plugin.server.onlinePlayers.forEach { player ->
            val chunk = player.location.chunk
            val city = plugin.cityManager.getCityAt(chunk)
            
            if (city != null && city.members.contains(player.uniqueId)) {
                // Player is in their own city - Apply Haste buff
                player.addPotionEffect(PotionEffect(PotionEffectType.HASTE, 220, 0, false, false, true))
                
                // Apply City Regen
                player.addPotionEffect(PotionEffect(PotionEffectType.REGENERATION, 220, 0, false, false, true))
            }
        }
    }
}
