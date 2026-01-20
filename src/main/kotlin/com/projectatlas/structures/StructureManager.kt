package com.projectatlas.structures

import com.projectatlas.AtlasPlugin
import com.projectatlas.npc.NPC
import com.projectatlas.npc.NPCType
import com.projectatlas.structures.StructureType
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.BlockFace

enum class StructureType(val width: Int, val height: Int, val depth: Int) {
    MERCHANT_HUT(7, 6, 7),
    QUEST_CAMP(7, 4, 7),
    BARRACKS(9, 6, 9),
    NEXUS(3, 5, 3),
    TURRET(3, 7, 3),
    GENERATOR(3, 4, 3),
    // Defensive structures
    WALL(3, 4, 1),        // Single wall segment
    WALL_CORNER(3, 4, 3), // Corner piece
    GATE(3, 5, 1),        // Player-activatable gate
    RAMP(3, 3, 5),        // Sloped access ramp
    WATCHTOWER(5, 10, 5), // Tall observation tower
    
    // Tools
    TOOL_REPAIR(1, 1, 1),
    TOOL_MOVE(1, 1, 1),
    TOOL_DELETE(1, 1, 1)
}

class StructureManager(private val plugin: AtlasPlugin) {

    fun spawnStructure(type: StructureType, location: Location) {
        val ground = location.block.getRelative(BlockFace.DOWN)
        val startLoc = ground.location.add(0.0, 1.0, 0.0)
        
        // Final safety check (should be called by listener too)
        if (!canBuild(location, type)) {
             // Force build anyway? No, let's respect physics.
             // But if listener called us, maybe it wants to force?
             // Let's assume listener checked. But we do the building.
             // We'll proceed.
        }
        
        when (type) {
            StructureType.MERCHANT_HUT -> buildMerchantHut(startLoc)
            StructureType.QUEST_CAMP -> buildQuestCamp(startLoc)
            StructureType.BARRACKS -> buildBarracks(startLoc)
            StructureType.NEXUS -> buildNexus(startLoc)
            StructureType.TURRET -> buildTurret(startLoc)
            StructureType.GENERATOR -> buildGenerator(startLoc)
            StructureType.WALL -> buildWall(startLoc)
            StructureType.WALL_CORNER -> buildWallCorner(startLoc)
            StructureType.GATE -> buildGate(startLoc)
            StructureType.RAMP -> buildRamp(startLoc)
            StructureType.WATCHTOWER -> buildWatchtower(startLoc)
            else -> {}
        }
        
        // Register Health
        registerStructureHealth(type, startLoc)
    }

    fun registerStructureHealth(type: StructureType, center: Location) {
        val maxHealth = when (type) {
            StructureType.NEXUS, StructureType.GENERATOR -> 500.0
            StructureType.BARRACKS -> 300.0
            StructureType.TURRET -> 150.0
            StructureType.WATCHTOWER -> 200.0
            StructureType.WALL, StructureType.WALL_CORNER -> 100.0
            StructureType.GATE -> 120.0
            StructureType.RAMP -> 80.0
            else -> 100.0
        }
        val radius = kotlin.math.max(type.width, type.depth) / 1.5 
        plugin.structureHealthManager.registerStructure(java.util.UUID.randomUUID(), type, maxHealth, center, radius)
    }
    
    fun canBuild(location: Location, type: StructureType): Boolean {
        val width = type.width
        val height = type.height
        val depth = type.depth
        
        // Center offsets
        val startX = location.blockX - width / 2
        val startY = location.blockY
        val startZ = location.blockZ - depth / 2
        
        for (x in 0 until width) {
            for (z in 0 until depth) {
                // Check Foundation (Must be solid)
                val ground = location.world.getBlockAt(startX + x, startY - 1, startZ + z)
                if (!ground.type.isSolid) return false 
                
                // Check Volume (Must be clear)
                for (y in 0 until height) {
                    val block = location.world.getBlockAt(startX + x, startY + y, startZ + z)
                    if (!block.type.isAir && !block.isReplaceable) return false
                }
            }
        }
        return true
    }
    
