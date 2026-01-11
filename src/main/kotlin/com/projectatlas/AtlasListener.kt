package com.projectatlas

import com.projectatlas.city.CityManager
import com.projectatlas.identity.IdentityManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class AtlasListener(
    private val identityManager: IdentityManager,
    private val cityManager: CityManager
) : Listener {

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        identityManager.createOrLoadProfile(event.player)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        identityManager.unloadProfile(event.player.uniqueId)
    }

    // Protection Logic
    @EventHandler
    fun onBreak(event: BlockBreakEvent) {
        val city = cityManager.getCityAt(event.block.chunk) ?: return
        val player = event.player
        
        // If city exists, check if player is a member
        if (!city.members.contains(player.uniqueId) && !player.hasPermission("atlas.admin")) {
            event.isCancelled = true
            player.sendMessage(Component.text("You cannot build in the territory of ${city.name}!", NamedTextColor.RED))
        }
    }

    @EventHandler
    fun onPlace(event: BlockPlaceEvent) {
        val city = cityManager.getCityAt(event.block.chunk) ?: return
        val player = event.player
        
        if (!city.members.contains(player.uniqueId) && !player.hasPermission("atlas.admin")) {
            event.isCancelled = true
            player.sendMessage(Component.text("You cannot build in the territory of ${city.name}!", NamedTextColor.RED))
        }
    }
}
