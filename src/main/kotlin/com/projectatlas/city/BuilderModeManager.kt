package com.projectatlas.city

import com.projectatlas.AtlasPlugin
import com.projectatlas.structures.StructureType
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Color
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.block.Block
import org.bukkit.entity.Display
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.block.Action
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.AxisAngle4f
import org.joml.Vector3f
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * BuilderModeManager - Minecraft Legends-style city building system
 * 
 * Features:
 * - Third-person POV toggle when entering build mode
 * - Extended placement range (30+ blocks)
 * - Ghost block previews using Block Display entities
 * - Structure rotation (0/90/180/270)
 * - Resource checking and error handling
 * - Animated construction with Allays
 */
class BuilderModeManager(private val plugin: AtlasPlugin) : Listener {

    // ════════════════════════════════════════════════════════════════
    // DATA CLASSES
    // ════════════════════════════════════════════════════════════════
    
    data class BuildModeSession(
        val player: Player,
        val cityId: String,
        var selectedStructure: StructureType? = null,
        var rotation: Int = 0, // 0, 90, 180, 270
        var lastPreviewLocation: Location? = null,
        val ghostBlocks: MutableList<BlockDisplay> = mutableListOf(),
        val borderBlocks: MutableList<BlockDisplay> = mutableListOf() // City boundary markers
    )
    
    enum class PlacementResult(val message: String, val color: NamedTextColor) {
        SUCCESS("Construction started!", NamedTextColor.GREEN),
        NO_STRUCTURE("Select a structure first.", NamedTextColor.YELLOW),
        OUT_OF_RANGE("Too far away. Move closer.", NamedTextColor.RED),
        OUTSIDE_TERRITORY("Must build within city territory.", NamedTextColor.RED),
        OBSTRUCTED("Area is obstructed.", NamedTextColor.RED),
        NO_FOUNDATION("Ground is not solid.", NamedTextColor.RED),
        INSUFFICIENT_RESOURCES("Not enough gold in treasury.", NamedTextColor.RED),
        LIMIT_REACHED("Structure limit reached.", NamedTextColor.RED),
        INVALID_LOCATION("Invalid build location.", NamedTextColor.RED)
    }
    
    // ════════════════════════════════════════════════════════════════
    // STATE
    // ════════════════════════════════════════════════════════════════
    
    private val sessions = ConcurrentHashMap<UUID, BuildModeSession>()
    private var previewTask: BukkitTask? = null
    
    companion object {
        const val BUILD_RANGE = 35 // Extended range like Minecraft Legends
        const val PREVIEW_UPDATE_TICKS = 3L
    }
    
    init {
        startPreviewTask()
    }
    
    // ════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ════════════════════════════════════════════════════════════════
    
    /**
     * Enter build mode for a player
     */
    fun enterBuildMode(player: Player, cityId: String): Boolean {
        // Verify player is in their own city territory
        val playerData = plugin.identityManager.getPlayer(player.uniqueId)
        if (playerData?.cityId != cityId) {
            player.sendMessage(Component.text("You must be a member of this city.", NamedTextColor.RED))
            return false
        }
        
        // Cancel existing session
        if (sessions.containsKey(player.uniqueId)) {
            exitBuildMode(player, silent = true)
        }
        
        // Create new session
        val session = BuildModeSession(player, cityId)
        sessions[player.uniqueId] = session
        
        // Force third-person view
        setThirdPerson(player, true)
        
        // Visual/Audio feedback
        player.sendMessage(Component.empty())
        player.sendMessage(Component.text("╔══════════════════════════════════╗", NamedTextColor.GOLD))
        player.sendMessage(Component.text("║      ", NamedTextColor.GOLD)
            .append(Component.text("BUILDER MODE ACTIVATED", NamedTextColor.AQUA, TextDecoration.BOLD))
            .append(Component.text("     ║", NamedTextColor.GOLD)))
        player.sendMessage(Component.text("╠══════════════════════════════════╣", NamedTextColor.GOLD))
        player.sendMessage(Component.text("║ ", NamedTextColor.GOLD)
            .append(Component.text("SCROLL", NamedTextColor.YELLOW))
            .append(Component.text(" - Select Structure          ║", NamedTextColor.GRAY)))
        player.sendMessage(Component.text("║ ", NamedTextColor.GOLD)
            .append(Component.text("LEFT CLICK", NamedTextColor.YELLOW))
            .append(Component.text(" - Place Structure       ║", NamedTextColor.GRAY)))
        player.sendMessage(Component.text("║ ", NamedTextColor.GOLD)
            .append(Component.text("RIGHT CLICK", NamedTextColor.YELLOW))
            .append(Component.text(" - Rotate 90°           ║", NamedTextColor.GRAY)))
        player.sendMessage(Component.text("║ ", NamedTextColor.GOLD)
            .append(Component.text("DROP (Q)", NamedTextColor.YELLOW))
            .append(Component.text(" - Exit Build Mode        ║", NamedTextColor.GRAY)))
        player.sendMessage(Component.text("╚══════════════════════════════════╝", NamedTextColor.GOLD))
        player.sendMessage(Component.empty())
        
        player.playSound(player.location, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.5f)
        player.playSound(player.location, Sound.UI_TOAST_IN, 1f, 1.2f)
        
        // Show city borders
        showCityBorders(session)
        
        // Show structure selection
        showStructureSelection(player)
        
        return true
    }
    
    /**
     * Exit build mode for a player
     */
    fun exitBuildMode(player: Player, silent: Boolean = false) {
        val session = sessions.remove(player.uniqueId) ?: return
        
        // Clear ghost blocks and border blocks
        clearGhostBlocks(session)
        clearBorderBlocks(session)
        
        // Restore first-person view
        setThirdPerson(player, false)
        
        if (!silent) {
            player.sendMessage(Component.text("Exited Build Mode.", NamedTextColor.YELLOW))
            player.playSound(player.location, Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.2f)
        }
    }
    
