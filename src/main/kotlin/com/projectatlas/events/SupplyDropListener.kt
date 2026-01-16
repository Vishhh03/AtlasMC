package com.projectatlas.events

import com.projectatlas.AtlasPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.Chest
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.persistence.PersistentDataType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Listener for Supply Drop mechanics:
 * - Tracks guardians around supply drops
 * - Blocks chest opening until at least half the guardians are killed
 */
class SupplyDropListener(private val plugin: AtlasPlugin) : Listener {

    // Track supply drop chests and their associated guardians
    // ChestLocation (as string) -> Set of Guardian UUIDs
    private val supplyDropGuardians = ConcurrentHashMap<String, MutableSet<UUID>>()
    private val supplyDropInitialCount = ConcurrentHashMap<String, Int>()
    
    private val guardKey = NamespacedKey.fromString("atlas_guard")!!
    
    /**
     * Register a supply drop chest and its guardians
     */
    fun registerSupplyDrop(chestLocation: org.bukkit.Location, guardians: List<LivingEntity>) {
        val locKey = "${chestLocation.blockX},${chestLocation.blockY},${chestLocation.blockZ},${chestLocation.world?.name}"
        supplyDropGuardians[locKey] = guardians.map { it.uniqueId }.toMutableSet()
        supplyDropInitialCount[locKey] = guardians.size
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    fun onGuardianDeath(event: EntityDeathEvent) {
        val entity = event.entity as? LivingEntity ?: return
        
        // Check if this is a supply drop guardian
        if (!entity.persistentDataContainer.has(guardKey, PersistentDataType.BYTE)) return
        
        // Remove from all tracked supply drops
        supplyDropGuardians.values.forEach { guardianSet ->
            guardianSet.remove(entity.uniqueId)
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onChestOpen(event: PlayerInteractEvent) {
        val block = event.clickedBlock ?: return
        if (block.type != Material.CHEST) return
        
        val locKey = "${block.x},${block.y},${block.z},${block.world.name}"
        
        // Check if this is a supply drop chest
        val guardians = supplyDropGuardians[locKey] ?: return
        val initialCount = supplyDropInitialCount[locKey] ?: return
        
        // Count alive guardians
        val aliveCount = guardians.count { uuid ->
            plugin.server.getEntity(uuid)?.let { !it.isDead } ?: false
        }
        
        val halfCount = (initialCount + 1) / 2 // Round up
        
        // Lock until ALL guardians are dead (user request)
        if (aliveCount > 0) {
            event.isCancelled = true
            val player = event.player
            player.sendMessage(Component.empty())
            player.sendMessage(Component.text("═══════════════════════════════", NamedTextColor.RED))
            player.sendMessage(Component.text("  ⚠ SUPPLY DROP LOCKED ⚠", NamedTextColor.RED))
            player.sendMessage(Component.text("  Kill ALL the guardians first!", NamedTextColor.YELLOW))
            player.sendMessage(Component.text("  Guardians remaining: $aliveCount / $initialCount", NamedTextColor.GRAY))
            player.sendMessage(Component.text("  Need to kill: $aliveCount more", NamedTextColor.GOLD))
            player.sendMessage(Component.text("═══════════════════════════════", NamedTextColor.RED))
            player.sendMessage(Component.empty())
            player.playSound(player.location, org.bukkit.Sound.BLOCK_CHEST_LOCKED, 1.0f, 0.8f)
        } else {
            // Unlock message on first successful open
            if (aliveCount > 0) {
                event.player.sendMessage(Component.text("☑ Supply drop unlocked! Remaining guardians will disperse soon.", NamedTextColor.GREEN))
                
                // Accelerate despawn of remaining guardians to 1 minute
                val cleanupManager = com.projectatlas.util.EntityCleanupManager(plugin) // Just to access key, or better use manual key
                val expiryKey = NamespacedKey(plugin, "atlas_expiry")
                val shortExpiry = System.currentTimeMillis() + (60 * 1000) // 1 minute
                
                guardians.mapNotNull { plugin.server.getEntity(it) }.forEach { entity ->
                    entity.persistentDataContainer.set(expiryKey, PersistentDataType.LONG, shortExpiry)
                }
            }
            // Clean up tracking
            supplyDropGuardians.remove(locKey)
            supplyDropInitialCount.remove(locKey)
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onChestBreak(event: org.bukkit.event.block.BlockBreakEvent) {
        val block = event.block
        if (block.type != Material.CHEST) return
        
        val locKey = "${block.x},${block.y},${block.z},${block.world.name}"
        
        // If it's a locked supply drop, verify guardians first
        val guardians = supplyDropGuardians[locKey]
        if (guardians != null) {
            val initialCount = supplyDropInitialCount[locKey] ?: 0
            val aliveCount = guardians.count { uuid -> plugin.server.getEntity(uuid)?.let { !it.isDead } ?: false }
            val halfCount = (initialCount + 1) / 2
            
            if (aliveCount >= halfCount) {
                event.isCancelled = true
                event.player.sendMessage(Component.text("⚠ Cannot break locked supply drop! Kill guardians first.", NamedTextColor.RED))
                return
            }
            
            // If breakable (unlocked), accelerate despawn of guardians
            val expiryKey = NamespacedKey(plugin, "atlas_expiry")
            val shortExpiry = System.currentTimeMillis() + (60 * 1000) // 1 minute
            
            guardians.mapNotNull { plugin.server.getEntity(it) }.forEach { entity ->
                entity.persistentDataContainer.set(expiryKey, PersistentDataType.LONG, shortExpiry)
            }
            
            supplyDropGuardians.remove(locKey)
            supplyDropInitialCount.remove(locKey)
        }
    }
    
    /**
     * Auto-register guardians spawned near a chest
     */
    fun trackGuardiansNearChest(chestLocation: org.bukkit.Location) {
        val world = chestLocation.world ?: return
        val locKey = "${chestLocation.blockX},${chestLocation.blockY},${chestLocation.blockZ},${world.name}"
        
        // Find all guardians within 15 blocks
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            val nearbyGuardians = world.getNearbyEntities(chestLocation, 15.0, 15.0, 15.0)
                .filterIsInstance<LivingEntity>()
                .filter { it.persistentDataContainer.has(guardKey, PersistentDataType.BYTE) }
            
            if (nearbyGuardians.isNotEmpty()) {
                supplyDropGuardians[locKey] = nearbyGuardians.map { it.uniqueId }.toMutableSet()
                supplyDropInitialCount[locKey] = nearbyGuardians.size
            }
        }, 20L) // Wait 1 second for all entities to spawn
    }
}
