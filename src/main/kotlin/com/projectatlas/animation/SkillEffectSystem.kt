package com.projectatlas.animation

import com.projectatlas.AtlasPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Vector
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * SKILL EFFECT SYSTEM - Visual Feedback for Skill Tree Abilities
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * Provides eye-catching visual and audio feedback when skill tree abilities activate.
 * Effects are designed to be noticeable but not overwhelming during combat.
 */
class SkillEffectSystem(private val plugin: AtlasPlugin) {
    
    // Track active aura effects per player
    private val activeAuras = ConcurrentHashMap<UUID, BukkitRunnable>()
    
    // ═══════════════════════════════════════════════════════════════════════════
    // COMBAT EFFECTS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Play critical hit effect - red slash particles and impactful sound.
     */
    fun playCritEffect(player: Player, target: LivingEntity) {
        val loc = target.location.add(0.0, 1.0, 0.0)
        
        // Red crit particles in a sweep pattern
        for (i in 0..10) {
            val offset = (i - 5) * 0.1
            loc.world.spawnParticle(
                Particle.DUST,
                loc.clone().add(offset, 0.0, offset),
                3,
                0.1, 0.2, 0.1,
                0.0,
                Particle.DustOptions(Color.RED, 1.5f)
            )
        }
        
        // Sweep attack particle
        loc.world.spawnParticle(Particle.SWEEP_ATTACK, loc, 1)
        
        // Crit sound
        player.playSound(player.location, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.2f)
        target.world.playSound(loc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.8f, 0.8f)
        
        // Floating text indicator
        showDamageIndicator(loc, "CRIT!", NamedTextColor.RED)
    }
    
    /**
     * Play life leech effect - green hearts flowing from target to player.
     */
    fun playLeechEffect(player: Player, target: LivingEntity, amount: Double) {
        val from = target.location.add(0.0, 1.0, 0.0)
        val to = player.location.add(0.0, 1.0, 0.0)
        
        // Animate hearts flowing from target to player
        object : BukkitRunnable() {
            var tick = 0
            val maxTicks = 15
            
            override fun run() {
                if (tick >= maxTicks) {
                    // Final burst at player
                    player.world.spawnParticle(Particle.HEART, to, 5, 0.3, 0.3, 0.3, 0.0)
                    player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f)
                    cancel()
                    return
                }
                
                // Interpolate position
                val t = tick.toDouble() / maxTicks
                val current = Location(
                    from.world,
                    lerp(from.x, to.x, t),
                    lerp(from.y, to.y, t) + sin(t * PI) * 0.5, // Arc upward
                    lerp(from.z, to.z, t)
                )
                
                // Green healing particles
                current.world.spawnParticle(
                    Particle.DUST,
                    current,
                    3,
                    0.1, 0.1, 0.1,
                    0.0,
                    Particle.DustOptions(Color.LIME, 1.0f)
                )
                
                tick++
            }
        }.runTaskTimer(plugin, 0L, 1L)
        
