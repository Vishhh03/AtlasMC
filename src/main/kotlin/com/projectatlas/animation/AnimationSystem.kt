package com.projectatlas.animation

import com.projectatlas.AtlasPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.*
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Transformation
import org.joml.AxisAngle4f
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * ATLAS ANIMATION SYSTEM - Custom ModelEngine-like Animation via Display Entities
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * This system provides skeletal animation for mobs using native 1.21 Display Entities.
 * No ProtocolLib or client mods required!
 * 
 * Features:
 * - Bone-based skeletal system (each bone = ItemDisplay)
 * - Keyframe animations with smooth interpolation
 * - Predefined animations: idle, walk, attack, death, spawn, custom
 * - Attach animated models to any LivingEntity
 * - Per-bone transformations (position, rotation, scale)
 * - Resource pack integration for custom models
 * 
 * Architecture:
 *   AnimatedModel → List<Bone> → ItemDisplay entities
 *   AnimationController → plays Animation → updates Bone transforms per tick
 */
class AnimationSystem(private val plugin: AtlasPlugin) {
    
    // Active animated models (entity UUID -> AnimatedModel)
    private val activeModels = ConcurrentHashMap<UUID, AnimatedModel>()
    
    // Animation definitions (name -> Animation)
    private val animations = ConcurrentHashMap<String, Animation>()
    
    // Model blueprints (name -> ModelBlueprint)
    private val modelBlueprints = ConcurrentHashMap<String, ModelBlueprint>()
    
    // Procedural animator for dynamic runtime animations
    val proceduralAnimator = ProceduralAnimator()
    
    // PDC keys
    private val animatedKey = NamespacedKey(plugin, "animated_model")
    private val boneIdKey = NamespacedKey(plugin, "bone_id")
    
