package com.projectatlas.siege

import com.projectatlas.AtlasPlugin
import com.projectatlas.structures.StructureType
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.sin

// Roles defined as constants on Mob PersistentData
object SiegeRoles {
    const val ROLE_KEY = "siege_role"
    const val GRUNT = "GRUNT"           // Targets Players
    const val SNIPER = "SNIPER"         // Ranged, targets players from distance
    const val BREACHER = "BREACHER"     // Targets Structures
    const val SABOTEUR = "SABOTEUR"     // Targets Turrets → Generators → Core
    const val COMMANDER = "COMMANDER"   // Boss mob, coordinates squads, buffs allies
}

/**
 * Smart Siege Mob AI System
 * 
 * Features:
 * - Target Distribution: Prevents all mobs from piling on same target
 * - Threat Assessment: Saboteurs prioritize disabling defenses first
 * - Role Memory: Mobs stick to targets for stability
 * - Commander Coordination: Boss assigns objectives to squads
 * - Anti-Cluster: Spread spawning and flanking behavior
 */
class SiegeMobBehavior(private val plugin: AtlasPlugin) : BukkitRunnable() {

    private val namespacedKey = org.bukkit.NamespacedKey(plugin, SiegeRoles.ROLE_KEY)
    private val targetMemoryKey = org.bukkit.NamespacedKey(plugin, "siege_target")
    private var tick = 0
    
    // Target distribution tracking: structureId -> number of mobs assigned
    private val structureAssignments = ConcurrentHashMap<UUID, Int>()
    
    // Player target distribution: playerUUID -> number of mobs targeting
    private val playerAssignments = ConcurrentHashMap<UUID, Int>()
    
    companion object {
        const val TARGET_MEMORY_TICKS = 100  // 5 seconds before re-evaluating
        const val MAX_MOBS_PER_STRUCTURE = 3 // Max attackers on one structure
        const val MAX_MOBS_PER_PLAYER = 4    // Max attackers on one player
        const val SPAWN_RADIUS = 25.0        // Radius for spread spawning
    }

    override fun run() {
        tick++
        
        // Clear assignments every 5 seconds for refresh
        if (tick % 100 == 0) {
            structureAssignments.clear()
            playerAssignments.clear()
        }
        
        val allSieges = getActiveSieges()
        allSieges.forEach { siege ->
            if (siege.isBattle()) {
                processSiege(siege)
            }
        }
    }
    
    private fun getActiveSieges(): Collection<SiegeManager.ActiveSiege> {
        return plugin.siegeManager.getAllActiveSieges()
    }

    private fun processSiege(siege: SiegeManager.ActiveSiege) {
        siege.spawnedMobs.toList().forEach { mobId ->
            val entity = plugin.server.getEntity(mobId) as? Mob ?: return@forEach
            if (!entity.isValid || entity.isDead) return@forEach
            
            val role = entity.persistentDataContainer.get(
                namespacedKey, 
                org.bukkit.persistence.PersistentDataType.STRING
            ) ?: SiegeRoles.GRUNT
            
            when (role) {
                SiegeRoles.BREACHER -> handleBreacher(entity, siege)
                SiegeRoles.GRUNT -> handleGrunt(entity, siege)
                SiegeRoles.SNIPER -> handleSniper(entity, siege)
                SiegeRoles.SABOTEUR -> handleSaboteur(entity, siege)
                SiegeRoles.COMMANDER -> handleCommander(entity, siege)
            }
        }
    }
    
    // ═══════════════════════════════════════════════════════════════
    // SABOTEUR: Smart threat assessment (disables defenses first)
    // ═══════════════════════════════════════════════════════════════
    
    private fun handleSaboteur(mob: Mob, siege: SiegeManager.ActiveSiege) {
        // Check if we have a memorized target
        if (hasValidMemorizedTarget(mob)) return
        
        // Priority 1: Active turrets (biggest threat)
        val turret = findLeastContestedStructure(mob, listOf(StructureType.TURRET))
        if (turret != null) {
            assignAndMoveToStructure(mob, turret)
            return
        }
        
        // Priority 2: Generators (power source)
        val generator = findLeastContestedStructure(mob, listOf(StructureType.GENERATOR))
        if (generator != null) {
            assignAndMoveToStructure(mob, generator)
            return
        }
        
        // Priority 3: Nexus/Core
        val nexus = findLeastContestedStructure(mob, listOf(StructureType.NEXUS))
        if (nexus != null) {
            assignAndMoveToStructure(mob, nexus)
            return
        }
        
        // Fallback: Attack players
        handleGrunt(mob, siege)
    }
    
