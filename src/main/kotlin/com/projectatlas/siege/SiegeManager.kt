package com.projectatlas.siege

import com.projectatlas.AtlasPlugin
import com.projectatlas.city.City
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class SiegeManager(private val plugin: AtlasPlugin) : Listener {

    private val activeSieges = ConcurrentHashMap<String, ActiveSiege>()
    
    companion object {
        const val SIEGE_COOLDOWN_MS = 2 * 60 * 60 * 1000L // 2 hours
        const val WAVES_PER_SIEGE = 5
        const val MOBS_PER_WAVE_BASE = 5
    }

    data class ActiveSiege(
        val cityId: String,
        val startTime: Long = System.currentTimeMillis(),
        var currentWave: Int = 1,
        var mobsRemaining: Int = 0,
        var bossBar: BossBar? = null,
        val spawnedMobs: MutableList<UUID> = mutableListOf()
    )

    fun canSiege(city: City): Boolean {
        val timeSinceLast = System.currentTimeMillis() - city.lastSiegeTime
        return timeSinceLast >= SIEGE_COOLDOWN_MS && !activeSieges.containsKey(city.id)
    }

    fun startSiege(city: City, triggerLocation: Location): Boolean {
        if (!canSiege(city)) return false
        
        val siege = ActiveSiege(cityId = city.id)
        activeSieges[city.id] = siege
        
        // Create boss bar
        val bossBar = BossBar.bossBar(
            Component.text("⚔ SIEGE: ${city.name} - Wave 1/$WAVES_PER_SIEGE", NamedTextColor.RED),
            1f,
            BossBar.Color.RED,
            BossBar.Overlay.PROGRESS
        )
        siege.bossBar = bossBar
        
        // Show boss bar to all city members online
        city.members.forEach { memberId ->
            plugin.server.getPlayer(memberId)?.showBossBar(bossBar)
        }
        
        // Announce
        plugin.server.broadcast(Component.text("═══════════════════════════════", NamedTextColor.DARK_RED))
        plugin.server.broadcast(Component.text("  ⚔ SIEGE BEGINS ON ${city.name.uppercase()}!", NamedTextColor.RED))
        plugin.server.broadcast(Component.text("  Defend your city!", NamedTextColor.YELLOW))
        plugin.server.broadcast(Component.text("═══════════════════════════════", NamedTextColor.DARK_RED))
        
        // Play war horn
        plugin.server.onlinePlayers.forEach { 
            it.playSound(it.location, Sound.EVENT_RAID_HORN, 2.0f, 0.8f)
        }
        
        // Start first wave
        spawnWave(city, siege, triggerLocation)
        return true
    }

    private fun spawnWave(city: City, siege: ActiveSiege, location: Location) {
        val world = location.world
        val mobCount = MOBS_PER_WAVE_BASE + (siege.currentWave * 2) // Wave 1: 7, Wave 5: 15
        siege.mobsRemaining = mobCount
        
        // Update boss bar
        siege.bossBar?.name(Component.text("⚔ SIEGE: ${city.name} - Wave ${siege.currentWave}/$WAVES_PER_SIEGE", NamedTextColor.RED))
        siege.bossBar?.progress(1f)
        
        // Spawn mobs in a ring around the location
        val radius = 10.0
        for (i in 0 until mobCount) {
            val angle = (2 * Math.PI * i) / mobCount
            val spawnX = location.x + radius * Math.cos(angle)
            val spawnZ = location.z + radius * Math.sin(angle)
            val spawnLoc = Location(world, spawnX, location.y, spawnZ)
            
            val mob = when {
                siege.currentWave >= 4 && i % 3 == 0 -> spawnBreacher(world, spawnLoc)
                siege.currentWave >= 3 && i % 4 == 0 -> spawnSniper(world, spawnLoc)
                else -> spawnGrunt(world, spawnLoc)
            }
            
            siege.spawnedMobs.add(mob.uniqueId)
        }
        
        // Announce wave
        city.members.forEach { memberId ->
            plugin.server.getPlayer(memberId)?.sendMessage(
                Component.text("Wave ${siege.currentWave} incoming! $mobCount hostiles!", NamedTextColor.RED)
            )
        }
    }

    private fun spawnGrunt(world: org.bukkit.World, loc: Location): Entity {
        return world.spawn(loc, Zombie::class.java) { zombie ->
            zombie.customName(Component.text("Siege Grunt", NamedTextColor.GRAY))
            zombie.isCustomNameVisible = true
            zombie.addPotionEffect(PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 0, false, false))
        }
    }

    private fun spawnBreacher(world: org.bukkit.World, loc: Location): Entity {
        return world.spawn(loc, Vindicator::class.java) { vin ->
            vin.customName(Component.text("Siege Breacher", NamedTextColor.DARK_RED))
            vin.isCustomNameVisible = true
            vin.addPotionEffect(PotionEffect(PotionEffectType.STRENGTH, PotionEffect.INFINITE_DURATION, 1, false, false))
            vin.addPotionEffect(PotionEffect(PotionEffectType.RESISTANCE, PotionEffect.INFINITE_DURATION, 1, false, false))
        }
    }

    private fun spawnSniper(world: org.bukkit.World, loc: Location): Entity {
        return world.spawn(loc, Skeleton::class.java) { skel ->
            skel.customName(Component.text("Siege Sniper", NamedTextColor.GOLD))
            skel.isCustomNameVisible = true
            skel.addPotionEffect(PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 1, false, false))
        }
    }

    @EventHandler
    fun onMobKill(event: EntityDeathEvent) {
        val entity = event.entity
        
        // Find which siege this mob belongs to
        for ((cityId, siege) in activeSieges) {
            if (siege.spawnedMobs.contains(entity.uniqueId)) {
                siege.spawnedMobs.remove(entity.uniqueId)
                siege.mobsRemaining--
                
                // Update boss bar
                val progress = siege.mobsRemaining.toFloat() / (MOBS_PER_WAVE_BASE + (siege.currentWave * 2))
                siege.bossBar?.progress(progress.coerceIn(0f, 1f))
                
                // Check wave complete
                if (siege.mobsRemaining <= 0) {
                    onWaveComplete(cityId, siege)
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
                    player.sendMessage(Component.text("Wave complete! Prepare for wave ${siege.currentWave}...", NamedTextColor.GREEN))
                    player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f)
                }
            }
            
            // Delay next wave 10 seconds
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                val activeCity = plugin.cityManager.getCity(cityId)
                val activeSiege = activeSieges[cityId]
                if (activeCity != null && activeSiege != null) {
                    // Find a spawn location (use first chunk center)
                    val chunkKey = activeCity.claimedChunks.firstOrNull() ?: return@Runnable
                    val parts = chunkKey.split(":")
                    if (parts.size < 2) return@Runnable
                    val coords = parts[1].split(",")
                    if (coords.size < 2) return@Runnable
                    val world = plugin.server.getWorld(parts[0]) ?: return@Runnable
                    val loc = Location(world, coords[0].toInt() * 16.0 + 8, 64.0, coords[1].toInt() * 16.0 + 8)
                    val highestY = world.getHighestBlockYAt(loc.blockX, loc.blockZ).toDouble() + 1
                    loc.y = highestY
                    spawnWave(activeCity, activeSiege, loc)
                }
            }, 200L) // 10 seconds
        }
    }

    private fun endSiege(city: City, siege: ActiveSiege, victory: Boolean) {
        activeSieges.remove(city.id)
        
        // Hide boss bar
        city.members.forEach { memberId ->
            siege.bossBar?.let { plugin.server.getPlayer(memberId)?.hideBossBar(it) }
        }
        
        // Cleanup remaining mobs
        siege.spawnedMobs.forEach { mobId ->
            plugin.server.worlds.forEach { world ->
                world.getEntity(mobId)?.remove()
            }
        }
        
        if (victory) {
            // Reward: Gold based on waves
            val reward = 500.0 * WAVES_PER_SIEGE
            city.treasury += reward
            city.lastSiegeTime = System.currentTimeMillis()
            plugin.cityManager.saveCity(city)
            
            plugin.server.broadcast(Component.text("═══════════════════════════════", NamedTextColor.GREEN))
            plugin.server.broadcast(Component.text("  ✓ ${city.name.uppercase()} DEFENDED!", NamedTextColor.GREEN))
            plugin.server.broadcast(Component.text("  Treasury +${reward}g", NamedTextColor.GOLD))
            plugin.server.broadcast(Component.text("═══════════════════════════════", NamedTextColor.GREEN))
            
            city.members.forEach { memberId ->
                plugin.server.getPlayer(memberId)?.playSound(
                    plugin.server.getPlayer(memberId)!!.location,
                    Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f
                )
            }
        } else {
            // Defeat: Lose treasury %
            val loss = city.treasury * 0.25
            city.treasury -= loss
            city.infrastructure.coreHealth -= 25
            city.lastSiegeTime = System.currentTimeMillis()
            plugin.cityManager.saveCity(city)
            
            plugin.server.broadcast(Component.text("═══════════════════════════════", NamedTextColor.DARK_RED))
            plugin.server.broadcast(Component.text("  ✗ ${city.name.uppercase()} FELL!", NamedTextColor.RED))
            plugin.server.broadcast(Component.text("  Treasury -${loss}g, Core -25 HP", NamedTextColor.GRAY))
            plugin.server.broadcast(Component.text("═══════════════════════════════", NamedTextColor.DARK_RED))
        }
    }
    
    fun getActiveSiege(cityId: String): ActiveSiege? = activeSieges[cityId]
}
