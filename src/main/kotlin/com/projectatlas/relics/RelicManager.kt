package com.projectatlas.relics

import com.projectatlas.AtlasPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.Random

/**
 * Relic System - Rare ancient artifacts with unique powers.
 * Relics spawn randomly in the world and grant special abilities.
 */
class RelicManager(private val plugin: AtlasPlugin) : Listener {
    
    enum class RelicType(val displayName: String, val material: Material, val color: NamedTextColor, val lore: List<String>) {
        PHOENIX_FEATHER("Phoenix Feather", Material.BLAZE_ROD, NamedTextColor.GOLD, 
            listOf("§6Ancient feather of rebirth", "§7Right-click: Instant full heal + Fire Resistance", "§8Cooldown: 5 minutes")),
        VOID_SHARD("Void Shard", Material.ENDER_EYE, NamedTextColor.DARK_PURPLE,
            listOf("§5Fragment of the End", "§7Right-click: Teleport 20 blocks forward", "§8Cooldown: 30 seconds")),
        FROST_HEART("Frost Heart", Material.PACKED_ICE, NamedTextColor.AQUA,
            listOf("§bHeart of an ancient glacier", "§7Right-click: Freeze nearby enemies", "§8Cooldown: 2 minutes")),
        TITAN_STONE("Titan Stone", Material.NETHERITE_INGOT, NamedTextColor.DARK_GRAY,
            listOf("§8Core of a fallen titan", "§7Right-click: Strength III + Resistance II (10s)", "§8Cooldown: 3 minutes")),
        STORM_ORB("Storm Orb", Material.HEART_OF_THE_SEA, NamedTextColor.BLUE,
            listOf("§9Captured lightning essence", "§7Right-click: Strike lightning at target", "§8Cooldown: 1 minute"))
    }
    
    private val cooldowns = mutableMapOf<String, Long>() // "uuid:relic" -> timestamp
    
    fun createRelic(type: RelicType): ItemStack {
        val item = ItemStack(type.material)
        val meta = item.itemMeta ?: return item
        
        meta.displayName(Component.text("✦ ${type.displayName} ✦", type.color, TextDecoration.BOLD))
        meta.lore(type.lore.map { Component.text(it) })
        meta.addEnchant(Enchantment.UNBREAKING, 1, true)
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
        
        // Custom model data to identify relics
        meta.setCustomModelData(9000 + type.ordinal)
        
        item.itemMeta = meta
        return item
    }
    
