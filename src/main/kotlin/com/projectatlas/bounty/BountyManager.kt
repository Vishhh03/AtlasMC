package com.projectatlas.bounty

import com.projectatlas.AtlasPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import com.projectatlas.achievements.AchievementManager

/**
 * Bounty System - Players can place bounties on other players.
 * When a bounty target is killed, the killer claims the reward!
 */
class BountyManager(private val plugin: AtlasPlugin) : Listener {
    
    data class Bounty(
        val targetUUID: UUID,
        val targetName: String,
        val placedBy: UUID,
        val amount: Double,
        val reason: String = "Wanted",
        val timestamp: Long = System.currentTimeMillis()
    )
    
    // Combat Tracking for "Legitimate Kill" check
    // Attacker UUID -> Map<Victim UUID, Timestamp>
    private val lastDamageDealt = ConcurrentHashMap<UUID, MutableMap<UUID, Long>>()
    
    private val activeBounties = ConcurrentHashMap<UUID, MutableList<Bounty>>()
    
    fun placeBounty(placer: Player, targetName: String, amount: Double, reason: String = "Wanted"): Boolean {
        val target = Bukkit.getOfflinePlayer(targetName)
        if (!target.hasPlayedBefore() && !target.isOnline) {
            placer.sendMessage(Component.text("Player not found!", NamedTextColor.RED))
            return false
        }
        
        if (target.uniqueId == placer.uniqueId) {
            placer.sendMessage(Component.text("You can't place a bounty on yourself!", NamedTextColor.RED))
            return false
        }
        
        if (amount < plugin.configManager.bountyMinimum) {
            placer.sendMessage(Component.text("Minimum bounty is ${plugin.configManager.bountyMinimum}g!", NamedTextColor.RED))
            return false
        }
        
        val profile = plugin.identityManager.getPlayer(placer.uniqueId) ?: return false
        if (profile.balance < amount) {
            placer.sendMessage(Component.text("Insufficient funds!", NamedTextColor.RED))
            return false
        }
        
        // Deduct and create bounty
        profile.balance -= amount
        plugin.identityManager.saveProfile(placer.uniqueId)
        
        val bounty = Bounty(target.uniqueId, target.name ?: targetName, placer.uniqueId, amount, reason)
        activeBounties.getOrPut(target.uniqueId) { mutableListOf() }.add(bounty)
        
        // Announce!
        plugin.server.broadcast(Component.empty())
        plugin.server.broadcast(Component.text("💀 BOUNTY PLACED 💀", NamedTextColor.DARK_RED, TextDecoration.BOLD))
        plugin.server.broadcast(Component.text("Target: ${target.name}", NamedTextColor.RED))
        plugin.server.broadcast(Component.text("Reward: ${amount}g", NamedTextColor.GOLD))
        plugin.server.broadcast(Component.text("Reason: $reason", NamedTextColor.GRAY))
        plugin.server.broadcast(Component.empty())
        
        placer.playSound(placer.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f)
        return true
    }
    
    fun getTotalBounty(uuid: UUID): Double {
        return activeBounties[uuid]?.sumOf { it.amount } ?: 0.0
    }
    
    fun getBounties(uuid: UUID): List<Bounty> {
        return activeBounties[uuid]?.toList() ?: emptyList()
    }
    
    fun listAllBounties(): List<Bounty> {
        return activeBounties.values.flatten().sortedByDescending { it.amount }
    }
    
    @EventHandler
    fun onPlayerKill(event: PlayerDeathEvent) {
        val victim = event.entity
        val killer = victim.killer ?: return
        
        val bounties = activeBounties.remove(victim.uniqueId) ?: return
        if (bounties.isEmpty()) return
        
        val totalReward = bounties.sumOf { it.amount }
        

        
        // Pay the killer
        val killerProfile = plugin.identityManager.getPlayer(killer.uniqueId)
        if (killerProfile != null) {
            // "Legitimate Kill" Check (Anti-Abuse)
            // Victim must have damaged killer in last 60 seconds
            val lastHit = lastDamageDealt[victim.uniqueId]?.get(killer.uniqueId) ?: 0L
            val isFightingBack = (System.currentTimeMillis() - lastHit) < 60000 
            
            if (!isFightingBack) {
                killer.sendMessage(Component.text("⚠ Bounty withheld: Target was not fighting back!", NamedTextColor.RED))
                killer.sendMessage(Component.text("To claim bounties, the target must be an active combatant.", NamedTextColor.GRAY))
                // Refund bounties or keep them active? 
                // Typically keep active if failed claim.
                // Re-add bounties
                activeBounties[victim.uniqueId] = bounties
                return
            }
        
            // Tax deduction (20% sink)
            val taxRate = 0.20
            val tax = totalReward * taxRate
            val payout = totalReward - tax
            
            killerProfile.balance += payout
            plugin.identityManager.saveProfile(killer.uniqueId)
            
            // Announce
            plugin.server.broadcast(Component.empty())
            plugin.server.broadcast(Component.text("⚔ BOUNTY CLAIMED ⚔", NamedTextColor.GOLD, TextDecoration.BOLD))
            plugin.server.broadcast(Component.text("${killer.name} killed ${victim.name}!", NamedTextColor.YELLOW))
            plugin.server.broadcast(Component.text("Reward: ${payout}g (Tax: ${tax}g)", NamedTextColor.GOLD))
            plugin.server.broadcast(Component.empty())
        } else {
             // Fallback for offline/error
             activeBounties[victim.uniqueId] = bounties
             return
        }
        
        killer.playSound(killer.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f)
        
        // Visual effects
        plugin.visualManager.showBountyClaimEffect(victim.location, totalReward)
        plugin.visualManager.showPlayerKillEffect(victim.location)
        
        // Achievement: Bounty Hunter
        plugin.achievementManager.awardAchievement(killer, "bounty_hunter")
        
        // Achievement: Wanted (for victim who had a bounty)
        plugin.achievementManager.awardAchievement(victim, "wanted")
        
        // Log to history if victim had a city
        val victimProfile = plugin.identityManager.getPlayer(victim.uniqueId)
        victimProfile?.cityId?.let { cityId ->
            plugin.historyManager.logEvent(cityId, "Citizen ${victim.name}'s bounty was claimed by ${killer.name}", com.projectatlas.history.EventType.POLITICS)
        }
    }
}
