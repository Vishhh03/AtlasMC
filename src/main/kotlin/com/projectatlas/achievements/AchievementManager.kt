package com.projectatlas.achievements

import com.projectatlas.AtlasPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Sound
import org.bukkit.entity.Player
import java.util.UUID

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class AchievementManager(private val plugin: AtlasPlugin) : Listener {

    // Achievement definitions
    private val achievements = mapOf(
        "first_blood" to Achievement("First Blood", "Kill your first mob", 50.0),
        "city_founder" to Achievement("City Founder", "Create your first city", 200.0),
        "class_chosen" to Achievement("Class Chosen", "Select your first class", 100.0),
        "penny_pincher" to Achievement("Penny Pincher", "Save up 1000 gold", 150.0),
        "social_butterfly" to Achievement("Social Butterfly", "Join a city", 75.0),
        "land_owner" to Achievement("Land Owner", "Claim your first chunk", 50.0),
        "tax_collector" to Achievement("Tax Collector", "Deposit 500 gold to treasury", 100.0),
        "supply_hunter" to Achievement("Supply Hunter", "Loot a supply drop chest", 200.0),
        "explorer" to Achievement("Explorer", "Travel 10,000 blocks", 150.0),
        "veteran" to Achievement("Veteran", "Play for 10 hours", 500.0),
        "quest_complete" to Achievement("Quest Master", "Complete your first quest", 100.0),
        "dungeon_conqueror" to Achievement("Dungeon Conqueror", "Complete your first dungeon", 300.0),
        "boss_slayer" to Achievement("Boss Slayer", "Contribute to a World Boss kill", 250.0),
        "relic_hunter" to Achievement("Relic Hunter", "Find your first ancient relic", 200.0),
        "bounty_hunter" to Achievement("Bounty Hunter", "Claim your first bounty", 300.0),
        "wanted" to Achievement("Wanted", "Have a bounty placed on you", 50.0),
        "level_10" to Achievement("Rising Star", "Reach Level 10", 500.0),
        "level_25" to Achievement("Seasoned Adventurer", "Reach Level 25", 1000.0),
        "siege_defender" to Achievement("Siege Defender", "Successfully defend against a siege", 400.0),
        "mayor" to Achievement("Mayor", "Become the mayor of a city", 250.0),
        "rich" to Achievement("Rich", "Accumulate 10,000 gold", 500.0),
        "ability_master" to Achievement("Ability Master", "Use your class ability 50 times", 200.0),
        "dungeon_speed" to Achievement("Speed Runner", "Complete a dungeon with speed bonus", 400.0),
        "dungeon_nightmare" to Achievement("Nightmare Survivor", "Complete a Nightmare difficulty dungeon", 1000.0)
    )

    data class Achievement(
        val name: String,
        val description: String,
        val reward: Double
    )

    /**
     * Award an achievement if not already earned
     */
    fun awardAchievement(player: Player, achievementId: String) {
        val profile = plugin.identityManager.getPlayer(player.uniqueId) ?: return
        val achievement = achievements[achievementId] ?: return
        
        // Check if already earned (stored in titles for now)
        val achievementKey = "achievement:$achievementId"
        if (profile.titles.contains(achievementKey)) return
        
        // Award it
        profile.titles.add(achievementKey)
        profile.balance += achievement.reward
        plugin.identityManager.saveProfile(player.uniqueId)
        
        // Notify player with fanfare
        player.sendMessage(Component.empty())
        player.sendMessage(Component.text("★ ACHIEVEMENT UNLOCKED ★", NamedTextColor.GOLD).decorate(TextDecoration.BOLD))
        player.sendMessage(Component.text(achievement.name, NamedTextColor.YELLOW))
        player.sendMessage(Component.text(achievement.description, NamedTextColor.GRAY))
        player.sendMessage(Component.text("+${achievement.reward}g", NamedTextColor.GREEN))
        player.sendMessage(Component.empty())
        
        // Sound effects
        player.playSound(player.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f)
    }

    /**
     * Check if player has earned an achievement
     */
    fun hasAchievement(player: Player, achievementId: String): Boolean {
        val profile = plugin.identityManager.getPlayer(player.uniqueId) ?: return false
        return profile.titles.contains("achievement:$achievementId")
    }

    /**
     * Get all achievements with earned status for a player
     */
    fun getAchievementsForPlayer(player: Player): List<Pair<Achievement, Boolean>> {
        val profile = plugin.identityManager.getPlayer(player.uniqueId)
        return achievements.map { (id, ach) ->
            ach to (profile?.titles?.contains("achievement:$id") ?: false)
        }
    }
    
    /**
     * Get total earned and total possible
     */
    fun getProgress(player: Player): Pair<Int, Int> {
        val profile = plugin.identityManager.getPlayer(player.uniqueId)
        val earned = achievements.keys.count { profile?.titles?.contains("achievement:$it") ?: false }
        return earned to achievements.size
    }

    @EventHandler
    fun onDungeonComplete(event: com.projectatlas.events.DungeonCompleteEvent) {
        if (!event.success) return
        
        awardAchievement(event.player, "dungeon_conqueror")
        
        if (event.difficulty >= 5) {
            awardAchievement(event.player, "dungeon_nightmare")
        }
    }
}
