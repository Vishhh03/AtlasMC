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
    private val questTemplates = listOf(
        Quest("zombie_hunt_easy", "Zombie Cleanup", "Eliminate zombies threatening the area", 
            Difficulty.EASY, QuestObjective.KillMobs(EntityType.ZOMBIE, 5), null, 100.0),
        Quest("zombie_hunt_medium", "Undead Purge", "Clear out a zombie infestation", 
            Difficulty.MEDIUM, QuestObjective.KillMobs(EntityType.ZOMBIE, 15), 300, 300.0),
        Quest("skeleton_hunt", "Bone Collectors", "Destroy skeleton archers", 
            Difficulty.MEDIUM, QuestObjective.KillMobs(EntityType.SKELETON, 10), 240, 250.0),
        Quest("spider_nest", "Spider Extermination", "Clear a spider nest", 
            Difficulty.HARD, QuestObjective.KillMobs(EntityType.SPIDER, 20), 180, 600.0),
        Quest("nightmare_horde", "Nightmare Siege", "Survive an endless horde", 
            Difficulty.NIGHTMARE, QuestObjective.KillMobs(EntityType.ZOMBIE, 50), 300, 1500.0)
    )
    
    fun getQuestTemplates(): List<Quest> = questTemplates
    
    fun getQuestByDifficulty(difficulty: Difficulty): Quest? {
        return questTemplates.filter { it.difficulty == difficulty }.randomOrNull()
    }

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
        val profile = plugin.identityManager.getPlayer(player.uniqueId)
        if (profile != null) {
            profile.balance += activeQuest.quest.reward
            plugin.identityManager.saveProfile(player.uniqueId)
        }
        
        // Announce
        player.sendMessage(Component.empty())
        player.sendMessage(Component.text("═══ QUEST COMPLETE ═══", NamedTextColor.GREEN))
        player.sendMessage(Component.text(activeQuest.quest.name, NamedTextColor.YELLOW))
        player.sendMessage(Component.text("+${activeQuest.quest.reward}g", NamedTextColor.GOLD))
        player.sendMessage(Component.empty())
        
        player.playSound(player.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f)
        
        // Award achievement
        plugin.achievementManager.awardAchievement(player, "quest_complete")
    }
}
