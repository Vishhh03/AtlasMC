package com.projectatlas.debug

import com.projectatlas.AtlasPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

object TextureDebugKit {
    
    fun giveAllTextures(player: Player) {
        val items = mutableListOf<ItemStack>()
        
        // Weapons with custom_model_data
        items.add(createItem(Material.NETHERITE_SWORD, "Hollow Knight Blade", 1001, "Purple/Cyan Sword"))
        items.add(createItem(Material.NETHERITE_SWORD, "Warden Flame Sword", 3001, "Fire Sword"))
        items.add(createItem(Material.NETHERITE_SWORD, "Ender Sentinel Scythe", 4001, "Purple Scythe"))
        items.add(createItem(Material.NETHERITE_SWORD, "Dragon Slayer", 5001, "Fire Dragon Sword"))
        
        // Log for debugging
        // Log for debugging
        items.forEach { item ->
            val meta = item.itemMeta
            if (meta != null && meta.hasCustomModelData()) {
                val cmd = meta.customModelData
                org.bukkit.Bukkit.getLogger().info("[TEXTURE DEBUG] ${item.type} has CustomModelData: $cmd")
            } else {
                 org.bukkit.Bukkit.getLogger().info("[TEXTURE DEBUG] ${item.type} has NO CustomModelData (Using Item Model?)")
            }
        }
        
        // Recovery Compass (animated)
        items.add(createItem(Material.RECOVERY_COMPASS, "Death Compass", null, "Skull + Red Needle (32 frames)"))
        
        // Badges/Crowns (paper with custom_model_data)
        items.add(createItem(Material.PAPER, "Ascendant Crown", null, "Golden Crown"))
        items.add(createItem(Material.PAPER, "Awakening Medal", null, "Bronze Medal"))
        items.add(createItem(Material.PAPER, "Settler Badge", null, "Silver Shield"))
        items.add(createItem(Material.PAPER, "Legend Crown", null, "Platinum Crown"))
        
        // Consumables
        items.add(createItem(Material.POTION, "Healing Salve", null, "Green Potion"))
        items.add(createItem(Material.TOTEM_OF_UNDYING, "Spirit Totem", null, "Cyan Totem"))
        
        // Tools
        items.add(createItem(Material.COMPASS, "Explorer Compass", null, "Golden Compass"))
        items.add(createItem(Material.TRIPWIRE_HOOK, "Dungeon Key", null, "Skull Key"))
        
        // Blueprints
        items.add(createItem(Material.PAPER, "Blueprint (Generic)", null, "Blue Rolled Paper"))
        items.add(createItem(Material.PAPER, "Blueprint (Barracks)", null, "Blue Rolled Paper"))
        items.add(createItem(Material.PAPER, "Blueprint (Turret)", null, "Blue Rolled Paper"))
        
        // Give all items
        items.forEach { player.inventory.addItem(it) }
        
        player.sendMessage(Component.text("✓ Gave ${items.size} texture test items", NamedTextColor.GREEN))
        org.bukkit.Bukkit.getLogger().info("[TEXTURE DEBUG] Gave ${player.name} full texture test kit (${items.size} items)")
    }
    
    private fun createItem(material: Material, name: String, customModelData: Int?, description: String): ItemStack {
        return ItemStack(material).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text(name, NamedTextColor.AQUA, TextDecoration.BOLD))
                lore(listOf(
                    Component.text("Expected: $description", NamedTextColor.GRAY),
                    Component.text("Texture Test (Item Model)", NamedTextColor.DARK_GRAY, TextDecoration.ITALIC)
                ))
                
                // NEW 1.21.4+ WAY: Use Item Model Component
                // Map the integer CMD to our string IDs for backward compatibility in this helper
                if (customModelData != null) {
                    val modelKey = when(customModelData) {
                        1001 -> "hollow_knight_blade"
                        3001 -> "warden_flame_sword"
                        4001 -> "ender_sentinel_scythe"
                        5001 -> "dragon_slayer"
                        else -> null
                    }
                    
                    if (modelKey != null) {
                        try {
                            // Use reflection or direct API if compiled against 1.21.4
                            // itemMeta.setItemModel(NamespacedKey("projectatlas", modelKey))
                            // Since we might be compiling against older lib locally, let's use UnsafeValues or PersistentDataContainer fallback?
                            // NO, we updated build.gradle, so we can use the API directly!
                            this.setItemModel(org.bukkit.NamespacedKey("projectatlas", modelKey))
                        } catch (e: NoSuchMethodError) {
                            // Fallback for older server builds
                            setCustomModelData(customModelData)
                        }
                    } else {
                        setCustomModelData(customModelData)
                    }
                } else {
                    // Items without CMD might be custom mapped by name in my generation script
                    // e.g. healing_salve, spirit_totem
                    val key = name.lowercase().replace(" ", "_").replace("(", "").replace(")", "")
                    try {
                        this.setItemModel(org.bukkit.NamespacedKey("projectatlas", key))
                    } catch (e: Exception) {}
                }
            }
        }
    }
}
