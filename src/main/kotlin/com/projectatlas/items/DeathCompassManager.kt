package com.projectatlas.items

import com.projectatlas.AtlasPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.CompassMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.NamespacedKey
import org.bukkit.Location

/**
 * Manages death compass - gives players a recovery compass pointing to their death location.
 */
class DeathCompassManager(private val plugin: AtlasPlugin) : Listener {
    
    private val deathLocationKey = NamespacedKey(plugin, "death_location")
    
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.entity
        val deathLocation = player.location
        
        // Store death location in player data
        val pdc = player.persistentDataContainer
        pdc.set(deathLocationKey, PersistentDataType.STRING, 
            "${deathLocation.world.name},${deathLocation.x},${deathLocation.y},${deathLocation.z}")
        
        // Vanilla Minecraft automatically sets the recovery compass target to the last death location
        // BUT for custom logic or if we want to force it, we need to handle it properly.
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        
        // Check if player has a stored death location
        val pdc = player.persistentDataContainer
        val deathData = pdc.get(deathLocationKey, PersistentDataType.STRING) ?: return
        
        // Create death compass
        val deathCompass = createDeathCompass(deathData, player.location)
        
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
    
    private fun createDeathCompass(deathData: String, respawnLoc: Location): ItemStack {
        val parts = deathData.split(",")
        val worldName = parts[0]
        val x = parts[1].toDouble().toInt()
        val y = parts[2].toDouble().toInt()
        val z = parts[3].toDouble().toInt()
        
        // In vanilla, Recovery Compass automatically points to LastDeathLocation NBT.
        // However, if we want to be sure, we can try to rely on vanilla behavior.
        // If the death happened in a different dimension, recovery compass scrambles.
        
        return ItemStack(Material.RECOVERY_COMPASS).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Death Compass", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD))
                lore(listOf(
                    Component.text("Points to your last death location", NamedTextColor.GRAY),
                    Component.text(""),
                    Component.text("☠ Death Location:", NamedTextColor.RED),
                    Component.text("  World: $worldName", NamedTextColor.DARK_GRAY),
                    Component.text("  X: $x, Y: $y, Z: $z", NamedTextColor.DARK_GRAY),
                    Component.text(""),
                    Component.text("The compass needle points the way...", NamedTextColor.DARK_PURPLE, TextDecoration.ITALIC)
                ))
                
                // If we want it to work like a regular compass but strictly for death:
                // Recovery compass logic is hardcoded in client to "LastDeathLocation".
                // If it's not pointing right, maybe the client didn't receive the death location packet?
                // Or maybe we should use a LODESTONE compass instead if we want manual control.
                
                if (this is CompassMeta) {
                    this.isLodestoneTracked = false
                    this.lodestone = org.bukkit.Bukkit.getWorld(worldName)?.let { 
                        Location(it, parts[1].toDouble(), parts[2].toDouble(), parts[3].toDouble()) 
                    }
                }
            }
        }
    }
}
