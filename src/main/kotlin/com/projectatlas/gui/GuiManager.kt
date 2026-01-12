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
        val inv = Bukkit.createInventory(null, 54, Component.text("✦ Project Atlas Menu ✦", NamedTextColor.DARK_BLUE))
        
        // Row 1: Core Features
        inv.setItem(10, createGuiItem(Material.PLAYER_HEAD, "Your Profile", "action:profile", "View your stats & class"))
        inv.setItem(12, createGuiItem(Material.DIAMOND_SWORD, "Class Selector", "action:class_menu", "Choose your combat role"))
        inv.setItem(14, createGuiItem(Material.BEACON, "City Management", "action:city_menu", "Manage your city"))
        inv.setItem(16, createGuiItem(Material.GOLD_INGOT, "Economy", "action:economy_menu", "Manage your finances"))
        
        // Row 2: Activities
        inv.setItem(19, createGuiItem(Material.WRITABLE_BOOK, "Quest Board", "action:quest_menu", "Take on challenges for gold"))
        inv.setItem(21, createGuiItem(Material.END_PORTAL_FRAME, "Dungeons", "action:dungeon_menu", "Enter instanced challenges"))
        inv.setItem(23, createGuiItem(Material.SKELETON_SKULL, "Bounties", "action:bounty_menu", "Hunt wanted players"))
        inv.setItem(25, createGuiItem(Material.DRAGON_HEAD, "World Bosses", "action:boss_info", "View boss event info"))
        
        // Row 3: Social & Marketplace
        inv.setItem(28, createGuiItem(Material.TOTEM_OF_UNDYING, "Party", "action:party_menu", "Manage your party"))
        inv.setItem(30, createGuiItem(Material.CRAFTING_TABLE, "Blueprint Shop", "action:blueprint_menu", "Buy & sell building designs"))
        inv.setItem(32, createGuiItem(Material.ENDER_EYE, "Relics", "action:relic_info", "Ancient artifacts info"))
        inv.setItem(34, createGuiItem(Material.NETHER_STAR, "Achievements", "action:achievement_menu", "Track your progress"))
        
        // Bottom row: Help
        inv.setItem(49, createGuiItem(Material.BOOK, "Help", "action:help", "View all commands"))

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
        val inv = Bukkit.createInventory(null, 36, Component.text("Select Class", NamedTextColor.DARK_GREEN))
        
        val profile = plugin.identityManager.getPlayer(player.uniqueId)
        val currentClass = profile?.playerClass
        
        // Show current class indicator
        inv.setItem(4, createInfoItem(Material.BOOK, "Current Class", currentClass ?: "None"))
        
        // Show cost/cooldown info
        val canChange = plugin.classManager.canChangeClass(player)
        val costInfo = when (canChange) {
            is com.projectatlas.classes.ClassManager.ClassChangeResult.Success -> {
                if (currentClass == null) "First class is FREE!" else "Cost: ${plugin.classManager.getClassChangeCost()}g"
            }
            is com.projectatlas.classes.ClassManager.ClassChangeResult.OnCooldown -> {
                "§cCooldown: ${canChange.hours}h ${canChange.minutes}m"
            }
            is com.projectatlas.classes.ClassManager.ClassChangeResult.InsufficientFunds -> {
                "§cNeed ${canChange.required}g (have ${canChange.current})"
            }
            else -> "Error"
        }
        inv.setItem(13, createInfoItem(Material.CLOCK, "Change Status", costInfo))
        
        inv.setItem(19, createClassItem(Material.IRON_CHESTPLATE, "Vanguard", "action:confirm_class_vanguard", currentClass == "Vanguard", "Health: 30", "Role: Tank"))
        inv.setItem(21, createClassItem(Material.FEATHER, "Scout", "action:confirm_class_scout", currentClass == "Scout", "Speed: Fast", "Role: Explorer"))
        inv.setItem(23, createClassItem(Material.SPLASH_POTION, "Medic", "action:confirm_class_medic", currentClass == "Medic", "Regen: Passive", "Role: Healer"))
        inv.setItem(31, createGuiItem(Material.BARRIER, "Back", "action:main_menu", "Return to Main Menu"))

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
                inv.setItem(24, createGuiItem(Material.ANVIL, "Infrastructure", "action:city_infra", "Upgrade defenses (Mayor only)"))
                inv.setItem(25, createGuiItem(Material.BRICKS, "Structure Shop", "action:structure_shop", "Buy building blueprints"))
            }
            
            // Core Health indicator
            inv.setItem(16, createInfoItem(Material.HEART_OF_THE_SEA, "Core Health", "${city.infrastructure.coreHealth}/100 HP"))
            
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

    // ========== INFRASTRUCTURE MENU ==========
    fun openInfrastructureMenu(player: Player) {
        val inv = Bukkit.createInventory(null, 45, Component.text("City Infrastructure", NamedTextColor.DARK_GRAY))
        
        val profile = plugin.identityManager.getPlayer(player.uniqueId)
        val city = profile?.cityId?.let { plugin.cityManager.getCity(it) } ?: return
        val infra = city.infrastructure
        
        // Treasury display
        inv.setItem(4, createInfoItem(Material.GOLD_BLOCK, "Treasury", "${city.treasury}g"))
        
        // Walls (damage reduction)
        val wallCost = infra.getWallUpgradeCost()
        inv.setItem(19, createGuiItem(Material.STONE_BRICKS, "Walls Lv.${infra.wallLevel}", "action:upgrade_wall",
            "Damage reduction: ${(infra.getWallDamageReduction() * 100).toInt()}%",
            if (wallCost != null) "Upgrade: ${wallCost}g" else "MAX LEVEL"))
        
        // Turrets (auto-attack during siege)
        inv.setItem(21, createGuiItem(Material.DISPENSER, "Turrets: ${infra.turretCount}/4", "action:upgrade_turret",
            "Auto-attack invaders during siege",
            if (infra.canAddTurret()) "Add: ${com.projectatlas.city.CityInfrastructure.TURRET_COST}g" else "MAX TURRETS"))
        
        // Generator (passive income)
        val genCost = infra.getGeneratorUpgradeCost()
        inv.setItem(23, createGuiItem(Material.REDSTONE_BLOCK, "Generator Lv.${infra.generatorLevel}", "action:upgrade_generator",
            "Passive income: ${infra.getPassiveIncome()}g/cycle",
            if (genCost != null) "Upgrade: ${genCost}g" else "MAX LEVEL"))
        
        // Barracks (defender NPCs)
        val barracksCost = infra.getBarracksUpgradeCost()
        inv.setItem(25, createGuiItem(Material.IRON_CHESTPLATE, "Barracks Lv.${infra.barracksLevel}", "action:upgrade_barracks",
            "Defenders during siege: ${infra.getDefenderCount()}",
            if (barracksCost != null) "Upgrade: ${barracksCost}g" else "MAX LEVEL"))
        
        // Core repair
        if (infra.coreHealth < 100) {
            val repairCost = (100 - infra.coreHealth) * 10 // 10g per HP
            inv.setItem(31, createGuiItem(Material.NETHER_STAR, "Repair Core", "action:repair_core",
                "Current: ${infra.coreHealth}/100 HP",
                "Cost: ${repairCost}g"))
        } else {
            inv.setItem(31, createInfoItem(Material.NETHER_STAR, "Core Status", "100/100 HP (Healthy)"))
        }
        
        inv.setItem(40, createGuiItem(Material.ARROW, "Back", "action:city_menu", "Return to City Menu"))
        player.openInventory(inv)
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

    // ========== QUEST MENU ==========
    fun openQuestMenu(player: Player) {
        val inv = Bukkit.createInventory(null, 36, Component.text("Quest Board", NamedTextColor.DARK_RED))
        
        // Check active quest
        val activeQuest = plugin.questManager.getActiveQuest(player)
        if (activeQuest != null) {
            inv.setItem(4, createInfoItem(Material.MAP, "Active Quest", activeQuest.quest.name))
            inv.setItem(13, createInfoItem(Material.EXPERIENCE_BOTTLE, "Progress", "${activeQuest.progress}/${activeQuest.getTargetCount()}"))
            if (activeQuest.getRemainingSeconds() != null) {
                inv.setItem(22, createInfoItem(Material.CLOCK, "Time Left", "${activeQuest.getRemainingSeconds()}s"))
            }
            inv.setItem(31, createDangerItem(Material.BARRIER, "Abandon Quest", "action:quest_abandon", "Give up on current quest"))
        } else {
            // Difficulty selection
            inv.setItem(10, createGuiItem(Material.LEATHER_CHESTPLATE, "Easy Quest", "action:quest_easy", "Reward: 100g", "Kill 5 mobs"))
            inv.setItem(12, createGuiItem(Material.CHAINMAIL_CHESTPLATE, "Medium Quest", "action:quest_medium", "Reward: 300g", "Kill 15 mobs", "Time: 5 min"))
            inv.setItem(14, createGuiItem(Material.IRON_CHESTPLATE, "Hard Quest", "action:quest_hard", "Reward: 600g", "Kill 20 mobs", "Time: 3 min", "Boss Bar tracking"))
            inv.setItem(16, createDangerItem(Material.NETHERITE_CHESTPLATE, "NIGHTMARE", "action:quest_nightmare", "Reward: 1500g", "Kill 50 mobs", "Time: 5 min", "For the brave only!"))
        }
        
        inv.setItem(27, createGuiItem(Material.ARROW, "Back", "action:main_menu", "Return to Main Menu"))
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
            
            // Classes (with confirmation flow)
            "action:confirm_class_vanguard" -> handleClassChange(player, "Vanguard")
            "action:confirm_class_scout" -> handleClassChange(player, "Scout")
            "action:confirm_class_medic" -> handleClassChange(player, "Medic")
            "action:class_vanguard" -> { plugin.classManager.setClass(player, "Vanguard"); player.closeInventory(); playSuccessSound(player) }
            "action:class_scout" -> { plugin.classManager.setClass(player, "Scout"); player.closeInventory(); playSuccessSound(player) }
            "action:class_medic" -> { plugin.classManager.setClass(player, "Medic"); player.closeInventory(); playSuccessSound(player) }
            
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
            "action:city_infra" -> openInfrastructureMenu(player)
            "action:structure_shop" -> openStructureShop(player)
            "action:buy_blueprint_barracks" -> handleBuyBlueprint(player, "Barracks", 1000.0)
            "action:buy_blueprint_nexus" -> handleBuyBlueprint(player, "Nexus", 5000.0)
            "action:buy_blueprint_market" -> handleBuyBlueprint(player, "Market", 500.0)
            "action:buy_blueprint_camp" -> handleBuyBlueprint(player, "Quest Camp", 500.0)
            "action:buy_blueprint_turret" -> handleBuyBlueprint(player, "Turret", 800.0)
            "action:buy_blueprint_generator" -> handleBuyBlueprint(player, "Generator", 2000.0)
            
            // Infrastructure Upgrades
            "action:upgrade_wall" -> handleUpgrade(player, "wall")
            "action:upgrade_turret" -> handleUpgrade(player, "turret")
            "action:upgrade_generator" -> handleUpgrade(player, "generator")
            "action:upgrade_barracks" -> handleUpgrade(player, "barracks")
            "action:repair_core" -> handleUpgrade(player, "core")
            
            // Economy
            "action:economy_pay" -> {
                player.closeInventory()
                player.sendMessage(Component.text("Use /atlas pay <player> <amount> to send money.", NamedTextColor.YELLOW))
            }
            
            // Quests
            "action:quest_menu" -> openQuestMenu(player)
            "action:quest_easy" -> {
                val quest = plugin.questManager.getQuestByDifficulty(com.projectatlas.quests.Difficulty.EASY)
                if (quest != null) {
                    plugin.questManager.startQuest(player, quest)
                    player.closeInventory()
                }
            }
            "action:quest_medium" -> {
                val quest = plugin.questManager.getQuestByDifficulty(com.projectatlas.quests.Difficulty.MEDIUM)
                if (quest != null) {
                    plugin.questManager.startQuest(player, quest)
                    player.closeInventory()
                }
            }
            "action:quest_hard" -> {
                val quest = plugin.questManager.getQuestByDifficulty(com.projectatlas.quests.Difficulty.HARD)
                if (quest != null) {
                    plugin.questManager.startQuest(player, quest)
                    player.closeInventory()
                }
            }
            "action:quest_nightmare" -> {
                val quest = plugin.questManager.getQuestByDifficulty(com.projectatlas.quests.Difficulty.NIGHTMARE)
                if (quest != null) {
                    plugin.questManager.startQuest(player, quest)
                    player.closeInventory()
                }
            }
            "action:quest_abandon" -> {
                plugin.questManager.abandonQuest(player)
                player.closeInventory()
            }
            
            // New Features
            "action:dungeon_menu" -> {
                player.closeInventory()
                player.performCommand("atlas dungeon")
            }
            "action:party_menu" -> {
                player.closeInventory()
                player.performCommand("atlas party")
            }
            "action:bounty_menu" -> {
                player.closeInventory()
                player.performCommand("atlas bounty")
            }
            "action:blueprint_menu" -> {
                player.closeInventory()
                player.performCommand("atlas bp list")
            }
            "action:relic_info" -> {
                player.closeInventory()
                player.sendMessage(Component.text("═══ RELICS ═══", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
                player.sendMessage(Component.text("Ancient artifacts spawn randomly in the world!", NamedTextColor.GRAY))
                player.sendMessage(Component.text("Each relic has a unique right-click ability.", NamedTextColor.GRAY))
                player.sendMessage(Component.text("Listen for announcements when they appear!", NamedTextColor.YELLOW))
            }
            "action:achievement_menu" -> {
                player.closeInventory()
                val (earned, total) = plugin.achievementManager.getProgress(player)
                player.sendMessage(Component.text("═══ ACHIEVEMENTS ($earned/$total) ═══", NamedTextColor.GOLD, TextDecoration.BOLD))
                plugin.achievementManager.getAchievementsForPlayer(player).forEach { (ach, unlocked) ->
                    val status = if (unlocked) "§a✓" else "§c✗"
                    player.sendMessage(Component.text("  $status ${ach.name}: ${ach.description} (${ach.reward}g)"))
                }
            }
            "action:boss_info" -> {
                player.closeInventory()
                player.sendMessage(Component.text("═══ WORLD BOSSES ═══", NamedTextColor.DARK_RED, TextDecoration.BOLD))
                if (plugin.worldBossManager.hasActiveBoss()) {
                    player.sendMessage(Component.text("⚠ A World Boss is currently active!", NamedTextColor.RED))
                } else {
                    player.sendMessage(Component.text("No boss active. They spawn periodically!", NamedTextColor.GRAY))
                }
                player.sendMessage(Component.text("Contribute damage to earn gold & XP!", NamedTextColor.YELLOW))
            }
            "action:help" -> {
                player.closeInventory()
                player.performCommand("atlas help")
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
    
    // ========== CLASS CHANGE HELPER ==========
    private fun handleClassChange(player: Player, className: String) {
        val profile = plugin.identityManager.getPlayer(player.uniqueId)
        val isFirstClass = profile?.playerClass == null
        
        if (isFirstClass) {
            // First class is free - just apply it
            plugin.classManager.setClass(player, className)
            player.closeInventory()
            playSuccessSound(player)
        } else {
            // Show confirmation with cost
            val cost = plugin.classManager.getClassChangeCost()
            openConfirmMenu(player, "Change to $className?", "action:class_$className".lowercase(), "action:class_menu", "This will cost ${cost}g")
        }
    }
    
    // ========== INFRASTRUCTURE UPGRADE HELPER ==========
    private fun handleUpgrade(player: Player, type: String) {
        val profile = plugin.identityManager.getPlayer(player.uniqueId) ?: return
        val city = profile.cityId?.let { plugin.cityManager.getCity(it) } ?: return
        val infra = city.infrastructure
        
        var cost: Int? = null
        var canUpgrade = false
        
        when (type) {
            "wall" -> {
                cost = infra.getWallUpgradeCost()
                canUpgrade = cost != null && city.treasury >= cost
                if (canUpgrade) { city.treasury -= cost!!; infra.wallLevel++ }
            }
            "turret" -> {
                cost = com.projectatlas.city.CityInfrastructure.TURRET_COST
                canUpgrade = infra.canAddTurret() && city.treasury >= cost
                if (canUpgrade) { city.treasury -= cost; infra.turretCount++ }
            }
            "generator" -> {
                cost = infra.getGeneratorUpgradeCost()
                canUpgrade = cost != null && city.treasury >= cost
                if (canUpgrade) { city.treasury -= cost!!; infra.generatorLevel++ }
            }
            "barracks" -> {
                cost = infra.getBarracksUpgradeCost()
                canUpgrade = cost != null && city.treasury >= cost
                if (canUpgrade) { city.treasury -= cost!!; infra.barracksLevel++ }
            }
            "core" -> {
                cost = (100 - infra.coreHealth) * 10
                canUpgrade = infra.coreHealth < 100 && city.treasury >= cost
                if (canUpgrade) { city.treasury -= cost; infra.coreHealth = 100 }
            }
        }
        
        if (canUpgrade) {
            plugin.cityManager.saveCity(city)
            playSuccessSound(player)
            player.sendMessage(Component.text("Upgrade complete! (-${cost}g)", NamedTextColor.GREEN))
        } else {
            player.sendMessage(Component.text("Cannot upgrade - check funds or max level.", NamedTextColor.RED))
        }
        openInfrastructureMenu(player)
    }


    // ========== STRUCTURE SHOP ==========
    fun openStructureShop(player: Player) {
        val inv = Bukkit.createInventory(null, 27, Component.text("Structure Blueprints", NamedTextColor.BLUE))
        
        inv.setItem(10, createGuiItem(Material.PAPER, "Barracks Blueprint", "action:buy_blueprint_barracks", 
            "Cost: 1000g", "Spawns Defenders to protect city", "Requires: 7x7 flat space"))
            
        inv.setItem(12, createGuiItem(Material.PAPER, "Nexus Blueprint", "action:buy_blueprint_nexus", 
            "Cost: 5000g", "Grants city-wide buffs", "Requires: 3x3 flat space"))
            
        inv.setItem(14, createGuiItem(Material.PAPER, "Market Blueprint", "action:buy_blueprint_market", 
            "Cost: 500g", "Spawns a Merchant", "Requires: 5x5 flat space"))
            
        inv.setItem(16, createGuiItem(Material.PAPER, "Quest Camp Blueprint", "action:buy_blueprint_camp", 
            "Cost: 500g", "Spawns a Quest Giver", "Requires: 5x5 flat space"))
            
            

        inv.setItem(18, createGuiItem(Material.PAPER, "Turret Blueprint", "action:buy_blueprint_turret", 
            "Cost: 800g", "Defensive tower", "Requires: 3x3 flat space"))

        inv.setItem(20, createGuiItem(Material.PAPER, "Generator Blueprint", "action:buy_blueprint_generator", 
            "Cost: 2000g", "Industrial Unit", "Requires: 3x3 flat space"))
            
        inv.setItem(22, createGuiItem(Material.ARROW, "Back", "action:city_menu", "Return"))
        player.openInventory(inv)
    }

    private fun handleBuyBlueprint(player: Player, name: String, cost: Double) {
        val profile = plugin.identityManager.getPlayer(player.uniqueId) ?: return
        if (profile.balance >= cost) {
            profile.balance -= cost
            plugin.identityManager.saveProfile(player.uniqueId)
            
            val blueprint = ItemStack(Material.PAPER)
            val meta = blueprint.itemMeta
            meta.displayName(Component.text("Blueprint: $name", NamedTextColor.AQUA))
            meta.lore(listOf(Component.text("Right-click ground to build", NamedTextColor.GRAY)))
            blueprint.itemMeta = meta
            
            player.inventory.addItem(blueprint)
            playSuccessSound(player)
            player.sendMessage(Component.text("Purchased $name Blueprint!", NamedTextColor.GREEN))
        } else {
            player.sendMessage(Component.text("Not enough gold!", NamedTextColor.RED))
            player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f)
        }
    }
}
