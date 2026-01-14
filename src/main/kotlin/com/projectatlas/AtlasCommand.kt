package com.projectatlas

import com.projectatlas.city.CityManager
import com.projectatlas.economy.EconomyManager
import com.projectatlas.identity.IdentityManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

import com.projectatlas.gui.GuiManager
import com.projectatlas.schematic.SchematicManager

import com.projectatlas.politics.PoliticsManager

class AtlasCommand(
    private val identityManager: IdentityManager,
    private val economyManager: EconomyManager,
    private val cityManager: CityManager,
    private val guiManager: GuiManager,
    private val schematicManager: SchematicManager,
    private val politicsManager: PoliticsManager
) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("Only players can use this command.")
            return true
        }

        if (args.isEmpty()) {
            guiManager.openMainMenu(sender)
            return true
        }

        when (args[0].lowercase()) {
            "profile" -> handleProfile(sender)
            "balance", "bal" -> handleBalance(sender)
            "pay" -> handlePay(sender, args)
            "city" -> handleCity(sender, args)
            "event" -> handleEvent(sender, args)
            "help" -> handleHelp(sender)
            "spawn" -> handleSpawn(sender, args)
            "schem" -> handleSchematic(sender, args)
            "bounty" -> handleBounty(sender, args)
            "boss" -> handleBoss(sender, args)
            "relic" -> handleRelic(sender, args)
            "dungeon" -> handleDungeon(sender, args)
            "party" -> handleParty(sender, args)
            "blueprint", "bp" -> handleBlueprint(sender, args)
            "menu" -> handleMenu(sender)
            "skills", "skill", "tree" -> handleSkills(sender)
            "heal", "medkit" -> handleHeal(sender, args)
            // QoL Commands
            "sort" -> handleSort(sender)
            "stats" -> handleStats(sender, args)
            "scoreboard", "sb" -> handleScoreboard(sender)
            "damage", "dmg" -> handleDamageToggle(sender)
            "quickstack", "qs" -> handleQuickStack(sender)
            "atmosphere", "ambient", "shaders" -> handleAtmosphere(sender)
            
            else -> sender.sendMessage(Component.text("Unknown command. Type /atlas help for commands.", NamedTextColor.RED))
        }
        return true
    }

    // --- Subcommand Logic ---

    private fun handleProfile(player: Player) {
        val profile = identityManager.getPlayer(player.uniqueId) ?: return
        player.sendMessage(Component.text("--- Atlas Profile ---", NamedTextColor.GOLD))
        player.sendMessage(Component.text("Name: ${profile.name}"))
        player.sendMessage(Component.text("Reputation: ${profile.reputation}"))
        player.sendMessage(Component.text("Alignment: ${profile.alignment}"))
        player.sendMessage(Component.text("City: ${if (profile.cityId != null) cityManager.getCity(profile.cityId!!)?.name else "None"}"))
    }

    private fun handleSkills(player: Player) {
        val plugin = player.server.pluginManager.getPlugin("ProjectAtlas") as? AtlasPlugin
        if (plugin != null) {
            plugin.skillTreeManager.openSkillTree(player)
        } else {
            player.sendMessage(Component.text("Skill Tree is not available.", NamedTextColor.RED))
        }
    }

    private fun handleHeal(player: Player, args: Array<out String>) {
        val plugin = player.server.pluginManager.getPlugin("ProjectAtlas") as? AtlasPlugin
        if (plugin == null) {
            player.sendMessage(Component.text("Survival system not available.", NamedTextColor.RED))
            return
        }
        
        if (args.size < 2) {
            player.sendMessage(Component.text("Usage: /atlas heal <bandage|salve|medkit|remedy> [amount]", NamedTextColor.RED))
            player.sendMessage(Component.text("Available items:", NamedTextColor.GRAY))
            player.sendMessage(Component.text("  bandage - Heals 4 HP", NamedTextColor.WHITE))
            player.sendMessage(Component.text("  salve (healing_salve) - Heals 8 HP, clears debuffs", NamedTextColor.WHITE))
            player.sendMessage(Component.text("  medkit (medical_kit) - Heals 14 HP, clears debuffs", NamedTextColor.WHITE))
            player.sendMessage(Component.text("  remedy (herbal_remedy) - Heals 6 HP", NamedTextColor.WHITE))
            return
        }
        
        val itemName = when (args[1].lowercase()) {
            "bandage" -> "BANDAGE"
            "salve", "healing_salve" -> "HEALING_SALVE"
            "medkit", "medical_kit", "kit" -> "MEDICAL_KIT"
            "remedy", "herbal_remedy", "herb" -> "HERBAL_REMEDY"
            else -> args[1].uppercase()
        }
        
        val amount = args.getOrNull(2)?.toIntOrNull() ?: 1
        plugin.survivalManager.giveHealingItem(player, itemName, amount)
    }

    private fun handleBalance(player: Player) {
        val bal = economyManager.getBalance(player.uniqueId)
        player.sendMessage(Component.text("Balance: $bal"))
    }

    private fun handlePay(player: Player, args: Array<out String>) {
        if (args.size < 3) {
            player.sendMessage(Component.text("Usage: /atlas pay <player> <amount>", NamedTextColor.RED))
            return
        }
        val targetName = args[1]
        val amount = args[2].toDoubleOrNull()
        
        if (amount == null || amount <= 0) {
            player.sendMessage(Component.text("Invalid amount.", NamedTextColor.RED))
            return
        }
        
        val target = player.server.getPlayer(targetName)
        if (target == null) {
            player.sendMessage(Component.text("Player not found.", NamedTextColor.RED))
            return
        }
        
        if (economyManager.transfer(player.uniqueId, target.uniqueId, amount)) {
            player.sendMessage(Component.text("Sent $amount to ${target.name}.", NamedTextColor.GREEN))
            target.sendMessage(Component.text("Received $amount from ${player.name}.", NamedTextColor.GREEN))
        } else {
            player.sendMessage(Component.text("Insufficient funds.", NamedTextColor.RED))
        }
    }

    private fun handleCity(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            // Open City GUI instead of showing usage error
            guiManager.openCityMenu(player)
            return
        }

        when (args[1].lowercase()) {
            "create" -> {
                if (args.size < 3) {
                    player.sendMessage(Component.text("Usage: /atlas city create <name>", NamedTextColor.RED))
                    return
                }
                val name = args[2]
                val profile = identityManager.getPlayer(player.uniqueId) ?: return
                
                // Check cost
                val cost = org.bukkit.plugin.java.JavaPlugin.getPlugin(AtlasPlugin::class.java).configManager.cityCreationCost
                if (profile.balance < cost) {
                    player.sendMessage(Component.text("City creation costs $cost gold. You have ${profile.balance}.", NamedTextColor.RED))
                    return
                }
                
                val city = cityManager.createCity(name, player)
                if (city != null) {
                    profile.balance -= cost
                    profile.cityId = city.id
                    player.sendMessage(Component.text("City $name created! (-$cost gold)", NamedTextColor.GREEN))
                } else {
                    player.sendMessage(Component.text("City creation failed (name taken?).", NamedTextColor.RED))
                }
            }
            "claim" -> {
                val profile = identityManager.getPlayer(player.uniqueId)
                if (profile?.cityId == null) {
                    player.sendMessage(Component.text("You are not in a city.", NamedTextColor.RED))
                    return
                }
                
                val city = cityManager.getCity(profile.cityId!!)
                if (city?.mayor != player.uniqueId) {
                     player.sendMessage(Component.text("Only the mayor can claim chunks.", NamedTextColor.RED))
                     return
                }
                
                // Calculate chunk cost (scales with territory size)
                val plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(AtlasPlugin::class.java)
                val cost = plugin.configManager.getChunkClaimCost(city.claimedChunks.size)
                
                if (city.treasury < cost) {
                    player.sendMessage(Component.text("Chunk claim costs ${"%.0f".format(cost)}g from treasury. Treasury has ${city.treasury}g.", NamedTextColor.RED))
                    return
                }

                if (cityManager.claimChunk(city.id, player.location.chunk)) {
                    city.treasury -= cost
                    cityManager.saveCity(city)
                    player.sendMessage(Component.text("Territory claimed! (-${"%.0f".format(cost)}g from treasury)", NamedTextColor.GREEN))
                    val nextCost = plugin.configManager.getChunkClaimCost(city.claimedChunks.size)
                    player.sendMessage(Component.text("Next chunk will cost ${"%.0f".format(nextCost)}g", NamedTextColor.GRAY))
                } else {
                    player.sendMessage(Component.text("Claim failed (already owned?).", NamedTextColor.RED))
                }
            }
            "invite" -> {
                if (args.size < 3) {
                    player.sendMessage(Component.text("Usage: /atlas city invite <player>", NamedTextColor.RED))
                    return
                }
                val target = player.server.getPlayer(args[2])
                if (target == null) {
                    player.sendMessage(Component.text("Player not found.", NamedTextColor.RED))
                    return
                }
                cityManager.sendInvite(player, target)
            }
            "join" -> cityManager.acceptInvite(player)
            "kick" -> {
                if (args.size < 3) {
                    player.sendMessage(Component.text("Usage: /atlas city kick <player>", NamedTextColor.RED))
                    return
                }
                cityManager.kickPlayer(player, args[2])
            }
            "leave" -> cityManager.leaveCity(player)
            "info" -> {
                val city = cityManager.getCityAt(player.location.chunk)
                if (city != null) {
                    val mayorName = player.server.getOfflinePlayer(city.mayor).name ?: "Unknown"
                    player.sendMessage(Component.text("Territory: ${city.name} (Mayor: $mayorName)", NamedTextColor.AQUA))
                    player.sendMessage(Component.text("Members: ${city.members.size}", NamedTextColor.GRAY))
                    player.sendMessage(Component.text("Tax: ${city.taxRate}% | Treasury: ${city.treasury}", NamedTextColor.GOLD))
                } else {
                    player.sendMessage(Component.text("Wilderness", NamedTextColor.GRAY))
                }
            }
            "tax" -> {
                if (args.size < 3) {
                    player.sendMessage(Component.text("Usage: /atlas city tax <percentage>", NamedTextColor.RED))
                    return
                }
                
                val profile = identityManager.getPlayer(player.uniqueId)
                if (profile?.cityId == null) return
                val city = cityManager.getCity(profile.cityId!!) ?: return
                
                if (city.mayor != player.uniqueId) {
                    player.sendMessage(Component.text("Only mayor can set tax.", NamedTextColor.RED))
                    return
                }
                
                val rate = args[2].toDoubleOrNull()
                if (rate == null || rate < 0 || rate > 100) {
                    player.sendMessage(Component.text("Invalid percentage (0-100).", NamedTextColor.RED))
                    return
                }
                
                cityManager.setTaxRate(city.id, rate)
                player.sendMessage(Component.text("Tax rate set to $rate%", NamedTextColor.GREEN))
            }
            "deposit" -> {
                 if (args.size < 3) {
                    player.sendMessage(Component.text("Usage: /atlas city deposit <amount>", NamedTextColor.RED))
                    return
                }
                val amount = args[2].toDoubleOrNull()
                if (amount == null || amount <= 0) {
                     player.sendMessage(Component.text("Invalid amount.", NamedTextColor.RED))
                     return
                }
                
                val profile = identityManager.getPlayer(player.uniqueId) ?: return
                if (profile.cityId == null) {
                    player.sendMessage(Component.text("You are not in a city.", NamedTextColor.RED))
                    return
                }

                if (economyManager.withdraw(player.uniqueId, amount)) {
                    cityManager.depositToTreasury(profile.cityId!!, amount)
                    player.sendMessage(Component.text("Deposited $amount to treasury.", NamedTextColor.GREEN))
                } else {
                    player.sendMessage(Component.text("Insufficient funds.", NamedTextColor.RED))
                }
            }
            "election" -> {
                val profile = identityManager.getPlayer(player.uniqueId)
                if (profile?.cityId == null) return
                val city = cityManager.getCity(profile.cityId!!) ?: return
                
                politicsManager.startElection(city, player)
            }
            "vote" -> {
                if (args.size < 3) {
                    player.sendMessage(Component.text("Usage: /atlas city vote <player>", NamedTextColor.RED))
                    return
                }
                val profile = identityManager.getPlayer(player.uniqueId)
                if (profile?.cityId == null) return
                val city = cityManager.getCity(profile.cityId!!) ?: return
                
                politicsManager.castVote(city, player, args[2])
            }
        }
    }
    
    private fun handleEvent(player: Player, args: Array<out String>) {
        if (!player.hasPermission("atlas.admin")) {
            player.sendMessage(Component.text("No permission.", NamedTextColor.RED))
            return
        }
        if (args.size < 2) {
             player.sendMessage(Component.text("Usage: /atlas event start", NamedTextColor.RED))
             return
        }
        
        if (args[1].equals("start", ignoreCase = true)) {
            val plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(AtlasPlugin::class.java)
            plugin.eventManager.forceTrigger()
            player.sendMessage(Component.text("Event triggered manually.", NamedTextColor.GREEN))
        }
    }

    private fun handleHelp(player: Player) {
        player.sendMessage(Component.empty())
        player.sendMessage(Component.text("═══════════════════════════════", NamedTextColor.GOLD))
        player.sendMessage(Component.text("  ⚔ Project Atlas Help ⚔", NamedTextColor.YELLOW))
        player.sendMessage(Component.text("═══════════════════════════════", NamedTextColor.GOLD))
        player.sendMessage(Component.empty())
        player.sendMessage(Component.text("  Core Commands:", NamedTextColor.WHITE))
        player.sendMessage(Component.text("  /atlas - Open main menu", NamedTextColor.GRAY))
        player.sendMessage(Component.text("  /atlas profile - View your stats", NamedTextColor.GRAY))
        player.sendMessage(Component.text("  /atlas skills - Open skill tree", NamedTextColor.GRAY))
        player.sendMessage(Component.text("  /atlas bal - View balance", NamedTextColor.GRAY))
        player.sendMessage(Component.text("  /atlas pay <player> <amount>", NamedTextColor.GRAY))
        player.sendMessage(Component.empty())
        player.sendMessage(Component.text("  City & Social:", NamedTextColor.WHITE))
        player.sendMessage(Component.text("  /atlas city <create|claim|invite|...>", NamedTextColor.GRAY))
        player.sendMessage(Component.text("  /atlas party <create|invite|accept|...>", NamedTextColor.GRAY))
        player.sendMessage(Component.text("  /atlas bounty <place|check|list>", NamedTextColor.GRAY))
        player.sendMessage(Component.text("  /pc <message> - Party chat", NamedTextColor.GRAY))
        player.sendMessage(Component.empty())
        player.sendMessage(Component.text("  QoL Features:", NamedTextColor.WHITE))
        player.sendMessage(Component.text("  /atlas sort - Sort inventory", NamedTextColor.GRAY))
        player.sendMessage(Component.text("  /atlas stats - View kill stats", NamedTextColor.GRAY))
        player.sendMessage(Component.text("  /atlas sb - Toggle scoreboard", NamedTextColor.GRAY))
        player.sendMessage(Component.text("  /atlas dmg - Toggle damage numbers", NamedTextColor.GRAY))
        player.sendMessage(Component.text("  /atlas qs - Quick stack to nearby chests", NamedTextColor.GRAY))
        player.sendMessage(Component.empty())
        player.sendMessage(Component.text("  Content:", NamedTextColor.WHITE))
        player.sendMessage(Component.text("  /atlas dungeon - Enter dungeons", NamedTextColor.GRAY))
        player.sendMessage(Component.text("  /atlas blueprint - Schematic marketplace", NamedTextColor.GRAY))
        if (player.hasPermission("atlas.admin")) {
            player.sendMessage(Component.empty())
            player.sendMessage(Component.text("  Admin:", NamedTextColor.RED))
            player.sendMessage(Component.text("  /atlas event start - Force supply drop", NamedTextColor.DARK_RED))
            player.sendMessage(Component.text("  /atlas boss spawn - Force world boss", NamedTextColor.DARK_RED))
            player.sendMessage(Component.text("  /atlas relic spawn - Force relic spawn", NamedTextColor.DARK_RED))
        }
        player.sendMessage(Component.text("═══════════════════════════════", NamedTextColor.GOLD))
        player.sendMessage(Component.empty())
    }

    // --- Tab Completion ---

    // --- Tab Completion ---

    // --- Tab Completion handling moved to bottom of file ---
    
    private fun handleSpawn(player: Player, args: Array<out String>) {
        if (!player.hasPermission("atlas.admin")) {
            player.sendMessage(Component.text("You do not have permission.", NamedTextColor.RED))
            return
        }
        
        if (args.size < 2) {
            player.sendMessage(Component.text("Usage: /atlas spawn <structure_type>", NamedTextColor.RED))
            return
        }
        
        val typeStr = args[1].uppercase()
        try {
            val type = com.projectatlas.structures.StructureType.valueOf(typeStr)
            val plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(AtlasPlugin::class.java)
            plugin.structureManager.spawnStructure(type, player.location)
            player.sendMessage(Component.text("Spawned structure: $typeStr", NamedTextColor.GREEN))
        } catch (e: IllegalArgumentException) {
            player.sendMessage(Component.text("Invalid structure type. Valid: MERCHANT_HUT, QUEST_CAMP", NamedTextColor.RED))
        }
    }

    private fun handleSchematic(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage(Component.text("Usage: /atlas schem <pos1|pos2|save|load|paste> [name]", NamedTextColor.RED))
            return
        }

        when (args[1].lowercase()) {
            "pos1" -> {
                schematicManager.setPos1(player, player.location)
                player.sendMessage(Component.text("Position 1 set.", NamedTextColor.GREEN))
            }
            "pos2" -> {
                schematicManager.setPos2(player, player.location)
                player.sendMessage(Component.text("Position 2 set.", NamedTextColor.GREEN))
            }
            "save" -> {
                if (args.size < 3) {
                    player.sendMessage(Component.text("Usage: /atlas schem save <name>", NamedTextColor.RED))
                    return
                }
                val name = args[2]
                if (schematicManager.saveSchematic(name, player)) {
                    player.sendMessage(Component.text("Schematic '$name' saved!", NamedTextColor.GREEN))
                } else {
                    player.sendMessage(Component.text("Failed to save. Did you set pos1 and pos2?", NamedTextColor.RED))
                }
            }
            "load" -> {
                // Just check if it exists
                if (args.size < 3) return
                val name = args[2]
                val schem = schematicManager.loadSchematic(name)
                if (schem != null) {
                    player.sendMessage(Component.text("Schematic '$name' found: ${schem.width}x${schem.height}x${schem.length}", NamedTextColor.YELLOW))
                } else {
                    player.sendMessage(Component.text("Schematic not found.", NamedTextColor.RED))
                }
            }
            "paste" -> {
                if (args.size < 3) return
                if (!player.hasPermission("atlas.admin")) {
                    player.sendMessage(Component.text("No permission.", NamedTextColor.RED))
                    return
                }
                val name = args[2]
                schematicManager.pasteSchematic(name, player.location)
                player.sendMessage(Component.text("Pasted '$name'.", NamedTextColor.GREEN))
            }
        }
    }
    
    private fun handleBounty(player: Player, args: Array<out String>) {
        val plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(AtlasPlugin::class.java)
        
        if (args.size < 2) {
            // List bounties
            val bounties = plugin.bountyManager.listAllBounties()
            if (bounties.isEmpty()) {
                player.sendMessage(Component.text("No active bounties.", NamedTextColor.GRAY))
                return
            }
            player.sendMessage(Component.text("═══ ACTIVE BOUNTIES ═══", NamedTextColor.DARK_RED))
            bounties.take(10).forEach { b ->
                player.sendMessage(Component.text("  💀 ${b.targetName}: ${b.amount}g (${b.reason})", NamedTextColor.RED))
            }
            return
        }
        
        when (args[1].lowercase()) {
            "place", "set" -> {
                if (args.size < 4) {
                    player.sendMessage(Component.text("Usage: /atlas bounty place <player> <amount> [reason]", NamedTextColor.RED))
                    return
                }
                val targetName = args[2]
                val amount = args[3].toDoubleOrNull() ?: run {
                    player.sendMessage(Component.text("Invalid amount.", NamedTextColor.RED))
                    return
                }
                val reason = if (args.size > 4) args.slice(4 until args.size).joinToString(" ") else "Wanted"
                plugin.bountyManager.placeBounty(player, targetName, amount, reason)
            }
            "check" -> {
                if (args.size < 3) {
                    player.sendMessage(Component.text("Usage: /atlas bounty check <player>", NamedTextColor.RED))
                    return
                }
                val target = player.server.getOfflinePlayer(args[2])
                val total = plugin.bountyManager.getTotalBounty(target.uniqueId)
                if (total <= 0) {
                    player.sendMessage(Component.text("${target.name} has no bounty.", NamedTextColor.GRAY))
                } else {
                    player.sendMessage(Component.text("${target.name}'s bounty: ${total}g", NamedTextColor.GOLD))
                }
            }
        }
    }
    
    private fun handleBoss(player: Player, args: Array<out String>) {
        if (!player.hasPermission("atlas.admin")) {
            player.sendMessage(Component.text("No permission.", NamedTextColor.RED))
            return
        }
        
        val plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(AtlasPlugin::class.java)
        
        if (args.size < 2 || args[1].lowercase() == "spawn") {
            val type = if (args.size >= 3) args[2] else null
            if (plugin.worldBossManager.forceSpawn(player, type)) {
                player.sendMessage(Component.text("World Boss spawned!", NamedTextColor.GREEN))
            } else {
                player.sendMessage(Component.text("Failed to spawn (already active or on cooldown).", NamedTextColor.RED))
            }
        }
    }
    
    private fun handleRelic(player: Player, args: Array<out String>) {
        if (!player.hasPermission("atlas.admin")) {
            player.sendMessage(Component.text("No permission.", NamedTextColor.RED))
            return
        }
        
        val plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(AtlasPlugin::class.java)
        
        if (args.size < 2 || args[1].lowercase() == "spawn") {
            plugin.relicManager.spawnRelicInWorld(player.location)
            player.sendMessage(Component.text("Relic spawned!", NamedTextColor.GREEN))
        } else if (args[1].lowercase() == "give") {
            val type = com.projectatlas.relics.RelicManager.RelicType.entries.random()
            player.inventory.addItem(plugin.relicManager.createRelic(type))
            player.sendMessage(Component.text("Gave you a ${type.displayName}!", NamedTextColor.GOLD))
        }
    }
    
    private fun handleDungeon(player: Player, args: Array<out String>) {
        val plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(AtlasPlugin::class.java)
        
        if (args.size < 2) {
            // List dungeons
            player.sendMessage(Component.text("═══ AVAILABLE DUNGEONS ═══", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD))
            com.projectatlas.dungeon.DungeonManager.DungeonType.entries.forEach { dungeon ->
                val stars = "★".repeat(dungeon.difficulty) + "☆".repeat(5 - dungeon.difficulty)
                player.sendMessage(Component.text("  ${dungeon.displayName}", NamedTextColor.LIGHT_PURPLE))
                player.sendMessage(Component.text("    $stars | Theme: ${dungeon.theme}", NamedTextColor.GRAY))
            }
            player.sendMessage(Component.text("Use: /atlas dungeon enter <name>", NamedTextColor.YELLOW))
            return
        }
        
        when (args[1].lowercase()) {
            "enter", "join" -> {
                if (args.size < 3) {
                    player.sendMessage(Component.text("Usage: /atlas dungeon enter <dungeon_name>", NamedTextColor.RED))
                    return
                }
                
                val dungeonName = args.slice(2 until args.size).joinToString(" ")
                val type = com.projectatlas.dungeon.DungeonManager.DungeonType.entries.find { 
                    it.displayName.equals(dungeonName, true) || it.name.equals(dungeonName.replace(" ", "_"), true)
                }
                
                if (type == null) {
                    player.sendMessage(Component.text("Dungeon not found! Use /atlas dungeon to see available dungeons.", NamedTextColor.RED))
                    return
                }
                
                plugin.dungeonManager.enterDungeon(player, type)
            }
            "leave", "exit" -> {
                plugin.dungeonManager.leaveDungeon(player)
            }
            else -> player.sendMessage(Component.text("Unknown dungeon command.", NamedTextColor.RED))
        }
    }
    
    private fun handleParty(player: Player, args: Array<out String>) {
        val plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(AtlasPlugin::class.java)
        
        if (args.size < 2) {
            plugin.partyManager.showPartyInfo(player)
            return
        }
        
        when (args[1].lowercase()) {
            "create" -> {
                plugin.partyManager.createParty(player)
            }
            "invite" -> {
                if (args.size < 3) {
                    player.sendMessage(Component.text("Usage: /atlas party invite <player>", NamedTextColor.RED))
                    return
                }
                plugin.partyManager.invitePlayer(player, args[2])
            }
            "accept" -> {
                plugin.partyManager.acceptInvite(player)
            }
            "decline" -> {
                plugin.partyManager.declineInvite(player)
            }
            "leave" -> {
                plugin.partyManager.leaveParty(player)
            }
            "kick" -> {
                if (args.size < 3) {
                    player.sendMessage(Component.text("Usage: /atlas party kick <player>", NamedTextColor.RED))
                    return
                }
                plugin.partyManager.kickPlayer(player, args[2])
            }
            "disband" -> {
                plugin.partyManager.disbandParty(player)
            }
            "info" -> {
                plugin.partyManager.showPartyInfo(player)
            }
            else -> {
                player.sendMessage(Component.text("Party commands:", NamedTextColor.GOLD))
                player.sendMessage(Component.text("  /atlas party create - Create a party", NamedTextColor.YELLOW))
                player.sendMessage(Component.text("  /atlas party invite <player> - Invite someone", NamedTextColor.YELLOW))
                player.sendMessage(Component.text("  /atlas party accept/decline - Respond to invite", NamedTextColor.YELLOW))
                player.sendMessage(Component.text("  /atlas party leave - Leave your party", NamedTextColor.YELLOW))
                player.sendMessage(Component.text("  /atlas party kick <player> - Kick a member", NamedTextColor.YELLOW))
                player.sendMessage(Component.text("  /atlas party disband - Disband the party", NamedTextColor.YELLOW))
            }
        }
    }
    
    private fun handleBlueprint(player: Player, args: Array<out String>) {
        val plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(AtlasPlugin::class.java)
        
        if (args.size < 2) {
            player.sendMessage(Component.text("Blueprint Commands:", NamedTextColor.GOLD, TextDecoration.BOLD))
            player.sendMessage(Component.text("  /atlas bp wand - Get selection wand", NamedTextColor.YELLOW))
            player.sendMessage(Component.text("  /atlas bp capture <name> <price> - Capture and list", NamedTextColor.YELLOW))
            player.sendMessage(Component.text("  /atlas bp list - Browse marketplace", NamedTextColor.YELLOW))
            player.sendMessage(Component.text("  /atlas bp mine - View your blueprints", NamedTextColor.YELLOW))
            player.sendMessage(Component.text("  /atlas bp preview <name> - Preview a blueprint", NamedTextColor.YELLOW))
            player.sendMessage(Component.text("  /atlas bp buy <name> - Purchase a blueprint", NamedTextColor.YELLOW))
            player.sendMessage(Component.text("  /atlas bp place - Place previewed blueprint", NamedTextColor.YELLOW))
            player.sendMessage(Component.text("  /atlas bp cancel - Cancel preview", NamedTextColor.YELLOW))
            return
        }
        
        when (args[1].lowercase()) {
            "wand" -> {
                plugin.blueprintMarketplace.giveSelectionWand(player)
            }
            "capture", "save" -> {
                if (args.size < 4) {
                    player.sendMessage(Component.text("Usage: /atlas bp capture <name> <price>", NamedTextColor.RED))
                    return
                }
                val price = args.last().toDoubleOrNull()
                if (price == null) {
                    player.sendMessage(Component.text("Invalid price!", NamedTextColor.RED))
                    return
                }
                val name = args.slice(2 until args.size - 1).joinToString(" ")
                plugin.blueprintMarketplace.captureBlueprint(player, name, price)
            }
            "list", "market", "shop" -> {
                val page = args.getOrNull(2)?.toIntOrNull() ?: 1
                plugin.blueprintMarketplace.listBlueprints(player, page)
            }
            "mine", "my" -> {
                plugin.blueprintMarketplace.getMyBlueprints(player)
            }
            "preview" -> {
                if (args.size < 3) {
                    player.sendMessage(Component.text("Usage: /atlas bp preview <name>", NamedTextColor.RED))
                    return
                }
                val name = args.slice(2 until args.size).joinToString(" ")
                plugin.blueprintMarketplace.startPreview(player, name)
            }
            "buy", "purchase" -> {
                if (args.size < 3) {
                    player.sendMessage(Component.text("Usage: /atlas bp buy <name>", NamedTextColor.RED))
                    return
                }
                val name = args.slice(2 until args.size).joinToString(" ")
                plugin.blueprintMarketplace.purchaseBlueprint(player, name)
            }
            "place" -> {
                plugin.blueprintMarketplace.placeBlueprint(player)
            }
            "force" -> {
                plugin.blueprintMarketplace.forcePlaceBlueprint(player)
            }
            "cancel" -> {
                plugin.blueprintMarketplace.cancelPreview(player)
                player.sendMessage(Component.text("Preview cancelled.", NamedTextColor.YELLOW))
            }
            "unlist", "toggle" -> {
                if (args.size < 3) {
                    player.sendMessage(Component.text("Usage: /atlas bp unlist <name>", NamedTextColor.RED))
                    return
                }
                val name = args.slice(2 until args.size).joinToString(" ")
                plugin.blueprintMarketplace.unlistBlueprint(player, name)
            }
            "delete" -> {
                if (args.size < 3) {
                    player.sendMessage(Component.text("Usage: /atlas bp delete <name>", NamedTextColor.RED))
                    return
                }
                val name = args.slice(2 until args.size).joinToString(" ")
                plugin.blueprintMarketplace.deleteBlueprint(player, name)
            }
        }
    }
    
    private fun handleMenu(player: Player) {
        val plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(AtlasPlugin::class.java)
        plugin.guiManager.openMainMenu(player)
    }
    
    // ============ QOL COMMANDS ============
    
    private fun handleSort(player: Player) {
        val plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(AtlasPlugin::class.java)
        plugin.qolManager.sortInventory(player)
    }
    
    private fun handleStats(player: Player, args: Array<out String>) {
        val plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(AtlasPlugin::class.java)
        
        player.sendMessage(Component.empty())
        player.sendMessage(Component.text("═══════════════════════════════", NamedTextColor.GOLD))
        player.sendMessage(Component.text("  📊 YOUR STATISTICS", NamedTextColor.YELLOW))
        player.sendMessage(Component.text("═══════════════════════════════", NamedTextColor.GOLD))
        
        // PvP Stats
        val (kills, deaths) = plugin.qolManager.getPvPStats(player)
        val kd = if (deaths > 0) kills.toDouble() / deaths else kills.toDouble()
        player.sendMessage(Component.text("  ⚔ PvP K/D: $kills / $deaths (${String.format("%.2f", kd)})", NamedTextColor.RED))
        
        // Mob Kill Stats (top 5)
        val mobStats = plugin.qolManager.getKillStats(player)
            .entries.sortedByDescending { it.value }.take(5)
        
        if (mobStats.isNotEmpty()) {
            player.sendMessage(Component.empty())
            player.sendMessage(Component.text("  🎯 Top Mob Kills:", NamedTextColor.GREEN))
            mobStats.forEach { (mob, count) ->
                player.sendMessage(Component.text("    ${mob.lowercase().replace("_", " ")}: $count", NamedTextColor.GRAY))
            }
        }
        
        player.sendMessage(Component.text("═══════════════════════════════", NamedTextColor.GOLD))
        player.sendMessage(Component.empty())
    }
    
    private fun handleScoreboard(player: Player) {
        val plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(AtlasPlugin::class.java)
        plugin.qolManager.toggleScoreboard(player)
    }
    
    private fun handleDamageToggle(player: Player) {
        val plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(AtlasPlugin::class.java)
        plugin.qolManager.toggleDamageNumbers(player)
    }
    
    private fun handleQuickStack(player: Player) {
        val plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(AtlasPlugin::class.java)
        plugin.qolManager.quickStack(player)
    }

    private fun handleAtmosphere(player: Player) {
        val plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(AtlasPlugin::class.java)
        plugin.atmosphereManager.toggleAtmosphere(player)
    }


    // ============ TAB COMPLETION ============
    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (sender !is Player) return emptyList()
        val plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(AtlasPlugin::class.java)
        
        return when (args.size) {
            1 -> listOf(
                "profile", "balance", "pay", "city", "help", "menu",
                "dungeon", "party", "bounty", "boss", "relic", "blueprint", "bp", "schem", "spawn",
                "sort", "stats", "scoreboard", "sb", "damage", "dmg", "quickstack", "qs",
                "atmosphere", "ambient", "shaders"
            ).filter { it.startsWith(args[0].lowercase()) }
            
            2 -> when (args[0].lowercase()) {
                "city" -> listOf("create", "join", "leave", "claim", "deposit", "tax", "invite", "kick", "info", "election", "vote")
                    .filter { it.startsWith(args[1].lowercase()) }
                "dungeon" -> listOf("enter", "leave", "modifiers")
                    .filter { it.startsWith(args[1].lowercase()) }
                "party" -> listOf("create", "invite", "accept", "decline", "leave", "kick", "disband", "info")
                    .filter { it.startsWith(args[1].lowercase()) }
                "bounty" -> listOf("place", "check", "list")
                    .filter { it.startsWith(args[1].lowercase()) }
                "boss" -> listOf("spawn", "info")
                    .filter { it.startsWith(args[1].lowercase()) }
                "relic" -> listOf("give", "spawn", "list")
                    .filter { it.startsWith(args[1].lowercase()) }
                "blueprint", "bp" -> listOf("wand", "capture", "list", "mine", "preview", "buy", "place", "cancel", "unlist", "delete")
                    .filter { it.startsWith(args[1].lowercase()) }
                "schem" -> listOf("pos1", "pos2", "save", "load", "paste").filter { it.startsWith(args[1].lowercase()) }
                "spawn" -> listOf("merchant_hut", "quest_camp").filter { it.startsWith(args[1].lowercase()) }
                "pay" -> org.bukkit.Bukkit.getOnlinePlayers().map { it.name }
                    .filter { it.lowercase().startsWith(args[1].lowercase()) }
                else -> emptyList()
            }
            
            3 -> when (args[0].lowercase()) {
                "dungeon" -> if (args[1].lowercase() in listOf("enter", "join")) {
                    com.projectatlas.dungeon.DungeonManager.DungeonType.entries
                        .map { it.displayName }
                        .filter { it.lowercase().startsWith(args[2].lowercase()) }
                } else emptyList()
                
                "party" -> if (args[1].lowercase() in listOf("invite", "kick")) {
                    org.bukkit.Bukkit.getOnlinePlayers().map { it.name }
                        .filter { it.lowercase().startsWith(args[2].lowercase()) }
                } else emptyList()
                
                "bounty" -> if (args[1].lowercase() in listOf("place", "check")) {
                    org.bukkit.Bukkit.getOnlinePlayers().map { it.name }
                        .filter { it.lowercase().startsWith(args[2].lowercase()) }
                } else emptyList()
                
                "blueprint", "bp" -> when (args[1].lowercase()) {
                    "preview", "buy", "unlist", "delete" -> {
                        plugin.blueprintMarketplace.let { bp ->
                            // Get blueprint names
                            listOf("<blueprint_name>") // Logic to get names can be improved later
                        }
                    }
                    else -> emptyList()
                }
                
                "boss" -> if (args[1].lowercase() == "spawn") {
                    com.projectatlas.worldboss.WorldBossManager.BossType.entries
                        .map { it.name }
                        .filter { it.lowercase().startsWith(args[2].lowercase()) }
                } else emptyList()
                
                "relic" -> if (args[1].lowercase() == "give") {
                    com.projectatlas.relics.RelicManager.RelicType.entries
                        .map { it.name }
                        .filter { it.lowercase().startsWith(args[2].lowercase()) }
                } else emptyList()
                
                "city" -> when (args[1].lowercase()) {
                    "invite", "kick" -> org.bukkit.Bukkit.getOnlinePlayers().map { it.name }
                        .filter { it.lowercase().startsWith(args[2].lowercase()) }
                    "vote" -> org.bukkit.Bukkit.getOnlinePlayers().map { it.name }
                        .filter { it.lowercase().startsWith(args[2].lowercase()) }
                    else -> emptyList()
                }
                
                else -> emptyList()
            }
            
            // For dungeon enter, suggest modifiers after dungeon name
            else -> emptyList()
        }
    }
}

