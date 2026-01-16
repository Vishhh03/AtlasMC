package com.projectatlas.gameplay

import com.projectatlas.AtlasPlugin
import com.projectatlas.visual.CustomItemManager
import com.projectatlas.visual.CustomItemManager.ModelData
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.inventory.EquipmentSlot

class ItemEffectListener(private val plugin: AtlasPlugin) : Listener {

    @EventHandler
    fun onPlayerUnteract(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        if (!event.action.isRightClick) return
        
        val player = event.player
        val item = event.item ?: return
        
        val modelData = CustomItemManager.getModelData(item)
        if (modelData == 0) return

        when (modelData) {
            ModelData.HEALING_SALVE -> {
                event.isCancelled = true // Prevent drinking animation if using honey bottle base, or handle manually
                
                // Heal 4 HP (2 hearts)
                val maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
                if (player.health >= maxHealth) {
                    player.sendMessage(Component.text("You are already at full health.", NamedTextColor.RED))
                    return
                }
                
                player.health = (player.health + 4.0).coerceAtMost(maxHealth)
                player.playSound(player.location, Sound.ENTITY_GENERIC_DRINK, 1.0f, 1.0f)
                player.sendMessage(Component.text("You used a Healing Salve.", NamedTextColor.GREEN))
                
                // Consume item
                item.amount -= 1
            }
            ModelData.SPIRIT_TOTEM -> {
                event.isCancelled = true
                
                // Regen II for 10 seconds
                player.addPotionEffect(PotionEffect(PotionEffectType.REGENERATION, 200, 1))
                player.playSound(player.location, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f)
                player.sendMessage(Component.text("The Spirit Totem grants you vitality!", NamedTextColor.AQUA))
                
                // Consume item
                item.amount -= 1
            }
        }
    }
}
