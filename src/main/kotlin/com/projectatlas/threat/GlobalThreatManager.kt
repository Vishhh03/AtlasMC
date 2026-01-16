package com.projectatlas.threat

import com.projectatlas.AtlasPlugin
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.GameRule
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable

class GlobalThreatManager(private val plugin: AtlasPlugin) {

    var threatLevel: Double = 0.0 // 0.0 to 100.0
    private var bossBar: BossBar? = null
    
    // Config
    private val threatIncreasePerMinute = 0.5 // +30% per hour
    private val threatReductionPerDungeon = 10.0
    
    init {
        // Initialize BossBar
        bossBar = BossBar.bossBar(
            Component.text("Global Threat: LOW", NamedTextColor.GREEN),
            0.0f,
            BossBar.Color.GREEN,
            BossBar.Overlay.NOTCHED_10
        )
        
        // Timer Task
        object : BukkitRunnable() {
            override fun run() {
                tick()
            }
        }.runTaskTimer(plugin, 1200L, 1200L) // Every 1 minute
    }
    
    fun tick() {
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
        
        // Update visuals
        updateVisuals()
        
        // Triggers
        if (threatLevel >= 100.0) {
            triggerBloodMoon()
        }
    }
    
    fun increaseThreat(amount: Double) {
        threatLevel = (threatLevel + amount).coerceIn(0.0, 100.0)
        updateVisuals()
    }
    
    fun reduceThreat(amount: Double) {
        threatLevel = (threatLevel - amount).coerceIn(0.0, 100.0)
        
        // Notify
        plugin.server.broadcast(Component.text("📉 Global Threat reduced by $amount%", NamedTextColor.GREEN))
        updateVisuals()
    }
    
    private fun updateVisuals() {
        // BossBar Text & Color
        val (title, color) = when {
            threatLevel < 30 -> "Global Threat: LOW" to BossBar.Color.GREEN
            threatLevel < 70 -> "Global Threat: MODERATE" to BossBar.Color.YELLOW
            threatLevel < 90 -> "Global Threat: HIGH" to BossBar.Color.RED
            else -> "Global Threat: CRITICAL" to BossBar.Color.PURPLE
        }
        
        val bar = bossBar ?: return
        bar.name(Component.text("$title (${String.format("%.1f", threatLevel)}%)", 
            if (color == BossBar.Color.PURPLE) NamedTextColor.DARK_PURPLE else NamedTextColor.WHITE))
        bar.color(color)
        bar.progress((threatLevel / 100.0).toFloat().coerceIn(0f, 1f))
        
        // Show to all players
        plugin.server.onlinePlayers.forEach { player ->
            player.showBossBar(bar)
            
            // Red Sky Effect (Packet/Time manipulation)
            if (threatLevel > 80) {
                 // Spooky red tint visual logic (Packet logic handled by visual manager)
                 // Or simple time shift:
                 // player.setPlayerTime(18000, false) // Midnight visual
            }
        }
        
        // World Border redness? Atmosphere manager handles particle fog
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
