package com.projectatlas.gui

import com.projectatlas.AtlasPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.NamespacedKey

class GuiManager(private val plugin: AtlasPlugin) : Listener {

    private val menuKey = NamespacedKey(plugin, "gui_menu")

    fun openMainMenu(player: Player) {
        val inv = Bukkit.createInventory(null, 27, Component.text("Project Atlas Menu", NamedTextColor.DARK_BLUE))
        
        // Items
        inv.setItem(11, createGuiItem(Material.BOOK, "Your Profile", "Click to view stats"))
        inv.setItem(13, createGuiItem(Material.DIAMOND_SWORD, "Class Selector", "Choose your combat role"))
        inv.setItem(15, createGuiItem(Material.BEACON, "City Management", "Manage your city"))

        player.openInventory(inv)
    }

    fun openClassMenu(player: Player) {
        val inv = Bukkit.createInventory(null, 27, Component.text("Select Class", NamedTextColor.DARK_GREEN))
        
        inv.setItem(10, createGuiItem(Material.IRON_CHESTPLATE, "Vanguard", "Health: 30", "Role: Tank"))
        inv.setItem(12, createGuiItem(Material.FEATHER, "Scout", "Speed: Fast", "Role: Explorer"))
        inv.setItem(14, createGuiItem(Material.POTION, "Medic", "Regen: Passive", "Role: Healer"))
        inv.setItem(26, createGuiItem(Material.BARRIER, "Back", "Return to Main Menu"))

        player.openInventory(inv)
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val item = event.currentItem ?: return
        val meta = item.itemMeta ?: return
        
        // Only handle clicks on items marked as Atlas GUI items
        if (!meta.persistentDataContainer.has(menuKey, PersistentDataType.BYTE)) return
        
        event.isCancelled = true // Prevent taking items
        val player = event.whoClicked as Player
        handleMenuClick(player, item)
    }

    private fun handleMenuClick(player: Player, item: ItemStack) {
        val meta = item.itemMeta ?: return
        val nameComponent = meta.displayName() ?: return
        // Ideally serialize component to string or check persistence data
        // Quick MVP hack: Check material type or localized name if available
        
        when (item.type) {
             Material.BOOK -> player.performCommand("atlas profile")
             Material.DIAMOND_SWORD -> openClassMenu(player)
             Material.BEACON -> player.sendMessage(Component.text("Use /atlas city commands directly for now!", NamedTextColor.GRAY))
             
             Material.IRON_CHESTPLATE -> {
                 player.performCommand("atlas class choose Vanguard")
                 player.closeInventory()
             }
             Material.FEATHER -> {
                 player.performCommand("atlas class choose Scout")
                 player.closeInventory()
             }
             Material.POTION -> {
                 player.performCommand("atlas class choose Medic")
                 player.closeInventory()
             }
             Material.BARRIER -> openMainMenu(player)
             else -> {}
        }
    }

    private fun createGuiItem(material: Material, name: String, vararg lore: String): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta
        meta.displayName(Component.text(name, NamedTextColor.GOLD))
        meta.lore(lore.map { Component.text(it, NamedTextColor.GRAY) })
        // Mark this item as an Atlas GUI item using Persistent Data
        meta.persistentDataContainer.set(menuKey, PersistentDataType.BYTE, 1)
        item.itemMeta = meta
        return item
    }
}
