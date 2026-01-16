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
    private val supplyDropCreationTimes = ConcurrentHashMap<String, Long>()
    
    private val guardKey = NamespacedKey.fromString("atlas_guard")!!
    private val dropDurationMillis = 20 * 60 * 1000L // 20 Minutes (Syncd with guardians)
    
    init {
        // Expiry Checker Task (Run every minute)
        plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            checkExpiredDrops()
        }, 1200L, 1200L)
        
        // Dynamic Glowing Task (Run every 2 seconds)
        // Applies glowing only when player is nearby (< 40 blocks) to simulate "short range" glow
        plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            updateGuardianGlowing()
        }, 40L, 40L)
    }

    private fun updateGuardianGlowing() {
        supplyDropGuardians.values.forEach { guardianSet ->
            guardianSet.forEach { uuid ->
                val entity = plugin.server.getEntity(uuid) as? LivingEntity ?: return@forEach
                if (!entity.isValid) return@forEach
                
                // Check for nearby players
                val nearbyPlayer = entity.getNearbyEntities(40.0, 40.0, 40.0).any { it is Player }
                
                if (nearbyPlayer) {
                    // Apply glowing for 2.5 seconds (overlapping slightly with 2s task)
                    entity.addPotionEffect(org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.GLOWING, 
                        50, // 2.5s
                        0, 
                        false, 
                        false // No Particles
                    ))
                }
            }
        }
    }

    /**
     * Register a supply drop chest and its guardians
     */
    fun registerSupplyDrop(chestLocation: org.bukkit.Location, guardians: List<LivingEntity>) {
        val locKey = "${chestLocation.blockX},${chestLocation.blockY},${chestLocation.blockZ},${chestLocation.world?.name}"
        supplyDropGuardians[locKey] = guardians.map { it.uniqueId }.toMutableSet()
        supplyDropInitialCount[locKey] = guardians.size
        supplyDropCreationTimes[locKey] = System.currentTimeMillis()
    }
    
    private fun checkExpiredDrops() {
        val now = System.currentTimeMillis()
        val toRemove = mutableListOf<String>()
        
        supplyDropCreationTimes.forEach { (locKey, creationTime) ->
            if (now - creationTime > dropDurationMillis) {
                expireDrop(locKey)
                toRemove.add(locKey)
            }
        }
        
        toRemove.forEach { supplyDropCreationTimes.remove(it) }
    }

    private fun expireDrop(locKey: String) {
        val parts = locKey.split(",")
        if (parts.size != 4) return
        
        val world = plugin.server.getWorld(parts[3]) ?: return
        val x = parts[0].toIntOrNull() ?: return
        val y = parts[1].toIntOrNull() ?: return
        val z = parts[2].toIntOrNull() ?: return
        
        val loc = org.bukkit.Location(world, x.toDouble(), y.toDouble(), z.toDouble())
        
        // 1. Remove Chest
        if (loc.block.type == Material.CHEST) {
            loc.block.type = Material.AIR
            world.spawnParticle(org.bukkit.Particle.CLOUD, loc.add(0.5, 0.5, 0.5), 20, 0.5, 0.5, 0.5, 0.1)
            world.playSound(loc, org.bukkit.Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 1.0f)
        }
        
        // 2. Remove Guardians
        supplyDropGuardians[locKey]?.forEach { uuid ->
            plugin.server.getEntity(uuid)?.remove()
        }
        
        // 3. Cleanup Maps
        supplyDropGuardians.remove(locKey)
        supplyDropInitialCount.remove(locKey)
        
        // 4. Broadcast
        plugin.server.broadcast(Component.text("The Supply Drop at $x, $z has expired and vanished!", NamedTextColor.GRAY))
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
            supplyDropCreationTimes.remove(locKey)
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
            supplyDropCreationTimes.remove(locKey)
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
