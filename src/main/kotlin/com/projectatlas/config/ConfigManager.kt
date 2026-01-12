package com.projectatlas.config

import com.projectatlas.AtlasPlugin
import org.bukkit.Material

class ConfigManager(private val plugin: AtlasPlugin) {

    // Economy
    var startingBalance: Double = 100.0
    var marketplaceFeePercent: Int = 10
    
    // Supply Drop
    var supplyDropEnabled: Boolean = true
    var supplyDropIntervalMinutes: Int = 30
    var supplyDropRadius: Int = 500
    
    // World Boss
    var worldBossEnabled: Boolean = true
    var worldBossCooldownMinutes: Int = 30
    var worldBossRelicDropChance: Double = 0.25
    
    // Relic Spawn
    var relicSpawnEnabled: Boolean = true
    var relicSpawnCheckMinutes: Int = 10
    var relicSpawnChance: Double = 0.05
    
    // City
    var cityCreationCost: Double = 1000.0
    var chunkClaimBaseCost: Double = 100.0
    var chunkClaimScaling: Double = 1.5
    var cityMaxChunks: Int = 50
    var cityMaxMembers: Int = 20
    
    // Classes
    var classChangeCooldownHours: Int = 24
    var abilityCooldownSeconds: Int = 30
    
    // Dungeons
    var dungeonMaxSize: Int = 64
    var dungeonTimeBonusThreshold: Double = 0.5
    var dungeonSpeedBonusMultiplier: Double = 1.5
    var dungeonRelicDropChance: Double = 0.20
    
    // Party
    var partyMaxSize: Int = 4
    var partyInviteTimeoutSeconds: Int = 60
    
    // Bounty
    var bountyMinimum: Double = 50.0
    var bountyMaximum: Double = 100000.0
    
    // Blueprints
    var blueprintMaxDimension: Int = 64
    var blueprintMaxBlocks: Int = 50000
    var blueprintMinBlocks: Int = 5
    var blueprintMinPrice: Double = 10.0
    var blueprintCreatorRevenue: Int = 90
    
    // Relic Cooldowns (seconds)
    var relicCooldowns = mutableMapOf<String, Int>()
    
    // Menu
    var menuTriggerItem: Material = Material.COMPASS
    var menuTriggerSneaking: Boolean = false
    
    // Daily Rewards
    var dailyRewardsEnabled: Boolean = true
    var dailyRewards = mutableMapOf<Int, Int>()
    
    // Debug
    var debugEnabled: Boolean = false
    var verboseLogging: Boolean = false

