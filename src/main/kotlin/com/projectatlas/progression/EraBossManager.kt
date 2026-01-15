package com.projectatlas.progression

import com.projectatlas.AtlasPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Era Boss Manager - Handles the gate bosses for each era transition.
 * 
 * Bosses:
 * - Era 0 → 1: Hollow Knight (Wither Skeleton)
 * - Era 1 → 2: Tax Collector (Illager Raid)
 * - Era 2 → 3: Warden of Flames (Blaze King)
 * - Era 3 → 4: Ender Sentinel (Enderman Boss)
 */
class EraBossManager(private val plugin: AtlasPlugin) : Listener {

    // Track active bosses and their spawners
    private val activeBosses = ConcurrentHashMap<UUID, EraBoss>()
    private val bossParticipants = ConcurrentHashMap<UUID, MutableSet<UUID>>() // Boss UUID -> Player UUIDs
    
    data class EraBoss(
        val entityUUID: UUID,
        val type: BossType,
        val spawnerPlayer: UUID,
        val spawnTime: Long = System.currentTimeMillis()
    )

    enum class BossType(
        val displayName: String,
        val targetEra: ProgressionManager.Era,
        val baseHealth: Double,
        val color: NamedTextColor
    ) {
        HOLLOW_KNIGHT("The Hollow Knight", ProgressionManager.Era.AWAKENING, 100.0, NamedTextColor.DARK_GRAY),
        TAX_COLLECTOR("The Tax Collector", ProgressionManager.Era.SETTLEMENT, 200.0, NamedTextColor.DARK_GREEN),
        WARDEN_OF_FLAMES("Warden of Flames", ProgressionManager.Era.EXPEDITION, 350.0, NamedTextColor.GOLD),
        ENDER_SENTINEL("The Ender Sentinel", ProgressionManager.Era.ASCENSION, 500.0, NamedTextColor.DARK_PURPLE);

        fun getScaledHealth(playerCount: Int): Double {
            return baseHealth * (1.0 + (playerCount - 1) * 0.4)
        }
    }

    // ══════════════════════════════════════════════════════════════
    // BOSS SPAWNING
    // ══════════════════════════════════════════════════════════════

