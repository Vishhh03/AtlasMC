package com.projectatlas.structures

import com.projectatlas.AtlasPlugin
import com.projectatlas.visual.CustomItemManager
import com.projectatlas.visual.CustomItemManager.ModelData
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot

class BlueprintListener(private val plugin: AtlasPlugin) : Listener {

    @EventHandler
    fun onBlueprintUse(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        
        val item = event.item ?: return
        val player = event.player
        val block = event.clickedBlock ?: return
        
        // Prevent placing the paper item itself
        if (CustomItemManager.isCustomItem(item)) {
            val modelData = CustomItemManager.getModelData(item)
            
            val structureType = when (modelData) {
                ModelData.BLUEPRINT_GENERIC -> StructureType.MERCHANT_HUT
                ModelData.BLUEPRINT_BARRACKS -> StructureType.BARRACKS
                ModelData.BLUEPRINT_TURRET -> StructureType.TURRET
                else -> null
            }
            
            if (structureType != null) {
                event.isCancelled = true // Don't place the paper
                
                // Check permissions/cost if needed
                
                // Spawn structure
                val location = block.location.add(0.0, 1.0, 0.0)
                
                if (plugin.structureManager.canBuild(location, structureType)) {
                    plugin.structureManager.spawnStructure(structureType, location)
                    player.sendMessage(Component.text("Constructing ${structureType.name}...", NamedTextColor.GREEN))
                    
                    // Consume item
                    item.amount -= 1
                } else {
                    player.sendMessage(Component.text("Cannot build here! Area obstructed.", NamedTextColor.RED))
                }
            }
        }
    }
}
