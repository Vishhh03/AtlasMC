package com.projectatlas.visual

import com.projectatlas.AtlasPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Transformation
import org.joml.AxisAngle4f
import org.joml.Vector3f
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Display Entity Manager - Leverages 1.21's native Display Entities for all visual effects.
 * 
 * Native Features Used:
 * - TextDisplay: Holograms, Damage Numbers, Combo Counters, Boss HP Bars
 * - ItemDisplay: Floating item previews, loot indicators
 * - BlockDisplay: Ghost blocks for schematics, territory borders
 * - Interpolation: Smooth animations via transformation lerping
 * 
 * Benefits over ProtocolLib/ArmorStands:
 * - No packet manipulation required
 * - Smooth client-side interpolation (60fps animations)
 * - Lower bandwidth (transformations = tiny packets)
 * - Full RGB color support with gradients
 */
class PacketManager(private val plugin: AtlasPlugin) {

    // Tracked displays per player (for cleanup)
    private val playerDisplays = ConcurrentHashMap<UUID, MutableList<UUID>>()
    
    // Global displays (world holograms, boss bars)
    private val globalDisplays = ConcurrentHashMap<String, UUID>()

    fun isAvailable(): Boolean = true

    // ═══════════════════════════════════════════════════════════════
    // TEXT DISPLAY - HOLOGRAMS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Create a floating text hologram visible to all players.
     * @param location Where to spawn it
     * @param text The text content
     * @param scale Size multiplier (default 1.0)
     * @param backgroundColor Background color with alpha (null = transparent)
     * @return Entity UUID for later manipulation
     */
    fun createHologram(
        location: Location,
        text: Component,
        scale: Float = 1.0f,
        backgroundColor: Color? = null
    ): UUID? {
        return try {
            val display = location.world.spawn(location, TextDisplay::class.java) { td ->
                td.text(text)
                td.billboard = Display.Billboard.CENTER
                td.isShadowed = true
                td.backgroundColor = backgroundColor ?: Color.fromARGB(0, 0, 0, 0)
                td.alignment = TextDisplay.TextAlignment.CENTER
                
                // Apply scale
                td.transformation = Transformation(
                    Vector3f(0f, 0f, 0f),
                    AxisAngle4f(0f, 0f, 0f, 1f),
                    Vector3f(scale, scale, scale),
                    AxisAngle4f(0f, 0f, 0f, 1f)
                )
            }
            display.uniqueId
        } catch (e: Exception) {
            plugin.logger.warning("Failed to spawn TextDisplay: ${e.message}")
            null
        }
    }

    /**
     * Create a hologram visible only to one player.
     * Uses hideEntity API to hide from others.
     */
    fun createPlayerHologram(
        location: Location,
        text: Component,
        viewer: Player,
        scale: Float = 1.0f
    ): UUID? {
        val uuid = createHologram(location, text, scale) ?: return null
        val entity = plugin.server.getEntity(uuid) as? TextDisplay ?: return null

        // Hide from all other players
        plugin.server.onlinePlayers.forEach { other ->
            if (other.uniqueId != viewer.uniqueId) {
                other.hideEntity(plugin, entity)
            }
        }
        
        // Track for cleanup
        playerDisplays.getOrPut(viewer.uniqueId) { mutableListOf() }.add(uuid)
        return uuid
    }

    /**
     * Create a multi-line hologram with individual line control.
     */
    fun createMultiLineHologram(
        location: Location,
        lines: List<Component>,
        lineSpacing: Float = 0.3f,
        scale: Float = 1.0f
    ): List<UUID> {
        val uuids = mutableListOf<UUID>()
        lines.forEachIndexed { index, line ->
            val lineLoc = location.clone().add(0.0, (lines.size - 1 - index) * lineSpacing.toDouble(), 0.0)
            createHologram(lineLoc, line, scale)?.let { uuids.add(it) }
        }
        return uuids
    }

