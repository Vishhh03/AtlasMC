package com.projectatlas.events

import com.projectatlas.structures.StructureManager
import com.projectatlas.AtlasPlugin
import com.projectatlas.util.LocationUtils
import org.bukkit.Material
import org.bukkit.block.Biome
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.world.ChunkLoadEvent
import java.util.Random

class WorldGenListener(private val plugin: AtlasPlugin) : Listener {

    private val random = Random()
    private val spawnChance = 0.005 // 0.5% chance per chunk

    @EventHandler
    fun onChunkLoad(event: ChunkLoadEvent) {
        if (!event.isNewChunk) return
        
        // Random check
        if (random.nextDouble() > spawnChance) return
        
        val chunk = event.chunk
        val world = chunk.world
        
        // Check if inside a city
        if (plugin.cityManager.getCityAt(chunk) != null) return
        
        // Pick random spot
        val x = chunk.x * 16 + random.nextInt(16)
        val z = chunk.z * 16 + random.nextInt(16)
        
        // Find safe ground level (not on trees)
        val y = LocationUtils.findSafeGroundY(world, x, z) ?: return
        
        val location = world.getBlockAt(x, y, z).location
        val groundBlock = world.getBlockAt(x, y - 1, z)
        val biome = world.getBiome(x, y, z)
        
        // 1. Avoid Oceans/Rivers
        if (biome == Biome.OCEAN || biome == Biome.RIVER || biome == Biome.DEEP_OCEAN || biome == Biome.FROZEN_OCEAN) return
        
        // 2. Avoid Liquid/Air ground
        if (groundBlock.isLiquid || groundBlock.type == Material.AIR) return
        
        // 3. Ensure spawn location is clear
        val spawnBlock = world.getBlockAt(x, y, z)
        if (!spawnBlock.type.isAir) return
        
        // 4. Choose Structure Type
        val type = if (random.nextBoolean()) 
            com.projectatlas.structures.StructureType.MERCHANT_HUT 
        else 
            com.projectatlas.structures.StructureType.QUEST_CAMP
            
        // Spawn
        plugin.structureManager.spawnStructure(type, location)
        plugin.logger.info("Natural Structure Spawned: $type at ${location.blockX}, ${location.blockY}, ${location.blockZ}")
    }
}