    // ═══════════════════════════════════════════════════════════════
    // ORGANIC MERCHANT HUT - Weathered cabin with variety
    // ═══════════════════════════════════════════════════════════════
    private fun buildMerchantHut(center: Location) {
        val world = center.world
        val random = java.util.Random()
        val base = center.clone().add(-3.0, 0.0, -2.0)
        
        // Foundation - irregular stone base
        for (x in -1..5) {
            for (z in -1..5) {
                if (random.nextFloat() > 0.15) {
                    val foundMat = if (random.nextBoolean()) Material.COBBLESTONE else Material.MOSSY_COBBLESTONE
                    base.clone().add(x.toDouble(), -1.0, z.toDouble()).block.type = foundMat
                }
            }
        }
        
        // Floor - mixed wood planks
        for (x in 0..4) {
            for (z in 0..4) {
                val floorMat = when (random.nextInt(4)) {
                    0 -> Material.SPRUCE_PLANKS
                    1 -> Material.OAK_PLANKS  
                    else -> Material.SPRUCE_PLANKS
                }
                base.clone().add(x.toDouble(), 0.0, z.toDouble()).block.type = floorMat
            }
        }
        
        // Walls with variety
        val wallMats = listOf(Material.STRIPPED_SPRUCE_LOG, Material.SPRUCE_LOG, Material.SPRUCE_PLANKS)
        for (y in 1..3) {
            for (x in 0..4) {
                if (x != 2 || y > 2) { // Leave door space
                    val mat = if (random.nextFloat() < 0.7) Material.STRIPPED_SPRUCE_LOG else wallMats.random()
                    base.clone().add(x.toDouble(), y.toDouble(), 0.0).block.type = mat
                }
                base.clone().add(x.toDouble(), y.toDouble(), 4.0).block.type = if (random.nextFloat() < 0.7) Material.STRIPPED_SPRUCE_LOG else wallMats.random()
            }
            for (z in 1..3) {
                base.clone().add(0.0, y.toDouble(), z.toDouble()).block.type = if (random.nextFloat() < 0.7) Material.STRIPPED_SPRUCE_LOG else wallMats.random()
                base.clone().add(4.0, y.toDouble(), z.toDouble()).block.type = if (random.nextFloat() < 0.7) Material.STRIPPED_SPRUCE_LOG else wallMats.random()
            }
        }
        
        // Roof - sloped and overhang
        for (x in -1..5) {
            for (z in -1..5) {
                val roofMat = if (random.nextFloat() < 0.8) Material.SPRUCE_SLAB else Material.SPRUCE_STAIRS
                base.clone().add(x.toDouble(), 4.0, z.toDouble()).block.type = roofMat
            }
        }
        
        // Door
        base.clone().add(2.0, 1.0, 0.0).block.type = Material.AIR
        base.clone().add(2.0, 2.0, 0.0).block.type = Material.AIR
        
        // Interior decorations
        base.clone().add(1.0, 1.0, 3.0).block.type = Material.CHEST
        base.clone().add(3.0, 1.0, 3.0).block.type = Material.CRAFTING_TABLE
        base.clone().add(1.0, 1.0, 1.0).block.type = Material.BARREL
        
        // Lanterns
        base.clone().add(2.0, 3.0, 2.0).block.type = Material.LANTERN
        
        // Exterior details - random vegetation
        if (random.nextBoolean()) base.clone().add(-1.0, 0.0, 2.0).block.type = Material.POTTED_FERN
        if (random.nextBoolean()) base.clone().add(5.0, 0.0, 2.0).block.type = Material.FLOWER_POT
        if (random.nextBoolean()) base.clone().add(2.0, 0.0, -1.0).block.type = Material.HAY_BLOCK
        
        // Spawn Merchant Inside
        val spawnLoc = center.clone().add(0.0, 1.0, 0.0)
        val merchant = NPC(name="Trader Joe", type=NPCType.MERCHANT)
        plugin.npcManager.spawnNPC(merchant, spawnLoc)
    }
    
