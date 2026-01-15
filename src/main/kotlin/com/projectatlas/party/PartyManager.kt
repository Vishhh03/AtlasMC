package com.projectatlas.party

import com.projectatlas.AtlasPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent

/**
 * Party System - Group players together for dungeons and raids!
 */
class PartyManager(private val plugin: AtlasPlugin) : Listener {
    
    data class Party(
        val id: UUID = UUID.randomUUID(),
        var leader: UUID,
        val members: MutableSet<UUID> = mutableSetOf(),
        val pendingInvites: MutableSet<UUID> = mutableSetOf()
    ) {
        fun size() = members.size
        fun isFull() = members.size >= MAX_PARTY_SIZE
        fun isLeader(uuid: UUID) = leader == uuid
        fun isMember(uuid: UUID) = members.contains(uuid)
    }
    
    companion object {
        const val MAX_PARTY_SIZE = 4
        const val INVITE_TIMEOUT_MS = 60_000L // 1 minute
    }
    
    private val parties = ConcurrentHashMap<UUID, Party>() // Party ID -> Party
    private val playerParties = ConcurrentHashMap<UUID, UUID>() // Player UUID -> Party ID
    private val pendingInvites = ConcurrentHashMap<UUID, Pair<UUID, Long>>() // Target UUID -> (Party ID, Timestamp)
    
    fun createParty(leader: Player): Party? {
        if (playerParties.containsKey(leader.uniqueId)) {
            leader.sendMessage(Component.text("You're already in a party! Leave first with /atlas party leave", NamedTextColor.RED))
            return null
        }
        
        val party = Party(leader = leader.uniqueId)
        party.members.add(leader.uniqueId)
        
        parties[party.id] = party
        playerParties[leader.uniqueId] = party.id
        
        leader.sendMessage(Component.text("═══ PARTY CREATED ═══", NamedTextColor.GREEN, TextDecoration.BOLD))
        leader.sendMessage(Component.text("You are the party leader!", NamedTextColor.YELLOW))
        leader.sendMessage(Component.text("Invite players with: /atlas party invite <player>", NamedTextColor.GRAY))
        leader.playSound(leader.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f)
        
        return party
    }
    
    fun invitePlayer(inviter: Player, targetName: String): Boolean {
        val partyId = playerParties[inviter.uniqueId]
        if (partyId == null) {
            inviter.sendMessage(Component.text("You're not in a party! Create one first.", NamedTextColor.RED))
            return false
        }
        
        val party = parties[partyId] ?: return false
        
        if (!party.isLeader(inviter.uniqueId)) {
            inviter.sendMessage(Component.text("Only the party leader can invite players.", NamedTextColor.RED))
            return false
        }
        
        if (party.isFull()) {
            inviter.sendMessage(Component.text("Party is full! (Max: $MAX_PARTY_SIZE)", NamedTextColor.RED))
            return false
        }
        
        val target = Bukkit.getPlayer(targetName)
        if (target == null) {
            inviter.sendMessage(Component.text("Player not found or offline.", NamedTextColor.RED))
            return false
        }
        
        if (playerParties.containsKey(target.uniqueId)) {
            inviter.sendMessage(Component.text("${target.name} is already in a party.", NamedTextColor.RED))
            return false
        }
        
        // Send invite
        pendingInvites[target.uniqueId] = partyId to System.currentTimeMillis()
        
        inviter.sendMessage(Component.text("Invited ${target.name} to your party!", NamedTextColor.GREEN))
        
        target.sendMessage(Component.empty())
        target.sendMessage(Component.text("═══ PARTY INVITE ═══", NamedTextColor.GOLD, TextDecoration.BOLD))
        target.sendMessage(Component.text("${inviter.name} invited you to their party!", NamedTextColor.YELLOW))
        target.sendMessage(Component.text("Type /atlas party accept to join", NamedTextColor.GREEN))
        target.sendMessage(Component.text("Type /atlas party decline to refuse", NamedTextColor.RED))
        target.sendMessage(Component.text("Expires in 60 seconds...", NamedTextColor.GRAY))
        target.sendMessage(Component.empty())
        target.playSound(target.location, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f)
        
        return true
    }
    
    fun acceptInvite(player: Player): Boolean {
        val invite = pendingInvites.remove(player.uniqueId)
        if (invite == null) {
            player.sendMessage(Component.text("You don't have any pending invites.", NamedTextColor.RED))
            return false
        }
        
        val (partyId, timestamp) = invite
        if (System.currentTimeMillis() - timestamp > INVITE_TIMEOUT_MS) {
            player.sendMessage(Component.text("That invite has expired.", NamedTextColor.RED))
            return false
        }
        
        val party = parties[partyId]
        if (party == null || party.isFull()) {
            player.sendMessage(Component.text("That party no longer exists or is full.", NamedTextColor.RED))
            return false
        }
        
        // Join party
        party.members.add(player.uniqueId)
        playerParties[player.uniqueId] = partyId
        
        // Notify everyone
        broadcastToParty(party, Component.text("${player.name} has joined the party!", NamedTextColor.GREEN))
        player.sendMessage(Component.text("You joined the party!", NamedTextColor.GREEN))
        player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f)
        
