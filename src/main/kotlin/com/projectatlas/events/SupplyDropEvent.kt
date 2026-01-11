package com.projectatlas.events

import com.projectatlas.AtlasPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Color
import org.bukkit.FireworkEffect
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.block.Chest
import org.bukkit.entity.Zombie
import org.bukkit.entity.Skeleton
import org.bukkit.entity.Firework
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
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
            
            val claimedCity = plugin.cityManager.getCityAt(targetBlock.chunk)
            if (claimedCity == null) break
            
        } while (attempts < maxAttempts)
        
        if (plugin.cityManager.getCityAt(targetBlock.chunk) != null) {
            plugin.logger.info("Supply drop aborted: could not find wilderness location after $maxAttempts attempts")
            return
        }
        
        val dropLocation = targetBlock.location.add(0.5, 0.0, 0.5)
        
        // ========== VISUAL EFFECTS ==========
        
        // 2. Lightning Strike Effect (visual only, no damage)
        world.strikeLightningEffect(dropLocation)
        
        // 3. Explosion Sound + Particles
        world.playSound(dropLocation, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.8f)
        world.spawnParticle(Particle.EXPLOSION_EMITTER, dropLocation, 3)
        world.spawnParticle(Particle.FLAME, dropLocation, 50, 2.0, 2.0, 2.0, 0.05)
        
        // 4. Spawn Loot Chest
        targetBlock.type = Material.CHEST
        val chest = targetBlock.state as? Chest ?: return
        
        // Populate Loot
        val loot = listOf(
            ItemStack(Material.DIAMOND, random.nextInt(3) + 2),
            ItemStack(Material.IRON_INGOT, random.nextInt(10) + 5),
            ItemStack(Material.GOLDEN_APPLE, random.nextInt(2) + 1),
            ItemStack(Material.EXPERIENCE_BOTTLE, random.nextInt(8) + 3),
            ItemStack(Material.TNT, random.nextInt(4) + 1),
            ItemStack(Material.ENDER_PEARL, random.nextInt(3) + 1)
        )
        
        loot.forEach { 
            if (random.nextBoolean() || random.nextBoolean()) { // 75% chance per item
                chest.inventory.addItem(it)
            }
        }
        
        // 5. Particle Ring around chest (continuous effect via scheduler)
        val particleTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            world.spawnParticle(Particle.END_ROD, dropLocation.clone().add(0.0, 1.5, 0.0), 5, 1.5, 0.5, 1.5, 0.02)
            world.spawnParticle(Particle.ENCHANT, dropLocation.clone().add(0.0, 0.5, 0.0), 10, 1.0, 0.5, 1.0, 0.5)
        }, 0L, 20L) // Every second
        
        // Cancel particle effect after 5 minutes (6000 ticks)
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            particleTask.cancel()
        }, 6000L)
        
        // 6. Spawn Guards with Glowing Effect
        val baseLoc = dropLocation.clone()
        
        val guardian1 = world.spawn(baseLoc.clone().add(3.0, 0.0, 0.0), Zombie::class.java).apply { 
            customName(Component.text("Loot Guardian", NamedTextColor.RED))
            isCustomNameVisible = true
            addPotionEffect(PotionEffect(PotionEffectType.GLOWING, PotionEffect.INFINITE_DURATION, 0, false, false))
            addPotionEffect(PotionEffect(PotionEffectType.RESISTANCE, PotionEffect.INFINITE_DURATION, 1, false, false)) // Tankier
        }
        
        val guardian2 = world.spawn(baseLoc.clone().add(-3.0, 0.0, 0.0), Zombie::class.java).apply { 
            customName(Component.text("Loot Guardian", NamedTextColor.RED))
            isCustomNameVisible = true
            addPotionEffect(PotionEffect(PotionEffectType.GLOWING, PotionEffect.INFINITE_DURATION, 0, false, false))
            addPotionEffect(PotionEffect(PotionEffectType.RESISTANCE, PotionEffect.INFINITE_DURATION, 1, false, false))
        }
        
        val sniper = world.spawn(baseLoc.clone().add(0.0, 0.0, 4.0), Skeleton::class.java).apply { 
            customName(Component.text("Loot Sniper", NamedTextColor.GOLD))
            isCustomNameVisible = true
            addPotionEffect(PotionEffect(PotionEffectType.GLOWING, PotionEffect.INFINITE_DURATION, 0, false, false))
        }
        
        // 7. Firework Launch (visual beacon)
        val firework = world.spawn(dropLocation.clone().add(0.0, 1.0, 0.0), Firework::class.java)
        val fireworkMeta = firework.fireworkMeta
        fireworkMeta.addEffect(
            FireworkEffect.builder()
                .with(FireworkEffect.Type.BALL_LARGE)
                .withColor(Color.RED, Color.ORANGE)
                .withFade(Color.YELLOW)
                .trail(true)
                .flicker(true)
                .build()
        )
        fireworkMeta.power = 2
        firework.fireworkMeta = fireworkMeta

        // 8. Broadcast (Vague)
        val biomeName = targetBlock.biome.key.key.replace("_", " ")
        plugin.server.broadcast(Component.empty())
        plugin.server.broadcast(Component.text("═══════════════════════════════", NamedTextColor.GOLD))
        plugin.server.broadcast(Component.text("  ⚠ SUPPLY DROP DETECTED ⚠", NamedTextColor.RED))
        plugin.server.broadcast(Component.text("  Location: Somewhere in a $biomeName...", NamedTextColor.YELLOW))
        plugin.server.broadcast(Component.text("  Hint: ${x - (x % 100)}, ${z - (z % 100)} (Approx)", NamedTextColor.GRAY))
        plugin.server.broadcast(Component.text("  Guarded by hostiles!", NamedTextColor.RED))
        plugin.server.broadcast(Component.text("═══════════════════════════════", NamedTextColor.GOLD))
        plugin.server.broadcast(Component.empty())
        
        // Play global sound to all players
        plugin.server.onlinePlayers.forEach { player ->
            player.playSound(player.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f)
        }
        
        plugin.logger.info("Supply drop spawned at $x, ${targetBlock.y}, $z in $biomeName")
    }
}