        // Sound at target
        target.world.playSound(from, Sound.ENTITY_GENERIC_DRINK, 0.5f, 1.8f)
    }
    
    /**
     * Play dodge effect - blur/speed lines and woosh sound.
     */
    fun playDodgeEffect(player: Player) {
        val loc = player.location
        
        // Speed line particles radiating outward
        for (i in 0..8) {
            val angle = (2 * PI * i) / 8
            val direction = Vector(cos(angle) * 0.8, 0.2, sin(angle) * 0.8)
            
            loc.world.spawnParticle(
                Particle.CLOUD,
                loc.clone().add(0.0, 1.0, 0.0),
                2,
                direction.x, direction.y, direction.z,
                0.15
            )
        }
        
        // Motion blur effect
        loc.world.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, loc.clone().add(0.0, 1.0, 0.0), 10, 0.3, 0.5, 0.3, 0.02)
        
        // Woosh sound
        player.playSound(loc, Sound.ENTITY_PHANTOM_FLAP, 1.0f, 1.5f)
        player.playSound(loc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.5f, 2.0f)
        
        // Action bar message
        player.sendActionBar(Component.text("⚡ DODGED!", NamedTextColor.AQUA))
    }
    
    /**
     * Play execute effect - skull particles and dark flash.
     */
    fun playExecuteEffect(player: Player, target: LivingEntity) {
        val loc = target.location.add(0.0, 1.5, 0.0)
        
        // Dark skull particles
        loc.world.spawnParticle(Particle.SOUL, loc, 20, 0.3, 0.3, 0.3, 0.05)
        loc.world.spawnParticle(Particle.SMOKE, loc, 15, 0.4, 0.5, 0.4, 0.03)
        
        // Blood splatter
        loc.world.spawnParticle(
            Particle.DUST,
            loc,
            25,
            0.5, 0.5, 0.5,
            0.1,
            Particle.DustOptions(Color.fromRGB(100, 0, 0), 2.0f)
        )
        
        // Execution sound
        player.playSound(loc, Sound.ENTITY_WITHER_BREAK_BLOCK, 0.6f, 0.5f)
        loc.world.playSound(loc, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.0f, 0.6f)
        
        // Floating text
        showDamageIndicator(loc, "💀 EXECUTE!", NamedTextColor.DARK_RED)
    }
    
    /**
     * Play thorns damage effect - reflected damage visualization.
     */
    fun playThornsEffect(player: Player, attacker: LivingEntity, damage: Double) {
        val playerLoc = player.location.add(0.0, 1.0, 0.0)
        val attackerLoc = attacker.location.add(0.0, 1.0, 0.0)
        
        // Thorns particles around player
        for (i in 0..6) {
            val angle = (2 * PI * i) / 6
            val offset = Vector(cos(angle) * 0.6, 0.0, sin(angle) * 0.6)
            playerLoc.world.spawnParticle(
                Particle.CRIT,
                playerLoc.clone().add(offset),
                3,
                0.1, 0.2, 0.1,
                0.05
            )
        }
        
        // Damage line to attacker
        val direction = attackerLoc.toVector().subtract(playerLoc.toVector()).normalize()
        for (i in 0..5) {
            val point = playerLoc.clone().add(direction.clone().multiply(i * 0.4))
            point.world.spawnParticle(
                Particle.DUST,
                point,
                2,
                0.05, 0.05, 0.05,
                0.0,
                Particle.DustOptions(Color.PURPLE, 1.0f)
            )
        }
        
        // Sound
        player.playSound(playerLoc, Sound.ENCHANT_THORNS_HIT, 1.0f, 1.2f)
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // AURA EFFECTS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Start berserker aura - red pulsing effect around player.
     */
    fun startBerserkerAura(player: Player) {
        // Stop existing aura if any
        stopBerserkerAura(player)
        
        val task = object : BukkitRunnable() {
            var tick = 0
            
            override fun run() {
                if (!player.isOnline || player.isDead) {
                    cancel()
                    activeAuras.remove(player.uniqueId)
                    return
                }
                
                val loc = player.location.add(0.0, 1.0, 0.0)
                val intensity = 0.5 + sin(tick * 0.3) * 0.3 // Pulsing
                
                // Red aura particles in a ring
                for (i in 0..5) {
                    val angle = (2 * PI * i) / 6 + tick * 0.1
                    val offset = Vector(cos(angle) * 0.8, sin(tick * 0.15) * 0.2, sin(angle) * 0.8)
                    
                    loc.world.spawnParticle(
                        Particle.DUST,
                        loc.clone().add(offset),
                        1,
                        0.0, 0.0, 0.0,
                        0.0,
                        Particle.DustOptions(Color.fromRGB((200 * intensity).toInt() + 55, 20, 20), 1.2f)
                    )
                }
                
                // Occasional flame burst
                if (tick % 20 == 0) {
                    loc.world.spawnParticle(Particle.FLAME, loc, 5, 0.3, 0.3, 0.3, 0.02)
                }
                
                tick++
            }
        }
        
        task.runTaskTimer(plugin, 0L, 2L)
        activeAuras[player.uniqueId] = task
        
        // Activation sound
        player.playSound(player.location, Sound.ENTITY_BLAZE_AMBIENT, 0.5f, 0.8f)
        player.sendActionBar(Component.text("🔥 BERSERKER MODE!", NamedTextColor.DARK_RED))
    }
    
    /**
     * Stop berserker aura effect.
     */
    fun stopBerserkerAura(player: Player) {
        activeAuras.remove(player.uniqueId)?.cancel()
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // MOBILITY EFFECTS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Play double jump effect - white feather burst.
     */
    fun playDoubleJumpEffect(player: Player) {
        val loc = player.location
        
        // Feather burst particles
        for (i in 0..12) {
            val angle = (2 * PI * i) / 12
            val direction = Vector(cos(angle) * 0.5, -0.3, sin(angle) * 0.5)
            
            loc.world.spawnParticle(
                Particle.FIREWORK,
                loc.clone().add(0.0, 0.5, 0.0),
                2,
                direction.x, direction.y, direction.z,
                0.1
            )
        }
        
        // Cloud puff
        loc.world.spawnParticle(Particle.CLOUD, loc.clone().add(0.0, 0.2, 0.0), 8, 0.3, 0.1, 0.3, 0.02)
        
        // Sound
        player.playSound(loc, Sound.ENTITY_BAT_TAKEOFF, 0.8f, 1.2f)
        player.playSound(loc, Sound.ENTITY_PHANTOM_FLAP, 0.5f, 1.8f)
    }
    
    /**
     * Play no fall damage landing effect.
     */
    fun playNoFallDamageEffect(player: Player) {
        val loc = player.location
        
        // Ground impact ring
        for (i in 0..12) {
            val angle = (2 * PI * i) / 12
            val offset = Vector(cos(angle) * 1.2, 0.1, sin(angle) * 1.2)
            
            loc.world.spawnParticle(
                Particle.DUST,
                loc.clone().add(offset),
                3,
                0.1, 0.0, 0.1,
                0.0,
                Particle.DustOptions(Color.GRAY, 1.5f)
            )
        }
        
        loc.world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, loc, 5, 0.3, 0.1, 0.3, 0.01)
        
        // Soft landing sound
        player.playSound(loc, Sound.BLOCK_WOOL_FALL, 1.0f, 0.8f)
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // WEAPON ABILITY EFFECTS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Play hollow knight dash trail effect.
     */
    fun playDashTrailEffect(player: Player, startLoc: Location, endLoc: Location) {
        val direction = endLoc.toVector().subtract(startLoc.toVector())
        val distance = direction.length()
        direction.normalize()
        
        // Soul fire trail
        for (i in 0..(distance * 4).toInt()) {
            val point = startLoc.clone().add(direction.clone().multiply(i * 0.25))
            point.world.spawnParticle(Particle.SOUL_FIRE_FLAME, point, 1, 0.1, 0.1, 0.1, 0.01)
        }
        
        // Afterimage effect (dark smoke at origin)
        startLoc.world.spawnParticle(Particle.LARGE_SMOKE, startLoc.clone().add(0.0, 1.0, 0.0), 10, 0.2, 0.5, 0.2, 0.01)
    }
    
    /**
     * Play sonic boom beam effect.
     */
    fun playSonicBoomEffect(origin: Location, direction: Vector, range: Int) {
        // Beam distortion particles
        for (i in 0..range) {
            val point = origin.clone().add(direction.clone().multiply(i.toDouble()))
            
            // Sonic boom particle
            point.world.spawnParticle(Particle.SONIC_BOOM, point, 1)
            
            // Shockwave ring at each point
            for (j in 0..4) {
                val angle = (2 * PI * j) / 4
                val offset = Vector(cos(angle) * 0.3, sin(angle) * 0.3, 0.0)
                // Rotate offset to be perpendicular to direction
                val perpendicular = direction.clone().crossProduct(Vector(0, 1, 0)).normalize()
                val ringPoint = point.clone().add(perpendicular.multiply(cos(angle) * 0.5)).add(Vector(0.0, sin(angle) * 0.5, 0.0))
                
                ringPoint.world.spawnParticle(
                    Particle.DUST,
                    ringPoint,
                    1,
                    0.0, 0.0, 0.0,
                    0.0,
                    Particle.DustOptions(Color.AQUA, 0.8f)
                )
            }
        }
    }
    
    /**
     * Play dragon roar shockwave effect.
     */
    fun playDragonRoarShockwave(center: Location, radius: Double) {
        object : BukkitRunnable() {
            var currentRadius = 0.5
            
            override fun run() {
                if (currentRadius >= radius) {
                    cancel()
                    return
                }
                
                // Expanding ring of particles
                val particleCount = (currentRadius * 8).toInt().coerceAtLeast(8)
                for (i in 0 until particleCount) {
                    val angle = (2 * PI * i) / particleCount
                    val x = cos(angle) * currentRadius
                    val z = sin(angle) * currentRadius
                    
                    center.world.spawnParticle(
                        Particle.DUST,
                        center.clone().add(x, 0.2, z),
                        1,
                        0.0, 0.0, 0.0,
                        0.0,
                        Particle.DustOptions(Color.ORANGE, 1.5f)
                    )
                }
                
                // Ground crack particles
                if (currentRadius.toInt() % 2 == 0) {
                    center.world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, center, 3, currentRadius * 0.5, 0.1, currentRadius * 0.5, 0.01)
                }
                
                currentRadius += 0.8
            }
        }.runTaskTimer(plugin, 0L, 1L)
        
        // Explosion effect at center
        center.world.spawnParticle(Particle.EXPLOSION, center.clone().add(0.0, 1.0, 0.0), 1)
    }
    
    /**
     * Play ender teleport rift effect.
     */
    fun playEnderTeleportEffect(from: Location, to: Location) {
        // Origin effect - dissolving
        from.world.spawnParticle(Particle.PORTAL, from.clone().add(0.0, 1.0, 0.0), 50, 0.3, 0.6, 0.3, 0.5)
        from.world.spawnParticle(Particle.REVERSE_PORTAL, from.clone().add(0.0, 1.0, 0.0), 30, 0.2, 0.4, 0.2, 0.3)
        
        // Rift line connecting origin to destination
        val direction = to.toVector().subtract(from.toVector())
        val distance = direction.length()
        direction.normalize()
        
        object : BukkitRunnable() {
            var progress = 0.0
            
            override fun run() {
                if (progress >= distance) {
                    cancel()
                    return
                }
                
                val point = from.clone().add(direction.clone().multiply(progress))
                point.world.spawnParticle(Particle.END_ROD, point.add(0.0, 1.0, 0.0), 1, 0.0, 0.0, 0.0, 0.0)
                
                progress += 1.0
            }
        }.runTaskTimer(plugin, 0L, 1L)
        
        // Destination effect - reforming
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            to.world.spawnParticle(Particle.PORTAL, to.clone().add(0.0, 1.0, 0.0), 40, 0.3, 0.6, 0.3, 0.3)
            to.world.spawnParticle(Particle.END_ROD, to.clone().add(0.0, 1.0, 0.0), 20, 0.3, 0.5, 0.3, 0.1)
            to.world.playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f)
        }, (distance / 2).toLong().coerceAtLeast(5L))
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════════════════════
    
    private fun showDamageIndicator(location: Location, text: String, color: NamedTextColor) {
        // Spawn temporary armor stand or use title - for simplicity, just action bar nearby
        location.world.players.filter { it.location.distance(location) < 16 }.forEach { player ->
            // This is a simple implementation - could use display entities for floating text
        }
    }
    
    private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t
    
    /**
     * Cleanup all active effects.
     */
    fun cleanup() {
        activeAuras.values.forEach { it.cancel() }
        activeAuras.clear()
    }
}
