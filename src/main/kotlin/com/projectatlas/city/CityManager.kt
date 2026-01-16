package com.projectatlas.city

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.projectatlas.AtlasPlugin
import org.bukkit.Chunk
import org.bukkit.entity.Player
import com.projectatlas.history.EventType
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration

class CityManager(private val plugin: AtlasPlugin) {
    private val cities = ConcurrentHashMap<String, City>()
    private val chunkMap = ConcurrentHashMap<String, String>() // ChunkKey -> CityId
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val dataFolder = File(plugin.dataFolder, "cities")

    init {
        if (!dataFolder.exists()) dataFolder.mkdirs()
        loadAllCities()
        startCityTask()
    }
    
    // Periodic City Logic (1 Minute Tick)
    private fun startCityTask() {
        plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            cities.values.forEach { city ->
                // 1. Industrial Forge: Passive Income
                if (city.specialization == CitySpecialization.INDUSTRIAL_FORGE) {
                    var income = 10.0 + (city.members.size * 2.0)
                    
                    // Overclock: Consume 1 Redstone (Energy) for +50% Gold
                    if (city.energy > 0) {
                        city.energy--
                        income *= 1.5
                        // Notify Mayor (optional)
                    }
                    
                    city.treasury += income
                    // Use action bar so it's not spammy
                    // plugin.server.getPlayer(city.mayor)?.sendActionBar(Component.text("+$income g (Forge)", NamedTextColor.GOLD))
                }
                
                // 2. Arcane Sanctum: Threat Reduction Fuel
                // Logic is mostly in GlobalThreatManager, but we consume fuel here
                if (city.specialization == CitySpecialization.ARCANE_SANCTUM) {
                    if (city.mana > 0) {
                        city.mana--
                        // Sanctum is "Powered" for this minute
                    }
                }
            }
        }, 1200L, 1200L) // 1 minute
    }
    
    // Core Logic
    fun createCity(name: String, mayor: Player): City? {
        if (cities.values.any { it.name.equals(name, ignoreCase = true) }) return null
        
        val city = City(name = name, mayor = mayor.uniqueId)
        city.addMember(mayor.uniqueId)
        
        cities[city.id] = city
        saveCity(city)
        
        plugin.historyManager.logEvent(city.id, "City ${city.name} was founded by ${mayor.name}", EventType.FOUNDING)
        return city
    }

    fun getCity(id: String): City? = cities[id]
    
    fun getAllCities(): Collection<City> = cities.values

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

        // Rejoin Cooldown Check
        if (profile.lastCityId == city.id) {
            val cooldown = 24 * 60 * 60 * 1000L // 24 Hours
            val timeSinceLeave = System.currentTimeMillis() - profile.lastCityLeaveTime
            if (timeSinceLeave < cooldown) {
                val remaining = (cooldown - timeSinceLeave) / 1000 / 60 // Minutes
                player.sendMessage(Component.text("You cannot rejoin the same city for another $remaining minutes.", NamedTextColor.RED))
                return
            }
        }

        city.addMember(player.uniqueId)
        profile.cityId = city.id
        saveCity(city)
        player.sendMessage("Welcome to ${city.name}!")
        
        // Track progression milestones
        plugin.milestoneListener.onCityJoin(player)
        plugin.milestoneListener.onCityMemberChange(player, city.members.size)
        
        // Notify other members
        city.members.forEach { memberUUID ->
            if (memberUUID != player.uniqueId) {
                plugin.server.getPlayer(memberUUID)?.sendMessage("${player.name} has joined ${city.name}!")
            }
        }
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

    fun setSpecialization(cityId: String, spec: CitySpecialization) {
        val city = cities[cityId] ?: return
        city.specialization = spec
        saveCity(city)
        
        plugin.server.broadcast(Component.text("═══════════════════════════════", spec.color))
        plugin.server.broadcast(
            Component.text("  ${city.name} has specialized as a ", NamedTextColor.WHITE)
                .append(Component.text(spec.displayName, spec.color).decorate(TextDecoration.BOLD))
        )
        plugin.server.broadcast(Component.text("  ${spec.description.lines().first()}", NamedTextColor.GRAY))
        plugin.server.broadcast(Component.text("═══════════════════════════════", spec.color))
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
        profile.lastCityId = city.id
        profile.lastCityLeaveTime = System.currentTimeMillis()
        // Retain Era progress as Solo Era
        profile.soloEra = city.currentEra
        saveCity(city)
        player.sendMessage("You have left ${city.name}.")
    }

    fun upgradeInfrastructure(player: Player, module: String) {
        val profile = plugin.identityManager.getPlayer(player.uniqueId) ?: return
        val cityId = profile.cityId ?: return
        val city = getCity(cityId) ?: return
        
        // MAYOR/OFFICER Check
        if (city.mayor != player.uniqueId) {
            player.sendMessage(Component.text("Only the mayor can build upgrades.", NamedTextColor.RED))
            return
        }

        val infra = city.infrastructure
        var cost: Int? = null
        var newLevel = 0
        var moduleName = ""

        when (module.lowercase()) {
            "wall" -> {
                cost = infra.getWallUpgradeCost()
                newLevel = infra.wallLevel + 1
                moduleName = "Wall"
            }
            "generator" -> {
                cost = infra.getGeneratorUpgradeCost()
                newLevel = infra.generatorLevel + 1
                moduleName = "Generator"
            }
            "barracks" -> {
                cost = infra.getBarracksUpgradeCost()
                newLevel = infra.barracksLevel + 1
                moduleName = "Barracks"
            }
            "market" -> {
                cost = infra.getMarketUpgradeCost()
                newLevel = infra.marketLevel + 1
                moduleName = "Market"
            }
            "clinic" -> {
                cost = infra.getClinicUpgradeCost()
                newLevel = infra.clinicLevel + 1
                moduleName = "Clinic"
            }
            else -> {
                player.sendMessage(Component.text("Unknown module: $module. valid: wall, generator, barracks, market, clinic", NamedTextColor.RED))
                return
            }
        }

        if (cost == null) {
            player.sendMessage(Component.text("$moduleName is already at max level.", NamedTextColor.RED))
            return
        }

        if (city.treasury < cost) {
            player.sendMessage(Component.text("Insufficient funds. Need $cost g (Treasury: ${city.treasury})", NamedTextColor.RED))
            return
        }

        // Execute Upgrade
        city.treasury -= cost
        when (module.lowercase()) {
            "wall" -> infra.wallLevel = newLevel
            "generator" -> infra.generatorLevel = newLevel
            "barracks" -> infra.barracksLevel = newLevel
            "market" -> infra.marketLevel = newLevel
            "clinic" -> infra.clinicLevel = newLevel
        }
        
        saveCity(city)
        player.sendMessage(Component.text("Upgraded $moduleName to Level $newLevel! (-$cost g)", NamedTextColor.GREEN))
        plugin.historyManager.logEvent(city.id, "${city.name} built/upgraded $moduleName to Level $newLevel", EventType.CITY_UPGRADE)
    }

    // Persistence
    private fun loadAllCities() {
        if (!dataFolder.exists()) return
        dataFolder.listFiles { _, name -> name.endsWith(".json") }?.forEach { file ->
            try {
                val city = gson.fromJson(file.readText(), City::class.java)
                // Fix for Gson not using default values for missing fields
                if (city.infrastructure == null) {
                    city.infrastructure = CityInfrastructure()
                }
                cities[city.id] = city
                city.claimedChunks.forEach { chunkKey ->
                    chunkMap[chunkKey] = city.id
                }
            } catch (e: Exception) {
                plugin.logger.severe("Failed to load city ${file.name}: ${e.message}")
            }
        }
    }

    fun saveCity(city: City) {
        val file = File(dataFolder, "${city.id}.json")
        try {
            file.writeText(gson.toJson(city))
        } catch (e: Exception) {
            plugin.logger.severe("Failed to save city ${city.name}: ${e.message}")
        }
    }
    
    private fun getChunkKey(chunk: Chunk): String = "${chunk.world.name}:${chunk.x},${chunk.z}"
}