    // ═══════════════════════════════════════════════════════════════
    // BREACHER: Smart structure targeting with distribution
    // ═══════════════════════════════════════════════════════════════
    
    private fun handleBreacher(mob: Mob, siege: SiegeManager.ActiveSiege) {
        if (hasValidMemorizedTarget(mob)) return
        
        // Priority: Barracks -> Turrets -> Walls -> Any structure
        val priorities = listOf(
            listOf(StructureType.BARRACKS),
            listOf(StructureType.TURRET),
            listOf(StructureType.NEXUS)
        )
        
        for (structTypes in priorities) {
            val target = findLeastContestedStructure(mob, structTypes)
            if (target != null) {
                assignAndMoveToStructure(mob, target)
                return
            }
        }
        
        // Fallback
        handleGrunt(mob, siege)
    }
    
    // ═══════════════════════════════════════════════════════════════
    // GRUNT: Smart player targeting with distribution
    // ═══════════════════════════════════════════════════════════════
    
    private fun handleGrunt(mob: Mob, siege: SiegeManager.ActiveSiege) {
        // If already targeting a valid player, stick with it
        val currentTarget = mob.target
        if (currentTarget is Player && 
            currentTarget.gameMode != GameMode.CREATIVE && 
            currentTarget.gameMode != GameMode.SPECTATOR &&
            !currentTarget.isDead) {
            return
        }
        
        // Find least contested player
        val players = mob.world.getNearbyEntities(mob.location, 40.0, 15.0, 40.0)
            .filterIsInstance<Player>()
            .filter { it.gameMode != GameMode.CREATIVE && it.gameMode != GameMode.SPECTATOR }
        
        if (players.isEmpty()) {
            // No players, attack structures
            val anyStruct = findLeastContestedStructure(mob, null)
            if (anyStruct != null) {
                assignAndMoveToStructure(mob, anyStruct)
            }
            return
        }
        
        // Sort by: (assignment count, distance) - prefer less contested, closer players
        val bestTarget = players.minWithOrNull(compareBy(
            { playerAssignments[it.uniqueId] ?: 0 },
            { it.location.distanceSquared(mob.location) }
        ))
        
        if (bestTarget != null) {
            // Check if already at max assignments
            val currentAssigned = playerAssignments[bestTarget.uniqueId] ?: 0
            if (currentAssigned < MAX_MOBS_PER_PLAYER || players.size == 1) {
                playerAssignments[bestTarget.uniqueId] = currentAssigned + 1
                mob.target = bestTarget
            } else {
                // All players contested, just pick nearest
                val nearest = players.minByOrNull { it.location.distanceSquared(mob.location) }
                mob.target = nearest
            }
        }
    }
    
    // ═══════════════════════════════════════════════════════════════
    // SNIPER: Ranged combat with distance keeping
    // ═══════════════════════════════════════════════════════════════
    
    private fun handleSniper(mob: Mob, siege: SiegeManager.ActiveSiege) {
        val players = mob.world.getNearbyEntities(mob.location, 45.0, 15.0, 45.0)
            .filterIsInstance<Player>()
            .filter { it.gameMode != GameMode.CREATIVE && it.gameMode != GameMode.SPECTATOR }
        
        if (players.isEmpty()) {
            handleGrunt(mob, siege)
            return
        }
        
        // Target lowest HP player (sniper priority)
        val target = players.minByOrNull { it.health }!!
        val distance = mob.location.distance(target.location)
        
        // Optimal range: 12-20 blocks
        when {
            distance < 10.0 -> {
                // Too close, retreat
                val direction = mob.location.toVector()
                    .subtract(target.location.toVector())
                    .normalize()
                val retreatLoc = mob.location.clone().add(direction.multiply(6.0))
                mob.pathfinder.moveTo(retreatLoc)
            }
            distance > 22.0 -> {
                // Too far, advance
                mob.pathfinder.moveTo(target.location)
            }
        }
        mob.target = target
    }
    
