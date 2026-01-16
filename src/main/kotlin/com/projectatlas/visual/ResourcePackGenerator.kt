package com.projectatlas.visual

import com.projectatlas.AtlasPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import java.io.File
import com.projectatlas.visual.CustomItemManager.ModelData

class ResourcePackGenerator(private val plugin: AtlasPlugin) {

    private val packFolder = File(plugin.dataFolder, "resource-pack")
    
    fun generatePack() {
        plugin.logger.info("Generating resource pack...")
        
        if (!packFolder.exists()) packFolder.mkdirs()
        
        generateMcmeta()
        generateFolderStructure()
        generateItemModels()
        
        plugin.logger.info("Resource pack generated at: ${packFolder.absolutePath}")
    }
    
    private fun generateMcmeta() {
        val mcmeta = """
            {
              "pack": {
                "pack_format": 34,
                "description": "Project Atlas Official Resource Pack"
              }
            }
        """.trimIndent()
        
        File(packFolder, "pack.mcmeta").writeText(mcmeta)
    }
    
    private fun generateFolderStructure() {
        File(packFolder, "assets/minecraft/models/item").mkdirs()
        File(packFolder, "assets/minecraft/textures/item").mkdirs()
    }
    
    private fun generateItemModels() {
        // We need to map Vanilla Item -> List of Custom Overrides
        val overrides = mutableMapOf<Material, MutableList<CustomOverride>>()
        
        // Register all our custom items here
        
        // Weapons
        addOverride(overrides, Material.NETHERITE_SWORD, ModelData.HOLLOW_KNIGHT_BLADE, "hollow_knight_blade")
        addOverride(overrides, Material.NETHERITE_SWORD, ModelData.WARDEN_FLAME_SWORD, "warden_flame_sword")
        addOverride(overrides, Material.NETHERITE_SWORD, ModelData.ENDER_SENTINEL_SCYTHE, "ender_sentinel_scythe")
        addOverride(overrides, Material.NETHERITE_SWORD, ModelData.DRAGON_SLAYER, "dragon_slayer")
        addOverride(overrides, Material.IRON_AXE, ModelData.TAX_COLLECTOR_AXE, "tax_collector_axe")
        
        // Consumables
        addOverride(overrides, Material.HONEY_BOTTLE, ModelData.HEALING_SALVE, "healing_salve")
        addOverride(overrides, Material.TOTEM_OF_UNDYING, ModelData.SPIRIT_TOTEM, "spirit_totem")
        
        // Keys & Relics (using Gold Nugget as base for most)
        addOverride(overrides, Material.GOLD_NUGGET, ModelData.AWAKENING_MEDAL, "awakening_medal")
        addOverride(overrides, Material.GOLD_NUGGET, ModelData.SETTLER_BADGE, "settler_badge")
        addOverride(overrides, Material.GOLD_NUGGET, ModelData.ASCENDANT_CROWN, "ascendant_crown")
        addOverride(overrides, Material.GOLD_NUGGET, ModelData.LEGEND_CROWN, "legend_crown") // Using ascendant for now or need gen
        addOverride(overrides, Material.COMPASS, ModelData.EXPLORER_COMPASS, "explorer_compass")
        addOverride(overrides, Material.TRIPWIRE_HOOK, ModelData.DUNGEON_KEY, "dungeon_key")
        
        // Blueprints (Using Paper)
        addOverride(overrides, Material.PAPER, ModelData.BLUEPRINT_GENERIC, "blueprint_generic")
        addOverride(overrides, Material.PAPER, ModelData.BLUEPRINT_BARRACKS, "blueprint_barracks")
        addOverride(overrides, Material.PAPER, ModelData.BLUEPRINT_TURRET, "blueprint_turret")
        
        // Write the base JSON files for each vanilla material
        overrides.forEach { (material, customs) ->
            writeVanillaOverrideJson(material, customs)
            
            // Also write the individual custom model files
            customs.forEach { custom ->
                writeCustomModelJson(custom.modelName)
            }
        }
    }
    
    private fun addOverride(map: MutableMap<Material, MutableList<CustomOverride>>, material: Material, modelData: Int, modelName: String) {
        map.computeIfAbsent(material) { mutableListOf() }.add(CustomOverride(modelData, "item/$modelName"))
    }
    
    private fun writeVanillaOverrideJson(material: Material, overrides: List<CustomOverride>) {
        val materialName = material.key.key
        val file = File(packFolder, "assets/minecraft/models/item/$materialName.json")
        
        // Sort overrides by ID to ensure consistent order
        val sortedOverrides = overrides.sortedBy { it.modelData }
        
        val json = StringBuilder()
        json.append("{\n")
        json.append("  \"parent\": \"item/handheld\",\n") // Assuming handheld for most, or generated
        json.append("  \"textures\": {\n")
        json.append("    \"layer0\": \"item/$materialName\"\n")
        json.append("  },\n")
        json.append("  \"overrides\": [\n")
        
        sortedOverrides.forEachIndexed { index, override ->
            json.append("    { \"predicate\": { \"custom_model_data\": ${override.modelData} }, \"model\": \"${override.modelPath}\" }")
            if (index < sortedOverrides.size - 1) json.append(",")
            json.append("\n")
        }
        
        json.append("  ]\n")
        json.append("}")
        
        file.writeText(json.toString())
    }
    
    private fun writeCustomModelJson(modelName: String) {
        val file = File(packFolder, "assets/minecraft/models/item/$modelName.json")
        val json = """
            {
              "parent": "item/handheld",
              "textures": {
                "layer0": "item/$modelName"
              }
            }
        """.trimIndent()
        file.writeText(json)
    }
    
    data class CustomOverride(val modelData: Int, val modelPath: String, val modelName: String = modelPath.substringAfter("/"))
}
