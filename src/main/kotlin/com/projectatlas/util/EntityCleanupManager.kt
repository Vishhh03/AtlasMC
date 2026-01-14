package com.projectatlas.util

import com.projectatlas.AtlasPlugin
import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataType
import org.bukkit.scheduler.BukkitRunnable

class EntityCleanupManager(private val plugin: AtlasPlugin) {

    private val expiryKey = NamespacedKey(plugin, "atlas_expiry")

    init {
        startCleanupTask()
    }

    private fun startCleanupTask() {
        object : BukkitRunnable() {
            override fun run() {
                var removedCount = 0
                val currentTime = System.currentTimeMillis()
                
                plugin.server.worlds.forEach { world ->
                    world.entities.forEach { entity ->
                        // Check for expiry tag
                        val expiry = entity.persistentDataContainer.get(expiryKey, PersistentDataType.LONG)
                        if (expiry != null) {
                            if (currentTime > expiry) {
                                entity.remove()
                                removedCount++
                            }
                        }
                    }
                }
                
                if (removedCount > 0) {
                    plugin.logger.info("Cleaned up $removedCount expired entities.")
                }
            }
        }.runTaskTimer(plugin, 1200L, 1200L) // Run every 60 seconds (20 ticks * 60)
    }

    /**
     * Mark an entity for auto-despawn after a duration
     * @param entity The entity to mark
     * @param durationSeconds How long the entity should live (in seconds)
     */
    fun markForDespawn(entity: org.bukkit.entity.Entity, durationSeconds: Long) {
        val expiryTime = System.currentTimeMillis() + (durationSeconds * 1000)
        entity.persistentDataContainer.set(expiryKey, PersistentDataType.LONG, expiryTime)
    }
}