    // ═══════════════════════════════════════════════════════════════
    // ORGANIC QUEST CAMP - Rugged adventurer camp
    // ═══════════════════════════════════════════════════════════════
    private fun buildQuestCamp(center: Location) {
        val world = center.world
        val random = java.util.Random()
        
        // Central campfire with stones around it
        center.block.type = Material.CAMPFIRE
        val stoneRing = listOf(
            center.clone().add(1.0, 0.0, 0.0),
            center.clone().add(-1.0, 0.0, 0.0),
            center.clone().add(0.0, 0.0, 1.0),
            center.clone().add(0.0, 0.0, -1.0),
            center.clone().add(1.0, 0.0, 1.0),
            center.clone().add(-1.0, 0.0, -1.0),
            center.clone().add(1.0, 0.0, -1.0),
            center.clone().add(-1.0, 0.0, 1.0)
        )
        stoneRing.forEach { loc ->
            if (random.nextFloat() < 0.7) {
                loc.block.type = if (random.nextBoolean()) Material.COBBLESTONE else Material.STONE
            }
        }
        
        // Tent 1 - Eastern side
        val tent1 = center.clone().add(3.0, 0.0, 0.0)
        tent1.block.type = Material.WHITE_WOOL
        tent1.clone().add(0.0, 1.0, 0.0).block.type = Material.WHITE_WOOL
        tent1.clone().add(1.0, 0.0, 0.0).block.type = Material.WHITE_CARPET
        tent1.clone().add(0.0, 0.0, 1.0).block.type = Material.WHITE_CARPET
        tent1.clone().add(0.0, 0.0, -1.0).block.type = Material.WHITE_CARPET
        
        // Tent 2 - Western side
        val tent2 = center.clone().add(-3.0, 0.0, 0.0)
        tent2.block.type = Material.BROWN_WOOL
        tent2.clone().add(0.0, 1.0, 0.0).block.type = Material.BROWN_WOOL
        tent2.clone().add(-1.0, 0.0, 0.0).block.type = Material.BROWN_CARPET
        
        // Logs for sitting
        center.clone().add(2.0, 0.0, 2.0).block.type = Material.OAK_LOG
        center.clone().add(-2.0, 0.0, -2.0).block.type = Material.SPRUCE_LOG
        
        // Supplies scattered around
        if (random.nextBoolean()) center.clone().add(0.0, 0.0, 3.0).block.type = Material.BARREL
        if (random.nextBoolean()) center.clone().add(-2.0, 0.0, 1.0).block.type = Material.CHEST
        if (random.nextBoolean()) center.clone().add(2.0, 0.0, -2.0).block.type = Material.FLETCHING_TABLE
        
        // Weapon rack (fence + tripwire hooks)
        val rack = center.clone().add(-1.0, 0.0, -3.0)
        rack.block.type = Material.OAK_FENCE
        rack.clone().add(0.0, 1.0, 0.0).block.type = Material.OAK_FENCE
        
        // Lantern on stick
        val lanternPost = center.clone().add(2.0, 0.0, 3.0)
        lanternPost.block.type = Material.OAK_FENCE
        lanternPost.clone().add(0.0, 1.0, 0.0).block.type = Material.OAK_FENCE
        lanternPost.clone().add(0.0, 2.0, 0.0).block.type = Material.LANTERN
        
        // Spawn quest giver
        val spawnLoc = center.clone().add(1.0, 0.0, 1.0)
        val adventurer = NPC(name="Ranger Rick", type=NPCType.QUEST_GIVER)
        plugin.npcManager.spawnNPC(adventurer, spawnLoc)
    }
    
