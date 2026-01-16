package com.projectatlas.city

import java.util.UUID
import org.bukkit.Material

data class City(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var mayor: UUID,
    val members: MutableList<UUID> = mutableListOf(),
    val claimedChunks: MutableList<String> = mutableListOf(), // format: "worldName:x,z"
    var treasury: Double = 0.0,
    var taxRate: Double = 0.0, // Percentage (0.0 to 100.0)
    var infrastructure: CityInfrastructure = CityInfrastructure(),
    var lastSiegeTime: Long = 0L,
    val completedWonders: MutableSet<CityWonder> = mutableSetOf(),
    // Wonder -> Material -> Amount contributed
    val wonderProgress: MutableMap<CityWonder, MutableMap<Material, Int>> = mutableMapOf(),
    // ═══ ERA PROGRESSION (City-Based Competition) ═══
    var currentEra: Int = 0, // 0 = Awakening, 1 = Settlement, etc.
    val completedMilestones: MutableSet<String> = mutableSetOf(), // milestone IDs
    var specialization: CitySpecialization = CitySpecialization.NONE,
    var energy: Int = 0, // Redstone (Industrial Power)
    var mana: Int = 0 // Lapis (Arcane Power)
) {
    fun addMember(uuid: UUID) {
        if (!members.contains(uuid)) members.add(uuid)
    }

    fun removeMember(uuid: UUID) {
        members.remove(uuid)
    }
    
    fun getControlPoint(): org.bukkit.Location? {
        if (claimedChunks.isEmpty()) return null
        val parts = claimedChunks[0].split(":")
        if (parts.size < 2) return null
        val worldName = parts[0]
        val coords = parts[1].split(",")
        if (coords.size < 2) return null
        
        val world = org.bukkit.Bukkit.getWorld(worldName) ?: return null
        val x = coords[0].toInt() * 16 + 8
        val z = coords[1].toInt() * 16 + 8
        val y = world.getHighestBlockYAt(x, z).toDouble() + 1
        
        return org.bukkit.Location(world, x.toDouble(), y, z.toDouble())
    }
}
