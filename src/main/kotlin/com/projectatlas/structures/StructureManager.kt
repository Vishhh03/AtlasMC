package com.projectatlas.structures

import com.projectatlas.AtlasPlugin
import com.projectatlas.npc.NPC
import com.projectatlas.npc.NPCType
import com.projectatlas.structures.StructureType
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.BlockFace

enum class StructureType {
    MERCHANT_HUT,
    QUEST_CAMP,
    BARRACKS,
    NEXUS,
    TURRET,
    GENERATOR
}

class StructureManager(private val plugin: AtlasPlugin) {

    fun spawnStructure(type: StructureType, location: Location) {
        val ground = location.block.getRelative(BlockFace.DOWN)
        val startLoc = ground.location.add(0.0, 1.0, 0.0)
        
        when (type) {
            StructureType.MERCHANT_HUT -> buildMerchantHut(startLoc)
            StructureType.QUEST_CAMP -> buildQuestCamp(startLoc)
            StructureType.BARRACKS -> buildBarracks(startLoc)
            StructureType.NEXUS -> buildNexus(startLoc)
            StructureType.TURRET -> buildTurret(startLoc)
            StructureType.GENERATOR -> buildGenerator(startLoc)
        }
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
        center.clone().add(-2.0, 1.0, 1.0).block.type = Material.ARMOR_STAND
        
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
}
