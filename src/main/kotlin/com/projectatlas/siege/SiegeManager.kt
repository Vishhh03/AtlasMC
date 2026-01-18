package com.projectatlas.siege

import com.projectatlas.AtlasPlugin
import com.projectatlas.city.City
import com.projectatlas.city.CitySpecialization
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.block.Action
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.attribute.Attribute
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitTask
import com.projectatlas.history.EventType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Siege Manager - Complete City Siege System
 * 
 * Features:
 * - 5-wave siege with scaling difficulty
 * - Multiple mob types (Grunt, Archer, Breacher, Sapper, Commander)
 * - Infrastructure integration (walls, turrets, barracks, etc.)
 * - Tiered siege banners for different difficulties
 * - Balanced rewards and penalties
 */
class SiegeManager(private val plugin: AtlasPlugin) : Listener {

    private val activeSieges = ConcurrentHashMap<String, ActiveSiege>()
    private val siegeTasks = ConcurrentHashMap<String, MutableList<BukkitTask>>()
    
    companion object {
        // ═══════════════════════════════════════════════════════════════
        // SIEGE CONFIGURATION
        // ═══════════════════════════════════════════════════════════════
        const val SIEGE_COOLDOWN_MS = 2 * 60 * 60 * 1000L  // 2 hours between sieges
        const val POST_SIEGE_PROTECTION_MS = 8 * 60 * 60 * 1000L  // 8 hours protection
        const val PER_ATTACKER_COOLDOWN_MS = 24 * 60 * 60 * 1000L // 24h per-attacker cooldown
        
        const val WAVES_PER_SIEGE = 5
        const val MOBS_PER_WAVE_BASE = 3  // Reduced from 5 for better balance
        const val WAVE_DELAY_TICKS = 200L // 10 seconds between waves
        
        // ═══════════════════════════════════════════════════════════════
        // PREPARATION PHASE (Anti-Exploit)
        // ═══════════════════════════════════════════════════════════════
        const val PREP_PHASE_SECONDS = 600  // 10 minutes preparation time
        const val PAUSE_TIMEOUT_SECONDS = 300 // 5 min grace period if all defenders DC
        const val MIN_DEFENDER_ONLINE = 1     // Must have at least 1 defender online
        const val ATTACKER_ERA_REQUIREMENT = 1 // Must be Era 1+ to trigger siege
        
        // Wave scaling multipliers (mob count)
        val WAVE_MOB_SCALING = listOf(1.0, 1.2, 1.4, 1.6, 2.5)
        // Wave health scaling
        val WAVE_HEALTH_SCALING = listOf(1.0, 1.1, 1.2, 1.3, 1.5)
        
        // ═══════════════════════════════════════════════════════════════
        // REWARD CONFIGURATION
        // ═══════════════════════════════════════════════════════════════
        const val BASE_REWARD = 500.0
        const val WAVE_BONUS = 100.0
        const val FLAWLESS_BONUS = 250.0      // If core takes 0 damage
        const val PER_DEFENDER_BONUS = 50.0   // Bonus per online defender
        const val EMPTY_CITY_REWARD_CAP = 50.0 // Max if no defenders during battle
        
        // ═══════════════════════════════════════════════════════════════
        // PENALTY CONFIGURATION (Reduced for fairness)
        // ═══════════════════════════════════════════════════════════════
        const val TREASURY_LOSS_PERCENT = 0.10     // 10% treasury loss (was 15%)
        const val TREASURY_LOSS_DISCONNECT = 0.05  // 5% if disconnect timeout
        const val CORE_DAMAGE_BASE = 15            // Reduced from 25
        const val INFRASTRUCTURE_DAMAGE_CHANCE = 0.15 // 15% chance (was 20%)
        
        // ═══════════════════════════════════════════════════════════════
        // MOB STATS
        // ═══════════════════════════════════════════════════════════════
        val MOB_STATS = mapOf(
            SiegeRoles.GRUNT to MobStats(20.0, 3.0, "Siege Grunt", NamedTextColor.GRAY),
            SiegeRoles.SNIPER to MobStats(16.0, 4.0, "Siege Archer", NamedTextColor.GOLD),
            SiegeRoles.BREACHER to MobStats(40.0, 6.0, "Siege Breacher", NamedTextColor.DARK_RED),
            SiegeRoles.SABOTEUR to MobStats(24.0, 4.0, "Siege Sapper", NamedTextColor.DARK_PURPLE),
            SiegeRoles.COMMANDER to MobStats(150.0, 8.0, "Siege Commander", NamedTextColor.DARK_AQUA) // Buffed HP
        )
    }
    
    // Siege phases for state management
    enum class SiegePhase {
        PREPARATION, // 10 min countdown before mobs spawn
        BATTLE,      // Active combat
        PAUSED,      // All defenders disconnected
        ENDED        // Victory or defeat
    }
    
    // Result codes for siege validation
    enum class SiegeResult {
        OK,
        CITY_PROTECTED,      // Still in protection period
        ATTACKER_COOLDOWN,   // Attacker already sieged this city recently
        NO_DEFENDERS,        // No defenders online
        ERA_LOCKED,          // Attacker hasn't reached Era 1
        ALREADY_SIEGING,     // Already under siege
        CITY_COOLDOWN        // City on global cooldown
    }
    
    // Per-attacker cooldown tracking: cityId -> (attackerUUID -> lastSiegeTime)
    private val attackerCooldowns = ConcurrentHashMap<String, MutableMap<UUID, Long>>()
    
    data class MobStats(
        val health: Double,
        val damage: Double,
        val displayName: String,
        val color: NamedTextColor
    )
    
    /**
     * City strength calculation for dynamic siege difficulty
     * @property difficultyMultiplier Scales mob count (1.0 = baseline)
     * @property healthBonus Additional health % for mobs (0.0 = no bonus)
     * @property description Human-readable strength tier
     */
    data class CityStrength(
        val difficultyMultiplier: Double,
        val healthBonus: Double,
        val description: String
    )
    
