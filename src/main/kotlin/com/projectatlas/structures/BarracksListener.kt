package com.projectatlas.structures

import com.projectatlas.AtlasPlugin
import com.projectatlas.npc.NPC
import com.projectatlas.npc.NPCType
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.inventory.ItemStack

class BarracksListener(private val plugin: AtlasPlugin) : Listener {

    private val recruitmentTitle = Component.text("Recruit Guards", NamedTextColor.DARK_BLUE)

    @EventHandler
    fun onInteractNPC(event: PlayerInteractEntityEvent) {
        // Simple check for interacting with Captain NPC (Name based for now, ideally persistent data)
        val entity = event.rightClicked
        val name = entity.customName() ?: return
        
        if (name == Component.text("Captain Sterling")) {
            openRecruitmentMenu(event.player)
        }
    }
    
    private fun openRecruitmentMenu(player: Player) {
        val inv = Bukkit.createInventory(null, 27, recruitmentTitle)
        
        // Slot 11: Swordsman
        val swordsman = ItemStack(Material.IRON_SWORD)
        swordsman.editMeta { meta ->
            meta.displayName(Component.text("Recruit Swordsman", NamedTextColor.AQUA))
            meta.lore(listOf(
                Component.text("Cost: 64 Gold Nuggets", NamedTextColor.YELLOW),
                Component.text("A basic melee defender.", NamedTextColor.GRAY)
            ))
        }
        inv.setItem(11, swordsman)
        
        // Slot 15: Archer
        val archer = ItemStack(Material.BOW)
        archer.editMeta { meta ->
            meta.displayName(Component.text("Recruit Archer", NamedTextColor.GREEN))
            meta.lore(listOf(
                Component.text("Cost: 32 Gold Nuggets", NamedTextColor.YELLOW),
                Component.text("Ranged support unit.", NamedTextColor.GRAY)
            ))
        }
        inv.setItem(15, archer)
        
        player.openInventory(inv)
        player.playSound(player.location, Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f)
    }
    
    @EventHandler
    fun onMenuClick(event: InventoryClickEvent) {
        if (event.view.title() != recruitmentTitle) return
        event.isCancelled = true // Prevent taking items
        
        val player = event.whoClicked as? Player ?: return
        val current = event.currentItem ?: return
        
        when (current.type) {
            Material.IRON_SWORD -> handleRecruit(player, NPCType.GUARD, 64)
            Material.BOW -> handleRecruit(player, NPCType.ARCHER, 32) // Assuming ARCHER type exists or mapping to GUARD w/ Bow
            else -> {}
        }
    }
    
    private fun handleRecruit(player: Player, type: NPCType, cost: Int) {
        // Check cost
        if (!player.inventory.containsAtLeast(ItemStack(Material.GOLD_NUGGET), cost)) {
            player.sendMessage(Component.text("Not enough Gold Nuggets!", NamedTextColor.RED))
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            return
        }
        
        // Deduct cost
        player.inventory.removeItem(ItemStack(Material.GOLD_NUGGET, cost))
        
        // Spawn Guard
        val spawnLoc = player.location
        val npc = NPC(name = "City Guard", type = type)
        plugin.npcManager.spawnNPC(npc, spawnLoc)
        
        player.sendMessage(Component.text("Recruited a new unit!", NamedTextColor.GREEN))
        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f)
        player.closeInventory()
    }
}
