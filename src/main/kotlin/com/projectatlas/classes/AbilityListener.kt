package com.projectatlas.classes

import com.projectatlas.AtlasPlugin
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
    
    private val cooldowns = ConcurrentHashMap<UUID, Long>()

    @EventHandler
    fun onRightClick(event: PlayerInteractEvent) {
        // Must be right-click
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return
        
        // Item check: Must be holding specific items? 
        // For simplicity: Sword for Vanguard, Feather for Scout, Golden Apple/Dye for Medic?
        // Let's stick to: "Holding any sword/axe" or just "Sneak + Right Click empty hand"?
        // CDD says "Right-click usually triggers them."
        // Let's implement: Right-Click with SWORD (Vanguard), FEATHER (Scout), POPPY/DYE (Medic).
        // Or simpler: Right-Click with Class Item.
        
        val player = event.player
        val profile = plugin.identityManager.getPlayer(player.uniqueId) ?: return
        val className = profile.playerClass ?: return
        
        if (!isHoldingClassItem(player, className)) return
        
        // Cooldown Check
        val now = System.currentTimeMillis()
        val lastUse = cooldowns[player.uniqueId] ?: 0L
        val cooldownMs = getCooldown(className)
        
        if (now - lastUse < cooldownMs) {
            val remaining = (cooldownMs - (now - lastUse)) / 1000
            player.sendMessage(Component.text("Ability on cooldown! ${remaining}s", NamedTextColor.RED))
            return
        }
        
        // Execute Ability
        when (className) {
            "Vanguard" -> castShieldWall(player)
            "Scout" -> castDash(player)
            "Medic" -> castHealingPulse(player)
        }
        
        cooldowns[player.uniqueId] = now
    }
    
    private fun isHoldingClassItem(player: Player, className: String): Boolean {
        val type = player.inventory.itemInMainHand.type
        return when (className) {
            "Vanguard" -> type.name.contains("SWORD") || type == Material.SHIELD
            "Scout" -> type == Material.FEATHER || type == Material.BOW
            "Medic" -> type == Material.GOLDEN_APPLE || type == Material.PAPER || type.name.contains("POTION")
            else -> false
        }
    }
    
    private fun getCooldown(className: String): Long {
        return when (className) {
            "Vanguard" -> 20_000L // 20s
            "Scout" -> 10_000L // 10s
            "Medic" -> 15_000L // 15s
            else -> 1000L
        }
    }
    
    // --- Abilities ---
    
    private fun castShieldWall(player: Player) {
        player.sendMessage(Component.text("🛡️ SHIELD WALL ACTIVATED!", NamedTextColor.GOLD))
        player.playSound(player.location, Sound.ITEM_SHIELD_BLOCK, 1.0f, 0.5f)
        
        val effect = PotionEffect(PotionEffectType.RESISTANCE, 100, 2, false, false) // Res III for 5s
        player.addPotionEffect(effect)
        
        // Allies
        player.getNearbyEntities(5.0, 5.0, 5.0).forEach { entity ->
            if (entity is Player) {
                // Check if ally (same city/party - simplistic check for now)
                entity.addPotionEffect(effect)
                entity.sendMessage(Component.text("Protected by Vanguard!", NamedTextColor.BLUE))
            }
        }
    }
    
    private fun castDash(player: Player) {
        player.sendMessage(Component.text("⚡ DASH!", NamedTextColor.AQUA))
        player.playSound(player.location, Sound.ENTITY_BAT_TAKEOFF, 1.0f, 1.2f)
        
        player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 60, 3, false, false)) // Speed IV for 3s
        player.velocity = player.location.direction.multiply(1.5).setY(0.5)
    }
    
    private fun castHealingPulse(player: Player) {
        player.sendMessage(Component.text("❤ HEALING PULSE!", NamedTextColor.LIGHT_PURPLE))
        player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f)
        player.world.spawnParticle(org.bukkit.Particle.HEART, player.location.add(0.0, 1.0, 0.0), 10, 0.5, 0.5, 0.5)
        
        val healAmount = 8.0 // 4 hearts
        
        // Self
        val newHealth = (player.health + healAmount).coerceAtMost(player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0)
        player.health = newHealth
        
        // Allies
        player.getNearbyEntities(5.0, 5.0, 5.0).forEach { entity ->
            if (entity is Player) {
                val max = entity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
                entity.health = (entity.health + healAmount).coerceAtMost(max)
                entity.sendMessage(Component.text("Healed by Medic!", NamedTextColor.GREEN))
                entity.world.spawnParticle(org.bukkit.Particle.HEART, entity.location.add(0.0, 1.0, 0.0), 5)
            }
        }
    }
}
