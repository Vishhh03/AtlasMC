package com.projectatlas.dungeon

import com.projectatlas.AtlasPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.*
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.entity.EntityType
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerKickEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
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
    private val entryLocations = ConcurrentHashMap<UUID, Location>() // Track where players entered from
    
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
        
        generator.buildDungeon(plugin, startLoc, rooms, type.theme) {
            // 4. Create Instance
            val playerIds = partyMembers.map { it.uniqueId }.toMutableSet()
            val dungeon = ProceduralDungeon(instanceId, plugin, type.theme, type.difficulty, rooms, playerIds, startLoc)
            
            dungeonLog[instanceId] = dungeon
            
            // 5. Teleport Players
            val spawn = startLoc.clone().add(0.0, 2.0, 0.0)
            partyMembers.forEach { member ->
                // Save entry location
                entryLocations[member.uniqueId] = member.location
                
                activeDungeons[member.uniqueId] = dungeon
                member.teleport(spawn)
                member.sendMessage(Component.text("Entered ${type.displayName}!", NamedTextColor.GREEN))
                member.playSound(member.location, Sound.EVENT_RAID_HORN, 1.0f, 1.0f)
            }
        }
        
        return true
    }
    
    fun leaveDungeon(player: Player) {
        val dungeon = activeDungeons[player.uniqueId] ?: return
        
        activeDungeons.remove(player.uniqueId)
        dungeon.players.remove(player.uniqueId)
        player.hideBossBar(dungeon.bossBar)
        
        // Teleport back to entry location or spawn
        val returnLoc = entryLocations.remove(player.uniqueId) ?: player.world.spawnLocation
        player.teleport(returnLoc)
        player.sendMessage(Component.text("Left the dungeon.", NamedTextColor.YELLOW))
        
        if (dungeon.players.isEmpty()) {
            // Idempotency check with delay to prevent race conditions
            if (dungeon.active) {
                plugin.server.scheduler.runTaskLater(plugin, Runnable {
                    if (dungeon.active) {
                        dungeon.cleanup()
                        dungeon.active = false // Mark inactive after cleanup
                        dungeonLog.remove(dungeon.id)
                    }
                }, 60L) // 3-second delay
            }
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        // If player quits mid-dungeon, remove them. 
        // Logic: They "flee" or "disconnect". 
        // To be safe and avoid getting stuck:
        if (activeDungeons.containsKey(event.player.uniqueId)) {
            leaveDungeon(event.player)
        }
    }

    @EventHandler
    fun onKick(event: PlayerKickEvent) {
        if (activeDungeons.containsKey(event.player.uniqueId)) {
            leaveDungeon(event.player)
        }
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        // Check if player is stranded in world_the_end without an active dungeon
        if (event.player.world.name == "world_the_end" && !activeDungeons.containsKey(event.player.uniqueId)) {
            event.player.teleport(event.player.world.spawnLocation)
            event.player.sendMessage(Component.text("You were moved to spawn from a closed dungeon instance.", NamedTextColor.YELLOW))
        }
    }

    @EventHandler
    fun onDeath(event: PlayerDeathEvent) {
        val player = event.entity
        val dungeon = activeDungeons.remove(player.uniqueId) ?: return
        
        event.drops.clear()
        event.keepInventory = true
        event.keepLevel = true
        event.deathMessage(null) // Hide public death message
        
        // Remove from dungeon
        dungeon.players.remove(player.uniqueId)
        player.hideBossBar(dungeon.bossBar)
        
        player.sendMessage(Component.empty())
        player.sendMessage(Component.text("═══ DUNGEON FAILED ═══", NamedTextColor.DARK_RED))
        player.sendMessage(Component.text("You fell in the dungeon...", NamedTextColor.RED))
        player.sendMessage(Component.text("Your items have been preserved.", NamedTextColor.GRAY))
        player.sendMessage(Component.empty())
        
        // End dungeon if no players left
        if (dungeon.players.isEmpty()) {
            dungeon.active = false
        }
    }
    
    @EventHandler
    fun onRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        
        // Check if they have a saved entry location (meaning they just died in a dungeon)
        val returnLoc = entryLocations.remove(player.uniqueId)
        
        if (returnLoc != null) {
            event.respawnLocation = returnLoc
            player.sendMessage(Component.text("Returned to entrance.", NamedTextColor.YELLOW))
        }
    }
    
    /**
     * Block enderman spawns in dungeon areas (The End world)
     */
    @EventHandler
    fun onCreatureSpawn(event: CreatureSpawnEvent) {
        // Block natural/default enderman spawns in The End (dungeon world)
        if (event.entity.world.name == "world_the_end" && 
            event.entityType == EntityType.ENDERMAN &&
            event.spawnReason != CreatureSpawnEvent.SpawnReason.CUSTOM) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onBossDamage(event: EntityDamageEvent) {
        val entity = event.entity as? org.bukkit.entity.LivingEntity ?: return
        // Optimization: Only check if in dungeon world
        if (entity.world.name != "world_the_end") return 
        
        // Find if this entity is a boss of any active dungeon
        dungeonLog.values.forEach { dungeon ->
            if (dungeon.bossEntity?.uniqueId == entity.uniqueId) {
                val health = entity.health - event.finalDamage
                val maxHealth = entity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH)?.value ?: 100.0
                val pct = (health / maxHealth).coerceIn(0.0, 1.0)
                
                plugin.packetManager.updateBossHealthBar(entity, "☠ ${dungeon.theme.name} Boss ☠", pct)
                return
            }
        }
    }

    @EventHandler
    fun onBossDeath(event: EntityDeathEvent) {
         val entity = event.entity
         if (entity.world.name != "world_the_end") return

         dungeonLog.values.forEach { dungeon ->
            if (dungeon.bossEntity?.uniqueId == entity.uniqueId) {
                plugin.packetManager.removeBossHealthBar(entity)
            }
         }
    }
}
