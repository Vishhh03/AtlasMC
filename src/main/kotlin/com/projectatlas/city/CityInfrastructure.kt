package com.projectatlas.city

/**
 * City Infrastructure - Upgradeable modules that provide bonuses
 */
data class CityInfrastructure(
    var wallLevel: Int = 0,        // 0-5, reduces damage during sieges
    var turretCount: Int = 0,      // Number of auto-turrets (max 4)
    var generatorLevel: Int = 0,   // 0-3, generates passive income
    var barracksLevel: Int = 0,    // 0-3, spawns defender NPCs during siege
    var marketLevel: Int = 0,      // 0-3, increases tax/trade revenue
    var clinicLevel: Int = 0,      // 0-3, passive regen for members
    var coreHealth: Int = 100      // City Core HP - if destroyed, city falls
) {
    companion object {
        // Upgrade costs
        val WALL_COSTS = listOf(0, 500, 1000, 2000, 4000, 8000)
        val TURRET_COST = 1500
        val GENERATOR_COSTS = listOf(0, 1000, 2500, 5000)
        val BARRACKS_COSTS = listOf(0, 2000, 4000, 8000)
        val MARKET_COSTS = listOf(0, 1500, 3000, 6000)
        val CLINIC_COSTS = listOf(0, 1500, 3000, 6000)
    }
    
    fun getWallUpgradeCost(): Int? = WALL_COSTS.getOrNull(wallLevel + 1)
    fun getGeneratorUpgradeCost(): Int? = GENERATOR_COSTS.getOrNull(generatorLevel + 1)
    fun getBarracksUpgradeCost(): Int? = BARRACKS_COSTS.getOrNull(barracksLevel + 1)
    fun getMarketUpgradeCost(): Int? = MARKET_COSTS.getOrNull(marketLevel + 1)
    fun getClinicUpgradeCost(): Int? = CLINIC_COSTS.getOrNull(clinicLevel + 1)
    
    fun getWallDamageReduction(): Double = wallLevel * 0.1 // 10% per level, max 50%
    fun getPassiveIncome(): Double = generatorLevel * 25.0 // 25g per level per cycle
    fun getDefenderCount(): Int = barracksLevel * 2 // 2 defenders per level
    fun getMarketTaxBonus(): Double = marketLevel * 0.05 // +5% revenue per level
    fun getClinicRegenAmplifier(): Int = if (clinicLevel > 0) clinicLevel - 1 else -1 // Lvl 1: Regen I (-1 is none, 0 is I)
    
    fun canAddTurret(): Boolean = turretCount < 4
    
    fun repairCore(amount: Int) {
        coreHealth = (coreHealth + amount).coerceAtMost(100)
    }
}
