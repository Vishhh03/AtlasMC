package com.projectatlas.territory

import com.projectatlas.AtlasPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.block.Chest
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.scheduler.BukkitRunnable
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class OutpostType(val material: Material, val produceInterval: Int, val amount: Int) {
    IRON_MINE(Material.IRON_ORE, 20, 1), // 1 ore per 20s
    COAL_PIT(Material.COAL, 15, 2),
    GOLD_PAN(Material.GOLD_NUGGET, 30, 3),
    DIAMOND_DRILL(Material.DIAMOND, 300, 1) // 1 diamond per 5m (Very Valuable)
}

data class Outpost(
    val id: String,
    val name: String,
    val type: OutpostType,
    val center: Location,
    var ownerCityId: String? = null,
    var captureProgress: Double = 0.0, // 0 to 100
    var capturingCityId: String? = null
)

class OutpostManager(private val plugin: AtlasPlugin) : Listener {

    private val outposts = ConcurrentHashMap<String, Outpost>()
    
    init {
        // Ticking Task
        object : BukkitRunnable() {
            override fun run() {
                tick()
            }
        }.runTaskTimer(plugin, 20L, 20L) // 1s tick
    }
    
    // Admin command to create
    fun createOutpost(name: String, type: OutpostType, loc: Location) {
        val id = UUID.randomUUID().toString()
        val outpost = Outpost(id, name, type, loc)
        outposts[id] = outpost
        
        // Visual indicator
        loc.block.type = Material.BEDROCK
        loc.clone().add(0.0, 1.0, 0.0).block.type = Material.BEACON
        
        // Output Chest
        val chestLoc = loc.clone().add(1.0, 0.0, 0.0)
        chestLoc.block.type = Material.CHEST
        
        plugin.logger.info("Created outpost $name at $loc")
    }

    private fun tick() {
        outposts.values.forEach { outpost ->
            handleCapture(outpost)
            handleProduction(outpost)
            visualize(outpost)
        }
    }
    
    private fun handleCapture(outpost: Outpost) {
        // Check for players in radius
        val radius = 5.0
        val playersNear = outpost.center.getNearbyPlayers(radius)
        
        if (playersNear.isEmpty()) {
            if (outpost.captureProgress > 0 && outpost.captureProgress < 100) {
                 outpost.captureProgress -= 2.0 // Decay
                 if (outpost.captureProgress < 0) outpost.captureProgress = 0.0
            }
            return
        }
        
        // Logic: If players from SAME city -> capture. If mixed -> contested (pause).
        val cities = playersNear.mapNotNull { 
            plugin.identityManager.getPlayer(it.uniqueId)?.cityId 
        }.distinct()
        
        if (cities.size > 1) {
             // Contested
             playersNear.forEach { it.sendActionBar(Component.text("Outpost Contested!", NamedTextColor.RED)) }
             return
        }
        
        val cityId = cities.firstOrNull() ?: return // No city? Can't capture.
        
        // Capturing
        if (outpost.ownerCityId == cityId) {
            // Already owned - Secure/Heal?
            if (outpost.captureProgress < 100.0) outpost.captureProgress += 5.0
        } else {
            // New conqueror
            if (outpost.capturingCityId != cityId) {
                outpost.capturingCityId = cityId
                outpost.captureProgress = 0.0 // Reset if changed hands
            }
            
            outpost.captureProgress += 5.0 // +5% per second
            
            if (outpost.captureProgress >= 100.0) {
                outpost.ownerCityId = cityId
                val city = plugin.cityManager.getCity(cityId)
                plugin.server.broadcast(Component.text("${city?.name} has captured ${outpost.name}!", NamedTextColor.YELLOW))
                outpost.center.world.playSound(outpost.center, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f)
            }
            
            playersNear.forEach { it.sendActionBar(Component.text("Capturing: ${outpost.captureProgress.toInt()}%", NamedTextColor.YELLOW)) }
        }
    }
    
    private fun handleProduction(outpost: Outpost) {
        if (outpost.ownerCityId == null) return
        if (outpost.captureProgress < 100.0) return // Must fully own
        
        // Check time
        val now = System.currentTimeMillis() / 1000
        if (now % outpost.type.produceInterval == 0L) {
             val chestLoc = outpost.center.clone().add(1.0, 0.0, 0.0)
             if (chestLoc.block.type == Material.CHEST) {
                 val chest = chestLoc.block.state as Chest
                 val item = org.bukkit.inventory.ItemStack(outpost.type.material, outpost.type.amount)
                 val left = chest.inventory.addItem(item)
                 if (left.isNotEmpty()) {
                     // Full
                 } else {
                     // Sparkle
                     outpost.center.world.spawnParticle(org.bukkit.Particle.HEART, chestLoc.add(0.5, 1.0, 0.5), 5)
                 }
             }
        }
    }
    
    private fun visualize(outpost: Outpost) {
        val color = if (outpost.ownerCityId != null) org.bukkit.Particle.FLAME else org.bukkit.Particle.CLOUD
        // Draw circle? Too expensive? Just a beacon beam update or hologram
    }
}
