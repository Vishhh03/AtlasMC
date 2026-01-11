package com.projectatlas.achievements

import com.projectatlas.AtlasPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Sound
import org.bukkit.entity.Player
import java.util.UUID

class AchievementManager(private val plugin: AtlasPlugin) {

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
        "veteran" to Achievement("Veteran", "Play for 10 hours", 500.0)
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
}
