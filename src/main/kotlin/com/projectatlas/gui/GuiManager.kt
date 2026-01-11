package com.projectatlas.gui

import com.projectatlas.AtlasPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.NamespacedKey

class GuiManager(private val plugin: AtlasPlugin) : Listener {

    private val menuKey = NamespacedKey(plugin, "gui_menu")
    private val actionKey = NamespacedKey(plugin, "gui_action")

    // ========== MAIN MENU ==========
    fun openMainMenu(player: Player) {
        val inv = Bukkit.createInventory(null, 27, Component.text("Project Atlas Menu", NamedTextColor.DARK_BLUE))
        
        inv.setItem(10, createGuiItem(Material.PLAYER_HEAD, "Your Profile", "action:profile", "View your stats & class"))
        inv.setItem(12, createGuiItem(Material.DIAMOND_SWORD, "Class Selector", "action:class_menu", "Choose your combat role"))
        inv.setItem(14, createGuiItem(Material.BEACON, "City Management", "action:city_menu", "Manage your city"))
        inv.setItem(16, createGuiItem(Material.GOLD_INGOT, "Economy", "action:economy_menu", "Manage your finances"))

        player.openInventory(inv)
        playClickSound(player)
    }

    // ========== PROFILE MENU ==========
    fun openProfileMenu(player: Player) {
        val inv = Bukkit.createInventory(null, 27, Component.text("Your Profile", NamedTextColor.DARK_PURPLE))
        
        val profile = plugin.identityManager.getPlayer(player.uniqueId)
        val className = profile?.playerClass ?: "None"
        val cityName = profile?.cityId?.let { plugin.cityManager.getCity(it)?.name } ?: "Nomad"
        
        inv.setItem(10, createInfoItem(Material.NAME_TAG, "Name", player.name))
        inv.setItem(11, createInfoItem(Material.EMERALD, "Reputation", "${profile?.reputation ?: 0}"))
        inv.setItem(12, createInfoItem(Material.COMPASS, "Alignment", "${profile?.alignment ?: 0}"))
        inv.setItem(14, createInfoItem(Material.IRON_SWORD, "Class", className))
        inv.setItem(15, createInfoItem(Material.BELL, "City", cityName))
        inv.setItem(16, createInfoItem(Material.GOLD_INGOT, "Balance", "${profile?.balance ?: 0.0}"))
        
        // QoL: Quick action to change class
        inv.setItem(23, createGuiItem(Material.ARMOR_STAND, "Change Class", "action:class_menu", "Pick a new role"))
        inv.setItem(22, createGuiItem(Material.BARRIER, "Back", "action:main_menu", "Return to Main Menu"))
        
        player.openInventory(inv)
    }

    // ========== CLASS MENU ==========
    fun openClassMenu(player: Player) {
        val inv = Bukkit.createInventory(null, 27, Component.text("Select Class", NamedTextColor.DARK_GREEN))
        
        val profile = plugin.identityManager.getPlayer(player.uniqueId)
        val currentClass = profile?.playerClass
        
        // Show current class indicator
        inv.setItem(4, createInfoItem(Material.BOOK, "Current Class", currentClass ?: "None"))
        
        inv.setItem(10, createClassItem(Material.IRON_CHESTPLATE, "Vanguard", "action:class_vanguard", currentClass == "Vanguard", "Health: 30", "Role: Tank"))
        inv.setItem(12, createClassItem(Material.FEATHER, "Scout", "action:class_scout", currentClass == "Scout", "Speed: Fast", "Role: Explorer"))
        inv.setItem(14, createClassItem(Material.SPLASH_POTION, "Medic", "action:class_medic", currentClass == "Medic", "Regen: Passive", "Role: Healer"))
        inv.setItem(22, createGuiItem(Material.BARRIER, "Back", "action:main_menu", "Return to Main Menu"))

        player.openInventory(inv)
    }