    fun spawnRelicInWorld(location: Location) {
        val type = RelicType.entries.random()
        val item = location.world.dropItemNaturally(location, createRelic(type))
        item.isGlowing = true
        item.customName(Component.text("✦ RELIC ✦", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
        item.isCustomNameVisible = true
        
        // Particle beacon
        plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            if (!item.isValid || item.isDead) return@Runnable
            item.world.spawnParticle(Particle.END_ROD, item.location.add(0.0, 1.0, 0.0), 5, 0.2, 0.5, 0.2, 0.01)
        }, 0L, 20L)
        
        // Announce
        plugin.server.broadcast(Component.empty())
        plugin.server.broadcast(Component.text("✦ ANCIENT RELIC DISCOVERED ✦", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
        plugin.server.broadcast(Component.text("A ${type.displayName} has appeared!", type.color))
        plugin.server.broadcast(Component.text("Location: ${location.blockX}, ${location.blockY}, ${location.blockZ}", NamedTextColor.GRAY))
        plugin.server.broadcast(Component.empty())
    }
    
    @EventHandler
    fun onRelicUse(event: PlayerInteractEvent) {
        if (!event.action.name.contains("RIGHT")) return
        val item = event.item ?: return
        val meta = item.itemMeta ?: return
        val modelData = meta.customModelData
        
        if (modelData < 9000 || modelData > 9000 + RelicType.entries.size) return
        
        val type = RelicType.entries.getOrNull(modelData - 9000) ?: return
        val player = event.player
        
        // Cooldown check
        val key = "${player.uniqueId}:${type.name}"
        val now = System.currentTimeMillis()
        val lastUse = cooldowns[key] ?: 0L
        val cooldownMs = getCooldown(type)
        
        if (now - lastUse < cooldownMs) {
            val remaining = (cooldownMs - (now - lastUse)) / 1000
            player.sendMessage(Component.text("Relic on cooldown! ${remaining}s", NamedTextColor.RED))
            return
        }
        
        // Execute relic power
        when (type) {
            RelicType.PHOENIX_FEATHER -> {
                player.health = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
                player.addPotionEffect(PotionEffect(PotionEffectType.FIRE_RESISTANCE, 600, 0))
                player.world.spawnParticle(Particle.FLAME, player.location, 50, 1.0, 1.0, 1.0, 0.1)
                player.playSound(player.location, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.5f)
                player.sendMessage(Component.text("Phoenix power restores you!", NamedTextColor.GOLD))
            }
            RelicType.VOID_SHARD -> {
                val target = player.location.add(player.location.direction.multiply(20))
                target.y = player.world.getHighestBlockYAt(target.toLocation(player.world)) + 1.0
                player.teleport(target.toLocation(player.world).apply { 
                    yaw = player.location.yaw
                    pitch = player.location.pitch
                })
                player.world.spawnParticle(Particle.PORTAL, player.location, 100, 0.5, 1.0, 0.5)
                player.playSound(player.location, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f)
                player.sendMessage(Component.text("Void shift complete!", NamedTextColor.DARK_PURPLE))
            }
            RelicType.FROST_HEART -> {
                player.getNearbyEntities(8.0, 4.0, 8.0).filterIsInstance<Player>().forEach { target ->
                    if (target != player) {
                        target.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 100, 2))
                        target.addPotionEffect(PotionEffect(PotionEffectType.MINING_FATIGUE, 100, 1))
                        target.world.spawnParticle(Particle.SNOWFLAKE, target.location, 30, 0.5, 1.0, 0.5)
                    }
                }
                player.world.spawnParticle(Particle.SNOWFLAKE, player.location, 100, 4.0, 2.0, 4.0, 0.01)
                player.playSound(player.location, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.5f)
                player.sendMessage(Component.text("Frost wave unleashed!", NamedTextColor.AQUA))
            }
            RelicType.TITAN_STONE -> {
                player.addPotionEffect(PotionEffect(PotionEffectType.STRENGTH, 200, 2))
                player.addPotionEffect(PotionEffect(PotionEffectType.RESISTANCE, 200, 1))
                player.world.spawnParticle(Particle.LAVA, player.location, 30, 0.5, 1.0, 0.5)
                player.playSound(player.location, Sound.ENTITY_IRON_GOLEM_HURT, 1.0f, 0.5f)
                player.sendMessage(Component.text("Titan's power flows through you!", NamedTextColor.DARK_GRAY))
            }
            RelicType.STORM_ORB -> {
                val target = player.getTargetBlock(null, 50)
                if (target != null && target.type != Material.AIR) {
                    player.world.strikeLightning(target.location)
                    player.playSound(player.location, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f)
                    player.sendMessage(Component.text("Storm called!", NamedTextColor.BLUE))
                }
            }
        }
        
        cooldowns[key] = now
        event.isCancelled = true
    }
    
    private fun getCooldown(type: RelicType): Long {
        return when (type) {
            RelicType.PHOENIX_FEATHER -> 300_000L // 5 min
            RelicType.VOID_SHARD -> 30_000L // 30s
            RelicType.FROST_HEART -> 120_000L // 2 min
            RelicType.TITAN_STONE -> 180_000L // 3 min
            RelicType.STORM_ORB -> 60_000L // 1 min
        }
    }
    
    // Scheduled relic spawn (called from EventManager or similar)
    fun scheduleRandomRelicSpawn() {
        plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            val players = plugin.server.onlinePlayers.toList()
            if (players.isEmpty()) return@Runnable
            
            // 5% chance per check (every 10 min = ~30% per hour)
            if (Random().nextDouble() > 0.05) return@Runnable
            
            val lucky = players.random()
            val loc = lucky.location.clone().add(
                (Random().nextDouble() - 0.5) * 100,
                0.0,
                (Random().nextDouble() - 0.5) * 100
            )
            loc.y = lucky.world.getHighestBlockYAt(loc) + 1.0
            
            spawnRelicInWorld(loc)
        }, 12000L, 12000L) // Check every 10 minutes
    }
}
