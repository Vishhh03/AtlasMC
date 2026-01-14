package com.projectatlas.party

import com.projectatlas.AtlasPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Party Chat Command - /pc <message>
 */
class PartyChatCommand(private val plugin: AtlasPlugin) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("Only players can use party chat.")
            return true
        }

        if (args.isEmpty()) {
            sender.sendMessage(Component.text("Usage: /pc <message>", NamedTextColor.RED))
            return true
        }

        val message = args.joinToString(" ")
        plugin.partyManager.sendPartyChat(sender, message)
        return true
    }
}
