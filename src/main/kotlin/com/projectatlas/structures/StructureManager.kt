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
    
    // ... (existing Merchant logic) ...
    // 5x5 Wooden Hut
    private fun buildMerchantHut(center: Location) {
        val world = center.world
        val base = center.clone().add(-2.0, 0.0, -2.0)
        
        // Floor & Ceiling
        for (x in 0..4) {
            for (z in 0..4) {
                base.clone().add(x.toDouble(), 0.0, z.toDouble()).block.type = Material.SPRUCE_PLANKS
                base.clone().add(x.toDouble(), 4.0, z.toDouble()).block.type = Material.SPRUCE_SLAB
            }
        }
        
        // Walls
        for (y in 1..3) {
            for (x in 0..4) {
                base.clone().add(x.toDouble(), y.toDouble(), 0.0).block.type = Material.STRIPPED_SPRUCE_LOG // North
                base.clone().add(x.toDouble(), y.toDouble(), 4.0).block.type = Material.STRIPPED_SPRUCE_LOG // South
            }
            for (z in 0..4) {
                base.clone().add(0.0, y.toDouble(), z.toDouble()).block.type = Material.STRIPPED_SPRUCE_LOG // West
                base.clone().add(4.0, y.toDouble(), z.toDouble()).block.type = Material.STRIPPED_SPRUCE_LOG // East
            }
        }
        
        // Door
        base.clone().add(2.0, 1.0, 0.0).block.type = Material.AIR // Doorway
        base.clone().add(2.0, 2.0, 0.0).block.type = Material.AIR
        
        // Spawn Merchant Inside
        val spawnLoc = center.clone().add(0.0, 1.0, 0.0)
        val merchant = NPC(name="Trader Joe", type=NPCType.MERCHANT)
        plugin.npcManager.spawnNPC(merchant, spawnLoc)
    }
    
    // Campfire & Tents
    private fun buildQuestCamp(center: Location) {
        val world = center.world
        center.block.type = Material.CAMPFIRE
        // Simplified existing logic
        center.clone().add(2.0, 0.0, 0.0).block.type = Material.OAK_LOG
        center.clone().add(-2.0, 0.0, 0.0).block.type = Material.OAK_LOG
        
        val spawnLoc = center.clone().add(1.0, 0.0, 1.0)
        val adventurer = NPC(name="Ranger Rick", type=NPCType.QUEST_GIVER)
        plugin.npcManager.spawnNPC(adventurer, spawnLoc)
    }
    
    // 7x7 Barracks
    private fun buildBarracks(center: Location) {
        val base = center.clone().add(-3.0, 0.0, -3.0)
        for (x in 0..6) {
            for (z in 0..6) {
                base.clone().add(x.toDouble(), 0.0, z.toDouble()).block.type = Material.STONE_BRICKS
            }
        }
        // Walls
        for(x in 0..6) {
             base.clone().add(x.toDouble(), 1.0, 0.0).block.type = Material.COBBLESTONE_WALL
             base.clone().add(x.toDouble(), 1.0, 6.0).block.type = Material.COBBLESTONE_WALL
        }
        for(z in 0..6) {
             base.clone().add(0.0, 1.0, z.toDouble()).block.type = Material.COBBLESTONE_WALL
             base.clone().add(6.0, 1.0, z.toDouble()).block.type = Material.COBBLESTONE_WALL
        }
        val spawnLoc = center.clone().add(0.0, 1.0, 0.0)
        val captain = NPC(name="Captain Sterling", type=NPCType.QUEST_GIVER) // Placeholder type
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
