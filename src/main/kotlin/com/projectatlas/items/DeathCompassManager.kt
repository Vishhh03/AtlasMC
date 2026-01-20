package com.projectatlas.items

import com.projectatlas.AtlasPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.event.player.PlayerPickupItemEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.CompassMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.NamespacedKey
import org.bukkit.Location

/**
 * Manages death compass - gives players a recovery compass pointing to their death location.
 * 
 * Features:
 * - On death: Player receives a compass pointing to their death location
 * - Death history: If player dies WHILE holding a death compass, that compass's target
 *   location is preserved. When they collect it later, it still points to that older death.
 * - New compasses always point to the most recent death location
 * - Each compass stores its own target location in persistent data
 */
class DeathCompassManager(private val plugin: AtlasPlugin) : Listener {
    
    // Key for player's latest death location (stored on player)
    private val latestDeathKey = NamespacedKey(plugin, "latest_death_location")
    
    // Key for compass's target death location (stored on the item itself)
    private val compassTargetKey = NamespacedKey(plugin, "compass_death_target")
    
    // Key to identify this as a death compass
    private val deathCompassMarker = NamespacedKey(plugin, "is_death_compass")
    
    // Key to track death number/timestamp for ordering
    private val deathTimestampKey = NamespacedKey(plugin, "death_timestamp")
    
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.entity
        val deathLocation = player.location
        val deathTime = System.currentTimeMillis()
        
        val deathData = "${deathLocation.world.name},${deathLocation.x},${deathLocation.y},${deathLocation.z}"
        
        // Store the latest death location on the player
        val playerPdc = player.persistentDataContainer
        playerPdc.set(latestDeathKey, PersistentDataType.STRING, deathData)
        
        // Check if player died with a death compass in their inventory
        // If so, the compass retains its stored location - no changes needed since
        // each compass stores its own target in its item PDC
        
        // Log for debugging
        val compassCount = player.inventory.contents.filterNotNull().count { isDeathCompass(it) }
        if (compassCount > 0) {
            plugin.logger.info("[DeathCompass] ${player.name} died with $compassCount death compass(es) - they will retain their original targets")
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        
        // Check if player has a stored death location
        val pdc = player.persistentDataContainer
        val deathData = pdc.get(latestDeathKey, PersistentDataType.STRING) ?: return
        
        // Create death compass pointing to the LATEST death
        val deathCompass = createDeathCompass(deathData, System.currentTimeMillis())
        
        // Give compass after a short delay
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            player.inventory.addItem(deathCompass)
            player.sendMessage(Component.text(""))
            player.sendMessage(Component.text("☠ ", NamedTextColor.DARK_RED)
                .append(Component.text("You received a ", NamedTextColor.GRAY))
                .append(Component.text("Death Compass", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD))
                .append(Component.text(" pointing to your death location.", NamedTextColor.GRAY)))
            
            plugin.logger.info("[DeathCompass] Gave death compass to ${player.name}")
        }, 5L)
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    fun onItemPickup(event: EntityPickupItemEvent) {
        if (event.entity !is Player) return
        val player = event.entity as Player
        val item = event.item.itemStack
        
        // Check if this is a death compass
        if (!isDeathCompass(item)) return
        
        // Get the compass's stored target location
        val meta = item.itemMeta ?: return
        val targetData = meta.persistentDataContainer.get(compassTargetKey, PersistentDataType.STRING) ?: return
        val timestamp = meta.persistentDataContainer.get(deathTimestampKey, PersistentDataType.LONG) ?: 0L
        
        // Notify player they picked up a compass with a specific death location
        val parts = targetData.split(",")
        if (parts.size >= 4) {
            val worldName = parts[0]
            val x = parts[1].toDouble().toInt()
            val y = parts[2].toDouble().toInt()
            val z = parts[3].toDouble().toInt()
            
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                player.sendMessage(Component.text(""))
                player.sendMessage(Component.text("🧭 ", NamedTextColor.GOLD)
                    .append(Component.text("You recovered a ", NamedTextColor.GRAY))
                    .append(Component.text("Death Compass", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD))
                    .append(Component.text("!", NamedTextColor.GRAY)))
                player.sendMessage(Component.text("   → Points to: ", NamedTextColor.DARK_GRAY)
                    .append(Component.text("$worldName (X: $x, Y: $y, Z: $z)", NamedTextColor.RED)))
            }, 1L)
            
            plugin.logger.info("[DeathCompass] ${player.name} picked up a death compass pointing to $worldName ($x, $y, $z)")
        }
    }
    
    /**
     * Check if an item is a death compass
     */
    fun isDeathCompass(item: ItemStack): Boolean {
        if (item.type != Material.COMPASS && item.type != Material.RECOVERY_COMPASS) return false
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(deathCompassMarker, PersistentDataType.BYTE)
    }
    
    /**
     * Get the target location stored in a death compass
     */
    fun getCompassTarget(item: ItemStack): Location? {
        if (!isDeathCompass(item)) return null
        val meta = item.itemMeta ?: return null
        val targetData = meta.persistentDataContainer.get(compassTargetKey, PersistentDataType.STRING) ?: return null
        return parseLocationData(targetData)
    }
    
    private fun parseLocationData(data: String): Location? {
        val parts = data.split(",")
        if (parts.size < 4) return null
        val world = Bukkit.getWorld(parts[0]) ?: return null
        return Location(world, parts[1].toDouble(), parts[2].toDouble(), parts[3].toDouble())
    }
    
    private fun createDeathCompass(deathData: String, timestamp: Long): ItemStack {
        val parts = deathData.split(",")
        val worldName = parts[0]
        val x = parts[1].toDouble().toInt()
        val y = parts[2].toDouble().toInt()
        val z = parts[3].toDouble().toInt()
        
        // Use COMPASS (lodestone compass) instead of RECOVERY_COMPASS so we can control the target
        return ItemStack(Material.COMPASS).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Death Compass", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD))
                lore(listOf(
                    Component.text("Points to a death location", NamedTextColor.GRAY),
                    Component.text(""),
                    Component.text("☠ Death Location:", NamedTextColor.RED),
                    Component.text("  World: $worldName", NamedTextColor.DARK_GRAY),
                    Component.text("  X: $x, Y: $y, Z: $z", NamedTextColor.DARK_GRAY),
                    Component.text(""),
                    Component.text("Die with this compass to preserve", NamedTextColor.DARK_PURPLE, TextDecoration.ITALIC),
                    Component.text("this location for later recovery", NamedTextColor.DARK_PURPLE, TextDecoration.ITALIC)
                ))
                
                // Store compass-specific data in the item's PDC
                persistentDataContainer.set(deathCompassMarker, PersistentDataType.BYTE, 1)
                persistentDataContainer.set(compassTargetKey, PersistentDataType.STRING, deathData)
                persistentDataContainer.set(deathTimestampKey, PersistentDataType.LONG, timestamp)
                
                // Make the compass point to the death location using lodestone mechanics
                if (this is CompassMeta) {
                    this.isLodestoneTracked = false
                    this.lodestone = Bukkit.getWorld(worldName)?.let { 
                        Location(it, parts[1].toDouble(), parts[2].toDouble(), parts[3].toDouble()) 
                    }
                }
            }
        }
    }
}