    // ═══════════════════════════════════════════════════════════════
    // COMMANDER: Squad coordination and tactical leadership
    // ═══════════════════════════════════════════════════════════════
    
    private fun handleCommander(mob: Mob, siege: SiegeManager.ActiveSiege) {
        // 1. Analyze squad composition nearby
        val nearbyMobs = siege.spawnedMobs
            .mapNotNull { plugin.server.getEntity(it) as? LivingEntity }
            .filter { it.uniqueId != mob.uniqueId && it.location.distanceSquared(mob.location) < 400 } // 20 blocks
        
        val squadSize = nearbyMobs.size
        
        // 2. Apply buffs based on situation
        if (squadSize >= 3) {
            // Squad is healthy - apply strength buff
            nearbyMobs.forEach { ally ->
                if (!ally.hasPotionEffect(PotionEffectType.STRENGTH)) {
                    ally.addPotionEffect(PotionEffect(PotionEffectType.STRENGTH, 100, 0, false, true))
                }
            }
        }
        
        // 3. Rally cry when mobs are low (frenzy mode)
        if (siege.mobsRemaining < siege.mobsKilled / 3) {
            // Desperate push - give speed and strength
            nearbyMobs.forEach { ally ->
                if (!ally.hasPotionEffect(PotionEffectType.SPEED)) {
                    ally.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 200, 1, false, true))
                    ally.addPotionEffect(PotionEffect(PotionEffectType.STRENGTH, 200, 1, false, true))
                }
            }
            // Play rally sound
            if (tick % 40 == 0) {
                mob.world.playSound(mob.location, Sound.ENTITY_EVOKER_PREPARE_SUMMON, 2.0f, 0.5f)
            }
        }
        
        // 4. Commander targets players aggressively
        val nearestPlayer = mob.world.getNearbyEntities(mob.location, 50.0, 15.0, 50.0)
            .filterIsInstance<Player>()
            .filter { it.gameMode != GameMode.CREATIVE && it.gameMode != GameMode.SPECTATOR }
            .minByOrNull { it.location.distanceSquared(mob.location) }
        
        if (nearestPlayer != null) {
            mob.target = nearestPlayer
            mob.pathfinder.moveTo(nearestPlayer.location)
            
            // AOE stomp when close
            if (mob.location.distanceSquared(nearestPlayer.location) < 16.0 && tick % 40 == 0) {
                performAOEAttack(mob)
            }
        } else {
            // No players, lead charge on structures
            val targetStruct = findLeastContestedStructure(mob, listOf(StructureType.NEXUS, StructureType.BARRACKS))
            if (targetStruct != null) {
                assignAndMoveToStructure(mob, targetStruct)
            }
        }
    }
    
    private fun performAOEAttack(mob: Mob) {
        mob.world.getNearbyEntities(mob.location, 4.0, 2.0, 4.0)
            .filterIsInstance<Player>()
            .filter { it.gameMode != GameMode.CREATIVE }
            .forEach { player ->
                player.damage(4.0, mob)
                player.velocity = player.location.toVector()
                    .subtract(mob.location.toVector())
                    .normalize()
                    .multiply(0.6)
                    .setY(0.4)
            }
        mob.world.playSound(mob.location, Sound.ENTITY_RAVAGER_ROAR, 1.5f, 0.7f)
        mob.world.spawnParticle(org.bukkit.Particle.EXPLOSION, mob.location, 5)
    }
    
    // ═══════════════════════════════════════════════════════════════
    // TARGET DISTRIBUTION SYSTEM
    // ═══════════════════════════════════════════════════════════════
    
    private fun findLeastContestedStructure(mob: Mob, types: List<StructureType>?): UUID? {
        val structures = plugin.structureHealthManager.getAllStructures()
        
        data class StructureCandidate(
            val id: UUID,
            val distance: Double,
            val assignments: Int,
            val priority: Int // Lower = higher priority
        )
        
        val candidates = mutableListOf<StructureCandidate>()
        
        structures.forEach { (id, health) ->
            if (health.isRuined) return@forEach
            if (types != null && !types.contains(health.type)) return@forEach
            
            val locData = plugin.structureHealthManager.findStructureLocation(id) ?: return@forEach
            if (locData.center.world != mob.world) return@forEach
            
            val distSq = locData.center.distanceSquared(mob.location)
            if (distSq > 10000) return@forEach // 100 block max
            
            val assignments = structureAssignments[id] ?: 0
            val priority = when (health.type) {
                StructureType.TURRET -> 1
                StructureType.GENERATOR -> 2
                StructureType.BARRACKS -> 3
                StructureType.NEXUS -> 4
                else -> 5
            }
            
            candidates.add(StructureCandidate(id, kotlin.math.sqrt(distSq), assignments, priority))
        }
        
        if (candidates.isEmpty()) return null
        
        // Sort by: assignments (fewer first), then priority, then distance
        return candidates.minWithOrNull(compareBy(
            { if (it.assignments >= MAX_MOBS_PER_STRUCTURE) 100 else it.assignments },
            { it.priority },
            { it.distance }
        ))?.id
    }
    
    private fun assignAndMoveToStructure(mob: Mob, structId: UUID) {
        val locData = plugin.structureHealthManager.findStructureLocation(structId) ?: return
        val targetLoc = locData.center
        
        // Record assignment
        structureAssignments[structId] = (structureAssignments[structId] ?: 0) + 1
        
        // Store target in memory
        mob.persistentDataContainer.set(
            targetMemoryKey,
            org.bukkit.persistence.PersistentDataType.STRING,
            structId.toString()
        )
        
        // Navigate
        mob.pathfinder.moveTo(targetLoc)
        
        // Attack if close
        if (mob.location.distanceSquared(targetLoc) < 12.0) {
            plugin.structureHealthManager.damageStructure(structId, 5.0)
            mob.swingMainHand()
            mob.world.playSound(mob.location, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.6f, 0.5f)
            mob.world.spawnParticle(org.bukkit.Particle.CRIT, targetLoc.clone().add(0.0, 1.0, 0.0), 5)
        }
    }
    
    private fun hasValidMemorizedTarget(mob: Mob): Boolean {
        val memorized = mob.persistentDataContainer.get(
            targetMemoryKey,
            org.bukkit.persistence.PersistentDataType.STRING
        ) ?: return false
        
        try {
            val structId = UUID.fromString(memorized)
            val health = plugin.structureHealthManager.getHealth(structId)
            
            // Target still valid?
            if (health != null && !health.isRuined) {
                val locData = plugin.structureHealthManager.findStructureLocation(structId) ?: return false
                mob.pathfinder.moveTo(locData.center)
                
                // Attack if close
                if (mob.location.distanceSquared(locData.center) < 12.0) {
                    plugin.structureHealthManager.damageStructure(structId, 5.0)
                    mob.swingMainHand()
                    mob.world.playSound(mob.location, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.6f, 0.5f)
                }
                return true
            }
        } catch (e: Exception) {
            // Invalid UUID, clear memory
        }
        
        // Clear memory
        mob.persistentDataContainer.remove(targetMemoryKey)
        return false
    }
    
    // ═══════════════════════════════════════════════════════════════
    // SPREAD SPAWN FORMATION
    // ═══════════════════════════════════════════════════════════════
    
    /**
     * Spawns mobs in a spread formation around the center point
     * Called from SiegeManager when spawning waves
     */
    fun getSpreadSpawnLocations(center: Location, count: Int): List<Location> {
        val locations = mutableListOf<Location>()
        val angleStep = (2 * Math.PI) / count
        
        for (i in 0 until count) {
            val angle = angleStep * i
            val x = center.x + cos(angle) * SPAWN_RADIUS
            val z = center.z + sin(angle) * SPAWN_RADIUS
            
            val spawnLoc = Location(center.world, x, center.y, z)
            // Find safe Y level
            val block = spawnLoc.world?.getHighestBlockAt(spawnLoc.blockX, spawnLoc.blockZ)
            if (block != null) {
                spawnLoc.y = block.y.toDouble() + 1.0
            }
            locations.add(spawnLoc)
        }
        
        return locations
    }
}
