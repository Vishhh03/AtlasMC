package com.projectatlas.classes

import com.projectatlas.AtlasPlugin
import com.projectatlas.skills.SkillTreeManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class AbilityListener(private val plugin: AtlasPlugin) : Listener {
    
    // Key: "UUID_AbilityID" -> Timestamp
    private val cooldowns = ConcurrentHashMap<String, Long>()

    @EventHandler
    fun onRightClick(event: PlayerInteractEvent) {
        // Must be right-click
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return
        
        val player = event.player
        val item = player.inventory.itemInMainHand.type
        
        // Skip items that should use vanilla behavior (maps, food, etc.)
        if (item == Material.FILLED_MAP || item == Material.MAP || 
            item == Material.COMPASS || item == Material.CLOCK ||
            item.isEdible || item == Material.ENDER_PEARL ||
            item == Material.SNOWBALL || item == Material.EGG ||
            item == Material.BOW || item == Material.CROSSBOW ||
            item == Material.FISHING_ROD || item == Material.TRIDENT ||
            item == Material.SPLASH_POTION || item == Material.LINGERING_POTION) {
            return
        }
        
        val unlocked = plugin.skillTreeManager.getUnlockedNodes(player)
        
        // 1. Fireball (Blaze Rod)
        if (item == Material.BLAZE_ROD && "fireball_1" in unlocked) {
            val cooldown = plugin.configManager.fireballCooldownTicks * 50L
            castAbility(player, "fireball_1", cooldown) {
                castFireball(player)
            }
        }
        
        // 2. Shield Wall (Shield or Sword)
        else if ((item == Material.SHIELD || item.name.contains("SWORD")) && "shield_wall_1" in unlocked) {
            val cooldown = plugin.configManager.shieldWallCooldownTicks * 50L
            castAbility(player, "shield_wall_1", cooldown) {
                castShieldWall(player)
            }
        }
        
        // 3. Healing Pulse (Beacon or Golden Apple)
        else if ((item == Material.BEACON || item == Material.GOLDEN_APPLE) && "healing_pulse_1" in unlocked) {
            val cooldown = plugin.configManager.healingPulseCooldownTicks * 50L
            castAbility(player, "healing_pulse_1", cooldown) {
                castHealingPulse(player)
            }
        }
        
        // 4. Dash (Feather)
        else if (item == Material.FEATHER && "dash_1" in unlocked) {
            val cooldown = plugin.configManager.dashCooldownTicks * 50L
            castAbility(player, "dash_1", cooldown) {
                castDash(player)
            }
        }
    }
    
    private fun castAbility(player: Player, abilityId: String, cooldownMs: Long, action: () -> Unit) {
        val key = "${player.uniqueId}_$abilityId"
        val now = System.currentTimeMillis()
        val lastUse = cooldowns[key] ?: 0L
        
        if (now - lastUse < cooldownMs) {
            val remaining = (cooldownMs - (now - lastUse)) / 1000
            player.sendMessage(Component.text("Ability on cooldown! ${remaining}s", NamedTextColor.RED))
            // Play error sound?
            player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f)
            return
        }
        
        action()
        cooldowns[key] = now
    }
    
    // --- Abilities ---

    private fun castFireball(player: Player) {
        player.sendMessage(Component.text("🔥 FIREBALL!", NamedTextColor.RED))
        player.playSound(player.location, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 1.0f)
        
        val fireball = player.launchProjectile(org.bukkit.entity.Fireball::class.java)
        fireball.yield = 0F // No block damage
        fireball.setIsIncendiary(false)
        fireball.velocity = player.location.direction.multiply(1.5)
    }
    
    private fun castShieldWall(player: Player) {
        player.sendMessage(Component.text("🛡️ SHIELD WALL ACTIVATED!", NamedTextColor.GOLD))
        player.playSound(player.location, Sound.ITEM_SHIELD_BLOCK, 1.0f, 0.5f)
        
        val duration = plugin.configManager.shieldWallDurationTicks
        val effect = PotionEffect(PotionEffectType.RESISTANCE, duration, 2, false, false) // Res III for configured duration
        player.addPotionEffect(effect)
        
        // Allies
        player.getNearbyEntities(5.0, 5.0, 5.0).forEach { entity ->
            if (entity is Player) {
                entity.addPotionEffect(effect)
                entity.sendMessage(Component.text("Protected by Shield Wall!", NamedTextColor.BLUE))
            }
        }
    }
    
    private fun castDash(player: Player) {
        player.sendMessage(Component.text("⚡ DASH!", NamedTextColor.AQUA))
        player.playSound(player.location, Sound.ENTITY_BAT_TAKEOFF, 1.0f, 1.2f)
        
        player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 60, 3, false, false)) // Speed IV for 3s
        val velocity = plugin.configManager.dashVelocity
        player.velocity = player.location.direction.multiply(velocity).setY(0.5)
    }
    
    private fun castHealingPulse(player: Player) {
        player.sendMessage(Component.text("❤ HEALING PULSE!", NamedTextColor.LIGHT_PURPLE))
        player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f)
        player.world.spawnParticle(org.bukkit.Particle.HEART, player.location.add(0.0, 1.0, 0.0), 10, 0.5, 0.5, 0.5)
        
        val healAmount = plugin.configManager.healingPulseAmount
        
        // Self
        val newHealth = (player.health + healAmount).coerceAtMost(player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0)
        player.health = newHealth
        
        // Allies
        player.getNearbyEntities(5.0, 5.0, 5.0).forEach { entity ->
            if (entity is Player) {
                val max = entity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
                entity.health = (entity.health + healAmount).coerceAtMost(max)
                entity.sendMessage(Component.text("Healed by Pulse!", NamedTextColor.GREEN))
                entity.world.spawnParticle(org.bukkit.Particle.HEART, entity.location.add(0.0, 1.0, 0.0), 5)
            }
        }
    }
}
