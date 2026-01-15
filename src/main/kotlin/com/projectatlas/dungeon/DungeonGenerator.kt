package com.projectatlas.dungeon

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.BlockFace
import java.util.Random
import java.util.ArrayDeque
import org.bukkit.scheduler.BukkitRunnable
import com.projectatlas.AtlasPlugin

/**
 * Procedural Dungeon Generator
 * Uses a node-based grid system to generate layouts and builds them block-by-block.
 */
class DungeonGenerator {

    private val GRID_SIZE = 64 // Each room occupies a 64x64 area
    private val random = Random()

    /**
     * Generates a dungeon layout (Logical graph)
     */
    fun checkGraph(difficulty: Int): List<DungeonRoom> {
        val rooms = mutableListOf<DungeonRoom>()
        
        // 1. Start with Entrance
        var currentX = 0
        var currentZ = 0
        rooms.add(DungeonRoom(currentX, currentZ, RoomType.ENTRANCE))
        
        // 2. Generate Main Path (Line to Boss)
        val length = 4 + difficulty // 5 to 9 rooms long
        
        for (i in 1 until length) {
            // Pick direction (mostly forward/random twist)
            if (random.nextBoolean()) currentZ++ else currentX++
            
            val type = if (i == length - 1) RoomType.BOSS_ROOM 
                      else if (i % 2 == 1) RoomType.COMBAT_ARENA 
                      else RoomType.TRAP_ROOM
            
            rooms.add(DungeonRoom(currentX, currentZ, type))
            
            // 3. Optional Branches (Treasure/Puzzle)
            if (random.nextDouble() < 0.4 && type != RoomType.BOSS_ROOM) {
                // Branch off
                val branchX = if (random.nextBoolean()) currentX + 1 else currentX - 1
                val branchZ = currentZ
                
                // Ensure no overlap
                if (rooms.none { it.x == branchX && it.z == branchZ }) {
                    val branchType = if (random.nextBoolean()) RoomType.TREASURE_ROOM else RoomType.PUZZLE_ROOM
                    rooms.add(DungeonRoom(branchX, branchZ, branchType))
                }
            }
        }
        
        return rooms
    }

    /**
     * Physically builds the dungeon in the world asynchronously.
     */
    fun buildDungeon(plugin: AtlasPlugin, startLocation: Location, rooms: List<DungeonRoom>, theme: DungeonTheme, callback: () -> Unit) {
        val queue = ArrayDeque<Pair<Location, Material>>()
        
        for (room in rooms) {
            val roomCenter = startLocation.clone().add(
                (room.x * GRID_SIZE).toDouble(), 
                0.0, 
                (room.z * GRID_SIZE).toDouble()
            )
            
            when (room.type) {
                RoomType.ENTRANCE -> buildAntechamber(queue, roomCenter, theme)
                RoomType.COMBAT_ARENA -> buildArena(queue, roomCenter, theme, 20, 20)
                RoomType.BOSS_ROOM -> buildArena(queue, roomCenter, theme, 30, 30)
                RoomType.TRAP_ROOM -> buildHallway(queue, roomCenter, theme, true)
                RoomType.HALLWAY -> buildHallway(queue, roomCenter, theme, false)
                RoomType.TREASURE_ROOM -> buildTreasureRoom(queue, roomCenter, theme)
                RoomType.PUZZLE_ROOM -> buildPuzzleRoom(queue, roomCenter, theme)
            }
            
            connectNeighbors(queue, roomCenter, room, rooms, theme)
        }
        
        // Process Queue
        object : BukkitRunnable() {
            override fun run() {
                val limit = 2500 
                var count = 0
                while (count < limit && !queue.isEmpty()) {
                     val (loc, mat) = queue.poll()
                     loc.block.type = mat
                     count++
                }
                
                if (queue.isEmpty()) {
                    cancel()
                    callback()
                }
            }
        }.runTaskTimer(plugin, 0L, 1L)
    }
    
    // --- Room Builders (Procedural Architecture) ---
    
