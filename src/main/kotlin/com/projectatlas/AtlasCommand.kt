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

class AtlasCommand(
    private val identityManager: IdentityManager,
    private val economyManager: EconomyManager,
    private val cityManager: CityManager
) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("Only players can use this command.")
            return true
        }

        if (args.isEmpty()) {
            sendHelp(sender)
            return true
        }

        when (args[0].lowercase()) {
            "profile" -> handleProfile(sender)
            "balance", "bal" -> handleBalance(sender)
            "pay" -> handlePay(sender, args)
            "city" -> handleCity(sender, args)
            "event" -> handleEvent(sender, args)
            else -> sendHelp(sender)
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
            player.sendMessage(Component.text("Usage: /atlas city <create|claim|invite|join|kick|leave|info>", NamedTextColor.RED))
            return
        }

        when (args[1].lowercase()) {
            "create" -> {
                if (args.size < 3) {
                    player.sendMessage(Component.text("Usage: /atlas city create <name>", NamedTextColor.RED))
                    return
                }
                val name = args[2]
                val city = cityManager.createCity(name, player)
                if (city != null) {
                    val profile = identityManager.getPlayer(player.uniqueId)
                    profile?.cityId = city.id
                    player.sendMessage(Component.text("City $name created!", NamedTextColor.GREEN))
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

                if (cityManager.claimChunk(city.id, player.location.chunk)) {
                    player.sendMessage(Component.text("Territory claimed!", NamedTextColor.GREEN))
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
                } else {
                    player.sendMessage(Component.text("Wilderness", NamedTextColor.GRAY))
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

    private fun sendHelp(player: Player) {
        player.sendMessage(Component.text("--- Project Atlas Help ---", NamedTextColor.GOLD))
        player.sendMessage(Component.text("/atlas profile - View stats", NamedTextColor.YELLOW))
        player.sendMessage(Component.text("/atlas bal - View balance", NamedTextColor.YELLOW))
        player.sendMessage(Component.text("/atlas pay <player> <amount>", NamedTextColor.YELLOW))
        player.sendMessage(Component.text("/atlas city <create|claim|invite|join|kick|leave|info>", NamedTextColor.YELLOW))
        if (player.hasPermission("atlas.admin")) {
            player.sendMessage(Component.text("/atlas event start - Force event", NamedTextColor.RED))
        }
    }

    // --- Tab Completion ---

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String>? {
        if (args.size == 1) {
            return listOf("profile", "bal", "pay", "city", "event").filter { it.startsWith(args[0], true) }
        }
        
        if (args[0].equals("city", true)) {
            if (args.size == 2) {
                return listOf("create", "claim", "invite", "join", "kick", "leave", "info").filter { it.startsWith(args[1], true) }
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

        return emptyList()
    }
}
