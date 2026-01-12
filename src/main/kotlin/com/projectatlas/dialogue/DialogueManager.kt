package com.projectatlas.dialogue

import com.projectatlas.AtlasPlugin
import com.projectatlas.npc.NPC
import com.projectatlas.npc.NPCType
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.NamespacedKey

class DialogueManager(private val plugin: AtlasPlugin) : Listener {

    /**
     * Open a generic dialogue for a player
     */
    fun openDialogue(player: Player, dialogue: Dialogue) {
        // 1. Play Sound
        player.playSound(player.location, org.bukkit.Sound.ENTITY_VILLAGER_TRADE, 1.0f, 1.0f)
        
        // 2. Send Message (Immersive Chat)
        player.sendMessage(Component.empty())
        player.sendMessage(
            Component.text(dialogue.speakerName, NamedTextColor.GOLD).decorate(TextDecoration.BOLD)
                .append(Component.text(": ", NamedTextColor.GRAY))
                .append(Component.text(dialogue.text, NamedTextColor.WHITE))
        )
        player.sendMessage(Component.empty())
        
        // 3. Send Clickable Options
        dialogue.options.forEach { option ->
            player.sendMessage(createOptionComponent("  [${option.text}]  ", option.command, option.color, option.hoverText))
        }
        
        // 4. UX Hint
        player.sendMessage(Component.empty())
        player.sendMessage(Component.text(" [Press T to select an option]", NamedTextColor.DARK_GRAY, TextDecoration.ITALIC))
        player.sendMessage(Component.empty())
    }

    /**
     * Helper: Convert NPC to Dialogue and open it
     */
    fun openDialogue(player: Player, npc: NPC) {
        val dialogue = when (npc.type) {
            NPCType.MERCHANT -> Dialogue(
                npc.name,
                "Greetings, traveler! Looking for fine wares?",
                listOf(
                    DialogueOption("Trade", "/atlas_dialogue trade ${npc.id}", NamedTextColor.GREEN, "Click to view shop"),
                    DialogueOption("Goodbye", "/atlas_dialogue close ${npc.id}", NamedTextColor.RED, "Click to leave")
                )
            )
            NPCType.QUEST_GIVER -> {
                if (plugin.questManager.getActiveQuest(player) == null) {
                    Dialogue(
                        npc.name,
                        "I have a task that requires a brave soul. Are you interested?",
                        listOf(
                            DialogueOption("I'll help!", "/atlas_dialogue quest_accept ${npc.id}", NamedTextColor.GREEN, "Accept quest"),
                            DialogueOption("Not now", "/atlas_dialogue close ${npc.id}", NamedTextColor.RED, "Decline")
                        )
                    )
                } else {
                    Dialogue(
                        npc.name,
                        "You already have a quest. Focus on that first!",
                        listOf(
                            DialogueOption("Understood", "/atlas_dialogue close ${npc.id}", NamedTextColor.GRAY, "Close")
                        )
                    )
                }
            }
        }
        openDialogue(player, dialogue)
    }
    
    private fun createOptionComponent(text: String, command: String, color: NamedTextColor, hoverText: String): Component {
        return Component.text(text, color)
            .decorate(TextDecoration.BOLD)
            .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(Component.text(hoverText, NamedTextColor.GRAY)))
            .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(command))
    }

    @EventHandler
    fun onCommandPreprocess(event: org.bukkit.event.player.PlayerCommandPreprocessEvent) {
        val message = event.message
        if (!message.startsWith("/atlas_dialogue ")) return
        
        event.isCancelled = true
        val parts = message.substring(16).split(" ") // remove "/atlas_dialogue "
        if (parts.size < 2) return
        
        val action = parts[0]
        val npcId = parts[1]
        val player = event.player
        val npc = plugin.npcManager.getNPC(npcId) ?: return
        
        when (action) {
            "trade" -> plugin.npcManager.openMerchantMenu(player, npc)
            "quest_accept" -> {
                val quest = plugin.questManager.getQuestTemplates().random()
                plugin.questManager.startQuest(player, quest)
            }
            "close" -> {
                player.sendMessage(
                    Component.text(npc.name, NamedTextColor.GOLD)
                        .append(Component.text(": Safe travels!", NamedTextColor.WHITE))
                )
                player.playSound(player.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            }
        }
    }
}
