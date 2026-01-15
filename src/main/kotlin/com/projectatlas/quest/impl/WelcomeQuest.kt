package com.projectatlas.quest.impl

import com.projectatlas.integration.TypewriterManager
import com.projectatlas.quest.CodeableQuest
import org.bukkit.Material
import org.bukkit.entity.Player

class WelcomeQuest : CodeableQuest {
    override val id = "welcome_quest"
    override val name = "Welcome to Atlas"
    override val description = "Get to know the city."

    override fun canStart(player: Player): Boolean {
        // Example condition: Always allow for testing
        return true
    }

    override fun onStart(player: Player) {
        player.sendMessage("§e[Quest] Starting Welcome Quest...")
        
        // Trigger Typewriter visual
        // This assumes a script named 'welcome_intro' exists or will be created in Typewriter plugin
        TypewriterManager.startVisuals(player, "welcome_intro")
        
        player.sendMessage("§7Hint: Watch the cutscene (if configured)!")
    }

    override fun onComplete(player: Player) {
         player.sendMessage("§a[Quest] Welcome Quest Completed!")
         player.inventory.addItem(org.bukkit.inventory.ItemStack(Material.COOKED_BEEF, 16))
    }
}
