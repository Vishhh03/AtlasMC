package com.projectatlas.visuals

import com.projectatlas.AtlasPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.block.BlockFace
import org.bukkit.block.data.Levelled
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.scheduler.BukkitRunnable
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * AtmosphereManager
 * Creates "Shader-like" visuals using server-side particles and sounds.
 * Handles Biome ambients, dynamic physics (footprints, ripples), and lighting cues.
 */
class AtmosphereManager(private val plugin: AtlasPlugin) : Listener {

    // playerSettings map removed in favor of IdentityManager settings
    private val random = Random.Default

    init {
        // Main Atmosphere Loop - Runs every 5 ticks (4 times per second) for smoothness without killing TPS
        object : BukkitRunnable() {
            override fun run() {
                plugin.server.onlinePlayers.forEach { player ->
                    if (isatmosphereEnabled(player) && player.gameMode != GameMode.SPECTATOR) {
                        playBiomeAmbience(player)
                        playBiomeAmbience(player)
                        playWaterEffects(player)
                        playThreatAmbience(player)
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 5L)
    }

    fun isatmosphereEnabled(player: Player): Boolean {
        // Default to true
        val profile = plugin.identityManager.getPlayer(player.uniqueId) ?: return true
        return profile.getSetting("atmosphere", true)
    }

    fun toggleAtmosphere(player: Player) {
        val profile = plugin.identityManager.getPlayer(player.uniqueId) ?: return
        val newState = !isatmosphereEnabled(player)
        profile.setSetting("atmosphere", newState)
        plugin.identityManager.saveProfile(player.uniqueId)
        
        if (newState) {
            player.sendMessage(Component.text("Atmosphere shading enabled.", NamedTextColor.GREEN))
        } else {
            player.sendMessage(Component.text("Atmosphere shading disabled.", NamedTextColor.RED))
        }
    }

    // -------------------------------------------------------------------------
    // Layer 1: Biome Atmospherics
    // -------------------------------------------------------------------------
    private fun playBiomeAmbience(player: Player) {
        val loc = player.location
        val world = loc.world ?: return
        val biome = world.getBiome(loc.blockX, loc.blockY, loc.blockZ)
        val isNight = world.time in 13000..23000
        val isRaining = world.hasStorm()

        // Random offset roughly around the player for "3D" feel
        fun randomLoc(): Location {
            return loc.clone().add(
                (random.nextDouble() - 0.5) * 16,
                (random.nextDouble() - 0.5) * 8 + 2, // Slight bias upwards
                (random.nextDouble() - 0.5) * 16
            )
        }

        val biomeName = biome.key.key.lowercase()

        // 1. FORESTS (Falling Leaves / Pollen)
        if (biomeName.contains("forest") || biomeName.contains("taiga") || biomeName.contains("jungle")) {
            // Pollen/Spores (Day)
            if (!isRaining) {
                // Subtle green particles
                player.spawnParticle(Particle.COMPOSTER, randomLoc(), 0, 0.0, 0.0, 0.0) 
            }
            
            // Falling Leaves (Spore Blossom Air uses green particles naturally)
            if (random.nextDouble() < 0.3) {
                 player.spawnParticle(Particle.SPORE_BLOSSOM_AIR, randomLoc(), 0, 0.0, -0.1, 0.0)
            }
        }

        // 2. SWAMPS & JUNGLES (Fireflies & Humidity)
        if (biomeName.contains("swamp") || biomeName.contains("jungle") || biomeName.contains("lush")) {
            if (isNight && !isRaining) {
                // Fireflies (End Rod or Wax On/Off are good tiny bright sparks)
                val fireflyLoc = randomLoc()
                // Make it blink
                player.spawnParticle(Particle.WAX_OFF, fireflyLoc, 0, 0.0, 0.0, 0.0)
                if (random.nextDouble() < 0.05) {
                    // Occasional glow effect
                    player.spawnParticle(Particle.GLOW, fireflyLoc, 0)
                }
            } else {
                // Humidity/Mist (White Ash looks like heavy air)
                if (random.nextDouble() < 0.2) {
                    player.spawnParticle(Particle.WHITE_ASH, randomLoc(), 0, 0.0, -0.05, 0.0)
                }
            }
        }

        // 3. DESERTS & MESAS (Heat Haze & Dust)
        if (biomeName.contains("desert") || biomeName.contains("badlands")) {
            if (!isNight && !isRaining) {
                // Heat Haze (Suspended Sand doesn't exist separately easily, use tiny smoke or end rod moving up)
                val heatLoc = randomLoc()
                // Very subtle upward drift
                // Using null particle data, count 0 allows setting velocity
                // x,y,z params become velocity dx,dy,dz
                // Small upward drift
                // WARN: too much smoke looks like fire. Maybe just occasional sparkles or "glimmer"
            }
            // Dust motes
            if (random.nextDouble() < 0.2) {
                // Gold particles look like sunlit dust
                player.spawnParticle(Particle.WITCH, randomLoc(), 0, 0.0, 0.0, 0.0) 
            }
        }

        // 4. CAVES (Gloom & Drips)
        // Check if under a solid block and low light (simple cave check)
        if (loc.block.lightLevel < 4 && loc.y < 50) {
            // Gloom (Ash)
            if (random.nextDouble() < 0.15) {
                player.spawnParticle(Particle.ASH, randomLoc(), 0)
            }
            // Dripping Water sound occasionally
            if (random.nextDouble() < 0.05) {
                player.playSound(randomLoc(), Sound.BLOCK_POINTED_DRIPSTONE_DRIP_WATER, 0.5f, 0.5f)
            }
        }

        // 5. OCEAN/WATER (Glimmer)
        if (biomeName.contains("ocean") || biomeName.contains("river")) {
            if (!isNight && world.isClearWeather) {
                 // Sparkles on water surface
                 val waterLoc = loc.clone().add(
                    (random.nextDouble() - 0.5) * 20, 
                    0.0, 
                    (random.nextDouble() - 0.5) * 20
                 )
                 // Find surface roughly
                 waterLoc.y = world.getHighestBlockYAt(waterLoc).toDouble() + 1
                 if (world.getBlockAt(waterLoc.clone().add(0.0,-1.0,0.0)).type == Material.WATER) {
                     player.spawnParticle(Particle.END_ROD, waterLoc, 0, 0.0, 0.0, 0.0) // Sparkle
                 }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Layer 2: Dynamic Physics (Constant Check)
    // -------------------------------------------------------------------------
    private fun playWaterEffects(player: Player) {
        val loc = player.location
        val blockAtFeet = loc.block
        val blockBelow = loc.clone().subtract(0.0, 0.1, 0.0).block

        // Water Ripples (Walking in shallow water)
        if (player.isSprinting || player.isWalking()) {
             // Logic: Feet in water or standing on water logged? simplified:
             if (blockAtFeet.type == Material.WATER && ((blockAtFeet.blockData as? Levelled)?.level ?: 0) != 0) {
                 // Do not spawn if in deep water (swimming visually different)
             } else if (blockAtFeet.type == Material.WATER && blockAtFeet.getRelative(BlockFace.UP).type == Material.AIR) {
                 // Shallowish or surface
                 player.spawnParticle(Particle.SPLASH, loc, 4, 0.2, 0.0, 0.2, 0.0)
                 player.spawnParticle(Particle.FISHING, loc, 1, 0.1, 0.0, 0.1, 0.05) // Wake substitute
             }
        }

        // Waterfall Mist
        // Scan nearby blocks for falling water hitting solid ground
        // Checking a small volume around player (e.g. radius 5) is expensive every tick.
        // We only check VERY close, or rely on probability.
        // Optimization: Let's pick 1 random block near player and check it.
        val scanLoc = loc.clone().add(
            (random.nextDouble() - 0.5) * 10,
            (random.nextDouble() - 0.5) * 6,
            (random.nextDouble() - 0.5) * 10
        )
        val scanBlock = scanLoc.block
        if (scanBlock.type == Material.WATER) {
            val down = scanBlock.getRelative(BlockFace.DOWN)
            if (down.type.isSolid) {
                // Water hitting ground -> MIST
               player.spawnParticle(Particle.CLOUD, scanLoc, 0, 0.0, 0.1, 0.0) // Tiny puff
            }
        }
    }

    // -------------------------------------------------------------------------
    // Layer 2: Dynamic Physics (Event Based)
    // -------------------------------------------------------------------------
    
    @EventHandler
    fun onMove(event: PlayerMoveEvent) {
        val player = event.player
        if (!isatmosphereEnabled(player)) return
        if (player.gameMode == GameMode.SPECTATOR) return

        // Wait to ensure they actually moved a block distance roughly
        if (event.from.blockX == event.to.blockX && event.from.blockY == event.to.blockY && event.from.blockZ == event.to.blockZ) return

        val blockBelow = player.location.subtract(0.0, 0.1, 0.0).block
        if (blockBelow.type.isAir) return
        
        // Footprints / Surface interactions
        if (player.isOnGround) {
            when {
                // Soft blocks: Sand, Gravel, Snow
                blockBelow.type.name.contains("SAND") || blockBelow.type == Material.GRAVEL || blockBelow.type == Material.SNOW_BLOCK -> {
                     // Tiny poof of dust
                     player.spawnParticle(Particle.BLOCK, player.location, 2, 0.1, 0.0, 0.1, blockBelow.blockData)
                }
                // Mud
                blockBelow.type == Material.MUD -> {
                     player.spawnParticle(Particle.BLOCK, player.location, 2, 0.1, 0.0, 0.1, blockBelow.blockData)
                     // Squish sound?
                }
            }
        }
    }

    // Helper extension
    private fun Player.isWalking(): Boolean {
        // Approximate, velocity checks are flaky, but good enough for visual fluff
        return this.velocity.lengthSquared() > 0.01
    }

    // -------------------------------------------------------------------------
    // Threat Visuals
    // -------------------------------------------------------------------------
    private var currentThreat: Double = 0.0
    
    fun setThreatLevel(level: Double) {
        this.currentThreat = level
    }
    
    private fun playThreatAmbience(player: Player) {
        if (currentThreat < 50.0) return // No effect for low threat
        
        val loc = player.location
        
        // 1. Spooky Dust (50-80%)
        if (currentThreat >= 50 && random.nextDouble() < 0.1) {
             val pLoc = loc.clone().add(
                (random.nextDouble() - 0.5) * 20, 
                (random.nextDouble() * 5), 
                (random.nextDouble() - 0.5) * 20
             )
             player.spawnParticle(Particle.ASH, pLoc, 0, 0.0, -0.1, 0.0)
        }
        
        // 2. Red Sky / Crimson Spores (80%+)
        if (currentThreat >= 80) {
             // Red tint particles
             // Use Red Dust
             val pLoc = loc.clone().add(
                (random.nextDouble() - 0.5) * 10, 
                (random.nextDouble() * 3), 
                (random.nextDouble() - 0.5) * 10
             )
             player.spawnParticle(Particle.DUST, pLoc, 0, 
                 Particle.DustOptions(org.bukkit.Color.RED, 0.5f))
                 
             if (random.nextDouble() < 0.01) {
                 player.playSound(pLoc, Sound.AMBIENT_CRIMSON_FOREST_ADDITIONS, 0.5f, 0.5f)
             }
        }
    }
}
