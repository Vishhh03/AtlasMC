package com.projectatlas.schematic

import org.bukkit.configuration.file.YamlConfiguration
import com.projectatlas.AtlasPlugin
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Player
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

class SchematicManager(private val plugin: AtlasPlugin) {

    private val schematicsDir = File(plugin.dataFolder, "schematics")
    private val selections = mutableMapOf<UUID, Pair<Location, Location>>()

    init {
        if (!schematicsDir.exists()) {
            schematicsDir.mkdirs()
        }
    }

    // Selection Logic
    fun setPos1(player: Player, loc: Location) {
        val current = selections[player.uniqueId]
        val pos2 = current?.second
        selections[player.uniqueId] = Pair(loc, pos2 ?: loc)
    }

    fun setPos2(player: Player, loc: Location) {
        val current = selections[player.uniqueId]
        val pos1 = current?.first
        selections[player.uniqueId] = Pair(pos1 ?: loc, loc)
    }

    fun getSelection(player: Player): Pair<Location, Location>? {
        return selections[player.uniqueId]
    }

    // Save
    fun saveSchematic(name: String, player: Player): Boolean {
        val selection = selections[player.uniqueId] ?: return false
        val min = getMinLoc(selection.first, selection.second)
        val max = getMaxLoc(selection.first, selection.second)

        val width = max.blockX - min.blockX + 1
        val height = max.blockY - min.blockY + 1
        val length = max.blockZ - min.blockZ + 1
        
        val blockList = mutableListOf<Map<String, Any>>()

        for (x in 0 until width) {
            for (y in 0 until height) {
                for (z in 0 until length) {
                    val block = min.clone().add(x.toDouble(), y.toDouble(), z.toDouble()).block
                    if (block.type != Material.AIR) {
                        blockList.add(mapOf(
                            "x" to x,
                            "y" to y,
                            "z" to z,
                            "mat" to block.type.name,
                            "data" to block.blockData.getAsString()
                        ))
                    }
                }
            }
        }

        val file = File(schematicsDir, "$name.yml")
        val config = YamlConfiguration()
        config.set("name", name)
        config.set("author", player.uniqueId.toString())
        config.set("width", width)
        config.set("height", height)
        config.set("length", length)
        config.set("blocks", blockList)
        
        config.save(file)
        return true
    }

    // Load
    fun loadSchematic(name: String): Schematic? {
        val file = File(schematicsDir, "$name.yml")
        if (!file.exists()) return null
        
        val config = YamlConfiguration.loadConfiguration(file)
        val width = config.getInt("width")
        val height = config.getInt("height")
        val length = config.getInt("length")
        val author = config.getString("author") ?: ""
        
        val blocks = mutableListOf<SchematicBlock>()
        val list = config.getMapList("blocks")
        
        for (map in list) {
            val x = (map["x"] as Int)
            val y = (map["y"] as Int)
            val z = (map["z"] as Int)
            val matStr = map["mat"] as String
            val data = map["data"] as String
            val mat = Material.getMaterial(matStr) ?: Material.STONE
            blocks.add(SchematicBlock(x, y, z, mat, data))
        }
        
        return Schematic(name, author, width, height, length, blocks)
    }

    // Paste (Simple)
    fun pasteSchematic(name: String, origin: Location) {
        val schematic = loadSchematic(name) ?: return
        
        schematic.blocks.forEach { block ->
            val loc = origin.clone().add(block.x.toDouble(), block.y.toDouble(), block.z.toDouble())
            loc.block.type = block.material
            loc.block.blockData = plugin.server.createBlockData(block.blockData)
        }
    }

    // Helpers
    private fun getMinLoc(l1: Location, l2: Location): Location {
        return Location(l1.world, min(l1.x, l2.x), min(l1.y, l2.y), min(l1.z, l2.z))
    }

    private fun getMaxLoc(l1: Location, l2: Location): Location {
        return Location(l1.world, max(l1.x, l2.x), max(l1.y, l2.y), max(l1.z, l2.z))
    }
}
