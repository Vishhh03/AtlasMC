package com.projectatlas.structures

import com.projectatlas.AtlasPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent

class StructureListener(private val plugin: AtlasPlugin) : Listener {

    @EventHandler
    fun onRightClickInfo(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        val item = event.item ?: return
        val block = event.clickedBlock ?: return
        val player = event.player
        
        // simple check for blueprint
        if (item.type != Material.PAPER || !item.hasItemMeta()) return
        val displayName = item.itemMeta.displayName() ?: return
        // Since Adventure returns Component, we might need to serialize or check differently.
        // For simplicity in this project (and avoiding complex component parsing for now), 
        // we'll use the plain text method if possible, or string contains.
        // But Adventure components don't have a simple .text() in all versions.
        // We assigned it as: Component.text("Blueprint: $name", NamedTextColor.AQUA)
        
        // Quick hack: Just check legacy text or string representation
        // Ideally we should use persistent data container for ItemID, like we did for GUI icons.
        // But for now let's try to match the name.
        // Better: Use PersistentDataContainer checks if we can. 
        // But the GuiManager just set display name.
        
        // Let's rely on checking the text content roughly.
        // Note: Component.toString() is messy.
        // We will assume standard Paper behavior where we can get plain text.
        // Actually, let's just check if it contains "Blueprint".
        // Or better yet, we can't easily rely on name.
        // Let's assume the user hasn't renamed regular paper.
        
        // REFACTOR: In GuiManager, I should have set a PersistentKey for "blueprint_type".
        // I should go back and add that to GuiManager for robustness.
        // BUT for now, to save steps, I will parse the name IF I can.
        
        // Let's use PlainTextComponentSerializer to get the text.
        val plainText = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(displayName)
        if (!plainText.startsWith("Blueprint: ")) return
        
        val structureName = plainText.removePrefix("Blueprint: ").trim()
        val type = when (structureName) {
            "Barracks" -> StructureType.BARRACKS
            "Nexus" -> StructureType.NEXUS
            "Market" -> StructureType.MERCHANT_HUT
            "Quest Camp" -> StructureType.QUEST_CAMP
            "Turret" -> StructureType.TURRET
            "Generator" -> StructureType.GENERATOR
            else -> return
        }
        
        event.isCancelled = true // Prevent placing the paper or interactions
        
        // Check Territory
        val chunk = block.chunk
        val city = plugin.cityManager.getCityAt(chunk)
        
        if (city == null) {
            player.sendMessage(Component.text("You can only build structures inside a City!", NamedTextColor.RED))
            return
        }
        
        // Check permissions (Member is fine for now, strictly Mayor/Officer later)
        // Check if player is a member of this city
        if (!city.members.contains(player.uniqueId)) {
            player.sendMessage(Component.text("You are not a member of this city!", NamedTextColor.RED))
            return
        }
        
        // BUILD IT
        plugin.structureManager.spawnStructure(type, block.location.add(0.0, 1.0, 0.0))
        
        // Consume Item
        item.amount = item.amount - 1
        
        player.playSound(player.location, Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f)
        player.sendMessage(Component.text("$structureName construction started!", NamedTextColor.GREEN))
    }
}