    private fun buildBox(queue: ArrayDeque<Pair<Location, Material>>, center: Location, radiusX: Int, radiusY: Int, radiusZ: Int, theme: DungeonTheme) {
        val world = center.world
        val cx = center.blockX
        val cy = center.blockY
        val cz = center.blockZ
        
        for (x in -radiusX..radiusX) {
            for (y in 0..radiusY) {
                for (z in -radiusZ..radiusZ) {
                    val loc = Location(world, (cx + x).toDouble(), (cy + y).toDouble(), (cz + z).toDouble())
                    
                    if (y == 0) queue.add(loc to theme.floor)
                    else if (y == radiusY) queue.add(loc to theme.ceiling)
                    else if (x == -radiusX || x == radiusX || z == -radiusZ || z == radiusZ) {
                        queue.add(loc to (if (random.nextDouble() < 0.2) theme.detail else theme.wall))
                    } else {
                        queue.add(loc to Material.AIR)
                    }
                }
            }
        }
    }
    
    private fun buildAntechamber(queue: ArrayDeque<Pair<Location, Material>>, center: Location, theme: DungeonTheme) {
        buildBox(queue, center, 8, 6, 8, theme)
        queue.add(center.clone().add(0.0, 1.0, 0.0) to Material.TORCH)
    }
    
    private fun buildArena(queue: ArrayDeque<Pair<Location, Material>>, center: Location, theme: DungeonTheme, sizeX: Int, sizeZ: Int) {
        buildBox(queue, center, sizeX, 10, sizeZ, theme)
        
        // Add Pillars
        for (i in 0 until 4) {
            val px = (random.nextInt(sizeX - 4) + 2) * (if (random.nextBoolean()) 1 else -1)
            val pz = (random.nextInt(sizeZ - 4) + 2) * (if (random.nextBoolean()) 1 else -1)
            
            for (y in 1 until 10) {
                queue.add(center.clone().add(px.toDouble(), y.toDouble(), pz.toDouble()) to theme.detail)
            }
        }
        
        if (random.nextBoolean()) {
            for (x in -2..2) {
                for (z in -2..2) {
                    queue.add(center.clone().add(x.toDouble(), 0.0, z.toDouble()) to theme.liquid)
                }
            }
        }
    }
    
    private fun buildHallway(queue: ArrayDeque<Pair<Location, Material>>, center: Location, theme: DungeonTheme, isTrap: Boolean) {
        buildBox(queue, center, 4, 5, 12, theme)
        
        if (isTrap) {
            for (z in -8..8 step 4) {
                for (x in -2..2) {
                    queue.add(center.clone().add(x.toDouble(), 0.0, z.toDouble()) to theme.liquid)
                }
            }
        }
    }
    
    private fun buildTreasureRoom(queue: ArrayDeque<Pair<Location, Material>>, center: Location, theme: DungeonTheme) {
        buildBox(queue, center, 6, 6, 6, theme)
        queue.add(center.clone().add(0.0, 1.0, 0.0) to Material.CHEST)
    }
    
    private fun buildPuzzleRoom(queue: ArrayDeque<Pair<Location, Material>>, center: Location, theme: DungeonTheme) {
        buildBox(queue, center, 8, 8, 8, theme)
        for (i in 0..5) {
            queue.add(center.clone().add(
                (random.nextInt(10) - 5).toDouble(),
                (i + 1).toDouble(),
                (random.nextInt(10) - 5).toDouble()
            ) to theme.detail)
        }
    }

    private fun connectNeighbors(queue: ArrayDeque<Pair<Location, Material>>, center: Location, current: DungeonRoom, allRooms: List<DungeonRoom>, theme: DungeonTheme) {
        val neighbors = allRooms.filter { 
            (it.x == current.x + 1 && it.z == current.z) ||
            (it.x == current.x - 1 && it.z == current.z) ||
            (it.x == current.x && it.z == current.z + 1) ||
            (it.x == current.x && it.z == current.z - 1)
        }
        
        for (neighbor in neighbors) {
            val dx = neighbor.x - current.x
            val dz = neighbor.z - current.z
            val direction = Location(center.world, dx.toDouble(), 0.0, dz.toDouble())
            
            for (i in 5 until GRID_SIZE - 5) {
                val tunnelLoc = center.clone().add(direction.x * i, 1.0, direction.z * i)
                for (y in 0..3) {
                    for (w in -1..1) {
                         val wx = if (dx == 0) w else 0
                         val wz = if (dz == 0) w else 0
                         val digLoc = tunnelLoc.clone().add(wx.toDouble(), y.toDouble(), wz.toDouble())
                         
                         if (y == 0) queue.add(digLoc to theme.floor)
                         else if (y == 3) queue.add(digLoc to theme.ceiling)
                         else queue.add(digLoc to Material.AIR)
                    }
                }
            }
        }
    }
}