    init {
        // Register built-in animations
        registerBuiltInAnimations()
        registerAdvancedAnimations()
        registerBuiltInModels()
        
        // Start the animation tick loop
        startAnimationLoop()
        
        plugin.logger.info("Animation System initialized with ${animations.size} animations and ${modelBlueprints.size} model blueprints!")
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Attach an animated model to a living entity.
     * The model will follow the entity and play animations.
     */
    fun attachModel(entity: LivingEntity, modelName: String): AnimatedModel? {
        val blueprint = modelBlueprints[modelName] ?: run {
            plugin.logger.warning("Model blueprint '$modelName' not found!")
            return null
        }
        
        // Remove existing model if any
        detachModel(entity)
        
        // Create the model from blueprint
        val model = createModelFromBlueprint(entity, blueprint)
        activeModels[entity.uniqueId] = model
        
        // Tag entity
        entity.persistentDataContainer.set(animatedKey, PersistentDataType.STRING, modelName)
        
        // Hide base entity visuals
        entity.isInvisible = true
        entity.isSilent = true // Optional: Prevent zombie groans if preferred
        entity.equipment?.clear() // Remove vanilla armor/items to prevent floating gear
        
        // Start idle animation by default
        playAnimation(entity, "idle")
        
        return model
    }
    
    /**
     * Detach and remove an animated model from an entity.
     */
    fun detachModel(entity: LivingEntity) {
        activeModels.remove(entity.uniqueId)?.destroy()
        entity.persistentDataContainer.remove(animatedKey)
    }
    
    /**
     * Play an animation on an entity's attached model.
     */
    fun playAnimation(entity: LivingEntity, animationName: String, loop: Boolean = true, speed: Float = 1.0f): Boolean {
        val model = activeModels[entity.uniqueId] ?: return false
        val animation = animations[animationName] ?: run {
            plugin.logger.warning("Animation '$animationName' not found!")
            return false
        }
        
        model.controller.playAnimation(animation, loop, speed)
        return true
    }
    
    /**
     * Stop current animation and return to idle.
     */
    fun stopAnimation(entity: LivingEntity) {
        val model = activeModels[entity.uniqueId] ?: return
        val idle = animations["idle"]
        if (idle != null) {
            model.controller.playAnimation(idle, loop = true, speed = 1.0f)
        } else {
            model.controller.stop()
        }
    }
    
    /**
     * Check if entity has an attached animated model.
     */
    fun hasModel(entity: LivingEntity): Boolean {
        return activeModels.containsKey(entity.uniqueId)
    }
    
    /**
     * Get current animation name playing on entity.
     */
    fun getCurrentAnimation(entity: LivingEntity): String? {
        return activeModels[entity.uniqueId]?.controller?.currentAnimation?.name
    }
    
    /**
     * Configure procedural animation settings for an entity.
     */
    fun configureProcedural(entity: LivingEntity, config: ProceduralConfig) {
        proceduralAnimator.configure(entity.uniqueId, config)
    }
    
    /**
     * Trigger a physical recoil effect on the entity.
     */
    fun triggerRecoil(entity: LivingEntity, fromDirection: org.bukkit.util.Vector, magnitude: Float = 1.0f) {
        proceduralAnimator.triggerRecoil(
            entity.uniqueId, 
            Vector3f(fromDirection.x.toFloat(), fromDirection.y.toFloat(), fromDirection.z.toFloat()),
            magnitude
        )
    }
    
    /**
     * Register a custom animation.
     */
    fun registerAnimation(animation: Animation) {
        animations[animation.name] = animation
    }
    
    /**
     * Register a custom model blueprint.
     */
    fun registerModelBlueprint(blueprint: ModelBlueprint) {
        modelBlueprints[blueprint.name] = blueprint
    }
    
    /**
     * Create a quick one-shot animation effect at a location (not attached to entity).
     */
    fun playEffectAnimation(location: Location, animationName: String, duration: Int = 40): UUID? {
        val animation = animations[animationName] ?: return null
        
        // Create temporary display entity
        val display = location.world.spawn(location, ItemDisplay::class.java) { id ->
            id.setItemStack(ItemStack(Material.NETHER_STAR))
            id.interpolationDuration = 3
        }
        
        // Animate and cleanup
        object : BukkitRunnable() {
            var tick = 0
            override fun run() {
                if (!display.isValid || tick >= duration) {
                    display.remove()
                    cancel()
                    return
                }
                
                // Apply animation frame
                val frame = animation.getFrameAt(tick.toFloat() / duration)
                frame.bones.values.firstOrNull()?.let { transform ->
                    applyTransform(display, transform)
                }
                
                tick++
            }
        }.runTaskTimer(plugin, 0L, 1L)
        
        return display.uniqueId
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // INTERNAL - Model Creation & Management
    // ═══════════════════════════════════════════════════════════════════════════
    
    private fun createModelFromBlueprint(entity: LivingEntity, blueprint: ModelBlueprint): AnimatedModel {
        val bones = mutableMapOf<String, Bone>()
        
        blueprint.bones.forEach { boneDef ->
            val boneLocation = entity.location.clone().add(boneDef.defaultOffset)
            
            val display = boneLocation.world.spawn(boneLocation, ItemDisplay::class.java) { id ->
                id.setItemStack(boneDef.itemStack)
                id.itemDisplayTransform = ItemDisplay.ItemDisplayTransform.NONE
                id.interpolationDuration = 3 // Smooth interpolation
                id.teleportDuration = 2
                id.isPersistent = false
                
                // Tag for identification
                id.persistentDataContainer.set(boneIdKey, PersistentDataType.STRING, boneDef.name)
                
                // Apply default transform
                id.transformation = Transformation(
                    Vector3f(boneDef.defaultOffset.x.toFloat(), boneDef.defaultOffset.y.toFloat(), boneDef.defaultOffset.z.toFloat()),
                    Quaternionf(),
                    Vector3f(boneDef.defaultScale, boneDef.defaultScale, boneDef.defaultScale),
                    Quaternionf()
                )
            }
            
            bones[boneDef.name] = Bone(
                name = boneDef.name,
                display = display,
                defaultOffset = boneDef.defaultOffset,
                parentBone = boneDef.parentBone
            )
        }
        
        return AnimatedModel(
            entityUuid = entity.uniqueId,
            entity = entity,
            bones = bones,
            controller = AnimationController()
        )
    }
    
    private fun startAnimationLoop() {
        object : BukkitRunnable() {
            override fun run() {
                val toRemove = mutableListOf<UUID>()
                
                activeModels.forEach { (uuid, model) ->
                    val entity = plugin.server.getEntity(uuid) as? LivingEntity
                    
                    if (entity == null || entity.isDead || !entity.isValid) {
                        model.destroy()
                        proceduralAnimator.removeEntity(uuid) // Cleanup procedural state
                        toRemove.add(uuid)
                        return@forEach
                    }
                    
                    // Update procedural animation state
                    val proceduralTransforms = proceduralAnimator.update(entity, model)
                    
                    // Update bone positions (follow entity) - pass procedural transforms if needed in future
                    updateBonePositions(model, entity)
                    
                    // Tick animation controller
                    model.controller.tick()
                    
                    // Apply animation transforms (Keyframe + Procedural)
                    applyAnimationFrame(model, proceduralTransforms)
                }
                
                toRemove.forEach { activeModels.remove(it) }
            }
        }.runTaskTimer(plugin, 0L, 1L)
    }
    
    private fun updateBonePositions(model: AnimatedModel, entity: LivingEntity) {
        val baseLocation = entity.location.clone()
        
        model.bones.values.forEach { bone ->
            val bonePos = baseLocation.clone().add(bone.defaultOffset)
            
            // Apply parent bone offset if applicable
            bone.parentBone?.let { parentName ->
                model.bones[parentName]?.let { parent ->
                    // Inherit parent position adjustments
                    val parentTransform = model.controller.getCurrentTransform(parentName)
                    if (parentTransform != null) {
                        bonePos.add(
                            parentTransform.position.x.toDouble(),
                            parentTransform.position.y.toDouble(),
                            parentTransform.position.z.toDouble()
                        )
                    }
                }
            }
            
            if (bone.display.isValid) {
                bone.display.teleport(bonePos)
            }
        }
    }
    
    private fun applyAnimationFrame(model: AnimatedModel, proceduralTransforms: Map<String, BoneTransform>) {
        val frame = model.controller.getCurrentFrame() ?: return
        
        model.bones.forEach { (boneName, bone) ->
            // Base keyframe transform
            var finalTransform = frame.bones[boneName] ?: BoneTransform.IDENTITY
            
            // Apply procedural offset if exists
            proceduralTransforms[boneName]?.let { proc ->
                finalTransform = BoneTransform(
                    position = Vector3f(finalTransform.position).add(proc.position),
                    rotation = Vector3f(finalTransform.rotation).add(proc.rotation),
                    scale = Vector3f(finalTransform.scale).mul(proc.scale)
                )
            }
            
            if (bone.display.isValid) {
                applyTransform(bone.display, finalTransform)
            }
        }
    }
    
    private fun applyTransform(display: ItemDisplay, transform: BoneTransform) {
        display.transformation = Transformation(
            transform.position,
            Quaternionf().rotationXYZ(
                transform.rotation.x,
                transform.rotation.y,
                transform.rotation.z
            ),
            transform.scale,
            Quaternionf()
        )
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // BUILT-IN ANIMATIONS
    // ═══════════════════════════════════════════════════════════════════════════
    
    private fun registerBuiltInAnimations() {
        // IDLE - Subtle breathing motion
        animations["idle"] = Animation(
            name = "idle",
            duration = 40,
            keyframes = listOf(
                Keyframe(0f, mapOf(
                    "body" to BoneTransform(Vector3f(0f, 0f, 0f), Vector3f(0f, 0f, 0f), Vector3f(1f, 1f, 1f)),
                    "head" to BoneTransform(Vector3f(0f, 1.5f, 0f), Vector3f(0f, 0f, 0f), Vector3f(1f, 1f, 1f))
                )),
                Keyframe(20f, mapOf(
                    "body" to BoneTransform(Vector3f(0f, 0.05f, 0f), Vector3f(0f, 0f, 0f), Vector3f(1.02f, 1.02f, 1.02f)),
                    "head" to BoneTransform(Vector3f(0f, 1.55f, 0f), Vector3f(0.05f, 0f, 0f), Vector3f(1f, 1f, 1f))
                )),
                Keyframe(40f, mapOf(
                    "body" to BoneTransform(Vector3f(0f, 0f, 0f), Vector3f(0f, 0f, 0f), Vector3f(1f, 1f, 1f)),
                    "head" to BoneTransform(Vector3f(0f, 1.5f, 0f), Vector3f(0f, 0f, 0f), Vector3f(1f, 1f, 1f))
                ))
            )
        )
        
        // WALK - Bobbing motion with limb movement
        animations["walk"] = Animation(
            name = "walk",
            duration = 20,
            keyframes = listOf(
                Keyframe(0f, mapOf(
                    "body" to BoneTransform(Vector3f(0f, 0f, 0f), Vector3f(0f, 0f, 0f), Vector3f(1f, 1f, 1f)),
                    "head" to BoneTransform(Vector3f(0f, 1.5f, 0f), Vector3f(0f, 0f, 0f), Vector3f(1f, 1f, 1f)),
                    "left_arm" to BoneTransform(Vector3f(-0.5f, 1.2f, 0f), Vector3f(0.5f, 0f, 0f), Vector3f(1f, 1f, 1f)),
                    "right_arm" to BoneTransform(Vector3f(0.5f, 1.2f, 0f), Vector3f(-0.5f, 0f, 0f), Vector3f(1f, 1f, 1f)),
                    "left_leg" to BoneTransform(Vector3f(-0.2f, 0f, 0f), Vector3f(-0.4f, 0f, 0f), Vector3f(1f, 1f, 1f)),
                    "right_leg" to BoneTransform(Vector3f(0.2f, 0f, 0f), Vector3f(0.4f, 0f, 0f), Vector3f(1f, 1f, 1f))
                )),
                Keyframe(5f, mapOf(
                    "body" to BoneTransform(Vector3f(0f, 0.08f, 0f), Vector3f(0f, 0f, 0f), Vector3f(1f, 1f, 1f)),
                    "head" to BoneTransform(Vector3f(0f, 1.58f, 0f), Vector3f(0f, 0f, 0f), Vector3f(1f, 1f, 1f)),
                    "left_arm" to BoneTransform(Vector3f(-0.5f, 1.28f, 0f), Vector3f(0f, 0f, 0f), Vector3f(1f, 1f, 1f)),
                    "right_arm" to BoneTransform(Vector3f(0.5f, 1.28f, 0f), Vector3f(0f, 0f, 0f), Vector3f(1f, 1f, 1f)),
                    "left_leg" to BoneTransform(Vector3f(-0.2f, 0.08f, 0f), Vector3f(0f, 0f, 0f), Vector3f(1f, 1f, 1f)),
                    "right_leg" to BoneTransform(Vector3f(0.2f, 0.08f, 0f), Vector3f(0f, 0f, 0f), Vector3f(1f, 1f, 1f))
                )),
                Keyframe(10f, mapOf(
                    "body" to BoneTransform(Vector3f(0f, 0f, 0f), Vector3f(0f, 0f, 0f), Vector3f(1f, 1f, 1f)),
                    "head" to BoneTransform(Vector3f(0f, 1.5f, 0f), Vector3f(0f, 0f, 0f), Vector3f(1f, 1f, 1f)),
                    "left_arm" to BoneTransform(Vector3f(-0.5f, 1.2f, 0f), Vector3f(-0.5f, 0f, 0f), Vector3f(1f, 1f, 1f)),
                    "right_arm" to BoneTransform(Vector3f(0.5f, 1.2f, 0f), Vector3f(0.5f, 0f, 0f), Vector3f(1f, 1f, 1f)),
                    "left_leg" to BoneTransform(Vector3f(-0.2f, 0f, 0f), Vector3f(0.4f, 0f, 0f), Vector3f(1f, 1f, 1f)),
                    "right_leg" to BoneTransform(Vector3f(0.2f, 0f, 0f), Vector3f(-0.4f, 0f, 0f), Vector3f(1f, 1f, 1f))
                )),
                Keyframe(15f, mapOf(
                    "body" to BoneTransform(Vector3f(0f, 0.08f, 0f), Vector3f(0f, 0f, 0f), Vector3f(1f, 1f, 1f)),
                    "head" to BoneTransform(Vector3f(0f, 1.58f, 0f), Vector3f(0f, 0f, 0f), Vector3f(1f, 1f, 1f)),
                    "left_arm" to BoneTransform(Vector3f(-0.5f, 1.28f, 0f), Vector3f(0f, 0f, 0f), Vector3f(1f, 1f, 1f)),
                    "right_arm" to BoneTransform(Vector3f(0.5f, 1.28f, 0f), Vector3f(0f, 0f, 0f), Vector3f(1f, 1f, 1f)),
                    "left_leg" to BoneTransform(Vector3f(-0.2f, 0.08f, 0f), Vector3f(0f, 0f, 0f), Vector3f(1f, 1f, 1f)),
                    "right_leg" to BoneTransform(Vector3f(0.2f, 0.08f, 0f), Vector3f(0f, 0f, 0f), Vector3f(1f, 1f, 1f))
                )),
                Keyframe(20f, mapOf(
                    "body" to BoneTransform(Vector3f(0f, 0f, 0f), Vector3f(0f, 0f, 0f), Vector3f(1f, 1f, 1f)),
                    "head" to BoneTransform(Vector3f(0f, 1.5f, 0f), Vector3f(0f, 0f, 0f), Vector3f(1f, 1f, 1f)),
                    "left_arm" to BoneTransform(Vector3f(-0.5f, 1.2f, 0f), Vector3f(0.5f, 0f, 0f), Vector3f(1f, 1f, 1f)),
                    "right_arm" to BoneTransform(Vector3f(0.5f, 1.2f, 0f), Vector3f(-0.5f, 0f, 0f), Vector3f(1f, 1f, 1f)),
                    "left_leg" to BoneTransform(Vector3f(-0.2f, 0f, 0f), Vector3f(-0.4f, 0f, 0f), Vector3f(1f, 1f, 1f)),
                    "right_leg" to BoneTransform(Vector3f(0.2f, 0f, 0f), Vector3f(0.4f, 0f, 0f), Vector3f(1f, 1f, 1f))
                ))
            )
        )
        
        // ATTACK - Quick strike motion
        animations["attack"] = Animation(
            name = "attack",
            duration = 15,
            keyframes = listOf(
                Keyframe(0f, mapOf(
                    "body" to BoneTransform(Vector3f(0f, 0f, 0f), Vector3f(0f, 0f, 0f), Vector3f(1f, 1f, 1f)),
                    "head" to BoneTransform(Vector3f(0f, 1.5f, 0f), Vector3f(0f, 0f, 0f), Vector3f(1f, 1f, 1f)),
                    "right_arm" to BoneTransform(Vector3f(0.5f, 1.2f, 0f), Vector3f(0f, 0f, 0f), Vector3f(1f, 1f, 1f)),
                    "weapon" to BoneTransform(Vector3f(0.7f, 1.4f, 0.3f), Vector3f(-0.5f, 0f, 0f), Vector3f(1f, 1f, 1f))
                )),
                Keyframe(3f, mapOf(
                    "body" to BoneTransform(Vector3f(0f, 0f, -0.1f), Vector3f(0f, 0.2f, 0f), Vector3f(1f, 1f, 1f)),
                    "head" to BoneTransform(Vector3f(0f, 1.5f, -0.1f), Vector3f(0.1f, 0.1f, 0f), Vector3f(1f, 1f, 1f)),
                    "right_arm" to BoneTransform(Vector3f(0.6f, 1.5f, -0.3f), Vector3f(-1.5f, 0f, 0f), Vector3f(1f, 1f, 1f)),
                    "weapon" to BoneTransform(Vector3f(0.8f, 2.0f, -0.5f), Vector3f(-2.0f, 0f, 0.3f), Vector3f(1.1f, 1.1f, 1.1f))
                )),
                Keyframe(6f, mapOf(
                    "body" to BoneTransform(Vector3f(0f, 0f, 0.2f), Vector3f(0f, -0.3f, 0f), Vector3f(1f, 1f, 1f)),
                    "head" to BoneTransform(Vector3f(0f, 1.5f, 0.2f), Vector3f(-0.1f, -0.2f, 0f), Vector3f(1f, 1f, 1f)),
                    "right_arm" to BoneTransform(Vector3f(0.4f, 0.8f, 0.5f), Vector3f(1.0f, 0f, 0f), Vector3f(1f, 1f, 1f)),
                    "weapon" to BoneTransform(Vector3f(0.5f, 0.5f, 0.8f), Vector3f(1.5f, 0f, -0.3f), Vector3f(1.15f, 1.15f, 1.15f))
                )),
                Keyframe(15f, mapOf(
                    "body" to BoneTransform(Vector3f(0f, 0f, 0f), Vector3f(0f, 0f, 0f), Vector3f(1f, 1f, 1f)),
                    "head" to BoneTransform(Vector3f(0f, 1.5f, 0f), Vector3f(0f, 0f, 0f), Vector3f(1f, 1f, 1f)),
                    "right_arm" to BoneTransform(Vector3f(0.5f, 1.2f, 0f), Vector3f(0f, 0f, 0f), Vector3f(1f, 1f, 1f)),
                    "weapon" to BoneTransform(Vector3f(0.7f, 1.4f, 0.3f), Vector3f(-0.5f, 0f, 0f), Vector3f(1f, 1f, 1f))
                ))
            )
        )
        
        // DEATH - Dramatic fall
        animations["death"] = Animation(
            name = "death",
            duration = 30,
            keyframes = listOf(
                Keyframe(0f, mapOf(
                    "body" to BoneTransform(Vector3f(0f, 0f, 0f), Vector3f(0f, 0f, 0f), Vector3f(1f, 1f, 1f)),
                    "head" to BoneTransform(Vector3f(0f, 1.5f, 0f), Vector3f(0f, 0f, 0f), Vector3f(1f, 1f, 1f))
                )),
                Keyframe(5f, mapOf(
                    "body" to BoneTransform(Vector3f(0f, -0.1f, 0f), Vector3f(0f, 0f, 0.3f), Vector3f(1f, 1f, 1f)),
                    "head" to BoneTransform(Vector3f(0f, 1.4f, 0f), Vector3f(0.3f, 0f, 0.2f), Vector3f(1f, 1f, 1f))
                )),
                Keyframe(15f, mapOf(
                    "body" to BoneTransform(Vector3f(0f, -0.5f, 0f), Vector3f((PI / 4).toFloat(), 0f, (PI / 3).toFloat()), Vector3f(1f, 1f, 1f)),
                    "head" to BoneTransform(Vector3f(0.2f, 1.0f, 0f), Vector3f(0.5f, 0.2f, 0.4f), Vector3f(1f, 1f, 1f))
                )),
                Keyframe(30f, mapOf(
                    "body" to BoneTransform(Vector3f(0.3f, -0.8f, 0f), Vector3f((PI / 2).toFloat(), 0f, (PI / 2).toFloat()), Vector3f(0.9f, 0.9f, 0.9f)),
                    "head" to BoneTransform(Vector3f(0.5f, 0.2f, 0f), Vector3f((PI / 3).toFloat(), 0.3f, (PI / 2).toFloat()), Vector3f(0.95f, 0.95f, 0.95f))
                ))
            )
        )
        
        // SPAWN - Pop in effect
        animations["spawn"] = Animation(
            name = "spawn",
            duration = 20,
            keyframes = listOf(
                Keyframe(0f, mapOf(
                    "body" to BoneTransform(Vector3f(0f, -1f, 0f), Vector3f(0f, 0f, 0f), Vector3f(0.1f, 0.1f, 0.1f)),
                    "head" to BoneTransform(Vector3f(0f, 0.5f, 0f), Vector3f(0f, 0f, 0f), Vector3f(0.1f, 0.1f, 0.1f))
                )),
                Keyframe(10f, mapOf(
                    "body" to BoneTransform(Vector3f(0f, 0.1f, 0f), Vector3f(0f, 0f, 0f), Vector3f(1.15f, 1.15f, 1.15f)),
                    "head" to BoneTransform(Vector3f(0f, 1.6f, 0f), Vector3f(0f, 0f, 0f), Vector3f(1.15f, 1.15f, 1.15f))
                )),
                Keyframe(15f, mapOf(
                    "body" to BoneTransform(Vector3f(0f, -0.05f, 0f), Vector3f(0f, 0f, 0f), Vector3f(0.95f, 0.95f, 0.95f)),
                    "head" to BoneTransform(Vector3f(0f, 1.45f, 0f), Vector3f(0f, 0f, 0f), Vector3f(0.95f, 0.95f, 0.95f))
                )),
                Keyframe(20f, mapOf(
                    "body" to BoneTransform(Vector3f(0f, 0f, 0f), Vector3f(0f, 0f, 0f), Vector3f(1f, 1f, 1f)),
                    "head" to BoneTransform(Vector3f(0f, 1.5f, 0f), Vector3f(0f, 0f, 0f), Vector3f(1f, 1f, 1f))
                ))
            )
        )
        
        // HURT - Quick recoil
        animations["hurt"] = Animation(
            name = "hurt",
            duration = 10,
            keyframes = listOf(
                Keyframe(0f, mapOf(
                    "body" to BoneTransform(Vector3f(0f, 0f, 0f), Vector3f(0f, 0f, 0f), Vector3f(1f, 1f, 1f))
                )),
                Keyframe(2f, mapOf(
                    "body" to BoneTransform(Vector3f(0f, 0f, -0.2f), Vector3f(-0.15f, 0f, 0f), Vector3f(0.95f, 1.05f, 0.95f))
                )),
                Keyframe(5f, mapOf(
                    "body" to BoneTransform(Vector3f(0f, 0f, 0.1f), Vector3f(0.1f, 0f, 0f), Vector3f(1.02f, 0.98f, 1.02f))
                )),
                Keyframe(10f, mapOf(
                    "body" to BoneTransform(Vector3f(0f, 0f, 0f), Vector3f(0f, 0f, 0f), Vector3f(1f, 1f, 1f))
                ))
            )
        )
        
        // CELEBRATE - Victory dance
        animations["celebrate"] = Animation(
            name = "celebrate",
            duration = 40,
            keyframes = listOf(
                Keyframe(0f, mapOf(
                    "body" to BoneTransform(Vector3f(0f, 0f, 0f), Vector3f(0f, 0f, 0f), Vector3f(1f, 1f, 1f)),
                    "left_arm" to BoneTransform(Vector3f(-0.5f, 1.2f, 0f), Vector3f(0f, 0f, 0f), Vector3f(1f, 1f, 1f)),
                    "right_arm" to BoneTransform(Vector3f(0.5f, 1.2f, 0f), Vector3f(0f, 0f, 0f), Vector3f(1f, 1f, 1f))
                )),
                Keyframe(10f, mapOf(
                    "body" to BoneTransform(Vector3f(0f, 0.3f, 0f), Vector3f(0f, 0f, 0f), Vector3f(1f, 1f, 1f)),
                    "left_arm" to BoneTransform(Vector3f(-0.6f, 2.0f, 0f), Vector3f(-2.5f, 0f, -0.3f), Vector3f(1f, 1f, 1f)),
                    "right_arm" to BoneTransform(Vector3f(0.6f, 2.0f, 0f), Vector3f(-2.5f, 0f, 0.3f), Vector3f(1f, 1f, 1f))
                )),
                Keyframe(20f, mapOf(
                    "body" to BoneTransform(Vector3f(0f, 0f, 0f), Vector3f(0f, 0.5f, 0f), Vector3f(1f, 1f, 1f)),
                    "left_arm" to BoneTransform(Vector3f(-0.5f, 1.8f, 0.2f), Vector3f(-2.2f, 0.3f, -0.2f), Vector3f(1f, 1f, 1f)),
                    "right_arm" to BoneTransform(Vector3f(0.5f, 1.8f, 0.2f), Vector3f(-2.2f, -0.3f, 0.2f), Vector3f(1f, 1f, 1f))
                )),
                Keyframe(30f, mapOf(
                    "body" to BoneTransform(Vector3f(0f, 0.2f, 0f), Vector3f(0f, -0.5f, 0f), Vector3f(1f, 1f, 1f)),
                    "left_arm" to BoneTransform(Vector3f(-0.6f, 1.9f, -0.2f), Vector3f(-2.3f, -0.3f, -0.25f), Vector3f(1f, 1f, 1f)),
                    "right_arm" to BoneTransform(Vector3f(0.6f, 1.9f, -0.2f), Vector3f(-2.3f, 0.3f, 0.25f), Vector3f(1f, 1f, 1f))
                )),
                Keyframe(40f, mapOf(
                    "body" to BoneTransform(Vector3f(0f, 0f, 0f), Vector3f(0f, 0f, 0f), Vector3f(1f, 1f, 1f)),
                    "left_arm" to BoneTransform(Vector3f(-0.5f, 1.2f, 0f), Vector3f(0f, 0f, 0f), Vector3f(1f, 1f, 1f)),
                    "right_arm" to BoneTransform(Vector3f(0.5f, 1.2f, 0f), Vector3f(0f, 0f, 0f), Vector3f(1f, 1f, 1f))
                ))
            )
        )
        
        // CHARGE - Preparing to attack
        animations["charge"] = Animation(
            name = "charge",
            duration = 25,
            keyframes = listOf(
                Keyframe(0f, mapOf(
                    "body" to BoneTransform(Vector3f(0f, 0f, 0f), Vector3f(0f, 0f, 0f), Vector3f(1f, 1f, 1f)),
                    "right_arm" to BoneTransform(Vector3f(0.5f, 1.2f, 0f), Vector3f(0f, 0f, 0f), Vector3f(1f, 1f, 1f))
                )),
                Keyframe(15f, mapOf(
                    "body" to BoneTransform(Vector3f(0f, -0.1f, -0.15f), Vector3f(0.1f, 0f, 0f), Vector3f(1.05f, 0.95f, 1.05f)),
                    "right_arm" to BoneTransform(Vector3f(0.6f, 1.6f, -0.4f), Vector3f(-2.0f, 0f, 0.3f), Vector3f(1.1f, 1.1f, 1.1f))
                )),
                Keyframe(25f, mapOf(
                    "body" to BoneTransform(Vector3f(0f, -0.15f, -0.2f), Vector3f(0.15f, 0f, 0f), Vector3f(1.08f, 0.92f, 1.08f)),
                    "right_arm" to BoneTransform(Vector3f(0.65f, 1.8f, -0.5f), Vector3f(-2.3f, 0f, 0.4f), Vector3f(1.15f, 1.15f, 1.15f))
                ))
            )
        )
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // BUILT-IN MODEL BLUEPRINTS
    // ═══════════════════════════════════════════════════════════════════════════
    
    private fun registerBuiltInModels() {
        // HUMANOID - Basic humanoid skeleton (zombie-like)
        modelBlueprints["humanoid"] = ModelBlueprint(
            name = "humanoid",
            bones = listOf(
                BoneDefinition("body", org.bukkit.util.Vector(0.0, 0.8, 0.0), ItemStack(Material.LEATHER_CHESTPLATE), 1.0f),
                BoneDefinition("head", org.bukkit.util.Vector(0.0, 1.6, 0.0), ItemStack(Material.ZOMBIE_HEAD), 0.8f),
                BoneDefinition("left_arm", org.bukkit.util.Vector(-0.5, 1.2, 0.0), ItemStack(Material.STICK), 0.6f, "body"),
                BoneDefinition("right_arm", org.bukkit.util.Vector(0.5, 1.2, 0.0), ItemStack(Material.STICK), 0.6f, "body"),
                BoneDefinition("left_leg", org.bukkit.util.Vector(-0.2, 0.0, 0.0), ItemStack(Material.STICK), 0.6f, "body"),
                BoneDefinition("right_leg", org.bukkit.util.Vector(0.2, 0.0, 0.0), ItemStack(Material.STICK), 0.6f, "body")
            )
        )
        
        // BOSS_GOLEM - Large armored golem
        modelBlueprints["boss_golem"] = ModelBlueprint(
            name = "boss_golem",
            bones = listOf(
                BoneDefinition("body", org.bukkit.util.Vector(0.0, 1.2, 0.0), ItemStack(Material.IRON_BLOCK), 1.5f),
                BoneDefinition("head", org.bukkit.util.Vector(0.0, 2.4, 0.0), ItemStack(Material.CARVED_PUMPKIN), 1.2f),
                BoneDefinition("left_arm", org.bukkit.util.Vector(-1.2, 1.8, 0.0), ItemStack(Material.IRON_BARS), 1.0f, "body"),
                BoneDefinition("right_arm", org.bukkit.util.Vector(1.2, 1.8, 0.0), ItemStack(Material.IRON_BARS), 1.0f, "body"),
                BoneDefinition("weapon", org.bukkit.util.Vector(1.5, 2.0, 0.5), ItemStack(Material.IRON_SWORD), 1.2f, "right_arm")
            )
        )
        
        // FLOATING_SKULL - Hovering skull boss
        modelBlueprints["floating_skull"] = ModelBlueprint(
            name = "floating_skull",
            bones = listOf(
                BoneDefinition("body", org.bukkit.util.Vector(0.0, 1.5, 0.0), ItemStack(Material.WITHER_SKELETON_SKULL), 2.0f),
                BoneDefinition("left_flame", org.bukkit.util.Vector(-0.8, 1.8, 0.0), ItemStack(Material.SOUL_LANTERN), 0.5f, "body"),
                BoneDefinition("right_flame", org.bukkit.util.Vector(0.8, 1.8, 0.0), ItemStack(Material.SOUL_LANTERN), 0.5f, "body")
            )
        )
        
        // SPIDER_BOSS - Large spider with legs
        modelBlueprints["spider_boss"] = ModelBlueprint(
            name = "spider_boss",
            bones = listOf(
                BoneDefinition("body", org.bukkit.util.Vector(0.0, 0.6, 0.0), ItemStack(Material.BLACK_WOOL), 1.5f),
                BoneDefinition("head", org.bukkit.util.Vector(0.0, 0.6, 0.8), ItemStack(Material.SPIDER_EYE), 1.0f, "body"),
                BoneDefinition("leg_fl", org.bukkit.util.Vector(-0.6, 0.3, 0.4), ItemStack(Material.END_ROD), 0.4f, "body"),
                BoneDefinition("leg_fr", org.bukkit.util.Vector(0.6, 0.3, 0.4), ItemStack(Material.END_ROD), 0.4f, "body"),
                BoneDefinition("leg_bl", org.bukkit.util.Vector(-0.6, 0.3, -0.4), ItemStack(Material.END_ROD), 0.4f, "body"),
                BoneDefinition("leg_br", org.bukkit.util.Vector(0.6, 0.3, -0.4), ItemStack(Material.END_ROD), 0.4f, "body")
            )
        )
        
        // CRYSTAL_GUARDIAN - Magical crystal entity
        modelBlueprints["crystal_guardian"] = ModelBlueprint(
            name = "crystal_guardian",
            bones = listOf(
                BoneDefinition("core", org.bukkit.util.Vector(0.0, 1.0, 0.0), ItemStack(Material.END_CRYSTAL), 0.8f),
                BoneDefinition("shard1", org.bukkit.util.Vector(0.5, 1.5, 0.0), ItemStack(Material.AMETHYST_SHARD), 0.4f, "core"),
                BoneDefinition("shard2", org.bukkit.util.Vector(-0.5, 1.5, 0.0), ItemStack(Material.AMETHYST_SHARD), 0.4f, "core"),
                BoneDefinition("shard3", org.bukkit.util.Vector(0.0, 1.5, 0.5), ItemStack(Material.AMETHYST_SHARD), 0.4f, "core"),
                BoneDefinition("shard4", org.bukkit.util.Vector(0.0, 1.5, -0.5), ItemStack(Material.AMETHYST_SHARD), 0.4f, "core")
            )
        )
        
        // ═══════════════════════════════════════════════════════════════════════════
        // BOSS MODELS WITH CUSTOM ITEMS
        // ═══════════════════════════════════════════════════════════════════════════
        
        // HOLLOW KNIGHT (Crypt Boss) - Wields the Hollow Knight Blade
        modelBlueprints["hollow_knight"] = ModelBlueprint(
            name = "hollow_knight",
            bones = listOf(
                BoneDefinition("body", org.bukkit.util.Vector(0.0, 0.9, 0.0), ItemStack(Material.CHAINMAIL_CHESTPLATE), 1.1f),
                BoneDefinition("head", org.bukkit.util.Vector(0.0, 1.8, 0.0), ItemStack(Material.WITHER_SKELETON_SKULL), 1.0f, "body"),
                BoneDefinition("left_arm", org.bukkit.util.Vector(-0.6, 1.3, 0.0), ItemStack(Material.STICK), 0.8f, "body"),
                BoneDefinition("right_arm", org.bukkit.util.Vector(0.6, 1.3, 0.0), ItemStack(Material.STICK), 0.8f, "body"),
                BoneDefinition("weapon", org.bukkit.util.Vector(0.5, -0.5, 0.0), com.projectatlas.visual.CustomItemManager.createHollowKnightBlade(), 1.0f, "right_arm"),
                BoneDefinition("left_leg", org.bukkit.util.Vector(-0.25, 0.0, 0.0), ItemStack(Material.CHAINMAIL_LEGGINGS), 0.8f, "body"),
                BoneDefinition("right_leg", org.bukkit.util.Vector(0.25, 0.0, 0.0), ItemStack(Material.CHAINMAIL_LEGGINGS), 0.8f, "body")
            )
        )
        
        // WARDEN KNIGHT (Infernal Boss) - Wields Warden's Flame
        modelBlueprints["warden_knight"] = ModelBlueprint(
            name = "warden_knight",
            bones = listOf(
                BoneDefinition("body", org.bukkit.util.Vector(0.0, 1.0, 0.0), ItemStack(Material.NETHERITE_CHESTPLATE), 1.2f),
                BoneDefinition("head", org.bukkit.util.Vector(0.0, 2.0, 0.0), ItemStack(Material.PIGLIN_HEAD), 1.0f, "body"),
                BoneDefinition("left_arm", org.bukkit.util.Vector(-0.7, 1.5, 0.0), ItemStack(Material.STICK), 0.9f, "body"),
                BoneDefinition("right_arm", org.bukkit.util.Vector(0.7, 1.5, 0.0), ItemStack(Material.STICK), 0.9f, "body"),
                BoneDefinition("weapon", org.bukkit.util.Vector(0.5, -0.5, 0.5), com.projectatlas.visual.CustomItemManager.createWardenFlameSword(), 1.2f, "right_arm"),
                BoneDefinition("shield", org.bukkit.util.Vector(-0.2, -0.2, 0.0), ItemStack(Material.NETHER_BRICK_FENCE), 1.0f, "left_arm"),
                BoneDefinition("left_leg", org.bukkit.util.Vector(-0.3, 0.0, 0.0), ItemStack(Material.NETHERITE_LEGGINGS), 0.9f, "body"),
                BoneDefinition("right_leg", org.bukkit.util.Vector(0.3, 0.0, 0.0), ItemStack(Material.NETHERITE_LEGGINGS), 0.9f, "body")
            )
        )
        
        // ENDER SENTINEL (Temple/End Boss) - Wields Scythe
        modelBlueprints["ender_sentinel"] = ModelBlueprint(
            name = "ender_sentinel",
            bones = listOf(
                BoneDefinition("body", org.bukkit.util.Vector(0.0, 1.5, 0.0), ItemStack(Material.OBSIDIAN), 0.8f),
                BoneDefinition("head", org.bukkit.util.Vector(0.0, 2.8, 0.0), ItemStack(Material.DRAGON_HEAD), 0.8f, "body"),
                BoneDefinition("left_arm", org.bukkit.util.Vector(-0.8, 2.0, 0.0), ItemStack(Material.END_ROD), 1.2f, "body"),
                BoneDefinition("right_arm", org.bukkit.util.Vector(0.8, 2.0, 0.0), ItemStack(Material.END_ROD), 1.2f, "body"),
                BoneDefinition("weapon", org.bukkit.util.Vector(0.8, -0.5, 0.0), com.projectatlas.visual.CustomItemManager.createEnderSentinelScythe(), 1.5f, "right_arm"),
                BoneDefinition("left_leg", org.bukkit.util.Vector(-0.3, 0.0, 0.0), ItemStack(Material.OBSIDIAN), 0.8f, "body"),
                BoneDefinition("right_leg", org.bukkit.util.Vector(0.3, 0.0, 0.0), ItemStack(Material.OBSIDIAN), 0.8f, "body")
            )
        )
        
        // SIMPLE - Just body + head (for basic mobs)
        modelBlueprints["simple"] = ModelBlueprint(
            name = "simple",
            bones = listOf(
                BoneDefinition("body", org.bukkit.util.Vector(0.0, 0.8, 0.0), ItemStack(Material.BARRIER), 1.0f),
                BoneDefinition("head", org.bukkit.util.Vector(0.0, 1.5, 0.0), ItemStack(Material.PLAYER_HEAD), 1.0f)
            )
        )
        // DODGE - Quick side step
        animations["dodge"] = Animation("dodge", 10, listOf(
            Keyframe(0f, mapOf("body" to BoneTransform.IDENTITY)),
            Keyframe(5f, mapOf("body" to BoneTransform(Vector3f(0.5f, -0.2f, 0f), Vector3f(0f, 0f, -0.2f), Vector3f(1f, 1f, 1f)))),
            Keyframe(10f, mapOf("body" to BoneTransform.IDENTITY))
        ))
    }
    
    private fun registerAdvancedAnimations() {
        // FLY - Floating with motion
        animations["fly"] = Animation("fly", 40, listOf(
            Keyframe(0f, mapOf("body" to BoneTransform(Vector3f(0f, 0f, 0f), Vector3f(0.1f, 0f, 0f), Vector3f(1f, 1f, 1f)))),
            Keyframe(20f, mapOf("body" to BoneTransform(Vector3f(0f, 0.5f, 0f), Vector3f(-0.1f, 0f, 0f), Vector3f(1f, 1f, 1f)))),
            Keyframe(40f, mapOf("body" to BoneTransform(Vector3f(0f, 0f, 0f), Vector3f(0.1f, 0f, 0f), Vector3f(1f, 1f, 1f))))
        ))
        
        // CAST - Spellcasting motion
        animations["cast"] = Animation("cast", 30, listOf(
            Keyframe(0f, mapOf("body" to BoneTransform.IDENTITY, "right_arm" to BoneTransform.IDENTITY)),
            Keyframe(10f, mapOf(
                "body" to BoneTransform(Vector3f(0f, 0.2f, 0f), Vector3f(-0.1f, 0f, 0f), Vector3f(1f, 1f, 1f)),
                "right_arm" to BoneTransform(Vector3f(0.5f, 2.0f, 0f), Vector3f(-3.0f, 0.5f, 0f), Vector3f(1.2f, 1.2f, 1.2f))
            )),
            Keyframe(20f, mapOf(
                "body" to BoneTransform(Vector3f(0f, 0.3f, 0.1f), Vector3f(0.1f, 0f, 0f), Vector3f(1f, 1f, 1f)),
                "right_arm" to BoneTransform(Vector3f(0.6f, 2.2f, 0.2f), Vector3f(-2.8f, -0.2f, 0f), Vector3f(1.1f, 1.1f, 1.1f))
            )),
            Keyframe(30f, mapOf("body" to BoneTransform.IDENTITY, "right_arm" to BoneTransform.IDENTITY))
        ))
        
        // ROAR - Intimidating pose
        animations["roar"] = Animation("roar", 45, listOf(
            Keyframe(0f, mapOf("body" to BoneTransform.IDENTITY, "head" to BoneTransform(Vector3f(0f, 1.5f, 0f), Vector3f(0f, 0f, 0f), Vector3f(1f, 1f, 1f)))),
            Keyframe(15f, mapOf(
                "body" to BoneTransform(Vector3f(0f, -0.2f, 0f), Vector3f(0.3f, 0f, 0f), Vector3f(1.1f, 1.1f, 1.1f)),
                "head" to BoneTransform(Vector3f(0f, 1.4f, 0.2f), Vector3f(-0.5f, 0f, 0f), Vector3f(1.1f, 1.1f, 1.1f))
            )),
            Keyframe(25f, mapOf(
                "body" to BoneTransform(Vector3f(0f, 0.1f, -0.1f), Vector3f(-0.2f, 0f, 0f), Vector3f(1.05f, 1.05f, 1.05f)),
                "head" to BoneTransform(Vector3f(0f, 1.6f, -0.2f), Vector3f(-0.8f, 0.2f, 0f), Vector3f(1.1f, 1.1f, 1.1f)) // Mouth open wide
            )),
            Keyframe(45f, mapOf("body" to BoneTransform.IDENTITY, "head" to BoneTransform(Vector3f(0f, 1.5f, 0f), Vector3f(0f, 0f, 0f), Vector3f(1f, 1f, 1f))))
        ))
        
        // SLEEP - Laying down
        animations["sleep"] = Animation("sleep", 60, listOf(
            Keyframe(0f, mapOf("body" to BoneTransform(Vector3f(0f, -0.8f, 0f), Vector3f(1.57f, 0f, 0f), Vector3f(1f, 1f, 1f)))),
            Keyframe(30f, mapOf("body" to BoneTransform(Vector3f(0f, -0.75f, 0f), Vector3f(1.57f, 0f, 0.1f), Vector3f(1.02f, 0.98f, 1.02f)))), // Breathe in
            Keyframe(60f, mapOf("body" to BoneTransform(Vector3f(0f, -0.8f, 0f), Vector3f(1.57f, 0f, 0f), Vector3f(1f, 1f, 1f)))) // Breathe out
        ))
        
        // SPIN - rapid rotation
         animations["spin"] = Animation("spin", 20, listOf(
            Keyframe(0f, mapOf("body" to BoneTransform.IDENTITY)),
            Keyframe(5f, mapOf("body" to BoneTransform(Vector3f(0f, 0.5f, 0f), Vector3f(0f, 1.57f, 0f), Vector3f(1f, 1f, 1f)))),
            Keyframe(10f, mapOf("body" to BoneTransform(Vector3f(0f, 0.5f, 0f), Vector3f(0f, 3.14f, 0f), Vector3f(1f, 1f, 1f)))),
            Keyframe(15f, mapOf("body" to BoneTransform(Vector3f(0f, 0.5f, 0f), Vector3f(0f, 4.71f, 0f), Vector3f(1f, 1f, 1f)))),
            Keyframe(20f, mapOf("body" to BoneTransform(Vector3f(0f, 0f, 0f), Vector3f(0f, 6.28f, 0f), Vector3f(1f, 1f, 1f))))
        ))
        
        // SLAM - Jump slam
        animations["slam"] = Animation("slam", 25, listOf(
            Keyframe(0f, mapOf("body" to BoneTransform.IDENTITY)),
            Keyframe(10f, mapOf("body" to BoneTransform(Vector3f(0f, 2.0f, 0f), Vector3f(-0.5f, 0f, 0f), Vector3f(1f, 1f, 1f)))), // Jump up
            Keyframe(15f, mapOf("body" to BoneTransform(Vector3f(0f, 0f, 0f), Vector3f(0.5f, 0f, 0f), Vector3f(1.2f, 0.8f, 1.2f)))), // Impact squish
            Keyframe(25f, mapOf("body" to BoneTransform.IDENTITY))
        ))
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CLEANUP
    // ═══════════════════════════════════════════════════════════════════════════
    
    fun cleanup() {
        activeModels.values.forEach { it.destroy() }
        activeModels.clear()
        // proceduralAnimator.cleanup() // If needed
    }
    
    fun getActiveModelCount(): Int = activeModels.size
    fun getAnimationCount(): Int = animations.size
    fun getModelBlueprintCount(): Int = modelBlueprints.size
}

// ═══════════════════════════════════════════════════════════════════════════════
// DATA CLASSES
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Represents an active animated model attached to an entity.
 */
data class AnimatedModel(
    val entityUuid: UUID,
    val entity: LivingEntity,
    val bones: Map<String, Bone>,
    val controller: AnimationController
) {
    fun destroy() {
        bones.values.forEach { bone ->
            if (bone.display.isValid) {
                bone.display.remove()
            }
        }
        controller.stop()
    }
}

/**
 * Individual bone in the skeleton (backed by an ItemDisplay).
 */
data class Bone(
    val name: String,
    val display: ItemDisplay,
    val defaultOffset: org.bukkit.util.Vector,
    val parentBone: String? = null
)

/**
 * Transform data for a single bone at a single point in time.
 */
data class BoneTransform(
    val position: Vector3f,
    val rotation: Vector3f, // Euler angles in radians
    val scale: Vector3f
) {
    companion object {
        val IDENTITY = BoneTransform(Vector3f(0f, 0f, 0f), Vector3f(0f, 0f, 0f), Vector3f(1f, 1f, 1f))
    }
    
    /**
     * Interpolate between this transform and another.
     */
    fun lerp(target: BoneTransform, t: Float): BoneTransform {
        return BoneTransform(
            position = Vector3f(position).lerp(target.position, t),
            rotation = Vector3f(rotation).lerp(target.rotation, t),
            scale = Vector3f(scale).lerp(target.scale, t)
        )
    }
}

/**
 * A keyframe in an animation - bones and their transforms at a specific time.
 */
data class Keyframe(
    val time: Float, // In ticks
    val bones: Map<String, BoneTransform>
)

/**
 * Complete animation definition with multiple keyframes.
 */
data class Animation(
    val name: String,
    val duration: Int, // Total duration in ticks
    val keyframes: List<Keyframe>
) {
    /**
     * Get interpolated frame at a given time (0.0 to 1.0 progress).
     */
    fun getFrameAt(progress: Float): Keyframe {
        val time = progress * duration
        
        // Find surrounding keyframes
        var prevFrame = keyframes.first()
        var nextFrame = keyframes.last()
        
        for (i in 0 until keyframes.size - 1) {
            if (keyframes[i].time <= time && keyframes[i + 1].time >= time) {
                prevFrame = keyframes[i]
                nextFrame = keyframes[i + 1]
                break
            }
        }
        
        // Calculate interpolation factor
        val frameRange = nextFrame.time - prevFrame.time
        val t = if (frameRange > 0) {
            ((time - prevFrame.time) / frameRange).coerceIn(0f, 1f)
        } else {
            0f
        }
        
        // Interpolate all bones
        val interpolatedBones = mutableMapOf<String, BoneTransform>()
        
        // Get all bone names from both frames
        val allBones = (prevFrame.bones.keys + nextFrame.bones.keys).toSet()
        
        allBones.forEach { boneName ->
            val prevTransform = prevFrame.bones[boneName] ?: BoneTransform.IDENTITY
            val nextTransform = nextFrame.bones[boneName] ?: BoneTransform.IDENTITY
            interpolatedBones[boneName] = prevTransform.lerp(nextTransform, t)
        }
        
        return Keyframe(time, interpolatedBones)
    }
}

/**
 * Controls animation playback for a model.
 */
class AnimationController {
    var currentAnimation: Animation? = null
        private set
    
    private var currentTick = 0f
    private var isLooping = true
    private var speed = 1.0f
    private var isPlaying = false
    
    private var currentFrame: Keyframe? = null
    
    fun playAnimation(animation: Animation, loop: Boolean = true, speed: Float = 1.0f) {
        this.currentAnimation = animation
        this.isLooping = loop
        this.speed = speed
        this.currentTick = 0f
        this.isPlaying = true
    }
    
    fun stop() {
        isPlaying = false
        currentAnimation = null
        currentFrame = null
    }
    
    fun tick() {
        if (!isPlaying || currentAnimation == null) return
        
        val anim = currentAnimation!!
        
        // Calculate progress
        val progress = (currentTick / anim.duration).coerceIn(0f, 1f)
        currentFrame = anim.getFrameAt(progress)
        
        // Advance time
        currentTick += speed
        
        // Handle looping or completion
        if (currentTick >= anim.duration) {
            if (isLooping) {
                currentTick = 0f
            } else {
                isPlaying = false
            }
        }
    }
    
    fun getCurrentFrame(): Keyframe? = currentFrame
    
    fun getCurrentTransform(boneName: String): BoneTransform? {
        return currentFrame?.bones?.get(boneName)
    }
    
    fun isAnimationComplete(): Boolean {
        return !isPlaying && currentAnimation != null
    }
}

/**
 * Blueprint for creating a model - defines the bone structure.
 */
data class ModelBlueprint(
    val name: String,
    val bones: List<BoneDefinition>
)

/**
 * Definition for a single bone in a blueprint.
 */
data class BoneDefinition(
    val name: String,
    val defaultOffset: org.bukkit.util.Vector,
    val itemStack: ItemStack,
    val defaultScale: Float = 1.0f,
    val parentBone: String? = null
)