    /**
     * Calculates city strength based on multiple factors:
     * - Infrastructure level (walls, turrets, barracks, etc.)
     * - Treasury wealth
     * - Expected active players (~25% of members, realistic assumption)
     * - Average era of city members
     */
    private fun calculateCityStrength(city: City): CityStrength {
        val infra = city.infrastructure
        
        // 1. Infrastructure score (0-100)
        val infraScore = (
            infra.wallLevel * 5 +           // Max 25
            infra.turretCount * 8 +         // Max 32 (4 turrets)
            infra.barracksLevel * 10 +      // Max 30
            infra.generatorLevel * 5 +      // Max 25
            infra.watchtowerLevel * 5 +     // Max 15
            infra.trapSystemLevel * 5 +     // Max 15
            infra.healingBeaconLevel * 5    // Max 15
        ).coerceIn(0, 100)
        
        // 2. Treasury score (0-50 based on wealth tiers)
        val treasuryScore = when {
            city.treasury >= 50000 -> 50    // Rich city
            city.treasury >= 20000 -> 35
            city.treasury >= 10000 -> 25
            city.treasury >= 5000 -> 15
            city.treasury >= 1000 -> 8
            else -> 0                        // Poor city
        }
        
        // 3. Expected active players (25% of members, minimum 1)
        val memberCount = city.members.size
        val expectedActive = (memberCount * 0.25).coerceAtLeast(1.0)
        val playerScore = when {
            expectedActive >= 5 -> 40       // Large active city
            expectedActive >= 3 -> 25
            expectedActive >= 2 -> 15
            else -> 5                        // Solo/duo city
        }
        
        // 4. Average era of members (check online members)
        var totalEra = 0
        var countedPlayers = 0
        city.members.forEach { memberId ->
            val player = plugin.server.getPlayer(memberId)
            if (player != null) {
                val era = plugin.progressionManager.getPlayerEra(player)
                totalEra += era.ordinal
                countedPlayers++
            }
        }
        val avgEra = if (countedPlayers > 0) totalEra.toDouble() / countedPlayers else 0.0
        val eraScore = (avgEra * 10).coerceIn(0.0, 40.0) // Max 40 for Era 4
        
        // Total score (0-230 max)
        val totalScore = infraScore + treasuryScore + playerScore + eraScore
        
        // Convert to difficulty multiplier and health bonus
        return when {
            totalScore >= 150 -> CityStrength(2.0, 0.5, "Fortress")       // Major city
            totalScore >= 100 -> CityStrength(1.6, 0.35, "Stronghold")   // Strong city
            totalScore >= 70 -> CityStrength(1.3, 0.2, "Settlement")     // Growing city
            totalScore >= 40 -> CityStrength(1.0, 0.1, "Outpost")        // Basic city
            else -> CityStrength(0.8, 0.0, "Hamlet")                      // Weak city (easier)
        }
    }

    data class ActiveSiege(
        val cityId: String,
        val attackerId: UUID,                    // Who triggered the siege
        val startTime: Long = System.currentTimeMillis(),
        var currentWave: Int = 0,                // 0 = prep phase
        var mobsRemaining: Int = 0,
        var mobsKilled: Int = 0,
        var bossBar: BossBar? = null,
        val spawnedMobs: MutableList<UUID> = mutableListOf(),
        val spawnedDefenders: MutableList<UUID> = mutableListOf(),
        var coreDamageTaken: Int = 0,
        var siegeTier: SiegeEquipment.SiegeBannerTier = SiegeEquipment.SiegeBannerTier.BASIC,
        var spawnLocation: Location? = null,
        var phase: SiegePhase = SiegePhase.PREPARATION,
        var pauseStartTime: Long? = null,        // When siege was paused
        var defenderCountAtStart: Int = 0,       // For reward scaling
        var prepCountdownTask: BukkitTask? = null
    ) {
        fun getMobMultiplier(): Double = siegeTier.mobMultiplier
        fun getRewardMultiplier(): Double = siegeTier.rewardMultiplier
        fun shouldSpawnMiniBoss(): Boolean = siegeTier.addMiniBoss
        fun isPaused(): Boolean = phase == SiegePhase.PAUSED
        fun isPrep(): Boolean = phase == SiegePhase.PREPARATION
        fun isBattle(): Boolean = phase == SiegePhase.BATTLE
    }

    // ═══════════════════════════════════════════════════════════════
    // SIEGE LIFECYCLE
    // ═══════════════════════════════════════════════════════════════

    /**
     * Comprehensive siege validation with anti-exploit checks
     */
    fun canSiege(city: City, attacker: Player): SiegeResult {
        val now = System.currentTimeMillis()
        
        // Check for active siege
        if (activeSieges.containsKey(city.id)) {
            return SiegeResult.ALREADY_SIEGING
        }
        
        // Check city global cooldown
        if (now - city.lastSiegeTime < SIEGE_COOLDOWN_MS) {
            return SiegeResult.CITY_COOLDOWN
        }
        
        // Check post-siege protection
        if (now - city.lastSiegeTime < POST_SIEGE_PROTECTION_MS) {
            return SiegeResult.CITY_PROTECTED
        }
        
        // Check per-attacker cooldown
        val cityCooldowns = attackerCooldowns[city.id]
        if (cityCooldowns != null) {
            val lastAttack = cityCooldowns[attacker.uniqueId]
            if (lastAttack != null && now - lastAttack < PER_ATTACKER_COOLDOWN_MS) {
                return SiegeResult.ATTACKER_COOLDOWN
            }
        }
        
        // Check attacker era requirement
        val playerEra = plugin.progressionManager.getPlayerEra(attacker)
        if (playerEra.ordinal < ATTACKER_ERA_REQUIREMENT) {
            return SiegeResult.ERA_LOCKED
        }
        
        // Check minimum defenders online
        val onlineDefenders = city.members.count { plugin.server.getPlayer(it) != null }
        if (onlineDefenders < MIN_DEFENDER_ONLINE) {
            return SiegeResult.NO_DEFENDERS
        }
        
        return SiegeResult.OK
    }

    /**
     * Legacy compatibility - simple check
     */
    fun canSiege(city: City): Boolean {
        val timeSinceLast = System.currentTimeMillis() - city.lastSiegeTime
        return timeSinceLast >= SIEGE_COOLDOWN_MS && !activeSieges.containsKey(city.id)
    }

