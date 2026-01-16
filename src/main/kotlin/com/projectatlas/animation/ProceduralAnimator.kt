package com.projectatlas.animation

import org.bukkit.Location
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.entity.Player
import org.joml.Vector3f
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * PROCEDURAL ANIMATION SYSTEM
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * Generates dynamic animations at runtime based on entity state and environment.
 * These animations are layered ON TOP of keyframe animations for added realism.
 * 
 * Features:
 * - Look-at-target: Head tracks target entities
 * - Breathing: Subtle chest expansion/contraction
 * - Movement lean: Body leans into movement direction
 * - Damage recoil: Dynamic knockback based on damage source
 * - Floating bob: Perlin-noise-based floating motion
 * - Wing flap: Physics-based wing motion
 * - Tail sway: Procedural tail/tentacle movement
 * - Idle sway: Subtle random idle motion
 */
class ProceduralAnimator {
    
    // Per-entity procedural state
    private val entityStates = ConcurrentHashMap<UUID, ProceduralState>()
    
    // Noise generators for organic motion
    private val noiseSeeds = ConcurrentHashMap<UUID, Long>()
    
    /**
     * Get or create procedural state for an entity.
     */
    fun getState(entityId: UUID): ProceduralState {
        return entityStates.getOrPut(entityId) { 
            noiseSeeds[entityId] = System.currentTimeMillis() + entityId.hashCode()
            ProceduralState() 
        }
    }
    
    /**
     * Update procedural animations for an entity.
     * Returns a map of bone name -> additional transform to apply.
     */
    fun update(
        entity: LivingEntity,
        model: AnimatedModel,
        deltaTime: Float = 0.05f // 1 tick = 0.05 seconds
    ): Map<String, BoneTransform> {
        val state = getState(entity.uniqueId)
        val result = mutableMapOf<String, BoneTransform>()
        
        // Update timing
        state.time += deltaTime
        state.tick++
        
        // Calculate velocity
        val currentPos = entity.location
        val velocity = if (state.lastPosition != null) {
            val dx = currentPos.x - state.lastPosition!!.x
            val dy = currentPos.y - state.lastPosition!!.y
            val dz = currentPos.z - state.lastPosition!!.z
            Vector3f(dx.toFloat(), dy.toFloat(), dz.toFloat())
        } else {
            Vector3f(0f, 0f, 0f)
        }
        state.lastPosition = currentPos.clone()
        state.velocity = velocity
        
        // Apply each procedural layer
        if (state.enableLookAt) {
            applyLookAt(entity, state, result)
        }
        
        if (state.enableBreathing) {
            applyBreathing(state, result)
        }
        
        if (state.enableMovementLean) {
            applyMovementLean(state, result)
        }
        
        if (state.enableFloatingBob) {
            applyFloatingBob(entity, state, result)
        }
        
        if (state.enableIdleSway) {
            applyIdleSway(state, result)
        }
        
        if (state.enableWingFlap) {
            applyWingFlap(state, result)
        }
        
        if (state.enableTailSway) {
            applyTailSway(state, result)
        }
        
        // Apply damage recoil if active
        if (state.recoilMagnitude > 0.01f) {
            applyDamageRecoil(state, result)
            state.recoilMagnitude *= 0.85f // Decay
        }
        
        return result
    }
    
    /**
     * Head tracks the nearest player or the entity's target.
     */
    private fun applyLookAt(entity: LivingEntity, state: ProceduralState, result: MutableMap<String, BoneTransform>) {
        val target: Location? = when {
            entity is Mob && entity.target != null -> entity.target?.eyeLocation
            else -> {
                // Look at nearest player within 10 blocks
                entity.world.players
                    .filter { it.location.distance(entity.location) <= 10 }
                    .minByOrNull { it.location.distance(entity.location) }
                    ?.eyeLocation
            }
        }
        
        if (target == null) {
            // Slowly return to neutral
            state.targetLookYaw = state.targetLookYaw * 0.95f
            state.targetLookPitch = state.targetLookPitch * 0.95f
        } else {
            // Calculate direction to target
            val direction = target.toVector().subtract(entity.eyeLocation.toVector()).normalize()
            
            // Calculate yaw (horizontal) and pitch (vertical) angles
            val yaw = atan2(direction.x, direction.z).toFloat()
            val pitch = -atan2(direction.y, sqrt(direction.x * direction.x + direction.z * direction.z)).toFloat()
            
            // Entity's facing direction
            val entityYaw = Math.toRadians(entity.location.yaw.toDouble()).toFloat()
            
            // Relative yaw (how much to turn head from body)
            var relativeYaw = yaw - entityYaw
            
            // Normalize to -PI to PI
            while (relativeYaw > PI) relativeYaw -= (2 * PI).toFloat()
            while (relativeYaw < -PI) relativeYaw += (2 * PI).toFloat()
            
            // Clamp head rotation
            relativeYaw = relativeYaw.coerceIn(-1.2f, 1.2f) // ~70 degrees max
            val clampedPitch = pitch.coerceIn(-0.8f, 0.5f) // Look down more than up
            
            // Smooth interpolation
            state.targetLookYaw += (relativeYaw - state.targetLookYaw) * 0.15f
            state.targetLookPitch += (clampedPitch - state.targetLookPitch) * 0.15f
        }
        
        // Apply to head bone
        val currentHead = result["head"] ?: BoneTransform.IDENTITY
        result["head"] = BoneTransform(
            currentHead.position,
            Vector3f(
                currentHead.rotation.x + state.targetLookPitch,
                currentHead.rotation.y + state.targetLookYaw,
                currentHead.rotation.z
            ),
            currentHead.scale
        )
    }
    
