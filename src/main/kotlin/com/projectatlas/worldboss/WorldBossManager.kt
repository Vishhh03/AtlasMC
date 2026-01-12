package com.projectatlas.worldboss

import com.projectatlas.AtlasPlugin
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Wither
import org.bukkit.entity.WitherSkeleton
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * World Boss Events - Massive bosses that require coordinated effort!
 * Players who contribute damage share the rewards.
 */
class WorldBossManager(private val plugin: AtlasPlugin) : Listener {
    
    data class ActiveBoss(
        val entity: LivingEntity,
        val type: BossType,
        val damageContributors: MutableMap<UUID, Double> = ConcurrentHashMap(),
        var bossBar: BossBar? = null,
        var taskId: BukkitTask? = null
    )
    
    enum class BossType(val displayName: String, val entityType: EntityType, val health: Double, val goldReward: Double, val xpReward: Long) {
        ANCIENT_WITHER("The Ancient Wither", EntityType.WITHER, 1000.0, 5000.0, 500),
        CORRUPTED_GUARDIAN("Corrupted Guardian", EntityType.ELDER_GUARDIAN, 500.0, 2500.0, 250),
        ENDER_TITAN("Ender Titan", EntityType.ENDERMAN, 750.0, 3500.0, 350),
        BLAZING_INFERNO("Blazing Inferno", EntityType.BLAZE, 600.0, 3000.0, 300),
        PHANTOM_LORD("The Phantom Lord", EntityType.PHANTOM, 400.0, 2000.0, 200),
        IRON_COLOSSUS("Iron Colossus", EntityType.IRON_GOLEM, 1200.0, 6000.0, 600),
        VOID_SERPENT("Void Serpent", EntityType.ENDER_DRAGON, 2000.0, 10000.0, 1000)
    }
    
    private var activeBoss: ActiveBoss? = null
    private var lastBossTime = 0L
    private val BOSS_COOLDOWN = 30 * 60 * 1000L // 30 minutes
    
