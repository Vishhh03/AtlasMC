package com.projectatlas.visual

import com.projectatlas.AtlasPlugin
import org.bukkit.Color
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import kotlin.math.abs

class CityBorderVisualizer(private val plugin: AtlasPlugin) : BukkitRunnable() {

    override fun run() {
        plugin.server.onlinePlayers.forEach { player ->
            // Skip if in build mode (they have their own full borders)
            if (plugin.builderModeManager.isInBuildMode(player)) return@forEach

            visualizeNearbyBorders(player)
        }
    }

    private fun visualizeNearbyBorders(player: Player) {
        val radius = 10 // Show borders within 10 blocks
        val loc = player.location
        val chunkX = loc.blockX shr 4
        val chunkZ = loc.blockZ shr 4

        val currentCity = plugin.cityManager.getCityAt(loc.chunk)
        val playerCityId = plugin.identityManager.getPlayer(player.uniqueId)?.cityId

        // Check 4 directions
        checkBorder(player, chunkX, chunkZ, 1, 0, currentCity?.id, playerCityId, radius) // East
        checkBorder(player, chunkX, chunkZ, -1, 0, currentCity?.id, playerCityId, radius) // West
        checkBorder(player, chunkX, chunkZ, 0, 1, currentCity?.id, playerCityId, radius) // South
        checkBorder(player, chunkX, chunkZ, 0, -1, currentCity?.id, playerCityId, radius) // North
    }

    private fun checkBorder(
        player: Player, 
        cX: Int, 
        cZ: Int, 
        dX: Int, 
        dZ: Int, 
        currentCityId: String?, 
        playerCityId: String?,
        radius: Int
    ) {
        val neighborChunk = player.world.getChunkAt(cX + dX, cZ + dZ)
        val neighborCity = plugin.cityManager.getCityAt(neighborChunk)

        // Border exists if city ID changes
        if (currentCityId != neighborCity?.id) {
            // Determine border coord
            val borderX: Double?
            val borderZ: Double?
            
            // Chunk edge coords
            // If dX=1 (East), border is at (cX+1)*16
            // If dX=-1 (West), border is at cX*16
            // If dZ=1 (South), border is at (cZ+1)*16
            // If dZ=-1 (North), border is at cZ*16
            
            if (dX != 0) {
                borderX = if (dX > 0) ((cX + 1) * 16).toDouble() else (cX * 16).toDouble()
                borderZ = null // Vertical line along Z
                
                // Check distance
                if (abs(player.location.x - borderX) > radius) return
            } else {
                borderZ = if (dZ > 0) ((cZ + 1) * 16).toDouble() else (cZ * 16).toDouble()
                borderX = null // Horizontal line along X
                
                // Check distance
                if (abs(player.location.z - borderZ) > radius) return
            }

            // Determine Color
            // If either side is MY city -> Green
            // If neither is my city (Wilderness vs Enemy, or Enemy A vs Enemy B) -> Red
            val isMyBorder = currentCityId == playerCityId || neighborCity?.id == playerCityId
            val color = if (isMyBorder) Color.fromRGB(50, 255, 50) else Color.RED
            
            drawBorderParticles(player, borderX, borderZ, color)
        }
    }

    private fun drawBorderParticles(player: Player, fixedX: Double?, fixedZ: Double?, color: Color) {
        val yBase = player.location.y
        val dust = Particle.DustOptions(color, 1.0f)
        
        // Draw segment near player
        // Range +/- 5 blocks along the wall axis
        for (i in -5..5) {
            val y = yBase + (i % 3) // simple variation or just constant? Let's do columns.
            // Actually, simplified: just a few particles at eye level and ground?
            // User said "lines". 
            
            // Let's spawn a column at the closest point, and points along the line
            if (fixedX != null) {
                // Vertical wall along Z
                // We want to draw particles at fixedX, z = player.z + i
                val z = player.location.z + i
                player.spawnParticle(Particle.DUST, fixedX, yBase + 1.0, z, 1, dust)
                player.spawnParticle(Particle.DUST, fixedX, yBase + 2.5, z, 1, dust)
            } else if (fixedZ != null) {
                // Wall along X
                val x = player.location.x + i
                player.spawnParticle(Particle.DUST, x, yBase + 1.0, fixedZ, 1, dust)
                player.spawnParticle(Particle.DUST, x, yBase + 2.5, fixedZ, 1, dust)
            }
        }
    }
}
