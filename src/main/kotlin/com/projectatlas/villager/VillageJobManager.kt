package com.projectatlas.villager

import com.projectatlas.AtlasPlugin
import com.projectatlas.city.City
import com.projectatlas.city.CityInfrastructure
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.entity.Villager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.UUID

class VillageJobManager(private val plugin: AtlasPlugin) : Listener {

    private val jobKey = NamespacedKey(plugin, "villager_job")
    private val guiActionKey = NamespacedKey(plugin, "gui_action")
    // Use a simpler mechanism for tracking villager selection: Metadata or temporary map
    private val activeInteractions = java.util.concurrent.ConcurrentHashMap<UUID, UUID>() // Player -> Villager

    enum class Job(
        val displayName: String,
        val icon: Material,
        val description: String,
        val requiredInfra: (CityInfrastructure) -> Int,
        val infraName: String
    ) {
        NONE("Unemployed", Material.BARRIER, "No assigned role", { 1 }, "None"),
        ARMORER("City Armorer", Material.ANVIL, "Repairs armor for Gold", { it.armoryLevel }, "Armory"),
        WEAPONSMITH("Weaponsmith", Material.GRINDSTONE, "Repairs weapons for Gold", { it.forgeLevel }, "Forge"),
        CLERIC("Healer", Material.POTION, "Heals players for Gold", { it.clinicLevel }, "Clinic"),
        FARMER("Farmer", Material.HAY_BLOCK, "Sells bulk food", { it.marketLevel }, "Market")
    }

    @EventHandler
    fun onInteract(event: PlayerInteractEntityEvent) {
        val villager = event.rightClicked as? Villager ?: return
        val player = event.player
        
        val city = plugin.cityManager.getCityAt(villager.location.chunk)
        
        // ASSIGNMENT (Shift + Click by Mayor)
        if (player.isSneaking && city != null && city.mayor == player.uniqueId) {
            event.isCancelled = true
            activeInteractions[player.uniqueId] = villager.uniqueId
            openAssignmentMenu(player, villager, city)
            return
        }
        
        // JOB INTERACTION
        if (villager.persistentDataContainer.has(jobKey, PersistentDataType.STRING)) {
            val jobName = villager.persistentDataContainer.get(jobKey, PersistentDataType.STRING)
            val job = Job.entries.find { it.name == jobName } ?: return
            
            if (job != Job.NONE) {
                event.isCancelled = true
                activeInteractions[player.uniqueId] = villager.uniqueId
                performJobInteraction(player, villager, job)
            }
        }
    }

    private fun openAssignmentMenu(player: Player, villager: Villager, city: City) {
        val inv = Bukkit.createInventory(null, 27, Component.text("Assign Villager Job", NamedTextColor.DARK_AQUA))
        
        val currentJobName = villager.persistentDataContainer.get(jobKey, PersistentDataType.STRING) ?: "NONE"
        
        Job.entries.forEachIndexed { index, job ->
            if (job == Job.NONE && index != 0) return@forEachIndexed // Skip duplicate logical check
            
            val infraLevel = job.requiredInfra(city.infrastructure)
            val isUnlocked = infraLevel > 0
            val isCurrent = job.name == currentJobName
            
            val item = ItemStack(if (isUnlocked) job.icon else Material.GRAY_STAINED_GLASS_PANE)
            item.editMeta { meta ->
                val color = if (isCurrent) NamedTextColor.GREEN else if (isUnlocked) NamedTextColor.YELLOW else NamedTextColor.RED
                meta.displayName(Component.text(job.displayName, color, TextDecoration.BOLD))
                
                val lore = mutableListOf<Component>()
                lore.add(Component.text(job.description, NamedTextColor.GRAY))
                lore.add(Component.empty())
                
                if (isCurrent) {
                    lore.add(Component.text("✓ CURRENT JOB", NamedTextColor.GREEN))
                } else if (!isUnlocked) {
                    lore.add(Component.text("✗ LOCKED", NamedTextColor.RED))
                    lore.add(Component.text("Requires: ${job.infraName} Level 1", NamedTextColor.RED))
                } else {
                    lore.add(Component.text("▶ Click to Assign", NamedTextColor.YELLOW))
                }
                meta.lore(lore)
                
                meta.persistentDataContainer.set(guiActionKey, PersistentDataType.STRING, "assign_job:${job.name}")
            }
            
            inv.setItem(index + 10, item) // Center row
        }
        
        player.openInventory(inv)
    }

    private fun performJobInteraction(player: Player, villager: Villager, job: Job) {
        when (job) {
            Job.ARMORER, Job.WEAPONSMITH -> openRepairMenu(player, job)
            Job.CLERIC -> openClericMenu(player)
            Job.FARMER -> openFarmerMenu(player)
            else -> {}
        }
    }
    
