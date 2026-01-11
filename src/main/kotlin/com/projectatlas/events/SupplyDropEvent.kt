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

        // 1. Find Location (avoid claimed city territory)
        var x: Int
        var z: Int
        var targetBlock: org.bukkit.block.Block
        var attempts = 0
        val maxAttempts = 10
        
        do {
            x = centerX + (random.nextInt(range * 2) - range)
            z = centerZ + (random.nextInt(range * 2) - range)
            val highestBlock = world.getHighestBlockAt(x, z)
            targetBlock = highestBlock.location.add(0.0, 1.0, 0.0).block
            attempts++
            
            // Check if this chunk is claimed by a city
            val claimedCity = plugin.cityManager.getCityAt(targetBlock.chunk)
            if (claimedCity == null) break // Found wilderness, use this location
            
        } while (attempts < maxAttempts)
        
        // If all attempts landed in city territory, abort this supply drop
        if (plugin.cityManager.getCityAt(targetBlock.chunk) != null) {
            plugin.logger.info("Supply drop aborted: could not find wilderness location after $maxAttempts attempts")
            return
        }
        
        // 2. Spawn Loot Chest
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
        
        // 3. Spawn Guards (PvE Challenge) - spread out around the chest
        val baseLoc = targetBlock.location.add(0.5, 0.0, 0.5)
        world.spawn(baseLoc.clone().add(2.0, 0.0, 0.0), org.bukkit.entity.Zombie::class.java).apply { customName(Component.text("Loot Guardian")); isCustomNameVisible = true }
        world.spawn(baseLoc.clone().add(-2.0, 0.0, 0.0), org.bukkit.entity.Zombie::class.java).apply { customName(Component.text("Loot Guardian")); isCustomNameVisible = true }
        world.spawn(baseLoc.clone().add(0.0, 0.0, 2.0), org.bukkit.entity.Skeleton::class.java).apply { customName(Component.text("Loot Sniper")); isCustomNameVisible = true }

        // 4. Broadcast (Vague)
        val biome = targetBlock.biome.toString().lowercase().replace("_", " ")
        plugin.server.broadcast(Component.text("--------------------------------", NamedTextColor.GOLD))
        plugin.server.broadcast(Component.text(">> SUPPLY DROP DETECTED <<", NamedTextColor.RED))
        plugin.server.broadcast(Component.text("Location: Somewhere in a $biome...", NamedTextColor.YELLOW))
        plugin.server.broadcast(Component.text("Hint: ${x - (x % 100)}, ${z - (z % 100)} (Approx)", NamedTextColor.GRAY))
        plugin.server.broadcast(Component.text("--------------------------------", NamedTextColor.GOLD))
    }
}
