package com.projectatlas.animation

import com.projectatlas.AtlasPlugin
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.*
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.scheduler.BukkitRunnable

/**
 * Automatically triggers animations based on entity actions.
 * 
 * This listener observes:
 * - Mob spawning → spawn animation
 * - Mob attacking → attack animation
 * - Mob taking damage → hurt animation
 * - Mob death → death animation
 * - Mob movement → walk/idle animations
 */
class AnimationListener(private val plugin: AtlasPlugin) : Listener {
    
    private val animationSystem: AnimationSystem
        get() = plugin.animationSystem
    
    // Track last known positions for movement detection
    private val lastPositions = mutableMapOf<java.util.UUID, org.bukkit.Location>()
    
    init {
        // Start movement detection task
        startMovementDetection()
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // EVENT HANDLERS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * When an animated mob spawns, play spawn animation.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onCreatureSpawn(event: CreatureSpawnEvent) {
        val entity = event.entity
        
        // Check if this mob has an animated model
        if (animationSystem.hasModel(entity)) {
            animationSystem.playAnimation(entity, "spawn", loop = false)
            
            // After spawn animation, switch to idle
            object : BukkitRunnable() {
                override fun run() {
                    if (entity.isValid && !entity.isDead) {
                        animationSystem.playAnimation(entity, "idle")
                    }
                }
            }.runTaskLater(plugin, 25L) // spawn animation is ~20 ticks
        }
    }
    
    /**
     * When an animated mob attacks, play attack animation.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityAttack(event: EntityDamageByEntityEvent) {
        val attacker = event.damager as? LivingEntity ?: return
        
        if (animationSystem.hasModel(attacker)) {
            // Play attack animation (non-looping), then return to previous state
            val wasWalking = animationSystem.getCurrentAnimation(attacker) == "walk"
            
            animationSystem.playAnimation(attacker, "attack", loop = false)
            
            // Return to walk/idle after attack
            object : BukkitRunnable() {
                override fun run() {
                    if (attacker.isValid && !attacker.isDead) {
                        val returnAnim = if (wasWalking) "walk" else "idle"
                        animationSystem.playAnimation(attacker, returnAnim)
                    }
                }
            }.runTaskLater(plugin, 18L) // attack animation is ~15 ticks
        }
    }
    
    /**
     * When an animated mob takes damage, play hurt animation.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageEvent) {
        val entity = event.entity as? LivingEntity ?: return
        
        if (animationSystem.hasModel(entity)) {
            // Calculate force direction for recoil
            var direction = entity.location.direction.multiply(-1) // Default: backwards
            
            if (event is EntityDamageByEntityEvent) {
                val damager = event.damager
                // Recoil away from damager
                direction = entity.location.toVector().subtract(damager.location.toVector()).normalize()
            }
            
            // Trigger procedural recoil physics
            // Magnitude based on damage (capped at 10 damage = max recoil)
            val magnitude = (event.finalDamage / 10.0).coerceIn(0.5, 2.0).toFloat()
            animationSystem.triggerRecoil(entity, direction, magnitude)
            
            // Don't play hurt ANIMATION if dying, but physics recoil connects feels good
            if (entity.health - event.finalDamage <= 0) return
            
            // Brief hurt animation
            val currentAnim = animationSystem.getCurrentAnimation(entity) ?: "idle"
            
            // Only play hurt if not already playing attack or hurt
            if (currentAnim != "attack" && currentAnim != "hurt") {
                animationSystem.playAnimation(entity, "hurt", loop = false, speed = 1.5f)
                
                object : BukkitRunnable() {
                    override fun run() {
                        if (entity.isValid && !entity.isDead) {
                            animationSystem.playAnimation(entity, currentAnim)
                        }
                    }
                }.runTaskLater(plugin, 8L)
            }
        }
    }
    
    /**
     * When an animated mob dies, play death animation and cleanup.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onEntityDeath(event: EntityDeathEvent) {
        val entity = event.entity
        
        if (animationSystem.hasModel(entity)) {
            // Play death animation
            animationSystem.playAnimation(entity, "death", loop = false)
            
            // Cleanup after death animation completes
            object : BukkitRunnable() {
                override fun run() {
                    animationSystem.detachModel(entity)
                }
            }.runTaskLater(plugin, 35L) // death animation is ~30 ticks
        }
        
        // Clean up position tracking
        lastPositions.remove(entity.uniqueId)
    }
    
    /**
     * When a player with an animated model moves.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player
        
        if (!animationSystem.hasModel(player)) return
        
        val from = event.from
        val to = event.to
        
        // Check if actually moving (not just head movement)
        val isMoving = from.x != to.x || from.z != to.z
        val currentAnim = animationSystem.getCurrentAnimation(player)
        
        when {
            isMoving && currentAnim != "walk" && currentAnim != "attack" -> {
                animationSystem.playAnimation(player, "walk")
            }
            !isMoving && currentAnim == "walk" -> {
                animationSystem.playAnimation(player, "idle")
            }
        }
    }
    
    /**
     * When a mob's target changes (starts chasing), switch to walk animation.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityTarget(event: EntityTargetEvent) {
        val entity = event.entity as? LivingEntity ?: return
        
        if (animationSystem.hasModel(entity)) {
            if (event.target != null) {
                // Has a target, likely moving
                val currentAnim = animationSystem.getCurrentAnimation(entity)
                if (currentAnim == "idle") {
                    animationSystem.playAnimation(entity, "walk")
                }
            }
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // MOVEMENT DETECTION FOR MOBS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Runs periodically to detect mob movement and trigger walk/idle animations.
     * Mobs don't have a move event, so we check position changes.
     */
    private fun startMovementDetection() {
        object : BukkitRunnable() {
            override fun run() {
                plugin.server.worlds.forEach { world ->
                    world.livingEntities.forEach { entity ->
                        // Skip players (handled by PlayerMoveEvent)
                        if (entity is Player) return@forEach
                        
                        // Only process animated mobs
                        if (!animationSystem.hasModel(entity)) return@forEach
                        
                        val currentLoc = entity.location
                        val lastLoc = lastPositions[entity.uniqueId]
                        
                        if (lastLoc != null) {
                            val dx = currentLoc.x - lastLoc.x
                            val dz = currentLoc.z - lastLoc.z
                            val isMoving = dx * dx + dz * dz > 0.01 // Small threshold
                            
                            val currentAnim = animationSystem.getCurrentAnimation(entity)
                            
                            when {
                                // Started moving
                                isMoving && currentAnim == "idle" -> {
                                    animationSystem.playAnimation(entity, "walk")
                                }
                                // Stopped moving
                                !isMoving && currentAnim == "walk" -> {
                                    animationSystem.playAnimation(entity, "idle")
                                }
                            }
                        }
                        
                        lastPositions[entity.uniqueId] = currentLoc.clone()
                    }
                }
                
                // Cleanup dead entities from tracking
                lastPositions.keys.removeIf { uuid ->
                    plugin.server.getEntity(uuid)?.let { !it.isValid || it.isDead } ?: true
                }
            }
        }.runTaskTimer(plugin, 5L, 5L) // Check every 5 ticks
    }
}