    // ═══════════════════════════════════════════════════════════════
    // TEXT DISPLAY - DAMAGE NUMBERS (Animated)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Show a floating damage number that rises and fades.
     * Uses 1.21's interpolation for smooth 60fps animation.
     * @param location Where to spawn
     * @param damage The damage value
     * @param isCritical Whether to use crit styling
     * @param viewer If provided, only this player sees it
     */
    fun showDamageNumber(
        location: Location,
        damage: Double,
        isCritical: Boolean = false,
        viewer: Player? = null
    ) {
        val damageText = if (damage >= 10) damage.toInt().toString() else String.format("%.1f", damage)
        
        // Color based on damage tier
        val color: TextColor = when {
            isCritical -> TextColor.color(255, 50, 50)     // Bright red
            damage >= 15 -> TextColor.color(255, 100, 50)  // Orange-red
            damage >= 10 -> TextColor.color(255, 170, 50)  // Orange
            damage >= 5 -> TextColor.color(255, 220, 50)   // Yellow
            else -> TextColor.color(255, 255, 150)         // Light yellow
        }
        
        var damageNumComp = Component.text(damageText, color)
        if (isCritical) {
            damageNumComp = damageNumComp.decorate(TextDecoration.BOLD)
        }

        val text = Component.text()
            .append(Component.text("❤ ", NamedTextColor.DARK_RED))
            .append(damageNumComp)
            .build()
        
        // Randomize spawn position slightly for visual variety
        val spawnLoc = location.clone().add(
            (Math.random() - 0.5) * 0.6,
            0.3 + Math.random() * 0.3,
            (Math.random() - 0.5) * 0.6
        )

        try {
            val display = spawnLoc.world.spawn(spawnLoc, TextDisplay::class.java) { td ->
                td.text(text)
                td.billboard = Display.Billboard.CENTER
                td.isShadowed = true
                td.backgroundColor = Color.fromARGB(0, 0, 0, 0)
                
                // Start small, will scale up
                td.transformation = Transformation(
                    Vector3f(0f, 0f, 0f),
                    AxisAngle4f(0f, 0f, 0f, 1f),
                    Vector3f(0.5f, 0.5f, 0.5f),
                    AxisAngle4f(0f, 0f, 0f, 1f)
                )
                
                // Set interpolation duration (in ticks) for smooth transitions
                td.interpolationDuration = 5
                td.teleportDuration = 3
            }
            
            // If player-specific, hide from others
            if (viewer != null) {
                plugin.server.onlinePlayers.forEach { other ->
                    if (other.uniqueId != viewer.uniqueId) {
                        other.hideEntity(plugin, display)
                    }
                }
            }
            
            // Animate: pop up, rise, fade out
            animateDamageNumber(display)
            
        } catch (e: Exception) {
            plugin.logger.warning("Failed to spawn damage number: ${e.message}")
        }
    }

    private fun animateDamageNumber(display: TextDisplay) {
        object : BukkitRunnable() {
            var tick = 0
            val maxTicks = 25
            
            override fun run() {
                if (!display.isValid || tick >= maxTicks) {
                    display.remove()
                    cancel()
                    return
                }
                
                when {
                    // Phase 1 (0-5): Pop up scale
                    tick < 5 -> {
                        val scale = 0.5f + (tick / 5f) * 0.7f  // 0.5 → 1.2
                        display.transformation = Transformation(
                            Vector3f(0f, tick * 0.08f, 0f),
                            AxisAngle4f(0f, 0f, 0f, 1f),
                            Vector3f(scale, scale, scale),
                            AxisAngle4f(0f, 0f, 0f, 1f)
                        )
                    }
                    // Phase 2 (5-15): Float up, stay visible
                    tick < 15 -> {
                        display.transformation = Transformation(
                            Vector3f(0f, 0.4f + (tick - 5) * 0.05f, 0f),
                            AxisAngle4f(0f, 0f, 0f, 1f),
                            Vector3f(1.0f, 1.0f, 1.0f),
                            AxisAngle4f(0f, 0f, 0f, 1f)
                        )
                    }
                    // Phase 3 (15-25): Shrink and fade
                    else -> {
                        val fadeProgress = (tick - 15) / 10f
                        val scale = 1.0f - fadeProgress * 0.5f
                        display.transformation = Transformation(
                            Vector3f(0f, 0.9f + (tick - 15) * 0.03f, 0f),
                            AxisAngle4f(0f, 0f, 0f, 1f),
                            Vector3f(scale, scale, scale),
                            AxisAngle4f(0f, 0f, 0f, 1f)
                        )
                    }
                }
                
                tick++
            }
        }.runTaskTimer(plugin, 0L, 1L)
    }

    // ═══════════════════════════════════════════════════════════════
    // TEXT DISPLAY - COMBO COUNTER
    // ═══════════════════════════════════════════════════════════════

    private val playerCombos = ConcurrentHashMap<UUID, UUID>() // Player -> Combo display UUID

