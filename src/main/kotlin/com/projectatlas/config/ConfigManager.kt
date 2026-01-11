package com.projectatlas.config

import com.projectatlas.AtlasPlugin

class ConfigManager(private val plugin: AtlasPlugin) {

    var startingBalance: Double = 100.0
    var supplyDropIntervalMinutes: Int = 30
    var supplyDropRadius: Int = 500

    fun loadConfig() {
        plugin.saveDefaultConfig()
        val config = plugin.config
        
        startingBalance = config.getDouble("economy.starting-balance", 100.0)
        supplyDropIntervalMinutes = config.getInt("events.supply-drop.interval-minutes", 30)
        supplyDropRadius = config.getInt("events.supply-drop.radius", 500)
        
        plugin.logger.info("Configuration loaded. Starting Balance: $startingBalance")
    }
}