    /**
     * Subtle chest breathing motion.
     */
    private fun applyBreathing(state: ProceduralState, result: MutableMap<String, BoneTransform>) {
        val breathCycle = sin(state.time * 2.5f) // ~0.4 second breath cycle
        val intensity = state.breathingIntensity
        
        // Chest expands/contracts
        val currentBody = result["body"] ?: BoneTransform.IDENTITY
        result["body"] = BoneTransform(
            Vector3f(
                currentBody.position.x,
                currentBody.position.y + breathCycle * 0.02f * intensity,
                currentBody.position.z
            ),
            currentBody.rotation,
            Vector3f(
                currentBody.scale.x + breathCycle * 0.01f * intensity,
                currentBody.scale.y + breathCycle * 0.015f * intensity,
                currentBody.scale.z + breathCycle * 0.01f * intensity
            )
        )
    }
    
    /**
     * Body leans into movement direction.
     */
    private fun applyMovementLean(state: ProceduralState, result: MutableMap<String, BoneTransform>) {
        val velocity = state.velocity
        val speed = sqrt(velocity.x * velocity.x + velocity.z * velocity.z)
        
        if (speed < 0.01f) {
            // Decay lean when stopped
            state.currentLeanX *= 0.9f
            state.currentLeanZ *= 0.9f
        } else {
            // Calculate lean direction (body tips forward in movement direction)
            val leanIntensity = min(speed * 3f, 0.3f) * state.leanMultiplier
            val targetLeanX = velocity.z * leanIntensity
            val targetLeanZ = -velocity.x * leanIntensity
            
            // Smooth interpolation
            state.currentLeanX += (targetLeanX - state.currentLeanX) * 0.2f
            state.currentLeanZ += (targetLeanZ - state.currentLeanZ) * 0.2f
        }
        
        // Apply to body
        val currentBody = result["body"] ?: BoneTransform.IDENTITY
        result["body"] = BoneTransform(
            currentBody.position,
            Vector3f(
                currentBody.rotation.x + state.currentLeanX,
                currentBody.rotation.y,
                currentBody.rotation.z + state.currentLeanZ
            ),
            currentBody.scale
        )
    }
    
    /**
     * Floating/hovering motion with organic noise.
     */
    private fun applyFloatingBob(entity: LivingEntity, state: ProceduralState, result: MutableMap<String, BoneTransform>) {
        val seed = noiseSeeds[entity.uniqueId] ?: 0L
        
        // Multi-frequency noise for organic motion
        val bobY = perlinNoise(state.time * 0.8f, seed) * 0.15f +
                   perlinNoise(state.time * 1.7f, seed + 1000) * 0.08f
        
        val bobX = perlinNoise(state.time * 0.5f, seed + 2000) * 0.05f
        val bobZ = perlinNoise(state.time * 0.6f, seed + 3000) * 0.05f
        
        // Slight rotation wobble
        val rotX = perlinNoise(state.time * 0.4f, seed + 4000) * 0.1f
        val rotZ = perlinNoise(state.time * 0.35f, seed + 5000) * 0.1f
        
        val intensity = state.floatIntensity
        
        // Apply to body (affects whole model)
        val currentBody = result["body"] ?: BoneTransform.IDENTITY
        result["body"] = BoneTransform(
            Vector3f(
                currentBody.position.x + bobX * intensity,
                currentBody.position.y + bobY * intensity,
                currentBody.position.z + bobZ * intensity
            ),
            Vector3f(
                currentBody.rotation.x + rotX * intensity,
                currentBody.rotation.y,
                currentBody.rotation.z + rotZ * intensity
            ),
            currentBody.scale
        )
    }
    