        return true
    }
    
    fun declineInvite(player: Player) {
        val invite = pendingInvites.remove(player.uniqueId)
        if (invite != null) {
            player.sendMessage(Component.text("Invite declined.", NamedTextColor.YELLOW))
            val party = parties[invite.first]
            if (party != null) {
                Bukkit.getPlayer(party.leader)?.sendMessage(
                    Component.text("${player.name} declined your party invite.", NamedTextColor.RED)
                )
            }
        }
    }
    
    fun leaveParty(player: Player): Boolean {
        val partyId = playerParties.remove(player.uniqueId) ?: return false
        val party = parties[partyId] ?: return false
        
        party.members.remove(player.uniqueId)
        player.sendMessage(Component.text("You left the party.", NamedTextColor.YELLOW))
        
        if (party.members.isEmpty()) {
            // Disband
            parties.remove(partyId)
        } else if (party.leader == player.uniqueId) {
            // Transfer leadership
            val newLeader = party.members.first()
            party.leader = newLeader
            broadcastToParty(party, Component.text("${player.name} left. ${Bukkit.getOfflinePlayer(newLeader).name} is now the leader!", NamedTextColor.YELLOW))
        } else {
            broadcastToParty(party, Component.text("${player.name} left the party.", NamedTextColor.YELLOW))
        }
        
        return true
    }
    
    fun kickPlayer(leader: Player, targetName: String): Boolean {
        val partyId = playerParties[leader.uniqueId] ?: return false
        val party = parties[partyId] ?: return false
        
        if (!party.isLeader(leader.uniqueId)) {
            leader.sendMessage(Component.text("Only the leader can kick players.", NamedTextColor.RED))
            return false
        }
        
        val target = Bukkit.getOfflinePlayer(targetName)
        if (!party.members.contains(target.uniqueId)) {
            leader.sendMessage(Component.text("Player not in your party.", NamedTextColor.RED))
            return false
        }
        
        if (target.uniqueId == leader.uniqueId) {
            leader.sendMessage(Component.text("You can't kick yourself!", NamedTextColor.RED))
            return false
        }
        
        party.members.remove(target.uniqueId)
        playerParties.remove(target.uniqueId)
        
        broadcastToParty(party, Component.text("${target.name} was kicked from the party.", NamedTextColor.RED))
        Bukkit.getPlayer(target.uniqueId)?.sendMessage(Component.text("You were kicked from the party.", NamedTextColor.RED))
        
        return true
    }
    
    fun getParty(player: Player): Party? {
        val partyId = playerParties[player.uniqueId] ?: return null
        return parties[partyId]
    }
    
    fun getPartyMembers(player: Player): List<UUID> {
        return getParty(player)?.members?.toList() ?: listOf(player.uniqueId)
    }
    
    fun getOnlinePartyMembers(player: Player): List<Player> {
        return getPartyMembers(player).mapNotNull { Bukkit.getPlayer(it) }
    }
    
    fun isInParty(player: Player): Boolean = playerParties.containsKey(player.uniqueId)
    
    fun isPartyLeader(player: Player): Boolean {
        val party = getParty(player) ?: return false
        return party.isLeader(player.uniqueId)
    }
    
    fun disbandParty(player: Player): Boolean {
        val partyId = playerParties[player.uniqueId] ?: return false
        val party = parties[partyId] ?: return false
        
        if (!party.isLeader(player.uniqueId)) {
            player.sendMessage(Component.text("Only the leader can disband the party.", NamedTextColor.RED))
            return false
        }
        
        broadcastToParty(party, Component.text("The party has been disbanded!", NamedTextColor.RED))
        
        party.members.forEach { playerParties.remove(it) }
        parties.remove(partyId)
        
        return true
    }
    
    fun showPartyInfo(player: Player) {
        val party = getParty(player)
        if (party == null) {
            player.sendMessage(Component.text("You're not in a party.", NamedTextColor.GRAY))
            player.sendMessage(Component.text("Create one with: /atlas party create", NamedTextColor.YELLOW))
            return
        }
        
        player.sendMessage(Component.text("═══ YOUR PARTY ═══", NamedTextColor.GOLD, TextDecoration.BOLD))
        player.sendMessage(Component.text("Members: ${party.size()}/$MAX_PARTY_SIZE", NamedTextColor.YELLOW))
        
        party.members.forEach { memberId ->
            val member = Bukkit.getOfflinePlayer(memberId)
            val status = if (member.isOnline) "§a●" else "§c○"
            val role = if (party.isLeader(memberId)) " §6[Leader]" else ""
            player.sendMessage(Component.text("  $status ${member.name}$role"))
        }
    }
    
    private fun broadcastToParty(party: Party, message: Component) {
        party.members.mapNotNull { Bukkit.getPlayer(it) }.forEach { it.sendMessage(message) }
    }
    
    /**
     * Party Chat - Send a message only to party members
     */
    fun sendPartyChat(sender: Player, message: String) {
        val party = getParty(sender)
        if (party == null) {
            sender.sendMessage(Component.text("You're not in a party!", NamedTextColor.RED))
            return
        }
        
        val formatted = Component.text("[Party] ", NamedTextColor.AQUA)
            .append(Component.text("${sender.name}: ", NamedTextColor.WHITE))
            .append(Component.text(message, NamedTextColor.GRAY))
        
        party.members.mapNotNull { Bukkit.getPlayer(it) }.forEach { member ->
            member.sendMessage(formatted)
            member.playSound(member.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.3f, 1.5f)
        }
    }
    
    // ═══════════════════════════════════════════════════════════════
    // EVENT HANDLERS
    // ═══════════════════════════════════════════════════════════════
    
    @EventHandler
    fun onEntityDamage(event: EntityDamageByEntityEvent) {
        val victim = event.entity as? Player ?: return
        val attacker = when(val d = event.damager) {
            is Player -> d
            is org.bukkit.entity.Projectile -> d.shooter as? Player
            else -> null
        } ?: return
        
        if (victim == attacker) return
        
        val partyA = playerParties[attacker.uniqueId] ?: return
        val partyB = playerParties[victim.uniqueId] ?: return
        
        if (partyA == partyB) {
            event.isCancelled = true
            attacker.sendActionBar(Component.text("Friendly Fire!", NamedTextColor.RED))
        }
    }
}
