package com.projectatlas.structures

import com.projectatlas.AtlasPlugin
import com.projectatlas.structures.StructureHealthManager.StructureHealth
import com.projectatlas.structures.StructureType
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.block.Container
import org.bukkit.entity.Arrow
import org.bukkit.entity.Monster
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Vector
import java.util.UUID

class StructureBehaviorTask(private val plugin: AtlasPlugin) : BukkitRunnable() {

    private var tickCounter = 0L

    override fun run() {
        tickCounter++
        
        val structures = plugin.structureHealthManager.getAllStructures()
        
        structures.forEach { (id, health) ->
            if (health.isRuined) return@forEach // Ruined structures do nothing
            
            val locationData = plugin.structureHealthManager.findStructureLocation(id) ?: return@forEach
            val center = locationData.center
            
            when (health.type) {
                StructureType.TURRET -> {
                    // Shoot every 2 seconds (40 ticks)
                    if (tickCounter % 40 == 0L) {
                        handleTurretLogic(center)
                    }
                }
                StructureType.GENERATOR -> {
                    // Produce every 60 seconds (1200 ticks)
                    if (tickCounter % 1200L == 0L) {
                        handleGeneratorLogic(center)
                    }
                }
                // Merchant Hut, Quest Camp, Barracks logic handled via listeners/NPCs
                else -> {}
            }
        }
    }
    
    private fun handleTurretLogic(center: org.bukkit.Location) {
        // Identify owner city from location
        val city = plugin.cityManager.getCityAt(center.chunk)
        val cityId = city?.id
        
        // Find nearest valid target
        val target = center.world.getNearbyEntities(center, 15.0, 15.0, 15.0)
            .filter { it is Monster || (it is Player && !isPlayerFriendly(it, cityId)) }
            .minByOrNull { it.location.distanceSquared(center) }
            
        if (target != null) {
            val shootSource = center.clone().add(0.0, 6.0, 0.0) // Top of turret
            
            // Calculate Direction
            val direction = target.location.clone().add(0.0, 1.0, 0.0).subtract(shootSource).toVector().normalize()
            
            // Spawn Arrow
            val arrow = shootSource.world.spawn(shootSource.add(direction), Arrow::class.java)
            arrow.velocity = direction.multiply(2.0)
            arrow.isCritical = true
            
            shootSource.world.playSound(shootSource, Sound.ENTITY_ARROW_SHOOT, 1.0f, 1.0f)
        }
    }
    
    private fun handleGeneratorLogic(center: org.bukkit.Location) {
        // Find output container (Assuming it's nearby or at a specific relative pos)
        val chestLoc = center.clone().add(0.0, 1.0, 1.0) // Based on buildGenerator layout or search nearby
        var containerBlock = chestLoc.block
        
        // Simple search for container in radius if exact pos fails
        if (containerBlock.state !is Container) {
             // Fallback: search 3x3x3 around center
             for (x in -2..2) {
                 for (y in 0..3) {
                     for (z in -2..2) {
                         val b = center.clone().add(x.toDouble(), y.toDouble(), z.toDouble()).block
                         if (b.state is Container) {
                             containerBlock = b
                             break
                         }
                     }
                 }
             }
        }
        
        if (containerBlock.state is Container) {
            val container = containerBlock.state as Container
            val producedItem = org.bukkit.inventory.ItemStack(Material.REDSTONE, 5) // Example resource
            
            // Check if full
            if (container.inventory.firstEmpty() != -1) {
                container.inventory.addItem(producedItem)
                center.world.playSound(center, Sound.BLOCK_ANVIL_USE, 0.5f, 2.0f)
                center.world.spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, center.add(0.0, 2.0, 0.0), 5)
            }
        }
    }
    
    private fun isPlayerFriendly(player: Player, turretCityId: String?): Boolean {
        // If turret has no city (wild), it shoots everyone (hostile)
        if (turretCityId == null) return false
        
        // Members of the city are friendly
        val profile = plugin.identityManager.getPlayer(player.uniqueId)
        if (profile?.cityId == turretCityId) return true
        
        // RELAXED TARGETING:
        // Neutrals (players with no city) are IGNORED (Friendly)
        if (profile?.cityId == null) return true
        
        // Players from OTHER cities are Hostile (Intruders)
        return false
    }
}
