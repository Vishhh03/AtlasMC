package com.projectatlas.config

import com.projectatlas.AtlasPlugin
import org.bukkit.Material

class ConfigManager(private val plugin: AtlasPlugin) {

    var startingBalance: Double = 100.0
    var supplyDropIntervalMinutes: Int = 30
    var supplyDropRadius: Int = 500
    
    // City Economy
    var cityCreationCost: Double = 1000.0
    var chunkClaimBaseCost: Double = 100.0
    var chunkClaimScaling: Double = 1.5
    
    // Menu Settings
    var menuTriggerItem: Material = Material.COMPASS
    var menuTriggerSneaking: Boolean = false // If true, must sneak + right-click

    fun loadConfig() {
        plugin.saveDefaultConfig()
        val config = plugin.config
        
        startingBalance = config.getDouble("economy.starting-balance", 100.0)
        supplyDropIntervalMinutes = config.getInt("events.supply-drop.interval-minutes", 30)
        supplyDropRadius = config.getInt("events.supply-drop.radius", 500)
        
        cityCreationCost = config.getDouble("city.creation-cost", 1000.0)
        chunkClaimBaseCost = config.getDouble("city.chunk-claim-base-cost", 100.0)
        chunkClaimScaling = config.getDouble("city.chunk-claim-scaling", 1.5)
        
        // Menu trigger item
        val itemName = config.getString("menu.trigger-item", "COMPASS")
        menuTriggerItem = Material.matchMaterial(itemName ?: "COMPASS") ?: Material.COMPASS
        menuTriggerSneaking = config.getBoolean("menu.require-sneaking", false)
        
        plugin.logger.info("Configuration loaded. Menu trigger: $menuTriggerItem (sneak: $menuTriggerSneaking)")
    }
    
    fun getChunkClaimCost(currentChunks: Int): Double {
        return chunkClaimBaseCost * Math.pow(chunkClaimScaling, currentChunks.toDouble().coerceAtMost(10.0))
    }
}