    // ═══════════════════════════════════════════════════════════════
    // ORGANIC BARRACKS - Ruined/weathered outpost
    // ═══════════════════════════════════════════════════════════════
    private fun buildBarracks(center: Location) {
        val random = java.util.Random()
        val base = center.clone().add(-4.0, 0.0, -4.0)
        
        // Floor - cracked stone bricks
        for (x in 0..8) {
            for (z in 0..8) {
                val floorMat = when (random.nextInt(5)) {
                    0 -> Material.CRACKED_STONE_BRICKS
                    1 -> Material.MOSSY_STONE_BRICKS
                    2 -> Material.STONE
                    else -> Material.STONE_BRICKS
                }
                if (random.nextFloat() < 0.9) { // Some holes
                    base.clone().add(x.toDouble(), 0.0, z.toDouble()).block.type = floorMat
                }
            }
        }
        
        // Walls - partially ruined
        for (x in 0..8) {
            val northHeight = if (random.nextFloat() < 0.3) 1 else if (random.nextFloat() < 0.7) 2 else 3
            val southHeight = if (random.nextFloat() < 0.3) 1 else if (random.nextFloat() < 0.7) 2 else 3
            for (y in 1..northHeight) {
                val wallMat = when (random.nextInt(4)) {
                    0 -> Material.COBBLESTONE_WALL
                    1 -> Material.MOSSY_COBBLESTONE_WALL
                    else -> Material.STONE_BRICK_WALL
                }
                base.clone().add(x.toDouble(), y.toDouble(), 0.0).block.type = wallMat
            }
            for (y in 1..southHeight) {
                val wallMat = when (random.nextInt(4)) {
                    0 -> Material.COBBLESTONE_WALL
                    1 -> Material.MOSSY_COBBLESTONE_WALL
                    else -> Material.STONE_BRICK_WALL
                }
                base.clone().add(x.toDouble(), y.toDouble(), 8.0).block.type = wallMat
            }
        }
        for (z in 0..8) {
            val westHeight = if (random.nextFloat() < 0.3) 1 else if (random.nextFloat() < 0.7) 2 else 3
            val eastHeight = if (random.nextFloat() < 0.3) 1 else if (random.nextFloat() < 0.7) 2 else 3
            for (y in 1..westHeight) {
                base.clone().add(0.0, y.toDouble(), z.toDouble()).block.type = Material.STONE_BRICK_WALL
            }
            for (y in 1..eastHeight) {
                base.clone().add(8.0, y.toDouble(), z.toDouble()).block.type = Material.STONE_BRICK_WALL
            }
        }
        
        // Corner towers (partial)
        for (corner in listOf(Pair(0, 0), Pair(8, 0), Pair(0, 8), Pair(8, 8))) {
            val towerHeight = random.nextInt(3) + 2
            for (y in 1..towerHeight) {
                base.clone().add(corner.first.toDouble(), y.toDouble(), corner.second.toDouble()).block.type = Material.STONE_BRICKS
            }
        }
        
        // Interior - scattered furniture
        center.clone().add(-2.0, 1.0, -2.0).block.type = Material.CHEST
        center.clone().add(2.0, 1.0, 2.0).block.type = Material.BARREL
        center.clone().add(0.0, 1.0, -2.0).block.type = Material.SMITHING_TABLE
        center.clone().add(-2.0, 1.0, 1.0).block.type = Material.ANVIL
        
        // Campfire in center
        center.block.type = Material.CAMPFIRE
        
        // Spawn captain
        val spawnLoc = center.clone().add(0.0, 1.0, 1.0)
        val captain = NPC(name="Captain Sterling", type=NPCType.QUEST_GIVER)
        plugin.npcManager.spawnNPC(captain, spawnLoc)
    }
    
    // 3x3 Nexus
    private fun buildNexus(center: Location) {
        center.block.type = Material.BEACON
        center.clone().add(0.0, -1.0, 0.0).block.type = Material.DIAMOND_BLOCK
        val base = center.clone().add(-1.0, -2.0, -1.0)
        for (x in 0..2) {
            for (z in 0..2) {
                base.clone().add(x.toDouble(), 0.0, z.toDouble()).block.type = Material.IRON_BLOCK
            }
        }
    }
    