    // ════════════════════════════════════════════════════════
    // MENUS
    // ════════════════════════════════════════════════════════

    private fun openRepairMenu(player: Player, job: Job) {
        val title = if (job == Job.ARMORER) "Armorer: Repair Items" else "Weaponsmith: Repair Items"
        val inv = Bukkit.createInventory(null, 27, Component.text(title, NamedTextColor.DARK_GRAY))
        
        // Fill glass
        val glass = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
        glass.editMeta { it.displayName(Component.empty()) }
        for (i in 0 until 27) inv.setItem(i, glass)
        
        // Center Slot: Input
        inv.setItem(13, null) // Empty for item
        
        // Repair Button (Right)
        val anvil = ItemStack(Material.ANVIL)
        anvil.editMeta { 
            it.displayName(Component.text("Repair Item", NamedTextColor.GREEN))
            it.lore(listOf(Component.text("Place item in center", NamedTextColor.GRAY)))
            it.persistentDataContainer.set(guiActionKey, PersistentDataType.STRING, "do_repair")
        }
        inv.setItem(15, anvil)
        
        player.openInventory(inv)
    }

    private fun openClericMenu(player: Player) {
        val inv = Bukkit.createInventory(null, 27, Component.text("Cleric Services", NamedTextColor.LIGHT_PURPLE))
        
        // Heal
        val heal = ItemStack(Material.POTION)
        heal.editMeta {
            it.displayName(Component.text("Restore Health", NamedTextColor.RED))
            it.lore(listOf(Component.text("Cost: 50g", NamedTextColor.YELLOW)))
            it.persistentDataContainer.set(guiActionKey, PersistentDataType.STRING, "cleric_heal")
        }
        inv.setItem(11, heal)
        
        // Cure
        val cure = ItemStack(Material.MILK_BUCKET)
        cure.editMeta {
            it.displayName(Component.text("Cure Poison/Wither", NamedTextColor.WHITE))
            it.lore(listOf(Component.text("Cost: 25g", NamedTextColor.YELLOW)))
            it.persistentDataContainer.set(guiActionKey, PersistentDataType.STRING, "cleric_cure")
        }
        inv.setItem(13, cure)
        
        // Buff
        val buff = ItemStack(Material.GLOWSTONE_DUST)
        buff.editMeta {
            it.displayName(Component.text("Blessing (Regen II)", NamedTextColor.GOLD))
            it.lore(listOf(Component.text("Cost: 100g", NamedTextColor.YELLOW), Component.text("Duration: 5m", NamedTextColor.GRAY)))
            it.persistentDataContainer.set(guiActionKey, PersistentDataType.STRING, "cleric_buff")
        }
        inv.setItem(15, buff)
        
        player.openInventory(inv)
    }
    
    private fun openFarmerMenu(player: Player) {
        val inv = Bukkit.createInventory(null, 27, Component.text("Farmer Market", NamedTextColor.GREEN))
        
        // Bread
        val bread = ItemStack(Material.BREAD, 16)
        bread.editMeta {
            it.displayName(Component.text("Buy Bread x16", NamedTextColor.YELLOW))
            it.lore(listOf(Component.text("Cost: 50g", NamedTextColor.GOLD)))
            it.persistentDataContainer.set(guiActionKey, PersistentDataType.STRING, "buy_food_bread")
        }
        inv.setItem(11, bread)
        
        // Steak
        val steak = ItemStack(Material.COOKED_BEEF, 16)
        steak.editMeta {
            it.displayName(Component.text("Buy Steak x16", NamedTextColor.YELLOW))
            it.lore(listOf(Component.text("Cost: 25g", NamedTextColor.GOLD)))
            it.persistentDataContainer.set(guiActionKey, PersistentDataType.STRING, "buy_food_steak")
        }
        inv.setItem(13, steak)
        
        // Golden Carrots
        val carrots = ItemStack(Material.GOLDEN_CARROT, 8)
        carrots.editMeta {
            it.displayName(Component.text("Buy Golden Carrots x8", NamedTextColor.YELLOW))
            it.lore(listOf(Component.text("Cost: 50g", NamedTextColor.GOLD)))
            it.persistentDataContainer.set(guiActionKey, PersistentDataType.STRING, "buy_food_gcarrot")
        }
        inv.setItem(15, carrots)
        
        player.openInventory(inv)
    }
    
    // ════════════════════════════════════════════════════════
    // CLICK HANDLER
    // ════════════════════════════════════════════════════════

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val view = event.view
        val title = view.title()
        val player = event.whoClicked as? Player ?: return
        val clickedItem = event.currentItem ?: return
        
