package com.projectatlas.events

import com.projectatlas.AtlasPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.block.Chest
import org.bukkit.inventory.ItemStack
import java.util.Random

class SupplyDropEvent(private val plugin: AtlasPlugin) {
    private val random = Random()

    fun trigger() {
        val world = plugin.server.worlds.firstOrNull() ?: return
        val centerX = 0
        val centerZ = 0
        val range = plugin.configManager.supplyDropRadius

        val x = centerX + (random.nextInt(range * 2) - range)
        val z = centerZ + (random.nextInt(range * 2) - range)
        
        val highestBlock = world.getHighestBlockAt(x, z)
        val targetBlock = highestBlock.location.add(0.0, 1.0, 0.0).block
        
        targetBlock.type = Material.CHEST
        val chest = targetBlock.state as? Chest ?: return
        
        // Populate Loot
        val loot = listOf(
            ItemStack(Material.DIAMOND, random.nextInt(3) + 1),
            ItemStack(Material.IRON_INGOT, random.nextInt(10) + 5),
            ItemStack(Material.GOLDEN_APPLE, random.nextInt(2) + 1),
            ItemStack(Material.EXPERIENCE_BOTTLE, random.nextInt(8) + 1),
            ItemStack(Material.TNT, random.nextInt(4) + 1)
        )
        
        loot.forEach { 
            if (random.nextBoolean()) {
                chest.inventory.addItem(it)
            }
        }
        
        plugin.server.broadcast(Component.text("--------------------------------", NamedTextColor.GOLD))
        plugin.server.broadcast(Component.text(">> SUPPLY DROP DETECTED <<", NamedTextColor.RED))
        plugin.server.broadcast(Component.text("Coordinates: X: $x, Z: $z", NamedTextColor.YELLOW))
        plugin.server.broadcast(Component.text("--------------------------------", NamedTextColor.GOLD))
    }
}
