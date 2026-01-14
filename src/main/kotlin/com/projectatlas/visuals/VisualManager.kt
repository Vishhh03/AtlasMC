package com.projectatlas.visuals

import com.projectatlas.AtlasPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import org.bukkit.*
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Enhanced Visuals Manager
 * Handles particle effects for territories, auras, abilities, atmosphere, and more.
 */
class VisualManager(private val plugin: AtlasPlugin) {

    private val enabledPlayers = mutableSetOf<UUID>()
    private val playerAuraEnabled = ConcurrentHashMap<UUID, Boolean>()
    
    // Track active visual effects
    private val activeEffects = ConcurrentHashMap<UUID, MutableSet<String>>()

    init {
        // Territory border particles (every 0.5s)
        object : BukkitRunnable() {
            override fun run() {
                plugin.server.onlinePlayers.forEach { player ->
                    showChunkBorders(player)
                }
            }
        }.runTaskTimer(plugin, 20L, 10L)
        
        // Player aura particles (every 0.25s)
        object : BukkitRunnable() {
            override fun run() {
                plugin.server.onlinePlayers.forEach { player ->
                    showPlayerAura(player)
                }
            }
        }.runTaskTimer(plugin, 20L, 5L)
        
        // Day/Night ambient effects (every 2s)
        object : BukkitRunnable() {
            override fun run() {
                plugin.server.onlinePlayers.forEach { player ->
                    showAmbientEffects(player)
                }
            }
        }.runTaskTimer(plugin, 40L, 40L)
        
        // Dungeon atmosphere (every 1s)
        object : BukkitRunnable() {
            override fun run() {
                showDungeonAtmosphere()
            }
        }.runTaskTimer(plugin, 20L, 20L)
    }

    fun toggleVisuals(player: Player) {
        if (enabledPlayers.contains(player.uniqueId)) {
            enabledPlayers.remove(player.uniqueId)
            player.sendMessage(Component.text("Territory Visuals Disabled", NamedTextColor.RED))
        } else {
            enabledPlayers.add(player.uniqueId)
            player.sendMessage(Component.text("Territory Visuals Enabled", NamedTextColor.GREEN))
        }
    }
    
    fun toggleAura(player: Player) {
        val current = playerAuraEnabled.getOrDefault(player.uniqueId, true)
        playerAuraEnabled[player.uniqueId] = !current
        player.sendMessage(Component.text(
            if (!current) "Aura Effects Enabled" else "Aura Effects Disabled",
            if (!current) NamedTextColor.GREEN else NamedTextColor.RED
        ))
    }

    // ═══════════════════════════════════════════════════════════════
    // PLAYER AURA EFFECTS
    // ═══════════════════════════════════════════════════════════════
    