    /**
     * Update or create the combo counter for a player.
     * Floats beside them and grows with combo count.
     */
    fun updateComboDisplay(player: Player, comboCount: Int) {
        // Remove existing combo display
        playerCombos.remove(player.uniqueId)?.let { uuid ->
            plugin.server.getEntity(uuid)?.remove()
        }
        
        if (comboCount < 2) return // Don't show for combo < 2
        
        // Determine style based on combo
        val (color, prefix) = when {
            comboCount >= 20 -> TextColor.color(255, 50, 255) to "⚡ ULTRA "
            comboCount >= 15 -> TextColor.color(255, 100, 100) to "🔥 MEGA "
            comboCount >= 10 -> TextColor.color(255, 150, 50) to "💥 SUPER "
            comboCount >= 5 -> TextColor.color(255, 200, 50) to "✦ "
            else -> TextColor.color(200, 200, 200) to ""
        }
        
        val text = Component.text()
            .append(Component.text(prefix, color))
            .append(Component.text("${comboCount}x COMBO", color, TextDecoration.BOLD))
            .build()
        
        val loc = player.location.clone().add(0.0, 2.3, 0.0)
        
        try {
            val display = loc.world.spawn(loc, TextDisplay::class.java) { td ->
                td.text(text)
                td.billboard = Display.Billboard.CENTER
                td.isShadowed = true
                td.backgroundColor = Color.fromARGB(100, 0, 0, 0)
                
                val scale = 0.7f + (comboCount.coerceAtMost(20) / 40f)
                td.transformation = Transformation(
                    Vector3f(0f, 0f, 0f),
                    AxisAngle4f(0f, 0f, 0f, 1f),
                    Vector3f(scale, scale, scale),
                    AxisAngle4f(0f, 0f, 0f, 1f)
                )
                td.interpolationDuration = 3
            }
            
            playerCombos[player.uniqueId] = display.uniqueId
            
            // Follow player
            object : BukkitRunnable() {
                override fun run() {
                    if (!display.isValid || !player.isOnline || playerCombos[player.uniqueId] != display.uniqueId) {
                        display.remove()
                        cancel()
                        return
                    }
                    display.teleport(player.location.clone().add(0.0, 2.3, 0.0))
                }
            }.runTaskTimer(plugin, 0L, 2L)
            
        } catch (e: Exception) {
            plugin.logger.warning("Failed to spawn combo display: ${e.message}")
        }
    }

