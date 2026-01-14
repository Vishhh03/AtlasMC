package com.projectatlas.dungeon

import com.projectatlas.AtlasPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.*
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerRespawnEvent
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Procedural Dungeon Manager
 * Integrates the new Generator and Room systems.
 */
class DungeonManager(private val plugin: AtlasPlugin) : Listener {

    private val generator = DungeonGenerator()
    private val activeDungeons = ConcurrentHashMap<UUID, ProceduralDungeon>() // Player UUID -> Instance
    private val dungeonLog = ConcurrentHashMap<UUID, ProceduralDungeon>() // Instance ID -> Instance
    
    // Mapping old types to new Themes
    enum class DungeonType(
        val displayName: String,
        val theme: DungeonTheme,
        val difficulty: Int
    ) {
        SHADOW_CAVERN("Shadow Cavern", DungeonTheme.CRYPT, 2),
        INFERNAL_PIT("Infernal Pit", DungeonTheme.INFERNAL, 3),
        FROZEN_DEPTHS("Frozen Depths", DungeonTheme.CRYPT, 2), // TODO: Add Ice Theme
        VOID_ARENA("Void Arena", DungeonTheme.CRYPT, 4), // TODO: Add End Theme
        ANCIENT_TEMPLE("Ancient Temple", DungeonTheme.TEMPLE, 3),
        PIRATE_COVE("Pirate's Cove", DungeonTheme.TEMPLE, 2),
        BLOOD_MOON("Blood Moon", DungeonTheme.INFERNAL, 5),
        DRAGONS_LAIR("Dragon's Lair", DungeonTheme.INFERNAL, 5)
    }

    /**
     * Get the active instance for a player
     */
    fun getActiveInstance(player: Player): ProceduralDungeon? {
        return activeDungeons[player.uniqueId]
    }

    fun enterDungeon(player: Player, type: DungeonType): Boolean {
        if (activeDungeons.containsKey(player.uniqueId)) {
            player.sendMessage(Component.text("You are already in a dungeon!", NamedTextColor.RED))
            return false
        }
        
        // 1. Party Handling
        val partyMembers = plugin.partyManager.getOnlinePartyMembers(player)
        if (partyMembers.any { activeDungeons.containsKey(it.uniqueId) }) {
             player.sendMessage(Component.text("Someone in your party is already in a dungeon!", NamedTextColor.RED))
             return false
        }
        
        // 2. Generate Logic
        player.sendMessage(Component.text("Generating ${type.displayName}...", NamedTextColor.YELLOW))
        val rooms = generator.checkGraph(type.difficulty)
        
        // 3. Find Space & Build
        // We'll use a random offset in the End or a dedicated world to avoid collisions
        // Simple hack: x = instanceIndex * 5000
        val instanceId = UUID.randomUUID()
        val offset = (dungeonLog.size * 5000)
        val world = plugin.server.getWorld("world_the_end") ?: plugin.server.worlds[0]
        val startLoc = Location(world, offset.toDouble() + 500.0, 100.0, 500.0)
        
        generator.buildDungeon(startLoc, rooms, type.theme)
        
        // 4. Create Instance
        val playerIds = partyMembers.map { it.uniqueId }.toMutableSet()
        val dungeon = ProceduralDungeon(instanceId, plugin, type.theme, rooms, playerIds, startLoc)
        
        dungeonLog[instanceId] = dungeon
        
        // 5. Teleport Players
        val spawn = startLoc.clone().add(0.0, 2.0, 0.0)
        partyMembers.forEach { member ->
            activeDungeons[member.uniqueId] = dungeon
            member.teleport(spawn)
            member.sendMessage(Component.text("Entered ${type.displayName}!", NamedTextColor.GREEN))
            member.playSound(member.location, Sound.EVENT_RAID_HORN, 1.0f, 1.0f)
        }
        
        return true
    }
    
    fun leaveDungeon(player: Player) {
        val dungeon = activeDungeons[player.uniqueId] ?: return
        
        activeDungeons.remove(player.uniqueId)
        dungeon.players.remove(player.uniqueId)
        
        player.teleport(player.world.spawnLocation) // Or saved location
        player.sendMessage(Component.text("Left the dungeon.", NamedTextColor.YELLOW))
        
        if (dungeon.players.isEmpty()) {
            dungeon.active = false
            // TODO: Unload/Cleanup chunks?
        }
    }

    @EventHandler
    fun onDeath(event: PlayerDeathEvent) {
        val player = event.entity
        val dungeon = activeDungeons[player.uniqueId] ?: return
        
        event.drops.clear()
        event.keepInventory = true
        event.keepLevel = true
        
        player.sendMessage(Component.text("You fell in the dungeon!", NamedTextColor.RED))
    }
    
    @EventHandler
    fun onRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        val dungeon = activeDungeons[player.uniqueId] ?: return
        
        // Respawn at start of dungeon if still active
        if (dungeon.active) {
            event.respawnLocation = dungeon.spawnLocation
        } else {
            activeDungeons.remove(player.uniqueId)
        }
    }
}