    // ========== CITY MENU ==========
    fun openCityMenu(player: Player) {
        val inv = Bukkit.createInventory(null, 36, Component.text("City Management", NamedTextColor.DARK_AQUA))
        
        val profile = plugin.identityManager.getPlayer(player.uniqueId)
        val city = profile?.cityId?.let { plugin.cityManager.getCity(it) }
        
        if (city == null) {
            inv.setItem(13, createGuiItem(Material.CRAFTING_TABLE, "Create City", "action:city_create", "Start your own settlement"))
            inv.setItem(15, createGuiItem(Material.ENDER_PEARL, "Join a City", "action:city_join", "Accept a pending invite"))
        } else {
            val isMayor = city.mayor == player.uniqueId
            
            inv.setItem(10, createInfoItem(Material.BELL, "City", city.name))
            inv.setItem(11, createInfoItem(Material.GOLD_BLOCK, "Treasury", "${city.treasury}"))
            inv.setItem(12, createInfoItem(Material.PAPER, "Tax Rate", "${city.taxRate}%"))
            inv.setItem(13, createInfoItem(Material.PLAYER_HEAD, "Members", "${city.members.size}"))
            
            inv.setItem(19, createGuiItem(Material.GRASS_BLOCK, "Claim Chunk", "action:city_claim", "Claim this chunk for your city"))
            inv.setItem(20, createGuiItem(Material.EMERALD, "Deposit", "action:city_deposit", "Add money to treasury"))
            
            if (isMayor) {
                inv.setItem(21, createGuiItem(Material.WRITABLE_BOOK, "Set Tax Rate", "action:city_tax", "Change tax (Mayor only)"))
                inv.setItem(22, createGuiItem(Material.NAME_TAG, "Invite Player", "action:city_invite", "Invite someone (Mayor only)"))
                inv.setItem(23, createGuiItem(Material.TNT, "Kick Player", "action:city_kick", "Remove a member (Mayor only)"))
            }
            
            // Safety: Leave City is RED and requires confirmation
            inv.setItem(28, createDangerItem(Material.IRON_DOOR, "Leave City", "action:confirm_leave_city", "Leave your current city", "§cClick to confirm"))
        }
        
        inv.setItem(31, createGuiItem(Material.ARROW, "Back", "action:main_menu", "Return to Main Menu"))
        player.openInventory(inv)
    }

