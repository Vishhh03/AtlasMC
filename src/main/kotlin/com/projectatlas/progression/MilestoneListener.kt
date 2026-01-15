package com.projectatlas.progression

import com.projectatlas.AtlasPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.PlayerBedEnterEvent
import org.bukkit.event.player.PlayerLevelChangeEvent
import org.bukkit.inventory.ItemStack

/**
 * Milestone Listener - Detects when players complete progression milestones.
 * 
 * Automatically tracks and awards milestones for:
 * - Level achievements
 * - Crafting achievements
 * - Sleep/rest
 * - Resource gathering
 * - Quest completion (handled in QuestManager)
 * - City actions (handled in CityManager)
 * - Dungeon completion (handled in DungeonManager)
 */
class MilestoneListener(private val plugin: AtlasPlugin) : Listener {

    // ══════════════════════════════════════════════════════════════
    // LEVEL MILESTONES
    // ══════════════════════════════════════════════════════════════

    @EventHandler
    fun onLevelUp(event: PlayerLevelChangeEvent) {
        val player = event.player
        val profile = plugin.identityManager.getPlayer(player.uniqueId) ?: return
        
        // Update Atlas level based on vanilla XP level (simplified mapping)
        // Could be more complex (custom XP system)
        val newAtlasLevel = event.newLevel
        if (newAtlasLevel > profile.level) {
            profile.level = newAtlasLevel
        }
        
        // Check level milestones
        when {
            profile.level >= 5 -> checkMilestone(player, ProgressionManager.Milestone.E0_LEVEL_5)
            profile.level >= 15 -> checkMilestone(player, ProgressionManager.Milestone.E1_LEVEL_15)
            profile.level >= 30 -> checkMilestone(player, ProgressionManager.Milestone.E2_LEVEL_30)
            profile.level >= 50 -> checkMilestone(player, ProgressionManager.Milestone.E3_LEVEL_50)
        }
        
        // Also check for level 5, 15, 30, 50 specifically
        if (profile.level >= 5) checkMilestone(player, ProgressionManager.Milestone.E0_LEVEL_5)
        if (profile.level >= 15) checkMilestone(player, ProgressionManager.Milestone.E1_LEVEL_15)
        if (profile.level >= 30) checkMilestone(player, ProgressionManager.Milestone.E2_LEVEL_30)
        if (profile.level >= 50) checkMilestone(player, ProgressionManager.Milestone.E3_LEVEL_50)
    }

    // ══════════════════════════════════════════════════════════════
    // SLEEP MILESTONE (Era 0)
    // ══════════════════════════════════════════════════════════════

