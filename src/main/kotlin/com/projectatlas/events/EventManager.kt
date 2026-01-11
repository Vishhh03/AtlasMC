package com.projectatlas.events

import com.projectatlas.AtlasPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.scheduler.BukkitRunnable

class EventManager(private val plugin: AtlasPlugin) {

    private val supplyDrop = SupplyDropEvent(plugin)

    fun startScheduler() {
        val intervalMinutes = plugin.configManager.supplyDropIntervalMinutes
        val intervalTicks = intervalMinutes * 1200L // 60 sec * 20 ticks = 1200
        
        object : BukkitRunnable() {
            override fun run() {
                supplyDrop.trigger()
            }
        }.runTaskTimer(plugin, 6000L, intervalTicks) 
    }
    
    fun forceTrigger() {
        supplyDrop.trigger()
    }
}
