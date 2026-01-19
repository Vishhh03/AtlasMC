package com.projectatlas.city

import com.projectatlas.AtlasPlugin
import org.bukkit.Color
import org.bukkit.Particle
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class CityVisualizer(private val plugin: AtlasPlugin) {
    private val enabledPlayers = ConcurrentHashMap.newKeySet<UUID>()

    fun toggle(player: Player) {
        if (enabledPlayers.contains(player.uniqueId)) {
            enabledPlayers.remove(player.uniqueId)
            player.sendMessage(net.kyori.adventure.text.Component.text("Hidden city borders.", net.kyori.adventure.text.format.NamedTextColor.RED))
        } else {
            enabledPlayers.add(player.uniqueId)
            player.sendMessage(net.kyori.adventure.text.Component.text("Showing city borders.", net.kyori.adventure.text.format.NamedTextColor.GREEN))
        }
    }

    fun startTask() {
        plugin.server.scheduler.runTaskTimerAsynchronously(plugin, Runnable {
            if (enabledPlayers.isEmpty()) return@Runnable

            enabledPlayers.removeIf { uuid ->
                val player = plugin.server.getPlayer(uuid)
                if (player == null || !player.isOnline) {
                    true
                } else {
                    showBorders(player)
                    false
                }
            }
        }, 0L, 20L) // Every 1 second
    }

    private fun showBorders(player: Player) {
        val chunk = player.location.chunk
        val radius = 1
        
        val world = player.world
        val pY = player.y.toInt()
        
        for (x in -radius..radius) {
            for (z in -radius..radius) {
                val cx = chunk.x + x
                val cz = chunk.z + z
                
                // Use the coordinate-based lookup to avoid loading chunks if pomsible
                val city = plugin.cityManager.getCityAt(world.name, cx, cz)
                
                if (city != null) {
                    val isOwnCity = plugin.identityManager.getPlayer(player.uniqueId)?.cityId == city.id
                    val color = if (isOwnCity) Color.LIME else Color.RED
                    
                    visualizeChunk(player, cx, cz, pY, color)
                }
            }
        }
    }
    
    // Extension/Correction: Access private chunkMap? No, stick to public API.
    
    private fun visualizeChunk(player: Player, cx: Int, cz: Int, y: Int, color: Color) {
        val minX = cx * 16.0
        val minZ = cz * 16.0
        val maxX = minX + 16.0
        val maxZ = minZ + 16.0
        
        val visualY = y.toDouble() + 1.0
        
        // Draw corners and some points along lines
        // 4 corners
        spawnColorParticle(player, minX, visualY, minZ, color)
        spawnColorParticle(player, maxX, visualY, minZ, color)
        spawnColorParticle(player, maxX, visualY, maxZ, color)
        spawnColorParticle(player, minX, visualY, maxZ, color)
        
        // Midpoints
        spawnColorParticle(player, minX + 8, visualY, minZ, color)
        spawnColorParticle(player, maxX, visualY, minZ + 8, color)
        spawnColorParticle(player, minX + 8, visualY, maxZ, color)
        spawnColorParticle(player, minX, visualY, minZ + 8, color)
    }

    private fun spawnColorParticle(player: Player, x: Double, y: Double, z: Double, color: Color) {
        // Use REDSTONE (Dust) for colored particles
         player.spawnParticle(
             Particle.DUST, 
             x, y, z, 
             1, 0.0, 0.0, 0.0, 0.0, 
             Particle.DustOptions(color, 1.0f)
         )
    }
}
