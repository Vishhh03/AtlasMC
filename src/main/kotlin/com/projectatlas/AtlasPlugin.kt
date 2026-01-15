package com.projectatlas

import com.projectatlas.city.CityManager
import com.projectatlas.economy.EconomyManager
import com.projectatlas.events.EventManager
import com.projectatlas.identity.IdentityManager
import org.bukkit.plugin.java.JavaPlugin

import com.projectatlas.config.ConfigManager



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
import com.projectatlas.party.PartyManager
import com.projectatlas.marketplace.BlueprintMarketplace
import com.projectatlas.skills.SkillTreeManager
import com.projectatlas.survival.SurvivalManager
import com.projectatlas.quests.QuestBoardManager
import com.projectatlas.economy.MarketManager
import com.projectatlas.visuals.VisualManager
import com.projectatlas.chat.ChatManager
import com.projectatlas.events.SupplyDropListener
import com.projectatlas.qol.QoLManager
import com.projectatlas.visuals.AtmosphereManager
import com.projectatlas.map.MapManager
import com.projectatlas.progression.ProgressionManager

class AtlasPlugin : JavaPlugin() {
    
    lateinit var configManager: ConfigManager
    lateinit var identityManager: IdentityManager
    lateinit var economyManager: EconomyManager
    lateinit var cityManager: CityManager
    lateinit var eventManager: EventManager

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
    lateinit var partyManager: PartyManager
    lateinit var blueprintMarketplace: BlueprintMarketplace
    lateinit var skillTreeManager: SkillTreeManager
    lateinit var survivalManager: SurvivalManager
    lateinit var questBoardManager: QuestBoardManager
    lateinit var marketManager: MarketManager
    lateinit var visualManager: VisualManager
    lateinit var chatManager: ChatManager
    lateinit var supplyDropListener: SupplyDropListener
    lateinit var qolManager: QoLManager
    lateinit var atmosphereManager: AtmosphereManager
    lateinit var wonderManager: com.projectatlas.city.WonderManager
    lateinit var outpostManager: com.projectatlas.territory.OutpostManager
    lateinit var mapManager: MapManager
    lateinit var progressionManager: ProgressionManager
    lateinit var eraBossManager: com.projectatlas.progression.EraBossManager
    lateinit var milestoneListener: com.projectatlas.progression.MilestoneListener
    lateinit var resourcePackManager: com.projectatlas.visual.ResourcePackManager

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
        partyManager = PartyManager(this)
        dungeonManager = DungeonManager(this)
        blueprintMarketplace = BlueprintMarketplace(this, schematicManager)
        skillTreeManager = SkillTreeManager(this)
        survivalManager = SurvivalManager(this)
        questBoardManager = QuestBoardManager(this)
        marketManager = MarketManager(this)
        visualManager = VisualManager(this)
        chatManager = ChatManager(this)
        supplyDropListener = SupplyDropListener(this)
        qolManager = QoLManager(this)
        atmosphereManager = AtmosphereManager(this)
        atmosphereManager = AtmosphereManager(this)
        mapManager = MapManager(this)
        progressionManager = ProgressionManager(this)
        eraBossManager = com.projectatlas.progression.EraBossManager(this)
        milestoneListener = com.projectatlas.progression.MilestoneListener(this)
        val mobScalingListener = com.projectatlas.progression.MobScalingListener(this)
        resourcePackManager = com.projectatlas.visual.ResourcePackManager(this)
        val entityCleanupManager = com.projectatlas.util.EntityCleanupManager(this) // Auto-starts task
        
        // Register Events
        server.pluginManager.registerEvents(AtlasListener(identityManager, cityManager, guiManager), this)
        server.pluginManager.registerEvents(mobScalingListener, this)
        server.pluginManager.registerEvents(resourcePackManager, this)
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
        server.pluginManager.registerEvents(blueprintMarketplace, this)
        server.pluginManager.registerEvents(skillTreeManager, this)
        server.pluginManager.registerEvents(survivalManager, this)
        server.pluginManager.registerEvents(questBoardManager, this)
        server.pluginManager.registerEvents(marketManager, this) // Register Market Events
        server.pluginManager.registerEvents(chatManager, this)
        server.pluginManager.registerEvents(supplyDropListener, this)
        server.pluginManager.registerEvents(qolManager, this)
        server.pluginManager.registerEvents(atmosphereManager, this)
        server.pluginManager.registerEvents(partyManager, this)
        server.pluginManager.registerEvents(achievementManager, this)
        server.pluginManager.registerEvents(mapManager, this)
        server.pluginManager.registerEvents(progressionManager, this)
        server.pluginManager.registerEvents(eraBossManager, this)
        server.pluginManager.registerEvents(milestoneListener, this)
        

        
        // Register Commands
        getCommand("atlas")?.setExecutor(AtlasCommand(identityManager, economyManager, cityManager, guiManager, schematicManager, politicsManager))
        getCommand("pc")?.setExecutor(com.projectatlas.party.PartyChatCommand(this))
        
        // Start Scheduler
        eventManager.startScheduler()
        
        // Start City Buffs (runs every 10 seconds = 200 ticks)
        server.scheduler.runTaskTimer(this, com.projectatlas.city.CityBuffTask(this), 200L, 200L)
        
        // Start Relic spawn scheduler
        relicManager.scheduleRandomRelicSpawn()
        // Initialize New Managers
        wonderManager = com.projectatlas.city.WonderManager(this)
        outpostManager = com.projectatlas.territory.OutpostManager(this)

        server.pluginManager.registerEvents(wonderManager, this)
        server.pluginManager.registerEvents(outpostManager, this)
        
        logger.info("Project Atlas has fully loaded v1.3 - Dungeon Update!")
    }

    override fun onDisable() {
        logger.info("Project Atlas is shutting down...")
        if (::identityManager.isInitialized) {
            identityManager.saveAll()
        }
    }
}
