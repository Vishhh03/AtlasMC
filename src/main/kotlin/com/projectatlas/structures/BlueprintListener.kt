package com.projectatlas.structures

import com.projectatlas.AtlasPlugin
import com.projectatlas.visual.CustomItemManager
import com.projectatlas.visual.CustomItemManager.ModelData
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class BlueprintListener(private val plugin: AtlasPlugin) : Listener {
    
    // Track active preview sessions for structure blueprints
    private val previewTasks = ConcurrentHashMap<UUID, BukkitTask>()
    
    // Error reason enum for clearer feedback
    enum class PlacementError(val message: String, val icon: String) {
        NONE("", ""),
        OBSTRUCTION("Blocked by existing blocks", "⚠"),
        NO_FOUNDATION("Needs solid ground below", "▼"),
        MIXED("Multiple issues detected", "✖")
    }
    
    init {
        // Start the preview task runner
        plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            updateAllPreviews()
        }, 0L, 5L) // Update every 5 ticks for smooth visuals
    }
    
    /**
     * Updates visual previews for all players holding blueprint items
     */
    private fun updateAllPreviews() {
        for (player in plugin.server.onlinePlayers) {
            val item = player.inventory.itemInMainHand
            if (!CustomItemManager.isCustomItem(item)) continue
            
            val modelData = CustomItemManager.getModelData(item)
            val structureType = getStructureType(modelData) ?: continue
            
            // Get the block the player is looking at
            val targetBlock = player.getTargetBlockExact(5) ?: continue
            val location = targetBlock.location.add(0.0, 1.0, 0.0)
            
            val (canBuild, error, problemLocations) = checkBuildWithDetails(location, structureType)
            drawStructurePreview(player, location, structureType, canBuild, problemLocations)
            
            // Show subtle actionbar hint when errors exist
            if (!canBuild && error != PlacementError.NONE) {
                player.sendActionBar(
                    Component.text("${error.icon} ${error.message}", NamedTextColor.RED)
                )
            }
        }
    }
    
    /**
     * Enhanced build check that returns detailed error info and problem locations
     */
    private fun checkBuildWithDetails(location: Location, type: StructureType): Triple<Boolean, PlacementError, List<Location>> {
        val width = type.width
        val height = type.height
        val depth = type.depth
        val world = location.world ?: return Triple(false, PlacementError.MIXED, emptyList())
        
        val startX = location.blockX - width / 2
        val startY = location.blockY
        val startZ = location.blockZ - depth / 2
        
        val problemLocations = mutableListOf<Location>()
        var hasObstruction = false
        var hasNoFoundation = false
        
        for (x in 0 until width) {
            for (z in 0 until depth) {
                // Check Foundation (Must be solid)
                val ground = world.getBlockAt(startX + x, startY - 1, startZ + z)
                if (!ground.type.isSolid) {
                    hasNoFoundation = true
                    problemLocations.add(ground.location.add(0.5, 0.5, 0.5))
                }
                
                // Check Volume (Must be clear)
                for (y in 0 until height) {
                    val block = world.getBlockAt(startX + x, startY + y, startZ + z)
                    if (!block.type.isAir && !block.isReplaceable) {
                        hasObstruction = true
                        problemLocations.add(block.location.add(0.5, 0.5, 0.5))
                    }
                }
            }
        }
        
        val canBuild = !hasObstruction && !hasNoFoundation
        val error = when {
            hasObstruction && hasNoFoundation -> PlacementError.MIXED
            hasObstruction -> PlacementError.OBSTRUCTION
            hasNoFoundation -> PlacementError.NO_FOUNDATION
            else -> PlacementError.NONE
        }
        
        return Triple(canBuild, error, problemLocations.take(15)) // Limit to 15 problem spots
    }
    
    /**
     * Draws a subtle preview outline for the structure placement
     */
    private fun drawStructurePreview(player: Player, location: Location, type: StructureType, canBuild: Boolean, problemLocations: List<Location> = emptyList()) {
        val width = type.width
        val height = type.height
        val depth = type.depth
        
        // Calculate bounds centered on location
        val startX = location.blockX - width / 2
        val startY = location.blockY
        val startZ = location.blockZ - depth / 2
        
        // Colors based on validity
        val borderColor = if (canBuild) Color.fromRGB(80, 200, 255) else Color.fromRGB(255, 80, 80)
        val cornerColor = if (canBuild) Color.fromRGB(100, 255, 150) else Color.fromRGB(255, 100, 100)
        val borderDust = Particle.DustOptions(borderColor, 0.5f)
        val cornerDust = Particle.DustOptions(cornerColor, 0.7f)
        
        val step = 0.4 // Particle spacing
        
        // --- Draw Floor Border Outline ---
        // North edge
        var x = startX.toDouble()
        while (x <= startX + width) {
            player.spawnParticle(Particle.DUST, Location(location.world, x, startY.toDouble(), startZ.toDouble()), 1, borderDust)
            x += step
        }
        // South edge
        x = startX.toDouble()
        while (x <= startX + width) {
            player.spawnParticle(Particle.DUST, Location(location.world, x, startY.toDouble(), (startZ + depth).toDouble()), 1, borderDust)
            x += step
        }
        // West edge
        var z = startZ.toDouble()
        while (z <= startZ + depth) {
            player.spawnParticle(Particle.DUST, Location(location.world, startX.toDouble(), startY.toDouble(), z), 1, borderDust)
            z += step
        }
        // East edge
        z = startZ.toDouble()
        while (z <= startZ + depth) {
            player.spawnParticle(Particle.DUST, Location(location.world, (startX + width).toDouble(), startY.toDouble(), z), 1, borderDust)
            z += step
        }
        
        // --- Draw Corner Pillars ---
        val corners = listOf(
            Pair(startX.toDouble(), startZ.toDouble()),
            Pair((startX + width).toDouble(), startZ.toDouble()),
            Pair(startX.toDouble(), (startZ + depth).toDouble()),
            Pair((startX + width).toDouble(), (startZ + depth).toDouble())
        )
        
        for ((cx, cz) in corners) {
            var y = startY
            while (y <= startY + height) {
                player.spawnParticle(Particle.DUST, Location(location.world, cx, y.toDouble(), cz), 1, cornerDust)
                y += 2 // Every other block for subtlety
            }
        }
        
        // --- Draw Top Border (subtle) ---
        val topY = startY + height
        // Only corners at top for subtlety
        for ((cx, cz) in corners) {
            player.spawnParticle(Particle.DUST, Location(location.world, cx, topY.toDouble(), cz), 2, cornerDust)
        }
        
        // --- Draw Problem Locations (subtle error indicators) ---
        if (!canBuild && problemLocations.isNotEmpty()) {
            val errorDust = Particle.DustOptions(Color.fromRGB(255, 50, 50), 0.6f)
            for (problemLoc in problemLocations) {
                // Subtle X pattern at problem blocks
                player.spawnParticle(Particle.DUST, problemLoc, 1, errorDust)
                player.spawnParticle(Particle.DUST, problemLoc.clone().add(0.2, 0.0, 0.2), 1, errorDust)
                player.spawnParticle(Particle.DUST, problemLoc.clone().add(-0.2, 0.0, 0.2), 1, errorDust)
                player.spawnParticle(Particle.DUST, problemLoc.clone().add(0.2, 0.0, -0.2), 1, errorDust)
                player.spawnParticle(Particle.DUST, problemLoc.clone().add(-0.2, 0.0, -0.2), 1, errorDust)
            }
        }
    }
    
    /**
     * Shows a subtle error pulse animation when placement fails
     */
    private fun showErrorPulse(player: Player, location: Location, type: StructureType) {
        val width = type.width
        val depth = type.depth
        val startX = location.blockX - width / 2
        val startZ = location.blockZ - depth / 2
        val startY = location.blockY
        
        // Animate a red pulse expanding outward
        var pulseRadius = 0.0
        val maxRadius = kotlin.math.max(width, depth) / 2.0 + 1.0
        
        plugin.server.scheduler.runTaskTimer(plugin, { task ->
            if (pulseRadius > maxRadius) {
                (task as BukkitTask).cancel()
                return@runTaskTimer
            }
            
            val pulseColor = Color.fromRGB(255, 60, 60)
            val pulseDust = Particle.DustOptions(pulseColor, (0.8f - (pulseRadius / maxRadius * 0.5f)).toFloat())
            
            // Draw expanding ring
            val centerX = startX + width / 2.0
            val centerZ = startZ + depth / 2.0
            val steps = 16
            for (i in 0 until steps) {
                val angle = (i.toDouble() / steps) * 2 * Math.PI
                val px = centerX + pulseRadius * kotlin.math.cos(angle)
                val pz = centerZ + pulseRadius * kotlin.math.sin(angle)
                player.spawnParticle(Particle.DUST, Location(location.world, px, startY + 0.1, pz), 1, pulseDust)
            }
            
            pulseRadius += 0.5
        }, 0L, 2L)
    }
    
    private fun getStructureType(modelData: Int): StructureType? {
        return when (modelData) {
            ModelData.BLUEPRINT_GENERIC -> StructureType.MERCHANT_HUT
            ModelData.BLUEPRINT_BARRACKS -> StructureType.BARRACKS
            ModelData.BLUEPRINT_TURRET -> StructureType.TURRET
            else -> null
        }
    }

    @EventHandler
    fun onBlueprintUse(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        
        val item = event.item ?: return
        val player = event.player
        val block = event.clickedBlock ?: return
        
        // Prevent placing the paper item itself
        if (CustomItemManager.isCustomItem(item)) {
            val modelData = CustomItemManager.getModelData(item)
            
            val structureType = getStructureType(modelData)
            
            if (structureType != null) {
                event.isCancelled = true // Don't place the paper
                
                // Spawn structure
                val location = block.location.add(0.0, 1.0, 0.0)
                val (canBuild, error, problemLocations) = checkBuildWithDetails(location, structureType)
                
                if (canBuild) {
                    plugin.structureManager.spawnStructure(structureType, location)
                    player.sendMessage(Component.text("Constructing ${structureType.name}...", NamedTextColor.GREEN))
                    
                    // Consume item
                    item.amount -= 1
                    
                    // Play placement sound
                    player.playSound(location, Sound.BLOCK_ANVIL_USE, 0.8f, 1.2f)
                } else {
                    // Enhanced error feedback
                    val errorMessage = when (error) {
                        PlacementError.OBSTRUCTION -> "Cannot build here! ${problemLocations.size} block(s) in the way."
                        PlacementError.NO_FOUNDATION -> "Cannot build here! Ground is not solid."
                        PlacementError.MIXED -> "Cannot build here! Area obstructed and ground unstable."
                        else -> "Cannot build here! Area obstructed."
                    }
                    
                    player.sendMessage(Component.text(errorMessage, NamedTextColor.RED))
                    player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f)
                    
                    // Show subtle error pulse animation
                    showErrorPulse(player, location, structureType)
                }
            }
        }
    }
}
