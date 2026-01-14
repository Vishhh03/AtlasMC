package com.projectatlas.dungeon

import com.projectatlas.AtlasPlugin
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import java.util.UUID

/**
 * Represents a live, procedural dungeon instance.
 */
class ProceduralDungeon(
    val id: UUID,
    val plugin: AtlasPlugin,
    val theme: DungeonTheme,
    val rooms: List<DungeonRoom>,
    val players: MutableSet<UUID>,
    val startLocation: Location
) {
    var active: Boolean = true
    private var currentRoomIndex: Int = 0 // Tracks "progress" loosely
    private val completedRooms = mutableSetOf<Int>() // HashCode of rooms or Index
    val spawnLocation = startLocation.clone().add(0.0, 2.0, 0.0)
    
    // Bossbar
    val bossBar = BossBar.bossBar(
        Component.text("Exploring: ${theme.name.lowercase().replaceFirstChar { it.uppercase() }}", NamedTextColor.RED),
        1.0f,
        BossBar.Color.PURPLE,
        BossBar.Overlay.NOTCHED_10
    )

    init {
        // Start loop
        object : BukkitRunnable() {
            override fun run() {
                if (!active || players.isEmpty()) {
                    cancel()
                    return
                }
                tick()
            }
        }.runTaskTimer(plugin, 20L, 20L)
    }

    fun tick() {
        val onlinePlayers = players.mapNotNull { plugin.server.getPlayer(it) }
        
        // Update BossBar
        onlinePlayers.forEach { it.showBossBar(bossBar) }
        
        // Check Room Logic
        for (player in onlinePlayers) {
            val room = getRoomAt(player.location) ?: continue
            
            if (!room.cleared && !room.active) {
                activateRoom(room, onlinePlayers)
            }
        }
    }
    
    private fun getRoomAt(loc: Location): DungeonRoom? {
        // Map world coords back to grid
        // startLocation is (0,0) in grid terms
        
        val relX = (loc.blockX - startLocation.blockX)
        val relZ = (loc.blockZ - startLocation.blockZ)
        
        // 64 is GRID_SIZE
        val gridX = Math.round(relX.toDouble() / 64.0).toInt()
        val gridZ = Math.round(relZ.toDouble() / 64.0).toInt()
        
        return rooms.find { it.x == gridX && it.z == gridZ }
    }
    
    private fun activateRoom(room: DungeonRoom, players: List<Player>) {
        room.active = true
        
        players.forEach { 
            it.sendMessage(Component.text("entered ${room.type.name}...", NamedTextColor.GRAY))
            it.playSound(it.location, Sound.BLOCK_IRON_DOOR_CLOSE, 1.0f, 0.5f)
        }
        
        // Spawn Mobs logic
        if (room.type == RoomType.COMBAT_ARENA || room.type == RoomType.BOSS_ROOM) {
            spawnMobs(room, players.size)
            
            // Lock room? (Visual barriers)
        } else {
            // instant clear for non-combat rooms
            room.cleared = true
            room.active = false
            players.forEach { it.playSound(it.location, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f) }
        }
    }
    
    private fun spawnMobs(room: DungeonRoom, scaling: Int) {
        val center = startLocation.clone().add((room.x * 64).toDouble(), 1.0, (room.z * 64).toDouble())
        
        val mobCount = if (room.type == RoomType.BOSS_ROOM) 1 else 5 * scaling
        
        // Spawn logic in a task to stagger
        object : BukkitRunnable() {
            var spawned = 0
            val aliveMobs = mutableListOf<UUID>()
            
            override fun run() {
                 if (!active) { cancel(); return }
                 
                 // Check if mobs died
                 aliveMobs.removeIf { plugin.server.getEntity(it)?.isValid != true }
                 
                 if (aliveMobs.isEmpty() && spawned >= mobCount) {
                     // Room Cleared!
                     room.cleared = true
                     room.active = false
                     players.mapNotNull { plugin.server.getPlayer(it) }.forEach { 
                         it.sendMessage(Component.text("Room Cleared!", NamedTextColor.GREEN))
                         it.playSound(it.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f)
                     }
                     if (room.type == RoomType.BOSS_ROOM) finishDungeon(true)
                     cancel()
                     return
                 }
                 
                 // Spawn more if needed
                 if (spawned < mobCount && aliveMobs.size < 5 + (scaling * 2)) {
                     val type = if (room.type == RoomType.BOSS_ROOM) theme.boss else theme.mobs.random()
                     
                     val spawnLoc = center.clone().add(
                        (Math.random() - 0.5) * 10,
                        1.0,
                        (Math.random() - 0.5) * 10
                     )
                     
                     val mob = center.world.spawnEntity(spawnLoc, type)
                     // Buff mob?
                     aliveMobs.add(mob.uniqueId)
                     spawned++
                     
                     // Atmosphere
                     center.world.spawnParticle(Particle.CLOUD, spawnLoc, 10)
                 }
            }
        }.runTaskTimer(plugin, 0L, 20L)
    }
    
    fun finishDungeon(success: Boolean) {
        active = false
        val onlinePlayers = players.mapNotNull { plugin.server.getPlayer(it) }
        onlinePlayers.forEach { 
            it.hideBossBar(bossBar)
            if (success) {
                it.sendMessage(Component.text("🏆 Dungeon Completed! 🏆", NamedTextColor.GOLD))
                // Give Rewards
                plugin.economyManager.deposit(it.uniqueId, 1000.0)
            } else {
                it.sendMessage(Component.text("☠ Dungeon Failed", NamedTextColor.RED))
            }
            
            // Teleport out after 10s
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                it.teleport(it.world.spawnLocation)
            }, 200L)
        }
    }
}
