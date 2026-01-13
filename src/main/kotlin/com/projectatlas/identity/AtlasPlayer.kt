package com.projectatlas.identity

import java.util.UUID

data class AtlasPlayer(
    val uuid: UUID,
    var name: String,
    var reputation: Int = 0,
    var alignment: Int = 0, // -100 to 100
    var balance: Double = 100.0,
    var cityId: String? = null,
    var currentXp: Long = 0,
    var level: Int = 1,
    val titles: MutableList<String> = mutableListOf(),
    var unlockedSkillNodes: String? = "origin" // Comma-separated node IDs, nullable for migration
)