    // ========== CONFIRMATION MENU ==========
    fun openConfirmMenu(player: Player, title: String, confirmAction: String, cancelAction: String, warningMessage: String) {
        val inv = Bukkit.createInventory(null, 27, Component.text(title, NamedTextColor.RED))
        
        inv.setItem(4, createInfoItem(Material.BARRIER, "Warning", warningMessage))
        inv.setItem(11, createDangerItem(Material.RED_WOOL, "CONFIRM", confirmAction, "This action cannot be undone!"))
        inv.setItem(15, createGuiItem(Material.LIME_WOOL, "Cancel", cancelAction, "Go back to safety"))
        
        player.openInventory(inv)
        player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f)
    }

    // ========== ECONOMY MENU ==========
    fun openEconomyMenu(player: Player) {
        val inv = Bukkit.createInventory(null, 27, Component.text("Economy", NamedTextColor.GOLD))
        
        val profile = plugin.identityManager.getPlayer(player.uniqueId)
        
        inv.setItem(11, createInfoItem(Material.GOLD_INGOT, "Your Balance", "${profile?.balance ?: 0.0}"))
        inv.setItem(15, createGuiItem(Material.PAPER, "Send Money", "action:economy_pay", "Transfer to another player", "(Use /atlas pay <name> <amount>)"))
        
        inv.setItem(22, createGuiItem(Material.BARRIER, "Back", "action:main_menu", "Return to Main Menu"))
        
        player.openInventory(inv)
    }

    // ========== EVENT HANDLER ==========
    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val item = event.currentItem ?: return
        val meta = item.itemMeta ?: return
        
        if (!meta.persistentDataContainer.has(menuKey, PersistentDataType.BYTE)) return
        
        event.isCancelled = true
        val player = event.whoClicked as Player
        
        val action = meta.persistentDataContainer.get(actionKey, PersistentDataType.STRING) ?: return
        handleAction(player, action)
    }

    private fun handleAction(player: Player, action: String) {
        playClickSound(player)
        
        when (action) {
            // Navigation
            "action:main_menu" -> openMainMenu(player)
            "action:profile" -> openProfileMenu(player)
            "action:class_menu" -> openClassMenu(player)
            "action:city_menu" -> openCityMenu(player)
            "action:economy_menu" -> openEconomyMenu(player)
            
            // Classes
            "action:class_vanguard" -> { player.performCommand("atlas class choose Vanguard"); player.closeInventory(); playSuccessSound(player) }
            "action:class_scout" -> { player.performCommand("atlas class choose Scout"); player.closeInventory(); playSuccessSound(player) }
            "action:class_medic" -> { player.performCommand("atlas class choose Medic"); player.closeInventory(); playSuccessSound(player) }
            
            // City Actions
            "action:city_create" -> {
                // Safety: If already in a city, warn them
                val profile = plugin.identityManager.getPlayer(player.uniqueId)
                if (profile?.cityId != null) {
                    openConfirmMenu(player, "Already in a City!", "action:city_create_force", "action:city_menu", "You must leave your current city first!")
                } else {
                    player.closeInventory()
                    player.sendMessage(Component.text("Use /atlas city create <name> to create your city.", NamedTextColor.YELLOW))
                }
            }
            "action:city_create_force" -> {
                player.closeInventory()
                player.sendMessage(Component.text("Leave your current city first with /atlas city leave", NamedTextColor.RED))
            }
            "action:city_join" -> { 
                player.performCommand("atlas city join")
                player.closeInventory()
                playSuccessSound(player)
            }
            "action:city_claim" -> { 
                player.performCommand("atlas city claim")
                openCityMenu(player)
                playSuccessSound(player)
            }
            
            // Confirmation for Leave City
            "action:confirm_leave_city" -> {
                openConfirmMenu(player, "Leave City?", "action:city_leave_confirmed", "action:city_menu", "You will lose access to city perks!")
            }
            "action:city_leave_confirmed" -> { 
                player.performCommand("atlas city leave")
                player.closeInventory()
            }
            
            "action:city_deposit" -> {
                player.closeInventory()
                player.sendMessage(Component.text("Use /atlas city deposit <amount> to add funds.", NamedTextColor.YELLOW))
            }
            "action:city_tax" -> {
                player.closeInventory()
                player.sendMessage(Component.text("Use /atlas city tax <percentage> to set tax rate.", NamedTextColor.YELLOW))
            }
            "action:city_invite" -> {
                player.closeInventory()
                player.sendMessage(Component.text("Use /atlas city invite <player> to invite someone.", NamedTextColor.YELLOW))
            }
            "action:city_kick" -> {
                player.closeInventory()
                player.sendMessage(Component.text("Use /atlas city kick <player> to remove a member.", NamedTextColor.YELLOW))
            }
            
            // Economy
            "action:economy_pay" -> {
                player.closeInventory()
                player.sendMessage(Component.text("Use /atlas pay <player> <amount> to send money.", NamedTextColor.YELLOW))
            }
        }
    }

    // ========== ITEM BUILDERS ==========
    private fun createGuiItem(material: Material, name: String, action: String, vararg lore: String): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta
        meta.displayName(Component.text(name, NamedTextColor.GOLD))
        meta.lore(lore.map { Component.text(it, NamedTextColor.GRAY) })
        meta.persistentDataContainer.set(menuKey, PersistentDataType.BYTE, 1)
        meta.persistentDataContainer.set(actionKey, PersistentDataType.STRING, action)
        item.itemMeta = meta
        return item
    }
    
    // Dangerous action items (red text)
    private fun createDangerItem(material: Material, name: String, action: String, vararg lore: String): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta
        meta.displayName(Component.text(name, NamedTextColor.RED).decorate(TextDecoration.BOLD))
        meta.lore(lore.map { Component.text(it, NamedTextColor.GRAY) })
        meta.persistentDataContainer.set(menuKey, PersistentDataType.BYTE, 1)
        meta.persistentDataContainer.set(actionKey, PersistentDataType.STRING, action)
        item.itemMeta = meta
        return item
    }
    
    // Class items with "selected" indicator
    private fun createClassItem(material: Material, name: String, action: String, isSelected: Boolean, vararg lore: String): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta
        val color = if (isSelected) NamedTextColor.GREEN else NamedTextColor.GOLD
        val prefix = if (isSelected) "✓ " else ""
        meta.displayName(Component.text("$prefix$name", color))
        val fullLore = lore.map { Component.text(it, NamedTextColor.GRAY) }.toMutableList()
        if (isSelected) fullLore.add(Component.text("(Currently Selected)", NamedTextColor.GREEN))
        meta.lore(fullLore)
        meta.persistentDataContainer.set(menuKey, PersistentDataType.BYTE, 1)
        meta.persistentDataContainer.set(actionKey, PersistentDataType.STRING, action)
        item.itemMeta = meta
        return item
    }
    
    private fun createInfoItem(material: Material, label: String, value: String): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta
        meta.displayName(Component.text(label, NamedTextColor.AQUA))
        meta.lore(listOf(Component.text(value, NamedTextColor.WHITE)))
        item.itemMeta = meta
        return item
    }
    
    // ========== SOUND EFFECTS ==========
    private fun playClickSound(player: Player) {
        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.5f, 1.0f)
    }
    
    private fun playSuccessSound(player: Player) {
        player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f)
    }
}
