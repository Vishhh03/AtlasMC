package com.projectatlas

import com.projectatlas.city.CityManager
import com.projectatlas.gui.GuiManager
import com.projectatlas.identity.IdentityManager
import com.projectatlas.skills.SkillTreeManager
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

import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.event.EventPriority
import org.bukkit.block.Container

class AtlasListener(
    private val identityManager: IdentityManager,
    private val cityManager: CityManager,
    private val guiManager: GuiManager
) : Listener {
    
    private val plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(AtlasPlugin::class.java)
    private val shiftTimes = java.util.concurrent.ConcurrentHashMap<java.util.UUID, Long>()
    private val damageMap = java.util.concurrent.ConcurrentHashMap<java.util.UUID, java.util.concurrent.ConcurrentHashMap<java.util.UUID, Double>>()


    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        identityManager.createOrLoadProfile(event.player)
        // Apply Skill Tree bonuses
        plugin.skillTreeManager.applyAllSkillEffects(event.player)
    }

    @EventHandler
    fun onRespawn(event: PlayerRespawnEvent) {
        // Re-apply Skill Tree bonuses
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            plugin.skillTreeManager.applyAllSkillEffects(event.player)
        }, 5L)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        identityManager.unloadProfile(event.player.uniqueId)
    }
    
    // HOTKEY: Swap Hands (Default 'F') opens Atlas Menu
    // HOTKEY: Shift + F = Atlas Menu
    @EventHandler(priority = EventPriority.HIGH)
    fun onMenuHotkey(event: org.bukkit.event.player.PlayerSwapHandItemsEvent) {
        val player = event.player
        
        if (player.isSneaking) {
            event.isCancelled = true
            guiManager.openMainMenu(player)
        }
    }

    // HOTKEY: Double Shift = Toggle Visuals
    @EventHandler
    fun onToggleSneak(event: org.bukkit.event.player.PlayerToggleSneakEvent) {
        if (!event.isSneaking) return // Only on down press
        
        val player = event.player
        val now = System.currentTimeMillis()
        val last = shiftTimes.getOrDefault(player.uniqueId, 0L)
        
        if (now - last < 500) {
            plugin.visualManager.toggleVisuals(player)
            shiftTimes.remove(player.uniqueId)
        } else {
            shiftTimes[player.uniqueId] = now
        }
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
    // ═══════════════════════════════════════════════════════════════
    // XP SHARING & MOB LOGIC
    // ═══════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDamage(event: org.bukkit.event.entity.EntityDamageByEntityEvent) {
        if (event.entity !is org.bukkit.entity.LivingEntity || event.entity is org.bukkit.entity.Player) return
        
        val damager = when (val d = event.damager) {
             is org.bukkit.entity.Player -> d
             is org.bukkit.entity.Projectile -> d.shooter as? org.bukkit.entity.Player
             else -> null
        } ?: return
        
        val mobId = event.entity.uniqueId
        val damage = event.finalDamage
        
        // Record damage contribution
        damageMap.computeIfAbsent(mobId) { java.util.concurrent.ConcurrentHashMap() }
            .merge(damager.uniqueId, damage) { old, new -> old + new }
    }

    @EventHandler
    fun onMobKill(event: EntityDeathEvent) {
        val entity = event.entity
        val mobId = entity.uniqueId
        
        // Base XP Calculation: 10 + (Max HP / 2) -> Tougher mobs give more XP
        val maxHp = entity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
        val baseXp = 10.0 + (maxHp / 2.0)
        
        val contributors = damageMap.remove(mobId)
        
        // Fallback for one-shots or misses: Give full XP to killer
        if (contributors == null || contributors.isEmpty()) {
            entity.killer?.let { killer ->
                identityManager.grantXp(killer, baseXp.toLong())
                sendXpMessage(killer, baseXp.toLong())
            }
            return
        }
        
        val totalDamage = contributors.values.sum()
        if (totalDamage <= 0) return
        
        val partyManager = plugin.partyManager
        
        contributors.forEach { (playerId, damage) ->
            val player = org.bukkit.Bukkit.getPlayer(playerId)
            
            if (player != null && player.isOnline) {
                // Calculate "Earned Share" based on contribution
                val shareRatio = damage / totalDamage
                val earnedXp = baseXp * shareRatio
                
                // Get nearby party members
                val nearbyMembers = if (partyManager.isInParty(player)) {
                    partyManager.getOnlinePartyMembers(player)
                        .filter { it.world == entity.world && it.location.distance(entity.location) < 50.0 }
                } else {
                    emptyList()
                }
                
                if (nearbyMembers.isNotEmpty()) {
                    // Split the earned share among the party
                    val splitXp = (earnedXp / nearbyMembers.size).toLong()
                    if (splitXp > 0) {
                        nearbyMembers.forEach { member ->
                            identityManager.grantXp(member, splitXp)
                            if (member.uniqueId == playerId) {
                                sendXpMessage(member, splitXp, true)
                            }
                        }
                    }
                } else {
                    // Solo: Keep full earned share
                    val finalXp = earnedXp.toLong()
                    if (finalXp > 0) {
                        identityManager.grantXp(player, finalXp)
                        sendXpMessage(player, finalXp)
                    }
                }
            }
        }
    }
    
    private fun sendXpMessage(player: org.bukkit.entity.Player, amount: Long, isPartyParams: Boolean = false) {
        val msg = if (isPartyParams) " (Party Split)" else ""
        player.sendActionBar(Component.text("+$amount XP$msg", NamedTextColor.AQUA))
    }
}