    fun clearComboDisplay(player: Player) {
        playerCombos.remove(player.uniqueId)?.let { uuid ->
            plugin.server.getEntity(uuid)?.remove()
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // ITEM DISPLAY - FLOATING ITEMS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Create a floating item display (for loot previews, rewards, etc.)
     * @param location Where to spawn
     * @param item The item to display
     * @param spinning Whether to spin the item
     * @param bobbing Whether to bob up and down
     */
    fun createItemDisplay(
        location: Location,
        item: ItemStack,
        spinning: Boolean = true,
        bobbing: Boolean = true,
        scale: Float = 1.0f
    ): UUID? {
        return try {
            val display = location.world.spawn(location, ItemDisplay::class.java) { id ->
                id.setItemStack(item)
                id.billboard = if (spinning) Display.Billboard.VERTICAL else Display.Billboard.FIXED
                id.itemDisplayTransform = ItemDisplay.ItemDisplayTransform.GROUND
                
                id.transformation = Transformation(
                    Vector3f(0f, 0f, 0f),
                    AxisAngle4f(0f, 0f, 1f, 0f),
                    Vector3f(scale, scale, scale),
                    AxisAngle4f(0f, 0f, 0f, 1f)
                )
                id.interpolationDuration = 10
            }
            
            // Animate bobbing
            if (bobbing || spinning) {
                animateItemDisplay(display, spinning, bobbing)
            }
            
            display.uniqueId
        } catch (e: Exception) {
            plugin.logger.warning("Failed to spawn ItemDisplay: ${e.message}")
            null
        }
    }

    private fun animateItemDisplay(display: ItemDisplay, spinning: Boolean, bobbing: Boolean) {
        object : BukkitRunnable() {
            var tick = 0
            
            override fun run() {
                if (!display.isValid) {
                    cancel()
                    return
                }
                
                val bobOffset = if (bobbing) kotlin.math.sin(tick * 0.1) * 0.15 else 0.0
                val rotation = if (spinning) (tick * 5f) % 360f else 0f
                
                // Use teleport for position, transformation for rotation
                val baseLoc = display.location
                baseLoc.y = baseLoc.y + bobOffset * 0.02 // Subtle movement
                
                display.transformation = Transformation(
                    Vector3f(0f, bobOffset.toFloat(), 0f),
                    AxisAngle4f(Math.toRadians(rotation.toDouble()).toFloat(), 0f, 1f, 0f),
                    display.transformation.scale,
                    AxisAngle4f(0f, 0f, 0f, 1f)
                )
                
                tick++
            }
        }.runTaskTimer(plugin, 0L, 2L)
    }

    // ═══════════════════════════════════════════════════════════════
    // BLOCK DISPLAY - GHOST BLOCKS / PREVIEWS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Create a ghost block display (for schematic previews, placement guides).
     * Uses semi-transparency for preview effect.
     */
    fun createBlockDisplay(
        location: Location,
        material: Material,
        scale: Float = 1.0f,
        glowing: Boolean = false
    ): UUID? {
        return try {
            val display = location.world.spawn(location, BlockDisplay::class.java) { bd ->
                bd.block = material.createBlockData()
                bd.isGlowing = glowing
                
                // Slight transparency for preview feel
                bd.transformation = Transformation(
                    Vector3f(-0.5f, 0f, -0.5f), // Center the block
                    AxisAngle4f(0f, 0f, 0f, 1f),
                    Vector3f(scale, scale, scale),
                    AxisAngle4f(0f, 0f, 0f, 1f)
                )
                bd.brightness = Display.Brightness(15, 15) // Full brightness
            }
            display.uniqueId
        } catch (e: Exception) {
            plugin.logger.warning("Failed to spawn BlockDisplay: ${e.message}")
            null
        }
    }

    /**
     * Create multiple ghost blocks for a schematic preview.
     * Returns list of entity UUIDs for bulk removal.
     */
    fun createSchematicPreview(
        origin: Location,
        blocks: Map<org.bukkit.util.Vector, Material>,
        glowing: Boolean = true
    ): List<UUID> {
        val uuids = mutableListOf<UUID>()
        
        blocks.forEach { (offset, material) ->
            if (material != Material.AIR) {
                val loc = origin.clone().add(offset)
                createBlockDisplay(loc, material, 0.99f, glowing)?.let { uuids.add(it) }
            }
        }
        
        return uuids
    }

    // ═══════════════════════════════════════════════════════════════
    // BOSS HEALTH BAR (TextDisplay based)
    // ═══════════════════════════════════════════════════════════════

    private val bossDisplays = ConcurrentHashMap<UUID, UUID>() // Boss entity -> Display entity

    /**
     * Create or update a health bar above a boss entity.
     */
    fun updateBossHealthBar(
        bossEntity: org.bukkit.entity.LivingEntity,
        bossName: String,
        healthPercent: Double
    ) {
        if (bossEntity.isDead) {
            removeBossHealthBar(bossEntity)
            return
        }

        val barLength = 20
        val filledCount = (healthPercent * barLength).toInt().coerceIn(0, barLength)
        
        // Build health bar: ████████░░░░
        val filledBar = "█".repeat(filledCount)
        val emptyBar = "░".repeat(barLength - filledCount)
        
        val healthColor = when {
            healthPercent > 0.6 -> TextColor.color(100, 255, 100)
            healthPercent > 0.3 -> TextColor.color(255, 200, 50)
            else -> TextColor.color(255, 80, 80)
        }
        
        val text = Component.text()
            .append(Component.text("☠ ", NamedTextColor.DARK_RED))
            .append(Component.text(bossName, NamedTextColor.RED, TextDecoration.BOLD))
            .append(Component.text(" ☠", NamedTextColor.DARK_RED))
            .appendNewline()
            .append(Component.text(filledBar, healthColor))
            .append(Component.text(emptyBar, NamedTextColor.DARK_GRAY))
            .append(Component.text(" ${(healthPercent * 100).toInt()}%", NamedTextColor.WHITE))
            .build()

        // Check for existing display
        val existingUuid = bossDisplays[bossEntity.uniqueId]
        if (existingUuid != null) {
            val display = plugin.server.getEntity(existingUuid) as? TextDisplay
            if (display != null && display.isValid) {
                // Just update text
                display.text(text)
                return
            }
        }
        
        // Spawn new if not exists
        try {
            val loc = bossEntity.location.clone().add(0.0, bossEntity.height + 0.5, 0.0)
            val display = loc.world.spawn(loc, TextDisplay::class.java) { td ->
                td.text(text)
                td.billboard = Display.Billboard.CENTER
                td.isShadowed = true
                td.backgroundColor = Color.fromARGB(150, 20, 0, 0)
                td.transformation = Transformation(
                    Vector3f(0f, 0f, 0f),
                    AxisAngle4f(0f, 0f, 0f, 1f),
                    Vector3f(1.2f, 1.2f, 1.2f),
                    AxisAngle4f(0f, 0f, 0f, 1f)
                )
            }
            
            bossDisplays[bossEntity.uniqueId] = display.uniqueId
            
            // Follow boss entity
            object : BukkitRunnable() {
                override fun run() {
                    if (!display.isValid || bossEntity.isDead || bossDisplays[bossEntity.uniqueId] != display.uniqueId) {
                        display.remove()
                        if (bossDisplays[bossEntity.uniqueId] == display.uniqueId) {
                            bossDisplays.remove(bossEntity.uniqueId)
                        }
                        cancel()
                        return
                    }
                    display.teleport(bossEntity.location.clone().add(0.0, bossEntity.height + 0.5, 0.0))
                }
            }.runTaskTimer(plugin, 0L, 2L)
            
        } catch (e: Exception) {
            plugin.logger.warning("Failed to spawn boss health bar: ${e.message}")
        }
    }

    fun removeBossHealthBar(bossEntity: org.bukkit.entity.LivingEntity) {
        bossDisplays.remove(bossEntity.uniqueId)?.let { uuid ->
            plugin.server.getEntity(uuid)?.remove()
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // PICKUP / REWARD NOTIFICATIONS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Show a floating notification above the player when they pick up something.
     */
    fun showPickupNotification(
        player: Player,
        text: Component,
        duration: Int = 40 // ticks
    ) {
        val loc = player.location.clone().add(0.0, 2.0, 0.0)
        
        try {
            val display = loc.world.spawn(loc, TextDisplay::class.java) { td ->
                td.text(text)
                td.billboard = Display.Billboard.CENTER
                td.isShadowed = true
                td.backgroundColor = Color.fromARGB(0, 0, 0, 0)
                td.transformation = Transformation(
                    Vector3f(0f, 0f, 0f),
                    AxisAngle4f(0f, 0f, 0f, 1f),
                    Vector3f(0.7f, 0.7f, 0.7f),
                    AxisAngle4f(0f, 0f, 0f, 1f)
                )
                td.interpolationDuration = 5
            }
            
            // Only visible to this player
            plugin.server.onlinePlayers.forEach { other ->
                if (other.uniqueId != player.uniqueId) {
                    other.hideEntity(plugin, display)
                }
            }
            
            // Animate and remove
            object : BukkitRunnable() {
                var tick = 0
                override fun run() {
                    if (!display.isValid || tick >= duration) {
                        display.remove()
                        cancel()
                        return
                    }
                    
                    // Follow player and float up slowly
                    val yOffset = tick * 0.02
                    display.teleport(player.location.clone().add(0.0, 2.0 + yOffset, 0.0))
                    
                    // Fade out in last 10 ticks
                    if (tick > duration - 10) {
                        val fadeProgress = (tick - (duration - 10)) / 10f
                        val scale = 0.7f * (1f - fadeProgress * 0.5f)
                        display.transformation = Transformation(
                            Vector3f(0f, 0f, 0f),
                            AxisAngle4f(0f, 0f, 0f, 1f),
                            Vector3f(scale, scale, scale),
                            AxisAngle4f(0f, 0f, 0f, 1f)
                        )
                    }
                    
                    tick++
                }
            }.runTaskTimer(plugin, 0L, 1L)
            
        } catch (e: Exception) {
            plugin.logger.warning("Failed to spawn pickup notification: ${e.message}")
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // CAMERA EFFECTS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Shake the camera using damage flash (simple but effective).
     */
    fun shakeCamera(player: Player) {
        player.damage(0.01)
        player.health = (player.health + 0.01).coerceAtMost(
            player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH)!!.value
        )
    }

    // ═══════════════════════════════════════════════════════════════
    // CLEANUP
    // ═══════════════════════════════════════════════════════════════

    fun removeDisplay(uuid: UUID) {
        plugin.server.getEntity(uuid)?.remove()
    }

    fun removeDisplays(uuids: List<UUID>) {
        uuids.forEach { removeDisplay(it) }
    }

    fun cleanupPlayer(player: Player) {
        playerDisplays.remove(player.uniqueId)?.forEach { uuid ->
            removeDisplay(uuid)
        }
        clearComboDisplay(player)
    }

    fun cleanupAll() {
        playerDisplays.values.flatten().forEach { removeDisplay(it) }
        playerDisplays.clear()
        
        globalDisplays.values.forEach { removeDisplay(it) }
        globalDisplays.clear()
        
        bossDisplays.values.forEach { removeDisplay(it) }
        bossDisplays.clear()
        
        playerCombos.values.forEach { removeDisplay(it) }
        playerCombos.clear()
    }
}
