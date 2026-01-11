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

        // 1. Find Location
        val x = centerX + (random.nextInt(range * 2) - range)
        val z = centerZ + (random.nextInt(range * 2) - range)
        
        val highestBlock = world.getHighestBlockAt(x, z)
        val targetBlock = highestBlock.location.add(0.0, 1.0, 0.0).block
        
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
        
        // 3. Spawn Guards (PvE Challenge)
        // Spawning 3 Zombies and 1 Skeleton around the chest
        val loc = targetBlock.location.add(0.5, 0.0, 0.5)
        world.spawn(loc, org.bukkit.entity.Zombie::class.java).apply { customName(Component.text("Loot Guardian")); isCustomNameVisible = true }
        world.spawn(loc, org.bukkit.entity.Zombie::class.java).apply { customName(Component.text("Loot Guardian")); isCustomNameVisible = true }
        world.spawn(loc, org.bukkit.entity.Skeleton::class.java).apply { customName(Component.text("Loot Sniper")); isCustomNameVisible = true }

        // 4. Broadcast (Vague)
        val biome = targetBlock.biome.name.lowercase().replace("_", " ")
        plugin.server.broadcast(Component.text("--------------------------------", NamedTextColor.GOLD))
        plugin.server.broadcast(Component.text(">> SUPPLY DROP DETECTED <<", NamedTextColor.RED))
        plugin.server.broadcast(Component.text("Location: Somewhere in a $biome...", NamedTextColor.YELLOW))
        plugin.server.broadcast(Component.text("Hint: ${x - (x % 100)}, ${z - (z % 100)} (Approx)", NamedTextColor.GRAY))
        plugin.server.broadcast(Component.text("--------------------------------", NamedTextColor.GOLD))
    }
}
