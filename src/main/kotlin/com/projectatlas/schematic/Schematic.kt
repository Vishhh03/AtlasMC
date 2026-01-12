package com.projectatlas.schematic

import org.bukkit.Material
import java.util.UUID

data class Schematic(
    val name: String,
    val author: String, // UUID
    val width: Int,
    val height: Int,
    val length: Int,
    val blocks: List<SchematicBlock>
)

data class SchematicBlock(
    val x: Int,
    val y: Int,
    val z: Int,
    val material: Material,
    val blockData: String // Stringified BlockData
)
