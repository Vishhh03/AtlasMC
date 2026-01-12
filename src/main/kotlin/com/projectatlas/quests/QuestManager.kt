package com.projectatlas.quests

import com.projectatlas.AtlasPlugin
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
    
    // Predefined quest templates
    // Predefined quest templates
    private val questTemplates = listOf(
        // Difficulty: EASY
        Quest("zombie_hunt_easy", "Zombie Cleanup", "Eliminate zombies threatening the area", 
            Difficulty.EASY, QuestObjective.KillMobs(EntityType.ZOMBIE, 5), null, 100.0,
            "Zombies often appear at night or in dark caves."),
        Quest("lost_supplies", "Lost Supplies", "Bring me 10 loaves of Bread", 
            Difficulty.EASY, QuestObjective.FetchItem(org.bukkit.Material.BREAD, 10), null, 150.0,
            "Craft Bread from Wheat, or trade with a Farmer in the city."),
            
        // Difficulty: MEDIUM
        Quest("zombie_hunt_medium", "Undead Purge", "Clear out a zombie infestation", 
            Difficulty.MEDIUM, QuestObjective.KillMobs(EntityType.ZOMBIE, 15), 300, 300.0,
            "Look for large groups of Zombies in the wilderness at night."),
        Quest("skeleton_hunt", "Bone Collectors", "Destroy skeleton archers", 
            Difficulty.MEDIUM, QuestObjective.KillMobs(EntityType.SKELETON, 10), 240, 250.0,
            "Skeletons hide under trees during the day to avoid sunlight."),
        Quest("find_scout", "Missing Scout", "Find the lost Scout wandering nearby", 
            Difficulty.MEDIUM, QuestObjective.FindNPC("Lost Scout"), 600, 400.0,
            "The Scout is wandering in the wilderness. Listen for reports of their location!"),
            
        // Difficulty: HARD
        Quest("spider_nest", "Spider Extermination", "Clear a spider nest", 
            Difficulty.HARD, QuestObjective.KillMobs(EntityType.SPIDER, 20), 180, 600.0,
            "Spiders are more aggressive at night. Listen for their hissing."),
        Quest("rare_gems", "Gem Hunter", "Bring me 5 Diamonds", 
            Difficulty.HARD, QuestObjective.FetchItem(org.bukkit.Material.DIAMOND, 5), null, 1000.0,
            "Diamonds are found deep underground (Y level -58) or in chests."),
            
        // Difficulty: NIGHTMARE
        Quest("nightmare_horde", "Nightmare Siege", "Survive an endless horde", 
            Difficulty.NIGHTMARE, QuestObjective.KillMobs(EntityType.ZOMBIE, 50), 300, 1500.0,
            "Prepare for a long battle. Bring plenty of food and healing potions.")
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
}