    /**
     * Start siege with full validation
     */
    fun startSiege(
        city: City, 
        triggerLocation: Location, 
        tier: SiegeEquipment.SiegeBannerTier = SiegeEquipment.SiegeBannerTier.BASIC,
        attacker: Player? = null
    ): Boolean {
        // If attacker provided, do full validation
        if (attacker != null) {
            val result = canSiege(city, attacker)
            if (result != SiegeResult.OK) {
                sendValidationError(attacker, result, city)
                return false
            }
        } else {
            // Admin bypass - only check basic requirements
            if (!canSiege(city)) return false
        }
        
        val attackerUUID = attacker?.uniqueId ?: UUID.randomUUID()
        val onlineDefenders = city.members.count { plugin.server.getPlayer(it) != null }
        
        val siege = ActiveSiege(
            cityId = city.id,
            attackerId = attackerUUID,
            siegeTier = tier,
            spawnLocation = triggerLocation,
            phase = SiegePhase.PREPARATION,
            defenderCountAtStart = onlineDefenders
        )
        activeSieges[city.id] = siege
        siegeTasks[city.id] = mutableListOf()
        
        // Record attacker cooldown
        if (attacker != null) {
            attackerCooldowns.getOrPut(city.id) { mutableMapOf() }[attackerUUID] = System.currentTimeMillis()
        }
        
        // Create boss bar for PREP phase
        val bossBar = BossBar.bossBar(
            Component.text("⚔ SIEGE INCOMING: ${city.name} - Prepare for battle!", NamedTextColor.GOLD),
            1f,
            BossBar.Color.YELLOW,
            BossBar.Overlay.PROGRESS
        )
        siege.bossBar = bossBar
        
        // Show boss bar to all city members online
        city.members.forEach { memberId ->
            plugin.server.getPlayer(memberId)?.showBossBar(bossBar)
        }
        
        // Broadcast siege declaration
        broadcastSiegeDeclared(city, tier, PREP_PHASE_SECONDS)
        
        // Play warning horn
        plugin.server.onlinePlayers.forEach { 
            it.playSound(it.location, Sound.EVENT_RAID_HORN, 2.0f, 1.2f)
        }
        
        // Start siege visual task
        val visualTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            if (!activeSieges.containsKey(city.id)) return@Runnable
            plugin.visualManager.showSiegeBorders(city.id)
        }, 0L, 10L)
        siegeTasks[city.id]?.add(visualTask)
        
        // Start the 10-minute countdown
        startPrepCountdown(city, siege, triggerLocation)
        
        return true
    }
    
    /**
     * 10-minute preparation countdown with updates every minute
     */
    private fun startPrepCountdown(city: City, siege: ActiveSiege, location: Location) {
        var secondsRemaining = PREP_PHASE_SECONDS
        
        val countdownTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            if (!activeSieges.containsKey(city.id) || siege.phase != SiegePhase.PREPARATION) {
                return@Runnable
            }
            
            secondsRemaining -= 1
            
            // Update boss bar progress
            siege.bossBar?.progress((secondsRemaining.toFloat() / PREP_PHASE_SECONDS).coerceIn(0f, 1f))
            
            // Announce at key intervals
            when (secondsRemaining) {
                300 -> announceCountdown(city, "5 minutes")
                180 -> announceCountdown(city, "3 minutes")
                60 -> announceCountdown(city, "1 minute")
                30 -> announceCountdown(city, "30 seconds")
                10 -> announceCountdown(city, "10 seconds")
                5, 4, 3, 2, 1 -> announceCountdown(city, "$secondsRemaining...")
            }
            
            if (secondsRemaining <= 0) {
                // Prep phase complete - begin battle
                siege.phase = SiegePhase.BATTLE
                siege.currentWave = 1
                siege.bossBar?.name(Component.text("⚔ SIEGE: ${city.name} - Wave 1/$WAVES_PER_SIEGE", NamedTextColor.RED))
                siege.bossBar?.color(BossBar.Color.RED)
                
                // Play war horn again
                plugin.server.onlinePlayers.forEach { 
                    it.playSound(it.location, Sound.EVENT_RAID_HORN, 2.0f, 0.8f)
                }
                
                broadcastSiegeStart(city, siege.siegeTier)
                
                // Spawn defenders
                spawnDefenders(city, siege, location)
                
                // Start infrastructure tasks
                startTurretTask(city, siege)
                startTrapSystemTask(city, siege)
                startHealingBeaconTask(city, siege)
                
                // Start defender disconnect monitor
                startDisconnectMonitor(city, siege)
                
                // Spawn first wave
                spawnWave(city, siege, location)
            }
        }, 20L, 20L) // Run every second
        
        siege.prepCountdownTask = countdownTask
        siegeTasks[city.id]?.add(countdownTask)
    }
    
    private fun announceCountdown(city: City, time: String) {
        city.members.forEach { memberId ->
            plugin.server.getPlayer(memberId)?.let { player ->
                player.sendMessage(Component.text("⚠ Siege begins in $time!", NamedTextColor.GOLD))
                player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f)
            }
        }
    }
    
    /**
     * Monitor for all defenders disconnecting (pause siege)
     */
    private fun startDisconnectMonitor(city: City, siege: ActiveSiege) {
        val monitorTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            if (!activeSieges.containsKey(city.id)) return@Runnable
            if (siege.phase == SiegePhase.ENDED) return@Runnable
            
            val onlineDefenders = city.members.count { plugin.server.getPlayer(it) != null }
            
            if (siege.phase == SiegePhase.BATTLE && onlineDefenders == 0) {
                // All defenders disconnected - pause siege
                siege.phase = SiegePhase.PAUSED
                siege.pauseStartTime = System.currentTimeMillis()
                
                // Pause all siege mobs
                siege.spawnedMobs.mapNotNull { plugin.server.getEntity(it) as? LivingEntity }
                    .forEach { mob -> 
                        mob.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, Int.MAX_VALUE, 255, false, false))
                        mob.setAI(false)
                    }
                
                plugin.server.broadcast(
                    Component.text("⏸ Siege on ${city.name} PAUSED - All defenders disconnected!", NamedTextColor.YELLOW)
                )
            } else if (siege.phase == SiegePhase.PAUSED && onlineDefenders > 0) {
                // Defender reconnected - resume siege
                siege.phase = SiegePhase.BATTLE
                siege.pauseStartTime = null
                
                // Unfreeze mobs
                siege.spawnedMobs.mapNotNull { plugin.server.getEntity(it) as? LivingEntity }
                    .forEach { mob -> 
                        mob.removePotionEffect(PotionEffectType.SLOWNESS)
                        mob.setAI(true)
                    }
                
                city.members.forEach { memberId ->
                    plugin.server.getPlayer(memberId)?.sendMessage(
                        Component.text("▶ Siege RESUMED! Enemies are attacking!", NamedTextColor.RED)
                    )
                }
            } else if (siege.phase == SiegePhase.PAUSED) {
                // Check pause timeout
                val pauseDuration = System.currentTimeMillis() - (siege.pauseStartTime ?: 0)
                if (pauseDuration > PAUSE_TIMEOUT_SECONDS * 1000L) {
                    // Timeout - end siege with reduced penalty
                    endSiegeDisconnectTimeout(city, siege)
                }
            }
        }, 20L, 40L) // Check every 2 seconds
        
        siegeTasks[city.id]?.add(monitorTask)
    }
    
    /**
     * End siege due to disconnect timeout - reduced penalties
     */
    private fun endSiegeDisconnectTimeout(city: City, siege: ActiveSiege) {
        siege.phase = SiegePhase.ENDED
        activeSieges.remove(city.id)
        
        // Cancel all tasks
        siegeTasks[city.id]?.forEach { it.cancel() }
        siegeTasks.remove(city.id)
        
        // Hide boss bar
        city.members.forEach { memberId ->
            siege.bossBar?.let { plugin.server.getPlayer(memberId)?.hideBossBar(it) }
        }
        
        // Cleanup mobs
        siege.spawnedMobs.forEach { mobId -> plugin.server.getEntity(mobId)?.remove() }
        siege.spawnedDefenders.forEach { defenderId -> plugin.server.getEntity(defenderId)?.remove() }
        
        // Apply reduced penalties (half)
        val treasuryLoss = city.treasury * TREASURY_LOSS_DISCONNECT
        city.treasury -= treasuryLoss
        city.infrastructure.damageCore(CORE_DAMAGE_BASE / 2)
        
        plugin.server.broadcast(Component.text("", NamedTextColor.GRAY))
        plugin.server.broadcast(
            Component.text("  ⏱ Siege on ${city.name} ended (Defender Timeout)", NamedTextColor.YELLOW)
        )
        plugin.server.broadcast(Component.text("  Reduced penalties applied.", NamedTextColor.GRAY))
        plugin.server.broadcast(Component.text("", NamedTextColor.GRAY))
        
        city.lastSiegeTime = System.currentTimeMillis()
        plugin.cityManager.saveCity(city)
        
        plugin.historyManager.logEvent(
            city.id, 
            "Siege ended - defender disconnect timeout", 
            EventType.SIEGE
        )
    }
    
    private fun sendValidationError(player: Player, result: SiegeResult, city: City) {
        val message = when (result) {
            SiegeResult.ALREADY_SIEGING -> "${city.name} is already under siege!"
            SiegeResult.CITY_COOLDOWN -> "${city.name} was recently sieged. Cooldown active."
            SiegeResult.CITY_PROTECTED -> "${city.name} is protected for 8 hours after a siege."
            SiegeResult.ATTACKER_COOLDOWN -> "You already attacked this city recently. Wait 24 hours."
            SiegeResult.NO_DEFENDERS -> "At least 1 defender must be online to start a siege."
            SiegeResult.ERA_LOCKED -> "You must reach Era 1 to trigger sieges."
            else -> "Cannot siege this city."
        }
        player.sendMessage(Component.text(message, NamedTextColor.RED))
    }
    
    private fun broadcastSiegeDeclared(city: City, tier: SiegeEquipment.SiegeBannerTier, prepSeconds: Int) {
        val minutes = prepSeconds / 60
        val tierText = when (tier) {
            SiegeEquipment.SiegeBannerTier.BASIC -> ""
            SiegeEquipment.SiegeBannerTier.WAR -> " [WAR]"
            SiegeEquipment.SiegeBannerTier.CHAOS -> " [CHAOS]"
        }
        
        plugin.server.broadcast(Component.text("", NamedTextColor.GOLD))
        plugin.server.broadcast(Component.text("  ⚔ SIEGE DECLARED ON ${city.name.uppercase()}$tierText!", tier.color).decorate(TextDecoration.BOLD))
        plugin.server.broadcast(Component.text("  Battle begins in $minutes minutes! Defenders, prepare!", NamedTextColor.YELLOW))
        plugin.server.broadcast(Component.text("", NamedTextColor.GOLD))
    }
    
    private fun broadcastSiegeStart(city: City, tier: SiegeEquipment.SiegeBannerTier) {
        val tierText = when (tier) {
            SiegeEquipment.SiegeBannerTier.BASIC -> ""
            SiegeEquipment.SiegeBannerTier.WAR -> " [WAR]"
            SiegeEquipment.SiegeBannerTier.CHAOS -> " [CHAOS]"
        }
        
        plugin.server.broadcast(Component.text("", NamedTextColor.DARK_RED))
        plugin.server.broadcast(Component.text("  ⚔ SIEGE BEGINS ON ${city.name.uppercase()}$tierText!", tier.color).decorate(TextDecoration.BOLD))
        plugin.server.broadcast(Component.text("  Defend your city against 5 waves of enemies!", NamedTextColor.YELLOW))
        plugin.server.broadcast(Component.text("", NamedTextColor.DARK_RED))
    }
    
    // ═══════════════════════════════════════════════════════════════
    // INFRASTRUCTURE TASKS
    // ═══════════════════════════════════════════════════════════════
    
    private fun startTurretTask(city: City, siege: ActiveSiege) {
        if (city.infrastructure.turretCount <= 0) return
        
        val task = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            if (!activeSieges.containsKey(city.id)) return@Runnable
            
            val targets = siege.spawnedMobs.mapNotNull { plugin.server.getEntity(it) as? LivingEntity }
                .filter { it.isValid && !it.isDead }
            if (targets.isEmpty()) return@Runnable
            
            // Fire X shots (one per turret)
            for (i in 0 until city.infrastructure.turretCount) {
                val target = targets.randomOrNull() ?: break
                target.damage(5.0) // Turret damage
                target.world.spawnParticle(Particle.CRIT, target.location.add(0.0, 1.0, 0.0), 10)
                target.world.playSound(target.location, Sound.ENTITY_ARROW_HIT, 1.0f, 1.0f)
            }
        }, 40L, 40L) // Every 2 seconds
        
        siegeTasks[city.id]?.add(task)
    }
    
    private fun startTrapSystemTask(city: City, siege: ActiveSiege) {
        if (city.infrastructure.trapSystemLevel <= 0) return
        
        val trapDamage = city.infrastructure.getTrapDamage()
        val slowAmp = city.infrastructure.getTrapSlowAmplifier()
        
        val task = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            if (!activeSieges.containsKey(city.id)) return@Runnable
            
            siege.spawnedMobs.mapNotNull { plugin.server.getEntity(it) as? LivingEntity }
                .filter { it.isValid && !it.isDead }
                .forEach { mob ->
                    // Check if mob is in city territory
                    val chunk = mob.location.chunk
                    val cityAtChunk = plugin.cityManager.getCityAt(chunk)
                    if (cityAtChunk?.id == city.id) {
                        // Apply trap effects
                        mob.damage(trapDamage)
                        mob.addPotionEffect(PotionEffect(
                            PotionEffectType.SLOWNESS, 
                            40, // 2 seconds
                            slowAmp,
                            false, 
                            true
                        ))
                        mob.world.spawnParticle(Particle.SMOKE, mob.location, 5)
                    }
                }
        }, 20L, 20L) // Every second
        
        siegeTasks[city.id]?.add(task)
    }
    
    private fun startHealingBeaconTask(city: City, siege: ActiveSiege) {
        if (city.infrastructure.healingBeaconLevel <= 0) return
        
        val healAmount = city.infrastructure.getHealingBeaconAmount()
        
        val task = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            if (!activeSieges.containsKey(city.id)) return@Runnable
            
            // Heal defenders (Iron Golems)
            siege.spawnedDefenders.mapNotNull { plugin.server.getEntity(it) as? LivingEntity }
                .filter { it.isValid && !it.isDead }
                .forEach { defender ->
                    val maxHealth = defender.getAttribute(Attribute.MAX_HEALTH)?.value ?: 100.0
                    if (defender.health < maxHealth) {
                        defender.health = (defender.health + healAmount).coerceAtMost(maxHealth)
                        defender.world.spawnParticle(Particle.HEART, defender.location.add(0.0, 2.0, 0.0), 2)
                    }
                }
            
            // Also heal city members in territory
            city.members.forEach { memberId ->
                plugin.server.getPlayer(memberId)?.let { player ->
                    val cityAtChunk = plugin.cityManager.getCityAt(player.location.chunk)
                    if (cityAtChunk?.id == city.id) {
                        val maxHealth = player.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
                        if (player.health < maxHealth) {
                            player.health = (player.health + healAmount / 2).coerceAtMost(maxHealth)
                        }
                    }
                }
            }
        }, 40L, 40L) // Every 2 seconds
        
        siegeTasks[city.id]?.add(task)
    }

    private fun spawnDefenders(city: City, siege: ActiveSiege, location: Location) {
        val count = city.infrastructure.getDefenderCount()
        if (count <= 0) return
        
        val armorBonus = city.infrastructure.getDefenderArmorBonus()
        val damageMultiplier = city.infrastructure.getDefenderDamageMultiplier()
        val isMilitaryBastion = city.specialization == CitySpecialization.MILITARY_BASTION
        
        for (i in 0 until count) {
            val spawnLoc = location.clone().add((Math.random() * 4 - 2), 0.0, (Math.random() * 4 - 2))
            spawnLoc.world.spawn(spawnLoc, IronGolem::class.java) { golem ->
                golem.customName(Component.text("Defender of ${city.name}", NamedTextColor.BLUE))
                golem.isCustomNameVisible = true
                golem.isPlayerCreated = true
                
                // Apply infrastructure bonuses
                var baseHealth = 100.0
                
                // Military Bastion: 2x health and Strength II
                if (isMilitaryBastion) {
                    baseHealth = 200.0
                    golem.addPotionEffect(PotionEffect(PotionEffectType.STRENGTH, PotionEffect.INFINITE_DURATION, 1))
                }
                
                golem.getAttribute(Attribute.MAX_HEALTH)?.baseValue = baseHealth
                golem.health = baseHealth
                
                // Armory bonus: armor toughness
                if (armorBonus > 0) {
                    golem.getAttribute(Attribute.ARMOR_TOUGHNESS)?.baseValue = armorBonus
                }
                
                // Forge bonus: attack damage
                if (damageMultiplier > 1.0) {
                    val baseDamage = golem.getAttribute(Attribute.ATTACK_DAMAGE)?.baseValue ?: 7.0
                    golem.getAttribute(Attribute.ATTACK_DAMAGE)?.baseValue = baseDamage * damageMultiplier
                }
                
                siege.spawnedDefenders.add(golem.uniqueId)
            }
        }
        
        city.members.forEach { memberId ->
            plugin.server.getPlayer(memberId)?.sendMessage(
                Component.text("🛡️ Barracks deployed $count defenders!", NamedTextColor.GREEN)
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // WAVE SPAWNING
    // ═══════════════════════════════════════════════════════════════

    private fun spawnWave(city: City, siege: ActiveSiege, location: Location) {
        val world = location.world ?: return
        
        // Calculate city strength for dynamic scaling
        val cityStrength = calculateCityStrength(city)
        val difficultyMultiplier = cityStrength.difficultyMultiplier
        val healthMultiplierBonus = cityStrength.healthBonus
        
        // Calculate mob count with wave scaling, tier multiplier, and city strength
        val waveIndex = (siege.currentWave - 1).coerceIn(0, WAVE_MOB_SCALING.size - 1)
        val baseCount = (MOBS_PER_WAVE_BASE + (siege.currentWave * 2))
        val scaledCount = (baseCount * WAVE_MOB_SCALING[waveIndex] * siege.getMobMultiplier() * difficultyMultiplier).toInt()
            .coerceIn(3, 30) // Min 3, max 30 per wave
        
        siege.mobsRemaining = scaledCount
        
        // Update boss bar
        siege.bossBar?.name(
            Component.text("⚔ SIEGE: ${city.name} - Wave ${siege.currentWave}/$WAVES_PER_SIEGE", NamedTextColor.RED)
        )
        siege.bossBar?.progress(1f)
        
        // Analyze city defenses to adjust composition
        val infra = city.infrastructure
        val hasTurrets = infra.turretCount > 0
        val hasStrongBarracks = infra.barracksLevel >= 2
        val hasTraps = infra.trapSystemLevel > 0
        
        // Smart composition weights based on city defenses
        val sapperWeight = if (hasTurrets) 0.25 else 0.10  // More sappers vs turrets
        val breacherWeight = if (hasStrongBarracks) 0.30 else 0.15
        val archerWeight = if (hasTraps) 0.15 else 0.25  // Fewer archers if traps (they're ranged anyway)
        
        // Spawn mobs in a spread formation around the location
        val radius = 25.0
        for (i in 0 until scaledCount) {
            val angle = (2 * Math.PI * i) / scaledCount
            val spawnX = location.x + radius * Math.cos(angle)
            val spawnZ = location.z + radius * Math.sin(angle)
            var spawnLoc = Location(world, spawnX, location.y, spawnZ)
            spawnLoc.y = world.getHighestBlockYAt(spawnLoc.blockX, spawnLoc.blockZ).toDouble() + 1
            
            val healthMultiplier = WAVE_HEALTH_SCALING[waveIndex] * (1.0 + healthMultiplierBonus)
            val spawnRoll = Math.random()
            
            val mob = when {
                // Wave 5: Spawn Commander (boss) - always first
                siege.currentWave >= 5 && i == 0 -> spawnCommander(world, spawnLoc, healthMultiplier)
                // Mini-boss for Chaos tier
                siege.shouldSpawnMiniBoss() && i == 0 -> spawnCommander(world, spawnLoc, healthMultiplier * 0.6)
                // Sappers: Priority vs turrets/generators (wave 3+)
                siege.currentWave >= 3 && spawnRoll < sapperWeight -> spawnSapper(world, spawnLoc, healthMultiplier)
                // Breachers: Priority vs barracks (wave 2+)
                siege.currentWave >= 2 && spawnRoll < sapperWeight + breacherWeight -> spawnBreacher(world, spawnLoc, healthMultiplier)
                // Archers: Ranged support
                spawnRoll < sapperWeight + breacherWeight + archerWeight -> spawnArcher(world, spawnLoc, healthMultiplier)
                // Default: Grunts (player hunters)
                else -> spawnGrunt(world, spawnLoc, healthMultiplier)
            }
            
            siege.spawnedMobs.add(mob.uniqueId)
        }
        
        // Announce wave with composition hint
        val compositionHint = when {
            hasTurrets && siege.currentWave >= 3 -> " Watch your turrets!"
            hasStrongBarracks -> " They're targeting the barracks!"
            else -> ""
        }
        
        city.members.forEach { memberId ->
            plugin.server.getPlayer(memberId)?.let { player ->
                player.sendMessage(
                    Component.text("⚔ Wave ${siege.currentWave} incoming! $scaledCount hostiles approaching!$compositionHint", NamedTextColor.RED)
                )
                player.playSound(player.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // MOB SPAWNING
    // ═══════════════════════════════════════════════════════════════

    private fun spawnGrunt(world: org.bukkit.World, loc: Location, healthMultiplier: Double): Entity {
        val stats = MOB_STATS[SiegeRoles.GRUNT]!!
        return world.spawn(loc, Zombie::class.java) { zombie ->
            zombie.customName(Component.text(stats.displayName, stats.color))
            zombie.isCustomNameVisible = true
            zombie.isBaby = false
            zombie.getAttribute(Attribute.MAX_HEALTH)?.baseValue = stats.health * healthMultiplier
            zombie.health = stats.health * healthMultiplier
            zombie.getAttribute(Attribute.ATTACK_DAMAGE)?.baseValue = stats.damage
            zombie.addPotionEffect(PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 0, false, false))
            setSiegeRole(zombie, SiegeRoles.GRUNT)
        }
    }

    private fun spawnArcher(world: org.bukkit.World, loc: Location, healthMultiplier: Double): Entity {
        val stats = MOB_STATS[SiegeRoles.SNIPER]!!
        return world.spawn(loc, Skeleton::class.java) { skel ->
            skel.customName(Component.text(stats.displayName, stats.color))
            skel.isCustomNameVisible = true
            skel.getAttribute(Attribute.MAX_HEALTH)?.baseValue = stats.health * healthMultiplier
            skel.health = stats.health * healthMultiplier
            skel.addPotionEffect(PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 1, false, false))
            setSiegeRole(skel, SiegeRoles.SNIPER)
        }
    }

    private fun spawnBreacher(world: org.bukkit.World, loc: Location, healthMultiplier: Double): Entity {
        val stats = MOB_STATS[SiegeRoles.BREACHER]!!
        return world.spawn(loc, Vindicator::class.java) { vin ->
            vin.customName(Component.text(stats.displayName, stats.color))
            vin.isCustomNameVisible = true
            vin.getAttribute(Attribute.MAX_HEALTH)?.baseValue = stats.health * healthMultiplier
            vin.health = stats.health * healthMultiplier
            vin.getAttribute(Attribute.ATTACK_DAMAGE)?.baseValue = stats.damage
            vin.addPotionEffect(PotionEffect(PotionEffectType.STRENGTH, PotionEffect.INFINITE_DURATION, 1, false, false))
            vin.addPotionEffect(PotionEffect(PotionEffectType.RESISTANCE, PotionEffect.INFINITE_DURATION, 1, false, false))
            setSiegeRole(vin, SiegeRoles.BREACHER)
        }
    }
    
    private fun spawnSapper(world: org.bukkit.World, loc: Location, healthMultiplier: Double): Entity {
        val stats = MOB_STATS[SiegeRoles.SABOTEUR]!!
        return world.spawn(loc, Witch::class.java) { witch ->
            witch.customName(Component.text(stats.displayName, stats.color))
            witch.isCustomNameVisible = true
            witch.getAttribute(Attribute.MAX_HEALTH)?.baseValue = stats.health * healthMultiplier
            witch.health = stats.health * healthMultiplier
            witch.addPotionEffect(PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false))
            witch.addPotionEffect(PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 2, false, false))
            setSiegeRole(witch, SiegeRoles.SABOTEUR)
        }
    }
    
    private fun spawnCommander(world: org.bukkit.World, loc: Location, healthMultiplier: Double): Entity {
        val stats = MOB_STATS[SiegeRoles.COMMANDER]!!
        return world.spawn(loc, Ravager::class.java) { ravager ->
            ravager.customName(
                Component.text("☠ ${stats.displayName}", stats.color).decorate(TextDecoration.BOLD)
            )
            ravager.isCustomNameVisible = true
            ravager.getAttribute(Attribute.MAX_HEALTH)?.baseValue = stats.health * healthMultiplier
            ravager.health = stats.health * healthMultiplier
            ravager.getAttribute(Attribute.ATTACK_DAMAGE)?.baseValue = stats.damage
            ravager.addPotionEffect(PotionEffect(PotionEffectType.RESISTANCE, PotionEffect.INFINITE_DURATION, 1, false, false))
            ravager.addPotionEffect(PotionEffect(PotionEffectType.FIRE_RESISTANCE, PotionEffect.INFINITE_DURATION, 0, false, false))
            setSiegeRole(ravager, SiegeRoles.COMMANDER)
        }
    }
    
    private fun setSiegeRole(entity: LivingEntity, role: String) {
        entity.persistentDataContainer.set(
            NamespacedKey(plugin, SiegeRoles.ROLE_KEY), 
            PersistentDataType.STRING, 
            role
        )
    }

    // ═══════════════════════════════════════════════════════════════
    // EVENT HANDLERS
    // ═══════════════════════════════════════════════════════════════

    @EventHandler
    fun onMobKill(event: EntityDeathEvent) {
        val entity = event.entity
        if (entity !is LivingEntity) return
        
        // Find which siege this mob belongs to
        for ((cityId, siege) in activeSieges) {
            if (siege.spawnedMobs.contains(entity.uniqueId)) {
                siege.spawnedMobs.remove(entity.uniqueId)
                siege.mobsRemaining--
                siege.mobsKilled++
                
                // Update boss bar
                val totalMobs = (MOBS_PER_WAVE_BASE + (siege.currentWave * 2)) * 
                    WAVE_MOB_SCALING[(siege.currentWave - 1).coerceIn(0, 4)] * 
                    siege.getMobMultiplier()
                val progress = siege.mobsRemaining.toFloat() / totalMobs.toFloat()
                siege.bossBar?.progress(progress.coerceIn(0f, 1f))
                
                // Check wave complete
                if (siege.mobsRemaining <= 0) {
                    onWaveComplete(cityId, siege)
                }
                break
            }
        }
    }
    
    @EventHandler
    fun onEntityDamage(event: org.bukkit.event.entity.EntityDamageByEntityEvent) {
        val damager = event.damager as? Player ?: return
        val entity = event.entity
        
        // Check if target is a siege mob
        for (siege in activeSieges.values) {
            if (siege.spawnedMobs.contains(entity.uniqueId)) {
                // Apply skill tree bonus
                val mult = plugin.skillTreeManager.getSiegeDamageMultiplier(damager)
                if (mult > 1.0) {
                    event.damage *= mult
                    damager.world.spawnParticle(Particle.ENCHANTED_HIT, entity.location.add(0.0, 1.0, 0.0), 5)
                }
                break
            }
        }
    }

    private fun onWaveComplete(cityId: String, siege: ActiveSiege) {
        val city = plugin.cityManager.getCity(cityId) ?: return
        
        if (siege.currentWave >= WAVES_PER_SIEGE) {
            // VICTORY!
            endSiege(city, siege, victory = true)
        } else {
            // Next wave
            siege.currentWave++
            
            city.members.forEach { memberId ->
                plugin.server.getPlayer(memberId)?.let { player ->
                    player.sendMessage(
                        Component.text("✓ Wave ${siege.currentWave - 1} complete! Prepare for wave ${siege.currentWave}...", NamedTextColor.GREEN)
                    )
                    player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f)
                }
            }
            
            // Delay next wave
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                val activeCity = plugin.cityManager.getCity(cityId)
                val activeSiege = activeSieges[cityId]
                if (activeCity != null && activeSiege != null) {
                    val loc = activeSiege.spawnLocation ?: return@Runnable
                    spawnWave(activeCity, activeSiege, loc)
                }
            }, WAVE_DELAY_TICKS)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // SIEGE END
    // ═══════════════════════════════════════════════════════════════

    private fun endSiege(city: City, siege: ActiveSiege, victory: Boolean) {
        activeSieges.remove(city.id)
        
        // Cancel all tasks
        siegeTasks[city.id]?.forEach { it.cancel() }
        siegeTasks.remove(city.id)
        
        // Hide boss bar
        city.members.forEach { memberId ->
            siege.bossBar?.let { plugin.server.getPlayer(memberId)?.hideBossBar(it) }
        }
        
        // Cleanup remaining mobs
        siege.spawnedMobs.forEach { mobId ->
            plugin.server.getEntity(mobId)?.remove()
        }
        siege.spawnedDefenders.forEach { defenderId ->
            plugin.server.getEntity(defenderId)?.remove()
        }
        
        if (victory) {
            handleVictory(city, siege)
        } else {
            handleDefeat(city, siege)
        }
        
        city.lastSiegeTime = System.currentTimeMillis()
        plugin.cityManager.saveCity(city)
    }
    
    private fun handleVictory(city: City, siege: ActiveSiege) {
        // Calculate rewards
        var reward = BASE_REWARD
        reward += WAVE_BONUS * WAVES_PER_SIEGE
        
        // Flawless bonus
        if (siege.coreDamageTaken == 0) {
            reward += FLAWLESS_BONUS
        }
        
        // Apply tier multiplier
        reward *= siege.getRewardMultiplier()
        
        city.treasury += reward
        
        plugin.server.broadcast(Component.text("", NamedTextColor.GREEN))
        plugin.server.broadcast(
            Component.text("  ✓ ${city.name.uppercase()} DEFENDED SUCCESSFULLY!", NamedTextColor.GREEN)
                .decorate(TextDecoration.BOLD)
        )
        plugin.server.broadcast(Component.text("  Treasury +${reward.toInt()}g", NamedTextColor.GOLD))
        if (siege.coreDamageTaken == 0) {
            plugin.server.broadcast(Component.text("  🏆 FLAWLESS DEFENSE! +${FLAWLESS_BONUS.toInt()}g bonus!", NamedTextColor.AQUA))
        }
        plugin.server.broadcast(Component.text("", NamedTextColor.GREEN))
        
        city.members.forEach { memberId ->
            plugin.server.getPlayer(memberId)?.let { player ->
                player.playSound(player.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f)
                plugin.milestoneListener.onSiegeSurvive(player, isWin = true)
            }
        }
        
        plugin.historyManager.logEvent(
            city.id, 
            "Defended siege (${siege.siegeTier.displayName}, Wave $WAVES_PER_SIEGE, +${reward.toInt()}g)", 
            EventType.SIEGE
        )
    }
    
    private fun handleDefeat(city: City, siege: ActiveSiege) {
        // Treasury loss
        val treasuryLoss = city.treasury * TREASURY_LOSS_PERCENT
        city.treasury -= treasuryLoss
        
        // Core damage (mitigated by walls)
        val coreDamage = city.infrastructure.damageCore(CORE_DAMAGE_BASE)
        
        // Random infrastructure damage
        if (Math.random() < INFRASTRUCTURE_DAMAGE_CHANCE) {
            damageRandomInfrastructure(city)
        }
        
        plugin.server.broadcast(Component.text("", NamedTextColor.DARK_RED))
        plugin.server.broadcast(
            Component.text("  ✗ ${city.name.uppercase()} HAS FALLEN!", NamedTextColor.RED)
                .decorate(TextDecoration.BOLD)
        )
        plugin.server.broadcast(Component.text("  Treasury -${treasuryLoss.toInt()}g", NamedTextColor.GRAY))
        plugin.server.broadcast(Component.text("  City Core -$coreDamage HP (${city.infrastructure.coreHealth}/100)", NamedTextColor.RED))
        plugin.server.broadcast(Component.text("", NamedTextColor.DARK_RED))
        
        // Check if city is destroyed
        if (city.infrastructure.isCoreDestroyed()) {
            plugin.server.broadcast(
                Component.text("  💀 ${city.name} has been DESTROYED!", NamedTextColor.DARK_RED)
                    .decorate(TextDecoration.BOLD)
            )
            // TODO: Handle city destruction (disband city, etc.)
        }
        
        plugin.historyManager.logEvent(
            city.id, 
            "Lost siege at wave ${siege.currentWave} (${siege.siegeTier.displayName})", 
            EventType.SIEGE
        )
    }
    
    private fun damageRandomInfrastructure(city: City) {
        val infra = city.infrastructure
        val modules = mutableListOf<String>()
        
        if (infra.wallLevel > 0) modules.add("wall")
        if (infra.turretCount > 0) modules.add("turret")
        if (infra.barracksLevel > 0) modules.add("barracks")
        if (infra.generatorLevel > 0) modules.add("generator")
        if (infra.marketLevel > 0) modules.add("market")
        if (infra.clinicLevel > 0) modules.add("clinic")
        if (infra.watchtowerLevel > 0) modules.add("watchtower")
        if (infra.trapSystemLevel > 0) modules.add("trapsystem")
        if (infra.healingBeaconLevel > 0) modules.add("healingbeacon")
        
        if (modules.isEmpty()) return
        
        val damagedModule = modules.random()
        when (damagedModule) {
            "wall" -> infra.wallLevel = (infra.wallLevel - 1).coerceAtLeast(0)
            "turret" -> infra.turretCount = (infra.turretCount - 1).coerceAtLeast(0)
            "barracks" -> infra.barracksLevel = (infra.barracksLevel - 1).coerceAtLeast(0)
            "generator" -> infra.generatorLevel = (infra.generatorLevel - 1).coerceAtLeast(0)
            "market" -> infra.marketLevel = (infra.marketLevel - 1).coerceAtLeast(0)
            "clinic" -> infra.clinicLevel = (infra.clinicLevel - 1).coerceAtLeast(0)
            "watchtower" -> infra.watchtowerLevel = (infra.watchtowerLevel - 1).coerceAtLeast(0)
            "trapsystem" -> infra.trapSystemLevel = (infra.trapSystemLevel - 1).coerceAtLeast(0)
            "healingbeacon" -> infra.healingBeaconLevel = (infra.healingBeaconLevel - 1).coerceAtLeast(0)
        }
        
        plugin.server.broadcast(
            Component.text("  ⚠ ${damagedModule.uppercase()} was damaged in the attack!", NamedTextColor.YELLOW)
        )
    }

    // ═══════════════════════════════════════════════════════════════
    // SIEGE BANNER USAGE
    // ═══════════════════════════════════════════════════════════════
    
    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        val item = event.item ?: return
        
        // Check for Siege Banner using the new equipment system
        val tier = SiegeEquipment.getSiegeBannerTier(plugin, item)
        if (tier != null) {
            event.isCancelled = true
            
            val block = event.clickedBlock ?: return
            val city = plugin.cityManager.getCityAt(block.chunk)
            
            if (city == null) {
                event.player.sendMessage(Component.text("You must use this inside a City territory!", NamedTextColor.RED))
                return
            }
            
            // Check if player is attacking their own city
            val profile = plugin.identityManager.getPlayer(event.player.uniqueId)
            if (profile?.cityId == city.id) {
                event.player.sendMessage(Component.text("You cannot siege your own city!", NamedTextColor.RED))
                return
            }
            
            // Use full validation with attacker player
            if (startSiege(city, block.location.add(0.0, 1.0, 0.0), tier, event.player)) {
                item.amount -= 1
                event.player.sendMessage(Component.text("${tier.displayName} activated! Siege declared!", tier.color))
                event.player.sendMessage(Component.text("Battle begins in 10 minutes.", NamedTextColor.YELLOW))
            }
            // Error message already sent by startSiege/sendValidationError
            return
        }
        
        // Legacy support: Check for old-style Siege Banner by display name
        if (item.type == Material.RED_BANNER && item.hasItemMeta()) {
            val displayName = item.itemMeta.displayName()
            val text = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(displayName ?: Component.text(""))
            if (text.contains("Siege Banner")) {
                event.isCancelled = true
                val block = event.clickedBlock ?: return
                val city = plugin.cityManager.getCityAt(block.chunk)
                
                if (city == null) {
                    event.player.sendMessage(Component.text("You must use this inside a City territory!", NamedTextColor.RED))
                    return
                }
                
                if (startSiege(city, block.location.add(0.0, 1.0, 0.0))) {
                    item.amount -= 1
                    event.player.sendMessage(Component.text("Siege triggered!", NamedTextColor.RED))
                } else {
                    event.player.sendMessage(Component.text("Cannot siege this city right now (Cooldown or Active).", NamedTextColor.RED))
                }
            }
        }
    }
    
    // ═══════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════
    
    fun getActiveSiege(cityId: String): ActiveSiege? = activeSieges[cityId]
    
    fun getAllActiveSieges(): Collection<ActiveSiege> = activeSieges.values
    
    fun forceSurrender(city: City): Boolean {
        val siege = activeSieges[city.id] ?: return false
        endSiege(city, siege, victory = false)
        return true
    }
    
    fun getSiegeStatus(cityId: String): String? {
        val siege = activeSieges[cityId] ?: return null
        return "Wave ${siege.currentWave}/$WAVES_PER_SIEGE | Mobs Remaining: ${siege.mobsRemaining} | Tier: ${siege.siegeTier.displayName}"
    }
}
