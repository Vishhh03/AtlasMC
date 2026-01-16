package com.projectatlas.progression

import com.projectatlas.AtlasPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerPortalEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.inventory.ItemStack
import java.time.Duration

/**
 * Progression Manager - Handles the Era-based progression system.
 * 
 * Inspired by Elden Ring's gated content and Minecraft's dimension progression,
 * but with social/city requirements for multiplayer focus.
 */
class ProgressionManager(private val plugin: AtlasPlugin) : Listener {

    companion object {
        const val MAX_ERA = 4
    }

    /**
     * Era definitions with their requirements
     */
    enum class Era(
        val displayName: String,
        val color: NamedTextColor,
        val minLevel: Int,
        val icon: Material
    ) {
        AWAKENING("Awakening", NamedTextColor.GRAY, 1, Material.WOODEN_SWORD),
        SETTLEMENT("Settlement", NamedTextColor.GREEN, 5, Material.EMERALD),
        EXPEDITION("Expedition", NamedTextColor.RED, 15, Material.BLAZE_ROD),
        ASCENSION("Ascension", NamedTextColor.LIGHT_PURPLE, 30, Material.ENDER_EYE),
        LEGEND("Legend", NamedTextColor.GOLD, 50, Material.DRAGON_HEAD);

        fun next(): Era? = entries.getOrNull(ordinal + 1)
    }

