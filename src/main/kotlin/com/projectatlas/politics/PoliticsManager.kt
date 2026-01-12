package com.projectatlas.politics

import com.projectatlas.AtlasPlugin
import com.projectatlas.city.City
import com.projectatlas.history.EventType
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class Election(
    val cityId: String,
    val startTime: Long = System.currentTimeMillis(),
    val votes: MutableMap<UUID, UUID> = mutableMapOf(), // Voter -> Candidate
    val candidates: MutableSet<UUID> = mutableSetOf()
) {
    // Election runs for 5 minutes for testing (Real game would be 24h)
    val endTime: Long = startTime + (5 * 60 * 1000) 
}

class PoliticsManager(private val plugin: AtlasPlugin) {
    
    private val activeElections = ConcurrentHashMap<String, Election>()

    fun startElection(city: City, initiator: Player) {
        if (activeElections.containsKey(city.id)) {
            initiator.sendMessage(Component.text("An election is already in progress!", NamedTextColor.RED))
            return
        }
        
        val election = Election(city.id)
        election.candidates.addAll(city.members) // All members are candidates for now
        activeElections[city.id] = election
        
        broadcastToCity(city, Component.text("🗳️ ELECTION STARTED! Use /atlas city vote <player> to vote for Mayor!", NamedTextColor.GOLD))
        plugin.historyManager.logEvent(city.id, "Election for Mayor started.", EventType.POLITICS)
        
        // Schedule end
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            endElection(city)
        }, 20L * 60 * 5) // 5 minutes (approx ticks)
    }

    fun castVote(city: City, voter: Player, candidateName: String) {
        val election = activeElections[city.id]
        if (election == null) {
            voter.sendMessage(Component.text("No election is currently active.", NamedTextColor.RED))
            return
        }
        
        // Find candidate UUID
        val candidateId = city.members.find { 
            plugin.server.getOfflinePlayer(it).name?.equals(candidateName, ignoreCase = true) == true 
        }
        
        if (candidateId == null) {
            voter.sendMessage(Component.text("That player is not a member of this city.", NamedTextColor.RED))
            return
        }
        
        election.votes[voter.uniqueId] = candidateId
        voter.sendMessage(Component.text("Vote cast for $candidateName!", NamedTextColor.GREEN))
    }

    private fun endElection(city: City) {
        val election = activeElections.remove(city.id) ?: return
        
        if (election.votes.isEmpty()) {
            broadcastToCity(city, Component.text("Election ended with NO votes. Current Mayor remains.", NamedTextColor.YELLOW))
            return
        }
        
        // Tally votes
        val counts = election.votes.values.groupingBy { it }.eachCount()
        val winnerId = counts.maxByOrNull { it.value }?.key
        
        if (winnerId != null) {
            city.mayor = winnerId
            plugin.cityManager.saveCity(city)
            
            val winnerName = plugin.server.getOfflinePlayer(winnerId).name ?: "Unknown"
            broadcastToCity(city, Component.text("🎉 ELECTION ENDED! New Mayor is $winnerName!", NamedTextColor.GREEN))
            plugin.historyManager.logEvent(city.id, "$winnerName was elected Mayor.", EventType.POLITICS)
        }
    }
    
    fun setTaxRate(city: City, mayor: Player, rate: Double) {
        if (city.mayor != mayor.uniqueId) {
            mayor.sendMessage(Component.text("Only the Mayor can set taxes.", NamedTextColor.RED))
            return
        }
        
        if (rate < 0.0 || rate > 20.0) {
            mayor.sendMessage(Component.text("Tax rate must be between 0% and 20%.", NamedTextColor.RED))
            return
        }
        
        city.taxRate = rate
        plugin.cityManager.saveCity(city)
        broadcastToCity(city, Component.text("Mayor has set the Tax Rate to $rate%", NamedTextColor.GOLD))
        plugin.historyManager.logEvent(city.id, "Tax rate set to $rate%", EventType.ECONOMY)
    }

    private fun broadcastToCity(city: City, message: Component) {
        city.members.forEach { uuid ->
            plugin.server.getPlayer(uuid)?.sendMessage(message)
        }
    }
}
