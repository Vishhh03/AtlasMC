package com.projectatlas.identity

import java.util.UUID

data class AtlasPlayer(
    val uuid: UUID,
    var name: String,
    var reputation: Int = 0,
    var alignment: Int = 0, // -100 to 100
    var balance: Double = 100.0,
    var cityId: String? = null,
    var playerClass: String? = null,
    var lastClassChange: Long = 0L, // Epoch millis - cooldown tracker
    val titles: MutableList<String> = mutableListOf()
)