        // Check actions
        val action = clickedItem.itemMeta?.persistentDataContainer?.get(guiActionKey, PersistentDataType.STRING)
        
        if (action != null) {
            event.isCancelled = true
            handleAction(player, action, event)
        } else if (Component.text("Repair Items", NamedTextColor.DARK_GRAY) == title /* Fuzzy match title? */ 
                   || view.title.contains("Repair Items")) { // Simplified title check
             // Allow moving items into slot 13
             if (event.slot != 13 && event.clickedInventory == event.view.topInventory) {
                 event.isCancelled = true
             }
             // Slot 13 is free
             // Bottom inventory is free
        }
    }
    
    private fun handleAction(player: Player, action: String, event: InventoryClickEvent) {
        when {
            action.startsWith("assign_job:") -> {
                val jobName = action.substringAfter(":")
                val job = Job.entries.find { it.name == jobName } ?: return
                
                val villagerId = activeInteractions[player.uniqueId] ?: return
                val villager = plugin.server.getEntity(villagerId) as? Villager
                
                if (villager != null) {
                    villager.persistentDataContainer.set(jobKey, PersistentDataType.STRING, job.name)
                    villager.customName(Component.text(job.displayName, NamedTextColor.AQUA))
                    villager.profession = when(job) {
                        Job.ARMORER -> Villager.Profession.ARMORER
                        Job.WEAPONSMITH -> Villager.Profession.WEAPONSMITH
                        Job.CLERIC -> Villager.Profession.CLERIC
                        Job.FARMER -> Villager.Profession.FARMER
                        else -> Villager.Profession.NITWIT
                    }
                    player.sendMessage(Component.text("Assigned job: ${job.displayName}", NamedTextColor.GREEN))
                    player.playSound(player.location, Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f)
                    player.closeInventory()
                }
            }
            action == "do_repair" -> {
                val inv = event.view.topInventory
                val item = inv.getItem(13)
                if (item == null || item.type == Material.AIR) {
                    player.sendMessage(Component.text("Place an item to repair!", NamedTextColor.RED))
                    return
                }
                
                // Check if repairable
                val meta = item.itemMeta as? org.bukkit.inventory.meta.Damageable
                if (meta == null || !meta.hasDamage()) {
                    player.sendMessage(Component.text("This item is already pristine.", NamedTextColor.GREEN))
                    return
                }
                
                // Calculate Cost (1g per 10 durability?)
                val damage = meta.damage
                val cost = (damage / 10).coerceAtLeast(5)
                
                if (plugin.economyManager.withdraw(player.uniqueId, cost.toDouble())) {
                    meta.damage = 0
                    item.itemMeta = meta
                    player.playSound(player.location, Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f)
                    player.sendMessage(Component.text("Repaired for ${cost}g!", NamedTextColor.GREEN))
                } else {
                    player.sendMessage(Component.text("Repair costs ${cost}g.", NamedTextColor.RED))
                }
            }
            action == "cleric_heal" -> {
                if (plugin.economyManager.withdraw(player.uniqueId, 50.0)) {
                    player.health = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH)!!.value
                    player.foodLevel = 20
                    player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f)
                    player.sendMessage(Component.text("Restored!", NamedTextColor.LIGHT_PURPLE))
                }
            }
            action == "cleric_cure" -> {
                if (plugin.economyManager.withdraw(player.uniqueId, 25.0)) {
                    player.activePotionEffects.forEach { player.removePotionEffect(it.type) }
                    player.playSound(player.location, Sound.ITEM_HONEY_BOTTLE_DRINK, 1.0f, 1.0f)
                    player.sendMessage(Component.text("Cured!", NamedTextColor.GREEN))
                }
            }
            action.startsWith("buy_food_") -> {
                val type = action.substringAfter("buy_food_")
                val (mat, cost, amount) = when(type) {
                    "bread" -> Triple(Material.BREAD, 50.0, 16)
                    "steak" -> Triple(Material.COOKED_BEEF, 25.0, 16)
                    "gcarrot" -> Triple(Material.GOLDEN_CARROT, 50.0, 8)
                    else -> return
                }
                
                if (plugin.economyManager.withdraw(player.uniqueId, cost)) {
                    player.inventory.addItem(ItemStack(mat, amount))
                    player.playSound(player.location, Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f)
                }
            }
        }
    }
    
    @EventHandler
    fun onClose(event: InventoryCloseEvent) {
        val view = event.view
        if (view.title.contains("Repair Items")) {
            val item = view.topInventory.getItem(13)
            if (item != null) {
                event.player.inventory.addItem(item) // Return item
            }
        }
    }
}