    private fun showPlayerAura(player: Player) {
        if (!playerAuraEnabled.getOrDefault(player.uniqueId, true)) return
        if (player.gameMode == GameMode.SPECTATOR) return
        
        val profile = plugin.identityManager.getPlayer(player.uniqueId) ?: return
        val unlocked = plugin.skillTreeManager.getUnlockedNodes(player)
        val loc = player.location.add(0.0, 0.1, 0.0)
        
        // 1. Bounty Target - Ominous dark particles
        val bounty = plugin.bountyManager.getTotalBounty(player.uniqueId)
        if (bounty > 0) {
            spawnAuraRing(loc, Particle.SMOKE, 0.8, 3)
            if (bounty >= 500) {
                spawnAuraRing(loc, Particle.SOUL, 1.0, 2)
            }
            return // Bounty overrides other auras
        }
        
        // 2. Skill-based auras
        val combatNodes = unlocked.count { it.contains("sword") || it.contains("axe") || it.contains("crit") || it.contains("combat") }
        val defenseNodes = unlocked.count { it.contains("health") || it.contains("armor") || it.contains("regen") || it.contains("defense") }
        val utilityNodes = unlocked.count { it.contains("utility") || it.contains("xp") || it.contains("night") || it.contains("water") }
        val mobilityNodes = unlocked.count { it.contains("speed") || it.contains("dash") || it.contains("jump") || it.contains("mobility") }
        val darkNodes = unlocked.count { it.contains("dark") || it.contains("berserker") || it.contains("sneak") }
        
        // Determine dominant path
        val maxPath = maxOf(combatNodes, defenseNodes, utilityNodes, mobilityNodes, darkNodes)
        
        when {
            maxPath == darkNodes && darkNodes >= 3 -> {
                // Dark Arts - Purple soul flames
                spawnAuraRing(loc, Particle.SOUL_FIRE_FLAME, 0.6, 2)
            }
            maxPath == combatNodes && combatNodes >= 3 -> {
                // Combat - Red flames/sparks
                spawnAuraRing(loc, Particle.FLAME, 0.5, 2)
            }
            maxPath == defenseNodes && defenseNodes >= 3 -> {
                // Defense - Blue/white shields
                spawnAuraRing(loc, Particle.SNOWFLAKE, 0.6, 2)
            }
            maxPath == mobilityNodes && mobilityNodes >= 3 -> {
                // Mobility - Wind/speed lines
                spawnAuraRing(loc, Particle.CLOUD, 0.5, 2)
            }
            maxPath == utilityNodes && utilityNodes >= 3 -> {
                // Utility - Golden sparkles
                spawnAuraRing(loc, Particle.WAX_OFF, 0.6, 3)
            }
        }
        
        // 3. High reputation bonus glow
        if (profile.reputation >= 100) {
            player.world.spawnParticle(Particle.END_ROD, loc.clone().add(0.0, 2.2, 0.0), 1, 0.1, 0.0, 0.1, 0.0)
        }
    }
    