    @EventHandler
    fun onBedEnter(event: PlayerBedEnterEvent) {
        if (event.bedEnterResult == PlayerBedEnterEvent.BedEnterResult.OK) {
            val player = event.player
            
            // Schedule check for after sleep completes
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                if (player.bedSpawnLocation != null) {
                    checkMilestone(player, ProgressionManager.Milestone.E0_SLEEP)
                }
            }, 100L) // 5 seconds after entering bed
        }
    }

    // ══════════════════════════════════════════════════════════════
    // CRAFTING MILESTONES
    // ══════════════════════════════════════════════════════════════

    @EventHandler
    fun onCraft(event: CraftItemEvent) {
        val player = event.whoClicked as? Player ?: return
        val result = event.recipe.result
        
        // Check for iron gear milestone
        checkIronGearMilestone(player)
        
        // Check for Eyes of Ender
        if (result.type == Material.ENDER_EYE) {
            checkEyesMilestone(player)
        }
    }

    private fun checkIronGearMilestone(player: Player) {
        val inventory = player.inventory
        
        val hasIronHelmet = inventory.contains(Material.IRON_HELMET) || 
                           inventory.helmet?.type == Material.IRON_HELMET
        val hasIronChest = inventory.contains(Material.IRON_CHESTPLATE) || 
                          inventory.chestplate?.type == Material.IRON_CHESTPLATE
        val hasIronLegs = inventory.contains(Material.IRON_LEGGINGS) || 
                         inventory.leggings?.type == Material.IRON_LEGGINGS
        val hasIronBoots = inventory.contains(Material.IRON_BOOTS) || 
                          inventory.boots?.type == Material.IRON_BOOTS
        val hasIronSword = inventory.contains(Material.IRON_SWORD)
        val hasIronPickaxe = inventory.contains(Material.IRON_PICKAXE)
        
        // Need at least armor set + one tool
        if (hasIronHelmet && hasIronChest && hasIronLegs && hasIronBoots && (hasIronSword || hasIronPickaxe)) {
            checkMilestone(player, ProgressionManager.Milestone.E0_IRON_GEAR)
        }
    }

    private fun checkEyesMilestone(player: Player) {
        val inventory = player.inventory
        var eyeCount = 0
        
        inventory.contents.filterNotNull().forEach { item ->
            if (item.type == Material.ENDER_EYE) {
                eyeCount += item.amount
            }
        }
        
        if (eyeCount >= 12) {
            checkMilestone(player, ProgressionManager.Milestone.E3_EYES)
        }
    }

    // ══════════════════════════════════════════════════════════════
    // RESOURCE GATHERING MILESTONES
    // ══════════════════════════════════════════════════════════════

    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        
        // Check for blaze rod milestone (when picking up, not breaking)
        // This is handled in pickup event instead
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        
        // Check for blaze rods
        checkBlazeRodMilestone(player)
        
        // Recheck iron gear
        checkIronGearMilestone(player)
        
        // Recheck eyes
        checkEyesMilestone(player)
    }

    private fun checkBlazeRodMilestone(player: Player) {
        var rodCount = 0
        
        player.inventory.contents.filterNotNull().forEach { item ->
            if (item.type == Material.BLAZE_ROD) {
                rodCount += item.amount
            }
        }
        
        if (rodCount >= 3) {
            checkMilestone(player, ProgressionManager.Milestone.E2_BLAZE_RODS)
        }
    }

    // ══════════════════════════════════════════════════════════════
    // COMBAT MILESTONES
    // ══════════════════════════════════════════════════════════════

    @EventHandler
    fun onEntityKill(event: EntityDeathEvent) {
        val killer = event.entity.killer ?: return
        
        // Quest completion tracking is handled in QuestManager
        // Dungeon completion tracking is handled in DungeonManager
        // Boss kills are handled in EraBossManager
    }

    // ══════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ══════════════════════════════════════════════════════════════

    private fun checkMilestone(player: Player, milestone: ProgressionManager.Milestone) {
        val completed = plugin.progressionManager.getCompletedMilestones(player)
        if (completed.contains(milestone.id)) return
        
        plugin.progressionManager.completeMilestone(player, milestone)
    }

    /**
     * Called externally when a quest is completed
     */
    fun onQuestComplete(player: Player) {
        // Track quest count
        val profile = plugin.identityManager.getPlayer(player.uniqueId) ?: return
        val questCount = (profile.settings["quest_count"]?.toString()?.toIntOrNull() ?: 0) + 1
        profile.settings["quest_count_$questCount"] = true
        
        if (questCount >= 3) {
            checkMilestone(player, ProgressionManager.Milestone.E0_QUESTS_3)
        }
    }

    /**
     * Called when joining a city
     */
    fun onCityJoin(player: Player) {
        checkMilestone(player, ProgressionManager.Milestone.E1_CITY)
    }

    /**
     * Called when earning gold through trading
     */
    fun onGoldEarned(player: Player, amount: Double) {
        val profile = plugin.identityManager.getPlayer(player.uniqueId) ?: return
        val totalEarned = (profile.settings["gold_earned"]?.toString()?.toDoubleOrNull() ?: 0.0) + amount
        // Can't store double in boolean map, so we track via milestones
        
        if (totalEarned >= 1000.0) {
            checkMilestone(player, ProgressionManager.Milestone.E1_GOLD_1K)
        }
    }

    /**
     * Called when city gets a new member
     */
    fun onCityMemberChange(player: Player, memberCount: Int) {
        if (memberCount >= 3) {
            checkMilestone(player, ProgressionManager.Milestone.E1_CITY_MEMBERS)
        }
    }

    /**
     * Called when infrastructure is built
     */
    fun onInfrastructureBuild(player: Player) {
        checkMilestone(player, ProgressionManager.Milestone.E1_INFRASTRUCTURE)
    }

    /**
     * Called when a dungeon is completed
     */
    fun onDungeonComplete(player: Player, isNightmare: Boolean = false) {
        checkMilestone(player, ProgressionManager.Milestone.E2_DUNGEON)
        if (isNightmare) {
            checkMilestone(player, ProgressionManager.Milestone.E3_NIGHTMARE)
        }
    }

    /**
     * Called when city survives a siege
     */
    fun onSiegeSurvive(player: Player, isWin: Boolean) {
        checkMilestone(player, ProgressionManager.Milestone.E2_SIEGE_SURVIVE)
        if (isWin) {
            checkMilestone(player, ProgressionManager.Milestone.E3_SIEGE_WIN)
        }
    }

    /**
     * Called when a relic is obtained
     */
    fun onRelicObtained(player: Player) {
        checkMilestone(player, ProgressionManager.Milestone.E2_RELIC)
    }

    /**
     * Called when city reaches tier 3
     */
    fun onCityTierReached(player: Player, tier: Int) {
        if (tier >= 3) {
            checkMilestone(player, ProgressionManager.Milestone.E3_CITY_TIER)
        }
    }

    /**
     * Called when dragon is killed
     */
    fun onDragonKilled(player: Player) {
        checkMilestone(player, ProgressionManager.Milestone.E4_DRAGON)
    }

    /**
     * Called when city treasury reaches threshold
     */
    fun onTreasuryMilestone(player: Player, amount: Double) {
        if (amount >= 10000.0) {
            checkMilestone(player, ProgressionManager.Milestone.E4_EMPIRE)
        }
    }

    /**
     * Called when player becomes mayor
     */
    fun onBecomeMayor(player: Player) {
        checkMilestone(player, ProgressionManager.Milestone.E4_MAYOR)
    }

    /**
     * Called when all dungeons are completed
     */
    fun onAllDungeonsComplete(player: Player) {
        checkMilestone(player, ProgressionManager.Milestone.E4_ALL_DUNGEONS)
    }

    /**
     * Called when all relics are collected
     */
    fun onAllRelicsCollected(player: Player) {
        checkMilestone(player, ProgressionManager.Milestone.E4_ALL_RELICS)
    }
}
