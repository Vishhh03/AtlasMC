package com.projectatlas.structures

import com.projectatlas.AtlasPlugin
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class StructureHealthManager(private val plugin: AtlasPlugin) {

    private val healthMap = ConcurrentHashMap<UUID, StructureHealth>()
    private val locationMap = ConcurrentHashMap<UUID, StructureLocation>()
    
    data class StructureHealth(
        val structureId: UUID,
        val type: com.projectatlas.structures.StructureType,
        var currentHealth: Double,
        val maxHealth: Double,
        var isRuined: Boolean = false
    ) {
        fun getPercent(): Double = currentHealth / maxHealth
        
        fun getPhase(): DamagePhase {
            val pct = getPercent()
            return when {
                pct > 0.75 -> DamagePhase.PRISTINE
                pct > 0.50 -> DamagePhase.WORN
                pct > 0.25 -> DamagePhase.DAMAGED
                pct > 0.0 -> DamagePhase.CRITICAL
                else -> DamagePhase.RUINED
            }
        }
    }
    
    data class StructureLocation(val center: Location, val radius: Double)
    
    enum class DamagePhase {
        PRISTINE, WORN, DAMAGED, CRITICAL, RUINED
    }

    fun registerStructure(id: UUID, type: com.projectatlas.structures.StructureType, maxHealth: Double, center: Location, radius: Double) {
        healthMap[id] = StructureHealth(id, type, maxHealth, maxHealth)
        locationMap[id] = StructureLocation(center, radius)
    }
    
    fun getAllStructures(): Map<UUID, StructureHealth> = healthMap

    fun findStructureAt(location: Location): UUID? {
        // Simple distance check for now. Optimization: Spatial hash or chunk map
        return locationMap.entries.firstOrNull { (_, loc) ->
            loc.center.world == location.world && loc.center.distanceSquared(location) <= loc.radius * loc.radius
        }?.key
    }

    fun damageStructure(id: UUID, amount: Double): DamagePhase? {
        val health = healthMap[id] ?: return null
        if (health.isRuined) return DamagePhase.RUINED
        
        health.currentHealth = (health.currentHealth - amount).coerceAtLeast(0.0)
        
        if (health.currentHealth <= 0) {
            health.isRuined = true
            return DamagePhase.RUINED
        }
        
        return health.getPhase()
    }
    
    fun getHealth(id: UUID): StructureHealth? = healthMap[id]
    
    fun findStructureLocation(id: UUID): StructureLocation? = locationMap[id]
}
