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

    fun getCityAt(worldName: String, x: Int, z: Int): City? {
        val key = "${worldName}:${x},${z}"
        val cityId = chunkMap[key] ?: return null
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
        var isTurret = false

        when (module.lowercase()) {
            // ═══════════════════════════════════════════════════════════════
            // DEFENSIVE INFRASTRUCTURE
            // ═══════════════════════════════════════════════════════════════
            "wall" -> {
                cost = infra.getWallUpgradeCost()
                newLevel = infra.wallLevel + 1
                moduleName = "Wall"
            }
            "turret" -> {
                if (!infra.canAddTurret()) {
                    player.sendMessage(Component.text("Maximum turrets reached (4).", NamedTextColor.RED))
                    return
                }
                cost = CityInfrastructure.TURRET_COST
                newLevel = infra.turretCount + 1
                moduleName = "Turret"
                isTurret = true
            }
            "barracks" -> {
                cost = infra.getBarracksUpgradeCost()
                newLevel = infra.barracksLevel + 1
                moduleName = "Barracks"
            }
            "watchtower" -> {
                cost = infra.getWatchtowerUpgradeCost()
                newLevel = infra.watchtowerLevel + 1
                moduleName = "Watchtower"
            }
            "trapsystem", "trap" -> {
                cost = infra.getTrapSystemUpgradeCost()
                newLevel = infra.trapSystemLevel + 1
                moduleName = "Trap System"
            }
            
            // ═══════════════════════════════════════════════════════════════
            // ECONOMIC INFRASTRUCTURE
            // ═══════════════════════════════════════════════════════════════
            "generator" -> {
                cost = infra.getGeneratorUpgradeCost()
                newLevel = infra.generatorLevel + 1
                moduleName = "Generator"
            }
            "market" -> {
                cost = infra.getMarketUpgradeCost()
                newLevel = infra.marketLevel + 1
                moduleName = "Market"
            }
            
            // ═══════════════════════════════════════════════════════════════
            // SUPPORT INFRASTRUCTURE
            // ═══════════════════════════════════════════════════════════════
            "clinic" -> {
                cost = infra.getClinicUpgradeCost()
                newLevel = infra.clinicLevel + 1
                moduleName = "Clinic"
            }
            "healingbeacon", "beacon" -> {
                cost = infra.getHealingBeaconUpgradeCost()
                newLevel = infra.healingBeaconLevel + 1
                moduleName = "Healing Beacon"
            }
            "armory" -> {
                cost = infra.getArmoryUpgradeCost()
                newLevel = infra.armoryLevel + 1
                moduleName = "Armory"
            }
            "forge" -> {
                cost = infra.getForgeUpgradeCost()
                newLevel = infra.forgeLevel + 1
                moduleName = "Forge"
            }
            
            else -> {
                player.sendMessage(Component.text("Unknown module: $module", NamedTextColor.RED))
                player.sendMessage(Component.text("Valid modules: wall, turret, barracks, watchtower, trap, generator, market, clinic, beacon, armory, forge", NamedTextColor.GRAY))
                return
            }
        }

        if (cost == null) {
            player.sendMessage(Component.text("$moduleName is already at max level.", NamedTextColor.RED))
            return
        }

        if (city.treasury < cost) {
            player.sendMessage(Component.text("Insufficient funds. Need $cost g (Treasury: ${city.treasury.toInt()}g)", NamedTextColor.RED))
            return
        }

        // Execute Upgrade
        city.treasury -= cost
        when (module.lowercase()) {
            "wall" -> infra.wallLevel = newLevel
            "turret" -> infra.turretCount = newLevel
            "barracks" -> infra.barracksLevel = newLevel
            "watchtower" -> infra.watchtowerLevel = newLevel
            "trapsystem", "trap" -> infra.trapSystemLevel = newLevel
            "generator" -> infra.generatorLevel = newLevel
            "market" -> infra.marketLevel = newLevel
            "clinic" -> infra.clinicLevel = newLevel
            "healingbeacon", "beacon" -> infra.healingBeaconLevel = newLevel
            "armory" -> infra.armoryLevel = newLevel
            "forge" -> infra.forgeLevel = newLevel
        }
        
        saveCity(city)
        
        val levelText = if (isTurret) "Count: $newLevel/4" else "Level $newLevel"
        player.sendMessage(Component.text("✓ Upgraded $moduleName to $levelText! (-$cost g)", NamedTextColor.GREEN))
        plugin.historyManager.logEvent(city.id, "${city.name} upgraded $moduleName to $levelText", EventType.CITY_UPGRADE)
    }
    
    fun getInfrastructureInfo(city: City): List<Component> {
        val infra = city.infrastructure
        val info = mutableListOf<Component>()
        
        info.add(Component.text("═══ ${city.name} Infrastructure ═══", NamedTextColor.GOLD))
        info.add(Component.empty())
        
        // Defense Rating
        info.add(Component.text("Defense Rating: ", NamedTextColor.WHITE)
            .append(Component.text("${infra.getDefenseRating()}", NamedTextColor.GREEN)))
        info.add(Component.empty())
        
        // Defensive
        info.add(Component.text("⚔ DEFENSIVE", NamedTextColor.RED))
        info.add(formatModuleLine("Wall", infra.wallLevel, 5, infra.getWallUpgradeCost()))
        info.add(formatModuleLine("Turret", infra.turretCount, 4, if (infra.canAddTurret()) CityInfrastructure.TURRET_COST else null))
        info.add(formatModuleLine("Barracks", infra.barracksLevel, 3, infra.getBarracksUpgradeCost()))
        info.add(formatModuleLine("Watchtower", infra.watchtowerLevel, 3, infra.getWatchtowerUpgradeCost()))
        info.add(formatModuleLine("Trap System", infra.trapSystemLevel, 3, infra.getTrapSystemUpgradeCost()))
        info.add(Component.empty())
        
        // Economic
        info.add(Component.text("💰 ECONOMIC", NamedTextColor.GOLD))
        info.add(formatModuleLine("Generator", infra.generatorLevel, 3, infra.getGeneratorUpgradeCost()))
        info.add(formatModuleLine("Market", infra.marketLevel, 3, infra.getMarketUpgradeCost()))
        info.add(Component.empty())
        
        // Support
        info.add(Component.text("💚 SUPPORT", NamedTextColor.GREEN))
        info.add(formatModuleLine("Clinic", infra.clinicLevel, 3, infra.getClinicUpgradeCost()))
        info.add(formatModuleLine("Healing Beacon", infra.healingBeaconLevel, 3, infra.getHealingBeaconUpgradeCost()))
        info.add(formatModuleLine("Armory", infra.armoryLevel, 3, infra.getArmoryUpgradeCost()))
        info.add(formatModuleLine("Forge", infra.forgeLevel, 3, infra.getForgeUpgradeCost()))
        info.add(Component.empty())
        
        // City Core
        info.add(Component.text("❤ City Core: ", NamedTextColor.WHITE)
            .append(Component.text("${infra.coreHealth}/${infra.maxCoreHealth} HP", 
                if (infra.getCoreHealthPercent() > 0.5) NamedTextColor.GREEN else NamedTextColor.RED)))
        
        return info
    }
    
    private fun formatModuleLine(name: String, level: Int, maxLevel: Int, upgradeCost: Int?): Component {
        val levelBars = "█".repeat(level) + "░".repeat(maxLevel - level)
        val costText = if (upgradeCost != null) " [${upgradeCost}g]" else " [MAX]"
        val costColor = if (upgradeCost != null) NamedTextColor.YELLOW else NamedTextColor.DARK_GRAY
        
        return Component.text("  $name: ", NamedTextColor.GRAY)
            .append(Component.text(levelBars, NamedTextColor.AQUA))
            .append(Component.text(" $level/$maxLevel", NamedTextColor.WHITE))
            .append(Component.text(costText, costColor))
    }

    // ═══════════════════════════════════════════════════════════════
    // BUILDING PLACEMENT
    // ═══════════════════════════════════════════════════════════════
    fun confirmBuildingPlacement(cityId: String, type: com.projectatlas.structures.StructureType, location: org.bukkit.Location): Boolean {
        val city = getCity(cityId) ?: return false
        
        // Check Limits
        val currentCount = city.placedStructures[type.name]?.size ?: 0
        
        if (type == com.projectatlas.structures.StructureType.TURRET) {
            if (!city.infrastructure.canAddTurret()) return false
        } else {
             // Limit 1 for major structures for now
            if (currentCount >= 1) return false
        }
        
        // Build It
        plugin.structureManager.spawnStructure(type, location)
        
        // Update City Data
        val locStr = "${location.world.name}:${location.blockX},${location.blockY},${location.blockZ}"
        city.placedStructures.computeIfAbsent(type.name) { mutableListOf() }.add(locStr)
        
        // Update Infrastructure Stats
        when(type) {
            com.projectatlas.structures.StructureType.TURRET -> city.infrastructure.turretCount++
            com.projectatlas.structures.StructureType.GENERATOR -> if(city.infrastructure.generatorLevel == 0) city.infrastructure.generatorLevel = 1
            com.projectatlas.structures.StructureType.BARRACKS -> if(city.infrastructure.barracksLevel == 0) city.infrastructure.barracksLevel = 1
            else -> { /* No specific stat update for others yet */ }
        }
        
        saveCity(city)
        return true
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
