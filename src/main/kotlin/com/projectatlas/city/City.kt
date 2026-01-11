package com.projectatlas.city

import java.util.UUID

data class City(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var mayor: UUID,
    val members: MutableList<UUID> = mutableListOf(),
    val claimedChunks: MutableList<String> = mutableListOf(), // format: "worldName:x,z"
    var treasury: Double = 0.0,
    var taxRate: Double = 0.0 // Percentage (0.0 to 100.0)
) {
    fun addMember(uuid: UUID) {
        if (!members.contains(uuid)) members.add(uuid)
    }

    fun removeMember(uuid: UUID) {
        members.remove(uuid)
    }
}