    /**
     * Milestone definitions for each Era
     */
    enum class Milestone(
        val era: Era,
        val id: String,
        val displayName: String,
        val description: String
    ) {
        // Era 0: Awakening
        E0_LEVEL_5(Era.AWAKENING, "e0_level_5", "Reach Level 5", "Gain experience and reach level 5"),
        E0_QUESTS_3(Era.AWAKENING, "e0_quests_3", "Complete 3 Quests", "Finish any 3 wilderness quests"),
        E0_IRON_GEAR(Era.AWAKENING, "e0_iron_gear", "Iron Arsenal", "Craft a full set of iron armor and tools"),
        E0_SLEEP(Era.AWAKENING, "e0_sleep", "Established", "Sleep in a bed to set your respawn"),
        E0_BOSS(Era.AWAKENING, "e0_boss", "Hollow Knight Slain", "Defeat the Hollow Knight boss"),

        // Era 1: Settlement
        E1_CITY(Era.SETTLEMENT, "e1_city", "Citizen", "Join or found a city"),
        E1_GOLD_1K(Era.SETTLEMENT, "e1_gold_1k", "Merchant", "Earn 1,000 gold through trading"),
        E1_CITY_MEMBERS(Era.SETTLEMENT, "e1_city_members", "Community", "City has 3+ members"),
        E1_INFRASTRUCTURE(Era.SETTLEMENT, "e1_infrastructure", "Builder", "Construct first city infrastructure"),
        E1_LEVEL_15(Era.SETTLEMENT, "e1_level_15", "Reach Level 15", "Continue growing stronger"),
        E1_BOSS(Era.SETTLEMENT, "e1_boss", "Tax Collector Defeated", "Survive the Tax Collector raid"),

        // Era 2: Expedition
        E2_BLAZE_RODS(Era.EXPEDITION, "e2_blaze_rods", "Blaze Hunter", "Collect 3 Blaze Rods"),
        E2_DUNGEON(Era.EXPEDITION, "e2_dungeon", "Dungeon Delver", "Complete any dungeon"),
        E2_SIEGE_SURVIVE(Era.EXPEDITION, "e2_siege_survive", "Siege Survivor", "City survives a siege"),
        E2_LEVEL_30(Era.EXPEDITION, "e2_level_30", "Reach Level 30", "Master of combat"),
        E2_RELIC(Era.EXPEDITION, "e2_relic", "Relic Bearer", "Obtain your first relic"),
        E2_BOSS(Era.EXPEDITION, "e2_boss", "Warden of Flames Slain", "Defeat the Nether boss"),

        // Era 3: Ascension
        E3_EYES(Era.ASCENSION, "e3_eyes", "End Seeker", "Craft 12 Eyes of Ender"),
        E3_CITY_TIER(Era.ASCENSION, "e3_city_tier", "Metropolis", "City reaches Tier 3 (5+ infrastructure)"),
        E3_SIEGE_WIN(Era.ASCENSION, "e3_siege_win", "Conqueror", "Win a siege (attack or defense)"),
        E3_LEVEL_50(Era.ASCENSION, "e3_level_50", "Reach Level 50", "Approaching legend status"),
        E3_NIGHTMARE(Era.ASCENSION, "e3_nightmare", "Nightmare Cleared", "Complete a Nightmare dungeon"),
        E3_BOSS(Era.ASCENSION, "e3_boss", "Ender Sentinel Slain", "Defeat the End gateway boss"),

        // Era 4: Legend
        E4_DRAGON(Era.LEGEND, "e4_dragon", "Dragon Slayer", "Defeat the Ender Dragon"),
        E4_EMPIRE(Era.LEGEND, "e4_empire", "Trade Empire", "City treasury reaches 10,000 gold"),
        E4_MAYOR(Era.LEGEND, "e4_mayor", "Mayor", "Become mayor of a capital city"),
        E4_ALL_DUNGEONS(Era.LEGEND, "e4_all_dungeons", "Dungeon Master", "Complete all dungeon tiers"),
        E4_ALL_RELICS(Era.LEGEND, "e4_all_relics", "Collector", "Collect all relics");

        companion object {
            fun forEra(era: Era): List<Milestone> = entries.filter { it.era == era }
            fun fromId(id: String): Milestone? = entries.find { it.id == id }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // CITY-BASED PROGRESSION (Era is shared by all city members)
    // ══════════════════════════════════════════════════════════════

    /**
     * Get the era of a player based on their city.
     * Players without a city are stuck at Era 0 (Awakening).
     */
    fun getPlayerEra(player: Player): Era {
        val profile = plugin.identityManager.getPlayer(player.uniqueId) ?: return Era.AWAKENING
        val cityId = profile.cityId
        
        if (cityId == null) {
            return Era.entries.getOrNull(profile.soloEra) ?: Era.AWAKENING
        }
        
        val city = plugin.cityManager.getCity(cityId) ?: return Era.AWAKENING
        return Era.entries.getOrNull(city.currentEra) ?: Era.AWAKENING
    }

    /**
     * Get the era of a city directly.
     */
    fun getCityEra(city: com.projectatlas.city.City): Era {
        return Era.entries.getOrNull(city.currentEra) ?: Era.AWAKENING
    }

    /**
     * Get completed milestones for a city.
     */
    fun getCityMilestones(city: com.projectatlas.city.City): Set<String> {
        return city.completedMilestones.toSet()
    }

    /**
     * Get completed milestones for a player's city.
     */
    fun getCompletedMilestones(player: Player): Set<String> {
        val profile = plugin.identityManager.getPlayer(player.uniqueId) ?: return emptySet()
        val cityId = profile.cityId ?: return emptySet()
        val city = plugin.cityManager.getCity(cityId) ?: return emptySet()
        return city.completedMilestones.toSet()
    }

    fun completeMilestone(player: Player, milestone: Milestone): Boolean {
        val profile = plugin.identityManager.getPlayer(player.uniqueId) ?: return false
        val cityId = profile.cityId
        
        // Players without a city can only complete Era 0 milestones (solo achievements)
        if (cityId == null) {
            if (milestone.era != Era.AWAKENING) return false
            // Store in player profile for solo players
            return completeSoloMilestone(player, milestone)
        }
        
        val city = plugin.cityManager.getCity(cityId) ?: return false
        
        // Already completed by city?
        if (city.completedMilestones.contains(milestone.id)) return false
        
        // Add to city milestones
        city.completedMilestones.add(milestone.id)
        plugin.cityManager.saveCity(city)
        
        // Notify ALL city members
        city.members.forEach { memberUUID ->
            plugin.server.getPlayer(memberUUID)?.let { member ->
                member.sendMessage(Component.empty())
                member.sendMessage(Component.text("════════════════════════════════", milestone.era.color))
                member.sendMessage(Component.text("  ✦ CITY MILESTONE ✦", NamedTextColor.GOLD, TextDecoration.BOLD))
                member.sendMessage(Component.text("  ${milestone.displayName}", milestone.era.color))
                member.sendMessage(Component.text("  Achieved by: ${player.name}", NamedTextColor.GRAY))
                member.sendMessage(Component.text("════════════════════════════════", milestone.era.color))
                member.sendMessage(Component.empty())
                member.playSound(member.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f)
            }
        }
        
        // Check for city era advancement
        checkCityEraAdvancement(city)
        
        return true
    }

    private fun completeSoloMilestone(player: Player, milestone: Milestone): Boolean {
        // For players without a city, track Era 0 milestones in their profile
        // They cannot advance past Era 0 without a city
        player.sendMessage(Component.text("✦ Solo Achievement: ${milestone.displayName}", NamedTextColor.GRAY))
        player.sendMessage(Component.text("  Join a city to advance beyond Era 0!", NamedTextColor.YELLOW))
        return true
    }

    fun checkCityEraAdvancement(city: com.projectatlas.city.City) {
        val currentEra = getCityEra(city)
        val nextEra = currentEra.next() ?: return // Already max era
        
        val required = Milestone.forEra(currentEra)
        
        // Check if all milestones for current era are complete
        val allComplete = required.all { city.completedMilestones.contains(it.id) }
        
        if (allComplete) {
            advanceCityEra(city, nextEra)
        }
    }

    private fun advanceCityEra(city: com.projectatlas.city.City, newEra: Era) {
        val oldEra = getCityEra(city)
        
        // Update city era
        city.currentEra = newEra.ordinal
        plugin.cityManager.saveCity(city)
        
        // Rewards for each member
        val goldReward = when (newEra) {
            Era.SETTLEMENT -> 500.0
            Era.EXPEDITION -> 1000.0
            Era.ASCENSION -> 2000.0
            Era.LEGEND -> 5000.0
            else -> 0.0
        }
        
        val title = when (newEra) {
            Era.SETTLEMENT -> "Settler"
            Era.EXPEDITION -> "Explorer"
            Era.ASCENSION -> "Ascendant"
            Era.LEGEND -> "Legend"
            else -> null
        }
        
        // Notify ALL members and give rewards
        city.members.forEach { memberUUID ->
            val profile = plugin.identityManager.getPlayer(memberUUID)
            if (profile != null) {
                profile.balance += goldReward
                if (title != null && !profile.titles.contains(title)) {
                    profile.titles.add(title)
                }
                plugin.identityManager.saveProfile(memberUUID)
            }
            
            plugin.server.getPlayer(memberUUID)?.let { member ->
                member.showTitle(Title.title(
                    Component.text("ERA ${newEra.ordinal}", newEra.color, TextDecoration.BOLD),
                    Component.text("${city.name} enters ${newEra.displayName}!", NamedTextColor.WHITE),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))
                ))
                
                member.sendMessage(Component.empty())
                member.sendMessage(Component.text("CITY ERA REWARDS:", NamedTextColor.GOLD, TextDecoration.BOLD))
                member.sendMessage(Component.text("  + ${goldReward.toInt()} Gold", NamedTextColor.YELLOW))
                member.sendMessage(Component.text("  + \"$title\" Title", NamedTextColor.LIGHT_PURPLE))
                member.sendMessage(Component.empty())
                
                member.playSound(member.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.8f)
                member.playSound(member.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.2f)
            }
        }
        
        // Server-wide announcement
        plugin.server.broadcast(Component.text("════════════════════════════════", newEra.color))
        plugin.server.broadcast(Component.text("  ⚔ ${city.name} ADVANCES TO ERA ${newEra.ordinal}: ${newEra.displayName}! ⚔", newEra.color, TextDecoration.BOLD))
        plugin.server.broadcast(Component.text("════════════════════════════════", newEra.color))
    }

