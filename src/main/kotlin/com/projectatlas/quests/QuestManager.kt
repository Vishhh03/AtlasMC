package com.projectatlas.quests

import com.projectatlas.AtlasPlugin
import org.bukkit.Location
import org.bukkit.scheduler.BukkitTask
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Sound
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class QuestManager(private val plugin: AtlasPlugin) : Listener {

    private val activeQuests = ConcurrentHashMap<UUID, ActiveQuest>()
    private val bossBars = ConcurrentHashMap<UUID, BossBar>()
    
    private var questTask: BukkitTask? = null
    
    init {
        startQuestLoop()
    }
    
    // Predefined quest templates - 40+ diverse quests!
    private val questTemplates = listOf(
        // ═══════════════════════════════════════════════════════════
        // EASY QUESTS (Quick tasks, beginner-friendly)
        // ═══════════════════════════════════════════════════════════
        Quest("zombie_hunt_easy", "Zombie Cleanup", "The dead rise from the soil... put them to rest.", 
            Difficulty.EASY, QuestObjective.KillMobs(EntityType.ZOMBIE, 5), null, 100.0,
            "Check caves and dark places."),
        Quest("lost_supplies", "Lost Supplies", "Our stocks are low... bread is life.", 
            Difficulty.EASY, QuestObjective.FetchItem(org.bukkit.Material.BREAD, 10), null, 150.0,
            "Wheat grows in the fields."),
        Quest("wood_gatherer", "Lumberjack Training", "We need timber for repairs. Bring oak logs.", 
            Difficulty.EASY, QuestObjective.FetchItem(org.bukkit.Material.OAK_LOG, 32), null, 120.0,
            "Find a forest."),
        Quest("wool_collector", "Shepherd's Request", "The winter is cold. Collect wool for blankets.", 
            Difficulty.EASY, QuestObjective.FetchItem(org.bukkit.Material.WHITE_WOOL, 16), null, 100.0,
            "Sheep roam the plains."),
        Quest("fishing_hobby", "Gone Fishing", "The old man by the river teaches patience. Catch some fish.", 
            Difficulty.EASY, QuestObjective.FishItems(5), null, 150.0,
            "Find water and wait."),
        Quest("chicken_hunt", "Poultry Problem", "Chickens have overrun the farm. Time for dinner.", 
            Difficulty.EASY, QuestObjective.KillMobs(EntityType.CHICKEN, 10), null, 80.0,
            "They cluck nearby."),
        Quest("first_steps", "Wanderer", "Stretch your legs. Walk 500 blocks in any direction.", 
            Difficulty.EASY, QuestObjective.TravelDistance(500), null, 100.0,
            "Just keep walking."),
            
        // ═══════════════════════════════════════════════════════════
        // MEDIUM QUESTS (More challenge, varied objectives)
        // ═══════════════════════════════════════════════════════════
        Quest("zombie_hunt_medium", "Undead Purge", "A horde approaches... hold the line.", 
            Difficulty.MEDIUM, QuestObjective.KillMobs(EntityType.ZOMBIE, 15), 300, 300.0,
            "They gather in numbers where the light fails."),
        Quest("skeleton_hunt", "Bone Collectors", "Rattling bones haunt the woods... silence them.", 
            Difficulty.MEDIUM, QuestObjective.KillMobs(EntityType.SKELETON, 10), 240, 250.0,
            "They hide under the canopy."),
        Quest("find_scout", "Missing Scout", "One of our own is missing... bring them home.", 
            Difficulty.MEDIUM, QuestObjective.FindNPC("Lost Scout"), 600, 400.0,
            "Last seen wandering the wilderness."),
        Quest("iron_age", "Iron Age", "The forge needs fuel. Mine iron ore.", 
            Difficulty.MEDIUM, QuestObjective.MineBlocks(org.bukkit.Material.IRON_ORE, 20), null, 350.0,
            "Dig deep, around Y=16."),
        Quest("desert_expedition", "Desert Expedition", "Legends speak of golden sands... find the desert.", 
            Difficulty.MEDIUM, QuestObjective.VisitBiome("desert"), null, 400.0,
            "Travel far from the green."),
        Quest("jungle_fever", "Jungle Fever", "A rare flower blooms only in the jungle. Explore.", 
            Difficulty.MEDIUM, QuestObjective.VisitBiome("jungle"), null, 450.0,
            "Dense trees and vines await."),
        Quest("creeper_exterminator", "Explosive Situation", "Creepers threaten our walls. Remove them.", 
            Difficulty.MEDIUM, QuestObjective.KillMobs(EntityType.CREEPER, 8), 300, 400.0,
            "Listen for the hiss."),
        Quest("tame_wolves", "Wolf Whisperer", "A pack could protect us. Tame some wolves.", 
            Difficulty.MEDIUM, QuestObjective.TameAnimals(3), null, 350.0,
            "Bring bones."),
        Quest("coal_miner", "Fuel the Fires", "The furnaces are cold. Mine coal.", 
            Difficulty.MEDIUM, QuestObjective.MineBlocks(org.bukkit.Material.COAL_ORE, 30), null, 200.0,
            "Check the mountains."),
        Quest("sugar_rush", "Sweet Tooth", "The baker needs sugar cane for cakes.", 
            Difficulty.MEDIUM, QuestObjective.FetchItem(org.bukkit.Material.SUGAR_CANE, 32), null, 180.0,
            "Near water."),
        Quest("long_walk", "Long Road Home", "A journey of a thousand blocks begins with one step.", 
            Difficulty.MEDIUM, QuestObjective.TravelDistance(2000), null, 300.0,
            "Keep your compass handy."),
        Quest("archer_training", "Archer Training", "Strays are deadly shots. Learn from defeating them.", 
            Difficulty.MEDIUM, QuestObjective.KillMobs(EntityType.STRAY, 6), 360, 300.0,
            "Cold biomes."),
        Quest("trade_deal", "The Art of the Deal", "Make trades with villagers.", 
            Difficulty.MEDIUM, QuestObjective.TradeWithVillager(5), null, 400.0,
            "Find a village."),
            
        // ═══════════════════════════════════════════════════════════
        // HARD QUESTS (Significant challenge, longer objectives)
        // ═══════════════════════════════════════════════════════════
        Quest("spider_nest", "Spider Extermination", "Eight legs, endless hunger... cleanse the nest.", 
            Difficulty.HARD, QuestObjective.KillMobs(EntityType.SPIDER, 20), 180, 600.0,
            "They hiss in the dark."),
        Quest("rare_gems", "Gem Hunter", "Shiny treasures from the deep... I desire them.", 
            Difficulty.HARD, QuestObjective.FetchItem(org.bukkit.Material.DIAMOND, 5), null, 1000.0,
            "Deep below the surface."),
        Quest("witch_hunt", "Witch Hunt", "Dark magic corrupts the swamp. End the coven.", 
            Difficulty.HARD, QuestObjective.KillMobs(EntityType.WITCH, 5), 420, 800.0,
            "Swamp huts..."),
        Quest("blazing_trail", "Blazing Trail", "The Nether's guardians burn bright. Douse their flames.", 
            Difficulty.HARD, QuestObjective.KillMobs(EntityType.BLAZE, 10), 600, 1200.0,
            "In the fortress."),
        Quest("enderman_menace", "Void Stalkers", "They watch from the shadows. Strike first.", 
            Difficulty.HARD, QuestObjective.KillMobs(EntityType.ENDERMAN, 8), 480, 900.0,
            "Don't blink."),
        Quest("gold_rush", "Gold Rush", "A merchant pays handsomely for gold.", 
            Difficulty.HARD, QuestObjective.FetchItem(org.bukkit.Material.GOLD_INGOT, 32), null, 750.0,
            "Badlands or deep caves."),
        Quest("ancient_debris", "Ancient Metal", "Netherite calls to those brave enough to seek it.", 
            Difficulty.HARD, QuestObjective.FetchItem(org.bukkit.Material.ANCIENT_DEBRIS, 3), null, 2000.0,
            "Y=15 in the Nether."),
        Quest("slayer_any", "Monster Slayer", "Prove your worth. Slay 50 hostile creatures.", 
            Difficulty.HARD, QuestObjective.KillAnyMobs(50), 600, 800.0,
            "They come at night."),
        Quest("grand_expedition", "Grand Expedition", "Travel 5000 blocks from here.", 
            Difficulty.HARD, QuestObjective.TravelDistance(5000), null, 600.0,
            "Pack supplies."),
        Quest("master_fisherman", "Master Angler", "The elder wants proof of your patience.", 
            Difficulty.HARD, QuestObjective.FishItems(20), null, 500.0,
            "Rain improves luck."),
        Quest("cave_spider_infestation", "Mineshaft Menace", "The abandoned mines are infested. Clear them.", 
            Difficulty.HARD, QuestObjective.KillMobs(EntityType.CAVE_SPIDER, 15), 360, 700.0,
            "Mineshafts..."),
        Quest("obsidian_harvester", "Portal Builder", "Collect obsidian for the portal.", 
            Difficulty.HARD, QuestObjective.MineBlocks(org.bukkit.Material.OBSIDIAN, 14), null, 600.0,
            "Diamond pickaxe required."),
        Quest("snow_journey", "Frozen Expedition", "Somewhere north lies a snowy tundra.", 
            Difficulty.HARD, QuestObjective.VisitBiome("snowy_taiga"), null, 500.0,
            "Bundle up."),
        Quest("mushroom_mystery", "Land of Shrooms", "Legends tell of a land where giant mushrooms grow...", 
            Difficulty.HARD, QuestObjective.VisitBiome("mushroom_fields"), null, 1500.0,
            "Very rare. Ocean shores."),
            
        // ═══════════════════════════════════════════════════════════
        // NIGHTMARE QUESTS (Extreme challenge, epic rewards)
        // ═══════════════════════════════════════════════════════════
        Quest("nightmare_horde", "Nightmare Siege", "They are coming in waves... survive.", 
            Difficulty.NIGHTMARE, QuestObjective.KillMobs(EntityType.ZOMBIE, 100), 600, 2500.0,
            "This will be a long night."),
        Quest("dragon_slayer_prep", "Dragon Prep", "Before facing the dragon, gather eyes.", 
            Difficulty.NIGHTMARE, QuestObjective.FetchItem(org.bukkit.Material.ENDER_EYE, 12), null, 3000.0,
            "Endermen and blaze powder."),
        Quest("wither_skeleton_hunt", "Skull Collector", "Three skulls needed for a dark ritual.", 
            Difficulty.NIGHTMARE, QuestObjective.KillMobs(EntityType.WITHER_SKELETON, 30), 900, 2000.0,
            "Fortress, sword of looting."),
        Quest("the_end_awaits", "End Explorer", "Visit the dimension of The End.", 
            Difficulty.NIGHTMARE, QuestObjective.VisitBiome("the_end"), null, 5000.0,
            "12 eyes, a portal awaits."),
        Quest("nether_veteran", "Nether Veteran", "Survive 10 minutes in the Nether.", 
            Difficulty.NIGHTMARE, QuestObjective.SurviveTime(600), 660, 1500.0,
            "Fire resistance recommended."),
        Quest("ultimate_journey", "Around the World", "Travel 10,000 blocks.", 
            Difficulty.NIGHTMARE, QuestObjective.TravelDistance(10000), null, 2000.0,
            "Horses, boats, and determination."),
        Quest("guardian_gauntlet", "Ocean Monument Raid", "The guardians protect ancient treasure.", 
            Difficulty.NIGHTMARE, QuestObjective.KillMobs(EntityType.ELDER_GUARDIAN, 1), 1200, 3500.0,
            "Underwater. Bring potions."),
        Quest("piglin_brute_hunt", "Brute Force", "The strongest piglins guard bastions.", 
            Difficulty.NIGHTMARE, QuestObjective.KillMobs(EntityType.PIGLIN_BRUTE, 10), 900, 2500.0,
            "Bastion remnants."),
        Quest("emerald_tycoon", "Emerald Tycoon", "Become the richest trader in the land.", 
            Difficulty.NIGHTMARE, QuestObjective.FetchItem(org.bukkit.Material.EMERALD, 64), null, 3000.0,
            "Trade or mine in mountains.")
    )
    
    // ... existing startQuest methods ...
    
    // Check for FETCH/FIND completion
    fun checkQuestCompletion(player: Player, targetEntity: String? = null) {
        val activeQuest = activeQuests[player.uniqueId] ?: return
        
        when (val obj = activeQuest.quest.objective) {
            is QuestObjective.FetchItem -> {
                // Check inventory
                val amount = player.inventory.all(obj.material).values.sumOf { it.amount }
                if (amount >= obj.count) {
                    // Remove items
                    val remove = org.bukkit.inventory.ItemStack(obj.material, obj.count)
                    player.inventory.removeItem(remove)
                    activeQuest.progress = obj.count
                    completeQuest(player, activeQuest)
                } else {
                    player.sendMessage(Component.text("You only have $amount/${obj.count} ${obj.material.name}!", NamedTextColor.RED))
                }
            }
            is QuestObjective.FindNPC -> {
                if (targetEntity != null && targetEntity.contains(obj.npcName, ignoreCase = true)) {
                    activeQuest.progress = 1
                    completeQuest(player, activeQuest)
                }
            }
            else -> {} // Kills are handled by event
        }
    }
    
    // ... rest of class ...
    
    fun getQuestTemplates(): List<Quest> = questTemplates
    
    fun getQuestByDifficulty(difficulty: Difficulty): Quest? {
        return questTemplates.filter { it.difficulty == difficulty }.randomOrNull()
    }
    
    // ... startQuest, abandonQuest, getActiveQuest ...

    // (KEEP existing methods: startQuest, abandonQuest, getActiveQuest, checkQuestTimeout)
    // IMPORTANT: I am truncating existing methods here for brevity in Replace, 
    // but in a real edit I must be careful not to delete them if I replace the whole block.
    // Since I am replacing the templates and adding methods, I will target the templates block and append the new method.
    // Re-reading: I should use separate blocks if possible, but the template list is early.
    
    // Let's just REPLACE THE TEMPLATES list and use a separate tool call for the new method to be safe,
    // OR just validly replace the top part.
    
    // Actually, I can replace the whole file content or large chunks.
    // I'll replace the templates lines 23-34 first.
    

    
    fun startQuest(player: Player, quest: Quest): Boolean {
        if (activeQuests.containsKey(player.uniqueId)) {
            player.sendMessage(Component.text("You already have an active quest!", NamedTextColor.RED))
            return false
        }
        
        val activeQuest = ActiveQuest(quest)
        activeQuests[player.uniqueId] = activeQuest
        
        // Announce
        player.sendMessage(Component.empty())
        player.sendMessage(Component.text("═══ QUEST STARTED ═══", NamedTextColor.GOLD))
        player.sendMessage(Component.text(quest.name, NamedTextColor.YELLOW))
        player.sendMessage(Component.text(quest.description, NamedTextColor.GRAY))
        player.sendMessage(Component.empty())
        player.sendMessage(Component.text("Hint: ${quest.hint}", NamedTextColor.AQUA).decorate(net.kyori.adventure.text.format.TextDecoration.ITALIC))
        player.sendMessage(Component.text("Difficulty: ${quest.difficulty.displayName}", NamedTextColor.RED))
        player.sendMessage(Component.text("Reward: ${quest.reward}g", NamedTextColor.GREEN))
        if (quest.timeLimitSeconds != null) {
            player.sendMessage(Component.text("Time Limit: ${quest.timeLimitSeconds}s", NamedTextColor.AQUA))
        }
        player.sendMessage(Component.empty())
        
        player.playSound(player.location, Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f)
        
        // Create boss bar for Hard+ quests
        if (quest.difficulty == Difficulty.HARD || quest.difficulty == Difficulty.NIGHTMARE) {
            val bossBar = BossBar.bossBar(
                Component.text("${quest.name}: 0/${activeQuest.getTargetCount()}", NamedTextColor.RED),
                0f,
                BossBar.Color.RED,
                BossBar.Overlay.PROGRESS
            )
            bossBars[player.uniqueId] = bossBar
            player.showBossBar(bossBar)
        }
        
        // Start timeout checker
        if (quest.timeLimitSeconds != null) {
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                checkQuestTimeout(player)
            }, quest.timeLimitSeconds * 20L)
        }
        
        return true
    }
    
    fun abandonQuest(player: Player) {
        val quest = activeQuests.remove(player.uniqueId)
        bossBars.remove(player.uniqueId)?.let { player.hideBossBar(it) }
        
        if (quest != null) {
            player.sendMessage(Component.text("Quest abandoned.", NamedTextColor.RED))
        }
    }
    
    fun getActiveQuest(player: Player): ActiveQuest? = activeQuests[player.uniqueId]
    
    fun hasActiveQuest(player: Player): Boolean = activeQuests.containsKey(player.uniqueId)
    
    /**
     * Complete quest manually (from QuestBoardManager turn-in)
     */
    fun completeQuestManual(player: Player) {
        val activeQuest = activeQuests[player.uniqueId] ?: return
        completeQuest(player, activeQuest)
    }
    
    private fun checkQuestTimeout(player: Player) {
        val activeQuest = activeQuests[player.uniqueId] ?: return
        
        if (activeQuest.isExpired() && !activeQuest.isComplete()) {
            activeQuests.remove(player.uniqueId)
            bossBars.remove(player.uniqueId)?.let { player.hideBossBar(it) }
            
            player.sendMessage(Component.text("═══ QUEST FAILED ═══", NamedTextColor.DARK_RED))
            player.sendMessage(Component.text("Time ran out!", NamedTextColor.RED))
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f)
        }
    }
    
    @EventHandler
    fun onMobKill(event: EntityDeathEvent) {
        val killer = event.entity.killer ?: return
        val activeQuest = activeQuests[killer.uniqueId] ?: return
        
        // Check if this mob type matches objective
        val objective = activeQuest.quest.objective
        if (objective is QuestObjective.KillMobs && event.entityType == objective.mobType) {
            activeQuest.progress++
            
            // Update boss bar
            bossBars[killer.uniqueId]?.let { bar ->
                val progress = activeQuest.progress.toFloat() / activeQuest.getTargetCount()
                bar.progress(progress.coerceIn(0f, 1f))
                bar.name(Component.text("${activeQuest.quest.name}: ${activeQuest.progress}/${activeQuest.getTargetCount()}", NamedTextColor.RED))
            }
            
            // Check completion
            if (activeQuest.isComplete()) {
                completeQuest(killer, activeQuest)
            } else {
                // Progress notification
                val remaining = activeQuest.getTargetCount() - activeQuest.progress
                killer.sendMessage(Component.text("Progress: ${activeQuest.progress}/${activeQuest.getTargetCount()} ($remaining remaining)", NamedTextColor.YELLOW))
            }
        }
    }
    
    private fun completeQuest(player: Player, activeQuest: ActiveQuest) {
        activeQuests.remove(player.uniqueId)
        bossBars.remove(player.uniqueId)?.let { player.hideBossBar(it) }
        
        // Award reward
        // Award reward with Tax Logic
        val profile = plugin.identityManager.getPlayer(player.uniqueId)
        var finalReward = activeQuest.quest.reward
        var taxAmount = 0.0
        
        if (profile != null) {
            // Apply Tax
            if (profile.cityId != null) {
                val city = plugin.cityManager.getCity(profile.cityId!!)
                if (city != null && city.taxRate > 0) {
                    taxAmount = finalReward * (city.taxRate / 100.0)
                    finalReward -= taxAmount
                    
                    // Pay to City
                    city.treasury += taxAmount
                    plugin.cityManager.saveCity(city)
                }
            }
            
            profile.balance += finalReward
            plugin.identityManager.saveProfile(player.uniqueId)
        }
        
        // Announce
        player.sendMessage(Component.empty())
        player.sendMessage(Component.text("═══ QUEST COMPLETE ═══", NamedTextColor.GREEN))
        player.sendMessage(Component.text(activeQuest.quest.name, NamedTextColor.YELLOW))
        player.sendMessage(Component.text("+${finalReward}g", NamedTextColor.GOLD))
        if (taxAmount > 0) {
             player.sendMessage(Component.text("(Tax Paid: ${taxAmount}g)", NamedTextColor.GRAY))
        }
        player.sendMessage(Component.empty())
        
        player.playSound(player.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f)
        
        // Award achievement
        plugin.achievementManager.awardAchievement(player, "quest_complete")
        
        // Grant XP
        plugin.identityManager.grantXp(player, 100L) // Base XP for any quest
    }
    private fun startQuestLoop() {
        questTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            activeQuests.forEach { (uuid, activeQuest) ->
                val player = plugin.server.getPlayer(uuid) ?: return@forEach
                tickQuest(player, activeQuest)
            }
        }, 20L, 20L)
    }

    private fun tickQuest(player: Player, activeQuest: ActiveQuest) {
        // Time Limit UI
        if (activeQuest.quest.timeLimitSeconds != null) {
            val remaining = activeQuest.getRemainingSeconds() ?: 0
            if (activeQuest.isExpired()) {
                checkQuestTimeout(player)
            } else {
                 bossBars[player.uniqueId]?.let { bar ->
                      bar.name(Component.text("${activeQuest.quest.name}: ${activeQuest.progress}/${activeQuest.getTargetCount()} | ${remaining}s", NamedTextColor.RED))
                 }
            }
        }
        
        // Provision Spawning
        val objective = activeQuest.quest.objective
        if (objective is QuestObjective.KillMobs && !activeQuest.isComplete()) {
            val world = player.world
            val isDay = world.time in 0..12000
            
            // Only spawn if few nearby
            val nearby = player.location.getNearbyEntities(30.0, 20.0, 30.0).count { it.type == objective.mobType }
            if (nearby < 3) {
                 spawnQuestMob(player, objective.mobType, isDay)
            }
        }
    }
    
    private fun spawnQuestMob(player: Player, type: EntityType, isDay: Boolean) {
        val loc = player.location
        // Simple random offset
        val x = loc.x + (Math.random() * 20 - 10)
        val z = loc.z + (Math.random() * 20 - 10)
        val y = loc.world.getHighestBlockYAt(x.toInt(), z.toInt()).toDouble() + 1
        
        val spawnLoc = Location(loc.world, x, y, z)
        if (spawnLoc.distance(loc) < 5) return // Too close
        
        try {
            loc.world.spawnEntity(spawnLoc, type).apply {
                if (this is LivingEntity) {
                    this.isGlowing = true // Help finding
                    this.customName(Component.text("Quest Target", NamedTextColor.RED))
                    this.isCustomNameVisible = true
                    
                    // Helmet if day and burns
                    if (isDay && (type == EntityType.ZOMBIE || type == EntityType.SKELETON)) {
                        this.equipment?.helmet = org.bukkit.inventory.ItemStack(org.bukkit.Material.LEATHER_HELMET)
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore spawn failures
        }
    }
}
