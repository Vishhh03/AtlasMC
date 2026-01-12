package com.projectatlas

import com.projectatlas.city.CityManager
import com.projectatlas.economy.EconomyManager
import com.projectatlas.events.EventManager
import com.projectatlas.identity.IdentityManager
import org.bukkit.plugin.java.JavaPlugin

import com.projectatlas.config.ConfigManager

import com.projectatlas.classes.ClassManager

import com.projectatlas.gui.GuiManager
import com.projectatlas.achievements.AchievementManager
import com.projectatlas.npc.NPCManager
import com.projectatlas.quests.QuestManager
import com.projectatlas.schematic.SchematicManager
import com.projectatlas.siege.SiegeManager
import com.projectatlas.dialogue.DialogueManager
import com.projectatlas.structures.StructureManager

class AtlasPlugin : JavaPlugin() {
    
    lateinit var configManager: ConfigManager
    lateinit var identityManager: IdentityManager
    lateinit var economyManager: EconomyManager
    lateinit var cityManager: CityManager
    lateinit var eventManager: EventManager
    lateinit var classManager: ClassManager
    lateinit var guiManager: GuiManager
    lateinit var achievementManager: AchievementManager
    lateinit var npcManager: NPCManager
    lateinit var questManager: QuestManager
    lateinit var siegeManager: SiegeManager
    lateinit var dialogueManager: DialogueManager
    lateinit var structureManager: StructureManager
    lateinit var schematicManager: SchematicManager

    override fun onEnable() {
        logger.info("Project Atlas is waking up...")
        
        // Load Config
        configManager = ConfigManager(this)
        configManager.loadConfig()
        
        // Initialize Managers
        identityManager = IdentityManager(this)
        economyManager = EconomyManager(identityManager)
        cityManager = CityManager(this)
        eventManager = EventManager(this)
        classManager = ClassManager(this)
        guiManager = GuiManager(this)
        achievementManager = AchievementManager(this)
        npcManager = NPCManager(this)
        questManager = QuestManager(this)
        siegeManager = SiegeManager(this)
        dialogueManager = DialogueManager(this)
        structureManager = StructureManager(this)
        schematicManager = SchematicManager(this)
        
        // Register Events
        server.pluginManager.registerEvents(AtlasListener(identityManager, cityManager, classManager, guiManager), this)
        server.pluginManager.registerEvents(guiManager, this)
        server.pluginManager.registerEvents(npcManager, this)
        server.pluginManager.registerEvents(questManager, this)
        server.pluginManager.registerEvents(siegeManager, this)
        server.pluginManager.registerEvents(dialogueManager, this)
        server.pluginManager.registerEvents(com.projectatlas.events.WorldGenListener(this), this)
        
        // Register Commands
        // Register Commands
        getCommand("atlas")?.setExecutor(AtlasCommand(identityManager, economyManager, cityManager, classManager, guiManager, schematicManager))
        
        // Start Scheduler
        eventManager.startScheduler()
        
        // Start City Buffs (runs every 10 seconds = 200 ticks)
        server.scheduler.runTaskTimer(this, com.projectatlas.city.CityBuffTask(this), 200L, 200L)
        
        logger.info("Project Atlas has fully loaded v1.1 Phase 2.")
    }

    override fun onDisable() {
        logger.info("Project Atlas is shutting down...")
        if (::identityManager.isInitialized) {
            identityManager.saveAll()
        }
    }
}