    // Legacy function for compatibility
    fun checkEraAdvancement(player: Player) {
        val profile = plugin.identityManager.getPlayer(player.uniqueId) ?: return
        val cityId = profile.cityId ?: return
        val city = plugin.cityManager.getCity(cityId) ?: return
        checkCityEraAdvancement(city)
    }


    // ══════════════════════════════════════════════════════════════
    // DIMENSION GATES
    // ══════════════════════════════════════════════════════════════

    @EventHandler
    fun onPortal(event: PlayerPortalEvent) {
        val player = event.player
        val era = getPlayerEra(player)

        when (event.cause) {
            PlayerTeleportEvent.TeleportCause.NETHER_PORTAL -> {
                if (era.ordinal < Era.EXPEDITION.ordinal) {
                    event.isCancelled = true
                    denyPortal(player, "Nether", Era.EXPEDITION)
                }
            }
            PlayerTeleportEvent.TeleportCause.END_PORTAL -> {
                if (era.ordinal < Era.ASCENSION.ordinal) {
                    event.isCancelled = true
                    denyPortal(player, "End", Era.ASCENSION)
                }
            }
            else -> {}
        }
    }

    private fun denyPortal(player: Player, dimension: String, requiredEra: Era) {
        player.sendMessage(Component.empty())
        player.sendMessage(Component.text("═══════════════════════════════════", NamedTextColor.DARK_RED))
        player.sendMessage(Component.text("  ✗ PORTAL SEALED", NamedTextColor.RED, TextDecoration.BOLD))
        player.sendMessage(Component.text("  You lack the power to traverse", NamedTextColor.GRAY))
        player.sendMessage(Component.text("  the $dimension gateway...", NamedTextColor.GRAY))
        player.sendMessage(Component.empty())
        player.sendMessage(Component.text("  Required: Era ${requiredEra.ordinal} (${requiredEra.displayName})", NamedTextColor.YELLOW))
        player.sendMessage(Component.text("═══════════════════════════════════", NamedTextColor.DARK_RED))
        player.sendMessage(Component.empty())

        player.playSound(player.location, Sound.BLOCK_ANVIL_LAND, 1.0f, 0.5f)
    }

