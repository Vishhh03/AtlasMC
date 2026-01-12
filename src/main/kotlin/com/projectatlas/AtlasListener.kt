package com.projectatlas

import com.projectatlas.city.CityManager
import com.projectatlas.gui.GuiManager
import com.projectatlas.identity.IdentityManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDeathEvent

import com.projectatlas.classes.ClassManager
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.event.EventPriority
import org.bukkit.block.Container

class AtlasListener(
    private val identityManager: IdentityManager,
    private val cityManager: CityManager,
    private val classManager: ClassManager,
    private val guiManager: GuiManager
) : Listener {
    
    private val plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(AtlasPlugin::class.java)

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        identityManager.createOrLoadProfile(event.player)
        classManager.applyClassEffects(event.player)
    }

    @EventHandler
    fun onRespawn(event: PlayerRespawnEvent) {
        classManager.applyClassEffects(event.player)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        identityManager.unloadProfile(event.player.uniqueId)
    }
    
    // HOTKEY: Swap Hands (Default 'F') opens Atlas Menu
    @EventHandler(priority = EventPriority.HIGH)
    fun onMenuHotkey(event: org.bukkit.event.player.PlayerSwapHandItemsEvent) {
        val player = event.player
        
        // Ensure sneaking to avoid accidental triggers/navigation conflict? 
        // User requested "hotkey", usually F. Let's make it Shift+F to be safe, or just F.
        // Let's go with just F, but cancel the swap so they don't actually swap.
        
        event.isCancelled = true
        guiManager.openMainMenu(player)
    }

    // Protection Logic
    @EventHandler
    fun onBreak(event: BlockBreakEvent) {
        val city = cityManager.getCityAt(event.block.chunk) ?: return
        val player = event.player
        
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
    
    @EventHandler(priority = EventPriority.LOW)
    fun onContainerAccess(event: PlayerInteractEvent) {
        val block = event.clickedBlock ?: return
        val city = cityManager.getCityAt(block.chunk) ?: return
        val player = event.player
        
        if (block.state !is Container) return
        
        if (!city.members.contains(player.uniqueId) && !player.hasPermission("atlas.admin")) {
            event.isCancelled = true
            player.sendMessage(Component.text("You cannot access containers in ${city.name}!", NamedTextColor.RED))
        }
    }
    @EventHandler
    fun onMobKill(event: EntityDeathEvent) {
        val killer = event.entity.killer ?: return
        // Grant 10 XP per kill
        identityManager.grantXp(killer, 10L)
    }
}