    /**
     * Subtle random sway when idle.
     */
    private fun applyIdleSway(state: ProceduralState, result: MutableMap<String, BoneTransform>) {
        val swayX = sin(state.time * 0.7f) * 0.02f * state.idleSwayIntensity
        val swayZ = sin(state.time * 0.5f + 1.5f) * 0.015f * state.idleSwayIntensity
        
        val currentBody = result["body"] ?: BoneTransform.IDENTITY
        result["body"] = BoneTransform(
            currentBody.position,
            Vector3f(
                currentBody.rotation.x + swayX,
                currentBody.rotation.y,
                currentBody.rotation.z + swayZ
            ),
            currentBody.scale
        )
    }
    
    /**
     * Wing flapping motion.
     */
    private fun applyWingFlap(state: ProceduralState, result: MutableMap<String, BoneTransform>) {
        // Check if moving (flap faster when moving)
        val speed = sqrt(state.velocity.x.pow(2) + state.velocity.z.pow(2))
        val flapSpeed = if (speed > 0.1f) 15f else 8f
        
        val flapAngle = sin(state.time * flapSpeed) * state.wingFlapIntensity
        val flapOffset = cos(state.time * flapSpeed) * 0.1f * state.wingFlapIntensity
        
        // Left wing (rotates down/out)
        val leftWing = result["left_wing"] ?: BoneTransform.IDENTITY
        result["left_wing"] = BoneTransform(
            Vector3f(leftWing.position.x, leftWing.position.y + flapOffset, leftWing.position.z),
            Vector3f(leftWing.rotation.x, leftWing.rotation.y, leftWing.rotation.z - flapAngle),
            leftWing.scale
        )
        
        // Right wing (rotates opposite)
        val rightWing = result["right_wing"] ?: BoneTransform.IDENTITY
        result["right_wing"] = BoneTransform(
            Vector3f(rightWing.position.x, rightWing.position.y + flapOffset, rightWing.position.z),
            Vector3f(rightWing.rotation.x, rightWing.rotation.y, rightWing.rotation.z + flapAngle),
            rightWing.scale
        )
    }
    
    /**
     * Tail/tentacle swaying motion.
     */
    private fun applyTailSway(state: ProceduralState, result: MutableMap<String, BoneTransform>) {
        // Primary sway
        val sway1 = sin(state.time * 2f) * 0.4f * state.tailSwayIntensity
        // Secondary harmonic for organic feel
        val sway2 = sin(state.time * 3.5f) * 0.2f * state.tailSwayIntensity
        
        val totalSway = sway1 + sway2
        
        // Apply to tail bones (tail, tail_mid, tail_tip if they exist)
        listOf("tail" to 0.6f, "tail_mid" to 1.0f, "tail_tip" to 1.4f).forEach { (boneName, multiplier) ->
            val current = result[boneName] ?: BoneTransform.IDENTITY
            result[boneName] = BoneTransform(
                current.position,
                Vector3f(
                    current.rotation.x,
                    current.rotation.y + totalSway * multiplier,
                    current.rotation.z
                ),
                current.scale
            )
        }
    }
    
    /**
     * Apply damage recoil in a direction.
     */
    private fun applyDamageRecoil(state: ProceduralState, result: MutableMap<String, BoneTransform>) {
        val recoilX = state.recoilDirection.x * state.recoilMagnitude
        val recoilZ = state.recoilDirection.z * state.recoilMagnitude
        
        // Tilt body back
        val currentBody = result["body"] ?: BoneTransform.IDENTITY
        result["body"] = BoneTransform(
            Vector3f(
                currentBody.position.x + recoilX * 0.1f,
                currentBody.position.y,
                currentBody.position.z + recoilZ * 0.1f
            ),
            Vector3f(
                currentBody.rotation.x - recoilZ * 0.5f,
                currentBody.rotation.y,
                currentBody.rotation.z + recoilX * 0.5f
            ),
            currentBody.scale
        )
        
        // Head snaps back
        val currentHead = result["head"] ?: BoneTransform.IDENTITY
        result["head"] = BoneTransform(
            currentHead.position,
            Vector3f(
                currentHead.rotation.x - recoilZ * 0.3f,
                currentHead.rotation.y,
                currentHead.rotation.z + recoilX * 0.3f
            ),
            currentHead.scale
        )
    }
    
    /**
     * Trigger damage recoil from a direction.
     */
    fun triggerRecoil(entityId: UUID, fromDirection: Vector3f, magnitude: Float = 1.0f) {
        val state = getState(entityId)
        state.recoilDirection = fromDirection.normalize()
        state.recoilMagnitude = magnitude
    }
    
    /**
     * Configure procedural features for an entity.
     */
    fun configure(entityId: UUID, config: ProceduralConfig) {
        val state = getState(entityId)
        state.enableLookAt = config.lookAt
        state.enableBreathing = config.breathing
        state.enableMovementLean = config.movementLean
        state.enableFloatingBob = config.floatingBob
        state.enableIdleSway = config.idleSway
        state.enableWingFlap = config.wingFlap
        state.enableTailSway = config.tailSway
        
        state.breathingIntensity = config.breathingIntensity
        state.leanMultiplier = config.leanMultiplier
        state.floatIntensity = config.floatIntensity
        state.idleSwayIntensity = config.idleSwayIntensity
        state.wingFlapIntensity = config.wingFlapIntensity
        state.tailSwayIntensity = config.tailSwayIntensity
    }
    
