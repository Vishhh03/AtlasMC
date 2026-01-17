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
    /**
     * Open a generic dialogue for a player
     */
    fun openDialogue(player: Player, dialogue: Dialogue, npc: NPC? = null) {
        // Delegate to Cinematic Manager
        plugin.cinematicDialogueManager.startCinematicDialogue(player, npc, dialogue)
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
                    DialogueOption("Trade", "/atlas dialogue trade ${npc.id}", NamedTextColor.GREEN, "Click to view shop"),
                    DialogueOption("Goodbye", "/atlas dialogue close ${npc.id}", NamedTextColor.RED, "Click to leave")
                )
            )
            NPCType.QUEST_GIVER -> {
                if (plugin.questManager.getActiveQuest(player) == null) {
                    Dialogue(
                        npc.name,
                        "I have a task that requires a brave soul. Are you interested?",
                        listOf(
                            DialogueOption("I'll help!", "/atlas dialogue quest_accept ${npc.id}", NamedTextColor.GREEN, "Accept quest"),
                            DialogueOption("Not now", "/atlas dialogue close ${npc.id}", NamedTextColor.RED, "Decline")
                        )
                    )
                } else {
                    Dialogue(
                        npc.name,
                        "You already have a quest. Focus on that first!",
                        listOf(
                            DialogueOption("Understood", "/atlas dialogue close ${npc.id}", NamedTextColor.GRAY, "Close")
                        )
                    )
                }
            }
            NPCType.GUARD, NPCType.ARCHER -> Dialogue(
                npc.name,
                "Move along, citizen. I am on duty.",
                listOf(
                    DialogueOption("Goodbye", "/atlas dialogue close ${npc.id}", NamedTextColor.GRAY, "Leave")
                )
            )
        }
        openDialogue(player, dialogue, npc)
    }
    
    private fun createOptionComponent(text: String, command: String, color: NamedTextColor, hoverText: String): Component {
        return Component.text(text, color)
            .decorate(TextDecoration.BOLD)
            .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(Component.text(hoverText, NamedTextColor.GRAY)))
            .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(command))
    }

    // Handled via AtlasCommand now
    fun handleDialogueCommand(player: Player, args: Array<out String>) {
        if (args.size < 3) return
        
        val action = args[1]
        val npcId = args[2]
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