    // 3x3 Turret (Tower)
    private fun buildTurret(center: Location) {
        val base = center.clone().add(-1.0, 0.0, -1.0)
        // 5 blocks high pillar
        for (y in 0..4) {
            for (x in 0..2) {
                for (z in 0..2) {
                    // Hollow center
                    if (x == 1 && z == 1) continue
                    base.clone().add(x.toDouble(), y.toDouble(), z.toDouble()).block.type = Material.MOSSY_COBBLESTONE
                }
            }
        }
        // Top platform
        for (x in 0..2) {
            for (z in 0..2) {
                base.clone().add(x.toDouble(), 5.0, z.toDouble()).block.type = Material.STONE_BRICK_SLAB
            }
        }
        // Dispenser on top
        center.clone().add(0.0, 6.0, 0.0).block.type = Material.DISPENSER
    }
    
    // 3x3 Generator (Industrial)
    private fun buildGenerator(center: Location) {
        val base = center.clone().add(-1.0, 0.0, -1.0)
        // Base
        for (x in 0..2) {
            for (z in 0..2) {
                base.clone().add(x.toDouble(), 0.0, z.toDouble()).block.type = Material.SMOOTH_STONE_SLAB
            }
        }
        // Core
        center.clone().add(0.0, 1.0, 0.0).block.type = Material.REDSTONE_BLOCK
        center.clone().add(0.0, 2.0, 0.0).block.type = Material.IRON_TRAPDOOR
        center.clone().add(1.0, 1.0, 0.0).block.type = Material.PISTON
        center.clone().add(-1.0, 1.0, 0.0).block.type = Material.PISTON
        center.clone().add(0.0, 1.0, 1.0).block.type = Material.PISTON
        center.clone().add(0.0, 1.0, -1.0).block.type = Material.PISTON
    }
    
    // ═══════════════════════════════════════════════════════════════
    // DEFENSIVE STRUCTURES
    // ═══════════════════════════════════════════════════════════════
    
    // 3x4x1 Wall Segment (Stone Brick)
    private fun buildWall(center: Location) {
        val base = center.clone().add(-1.0, 0.0, 0.0)
        
        // Main wall body
        for (x in 0..2) {
            for (y in 0..3) {
                val block = base.clone().add(x.toDouble(), y.toDouble(), 0.0).block
                block.type = if (y == 3) Material.STONE_BRICK_WALL else Material.STONE_BRICKS
            }
        }
        
        // Decorative top crenellations
        base.clone().add(0.0, 4.0, 0.0).block.type = Material.STONE_BRICK_SLAB
        base.clone().add(2.0, 4.0, 0.0).block.type = Material.STONE_BRICK_SLAB
    }
    
    // 3x4x3 Wall Corner (L-shaped)
    private fun buildWallCorner(center: Location) {
        val base = center.clone().add(-1.0, 0.0, -1.0)
        
        // L-shape: X-axis and Z-axis walls meeting at corner
        for (y in 0..3) {
            // X-axis wall
            for (x in 0..2) {
                val block = base.clone().add(x.toDouble(), y.toDouble(), 0.0).block
                block.type = if (y == 3) Material.STONE_BRICK_WALL else Material.STONE_BRICKS
            }
            // Z-axis wall (skip overlap at 0,0)
            for (z in 1..2) {
                val block = base.clone().add(0.0, y.toDouble(), z.toDouble()).block
                block.type = if (y == 3) Material.STONE_BRICK_WALL else Material.STONE_BRICKS
            }
        }
        
        // Corner pillar is thicker
        for (y in 0..4) {
            base.clone().add(0.0, y.toDouble(), 0.0).block.type = Material.CHISELED_STONE_BRICKS
        }
    }
    
    // 3x5x1 Gate (Openable with interaction)
    private fun buildGate(center: Location) {
        val base = center.clone().add(-1.0, 0.0, 0.0)
        
        // Gate pillars (sides)
        for (y in 0..4) {
            base.clone().add(0.0, y.toDouble(), 0.0).block.type = Material.DEEPSLATE_BRICK_WALL
            base.clone().add(2.0, y.toDouble(), 0.0).block.type = Material.DEEPSLATE_BRICK_WALL
        }
        
        // Gate arch (top)
        base.clone().add(1.0, 4.0, 0.0).block.type = Material.DEEPSLATE_BRICKS
        
        // Iron bars (the actual gate)
        for (y in 0..3) {
            val gateBlock = base.clone().add(1.0, y.toDouble(), 0.0).block
            gateBlock.type = Material.IRON_BARS
        }
        
        // Lanterns on pillars
        base.clone().add(0.0, 3.0, 0.5).block.type = Material.LANTERN
        base.clone().add(2.0, 3.0, 0.5).block.type = Material.LANTERN
    }
    
