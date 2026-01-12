package com.projectatlas.events

import com.projectatlas.structures.StructureManager
import com.projectatlas.AtlasPlugin
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
        val y = world.getHighestBlockYAt(x, z)
        
        val location = world.getBlockAt(x, y, z).location
        val groundBlock = location.clone().add(0.0, -1.0, 0.0).block
        val biome = world.getBiome(x, y, z)
        
        // 1. Avoid Oceans/Rivers
        if (biome == Biome.OCEAN || biome == Biome.RIVER || biome == Biome.DEEP_OCEAN || biome == Biome.FROZEN_OCEAN) return
        
        // 2. Avoid Liquid/Air ground
        if (groundBlock.isLiquid || groundBlock.type == Material.AIR) return
        
        // 3. Choose Structure Type
        val type = if (random.nextBoolean()) 
            com.projectatlas.structures.StructureType.MERCHANT_HUT 
        else 
            com.projectatlas.structures.StructureType.QUEST_CAMP
            
        // Spawn
        plugin.structureManager.spawnStructure(type, location)
        plugin.logger.info("Natural Structure Spawned: $type at ${location.blockX}, ${location.blockY}, ${location.blockZ}")
    }
}