    /**
     * Remove entity from tracking.
     */
    fun removeEntity(entityId: UUID) {
        entityStates.remove(entityId)
        noiseSeeds.remove(entityId)
    }
    
    /**
     * Simple Perlin-like noise function.
     */
    private fun perlinNoise(x: Float, seed: Long): Float {
        val xi = x.toInt()
        val xf = x - xi
        
        // Fade function for smooth interpolation
        val u = xf * xf * (3 - 2 * xf)
        
        // Hash-based pseudo-random values
        val a = hash(xi, seed)
        val b = hash(xi + 1, seed)
        
        return lerp(a, b, u)
    }
    
    private fun hash(x: Int, seed: Long): Float {
        var h = (x * 374761393L + seed * 668265263L) xor (seed shr 13)
        h = (h xor (h shr 16)) * 2246822519L
        h = (h xor (h shr 13)) * 3266489917L
        h = h xor (h shr 16)
        return (h and 0xFFFF).toFloat() / 0xFFFF - 0.5f
    }
    
    private fun lerp(a: Float, b: Float, t: Float): Float = a + t * (b - a)
}

/**
 * State for procedural animations per entity.
 */
data class ProceduralState(
    var time: Float = 0f,
    var tick: Int = 0,
    var lastPosition: Location? = null,
    var velocity: Vector3f = Vector3f(0f, 0f, 0f),
    
    // Feature toggles
    var enableLookAt: Boolean = true,
    var enableBreathing: Boolean = true,
    var enableMovementLean: Boolean = true,
    var enableFloatingBob: Boolean = false,
    var enableIdleSway: Boolean = true,
    var enableWingFlap: Boolean = false,
    var enableTailSway: Boolean = false,
    
    // Intensities
    var breathingIntensity: Float = 1.0f,
    var leanMultiplier: Float = 1.0f,
    var floatIntensity: Float = 1.0f,
    var idleSwayIntensity: Float = 1.0f,
    var wingFlapIntensity: Float = 0.8f,
    var tailSwayIntensity: Float = 1.0f,
    
    // Look-at state
    var targetLookYaw: Float = 0f,
    var targetLookPitch: Float = 0f,
    
    // Movement lean state
    var currentLeanX: Float = 0f,
    var currentLeanZ: Float = 0f,
    
    // Damage recoil state
    var recoilDirection: Vector3f = Vector3f(0f, 0f, 0f),
    var recoilMagnitude: Float = 0f
)

/**
 * Configuration for procedural animation features.
 */
data class ProceduralConfig(
    val lookAt: Boolean = true,
    val breathing: Boolean = true,
    val movementLean: Boolean = true,
    val floatingBob: Boolean = false,
    val idleSway: Boolean = true,
    val wingFlap: Boolean = false,
    val tailSway: Boolean = false,
    
    val breathingIntensity: Float = 1.0f,
    val leanMultiplier: Float = 1.0f,
    val floatIntensity: Float = 1.0f,
    val idleSwayIntensity: Float = 1.0f,
    val wingFlapIntensity: Float = 0.8f,
    val tailSwayIntensity: Float = 1.0f
) {
    companion object {
        // Preset configurations
        val HUMANOID = ProceduralConfig(
            lookAt = true,
            breathing = true,
            movementLean = true,
            floatingBob = false,
            idleSway = true
        )
        
        val FLOATING = ProceduralConfig(
            lookAt = true,
            breathing = false,
            movementLean = false,
            floatingBob = true,
            idleSway = false,
            floatIntensity = 1.2f
        )
        
        val FLYING = ProceduralConfig(
            lookAt = true,
            breathing = false,
            movementLean = true,
            floatingBob = true,
            idleSway = false,
            wingFlap = true,
            floatIntensity = 0.6f,
            leanMultiplier = 1.5f
        )
        
        val BEAST = ProceduralConfig(
            lookAt = true,
            breathing = true,
            movementLean = true,
            floatingBob = false,
            idleSway = true,
            tailSway = true,
            breathingIntensity = 1.3f
        )
        
        val GOLEM = ProceduralConfig(
            lookAt = true,
            breathing = false,
            movementLean = true,
            floatingBob = false,
            idleSway = false,
            leanMultiplier = 0.5f
        )
        
        val GHOST = ProceduralConfig(
            lookAt = true,
            breathing = false,
            movementLean = false,
            floatingBob = true,
            idleSway = true,
            floatIntensity = 1.5f,
            idleSwayIntensity = 1.5f
        )
    }
}
