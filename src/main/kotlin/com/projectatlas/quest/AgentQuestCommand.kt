package com.projectatlas.quest

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class AgentQuestCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("Players only.")
            return true
        }
        
        if (args.isEmpty()) {
            sender.sendMessage("Usage: /agentquest start <questId>")
            return true
        }
        
        when (args[0].lowercase()) {
            "start" -> {
                if (args.size < 2) {
                    sender.sendMessage("Usage: /agentquest start <questId>")
                    return true
                }
                val questId = args[1]
                if (AgentQuestManager.startQuest(sender, questId)) {
                    sender.sendMessage("§aStarted quest: $questId")
                } else {
                    sender.sendMessage("§cCould not start quest '$questId'. Check ID or requirements.")
                }
            }
        }
        return true
    }
}