    fun spawnWorldBoss(location: Location, type: BossType = BossType.entries.random()): Boolean {
        if (activeBoss != null) {
            return false // Already have a boss
        }
        
        val now = System.currentTimeMillis()
        if (now - lastBossTime < BOSS_COOLDOWN) {
            return false
        }
        
        val entity = location.world.spawnEntity(location, type.entityType) as? LivingEntity ?: return false
        
        // Buff the boss
        entity.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.baseValue = type.health
        entity.health = type.health
        entity.customName(Component.text("☠ ${type.displayName} ☠", NamedTextColor.DARK_RED, TextDecoration.BOLD))
        entity.isCustomNameVisible = true
        
        // Special effects for Wither type
        if (entity is Wither) {
            entity.setCanTravelThroughPortals(false)
        }
        
        // Create boss bar
        val bar = BossBar.bossBar(
            Component.text("☠ ${type.displayName} ☠", NamedTextColor.DARK_RED),
            1.0f,
            BossBar.Color.RED,
            BossBar.Overlay.NOTCHED_10
        )
        
        plugin.server.onlinePlayers.forEach { it.showBossBar(bar) }
        
        val boss = ActiveBoss(entity, type, bossBar = bar)
        activeBoss = boss
        
        // Update task
        boss.taskId = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            if (!entity.isValid || entity.isDead) {
                boss.taskId?.cancel()
                return@Runnable
            }
            
            // Update boss bar
            val healthPercent = entity.health / type.health
            bar.progress(healthPercent.toFloat().coerceIn(0f, 1f))
            
            // Particle aura
            entity.world.spawnParticle(Particle.SMOKE, entity.location.add(0.0, 2.0, 0.0), 20, 1.0, 1.0, 1.0, 0.05)
            
            // Periodic roar
            if (plugin.server.currentTick % 200 == 0) {
                entity.world.playSound(entity.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.5f)
            }
        }, 0L, 20L)
        
        // Announce
        plugin.server.broadcast(Component.empty())
        plugin.server.broadcast(Component.text("═══════════════════════════════", NamedTextColor.DARK_RED))
        plugin.server.broadcast(Component.text("  ☠ WORLD BOSS SPAWNED ☠", NamedTextColor.RED, TextDecoration.BOLD))
        plugin.server.broadcast(Component.text("  ${type.displayName}", NamedTextColor.DARK_RED))
        plugin.server.broadcast(Component.text("  Location: ${location.blockX}, ${location.blockY}, ${location.blockZ}", NamedTextColor.GRAY))
        plugin.server.broadcast(Component.text("  Reward: ${type.goldReward}g + ${type.xpReward} XP", NamedTextColor.GOLD))
        plugin.server.broadcast(Component.text("═══════════════════════════════", NamedTextColor.DARK_RED))
        plugin.server.broadcast(Component.empty())
        
        plugin.server.onlinePlayers.forEach { 
            it.playSound(it.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.5f)
        }
        
        lastBossTime = now
        return true
    }
    
    @EventHandler
    fun onBossDamage(event: EntityDamageByEntityEvent) {
        val boss = activeBoss ?: return
        if (event.entity.uniqueId != boss.entity.uniqueId) return
        
        val damager = when (val d = event.damager) {
            is Player -> d
            is org.bukkit.entity.Projectile -> d.shooter as? Player
            else -> null
        } ?: return
        
        // Track damage contribution
        boss.damageContributors.merge(damager.uniqueId, event.finalDamage) { old, new -> old + new }
    }
    
    @EventHandler
    fun onBossDeath(event: EntityDeathEvent) {
        val boss = activeBoss ?: return
        if (event.entity.uniqueId != boss.entity.uniqueId) return
        
        // Hide boss bar
        boss.bossBar?.let { bar ->
            plugin.server.onlinePlayers.forEach { it.hideBossBar(bar) }
        }
        boss.taskId?.cancel()
        
        // Calculate contributions
        val totalDamage = boss.damageContributors.values.sum()
        if (totalDamage <= 0) {
            activeBoss = null
            return
        }
        
        // Announce and distribute rewards
        plugin.server.broadcast(Component.empty())
        plugin.server.broadcast(Component.text("═══════════════════════════════", NamedTextColor.GOLD))
        plugin.server.broadcast(Component.text("  ✓ WORLD BOSS DEFEATED!", NamedTextColor.GREEN, TextDecoration.BOLD))
        plugin.server.broadcast(Component.text("  ${boss.type.displayName} has fallen!", NamedTextColor.YELLOW))
        
        // Top contributors
        val sorted = boss.damageContributors.entries.sortedByDescending { it.value }
        plugin.server.broadcast(Component.text("  Top Contributors:", NamedTextColor.GRAY))
        
        sorted.take(5).forEachIndexed { index, (uuid, damage) ->
            val player = Bukkit.getOfflinePlayer(uuid)
            val percent = (damage / totalDamage * 100).toInt()
            val reward = (boss.type.goldReward * damage / totalDamage).toLong()
            val xpReward = (boss.type.xpReward * damage / totalDamage).toLong()
            
            plugin.server.broadcast(Component.text("    ${index + 1}. ${player.name}: $percent% (${reward}g)", NamedTextColor.GOLD))
            
            // Pay rewards to online players
            Bukkit.getPlayer(uuid)?.let { onlinePlayer ->
                val profile = plugin.identityManager.getPlayer(uuid)
                if (profile != null) {
                    profile.balance += reward
                    plugin.identityManager.saveProfile(uuid)
                }
                plugin.identityManager.grantXp(onlinePlayer, xpReward)
                onlinePlayer.playSound(onlinePlayer.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f)
            }
        }
        
        plugin.server.broadcast(Component.text("═══════════════════════════════", NamedTextColor.GOLD))
        plugin.server.broadcast(Component.empty())
        
        // Drop rare loot at location
        event.entity.world.dropItemNaturally(event.entity.location, ItemStack(Material.NETHER_STAR))
        event.entity.world.dropItemNaturally(event.entity.location, ItemStack(Material.DIAMOND, 5))
        
        // Spawn relic chance
        if (Math.random() < 0.25) {
            plugin.relicManager.spawnRelicInWorld(event.entity.location)
        }
        
        activeBoss = null
    }
    
    fun hasActiveBoss(): Boolean = activeBoss != null
    
    // Force spawn (admin command)
    fun forceSpawn(player: Player, typeName: String? = null): Boolean {
        val type = if (typeName != null) {
            BossType.entries.find { it.name.equals(typeName, true) } ?: BossType.entries.random()
        } else {
            BossType.entries.random()
        }
        return spawnWorldBoss(player.location, type)
    }
}
