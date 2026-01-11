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
                AtlasPlayer(player.uniqueId, player.name)
            }
        } else {
            AtlasPlayer(player.uniqueId, player.name)
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
}