    fun loadConfig() {
        plugin.saveDefaultConfig()
        plugin.reloadConfig()
        val config = plugin.config
        
        // Economy
        startingBalance = config.getDouble("economy.starting-balance", 100.0)
        marketplaceFeePercent = config.getInt("economy.marketplace-fee-percent", 10)
        
        // Supply Drop
        supplyDropEnabled = config.getBoolean("events.supply-drop.enabled", true)
        supplyDropIntervalMinutes = config.getInt("events.supply-drop.interval-minutes", 30)
        supplyDropRadius = config.getInt("events.supply-drop.radius", 500)
        
        // World Boss
        worldBossEnabled = config.getBoolean("events.world-boss.enabled", true)
        worldBossCooldownMinutes = config.getInt("events.world-boss.cooldown-minutes", 30)
        worldBossRelicDropChance = config.getDouble("events.world-boss.relic-drop-chance", 0.25)
        
        // Relic Spawn
        relicSpawnEnabled = config.getBoolean("events.relic-spawn.enabled", true)
        relicSpawnCheckMinutes = config.getInt("events.relic-spawn.check-interval-minutes", 10)
        relicSpawnChance = config.getDouble("events.relic-spawn.spawn-chance", 0.05)
        
        // City
        cityCreationCost = config.getDouble("city.creation-cost", 1000.0)
        chunkClaimBaseCost = config.getDouble("city.chunk-claim-base-cost", 100.0)
        chunkClaimScaling = config.getDouble("city.chunk-claim-scaling", 1.5)
        cityMaxChunks = config.getInt("city.max-chunks", 50)
        cityMaxMembers = config.getInt("city.max-members", 20)
        
        // Classes
        classChangeCooldownHours = config.getInt("classes.change-cooldown-hours", 24)
        abilityCooldownSeconds = config.getInt("classes.ability-cooldown-seconds", 30)
        
        // Dungeons
        dungeonMaxSize = config.getInt("dungeons.max-size-blocks", 64)
        dungeonTimeBonusThreshold = config.getDouble("dungeons.time-bonus-threshold", 0.5)
        dungeonSpeedBonusMultiplier = config.getDouble("dungeons.speed-bonus-multiplier", 1.5)
        dungeonRelicDropChance = config.getDouble("dungeons.relic-drop-chance", 0.20)
        
        // Party
        partyMaxSize = config.getInt("party.max-size", 4)
        partyInviteTimeoutSeconds = config.getInt("party.invite-timeout-seconds", 60)
        
        // Bounty
        bountyMinimum = config.getDouble("bounty.minimum-amount", 50.0)
        bountyMaximum = config.getDouble("bounty.maximum-amount", 100000.0)
        
        // Blueprints
        blueprintMaxDimension = config.getInt("blueprints.max-dimension", 64)
        blueprintMaxBlocks = config.getInt("blueprints.max-blocks", 50000)
        blueprintMinBlocks = config.getInt("blueprints.minimum-blocks", 5)
        blueprintMinPrice = config.getDouble("blueprints.minimum-price", 10.0)
        blueprintCreatorRevenue = config.getInt("blueprints.creator-revenue-percent", 90)
        
        // Relic Cooldowns
        val cooldownSection = config.getConfigurationSection("relics.cooldowns")
        if (cooldownSection != null) {
            cooldownSection.getKeys(false).forEach { key ->
                relicCooldowns[key] = cooldownSection.getInt(key)
            }
        }
        
        // Menu
        val itemName = config.getString("menu.trigger-item", "COMPASS")
        menuTriggerItem = Material.matchMaterial(itemName ?: "COMPASS") ?: Material.COMPASS
        menuTriggerSneaking = config.getBoolean("menu.require-sneaking", false)
        
        // Daily Rewards
        dailyRewardsEnabled = config.getBoolean("daily-rewards.enabled", true)
        val rewardsSection = config.getConfigurationSection("daily-rewards.rewards")
        if (rewardsSection != null) {
            rewardsSection.getKeys(false).forEach { key ->
                val day = key.removePrefix("day-").toIntOrNull() ?: return@forEach
                dailyRewards[day] = rewardsSection.getInt(key)
            }
        }
        
        // Debug
        debugEnabled = config.getBoolean("debug.enabled", false)
        verboseLogging = config.getBoolean("debug.verbose-logging", false)
        
        plugin.logger.info("Configuration loaded successfully!")
        if (debugEnabled) {
            plugin.logger.info("[DEBUG] Starting balance: $startingBalance")
            plugin.logger.info("[DEBUG] Party max size: $partyMaxSize")
            plugin.logger.info("[DEBUG] Daily rewards: $dailyRewards")
        }
    }
    
    fun getChunkClaimCost(currentChunks: Int): Double {
        return chunkClaimBaseCost * java.lang.Math.pow(chunkClaimScaling, currentChunks.toDouble().coerceAtMost(10.0))
    }
    
    fun getRelicCooldown(relicName: String): Int {
        return relicCooldowns[relicName.lowercase().replace(" ", "-").replace("'", "")] ?: 60
    }
    
    fun getDailyReward(dayStreak: Int): Int {
        // Cap at day 7, then repeat
        val day = ((dayStreak - 1) % 7) + 1
        return dailyRewards[day] ?: 50
    }
}
