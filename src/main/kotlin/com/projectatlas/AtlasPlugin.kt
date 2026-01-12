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
import com.projectatlas.history.HistoryManager
import com.projectatlas.politics.PoliticsManager
import com.projectatlas.bounty.BountyManager
import com.projectatlas.relics.RelicManager
import com.projectatlas.worldboss.WorldBossManager
import com.projectatlas.dungeon.DungeonManager

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
    lateinit var historyManager: HistoryManager
    lateinit var politicsManager: PoliticsManager
    lateinit var bountyManager: BountyManager
    lateinit var relicManager: RelicManager
    lateinit var worldBossManager: WorldBossManager
    lateinit var dungeonManager: DungeonManager

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
        historyManager = HistoryManager(this)
        politicsManager = PoliticsManager(this)
        bountyManager = BountyManager(this)
        relicManager = RelicManager(this)
        worldBossManager = WorldBossManager(this)
        dungeonManager = DungeonManager(this)
        
        // Register Events
        server.pluginManager.registerEvents(AtlasListener(identityManager, cityManager, classManager, guiManager), this)
        server.pluginManager.registerEvents(guiManager, this)
        server.pluginManager.registerEvents(npcManager, this)
        server.pluginManager.registerEvents(questManager, this)
        server.pluginManager.registerEvents(siegeManager, this)
        server.pluginManager.registerEvents(dialogueManager, this)
        server.pluginManager.registerEvents(com.projectatlas.events.WorldGenListener(this), this)
        server.pluginManager.registerEvents(com.projectatlas.events.WanderingNPCListener(this), this)
        server.pluginManager.registerEvents(com.projectatlas.structures.StructureListener(this), this)
        server.pluginManager.registerEvents(com.projectatlas.classes.AbilityListener(this), this)
        server.pluginManager.registerEvents(bountyManager, this)
        server.pluginManager.registerEvents(relicManager, this)
        server.pluginManager.registerEvents(worldBossManager, this)
        server.pluginManager.registerEvents(dungeonManager, this)
        
        // Register Commands
        // Register Commands
        getCommand("atlas")?.setExecutor(AtlasCommand(identityManager, economyManager, cityManager, classManager, guiManager, schematicManager, politicsManager))
        
        // Start Scheduler
        eventManager.startScheduler()
        
        // Start City Buffs (runs every 10 seconds = 200 ticks)
        server.scheduler.runTaskTimer(this, com.projectatlas.city.CityBuffTask(this), 200L, 200L)
        
        // Start Relic spawn scheduler
        relicManager.scheduleRandomRelicSpawn()
        
        logger.info("Project Atlas has fully loaded v1.3 - Dungeon Update!")
    }

    override fun onDisable() {
        logger.info("Project Atlas is shutting down...")
        if (::identityManager.isInitialized) {
            identityManager.saveAll()
        }
    }
}
