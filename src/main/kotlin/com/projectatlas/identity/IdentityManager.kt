package com.projectatlas.identity

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.projectatlas.AtlasPlugin
import org.bukkit.entity.Player
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class IdentityManager(private val plugin: AtlasPlugin) {
    private val players = ConcurrentHashMap<UUID, AtlasPlayer>()
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val dataFolder = File(plugin.dataFolder, "players")

    init {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs()
        }
    }

    fun getPlayer(uuid: UUID): AtlasPlayer? {
        return players[uuid]
    }

    fun createOrLoadProfile(player: Player): AtlasPlayer {
        if (players.containsKey(player.uniqueId)) return players[player.uniqueId]!!

        val file = File(dataFolder, "${player.uniqueId}.json")
        val profile = if (file.exists()) {
            try {
                gson.fromJson(file.readText(), AtlasPlayer::class.java).apply {
                    this.name = player.name // Update name if changed
                }
            } catch (e: Exception) {
                plugin.logger.severe("Failed to load data for ${player.name}: ${e.message}")
                AtlasPlayer(player.uniqueId, player.name, balance = plugin.configManager.startingBalance)
            }
        } else {
            AtlasPlayer(player.uniqueId, player.name, balance = plugin.configManager.startingBalance)
        }

        players[player.uniqueId] = profile
        return profile
    }

    fun saveProfile(uuid: UUID) {
        val profile = players[uuid] ?: return
        val file = File(dataFolder, "$uuid.json")
        try {
            file.writeText(gson.toJson(profile))
        } catch (e: Exception) {
            plugin.logger.severe("Failed to save data for $uuid: ${e.message}")
        }
    }

    fun saveAll() {
        players.keys.forEach { saveProfile(it) }
    }

    fun unloadProfile(uuid: UUID) {
        saveProfile(uuid)
        players.remove(uuid)
    }
    
    // API Methods
    fun modifyReputation(uuid: UUID, amount: Int) {
        val profile = players[uuid] ?: return
        profile.reputation += amount
        // Logic for bounds or effects can go here
    }
    
    // Load an offline player's profile from disk (for operations like kick)
    fun loadOfflineProfile(uuid: UUID): AtlasPlayer? {
        // First check if they're online
        players[uuid]?.let { return it }
        
        // Otherwise load from disk
        val file = File(dataFolder, "$uuid.json")
        return if (file.exists()) {
            try {
                gson.fromJson(file.readText(), AtlasPlayer::class.java)
            } catch (e: Exception) {
                plugin.logger.severe("Failed to load offline data for $uuid: ${e.message}")
                null
            }
        } else null
    }
    
    // Save an offline player's profile directly to disk
    fun saveOfflineProfile(profile: AtlasPlayer) {
        val file = File(dataFolder, "${profile.uuid}.json")
        try {
            file.writeText(gson.toJson(profile))
        } catch (e: Exception) {
            plugin.logger.severe("Failed to save offline data for ${profile.uuid}: ${e.message}")
        }
    }

    fun grantXp(player: Player, amount: Long) {
        val profile = getPlayer(player.uniqueId) ?: return
        profile.currentXp += amount
        
        // Check Level Up
        // Formula: XP required = Level * 100
        val required = profile.level * 100L
        if (profile.currentXp >= required) {
            profile.currentXp -= required
            profile.level++
            
            player.sendMessage(net.kyori.adventure.text.Component.text("Testing: Level Up! You are now Level ${profile.level}", net.kyori.adventure.text.format.NamedTextColor.GOLD))
            player.playSound(player.location, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f)
            
            // Re-apply class effects (stats might scale with level later)
            plugin.classManager.applyClassEffects(player)
        }
        saveProfile(player.uniqueId)
    }
}