    // ══════════════════════════════════════════════════════════════
    // FEATURE LOCKS
    // ══════════════════════════════════════════════════════════════

    fun canAccessSkillTree(player: Player): Boolean {
        return getPlayerEra(player).ordinal >= Era.SETTLEMENT.ordinal
    }

    fun canAccessDungeons(player: Player): Boolean {
        return getPlayerEra(player).ordinal >= Era.EXPEDITION.ordinal
    }

    fun canTriggerSiege(player: Player): Boolean {
        return getPlayerEra(player).ordinal >= Era.EXPEDITION.ordinal
    }

    fun canAccessRelics(player: Player): Boolean {
        val completed = getCompletedMilestones(player)
        return completed.contains(Milestone.E2_DUNGEON.id)
    }

    fun canFormAlliance(player: Player): Boolean {
        return getPlayerEra(player).ordinal >= Era.ASCENSION.ordinal
    }

    // ══════════════════════════════════════════════════════════════
    // MOB SCALING BY ERA
    // ══════════════════════════════════════════════════════════════

    /**
     * Get mob health multiplier based on closest player's era
     */
    fun getMobHealthMultiplier(era: Era): Double {
        return when (era) {
            Era.AWAKENING -> 1.0    // 100%
            Era.SETTLEMENT -> 1.2   // 120%
            Era.EXPEDITION -> 1.5   // 150%
            Era.ASCENSION -> 2.0    // 200%
            Era.LEGEND -> 2.5       // 250%
        }
    }

    /**
     * Get mob damage multiplier based on closest player's era
     */
    fun getMobDamageMultiplier(era: Era): Double {
        return when (era) {
            Era.AWAKENING -> 1.0    // 100%
            Era.SETTLEMENT -> 1.1   // 110%
            Era.EXPEDITION -> 1.3   // 130%
            Era.ASCENSION -> 1.5    // 150%
            Era.LEGEND -> 1.75      // 175%
        }
    }

    /**
     * Get the highest era among nearby players (for scaling)
     */
    fun getHighestNearbyPlayerEra(location: org.bukkit.Location, radius: Double = 50.0): Era {
        val nearbyPlayers = location.world.getNearbyPlayers(location, radius)
        return nearbyPlayers.maxOfOrNull { getPlayerEra(it).ordinal }
            ?.let { Era.entries.getOrNull(it) } ?: Era.AWAKENING
    }

    // ══════════════════════════════════════════════════════════════
    // PROGRESS GUI
    // ══════════════════════════════════════════════════════════════