    /**
     * Spawn the Era 0 boss: Hollow Knight
     * A powerful Wither Skeleton that appears at night
     */
    fun spawnHollowKnight(player: Player): Boolean {
        val location = player.location.add(0.0, 0.0, 5.0)
        val world = location.world

        // Spawn the Wither Skeleton
        val entity = world.spawn(location, WitherSkeleton::class.java) { skeleton ->
            skeleton.customName(Component.text("⚔ The Hollow Knight ⚔", NamedTextColor.DARK_GRAY, TextDecoration.BOLD))
            skeleton.isCustomNameVisible = true
            
            // Stats
            skeleton.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.baseValue = BossType.HOLLOW_KNIGHT.baseHealth
            skeleton.health = BossType.HOLLOW_KNIGHT.baseHealth
            skeleton.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE)?.baseValue = 8.0
            skeleton.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED)?.baseValue = 0.28
            skeleton.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE)?.baseValue = 0.8
            
            // Equipment
            skeleton.equipment.helmet = ItemStack(Material.NETHERITE_HELMET)
            skeleton.equipment.chestplate = ItemStack(Material.NETHERITE_CHESTPLATE)
            skeleton.equipment.setItemInMainHand(ItemStack(Material.NETHERITE_SWORD))
            
            // Effects
            skeleton.addPotionEffect(PotionEffect(PotionEffectType.FIRE_RESISTANCE, PotionEffect.INFINITE_DURATION, 0, false, false))
            skeleton.addPotionEffect(PotionEffect(PotionEffectType.GLOWING, PotionEffect.INFINITE_DURATION, 0, false, false))
            
            skeleton.removeWhenFarAway = false
        }

        registerBoss(entity, BossType.HOLLOW_KNIGHT, player)
        
        // Dramatic entrance
        announceSpawn(location, BossType.HOLLOW_KNIGHT)
        
        return true
    }

    /**
     * Spawn the Era 1 boss: Tax Collector
     * An Evoker with minion summoning
     */
    fun spawnTaxCollector(player: Player): Boolean {
        val location = player.location.add(0.0, 0.0, 8.0)
        val world = location.world

        val entity = world.spawn(location, Evoker::class.java) { evoker ->
            evoker.customName(Component.text("💰 The Tax Collector 💰", NamedTextColor.DARK_GREEN, TextDecoration.BOLD))
            evoker.isCustomNameVisible = true
            
            evoker.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.baseValue = BossType.TAX_COLLECTOR.baseHealth
            evoker.health = BossType.TAX_COLLECTOR.baseHealth
            evoker.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE)?.baseValue = 0.6
            
            evoker.addPotionEffect(PotionEffect(PotionEffectType.GLOWING, PotionEffect.INFINITE_DURATION, 0, false, false))
            
            evoker.removeWhenFarAway = false
        }

        registerBoss(entity, BossType.TAX_COLLECTOR, player)
        announceSpawn(location, BossType.TAX_COLLECTOR)
        
        // Spawn minions periodically
        startMinionSpawner(entity, location)
        
        return true
    }

    /**
     * Spawn the Era 2 boss: Warden of Flames
     * A giant Blaze with fire attacks
     */
    fun spawnWardenOfFlames(player: Player): Boolean {
        val location = player.location.add(0.0, 2.0, 8.0)
        val world = location.world

        val entity = world.spawn(location, Blaze::class.java) { blaze ->
            blaze.customName(Component.text("🔥 Warden of Flames 🔥", NamedTextColor.GOLD, TextDecoration.BOLD))
            blaze.isCustomNameVisible = true
            
            blaze.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.baseValue = BossType.WARDEN_OF_FLAMES.baseHealth
            blaze.health = BossType.WARDEN_OF_FLAMES.baseHealth
            
            blaze.addPotionEffect(PotionEffect(PotionEffectType.GLOWING, PotionEffect.INFINITE_DURATION, 0, false, false))
            
            blaze.removeWhenFarAway = false
        }

        registerBoss(entity, BossType.WARDEN_OF_FLAMES, player)
        announceSpawn(location, BossType.WARDEN_OF_FLAMES)
        
        // Fire nova attack
        startFireNovaAttack(entity)
        
        return true
    }

    /**
     * Spawn the Era 3 boss: Ender Sentinel
     * A powerful Enderman with teleport attacks
     */
    fun spawnEnderSentinel(player: Player): Boolean {
        val location = player.location.add(0.0, 0.0, 10.0)
        val world = location.world

        val entity = world.spawn(location, Enderman::class.java) { enderman ->
            enderman.customName(Component.text("👁 The Ender Sentinel 👁", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD))
            enderman.isCustomNameVisible = true
            
            enderman.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.baseValue = BossType.ENDER_SENTINEL.baseHealth
            enderman.health = BossType.ENDER_SENTINEL.baseHealth
            enderman.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE)?.baseValue = 12.0
            enderman.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE)?.baseValue = 1.0
            
            enderman.addPotionEffect(PotionEffect(PotionEffectType.GLOWING, PotionEffect.INFINITE_DURATION, 0, false, false))
            enderman.addPotionEffect(PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 1, false, false))
            
            enderman.removeWhenFarAway = false
        }

        registerBoss(entity, BossType.ENDER_SENTINEL, player)
        announceSpawn(location, BossType.ENDER_SENTINEL)
        
        // Teleport attack
        startTeleportAttack(entity)
        
        return true
    }

    // ══════════════════════════════════════════════════════════════
    // BOSS MECHANICS
    // ══════════════════════════════════════════════════════════════

    private fun registerBoss(entity: LivingEntity, type: BossType, spawner: Player) {
        val boss = EraBoss(entity.uniqueId, type, spawner.uniqueId)
        activeBosses[entity.uniqueId] = boss
        bossParticipants[entity.uniqueId] = mutableSetOf(spawner.uniqueId)
        
        // Create boss bar
        val bossBar = Bukkit.createBossBar(
            "§l${type.displayName}",
            when (type) {
                BossType.HOLLOW_KNIGHT -> org.bukkit.boss.BarColor.WHITE
                BossType.TAX_COLLECTOR -> org.bukkit.boss.BarColor.GREEN
                BossType.WARDEN_OF_FLAMES -> org.bukkit.boss.BarColor.RED
                BossType.ENDER_SENTINEL -> org.bukkit.boss.BarColor.PURPLE
            },
            org.bukkit.boss.BarStyle.SEGMENTED_10
        )
        bossBar.addPlayer(spawner)
        
        // Update boss bar periodically
        object : BukkitRunnable() {
            override fun run() {
                val bossEntity = plugin.server.getEntity(entity.uniqueId) as? LivingEntity
                if (bossEntity == null || bossEntity.isDead) {
                    bossBar.removeAll()
                    cancel()
                    return
                }
                
                val maxHealth = bossEntity.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.baseValue ?: 100.0
                bossBar.progress = (bossEntity.health / maxHealth).coerceIn(0.0, 1.0)
                
                // Add nearby players to boss bar
                bossEntity.getNearbyEntities(30.0, 30.0, 30.0)
                    .filterIsInstance<Player>()
                    .forEach { player ->
                        if (!bossBar.players.contains(player)) {
                            bossBar.addPlayer(player)
                            bossParticipants[entity.uniqueId]?.add(player.uniqueId)
                        }
                    }
            }
        }.runTaskTimer(plugin, 0L, 20L)
    }

    private fun announceSpawn(location: Location, type: BossType) {
        val world = location.world
        
        // Effects
        world.strikeLightningEffect(location)
        world.spawnParticle(Particle.EXPLOSION, location, 5)
        
        // Sound
        world.playSound(location, Sound.ENTITY_WITHER_SPAWN, 2.0f, 0.8f)
        
        // Announce to nearby players
        world.getNearbyPlayers(location, 50.0).forEach { player ->
            player.showTitle(Title.title(
                Component.text(type.displayName, type.color, TextDecoration.BOLD),
                Component.text("A powerful foe has appeared!", NamedTextColor.RED),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(2), Duration.ofMillis(500))
            ))
        }
    }

    private fun startMinionSpawner(boss: Entity, location: Location) {
        object : BukkitRunnable() {
            override fun run() {
                if (!boss.isValid || boss.isDead) {
                    cancel()
                    return
                }
                
                // Spawn 2-3 vindicators
                val count = (2..3).random()
                repeat(count) {
                    val spawnLoc = boss.location.add(
                        (Math.random() * 4 - 2),
                        0.0,
                        (Math.random() * 4 - 2)
                    )
                    boss.world.spawn(spawnLoc, Vindicator::class.java) { minion ->
                        minion.customName(Component.text("Tax Enforcer", NamedTextColor.GREEN))
                        minion.isCustomNameVisible = true
                    }
                }
                
                boss.world.playSound(boss.location, Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1.0f, 1.0f)
            }
        }.runTaskTimer(plugin, 200L, 300L) // Every 15 seconds
    }

    private fun startFireNovaAttack(boss: Entity) {
        object : BukkitRunnable() {
            override fun run() {
                if (!boss.isValid || boss.isDead) {
                    cancel()
                    return
                }
                
                // Fire nova - damage and ignite nearby players
                val nearby = boss.getNearbyEntities(8.0, 8.0, 8.0).filterIsInstance<Player>()
                if (nearby.isNotEmpty()) {
                    // Warning
                    boss.world.playSound(boss.location, Sound.ENTITY_BLAZE_SHOOT, 2.0f, 0.5f)
                    
                    // Delay then explode
                    plugin.server.scheduler.runTaskLater(plugin, Runnable {
                        if (boss.isValid && !boss.isDead) {
                            boss.world.spawnParticle(Particle.FLAME, boss.location, 100, 5.0, 2.0, 5.0)
                            nearby.forEach { player ->
                                if (player.location.distance(boss.location) < 8.0) {
                                    player.damage(6.0, boss as? LivingEntity)
                                    player.fireTicks = 80 // 4 seconds of fire
                                }
                            }
                        }
                    }, 40L) // 2 second delay
                }
            }
        }.runTaskTimer(plugin, 100L, 200L) // Every 10 seconds
    }

    private fun startTeleportAttack(boss: Entity) {
        object : BukkitRunnable() {
            override fun run() {
                if (!boss.isValid || boss.isDead) {
                    cancel()
                    return
                }
                
                // Teleport behind a random nearby player
                val targets = boss.getNearbyEntities(20.0, 20.0, 20.0).filterIsInstance<Player>()
                val target = targets.randomOrNull() ?: return
                
                // Teleport effect
                boss.world.spawnParticle(Particle.PORTAL, boss.location, 30)
                boss.world.playSound(boss.location, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f)
                
                // Teleport behind target
                val behindLocation = target.location.clone()
                    .subtract(target.location.direction.multiply(2.0))
                    .add(0.0, 0.0, 0.0)
                
                boss.teleport(behindLocation)
                
                boss.world.spawnParticle(Particle.PORTAL, boss.location, 30)
                boss.world.playSound(boss.location, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f)
                
                // Attack immediately after teleport
                if (boss is LivingEntity) {
                    (boss as? Mob)?.target = target
                }
            }
        }.runTaskTimer(plugin, 80L, 120L) // Every 6 seconds
    }

    // ══════════════════════════════════════════════════════════════
    // BOSS DEATH HANDLING
    // ══════════════════════════════════════════════════════════════

    @EventHandler
    fun onBossDeath(event: EntityDeathEvent) {
        val entity = event.entity
        val boss = activeBosses[entity.uniqueId] ?: return
        
        activeBosses.remove(entity.uniqueId)
        
        // Get all participants
        val participants = bossParticipants.remove(entity.uniqueId) ?: mutableSetOf()
        
        // Award milestone to all participants
        participants.forEach { playerUUID ->
            val player = plugin.server.getPlayer(playerUUID) ?: return@forEach
            
            val milestone = when (boss.type) {
                BossType.HOLLOW_KNIGHT -> ProgressionManager.Milestone.E0_BOSS
                BossType.TAX_COLLECTOR -> ProgressionManager.Milestone.E1_BOSS
                BossType.WARDEN_OF_FLAMES -> ProgressionManager.Milestone.E2_BOSS
                BossType.ENDER_SENTINEL -> ProgressionManager.Milestone.E3_BOSS
            }
            
            plugin.progressionManager.completeMilestone(player, milestone)
            
            // Extra rewards
            val goldReward = when (boss.type) {
                BossType.HOLLOW_KNIGHT -> 200.0
                BossType.TAX_COLLECTOR -> 500.0
                BossType.WARDEN_OF_FLAMES -> 1000.0
                BossType.ENDER_SENTINEL -> 2000.0
            }
            
            val profile = plugin.identityManager.getPlayer(playerUUID)
            if (profile != null) {
                profile.balance += goldReward
                player.sendMessage(Component.text("  +${goldReward.toInt()} Gold", NamedTextColor.GOLD))
            }
        }
        
        // Victory announcement
        entity.world.getNearbyPlayers(entity.location, 50.0).forEach { player ->
            player.showTitle(Title.title(
                Component.text("VICTORY!", NamedTextColor.GREEN, TextDecoration.BOLD),
                Component.text("${boss.type.displayName} has been defeated!", NamedTextColor.WHITE),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))
            ))
            player.playSound(player.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f)
        }
        
        // Special drops - Use custom items with CustomModelData for unique visuals
        event.drops.clear()
        
        // Era Key Fragment
        event.drops.add(com.projectatlas.visual.CustomItemManager.createEraKeyFragment(boss.type.ordinal))
        
        // Unique weapon drop based on boss type
        val weaponDrop = when (boss.type) {
            BossType.HOLLOW_KNIGHT -> com.projectatlas.visual.CustomItemManager.createHollowKnightBlade()
            BossType.TAX_COLLECTOR -> com.projectatlas.visual.CustomItemManager.createTaxCollectorAxe()
            BossType.WARDEN_OF_FLAMES -> com.projectatlas.visual.CustomItemManager.createWardenFlameSword()
            BossType.ENDER_SENTINEL -> com.projectatlas.visual.CustomItemManager.createEnderSentinelScythe()
        }
        event.drops.add(weaponDrop)
        
        // Bonus XP
        event.droppedExp = when (boss.type) {
            BossType.HOLLOW_KNIGHT -> 500
            BossType.TAX_COLLECTOR -> 1000
            BossType.WARDEN_OF_FLAMES -> 2000
            BossType.ENDER_SENTINEL -> 5000
        }
    }

    @EventHandler
    fun onBossDamage(event: EntityDamageByEntityEvent) {
        val boss = activeBosses[event.entity.uniqueId] ?: return
        
        // Track participants
        val damager = when (val d = event.damager) {
            is Player -> d
            is Projectile -> d.shooter as? Player
            else -> null
        } ?: return
        
        bossParticipants.getOrPut(event.entity.uniqueId) { mutableSetOf() }.add(damager.uniqueId)
    }

    // ══════════════════════════════════════════════════════════════
    // NATURAL BOSS SPAWNING
    // ══════════════════════════════════════════════════════════════

    /**
     * Check if a player should encounter an Era boss naturally
     */
    fun checkNaturalBossSpawn(player: Player): Boolean {
        val era = plugin.progressionManager.getPlayerEra(player)
        val completed = plugin.progressionManager.getCompletedMilestones(player)
        
        // Check if they've completed all non-boss milestones for their era
        val eraRequirements = ProgressionManager.Milestone.forEra(era)
            .filter { !it.id.endsWith("_boss") }
        
        val allNonBossComplete = eraRequirements.all { completed.contains(it.id) }
        
        if (!allNonBossComplete) return false
        
        // Already killed boss?
        val bossId = "${era.name.lowercase()}_boss"
        if (completed.any { it.contains(bossId) }) return false
        
        // Spawn appropriate boss
        return when (era) {
            ProgressionManager.Era.AWAKENING -> spawnHollowKnight(player)
            ProgressionManager.Era.SETTLEMENT -> spawnTaxCollector(player)
            ProgressionManager.Era.EXPEDITION -> spawnWardenOfFlames(player)
            ProgressionManager.Era.ASCENSION -> spawnEnderSentinel(player)
            else -> false
        }
    }
}
