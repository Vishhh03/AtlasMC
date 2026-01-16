package com.projectatlas.siege

import com.projectatlas.AtlasPlugin
import com.projectatlas.structures.StructureHealthManager.StructureHealth
import com.projectatlas.structures.StructureType
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.GameMode
import org.bukkit.Sound
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import java.util.UUID

// Roles defined as constants on Mob PersistentData
object SiegeRoles {
    const val ROLE_KEY = "siege_role"
    const val BREACHER = "BREACHER" // Targets Structures
    const val GRUNT = "GRUNT"       // Targets Players
    const val SABOTEUR = "SABOTEUR" // Targets Generators/Core
}

class SiegeMobBehavior(private val plugin: AtlasPlugin) : BukkitRunnable() {

    private val namespacedKey = org.bukkit.NamespacedKey(plugin, SiegeRoles.ROLE_KEY)
    private var tick = 0

    override fun run() {
        tick++
        // Run major logic every 20 ticks (1s) to save perf, but check attacks more often?
        // Let's settle on every 20 ticks for target updates.
        
        val allSieges = getActiveSieges() // Helper to get sieges from SiegeManager
        allSieges.forEach { siege ->
            processSiege(siege)
        }
    }
    
    // We need access to SiegeManager's list. Assuming SiegeManager has a getter.
    // Since SiegeManager.activeSieges is private in original code, we might need to modify SiegeManager to expose it or make this an inner class.
    // For now, let's assume we modify SiegeManager to make activeSieges accessible or use a getter.
    // Actually, I'll put this logic INSIDE a method in SiegeManager or make SiegeManager pass the list.
    // But to keep file separation, I'll use a public getter I'll add to SiegeManager.
    
    private fun getActiveSieges(): Collection<SiegeManager.ActiveSiege> {
        return plugin.siegeManager.getAllActiveSieges()
    }

    private fun processSiege(siege: SiegeManager.ActiveSiege) {
        val world = plugin.server.getWorld(siege.cityId) ?: return // Assume cityId maps to world? Or verify world context.
        // Actually cityId is a string ID. SiegeManager logic knows the world implicitly via mobs.
        
        siege.spawnedMobs.toList().forEach { mobId ->
            val entity = plugin.server.getEntity(mobId) as? Mob ?: return@forEach
            if (!entity.isValid) return@forEach
            
            val role = entity.persistentDataContainer.get(namespacedKey, org.bukkit.persistence.PersistentDataType.STRING) ?: SiegeRoles.GRUNT
            
            when (role) {
                SiegeRoles.BREACHER -> handleBreacher(entity, siege)
                SiegeRoles.GRUNT -> handleGrunt(entity, siege)
                SiegeRoles.SABOTEUR -> handleSaboteur(entity, siege)
            }
        }
    }
    
    private fun handleBreacher(mob: Mob, siege: SiegeManager.ActiveSiege) {
        // Priority: Defense Structures (Turrets, Barracks)
        // 1. Find nearest active structure of high priority
        val targetStruct = findNearestStructure(mob, listOf(StructureType.BARRACKS, StructureType.TURRET))
        
        if (targetStruct != null) {
            moveToAndAttackStructure(mob, targetStruct)
        } else {
            // Fallback to Grunt behavior
            handleGrunt(mob, siege)
        }
    }
    
    private fun handleSaboteur(mob: Mob, siege: SiegeManager.ActiveSiege) {
        // Priority: Generators -> Nexus -> Turrets
        val targetStruct = findNearestStructure(mob, listOf(StructureType.GENERATOR, StructureType.NEXUS))
        
        if (targetStruct != null) {
            moveToAndAttackStructure(mob, targetStruct)
        } else {
            handleGrunt(mob, siege)
        }
    }
    
    private fun handleGrunt(mob: Mob, siege: SiegeManager.ActiveSiege) {
        // Priority: Players
        // If no players, attack nearest structure
        if (mob.target is Player && (mob.target as Player).gameMode != GameMode.CREATIVE) return // Already happy
        
        val nearestPlayer = mob.world.getNearbyEntities(mob.location, 30.0, 10.0, 30.0)
            .filterIsInstance<Player>()
            .filter { it.gameMode != GameMode.CREATIVE && it.gameMode != GameMode.SPECTATOR }
            .minByOrNull { it.location.distanceSquared(mob.location) }
            
        if (nearestPlayer != null) {
            mob.target = nearestPlayer
        } else {
            // Attack any structure
            val anyStruct = findNearestStructure(mob, null)
            if (anyStruct != null) {
                moveToAndAttackStructure(mob, anyStruct)
            }
        }
    }
    
    // --- Helpers ---
    
    private fun findNearestStructure(mob: Mob, types: List<StructureType>?): UUID? {
        val structures = plugin.structureHealthManager.getAllStructures()
        var nearestId: UUID? = null
        var minDistSq = Double.MAX_VALUE
        
        structures.forEach { (id, health) ->
            if (health.isRuined) return@forEach
            if (types != null && !types.contains(health.type)) return@forEach
            
            val locData = plugin.structureHealthManager.findStructureLocation(id) ?: return@forEach
            if (locData.center.world != mob.world) return@forEach
            
            val distSq = locData.center.distanceSquared(mob.location)
            if (distSq < minDistSq && distSq < 10000) { // 100 block radius max
                minDistSq = distSq
                nearestId = id
            }
        }
        return nearestId
    }
    
    private fun moveToAndAttackStructure(mob: Mob, structId: UUID) {
        val locData = plugin.structureHealthManager.findStructureLocation(structId) ?: return
        val targetLoc = locData.center
        
        // Navigation (Paper Pathfinding API or vanilla hack)
        mob.pathfinder.moveTo(targetLoc)
        
        // Attack Logic: Check distance
        if (mob.location.distanceSquared(targetLoc) < 9.0) { // < 3 blocks
            // "Attack"
            plugin.structureHealthManager.damageStructure(structId, 5.0) // 5 dmg per tick? Too fast.
            // This runs every 20 ticks (1s) -> 5 DPS. Reasonable.
            
            // Visuals
            mob.swingMainHand()
            mob.world.playSound(mob.location, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.5f, 0.5f)
            mob.world.spawnParticle(org.bukkit.Particle.CRIT, targetLoc.clone().add(0.0, 1.0, 0.0), 3)
        }
    }
}
