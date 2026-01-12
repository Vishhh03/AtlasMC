package com.projectatlas

import com.projectatlas.city.CityManager
import com.projectatlas.economy.EconomyManager
import com.projectatlas.identity.IdentityManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

import com.projectatlas.gui.GuiManager
import com.projectatlas.classes.ClassManager
import com.projectatlas.schematic.SchematicManager

class AtlasCommand(
    private val identityManager: IdentityManager,
    private val economyManager: EconomyManager,
    private val cityManager: CityManager,
    private val classManager: ClassManager,
    private val guiManager: GuiManager,
    private val schematicManager: com.projectatlas.schematic.SchematicManager
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
            "class" -> handleClass(sender, args)
            "help" -> handleHelp(sender)
            "spawn" -> handleSpawn(sender, args)
            "schem" -> handleSchematic(sender, args)
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
        player.sendMessage(Component.text("--- Project Atlas Help ---", NamedTextColor.GOLD))
        player.sendMessage(Component.text("/atlas profile - View stats", NamedTextColor.YELLOW))
        player.sendMessage(Component.text("/atlas bal - View balance", NamedTextColor.YELLOW))
        player.sendMessage(Component.text("/atlas pay <player> <amount>", NamedTextColor.YELLOW))
        player.sendMessage(Component.text("/atlas city <create|claim|invite|join|kick|leave|info|tax|deposit>", NamedTextColor.YELLOW))
        player.sendMessage(Component.text("/atlas class choose <name> - Pick a class", NamedTextColor.YELLOW))
        if (player.hasPermission("atlas.admin")) {
            player.sendMessage(Component.text("/atlas event start - Force event", NamedTextColor.RED))
        }
    }

    private fun handleClass(player: Player, args: Array<out String>) {
        if (args.size < 3 || !args[1].equals("choose", true)) {
            player.sendMessage(Component.text("Usage: /atlas class choose <name>", NamedTextColor.RED))
            val classes = classManager.getAvailableClasses().joinToString(", ")
            player.sendMessage(Component.text("Available: $classes", NamedTextColor.GRAY))
            return
        }
        
        val className = args[2]
        // Normalize name case (simple hack)
        val properName = classManager.getAvailableClasses().find { it.equals(className, true) }
        
        if (properName == null) {
            player.sendMessage(Component.text("Invalid class. Choices: ${classManager.getAvailableClasses()}", NamedTextColor.RED))
            return
        }
        
        if (classManager.setClass(player, properName)) {
             player.sendMessage(Component.text("You are now a $properName!", NamedTextColor.GREEN))
        } else {
             player.sendMessage(Component.text("Failed to set class.", NamedTextColor.RED))
        }
    }

    // --- Tab Completion ---

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String>? {
        if (args.size == 1) {
            return listOf("profile", "bal", "pay", "city", "event", "class", "spawn").filter { it.startsWith(args[0], true) }
        }
        
        if (args[0].equals("city", true)) {
            if (args.size == 2) {
                return listOf("create", "claim", "invite", "join", "kick", "leave", "info", "tax", "deposit").filter { it.startsWith(args[1], true) }
            }
            // City Invite/Kick autocompletion
            if (args.size == 3 && (args[1].equals("invite", true) || args[1].equals("kick", true))) {
                return null // Return null to let Bukkit autocomplete player names
            }
        }
        
        if (args[0].equals("pay", true) && args.size == 2) {
            return null // Player names
        }
        
        if (args[0].equals("event", true) && args.size == 2 && sender.hasPermission("atlas.admin")) {
            return listOf("start").filter { it.startsWith(args[1], true) }
        }
        
        if (args[0].equals("class", true)) {
            if (args.size == 2) {
                return listOf("choose").filter { it.startsWith(args[1], true) }
            }
            if (args.size == 3 && args[1].equals("choose", true)) {
                return classManager.getAvailableClasses().filter { it.startsWith(args[2], true) }
            }
        }

        if (args[0].equals("spawn", true) && args.size == 2 && sender.hasPermission("atlas.admin")) {
            return listOf("merchant_hut", "quest_camp").filter { it.startsWith(args[1], true) }
        }

        if (args[0].equals("schem", true) && args.size == 2) {
            return listOf("pos1", "pos2", "save", "load", "paste").filter { it.startsWith(args[1], true) }
        }

        return emptyList()
    }
    
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
}