    fun openProgressGUI(player: Player) {
        val profile = plugin.identityManager.getPlayer(player.uniqueId)
        val cityId = profile?.cityId
        val city = cityId?.let { plugin.cityManager.getCity(it) }
        
        val guiTitle = if (city != null) {
            "⚔ ${city.name}'s Journey"
        } else {
            "⚔ YOUR JOURNEY (No City)"
        }
        
        val inv = Bukkit.createInventory(null, 54, Component.text(guiTitle, NamedTextColor.GOLD, TextDecoration.BOLD))

        val currentEra = getPlayerEra(player)
        val completed = getCompletedMilestones(player)
        
        // City info (slot 4)
        if (city != null) {
            inv.setItem(4, ItemStack(Material.EMERALD_BLOCK).apply {
                editMeta { meta ->
                    meta.displayName(Component.text("${city.name}", NamedTextColor.GREEN, TextDecoration.BOLD))
                    meta.lore(listOf(
                        Component.text("Era ${city.currentEra}: ${currentEra.displayName}", currentEra.color),
                        Component.text("Members: ${city.members.size}", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("All city members share this era!", NamedTextColor.YELLOW)
                    ))
                }
            })
        } else {
            inv.setItem(4, ItemStack(Material.BARRIER).apply {
                editMeta { meta ->
                    meta.displayName(Component.text("No City!", NamedTextColor.RED, TextDecoration.BOLD))
                    meta.lore(listOf(
                        Component.text("You are stuck at Era 0", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("Join or create a city to", NamedTextColor.YELLOW),
                        Component.text("advance beyond Awakening!", NamedTextColor.YELLOW),
                        Component.empty(),
                        Component.text("/atlas city create <name>", NamedTextColor.AQUA)
                    ))
                }
            })
        }

        // Era display (row 2)
        Era.entries.forEachIndexed { index, era ->
            val isComplete = era.ordinal < currentEra.ordinal
            val isCurrent = era == currentEra

            val item = ItemStack(if (isComplete) Material.LIME_STAINED_GLASS_PANE 
                                 else if (isCurrent) era.icon 
                                 else Material.GRAY_STAINED_GLASS_PANE)

            item.editMeta { meta ->
                val color = when {
                    isComplete -> NamedTextColor.GREEN
                    isCurrent -> era.color
                    else -> NamedTextColor.DARK_GRAY
                }

                meta.displayName(Component.text("Era ${era.ordinal}: ${era.displayName}", color, TextDecoration.BOLD))

                val lore = mutableListOf<Component>()
                lore.add(Component.text("Min Level: ${era.minLevel}", NamedTextColor.GRAY))
                lore.add(Component.empty())

                if (isComplete) {
                    lore.add(Component.text("✓ COMPLETED", NamedTextColor.GREEN))
                } else if (isCurrent) {
                    lore.add(Component.text("◉ CURRENT ERA", NamedTextColor.YELLOW))
                } else {
                    lore.add(Component.text("✗ LOCKED", NamedTextColor.RED))
                }

                meta.lore(lore)
            }

            inv.setItem(9 + index, item) // Row 2
        }

        // Milestones for current era (row 3-4)
        val currentMilestones = Milestone.forEra(currentEra)
        currentMilestones.forEachIndexed { index, milestone ->
            val isComplete = completed.contains(milestone.id)
            val slot = 18 + index // Row 3

            val item = ItemStack(if (isComplete) Material.LIME_DYE else Material.GRAY_DYE)
            item.editMeta { meta ->
                val color = if (isComplete) NamedTextColor.GREEN else NamedTextColor.YELLOW
                meta.displayName(Component.text(milestone.displayName, color, TextDecoration.BOLD))

                val lore = mutableListOf<Component>()
                lore.add(Component.text(milestone.description, NamedTextColor.GRAY))
                lore.add(Component.empty())

                if (isComplete) {
                    lore.add(Component.text("✓ COMPLETE", NamedTextColor.GREEN))
                } else {
                    lore.add(Component.text("○ In Progress", NamedTextColor.YELLOW))
                }

                meta.lore(lore)
            }

            inv.setItem(slot, item)
        }

        // Progress bar (bottom)
        val totalMilestones = Milestone.entries.size
        val completedCount = completed.size
        val progressPercent = ((completedCount.toDouble() / totalMilestones) * 100).toInt()

        inv.setItem(49, ItemStack(Material.EXPERIENCE_BOTTLE).apply {
            editMeta { meta ->
                meta.displayName(Component.text("Overall Progress: $progressPercent%", NamedTextColor.GOLD, TextDecoration.BOLD))
                val lore = mutableListOf<Component>(
                    Component.text("Milestones: $completedCount / $totalMilestones", NamedTextColor.GRAY),
                    Component.text("Current Era: ${currentEra.displayName}", currentEra.color)
                )
                if (city == null) {
                    lore.add(Component.empty())
                    lore.add(Component.text("⚠ Join a city to progress!", NamedTextColor.RED))
                }
                meta.lore(lore)
            }
        })

        player.openInventory(inv)
    }

    fun resetPlayer(player: Player) {
        val profile = plugin.identityManager.getPlayer(player.uniqueId) ?: return
        
        // Reset Level & XP
        profile.level = 1
        profile.currentXp = 0
        player.level = 1
        player.exp = 0.0f
        
        // Reset Titles
        profile.titles.clear()
        
        // Reset Balance (optional, but consistent with full reset)
        profile.balance = 100.0
        
        plugin.identityManager.saveProfile(player.uniqueId)
        
        player.sendMessage(Component.text("Your progression has been reset by an admin.", NamedTextColor.RED))
        player.playSound(player.location, Sound.ENTITY_WITHER_DEATH, 1.0f, 2.0f)
    }
}
