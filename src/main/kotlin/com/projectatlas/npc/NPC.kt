package com.projectatlas.npc

import org.bukkit.Location
import java.util.UUID

/**
 * Represents an NPC in the world
 */
data class NPC(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: NPCType,
    var worldName: String = "world",
    var x: Double = 0.0,
    var y: Double = 0.0,
    var z: Double = 0.0
) {
    fun getLocation(plugin: org.bukkit.plugin.Plugin): Location? {
        val world = plugin.server.getWorld(worldName) ?: return null
        return Location(world, x, y, z)
    }
    
    fun setLocation(loc: Location) {
        worldName = loc.world.name
        x = loc.x
        y = loc.y
        z = loc.z
    }
}

enum class NPCType {
    MERCHANT,      // Sells items for gold
    QUEST_GIVER    // Offers quests
}
