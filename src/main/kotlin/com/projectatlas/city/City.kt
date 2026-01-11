package com.projectatlas.city

import java.util.UUID

data class City(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var mayor: UUID,
    var balance: Double = 0.0,
    val members: MutableList<UUID> = mutableListOf(),
    val claimedChunks: MutableList<String> = mutableListOf() // format: "worldName:x,z"
) {
    fun addMember(uuid: UUID) {
        if (!members.contains(uuid)) members.add(uuid)
    }

    fun removeMember(uuid: UUID) {
        members.remove(uuid)
    }
}
