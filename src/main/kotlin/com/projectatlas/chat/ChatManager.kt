package com.projectatlas.chat

import com.projectatlas.AtlasPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.entity.Player

class ChatManager(private val plugin: AtlasPlugin) : Listener {

    @EventHandler
    fun onChat(event: AsyncPlayerChatEvent) {
        event.isCancelled = true
        val player = event.player
        val message = event.message
        
        // Channel Logic
        // Prefixes: "!" -> Global, "@" -> City, "#" -> Party
        
        if (message.startsWith("!")) {
            if (message.length > 1) sendGlobalChat(player, message.substring(1).trim())
        } else if (message.startsWith("@")) {
            if (message.length > 1) sendCityChat(player, message.substring(1).trim())
        } else if (message.startsWith("#")) {
            if (message.length > 1) sendPartyChat(player, message.substring(1).trim())
        } else {
            sendLocalChat(player, message)
        }
    }

    private fun sendGlobalChat(player: Player, msg: String) {
        if (msg.isBlank()) return
        val profile = plugin.identityManager.getPlayer(player.uniqueId)
        val city = profile?.cityId?.let { plugin.cityManager.getCity(it)?.name } ?: "Nomad"
        
        // [G] [City] Name: Message
        val component = Component.text("[G] ", NamedTextColor.DARK_GRAY)
            .append(Component.text("[$city] ", NamedTextColor.GOLD))
            .append(Component.text(player.name, NamedTextColor.WHITE))
            .append(Component.text(": $msg", NamedTextColor.GRAY))
            
        plugin.server.broadcast(component)
    }
    
    private fun sendLocalChat(player: Player, msg: String) {
        if (msg.isBlank()) return
        val radius = 50.0
        val component = Component.text("[L] ", NamedTextColor.BLUE)
            .append(Component.text(player.name, NamedTextColor.WHITE))
            .append(Component.text(": $msg", NamedTextColor.WHITE))
            
        var count = 0
        val onlinePlayers = plugin.server.onlinePlayers
        
        // Simple distance check (safe for async if position is read carefully, but AsyncPlayerChatEvent allows some access)
        // Note: Accessing entities async can be risky. For radius chat, better to just iterate online players.
        
        onlinePlayers.forEach { recipient ->
             if (recipient.world == player.world && recipient.location.distanceSquared(player.location) <= radius * radius) {
                 recipient.sendMessage(component)
                 count++
             }
        }
        
        if (count <= 1) { // Only self
            player.sendMessage(Component.text("(No one heard you. Use ! for Global)", NamedTextColor.DARK_GRAY))
        }
    }
    
    private fun sendCityChat(player: Player, msg: String) {
        val profile = plugin.identityManager.getPlayer(player.uniqueId)
        val cityId = profile?.cityId
        if (cityId == null) {
            player.sendMessage(Component.text("You are not in a city!", NamedTextColor.RED))
            return
        }
        
        val city = plugin.cityManager.getCity(cityId) ?: return
        val component = Component.text("[City] ${player.name}: $msg", NamedTextColor.AQUA)
        
        city.members.forEach { uuid ->
            val member = plugin.server.getPlayer(uuid)
            member?.sendMessage(component)
        }
    }
    
    private fun sendPartyChat(player: Player, msg: String) {
         if (!plugin.partyManager.isInParty(player)) {
            player.sendMessage(Component.text("You are not in a party!", NamedTextColor.RED))
            return
        }
        
        val partyMembers = plugin.partyManager.getOnlinePartyMembers(player)
        val component = Component.text("[Party] ${player.name}: $msg", NamedTextColor.LIGHT_PURPLE)
        
        partyMembers.forEach { it.sendMessage(component) }
    }
}
