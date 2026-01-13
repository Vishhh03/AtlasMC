package com.projectatlas.visuals

import com.projectatlas.AtlasPlugin
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable

/**
 * Visuals Manager
 * Handles particle effects for highlighting territories and other visual cues.
 */
class VisualManager(private val plugin: AtlasPlugin) {

    init {
        // Start the particle task
        object : BukkitRunnable() {
            override fun run() {
                plugin.server.onlinePlayers.forEach { player ->
                    showChunkBorders(player)
                }
            }
        }.runTaskTimer(plugin, 20L, 10L) // Every 0.5 seconds
    }

    private fun showChunkBorders(player: Player) {
        // Require holding Compass or Map
        val item = player.inventory.itemInMainHand
        if (item.type != Material.COMPASS && item.type != Material.FILLED_MAP) return

        val chunk = player.location.chunk
        
        // Get City at this chunk
        val city = plugin.cityManager.getCityAt(chunk) ?: return
        
        // Determine Color/Particle
        val playerProfile = plugin.identityManager.getPlayer(player.uniqueId)
        val playerCityId = playerProfile?.cityId
        
        val particle = when {
            playerCityId == city.id -> Particle.COMPOSTER // Green (Friendly)
            else -> Particle.FLAME // Red (Hostile/Foreign)
        }

        // Draw Borders
        val world = chunk.world
        val bx = chunk.x shl 4
        val bz = chunk.z shl 4
        val y = player.location.y + 1.0

        // Draw perimeter logic
        // We want to visualize the Chunk Square (16x16)
        
        for (i in 0..16 step 2) {
            // North Edge (z=0) varies x
            world.spawnParticle(particle, bx + i.toDouble(), y, bz.toDouble(), 1, 0.0, 0.0, 0.0, 0.0)
            
            // South Edge (z=16) varies x
            world.spawnParticle(particle, bx + i.toDouble(), y, bz + 16.0, 1, 0.0, 0.0, 0.0, 0.0)
            
            // West Edge (x=0) varies z
            world.spawnParticle(particle, bx.toDouble(), y, bz + i.toDouble(), 1, 0.0, 0.0, 0.0, 0.0)
            
            // East Edge (x=16) varies z
            world.spawnParticle(particle, bx + 16.0, y, bz + i.toDouble(), 1, 0.0, 0.0, 0.0, 0.0)
        }
    }
}
