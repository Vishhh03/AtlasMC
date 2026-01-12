package com.projectatlas.dialogue

import net.kyori.adventure.text.format.NamedTextColor

data class Dialogue(
    val speakerName: String,
    val text: String,
    val options: List<DialogueOption>
)

data class DialogueOption(
    val text: String,
    val command: String,
    val color: NamedTextColor = NamedTextColor.GREEN,
    val hoverText: String = ""
)
