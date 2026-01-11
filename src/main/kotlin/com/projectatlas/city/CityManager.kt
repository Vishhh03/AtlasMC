package com.projectatlas.city

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.projectatlas.AtlasPlugin
import org.bukkit.Chunk
import org.bukkit.entity.Player
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class CityManager(private val plugin: AtlasPlugin) {
    private val cities = ConcurrentHashMap<String, City>()
    private val chunkMap = ConcurrentHashMap<String, String>() // ChunkKey -> CityId
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val dataFolder = File(plugin.dataFolder, "cities")

    init {
        if (!dataFolder.exists()) dataFolder.mkdirs()
        loadAllCities()
    }
    
    // Core Logic
    fun createCity(name: String, mayor: Player): City? {
        if (cities.values.any { it.name.equals(name, ignoreCase = true) }) return null
        
        val city = City(name = name, mayor = mayor.uniqueId)
        city.addMember(mayor.uniqueId)
        
        cities[city.id] = city
        saveCity(city)
        return city
    }

    fun getCity(id: String): City? = cities[id]
    
    fun claimChunk(cityId: String, chunk: Chunk): Boolean {
        val key = getChunkKey(chunk)
        if (chunkMap.containsKey(key)) return false // Already claimed
        
        val city = getCity(cityId) ?: return false
        city.claimedChunks.add(key)
        chunkMap[key] = city.id
        saveCity(city)
        return true
    }
    
    fun getCityAt(chunk: Chunk): City? {
        val cityId = chunkMap[getChunkKey(chunk)] ?: return null
        return getCity(cityId)
    }
    
    // Management
    private val invites = ConcurrentHashMap<UUID, String>() // PlayerUUID -> CityID

    fun sendInvite(mayor: Player, target: Player) {
        val profile = plugin.identityManager.getPlayer(mayor.uniqueId) ?: return
        val cityId = profile.cityId ?: return
        val city = getCity(cityId) ?: return

        if (city.mayor != mayor.uniqueId) {
            mayor.sendMessage("Only the mayor can invite people.")
            return
        }
        
        if (getCity(profile.cityId!!)?.members?.contains(target.uniqueId) == true) {
             mayor.sendMessage("${target.name} is already in the city.")
             return
        }

        invites[target.uniqueId] = cityId
        target.sendMessage("You have been invited to join ${city.name}. Type /atlas city join to accept.")
        mayor.sendMessage("Invite sent to ${target.name}.")
    }

    fun acceptInvite(player: Player) {
        val cityId = invites.remove(player.uniqueId)
        if (cityId == null) {
            player.sendMessage("You have no pending invites.")
            return
        }
        
        val city = getCity(cityId) ?: return
        val profile = plugin.identityManager.getPlayer(player.uniqueId) ?: return
        
        if (profile.cityId != null) {
            player.sendMessage("You must leave your current city first.")
            return
        }

        city.addMember(player.uniqueId)
        profile.cityId = city.id
        saveCity(city)
        player.sendMessage("Welcome to ${city.name}!")
        // Notify members logic could go here
    }



    fun depositToTreasury(cityId: String, amount: Double) {
        val city = cities[cityId] ?: return
        city.treasury += amount
        saveCity(city)
    }

    fun setTaxRate(cityId: String, rate: Double) {
        val city = cities[cityId] ?: return
        city.taxRate = rate.coerceIn(0.0, 100.0)
        saveCity(city)
    }

    fun kickPlayer(mayor: Player, targetName: String) {
        val profile = plugin.identityManager.getPlayer(mayor.uniqueId) ?: return
        val cityId = profile.cityId ?: return
        val city = getCity(cityId) ?: return

        if (city.mayor != mayor.uniqueId) {
            mayor.sendMessage("Only the mayor can kick people.")
            return
        }
        
        val targetUuid = city.members.firstOrNull { 
             plugin.server.getOfflinePlayer(it).name.equals(targetName, ignoreCase = true) 
        }
        
        if (targetUuid == null) {
             mayor.sendMessage("Member not found.")
             return
        }
        
        if (targetUuid == mayor.uniqueId) {
            mayor.sendMessage("You cannot kick yourself.")
            return
        }

        city.removeMember(targetUuid)
        
        // Handle both online and offline players
        val onlineProfile = plugin.identityManager.getPlayer(targetUuid)
        if (onlineProfile != null) {
            onlineProfile.cityId = null
        } else {
            // Player is offline - load their profile, modify, and save
            val offlineProfile = plugin.identityManager.loadOfflineProfile(targetUuid)
            if (offlineProfile != null) {
                offlineProfile.cityId = null
                plugin.identityManager.saveOfflineProfile(offlineProfile)
            }
        }
        
        saveCity(city)
        mayor.sendMessage("Kicked $targetName.")
    }
    
    fun leaveCity(player: Player) {
        val profile = plugin.identityManager.getPlayer(player.uniqueId) ?: return
        if (profile.cityId == null) {
            player.sendMessage("You are not in a city.")
            return
        }
        
        val city = getCity(profile.cityId!!) ?: return
        if (city.mayor == player.uniqueId) {
             player.sendMessage("The mayor cannot leave the city. Disband it or transfer ownership (WIP).")
             return
        }
        
        city.removeMember(player.uniqueId)
        profile.cityId = null
        saveCity(city)
        player.sendMessage("You have left ${city.name}.")
    }

    // Persistence
    private fun loadAllCities() {
        if (!dataFolder.exists()) return
        dataFolder.listFiles { _, name -> name.endsWith(".json") }?.forEach { file ->
            try {
                val city = gson.fromJson(file.readText(), City::class.java)
                cities[city.id] = city
                city.claimedChunks.forEach { chunkKey ->
                    chunkMap[chunkKey] = city.id
                }
            } catch (e: Exception) {
                plugin.logger.severe("Failed to load city ${file.name}: ${e.message}")
            }
        }
    }

    private fun saveCity(city: City) {
        val file = File(dataFolder, "${city.id}.json")
        try {
            file.writeText(gson.toJson(city))
        } catch (e: Exception) {
            plugin.logger.severe("Failed to save city ${city.name}: ${e.message}")
        }
    }
    
    private fun getChunkKey(chunk: Chunk): String = "${chunk.world.name}:${chunk.x},${chunk.z}"
}
