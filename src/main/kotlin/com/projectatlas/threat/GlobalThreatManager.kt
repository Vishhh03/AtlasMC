package com.projectatlas.threat

import com.projectatlas.AtlasPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.GameRule
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable

class GlobalThreatManager(private val plugin: AtlasPlugin) {

    var threatLevel: Double = 0.0 // 0.0 to 100.0
    private var tickCounter = 0
    
    // Config
    private val threatIncreasePerMinute = 0.1 // +6% per hour (was 0.5)
    private val threatReductionPerDungeon = 10.0
    
    init {
        // Timer Task
        object : BukkitRunnable() {
            override fun run() {
                tick()
            }
        }.runTaskTimer(plugin, 1200L, 1200L) // Every 1 minute
    }
    
    fun tick() {
        tickCounter++
        
        // Calculate Arcane Sanctum mitigation
        var arcaneCount = 0
        plugin.cityManager.getAllCities().forEach { city ->
            if (city.specialization == com.projectatlas.city.CitySpecialization.ARCANE_SANCTUM && city.mana > 0) {
                arcaneCount++
            }
        }
        
        // Base increase: 0.5
        // Mitigation: 0.1 per Arcane Sanctum (Max 0.5 mitigation)
        val mitigation = (arcaneCount * 0.1).coerceAtMost(0.5)
        val netIncrease = (threatIncreasePerMinute - mitigation).coerceAtLeast(0.0)
        
        // Increase threat
        increaseThreat(netIncrease)
        
        // Update visuals (only when threat is notable)
        if (threatLevel >= 30) {
            updateVisuals()
        }
        
        // Triggers
        if (threatLevel >= 100.0) {
            triggerBloodMoon()
        }
    }
    
    fun increaseThreat(amount: Double) {
        threatLevel = (threatLevel + amount).coerceIn(0.0, 100.0)
        
        // Only update visuals if it's a significant increase
        if (amount >= 5.0) {
            updateVisuals()
        }
    }
    
    fun reduceThreat(amount: Double) {
        threatLevel = (threatLevel - amount).coerceIn(0.0, 100.0)
        
        // Notify
        plugin.server.broadcast(Component.text("📉 Global Threat reduced by $amount%", NamedTextColor.GREEN))
        updateVisuals()
    }
    
    private fun updateVisuals() {
        // Determine threat level display
        val (icon, label, color) = when {
            threatLevel < 30 -> Triple("☀", "LOW", NamedTextColor.GREEN)
            threatLevel < 70 -> Triple("⚠", "MODERATE", NamedTextColor.YELLOW)
            threatLevel < 90 -> Triple("🔥", "HIGH", NamedTextColor.RED)
            else -> Triple("☠", "CRITICAL", NamedTextColor.DARK_PURPLE)
        }
        
        // Build compact action bar message
        val actionBarText = Component.text()
            .append(Component.text("$icon ", color))
            .append(Component.text("Threat: ", NamedTextColor.GRAY))
            .append(Component.text(label, color).let { 
                if (threatLevel >= 90) it.decorate(TextDecoration.BOLD) else it 
            })
            .append(Component.text(" ${String.format("%.0f", threatLevel)}%", NamedTextColor.WHITE))
            .build()
        
        // Show to all players via action bar (less intrusive)
        plugin.server.onlinePlayers.forEach { player ->
            // Only show action bar, not a boss bar
            player.sendActionBar(actionBarText)
        }
        
        // Atmosphere effects for high threat
        plugin.atmosphereManager.setThreatLevel(threatLevel)
    }
    
    private fun triggerBloodMoon() {
        plugin.server.broadcast(Component.text("☠ THE BLOOD MOON RISES! ☠", NamedTextColor.DARK_RED))
        plugin.server.broadcast(Component.text("All Cities are under siege!", NamedTextColor.RED))
        
        // Reset threat partly
        threatLevel = 50.0 
        
        // Trigger Sieges on all eligible cities
        plugin.cityManager.getAllCities().forEach { city ->
             // Force siege (bypass cooldown)
             // Find a spawn point
             val center = city.getControlPoint() ?: return@forEach // Assuming logic exists
             plugin.siegeManager.startSiege(city, center)
        }
    }
    
    fun onDungeonComplete(valid: Boolean) {
        if (valid) reduceThreat(threatReductionPerDungeon)
    }
}
