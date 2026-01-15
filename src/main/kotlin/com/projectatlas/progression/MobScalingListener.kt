package com.projectatlas.progression

import com.projectatlas.AtlasPlugin
import org.bukkit.attribute.Attribute
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Monster
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent

/**
 * Mob Scaling Listener - Scales mob stats based on nearby player era.
 * 
 * Higher era players = stronger mobs, creating a persistent challenge.
 */
class MobScalingListener(private val plugin: AtlasPlugin) : Listener {

    /**
     * Scale mob health when spawning naturally
     */
    @EventHandler(priority = EventPriority.NORMAL)
    fun onMobSpawn(event: CreatureSpawnEvent) {
        val entity = event.entity
        
        // Only scale hostile mobs
        if (entity !is Monster) return
        
        // Don't scale in certain cases
        if (event.spawnReason == CreatureSpawnEvent.SpawnReason.CUSTOM ||
            event.spawnReason == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) {
            return
        }
        
        // Get highest era among nearby players
        val era = plugin.progressionManager.getHighestNearbyPlayerEra(entity.location)
        val healthMult = plugin.progressionManager.getMobHealthMultiplier(era)
        
        // Apply health scaling
        if (healthMult > 1.0) {
            val maxHealthAttr = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH) ?: return
            val baseHealth = maxHealthAttr.baseValue
            val newHealth = baseHealth * healthMult
            
            maxHealthAttr.baseValue = newHealth
            entity.health = newHealth
        }
    }

    /**
     * Scale mob damage when attacking players
     */
    @EventHandler(priority = EventPriority.NORMAL)
    fun onMobAttack(event: EntityDamageByEntityEvent) {
        val damager = event.damager
        val victim = event.entity
        
        // Only scale Monster -> Player damage
        if (damager !is Monster || victim !is Player) return
        
        // Get the player's era
        val era = plugin.progressionManager.getPlayerEra(victim)
        val damageMult = plugin.progressionManager.getMobDamageMultiplier(era)
        
        // Apply damage scaling
        if (damageMult > 1.0) {
            event.damage = event.damage * damageMult
        }
    }
}
