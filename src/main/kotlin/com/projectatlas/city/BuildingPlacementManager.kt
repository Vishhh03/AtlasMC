package com.projectatlas.city

import com.projectatlas.AtlasPlugin
import com.projectatlas.structures.StructureType
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class BuildingPlacementManager(private val plugin: AtlasPlugin) : Listener {

    data class PlacementSession(
        val player: Player,
        val type: StructureType,
        val cityId: String
    )

    private val sessions = ConcurrentHashMap<UUID, PlacementSession>()

    fun startPlacement(player: Player, type: StructureType, cityId: String) {
        // Cancel existing if any
        if (sessions.containsKey(player.uniqueId)) {
            cancelPlacement(player)
        }

        sessions[player.uniqueId] = PlacementSession(player, type, cityId)
        
        player.sendMessage(Component.text("Entered Placement Mode for ${type.name}", NamedTextColor.GREEN))
        player.sendMessage(Component.text("Left Click to Place", NamedTextColor.YELLOW))
        player.sendMessage(Component.text("Drop Item or Swap Hands to Cancel", NamedTextColor.RED))
        
        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
    }

    fun cancelPlacement(player: Player) {
        if (sessions.remove(player.uniqueId) != null) {
            player.sendMessage(Component.text("Placement cancelled.", NamedTextColor.RED))
            player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1f, 0.5f)
        }
    }
    
    // --- Event Handling ---

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        val session = sessions[event.player.uniqueId] ?: return
        
        if (event.action == Action.LEFT_CLICK_AIR || event.action == Action.LEFT_CLICK_BLOCK) {
            event.isCancelled = true // Prevent breaking blocks
            attemptPlacement(session)
        }
    }
    
    @EventHandler
    fun onDrop(event: PlayerDropItemEvent) {
        if (sessions.containsKey(event.player.uniqueId)) {
            event.isCancelled = true
            cancelPlacement(event.player)
        }
    }
    
    @EventHandler
    fun onSwap(event: PlayerItemHeldEvent) {
        if (sessions.containsKey(event.player.uniqueId)) {
            cancelPlacement(event.player)
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        sessions.remove(event.player.uniqueId)
    }

    // --- Logic ---

    private fun attemptPlacement(session: PlacementSession) {
        val player = session.player
        val targetBlock = player.getTargetBlockExact(10)
        
        if (targetBlock == null) {
            player.sendMessage(Component.text("Too far away.", NamedTextColor.RED))
            return
        }
        
        val location = targetBlock.location.add(0.0, 1.0, 0.0)
        val type = session.type
        
        // 1. Check Physics/Space
        if (!plugin.structureManager.canBuild(location, type)) {
             player.sendMessage(Component.text("Cannot build here (Blocked or uneven ground).", NamedTextColor.RED))
             player.playSound(player.location, Sound.BLOCK_ANVIL_PLACE, 1f, 0.5f)
             return
        }
        
        // 2. Check City Territory
        val chunk = location.chunk
        val cityAtLoc = plugin.cityManager.getCityAt(chunk)
        if (cityAtLoc?.id != session.cityId) {
             player.sendMessage(Component.text("Must build within your city territory.", NamedTextColor.RED))
             return
        }
        
        // 3. Confirm
        if (plugin.cityManager.confirmBuildingPlacement(session.cityId, type, location)) {
            player.sendMessage(Component.text("Construction started!", NamedTextColor.GREEN))
            player.playSound(player.location, Sound.BLOCK_ANVIL_USE, 1f, 1f)
            sessions.remove(player.uniqueId)
        } else {
            player.sendMessage(Component.text("Placement failed (Limit reached or invalid).", NamedTextColor.RED))
        }
    }

    // --- Visual Task ---
    
    fun startTask() {
        plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            sessions.values.forEach { session ->
                previewStructure(session)
            }
        }, 0L, 2L) // Every 2 ticks (10fps update)
    }

    private fun previewStructure(session: PlacementSession) {
        val player = session.player
        val targetBlock = player.getTargetBlockExact(10) ?: return
        
        val location = targetBlock.location.add(0.0, 1.0, 0.0)
        val type = session.type
        
        // Check Validity for Color
        val physicsValid = plugin.structureManager.canBuild(location, type)
        // We do a lighter territory check here (just chunk check)
        val chunk = location.chunk
        val cityAtLoc = plugin.cityManager.getCityAt(chunk)
        val territoryValid = cityAtLoc?.id == session.cityId
        
        val valid = physicsValid && territoryValid
        val color = if (valid) Color.LIME else Color.RED
        
        // Draw Box
        drawBox(player, location, type.width, type.height, type.depth, color)
    }
    
    private fun drawBox(player: Player, center: Location, width: Int, height: Int, depth: Int, color: Color) {
        val minX = center.blockX - width / 2.0
        val minY = center.blockY.toDouble()
        val minZ = center.blockZ - depth / 2.0
        
        val maxX = minX + width
        val maxY = minY + height
        val maxZ = minZ + depth
        
        // Corners
        spawnParticle(player, minX, minY, minZ, color)
        spawnParticle(player, maxX, minY, minZ, color)
        spawnParticle(player, minX, minY, maxZ, color)
        spawnParticle(player, maxX, minY, maxZ, color)
        
        spawnParticle(player, minX, maxY, minZ, color)
        spawnParticle(player, maxX, maxY, minZ, color)
        spawnParticle(player, minX, maxY, maxZ, color)
        spawnParticle(player, maxX, maxY, maxZ, color)
        
        // Edges (Simplified for performance - just corners + midpoints or scant lines?)
        // Let's do simple corners + centers of lines
       /*
        spawnParticle(player, (minX+maxX)/2, minY, minZ, color)
        spawnParticle(player, (minX+maxX)/2, maxY, minZ, color)
        spawnParticle(player, (minX+maxX)/2, minY, maxZ, color)
        spawnParticle(player, (minX+maxX)/2, maxY, maxZ, color)
        */
        // Use Dust
    }
    
    private fun spawnParticle(player: Player, x: Double, y: Double, z: Double, color: Color) {
        player.spawnParticle(Particle.DUST, x, y, z, 1, 0.0, 0.0, 0.0, 0.0, Particle.DustOptions(color, 1.0f))
    }
}
