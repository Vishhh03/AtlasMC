package com.projectatlas.city

/**
 * City Infrastructure - Upgradeable modules that provide bonuses
 * Enhanced for the City Siege System with balanced costs and effects
 */
data class CityInfrastructure(
    // ═══════════════════════════════════════════════════════════════
    // DEFENSIVE INFRASTRUCTURE
    // ═══════════════════════════════════════════════════════════════
    var wallLevel: Int = 0,           // 0-5, reduces damage during sieges (10% per level)
    var turretCount: Int = 0,         // Number of auto-turrets (max 4), fires at siege mobs
    var barracksLevel: Int = 0,       // 0-3, spawns Iron Golem defenders (2 per level)
    var watchtowerLevel: Int = 0,     // 0-3, increases siege warning time + grants vision
    var trapSystemLevel: Int = 0,     // 0-3, slows/damages siege mobs in city territory
    
    // ═══════════════════════════════════════════════════════════════
    // ECONOMIC INFRASTRUCTURE
    // ═══════════════════════════════════════════════════════════════
    var generatorLevel: Int = 0,      // 0-3, generates passive gold income
    var marketLevel: Int = 0,         // 0-3, increases tax/trade revenue
    
    // ═══════════════════════════════════════════════════════════════
    // SUPPORT INFRASTRUCTURE
    // ═══════════════════════════════════════════════════════════════
    var clinicLevel: Int = 0,         // 0-3, passive regen for members
    var healingBeaconLevel: Int = 0,  // 0-3, heals defenders during siege
    var armoryLevel: Int = 0,         // 0-3, defenders gain armor toughness
    var forgeLevel: Int = 0,          // 0-3, defenders deal bonus damage
    
    // ═══════════════════════════════════════════════════════════════
    // CITY CORE
    // ═══════════════════════════════════════════════════════════════
    var coreHealth: Int = 100,        // City Core HP - if destroyed, city falls
    var maxCoreHealth: Int = 100      // Max core health (can be upgraded)
) {
    companion object {
        // ═══════════════════════════════════════════════════════════════
        // UPGRADE COSTS (Balanced progression)
        // ═══════════════════════════════════════════════════════════════
        
        // Defensive
        val WALL_COSTS = listOf(0, 500, 1000, 2000, 4000, 8000)        // L1-L5
        val TURRET_COST = 1500                                          // Per turret (max 4)
        val BARRACKS_COSTS = listOf(0, 2000, 4000, 8000)               // L1-L3
        val WATCHTOWER_COSTS = listOf(0, 1500, 3500, 7000)             // L1-L3
        val TRAP_SYSTEM_COSTS = listOf(0, 1000, 2500, 5000)            // L1-L3
        
        // Economic
        val GENERATOR_COSTS = listOf(0, 1000, 2500, 5000)              // L1-L3
        val MARKET_COSTS = listOf(0, 1500, 3000, 6000)                 // L1-L3
        
        // Support
        val CLINIC_COSTS = listOf(0, 1500, 3000, 6000)                 // L1-L3
        val HEALING_BEACON_COSTS = listOf(0, 2000, 4000, 8000)         // L1-L3
        val ARMORY_COSTS = listOf(0, 2000, 4000, 8000)                 // L1-L3
        val FORGE_COSTS = listOf(0, 2500, 5000, 10000)                 // L1-L3
        
        // ═══════════════════════════════════════════════════════════════
        // SIEGE DAMAGE VALUES
        // ═══════════════════════════════════════════════════════════════
        val TURRET_DAMAGE = 5.0           // Damage per shot
        val TURRET_FIRE_RATE_TICKS = 40L  // Fire every 2 seconds
        val TRAP_DAMAGE_PER_LEVEL = 2.0   // Damage per tick per level
        val TRAP_SLOW_AMPLIFIER = 1       // Slowness II per level
        val HEALING_BEACON_AMOUNT = 2.0   // HP per tick per level
    }
    
    // ═══════════════════════════════════════════════════════════════
    // UPGRADE COST GETTERS
    // ═══════════════════════════════════════════════════════════════
    fun getWallUpgradeCost(): Int? = WALL_COSTS.getOrNull(wallLevel + 1)
    fun getGeneratorUpgradeCost(): Int? = GENERATOR_COSTS.getOrNull(generatorLevel + 1)
    fun getBarracksUpgradeCost(): Int? = BARRACKS_COSTS.getOrNull(barracksLevel + 1)
    fun getMarketUpgradeCost(): Int? = MARKET_COSTS.getOrNull(marketLevel + 1)
    fun getClinicUpgradeCost(): Int? = CLINIC_COSTS.getOrNull(clinicLevel + 1)
    fun getArmoryUpgradeCost(): Int? = ARMORY_COSTS.getOrNull(armoryLevel + 1)
    fun getForgeUpgradeCost(): Int? = FORGE_COSTS.getOrNull(forgeLevel + 1)
    fun getWatchtowerUpgradeCost(): Int? = WATCHTOWER_COSTS.getOrNull(watchtowerLevel + 1)
    fun getHealingBeaconUpgradeCost(): Int? = HEALING_BEACON_COSTS.getOrNull(healingBeaconLevel + 1)
    fun getTrapSystemUpgradeCost(): Int? = TRAP_SYSTEM_COSTS.getOrNull(trapSystemLevel + 1)
    
    // ═══════════════════════════════════════════════════════════════
    // INFRASTRUCTURE EFFECTS
    // ═══════════════════════════════════════════════════════════════
    
    // WALL: Reduces damage to City Core
    fun getWallDamageReduction(): Double = wallLevel * 0.10  // 10% per level, max 50%
    
    // GENERATOR: Passive gold income
    fun getPassiveIncome(): Double = generatorLevel * 25.0   // 25g per level per cycle
    
    // BARRACKS: Number of Iron Golem defenders spawned
    fun getDefenderCount(): Int = barracksLevel * 2          // 2/4/6 defenders
    
    // MARKET: Tax revenue bonus
    fun getMarketTaxBonus(): Double = marketLevel * 0.05     // +5%/+10%/+15% revenue
    
    // CLINIC: Regeneration effect amplifier
    fun getClinicRegenAmplifier(): Int = if (clinicLevel > 0) clinicLevel - 1 else -1
    
    // WATCHTOWER: Siege detection range (in chunks)
    fun getWatchtowerRange(): Int = watchtowerLevel * 2      // 2/4/6 chunk radius
    
    // WATCHTOWER: Early warning time before siege starts (in seconds)
    fun getSiegeWarningTime(): Int = watchtowerLevel * 10    // 10/20/30 seconds
    
    // TRAP SYSTEM: Damage per tick to siege mobs
    fun getTrapDamage(): Double = trapSystemLevel * TRAP_DAMAGE_PER_LEVEL
    
    // TRAP SYSTEM: Slowness amplifier (0 = Slowness I, 1 = Slowness II, etc.)
    fun getTrapSlowAmplifier(): Int = (trapSystemLevel - 1).coerceAtLeast(0)
    
    // HEALING BEACON: HP healed per tick to defenders
    fun getHealingBeaconAmount(): Double = healingBeaconLevel * HEALING_BEACON_AMOUNT
    
    // ARMORY: Armor toughness bonus for defenders
    fun getDefenderArmorBonus(): Double = armoryLevel * 2.0  // +2/+4/+6 toughness
    
    // FORGE: Damage multiplier for defenders
    fun getDefenderDamageMultiplier(): Double = 1.0 + (forgeLevel * 0.10)  // +10%/+20%/+30%
    
    // ═══════════════════════════════════════════════════════════════
    // TURRET MANAGEMENT
    // ═══════════════════════════════════════════════════════════════
    fun canAddTurret(): Boolean = turretCount < 4
    fun getTotalTurretDPS(): Double = turretCount * (TURRET_DAMAGE / (TURRET_FIRE_RATE_TICKS / 20.0))
    
    // ═══════════════════════════════════════════════════════════════
    // CORE MANAGEMENT
    // ═══════════════════════════════════════════════════════════════
    fun repairCore(amount: Int) {
        coreHealth = (coreHealth + amount).coerceAtMost(maxCoreHealth)
    }
    
    fun damageCore(rawDamage: Int): Int {
        val reduction = getWallDamageReduction()
        val finalDamage = ((rawDamage * (1.0 - reduction)).toInt()).coerceAtLeast(1)
        coreHealth = (coreHealth - finalDamage).coerceAtLeast(0)
        return finalDamage
    }
    
    fun isCoreDestroyed(): Boolean = coreHealth <= 0
    
    fun getCoreHealthPercent(): Double = coreHealth.toDouble() / maxCoreHealth.toDouble()
    
    // ═══════════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════════
    fun getTotalInfrastructureValue(): Int {
        var total = 0
        for (i in 1..wallLevel) total += WALL_COSTS.getOrElse(i) { 0 }
        for (i in 1..generatorLevel) total += GENERATOR_COSTS.getOrElse(i) { 0 }
        for (i in 1..barracksLevel) total += BARRACKS_COSTS.getOrElse(i) { 0 }
        for (i in 1..marketLevel) total += MARKET_COSTS.getOrElse(i) { 0 }
        for (i in 1..clinicLevel) total += CLINIC_COSTS.getOrElse(i) { 0 }
        for (i in 1..armoryLevel) total += ARMORY_COSTS.getOrElse(i) { 0 }
        for (i in 1..forgeLevel) total += FORGE_COSTS.getOrElse(i) { 0 }
        for (i in 1..watchtowerLevel) total += WATCHTOWER_COSTS.getOrElse(i) { 0 }
        for (i in 1..healingBeaconLevel) total += HEALING_BEACON_COSTS.getOrElse(i) { 0 }
        for (i in 1..trapSystemLevel) total += TRAP_SYSTEM_COSTS.getOrElse(i) { 0 }
        total += turretCount * TURRET_COST
        return total
    }
    
    fun getDefenseRating(): Int {
        return (wallLevel * 20) + 
               (turretCount * 15) + 
               (barracksLevel * 25) + 
               (watchtowerLevel * 10) + 
               (trapSystemLevel * 15) +
               (healingBeaconLevel * 10) +
               (armoryLevel * 10) +
               (forgeLevel * 10)
    }
}