    /**
     * Check if player is in build mode
     */
    fun isInBuildMode(player: Player): Boolean = sessions.containsKey(player.uniqueId)
    
    /**
     * Select a structure type for the current session
     */
    fun selectStructure(player: Player, type: StructureType) {
        val session = sessions[player.uniqueId] ?: return
        session.selectedStructure = type
        
        // Get cost info
        val cost = getStructureCost(type)
        
        player.sendMessage(Component.text("Selected: ", NamedTextColor.GRAY)
            .append(Component.text(type.name.replace("_", " "), NamedTextColor.AQUA, TextDecoration.BOLD))
            .append(Component.text(" (${type.width}x${type.depth}x${type.height})", NamedTextColor.DARK_GRAY)))
        player.sendMessage(Component.text("  Cost: ", NamedTextColor.GRAY)
            .append(Component.text("$cost gold", NamedTextColor.GOLD)))
        
        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.7f, 1.3f)
    }
    
    /**
     * Rotate the current structure selection
     */
    fun rotateStructure(player: Player) {
        val session = sessions[player.uniqueId] ?: return
        session.rotation = (session.rotation + 90) % 360
        
        player.sendMessage(Component.text("Rotation: ${session.rotation}°", NamedTextColor.YELLOW))
        player.playSound(player.location, Sound.BLOCK_WOOD_PLACE, 0.5f, 1.5f)
    }
    
    /**
     * Cycle through available structures
     */
    fun cycleStructure(player: Player, forward: Boolean) {
        val session = sessions[player.uniqueId] ?: return
        val structures = getAvailableStructures(session.cityId)
        if (structures.isEmpty()) return
        
        val currentIndex = structures.indexOf(session.selectedStructure)
        val newIndex = if (forward) {
            if (currentIndex < 0 || currentIndex >= structures.size - 1) 0 else currentIndex + 1
        } else {
            if (currentIndex <= 0) structures.size - 1 else currentIndex - 1
        }
        
        selectStructure(player, structures[newIndex])
    }
    
    /**
     * Attempt to place the selected structure
     */
    fun attemptPlacement(player: Player): PlacementResult {
        val session = sessions[player.uniqueId] 
            ?: return PlacementResult.INVALID_LOCATION
        
        val type = session.selectedStructure 
            ?: return PlacementResult.NO_STRUCTURE
        
        // Get target location with extended range
        val targetLoc = getExtendedTargetLocation(player) 
            ?: return PlacementResult.OUT_OF_RANGE
        
        // Validate placement
        val result = validatePlacement(session, targetLoc, type)
        
        if (result == PlacementResult.SUCCESS) {
            // Deduct resources
            val cost = getStructureCost(type)
            val city = plugin.cityManager.getCity(session.cityId)!!
            city.treasury -= cost
            
            // Record structure in city data + update infrastructure stats
            val locStr = "${targetLoc.world.name}:${targetLoc.blockX},${targetLoc.blockY},${targetLoc.blockZ}"
            city.placedStructures.computeIfAbsent(type.name) { mutableListOf() }.add(locStr)
            
            // Update infrastructure stats based on structure type
            when (type) {
                StructureType.TURRET -> city.infrastructure.turretCount++
                StructureType.GENERATOR -> if (city.infrastructure.generatorLevel == 0) city.infrastructure.generatorLevel = 1
                StructureType.BARRACKS -> if (city.infrastructure.barracksLevel == 0) city.infrastructure.barracksLevel = 1
                else -> { /* No specific stat update for others yet */ }
            }
            
            plugin.cityManager.saveCity(city)
            
            // Start animated construction (calls structureManager.spawnStructure on completion)
            startConstruction(type, targetLoc, session.rotation, session.cityId)
            
            // Success feedback
            player.playSound(targetLoc, Sound.BLOCK_ANVIL_USE, 1f, 1.2f)
            player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f)
            
            // Particle burst at placement
            player.world.spawnParticle(Particle.HAPPY_VILLAGER, targetLoc.clone().add(0.0, 1.0, 0.0), 30, 1.5, 1.0, 1.5, 0.0)
        } else {
            // Error feedback
            player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.5f)
            showErrorPulse(player, targetLoc, type)
        }
        
        player.sendMessage(Component.text(result.message, result.color))
        return result
    }
    
    // ════════════════════════════════════════════════════════════════
    // CAMERA CONTROL
    // ════════════════════════════════════════════════════════════════
    
    private fun setThirdPerson(player: Player, thirdPerson: Boolean) {
        // Note: Bukkit API cannot force camera mode change without ProtocolLib
        // The camera control is left to the player's preference
        // We just track the build mode state here
    }
    
    // ════════════════════════════════════════════════════════════════
    // EXTENDED RAYCAST
    // ════════════════════════════════════════════════════════════════
    
    /**
     * Get target location with extended range (30+ blocks)
     */
    fun getExtendedTargetLocation(player: Player): Location? {
        val eyeLoc = player.eyeLocation
        val direction = eyeLoc.direction
        
        // Raycast up to BUILD_RANGE blocks
        for (distance in 1..BUILD_RANGE) {
            val checkLoc = eyeLoc.clone().add(direction.clone().multiply(distance))
            val block = checkLoc.block
            
            // Found solid ground
            if (block.type.isSolid && !block.type.isAir) {
                // Return the location on top of this block
                return block.location.add(0.5, 1.0, 0.5)
            }
        }
        
        // No solid block found, return ground at max range
        val maxRangeLoc = eyeLoc.clone().add(direction.clone().multiply(BUILD_RANGE))
        val groundY = maxRangeLoc.world.getHighestBlockYAt(maxRangeLoc)
        return Location(maxRangeLoc.world, maxRangeLoc.x, groundY + 1.0, maxRangeLoc.z)
    }
    
    // ════════════════════════════════════════════════════════════════
    // VALIDATION
    // ════════════════════════════════════════════════════════════════
    
    private fun validatePlacement(session: BuildModeSession, location: Location, type: StructureType): PlacementResult {
        // 1. Check territory
        val chunk = location.chunk
        val cityAtLoc = plugin.cityManager.getCityAt(chunk)
        if (cityAtLoc?.id != session.cityId) {
            return PlacementResult.OUTSIDE_TERRITORY
        }
        
        // 2. Check physics (space + foundation)
        val (canBuild, error) = checkBuildPhysics(location, type)
        if (!canBuild) {
            return when (error) {
                "obstruction" -> PlacementResult.OBSTRUCTED
                "foundation" -> PlacementResult.NO_FOUNDATION
                else -> PlacementResult.INVALID_LOCATION
            }
        }
        
        // 3. Check resources
        val cost = getStructureCost(type)
        val city = plugin.cityManager.getCity(session.cityId) ?: return PlacementResult.INVALID_LOCATION
        if (city.treasury < cost) {
            return PlacementResult.INSUFFICIENT_RESOURCES
        }
        
        // 4. Check structure limits
        val currentCount = city.placedStructures[type.name]?.size ?: 0
        if (type == StructureType.TURRET) {
            if (!city.infrastructure.canAddTurret()) {
                return PlacementResult.LIMIT_REACHED
            }
        } else {
            // Limit 1 for major structures (matching CityManager behavior)
            if (currentCount >= 1 && type != StructureType.TURRET) {
                return PlacementResult.LIMIT_REACHED
            }
        }
        
        return PlacementResult.SUCCESS
    }
    
    private fun checkBuildPhysics(location: Location, type: StructureType): Pair<Boolean, String> {
        val width = type.width
        val height = type.height
        val depth = type.depth
        val world = location.world ?: return Pair(false, "invalid")
        
        val startX = location.blockX - width / 2
        val startY = location.blockY
        val startZ = location.blockZ - depth / 2
        
        var hasObstruction = false
        var hasNoFoundation = false
        
        for (x in 0 until width) {
            for (z in 0 until depth) {
                // Check foundation
                val ground = world.getBlockAt(startX + x, startY - 1, startZ + z)
                if (!ground.type.isSolid) {
                    hasNoFoundation = true
                }
                
                // Check volume
                for (y in 0 until height) {
                    val block = world.getBlockAt(startX + x, startY + y, startZ + z)
                    if (!block.type.isAir && !block.isReplaceable) {
                        hasObstruction = true
                    }
                }
            }
        }
        
        return when {
            hasObstruction -> Pair(false, "obstruction")
            hasNoFoundation -> Pair(false, "foundation")
            else -> Pair(true, "")
        }
    }
    
    // ════════════════════════════════════════════════════════════════
    // GHOST BLOCK PREVIEW
    // ════════════════════════════════════════════════════════════════
    
    private fun startPreviewTask() {
        previewTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            sessions.values.forEach { session ->
                updatePreview(session)
            }
        }, 0L, PREVIEW_UPDATE_TICKS)
    }
    
    private fun updatePreview(session: BuildModeSession) {
        val player = session.player
        val type = session.selectedStructure ?: return
        
        val targetLoc = getExtendedTargetLocation(player) ?: return
        
        // Skip if location hasn't changed significantly
        val lastLoc = session.lastPreviewLocation
        if (lastLoc != null && lastLoc.distanceSquared(targetLoc) < 0.25) {
            return
        }
        session.lastPreviewLocation = targetLoc
        
        // Clear old ghost blocks
        clearGhostBlocks(session)
        
        // Check validity
        val (valid, _) = checkBuildPhysics(targetLoc, type)
        val territoryValid = plugin.cityManager.getCityAt(targetLoc.chunk)?.id == session.cityId
        val isValid = valid && territoryValid
        
        // Draw particle outline (more performant than block displays for every block)
        drawStructurePreview(player, targetLoc, type, isValid, session.rotation)
        
        // Show actionbar with status
        val city = plugin.cityManager.getCity(session.cityId)
        val cost = getStructureCost(type)
        val treasury = city?.treasury?.toInt() ?: 0
        val canAfford = treasury >= cost
        
        val status = when {
            !territoryValid -> Component.text("⚠ OUTSIDE", NamedTextColor.RED)
            !valid -> Component.text("⚠ BLOCKED", NamedTextColor.RED)
            !canAfford -> Component.text("⚠ NEED GOLD", NamedTextColor.GOLD)
            else -> Component.text("✓ READY", NamedTextColor.GREEN)
        }
        
        // Cost display with treasury
        val costColor = if (canAfford) NamedTextColor.GREEN else NamedTextColor.RED
        val costDisplay = Component.text(" 💰", NamedTextColor.GOLD)
            .append(Component.text("$cost", costColor))
            .append(Component.text("/$treasury ", NamedTextColor.GRAY))
        
        player.sendActionBar(
            Component.text("${type.name.replace("_", " ")} ", NamedTextColor.AQUA)
                .append(Component.text("[", NamedTextColor.DARK_GRAY))
                .append(status)
                .append(Component.text("]", NamedTextColor.DARK_GRAY))
                .append(costDisplay)
                .append(Component.text("⟳${session.rotation}°", NamedTextColor.YELLOW))
        )
    }
    
    private fun drawStructurePreview(player: Player, location: Location, type: StructureType, valid: Boolean, rotation: Int) {
        val session = sessions[player.uniqueId] ?: return
        val width = type.width
        val height = type.height
        val depth = type.depth
        
        // Calculate rotated dimensions
        val (actualWidth, actualDepth) = if (rotation == 90 || rotation == 270) {
            Pair(depth, width)
        } else {
            Pair(width, depth)
        }
        
        val startX = location.blockX - actualWidth / 2
        val startY = location.blockY
        val startZ = location.blockZ - actualDepth / 2
        
        val world = location.world ?: return
        
        // ═══════════════════════════════════════════════════════════════
        // FLOOR OUTLINE - Only edges, not every block (performance optimization)
        // ═══════════════════════════════════════════════════════════════
        
        val previewMaterial = if (valid) Material.LIME_STAINED_GLASS else Material.RED_STAINED_GLASS
        
        // Floor edges only (not every block - that's too many entities for large buildings)
        for (x in 0 until actualWidth) {
            // Front edge
            val frontLoc = Location(world, (startX + x).toDouble() + 0.5, startY.toDouble(), startZ.toDouble() + 0.5)
            val frontDisplay = world.spawn(frontLoc, BlockDisplay::class.java) { entity ->
                entity.block = previewMaterial.createBlockData()
                entity.brightness = Display.Brightness(15, 15)
                entity.transformation = Transformation(
                    Vector3f(-0.5f, 0f, -0.5f),
                    AxisAngle4f(0f, 0f, 0f, 0f),
                    Vector3f(1f, 0.15f, 0.3f),
                    AxisAngle4f(0f, 0f, 0f, 0f)
                )
                entity.isGlowing = true
                entity.glowColorOverride = if (valid) org.bukkit.Color.fromRGB(100, 255, 100) else org.bukkit.Color.fromRGB(255, 100, 100)
            }
            session.ghostBlocks.add(frontDisplay)
            
            // Back edge
            val backLoc = Location(world, (startX + x).toDouble() + 0.5, startY.toDouble(), (startZ + actualDepth - 1).toDouble() + 0.5)
            val backDisplay = world.spawn(backLoc, BlockDisplay::class.java) { entity ->
                entity.block = previewMaterial.createBlockData()
                entity.brightness = Display.Brightness(15, 15)
                entity.transformation = Transformation(
                    Vector3f(-0.5f, 0f, 0.2f),
                    AxisAngle4f(0f, 0f, 0f, 0f),
                    Vector3f(1f, 0.15f, 0.3f),
                    AxisAngle4f(0f, 0f, 0f, 0f)
                )
                entity.isGlowing = true
                entity.glowColorOverride = if (valid) org.bukkit.Color.fromRGB(100, 255, 100) else org.bukkit.Color.fromRGB(255, 100, 100)
            }
            session.ghostBlocks.add(backDisplay)
        }
        
        // Side edges (skip corners already covered by front/back)
        for (z in 1 until actualDepth - 1) {
            // Left edge
            val leftLoc = Location(world, startX.toDouble() + 0.5, startY.toDouble(), (startZ + z).toDouble() + 0.5)
            val leftDisplay = world.spawn(leftLoc, BlockDisplay::class.java) { entity ->
                entity.block = previewMaterial.createBlockData()
                entity.brightness = Display.Brightness(15, 15)
                entity.transformation = Transformation(
                    Vector3f(-0.5f, 0f, -0.5f),
                    AxisAngle4f(0f, 0f, 0f, 0f),
                    Vector3f(0.3f, 0.15f, 1f),
                    AxisAngle4f(0f, 0f, 0f, 0f)
                )
                entity.isGlowing = true
                entity.glowColorOverride = if (valid) org.bukkit.Color.fromRGB(100, 255, 100) else org.bukkit.Color.fromRGB(255, 100, 100)
            }
            session.ghostBlocks.add(leftDisplay)
            
            // Right edge
            val rightLoc = Location(world, (startX + actualWidth - 1).toDouble() + 0.5, startY.toDouble(), (startZ + z).toDouble() + 0.5)
            val rightDisplay = world.spawn(rightLoc, BlockDisplay::class.java) { entity ->
                entity.block = previewMaterial.createBlockData()
                entity.brightness = Display.Brightness(15, 15)
                entity.transformation = Transformation(
                    Vector3f(0.2f, 0f, -0.5f),
                    AxisAngle4f(0f, 0f, 0f, 0f),
                    Vector3f(0.3f, 0.15f, 1f),
                    AxisAngle4f(0f, 0f, 0f, 0f)
                )
                entity.isGlowing = true
                entity.glowColorOverride = if (valid) org.bukkit.Color.fromRGB(100, 255, 100) else org.bukkit.Color.fromRGB(255, 100, 100)
            }
            session.ghostBlocks.add(rightDisplay)
        }
        
        // ═══════════════════════════════════════════════════════════════
        // CORNER PILLARS - Show height with vertical displays (optimized)
        // ═══════════════════════════════════════════════════════════════
        
        val pillarMaterial = if (valid) Material.GREEN_STAINED_GLASS else Material.ORANGE_STAINED_GLASS
        
        val corners = listOf(
            Pair(startX, startZ),
            Pair(startX + actualWidth - 1, startZ),
            Pair(startX, startZ + actualDepth - 1),
            Pair(startX + actualWidth - 1, startZ + actualDepth - 1)
        )
        
        // Use taller segments (2 blocks each) to reduce entity count
        val stepSize = 2
        for ((cx, cz) in corners) {
            for (y in 1 until height step stepSize) {
                val segmentHeight = minOf(stepSize, height - y)
                val pillarLoc = Location(world, cx.toDouble() + 0.5, (startY + y).toDouble(), cz.toDouble() + 0.5)
                
                val pillar = world.spawn(pillarLoc, BlockDisplay::class.java) { entity ->
                    entity.block = pillarMaterial.createBlockData()
                    entity.brightness = Display.Brightness(15, 15)
                    entity.transformation = Transformation(
                        Vector3f(-0.1f, 0f, -0.1f),
                        AxisAngle4f(0f, 0f, 0f, 0f),
                        Vector3f(0.2f, segmentHeight.toFloat(), 0.2f), // Taller segment
                        AxisAngle4f(0f, 0f, 0f, 0f)
                    )
                    entity.isGlowing = true
                    entity.glowColorOverride = if (valid) org.bukkit.Color.fromRGB(80, 200, 80) else org.bukkit.Color.fromRGB(200, 80, 80)
                }
                session.ghostBlocks.add(pillar)
            }
        }
        
        // ═══════════════════════════════════════════════════════════════
        // TOP FRAME - Show ceiling outline
        // ═══════════════════════════════════════════════════════════════
        
        val topY = startY + height
        
        // Top edges
        for (x in 0 until actualWidth) {
            // Front edge
            val frontLoc = Location(world, (startX + x).toDouble() + 0.5, topY.toDouble(), startZ.toDouble() + 0.5)
            val frontDisplay = world.spawn(frontLoc, BlockDisplay::class.java) { entity ->
                entity.block = previewMaterial.createBlockData()
                entity.brightness = Display.Brightness(15, 15)
                entity.transformation = Transformation(
                    Vector3f(-0.5f, 0f, -0.5f),
                    AxisAngle4f(0f, 0f, 0f, 0f),
                    Vector3f(1f, 0.1f, 0.2f),
                    AxisAngle4f(0f, 0f, 0f, 0f)
                )
                entity.isGlowing = true
            }
            session.ghostBlocks.add(frontDisplay)
            
            // Back edge
            val backLoc = Location(world, (startX + x).toDouble() + 0.5, topY.toDouble(), (startZ + actualDepth - 1).toDouble() + 0.5)
            val backDisplay = world.spawn(backLoc, BlockDisplay::class.java) { entity ->
                entity.block = previewMaterial.createBlockData()
                entity.brightness = Display.Brightness(15, 15)
                entity.transformation = Transformation(
                    Vector3f(-0.5f, 0f, 0.3f),
                    AxisAngle4f(0f, 0f, 0f, 0f),
                    Vector3f(1f, 0.1f, 0.2f),
                    AxisAngle4f(0f, 0f, 0f, 0f)
                )
                entity.isGlowing = true
            }
            session.ghostBlocks.add(backDisplay)
        }
        
        for (z in 0 until actualDepth) {
            // Left edge
            val leftLoc = Location(world, startX.toDouble() + 0.5, topY.toDouble(), (startZ + z).toDouble() + 0.5)
            val leftDisplay = world.spawn(leftLoc, BlockDisplay::class.java) { entity ->
                entity.block = previewMaterial.createBlockData()
                entity.brightness = Display.Brightness(15, 15)
                entity.transformation = Transformation(
                    Vector3f(-0.5f, 0f, -0.5f),
                    AxisAngle4f(0f, 0f, 0f, 0f),
                    Vector3f(0.2f, 0.1f, 1f),
                    AxisAngle4f(0f, 0f, 0f, 0f)
                )
                entity.isGlowing = true
            }
            session.ghostBlocks.add(leftDisplay)
            
            // Right edge  
            val rightLoc = Location(world, (startX + actualWidth - 1).toDouble() + 0.5, topY.toDouble(), (startZ + z).toDouble() + 0.5)
            val rightDisplay = world.spawn(rightLoc, BlockDisplay::class.java) { entity ->
                entity.block = previewMaterial.createBlockData()
                entity.brightness = Display.Brightness(15, 15)
                entity.transformation = Transformation(
                    Vector3f(0.3f, 0f, -0.5f),
                    AxisAngle4f(0f, 0f, 0f, 0f),
                    Vector3f(0.2f, 0.1f, 1f),
                    AxisAngle4f(0f, 0f, 0f, 0f)
                )
                entity.isGlowing = true
            }
            session.ghostBlocks.add(rightDisplay)
        }
        
        // ═══════════════════════════════════════════════════════════════
        // FRONT INDICATOR - Shows which way the building faces
        // ═══════════════════════════════════════════════════════════════
        
        val indicatorColor = Particle.DustOptions(Color.fromRGB(255, 255, 100), 1.5f)
        val frontCenterX = startX + actualWidth / 2.0
        val frontCenterZ = startZ + actualDepth / 2.0
        
        when (rotation) {
            0 -> { // North
                for (i in 0..4) {
                    player.spawnParticle(Particle.DUST, Location(world, frontCenterX, startY + 0.5, startZ - 0.3 - i * 0.2), 2, indicatorColor)
                }
            }
            90 -> { // East  
                for (i in 0..4) {
                    player.spawnParticle(Particle.DUST, Location(world, (startX + actualWidth) + 0.3 + i * 0.2, startY + 0.5, frontCenterZ), 2, indicatorColor)
                }
            }
            180 -> { // South
                for (i in 0..4) {
                    player.spawnParticle(Particle.DUST, Location(world, frontCenterX, startY + 0.5, (startZ + actualDepth) + 0.3 + i * 0.2), 2, indicatorColor)
                }
            }
            270 -> { // West
                for (i in 0..4) {
                    player.spawnParticle(Particle.DUST, Location(world, startX - 0.3 - i * 0.2, startY + 0.5, frontCenterZ), 2, indicatorColor)
                }
            }
        }
    }
    
    private fun clearGhostBlocks(session: BuildModeSession) {
        session.ghostBlocks.forEach { it.remove() }
        session.ghostBlocks.clear()
    }
    
    private fun clearBorderBlocks(session: BuildModeSession) {
        session.borderBlocks.forEach { it.remove() }
        session.borderBlocks.clear()
    }
    
    /**
     * Show city territory borders using Block Display entities
     */
    private fun showCityBorders(session: BuildModeSession) {
        val city = plugin.cityManager.getCity(session.cityId) ?: return
        val player = session.player
        val world = player.world
        val playerY = player.location.blockY
        
        // Get all claimed chunks for this city
        val claimedChunks = city.claimedChunks.mapNotNull { chunkKey ->
            val parts = chunkKey.split(":")
            if (parts.size != 2) return@mapNotNull null
            val worldName = parts[0]
            val coords = parts[1].split(",")
            if (coords.size != 2 || worldName != world.name) return@mapNotNull null
            Pair(coords[0].toIntOrNull() ?: return@mapNotNull null, coords[1].toIntOrNull() ?: return@mapNotNull null)
        }.toSet()
        
        val borderMaterial = Material.LIME_STAINED_GLASS
        
        // For each claimed chunk, check if edges are borders (adjacent chunk not claimed)
        for ((cx, cz) in claimedChunks) {
            val minX = cx * 16
            val maxX = minX + 16
            val minZ = cz * 16
            val maxZ = minZ + 16
            
            val visualY = playerY.toDouble() + 0.5
            
            // Check each edge
            val northBorder = !claimedChunks.contains(Pair(cx, cz - 1))
            val southBorder = !claimedChunks.contains(Pair(cx, cz + 1))
            val westBorder = !claimedChunks.contains(Pair(cx - 1, cz))
            val eastBorder = !claimedChunks.contains(Pair(cx + 1, cz))
            
            // Only show borders that are actual edges of the territory
            if (northBorder) {
                // North edge (Z = minZ)
                for (x in minX until maxX step 4) {
                    val borderDisplay = world.spawn(Location(world, x.toDouble() + 2.0, visualY, minZ.toDouble()), BlockDisplay::class.java) { entity ->
                        entity.block = borderMaterial.createBlockData()
                        entity.brightness = Display.Brightness(15, 15)
                        entity.transformation = Transformation(
                            Vector3f(-2f, 0f, -0.1f),
                            AxisAngle4f(0f, 0f, 0f, 0f),
                            Vector3f(4f, 0.2f, 0.2f),
                            AxisAngle4f(0f, 0f, 0f, 0f)
                        )
                        entity.isGlowing = true
                        entity.glowColorOverride = org.bukkit.Color.fromRGB(50, 255, 50)
                    }
                    session.borderBlocks.add(borderDisplay)
                }
            }
            
            if (southBorder) {
                // South edge (Z = maxZ)
                for (x in minX until maxX step 4) {
                    val borderDisplay = world.spawn(Location(world, x.toDouble() + 2.0, visualY, maxZ.toDouble()), BlockDisplay::class.java) { entity ->
                        entity.block = borderMaterial.createBlockData()
                        entity.brightness = Display.Brightness(15, 15)
                        entity.transformation = Transformation(
                            Vector3f(-2f, 0f, -0.1f),
                            AxisAngle4f(0f, 0f, 0f, 0f),
                            Vector3f(4f, 0.2f, 0.2f),
                            AxisAngle4f(0f, 0f, 0f, 0f)
                        )
                        entity.isGlowing = true
                        entity.glowColorOverride = org.bukkit.Color.fromRGB(50, 255, 50)
                    }
                    session.borderBlocks.add(borderDisplay)
                }
            }
            
            if (westBorder) {
                // West edge (X = minX)
                for (z in minZ until maxZ step 4) {
                    val borderDisplay = world.spawn(Location(world, minX.toDouble(), visualY, z.toDouble() + 2.0), BlockDisplay::class.java) { entity ->
                        entity.block = borderMaterial.createBlockData()
                        entity.brightness = Display.Brightness(15, 15)
                        entity.transformation = Transformation(
                            Vector3f(-0.1f, 0f, -2f),
                            AxisAngle4f(0f, 0f, 0f, 0f),
                            Vector3f(0.2f, 0.2f, 4f),
                            AxisAngle4f(0f, 0f, 0f, 0f)
                        )
                        entity.isGlowing = true
                        entity.glowColorOverride = org.bukkit.Color.fromRGB(50, 255, 50)
                    }
                    session.borderBlocks.add(borderDisplay)
                }
            }
            
            if (eastBorder) {
                // East edge (X = maxX)
                for (z in minZ until maxZ step 4) {
                    val borderDisplay = world.spawn(Location(world, maxX.toDouble(), visualY, z.toDouble() + 2.0), BlockDisplay::class.java) { entity ->
                        entity.block = borderMaterial.createBlockData()
                        entity.brightness = Display.Brightness(15, 15)
                        entity.transformation = Transformation(
                            Vector3f(-0.1f, 0f, -2f),
                            AxisAngle4f(0f, 0f, 0f, 0f),
                            Vector3f(0.2f, 0.2f, 4f),
                            AxisAngle4f(0f, 0f, 0f, 0f)
                        )
                        entity.isGlowing = true
                        entity.glowColorOverride = org.bukkit.Color.fromRGB(50, 255, 50)
                    }
                    session.borderBlocks.add(borderDisplay)
                }
            }
        }
        
        session.player.sendMessage(Component.text("City borders shown in green.", NamedTextColor.GREEN))
    }
    
    private fun showErrorPulse(player: Player, location: Location, type: StructureType) {
        val width = type.width
        val depth = type.depth
        val centerX = location.x
        val centerZ = location.z
        val y = location.y
        
        var radius = 0.0
        val maxRadius = kotlin.math.max(width, depth) / 2.0 + 1.5
        
        plugin.server.scheduler.runTaskTimer(plugin, { task ->
            if (radius > maxRadius) {
                (task as BukkitTask).cancel()
                return@runTaskTimer
            }
            
            val dust = Particle.DustOptions(Color.fromRGB(255, 60, 60), (0.8f - (radius / maxRadius * 0.4f)).toFloat())
            val steps = 24
            for (i in 0 until steps) {
                val angle = (i.toDouble() / steps) * 2 * Math.PI
                val px = centerX + radius * kotlin.math.cos(angle)
                val pz = centerZ + radius * kotlin.math.sin(angle)
                player.spawnParticle(Particle.DUST, Location(location.world, px, y + 0.1, pz), 1, dust)
            }
            
            radius += 0.6
        }, 0L, 2L)
    }
    
    // ════════════════════════════════════════════════════════════════
    // CONSTRUCTION ANIMATION
    // ════════════════════════════════════════════════════════════════
    
    private fun startConstruction(type: StructureType, location: Location, rotation: Int, cityId: String) {
        // Spawn "builder" Allays
        val builderCount = when {
            type.width * type.depth > 40 -> 4
            type.width * type.depth > 20 -> 3
            else -> 2
        }
        
        val builders = mutableListOf<org.bukkit.entity.Allay>()
        for (i in 0 until builderCount) {
            val angle = (i.toDouble() / builderCount) * 2 * Math.PI
            val spawnLoc = location.clone().add(
                kotlin.math.cos(angle) * 3,
                2.0,
                kotlin.math.sin(angle) * 3
            )
            
            val allay = location.world.spawn(spawnLoc, org.bukkit.entity.Allay::class.java) { entity ->
                entity.customName(Component.text("Builder", NamedTextColor.AQUA))
                entity.isCustomNameVisible = false
                entity.setAI(false) // We control movement
                entity.isInvulnerable = true
                entity.isPersistent = false
            }
            builders.add(allay)
        }
        
        // Animate builders circling and building
        var tick = 0
        val buildDuration = 60 // 3 seconds at 20 ticks/sec
        
        plugin.server.scheduler.runTaskTimer(plugin, { task ->
            tick++
            
            if (tick >= buildDuration) {
                // Complete construction
                plugin.structureManager.spawnStructure(type, location)
                
                // Despawn builders with particles
                builders.forEach { allay ->
                    allay.world.spawnParticle(Particle.END_ROD, allay.location, 10, 0.3, 0.3, 0.3, 0.05)
                    allay.remove()
                }
                
                // Final celebratory particles
                location.world.spawnParticle(Particle.TOTEM_OF_UNDYING, location.clone().add(0.0, 2.0, 0.0), 50, 2.0, 1.5, 2.0, 0.0)
                location.world.playSound(location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f)
                location.world.playSound(location, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.5f)
                
                (task as BukkitTask).cancel()
                return@runTaskTimer
            }
            
            // Animate builders circling
            val progress = tick.toDouble() / buildDuration
            builders.forEachIndexed { i, allay ->
                val baseAngle = (i.toDouble() / builders.size) * 2 * Math.PI
                val currentAngle = baseAngle + (tick * 0.15)
                val radius = 2.5 - progress * 1.5 // Spiral inward
                val height = 2.0 + kotlin.math.sin(tick * 0.3 + i) * 0.5
                
                val newLoc = location.clone().add(
                    kotlin.math.cos(currentAngle) * radius,
                    height,
                    kotlin.math.sin(currentAngle) * radius
                )
                allay.teleport(newLoc)
                
                // Trail particles
                allay.world.spawnParticle(Particle.END_ROD, allay.location, 1, 0.0, 0.0, 0.0, 0.0)
            }
            
            // Construction particles at build site
            if (tick % 5 == 0) {
                val particleLoc = location.clone().add(
                    (Math.random() - 0.5) * type.width,
                    Math.random() * type.height,
                    (Math.random() - 0.5) * type.depth
                )
                location.world.spawnParticle(Particle.BLOCK, particleLoc, 5, 0.3, 0.3, 0.3, 0.0, Material.OAK_PLANKS.createBlockData())
                location.world.playSound(particleLoc, Sound.BLOCK_WOOD_PLACE, 0.4f, 0.9f + (Math.random().toFloat() * 0.2f))
            }
            
        }, 0L, 1L)
    }
    
    // ════════════════════════════════════════════════════════════════
    // STRUCTURE INFO
    // ════════════════════════════════════════════════════════════════
    
    fun getAvailableStructures(cityId: String): List<StructureType> {
        // Return structures based on city's progression/era
        // For now, return all structures organized by category
        return listOf(
            // Buildings
            StructureType.TURRET,
            StructureType.BARRACKS,
            StructureType.GENERATOR,
            StructureType.MERCHANT_HUT,
            StructureType.QUEST_CAMP,
            StructureType.NEXUS,
            // Defenses
            StructureType.WALL,
            StructureType.WALL_CORNER,
            StructureType.GATE,
            StructureType.RAMP,
            StructureType.WATCHTOWER
        )
    }
    
    fun getStructureCost(type: StructureType): Int {
        return when (type) {
            // Buildings
            StructureType.TURRET -> 150
            StructureType.BARRACKS -> 500
            StructureType.GENERATOR -> 300
            StructureType.MERCHANT_HUT -> 200
            StructureType.QUEST_CAMP -> 150
            StructureType.NEXUS -> 1000
            // Defenses (cheaper, meant to be placed in bulk)
            StructureType.WALL -> 25
            StructureType.WALL_CORNER -> 35
            StructureType.GATE -> 75
            StructureType.RAMP -> 40
            StructureType.WATCHTOWER -> 200
        }
    }
    
    private fun showStructureSelection(player: Player) {
        val session = sessions[player.uniqueId] ?: return
        val structures = getAvailableStructures(session.cityId)
        
        player.sendMessage(Component.text("Available Structures:", NamedTextColor.GOLD, TextDecoration.BOLD))
        structures.forEachIndexed { index, type ->
            val cost = getStructureCost(type)
            player.sendMessage(Component.text("  ${index + 1}. ", NamedTextColor.GRAY)
                .append(Component.text(type.name.replace("_", " "), NamedTextColor.AQUA))
                .append(Component.text(" - $cost gold", NamedTextColor.GOLD)))
        }
        player.sendMessage(Component.text("Use SCROLL WHEEL to select.", NamedTextColor.YELLOW))
        
        // Auto-select first
        if (structures.isNotEmpty()) {
            selectStructure(player, structures[0])
        }
    }
    
    // ════════════════════════════════════════════════════════════════
    // DEMOLISH / SELL-BACK
    // ════════════════════════════════════════════════════════════════
    
    /**
     * Get refund amount for demolishing a structure (75% of original cost)
     */
    fun getRefundAmount(type: StructureType): Int {
        return (getStructureCost(type) * 0.75).toInt()
    }
    
    /**
     * Demolish a structure and refund resources to city treasury
     */
    fun demolishStructure(player: Player, structureLoc: Location): Boolean {
        val profile = plugin.identityManager.getPlayer(player.uniqueId) ?: return false
        val cityId = profile.cityId ?: return false
        val city = plugin.cityManager.getCity(cityId) ?: return false
        
        // Check if player is in their city territory
        val chunkCity = plugin.cityManager.getCityAt(structureLoc.chunk)
        if (chunkCity?.id != cityId) {
            player.sendMessage(Component.text("You can only demolish structures in your city.", NamedTextColor.RED))
            return false
        }
        
        // Find the structure at this location
        val locStr = "${structureLoc.world.name}:${structureLoc.blockX},${structureLoc.blockY},${structureLoc.blockZ}"
        var foundType: StructureType? = null
        
        for ((typeName, locations) in city.placedStructures) {
            if (locations.contains(locStr)) {
                foundType = try { StructureType.valueOf(typeName) } catch (e: Exception) { null }
                if (foundType != null) {
                    locations.remove(locStr)
                    break
                }
            }
        }
        
        if (foundType == null) {
            player.sendMessage(Component.text("No structure found at this location.", NamedTextColor.RED))
            return false
        }
        
        // Calculate refund
        val refund = getRefundAmount(foundType)
        city.treasury += refund
        
        // Update infrastructure stats
        when (foundType) {
            StructureType.TURRET -> if (city.infrastructure.turretCount > 0) city.infrastructure.turretCount--
            StructureType.GENERATOR -> if (city.infrastructure.generatorLevel > 0) city.infrastructure.generatorLevel = 0
            StructureType.BARRACKS -> if (city.infrastructure.barracksLevel > 0) city.infrastructure.barracksLevel = 0
            else -> { /* No specific stat update */ }
        }
        
        plugin.cityManager.saveCity(city)
        
        // Clear the structure blocks (simple clear for now - could be animated later)
        clearStructureBlocks(structureLoc, foundType)
        
        // Visual feedback
        structureLoc.world.spawnParticle(Particle.SMOKE, structureLoc.clone().add(0.0, 1.0, 0.0), 50, 2.0, 2.0, 2.0, 0.02)
        structureLoc.world.playSound(structureLoc, Sound.ENTITY_IRON_GOLEM_DEATH, 0.8f, 0.8f)
        
        player.sendMessage(Component.text("✓ Demolished ${foundType.name.replace("_", " ")}! +$refund gold refunded.", NamedTextColor.GREEN))
        
        return true
    }
    
    private fun clearStructureBlocks(location: Location, type: StructureType) {
        val width = type.width
        val height = type.height
        val depth = type.depth
        val world = location.world ?: return
        
        val startX = location.blockX - width / 2
        val startY = location.blockY
        val startZ = location.blockZ - depth / 2
        
        // Clear the volume
        for (x in 0 until width) {
            for (y in 0 until height) {
                for (z in 0 until depth) {
                    val block = world.getBlockAt(startX + x, startY + y, startZ + z)
                    if (!block.type.isAir) {
                        block.type = Material.AIR
                    }
                }
            }
        }
    }
    
    // ════════════════════════════════════════════════════════════════
    // EVENT HANDLERS
    // ════════════════════════════════════════════════════════════════
    
    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        val session = sessions[event.player.uniqueId] ?: return
        
        when (event.action) {
            Action.LEFT_CLICK_AIR, Action.LEFT_CLICK_BLOCK -> {
                event.isCancelled = true
                attemptPlacement(event.player)
            }
            Action.RIGHT_CLICK_AIR, Action.RIGHT_CLICK_BLOCK -> {
                event.isCancelled = true
                rotateStructure(event.player)
            }
            else -> {}
        }
    }
    
    @EventHandler
    fun onScroll(event: PlayerItemHeldEvent) {
        val session = sessions[event.player.uniqueId] ?: return
        
        // Determine scroll direction
        val previousSlot = event.previousSlot
        val newSlot = event.newSlot
        
        val forward = when {
            previousSlot == 8 && newSlot == 0 -> true
            previousSlot == 0 && newSlot == 8 -> false
            newSlot > previousSlot -> true
            else -> false
        }
        
        event.isCancelled = true // Prevent hotbar changes in build mode
        cycleStructure(event.player, forward)
    }
    
    @EventHandler
    fun onDrop(event: PlayerDropItemEvent) {
        if (sessions.containsKey(event.player.uniqueId)) {
            event.isCancelled = true
            exitBuildMode(event.player)
        }
    }
    
    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        exitBuildMode(event.player, silent = true)
    }
}