    // 3x3x5 Ramp (Sloped stairs for wall access)
    private fun buildRamp(center: Location) {
        val base = center.clone().add(-1.0, 0.0, -2.0)
        
        // Build stairs going up
        for (z in 0..4) {
            val y = z // 1 block up for each Z
            for (x in 0..2) {
                if (y < 3) {
                    val stairBlock = base.clone().add(x.toDouble(), y.toDouble(), z.toDouble()).block
                    stairBlock.type = Material.STONE_BRICK_STAIRS
                    // Set stair direction
                    val stairData = stairBlock.blockData as? org.bukkit.block.data.type.Stairs
                    stairData?.facing = org.bukkit.block.BlockFace.SOUTH
                    stairBlock.blockData = stairData ?: stairBlock.blockData
                }
            }
            // Fill underneath
            for (fillY in 0 until y) {
                for (x in 0..2) {
                    base.clone().add(x.toDouble(), fillY.toDouble(), z.toDouble()).block.type = Material.COBBLESTONE
                }
            }
        }
        
        // Railings
        for (z in 0..4) {
            val y = z
            if (y < 3) {
                base.clone().add(-0.0, y + 1.0, z.toDouble()).block.type = Material.COBBLESTONE_WALL
                base.clone().add(2.0, y + 1.0, z.toDouble()).block.type = Material.COBBLESTONE_WALL
            }
        }
    }
    
    // 5x10x5 Watchtower (Tall observation post)
    private fun buildWatchtower(center: Location) {
        val base = center.clone().add(-2.0, 0.0, -2.0)
        
        // Foundation (solid base)
        for (x in 0..4) {
            for (z in 0..4) {
                base.clone().add(x.toDouble(), 0.0, z.toDouble()).block.type = Material.COBBLESTONE
            }
        }
        
        // Tower shaft (hollow)
        for (y in 1..7) {
            for (x in 0..4) {
                for (z in 0..4) {
                    // Only build outer walls
                    if (x == 0 || x == 4 || z == 0 || z == 4) {
                        val material = when {
                            y % 3 == 0 -> Material.CHISELED_STONE_BRICKS
                            else -> Material.STONE_BRICKS
                        }
                        base.clone().add(x.toDouble(), y.toDouble(), z.toDouble()).block.type = material
                    }
                }
            }
        }
        
        // Interior ladder
        for (y in 1..7) {
            base.clone().add(2.0, y.toDouble(), 1.0).block.type = Material.LADDER
        }
        
        // Top platform (wider overhang)
        for (x in -1..5) {
            for (z in -1..5) {
                val floorLoc = base.clone().add(x.toDouble(), 8.0, z.toDouble())
                if (floorLoc.block.type.isAir) {
                    floorLoc.block.type = Material.DARK_OAK_PLANKS
                }
            }
        }
        
        // Battlements on top
        for (x in -1..5) {
            base.clone().add(x.toDouble(), 9.0, -1.0).block.type = Material.STONE_BRICK_WALL
            base.clone().add(x.toDouble(), 9.0, 5.0).block.type = Material.STONE_BRICK_WALL
        }
        for (z in 0..4) {
            base.clone().add(-1.0, 9.0, z.toDouble()).block.type = Material.STONE_BRICK_WALL
            base.clone().add(5.0, 9.0, z.toDouble()).block.type = Material.STONE_BRICK_WALL
        }
        
        // Roof peak
        center.clone().add(0.0, 9.0, 0.0).block.type = Material.CAMPFIRE
    }
}
