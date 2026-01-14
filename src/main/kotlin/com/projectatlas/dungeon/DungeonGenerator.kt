package com.projectatlas.dungeon

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.BlockFace
import java.util.Random

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
     * Physically builds the dungeon in the world based on the logical room list.
     */
    fun buildDungeon(startLocation: Location, rooms: List<DungeonRoom>, theme: DungeonTheme) {
        val world = startLocation.world
        val startY = startLocation.blockY
        
        for (room in rooms) {
            val roomCenter = startLocation.clone().add(
                (room.x * GRID_SIZE).toDouble(), 
                0.0, 
                (room.z * GRID_SIZE).toDouble()
            )
            
            when (room.type) {
                RoomType.ENTRANCE -> buildAntechamber(roomCenter, theme)
                RoomType.COMBAT_ARENA -> buildArena(roomCenter, theme, 20, 20)
                RoomType.BOSS_ROOM -> buildArena(roomCenter, theme, 30, 30) // Bigger
                RoomType.TRAP_ROOM -> buildHallway(roomCenter, theme, true)
                RoomType.HALLWAY -> buildHallway(roomCenter, theme, false)
                RoomType.TREASURE_ROOM -> buildTreasureRoom(roomCenter, theme)
                RoomType.PUZZLE_ROOM -> buildPuzzleRoom(roomCenter, theme)
            }
            
            // Generate corridors connecting PREVIOUS room to CURRENT room would require graph traversal
            // For this grid system, we can just build openings based on neighbors.
            connectNeighbors(roomCenter, room, rooms, theme)
        }
    }
    
    // --- Room Builders (Procedural Architecture) ---
    
    private fun buildBox(center: Location, radiusX: Int, radiusY: Int, radiusZ: Int, theme: DungeonTheme) {
        val world = center.world
        val cx = center.blockX
        val cy = center.blockY
        val cz = center.blockZ
        
        for (x in -radiusX..radiusX) {
            for (y in 0..radiusY) {
                for (z in -radiusZ..radiusZ) {
                    val loc = Location(world, (cx + x).toDouble(), (cy + y).toDouble(), (cz + z).toDouble())
                    
                    // Walls/Floor/Ceiling
                    if (y == 0) loc.block.type = theme.floor
                    else if (y == radiusY) loc.block.type = theme.ceiling
                    else if (x == -radiusX || x == radiusX || z == -radiusZ || z == radiusZ) {
                        loc.block.type = if (random.nextDouble() < 0.2) theme.detail else theme.wall
                    } else {
                        loc.block.type = Material.AIR
                    }
                }
            }
        }
    }
    
    private fun buildAntechamber(center: Location, theme: DungeonTheme) {
        buildBox(center, 8, 6, 8, theme)
        // Add some ambience
        center.clone().add(0.0, 1.0, 0.0).block.type = Material.TORCH
    }
    
    private fun buildArena(center: Location, theme: DungeonTheme, sizeX: Int, sizeZ: Int) {
        buildBox(center, sizeX, 10, sizeZ, theme)
        
        // Add Pillars
        for (i in 0 until 4) {
            val px = (random.nextInt(sizeX - 4) + 2) * (if (random.nextBoolean()) 1 else -1)
            val pz = (random.nextInt(sizeZ - 4) + 2) * (if (random.nextBoolean()) 1 else -1)
            
            for (y in 1 until 10) {
                center.clone().add(px.toDouble(), y.toDouble(), pz.toDouble()).block.type = theme.detail
            }
        }
        
        // Central Feature (Pit or Altar)
        if (random.nextBoolean()) {
            // Pit
            for (x in -2..2) {
                for (z in -2..2) {
                    center.clone().add(x.toDouble(), 0.0, z.toDouble()).block.type = theme.liquid
                }
            }
        }
    }
    
    private fun buildHallway(center: Location, theme: DungeonTheme, isTrap: Boolean) {
        buildBox(center, 4, 5, 12, theme) // Long Z-axis hallway
        
        if (isTrap) {
            // Lava Pits
            for (z in -8..8 step 4) {
                for (x in -2..2) {
                    center.clone().add(x.toDouble(), 0.0, z.toDouble()).block.type = theme.liquid
                }
            }
        }
    }
    
    private fun buildTreasureRoom(center: Location, theme: DungeonTheme) {
        buildBox(center, 6, 6, 6, theme)
        center.clone().add(0.0, 1.0, 0.0).block.type = Material.CHEST
        center.clone().add(0.0, 1.0, 0.0).block.state.update()
    }
    
    private fun buildPuzzleRoom(center: Location, theme: DungeonTheme) {
        buildBox(center, 8, 8, 8, theme)
        // Parkour blocks
        for (i in 0..5) {
            center.clone().add(
                (random.nextInt(10) - 5).toDouble(),
                (i + 1).toDouble(),
                (random.nextInt(10) - 5).toDouble()
            ).block.type = theme.detail
        }
    }

    private fun connectNeighbors(center: Location, current: DungeonRoom, allRooms: List<DungeonRoom>, theme: DungeonTheme) {
        // Simple corridor carving. If a room exists at (x+1), carve a door + path to +32 blocks X
        // Implementation simplified: just carve open the walls if neighbor exists
        
        val neighbors = allRooms.filter { 
            (it.x == current.x + 1 && it.z == current.z) ||
            (it.x == current.x - 1 && it.z == current.z) ||
            (it.x == current.x && it.z == current.z + 1) ||
            (it.x == current.x && it.z == current.z - 1)
        } // Direct neighbors
        
        for (neighbor in neighbors) {
            val dx = neighbor.x - current.x
            val dz = neighbor.z - current.z
            
            // Carve a 3x3 tunnel in that direction
            // Center wall coordinates active radius
            val wallDist = 10 // Approximate, need better radius logic, but safely carving 'infinite' tunnel 
                             // until midpoint works for grid systems
            
            // Vector to neighbor center
            val direction = Location(center.world, dx.toDouble(), 0.0, dz.toDouble())
            
            for (i in 5 until GRID_SIZE - 5) {
                val tunnelLoc = center.clone().add(
                    direction.x * i,
                    1.0,
                    direction.z * i
                )
                // Clear air
                for (y in 0..3) {
                    for (w in -1..1) {
                         // Orthogonal width
                         val wx = if (dx == 0) w else 0
                         val wz = if (dz == 0) w else 0
                         
                         val digLoc = tunnelLoc.clone().add(wx.toDouble(), y.toDouble(), wz.toDouble())
                         
                         if (y == 0) digLoc.block.type = theme.floor 
                         else if (y == 3) digLoc.block.type = theme.ceiling
                         else digLoc.block.type = Material.AIR
                    }
                }
            }
        }
    }
}