    private fun spawnAuraRing(center: Location, particle: Particle, radius: Double, count: Int) {
        for (i in 0 until count) {
            val angle = (System.currentTimeMillis() / 500.0 + i * (2 * Math.PI / count)) % (2 * Math.PI)
            val x = center.x + Math.cos(angle) * radius
            val z = center.z + Math.sin(angle) * radius
            center.world?.spawnParticle(particle, x, center.y, z, 1, 0.0, 0.0, 0.0, 0.0)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // ABILITY CAST VISUALS
    // ═══════════════════════════════════════════════════════════════
    
    /**
     * Call this when Fireball is cast for spiral trail effect
     */
    fun showFireballTrail(player: Player) {
        val direction = player.location.direction.normalize()
        var distance = 0.0
        
        object : BukkitRunnable() {
            override fun run() {
                if (distance > 30) {
                    cancel()
                    return
                }
                
                val loc = player.eyeLocation.add(direction.clone().multiply(distance))
                
                // Spiral effect
                for (i in 0 until 3) {
                    val angle = (distance * 2 + i * 2.0) % (2 * Math.PI)
                    val offsetX = Math.cos(angle) * 0.3
                    val offsetZ = Math.sin(angle) * 0.3
                    loc.world?.spawnParticle(Particle.FLAME, loc.x + offsetX, loc.y, loc.z + offsetZ, 1, 0.0, 0.0, 0.0, 0.0)
                }
                
                loc.world?.spawnParticle(Particle.LAVA, loc, 1)
                distance += 1.5
            }
        }.runTaskTimer(plugin, 0L, 1L)
    }
    
    /**
     * Call this when Shield Wall is cast - hexagonal barrier
     */
    fun showShieldWallEffect(player: Player) {
        val center = player.location
        
        object : BukkitRunnable() {
            var ticks = 0
            override fun run() {
                if (ticks > 40) {
                    cancel()
                    return
                }
                
                // Hexagonal shield dome
                for (i in 0 until 6) {
                    val angle = i * Math.PI / 3
                    val radius = 3.0
                    val x = center.x + Math.cos(angle) * radius
                    val z = center.z + Math.sin(angle) * radius
                    
                    // Vertical pillars
                    for (y in 0..4) {
                        center.world?.spawnParticle(Particle.END_ROD, x, center.y + y * 0.5, z, 1, 0.0, 0.0, 0.0, 0.0)
                    }
                    
                    // Connect to next vertex
                    val nextAngle = (i + 1) * Math.PI / 3
                    val nextX = center.x + Math.cos(nextAngle) * radius
                    val nextZ = center.z + Math.sin(nextAngle) * radius
                    
                    for (t in 0..5) {
                        val lerpX = x + (nextX - x) * t / 5.0
                        val lerpZ = z + (nextZ - z) * t / 5.0
                        center.world?.spawnParticle(Particle.ENCHANT, lerpX, center.y + 2.0, lerpZ, 1, 0.0, 0.0, 0.0, 0.0)
                    }
                }
                
                ticks += 2
            }
        }.runTaskTimer(plugin, 0L, 2L)
    }
    
    /**
     * Call this when Dash is cast - motion blur trail
     */
    fun showDashEffect(player: Player) {
        val startLoc = player.location.clone()
        
        object : BukkitRunnable() {
            var ticks = 0
            override fun run() {
                if (ticks > 10) {
                    cancel()
                    return
                }
                
                val current = player.location
                
                // Wind trail
                for (i in 0 until 5) {
                    val t = i / 5.0
                    val x = startLoc.x + (current.x - startLoc.x) * t
                    val y = startLoc.y + (current.y - startLoc.y) * t + 1.0
                    val z = startLoc.z + (current.z - startLoc.z) * t
                    
                    current.world?.spawnParticle(Particle.CLOUD, x, y, z, 1, 0.1, 0.1, 0.1, 0.0)
                    current.world?.spawnParticle(Particle.SWEEP_ATTACK, x, y, z, 1, 0.0, 0.0, 0.0, 0.0)
                }
                
                ticks++
            }
        }.runTaskTimer(plugin, 0L, 1L)
    }
    
    /**
     * Call this when Healing Pulse is cast - expanding ring of hearts
     */
    fun showHealingPulseEffect(player: Player) {
        val center = player.location
        
        object : BukkitRunnable() {
            var radius = 0.5
            override fun run() {
                if (radius > 6.0) {
                    cancel()
                    return
                }
                
                // Expanding heart ring
                for (i in 0 until 12) {
                    val angle = i * Math.PI * 2 / 12
                    val x = center.x + Math.cos(angle) * radius
                    val z = center.z + Math.sin(angle) * radius
                    center.world?.spawnParticle(Particle.HEART, x, center.y + 1.0, z, 1, 0.0, 0.0, 0.0, 0.0)
                }
                
                // Inner glow
                center.world?.spawnParticle(Particle.HAPPY_VILLAGER, center.x, center.y + 1.5, center.z, 3, 0.3, 0.3, 0.3, 0.0)
                
                radius += 0.5
            }
        }.runTaskTimer(plugin, 0L, 2L)
    }

    // ═══════════════════════════════════════════════════════════════
    // TERRITORY CHUNK BORDERS
    // ═══════════════════════════════════════════════════════════════

    private fun showChunkBorders(player: Player) {
        val item = player.inventory.itemInMainHand
        val isHoldingItem = item.type == Material.COMPASS || item.type == Material.FILLED_MAP
        
        if (!isHoldingItem && !enabledPlayers.contains(player.uniqueId)) return

        val chunk = player.location.chunk
        val city = plugin.cityManager.getCity(plugin.cityManager.getCityAt(chunk)?.id ?: return) ?: return
        
        val playerProfile = plugin.identityManager.getPlayer(player.uniqueId)
        val playerCityId = playerProfile?.cityId
        
        // Color code by relationship
        val (particle, color) = when {
            playerCityId == city.id -> Pair(Particle.COMPOSTER, Color.GREEN)
            else -> Pair(Particle.DUST, Color.RED)
        }

        val world = chunk.world
        val bx = chunk.x shl 4
        val bz = chunk.z shl 4
        val y = player.location.y + 0.5
        
        // Enhanced borders with dust color
        val dustOptions = if (particle == Particle.DUST) {
            Particle.DustOptions(color, 1.0f)
        } else null

        for (i in 0..16 step 2) {
            if (dustOptions != null) {
                world.spawnParticle(Particle.DUST, bx + i.toDouble(), y, bz.toDouble(), 1, 0.0, 0.0, 0.0, 0.0, dustOptions)
                world.spawnParticle(Particle.DUST, bx + i.toDouble(), y, bz + 16.0, 1, 0.0, 0.0, 0.0, 0.0, dustOptions)
                world.spawnParticle(Particle.DUST, bx.toDouble(), y, bz + i.toDouble(), 1, 0.0, 0.0, 0.0, 0.0, dustOptions)
                world.spawnParticle(Particle.DUST, bx + 16.0, y, bz + i.toDouble(), 1, 0.0, 0.0, 0.0, 0.0, dustOptions)
            } else {
                world.spawnParticle(particle, bx + i.toDouble(), y, bz.toDouble(), 1, 0.0, 0.0, 0.0, 0.0)
                world.spawnParticle(particle, bx + i.toDouble(), y, bz + 16.0, 1, 0.0, 0.0, 0.0, 0.0)
                world.spawnParticle(particle, bx.toDouble(), y, bz + i.toDouble(), 1, 0.0, 0.0, 0.0, 0.0)
                world.spawnParticle(particle, bx + 16.0, y, bz + i.toDouble(), 1, 0.0, 0.0, 0.0, 0.0)
            }
        }
        
        // Show wall level indicator at corners
        val infra = city.infrastructure
        if (infra.wallLevel > 0) {
            val wallHeight = infra.wallLevel * 0.5
            for (yOffset in 0 until (wallHeight * 2).toInt()) {
                world.spawnParticle(Particle.DUST, bx.toDouble(), y + yOffset * 0.5, bz.toDouble(), 1, 0.0, 0.0, 0.0, 0.0, 
                    Particle.DustOptions(Color.GRAY, 0.8f))
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // KILL/DEATH EFFECTS
    // ═══════════════════════════════════════════════════════════════
    
    /**
     * Call when a bounty is claimed
     */
    fun showBountyClaimEffect(location: Location, amount: Double) {
        val world = location.world ?: return
        
        // Lightning effect
        world.strikeLightningEffect(location)
        
        // Gold particle shower
        object : BukkitRunnable() {
            var ticks = 0
            override fun run() {
                if (ticks > 20) {
                    cancel()
                    return
                }
                
                for (i in 0 until 10) {
                    val x = location.x + (Math.random() - 0.5) * 3
                    val z = location.z + (Math.random() - 0.5) * 3
                    world.spawnParticle(Particle.WAX_OFF, x, location.y + 2 + Math.random(), z, 1, 0.0, -0.1, 0.0, 0.0)
                }
                
                ticks++
            }
        }.runTaskTimer(plugin, 0L, 1L)
        
        // Sound
        world.playSound(location, Sound.ENTITY_PLAYER_LEVELUP, 1.5f, 0.8f)
        world.playSound(location, Sound.BLOCK_BELL_USE, 1.0f, 1.2f)
    }
    
    /**
     * Call when a player kills another player
     */
    fun showPlayerKillEffect(location: Location) {
        val world = location.world ?: return
        
        // Soul particles rising
        object : BukkitRunnable() {
            var ticks = 0
            override fun run() {
                if (ticks > 30) {
                    cancel()
                    return
                }
                
                val y = location.y + ticks * 0.15
                for (i in 0 until 5) {
                    val angle = (ticks + i) * 0.3
                    val x = location.x + Math.cos(angle) * 0.5
                    val z = location.z + Math.sin(angle) * 0.5
                    world.spawnParticle(Particle.SOUL, x, y, z, 1, 0.0, 0.05, 0.0, 0.0)
                }
                
                ticks++
            }
        }.runTaskTimer(plugin, 0L, 1L)
    }
    
    /**
     * Call when a relic drops
     */
    fun showRelicDropEffect(location: Location) {
        val world = location.world ?: return
        
        // Beacon beam effect (simulated with particles)
        object : BukkitRunnable() {
            var ticks = 0
            override fun run() {
                if (ticks > 60) {
                    cancel()
                    return
                }
                
                // Vertical beam
                for (y in 0 until 15) {
                    world.spawnParticle(Particle.END_ROD, location.x, location.y + y, location.z, 1, 0.0, 0.0, 0.0, 0.0)
                }
                
                // Enchantment swirl at base
                for (i in 0 until 8) {
                    val angle = (ticks + i) * 0.2
                    val radius = 1.5
                    val x = location.x + Math.cos(angle) * radius
                    val z = location.z + Math.sin(angle) * radius
                    world.spawnParticle(Particle.ENCHANT, x, location.y + 0.5, z, 3, 0.0, 0.5, 0.0, 0.0)
                }
                
                ticks += 2
            }
        }.runTaskTimer(plugin, 0L, 2L)
        
        // Sound
        world.playSound(location, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f)
        world.playSound(location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.5f)
    }

    // ═══════════════════════════════════════════════════════════════
    // DUNGEON ATMOSPHERE
    // ═══════════════════════════════════════════════════════════════
    
    private fun showDungeonAtmosphere() {
        // Get all players in dungeon instances
        plugin.server.onlinePlayers.forEach { player ->
            if (player.world.name == "world_the_end") {
                val dungeonInstance = plugin.dungeonManager.getActiveInstance(player)
                if (dungeonInstance != null) {
                    showDungeonFog(player)
                }
            }
        }
    }
    
    private fun showDungeonFog(player: Player) {
        val loc = player.location
        val world = loc.world ?: return
        
        // Ambient fog particles
        for (i in 0 until 5) {
            val x = loc.x + (Math.random() - 0.5) * 20
            val y = loc.y + Math.random() * 3
            val z = loc.z + (Math.random() - 0.5) * 20
            
            world.spawnParticle(Particle.SMOKE, x, y, z, 1, 0.5, 0.2, 0.5, 0.0)
        }
        
        // Eerie ambient sounds occasionally
        if ((System.currentTimeMillis() / 1000) % 15 == 0L && Math.random() < 0.3) {
            player.playSound(loc, Sound.AMBIENT_CAVE, 0.3f, 0.8f)
        }
    }
    
    /**
     * Call when a dungeon boss spawns
     */
    fun showBossSpawnEffect(location: Location, bossName: String) {
        val world = location.world ?: return
        
        // Ground shake (screen shake via brief damage effect - use blindness instead)
        world.getNearbyEntities(location, 30.0, 30.0, 30.0)
            .filterIsInstance<Player>()
            .forEach { player ->
                // Title announcement
                player.showTitle(Title.title(
                    Component.text(bossName, NamedTextColor.DARK_RED),
                    Component.text("has awakened!", NamedTextColor.RED),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(2), Duration.ofMillis(500))
                ))
                
                // Camera shake simulation via rapid FOV changes not possible, use sound instead
                player.playSound(location, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.5f)
                player.playSound(location, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 0.7f)
            }
        
        // Ground explosion particles
        for (i in 0 until 20) {
            val angle = Math.random() * Math.PI * 2
            val radius = Math.random() * 5
            val x = location.x + Math.cos(angle) * radius
            val z = location.z + Math.sin(angle) * radius
            
            world.spawnParticle(Particle.EXPLOSION, x, location.y, z, 1)
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, x, location.y + 0.5, z, 3, 0.2, 0.5, 0.2, 0.0)
        }
        
        // Dark pillar rising
        object : BukkitRunnable() {
            var height = 0
            override fun run() {
                if (height > 20) {
                    cancel()
                    return
                }
                
                for (i in 0 until 8) {
                    val angle = i * Math.PI / 4
                    val x = location.x + Math.cos(angle) * 2
                    val z = location.z + Math.sin(angle) * 2
                    world.spawnParticle(Particle.SOUL, x, location.y + height, z, 2, 0.1, 0.1, 0.1, 0.0)
                }
                
                height++
            }
        }.runTaskTimer(plugin, 0L, 1L)
    }

    // ═══════════════════════════════════════════════════════════════
    // DAY/NIGHT AMBIENT EFFECTS
    // ═══════════════════════════════════════════════════════════════
    
    private fun showAmbientEffects(player: Player) {
        if (player.world.name.contains("nether") || player.world.name.contains("end")) return
        
        val world = player.world
        val time = world.time
        val loc = player.location
        
        when {
            // Dawn (22000-24000 or 0-1000) - Golden particles
            time >= 22000 || time < 1000 -> {
                if (Math.random() < 0.3) {
                    for (i in 0 until 3) {
                        val x = loc.x + (Math.random() - 0.5) * 15
                        val y = loc.y + Math.random() * 5
                        val z = loc.z + (Math.random() - 0.5) * 15
                        world.spawnParticle(Particle.WAX_OFF, x, y, z, 1, 0.0, 0.02, 0.0, 0.0)
                    }
                }
            }
            
            // Dusk (11500-13000) - Orange/red particles
            time in 11500..13000 -> {
                if (Math.random() < 0.3) {
                    for (i in 0 until 3) {
                        val x = loc.x + (Math.random() - 0.5) * 15
                        val y = loc.y + Math.random() * 5
                        val z = loc.z + (Math.random() - 0.5) * 15
                        world.spawnParticle(Particle.DUST, x, y, z, 1, 0.0, 0.0, 0.0, 0.0,
                            Particle.DustOptions(Color.ORANGE, 0.8f))
                    }
                }
            }
            
            // Night (13000-22000) - Subtle glowing eyes effect near mobs
            time in 13000..22000 -> {
                if (Math.random() < 0.1) {
                    // Find nearby hostile mobs
                    world.getNearbyEntities(loc, 20.0, 10.0, 20.0)
                        .filter { it.type.isAlive && isHostile(it.type) }
                        .take(3)
                        .forEach { mob ->
                            val mobLoc = mob.location.add(0.0, mob.height - 0.3, 0.0)
                            world.spawnParticle(Particle.DUST, mobLoc.x - 0.15, mobLoc.y, mobLoc.z, 1, 0.0, 0.0, 0.0, 0.0,
                                Particle.DustOptions(Color.RED, 0.3f))
                            world.spawnParticle(Particle.DUST, mobLoc.x + 0.15, mobLoc.y, mobLoc.z, 1, 0.0, 0.0, 0.0, 0.0,
                                Particle.DustOptions(Color.RED, 0.3f))
                        }
                }
            }
        }
    }
    
    private fun isHostile(type: org.bukkit.entity.EntityType): Boolean {
        return type in listOf(
            org.bukkit.entity.EntityType.ZOMBIE,
            org.bukkit.entity.EntityType.SKELETON,
            org.bukkit.entity.EntityType.SPIDER,
            org.bukkit.entity.EntityType.CREEPER,
            org.bukkit.entity.EntityType.ENDERMAN,
            org.bukkit.entity.EntityType.WITCH,
            org.bukkit.entity.EntityType.PHANTOM
        )
    }

    // ═══════════════════════════════════════════════════════════════
    // SIEGE VISUAL EFFECTS
    // ═══════════════════════════════════════════════════════════════
    
    /**
     * Show pulsing red borders during a siege
     */
    fun showSiegeBorders(cityId: String) {
        val city = plugin.cityManager.getCity(cityId) ?: return
        
        city.claimedChunks.forEach { chunkKey ->
            val parts = chunkKey.split(":")
            if (parts.size < 2) return@forEach
            
            val worldName = parts[0]
            val coords = parts[1].split(",")
            if (coords.size < 2) return@forEach
            
            val world = plugin.server.getWorld(worldName) ?: return@forEach
            val chunkX = coords[0].toIntOrNull() ?: return@forEach
            val chunkZ = coords[1].toIntOrNull() ?: return@forEach
            
            val bx = chunkX shl 4
            val bz = chunkZ shl 4
            
            // Pulsing intensity
            val intensity = (Math.sin(System.currentTimeMillis() / 200.0) + 1) / 2
            val size = (0.8 + intensity * 0.4).toFloat()
            
            // Red pulsing border
            for (i in 0..16 step 4) {
                val y = world.getHighestBlockYAt(bx + i, bz).toDouble() + 2
                world.spawnParticle(Particle.DUST, bx + i.toDouble(), y, bz.toDouble(), 2, 0.0, 0.5, 0.0, 0.0,
                    Particle.DustOptions(Color.RED, size))
            }
        }
    }
}
