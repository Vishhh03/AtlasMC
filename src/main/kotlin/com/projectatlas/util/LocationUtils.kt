package com.projectatlas.util

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World

/**
 * Utility functions for location/spawn validation
 */
object LocationUtils {
    
    /**
     * Check if a material is a tree-related block
     */
    fun isTreeBlock(type: Material): Boolean {
        val name = type.name
        return name.endsWith("_LOG") || 
               name.endsWith("_LEAVES") || 
               name.endsWith("_WOOD") ||
               type == Material.VINE ||
               type == Material.BIG_DRIPLEAF ||
               type == Material.SMALL_DRIPLEAF ||
               type == Material.AZALEA ||
               type == Material.FLOWERING_AZALEA ||
               type == Material.MANGROVE_ROOTS ||
               type == Material.MUDDY_MANGROVE_ROOTS ||
               type == Material.TALL_GRASS ||
               type == Material.TALL_SEAGRASS
    }
    
    /**
     * Check if a material is not suitable ground for spawning
     */
    fun isInvalidGround(type: Material): Boolean {
        return isTreeBlock(type) || 
               type.isAir ||
               type == Material.WATER ||
               type == Material.LAVA ||
               type == Material.CACTUS ||
               type == Material.BAMBOO ||
               type == Material.SUGAR_CANE ||
               type == Material.KELP ||
               type == Material.SEAGRASS
    }
    
    /**
     * Find safe ground level at given coordinates, skipping tree blocks
     * Returns null if no valid ground found
     */
    fun findSafeGroundY(world: World, x: Int, z: Int, maxAttempts: Int = 25): Int? {
        var y = world.getHighestBlockYAt(x, z)
        var attempts = 0
        
        // Skip down through invalid blocks to find real ground
        while (attempts < maxAttempts) {
            val groundBlock = world.getBlockAt(x, y - 1, z)
            
            if (!isInvalidGround(groundBlock.type)) {
                // Found valid ground, check if spawn point is clear
                val spawnBlock = world.getBlockAt(x, y, z)
                if (spawnBlock.type.isAir || isTreeBlock(spawnBlock.type)) {
                    return y
                }
            }
            
            y--
            attempts++
            
            if (y < 1) return null
        }
        
        return null
    }
    
    /**
     * Get a safe spawn location, avoiding trees and invalid ground
     * Returns null if no valid location found
     */
    fun getSafeSpawnLocation(world: World, baseX: Int, baseZ: Int): Location? {
        val y = findSafeGroundY(world, baseX, baseZ) ?: return null
        return Location(world, baseX.toDouble() + 0.5, y.toDouble(), baseZ.toDouble() + 0.5)
    }
    
    /**
     * Get safe spawn location with offset from a base location
     */
    fun getSafeSpawnLocationWithOffset(base: Location, offsetX: Int, offsetZ: Int): Location? {
        val world = base.world ?: return null
        return getSafeSpawnLocation(world, base.blockX + offsetX, base.blockZ + offsetZ)
    }
}
