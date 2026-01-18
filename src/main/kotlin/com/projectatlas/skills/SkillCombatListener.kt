package com.projectatlas.skills

import com.projectatlas.AtlasPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Sound
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent

class SkillCombatListener(private val plugin: AtlasPlugin) : Listener {

    private val skillManager: SkillTreeManager
        get() = plugin.skillTreeManager
        
    private val effectSystem: com.projectatlas.animation.SkillEffectSystem
        get() = plugin.skillEffectSystem

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        val defender = event.entity as? LivingEntity ?: return
        
        // 1. DODGE LOGIC (Defender is Player)
        if (defender is Player) {
            val dodgeChance = skillManager.getDodgeChance(defender)
            if (dodgeChance > 0 && Math.random() < dodgeChance) {
                event.isCancelled = true
                effectSystem.playDodgeEffect(defender)
                defender.sendMessage(Component.text("⚡ Dodged!", NamedTextColor.AQUA))
                return // Damage avoided completely
            }
        }
        
        // 2. THORNS LOGIC (Defender is Player)
        if (defender is Player && event.damager is LivingEntity) {
            val thornsPercent = skillManager.getThornsPercent(defender)
            if (thornsPercent > 0) {
                val thornsDamage = event.finalDamage * thornsPercent
                if (thornsDamage > 0.5) {
                    val attacker = event.damager as LivingEntity
                    attacker.damage(thornsDamage, defender)
                    effectSystem.playThornsEffect(defender, attacker, thornsDamage)
                }
            }
        }

        // 3. ATTACK LOGIC (Attacker is Player)
        val attacker = when (val damager = event.damager) {
            is Player -> damager
            is Projectile -> damager.shooter as? Player
            else -> null
        } ?: return // If attacker not player, we are done
        
        var damage = event.damage
        
        // A. Base Damage Multipliers (Melee/Bow/Sneak)
        if (event.damager is Projectile) {
            damage *= skillManager.getBowDamageMultiplier(attacker)
        } else {
             damage *= skillManager.getMeleeDamageMultiplier(attacker)
        }
        
        if (attacker.isSneaking) {
            damage *= skillManager.getSneakDamageMultiplier(attacker)
        }
        
        // B. Critical Hit
        val critChance = skillManager.getCritChance(attacker)
        var isCrit = false
        if (critChance > 0 && Math.random() < critChance) {
            isCrit = true
            val critMult = skillManager.getCritMultiplier(attacker)
            damage *= critMult
            effectSystem.playCritEffect(attacker, defender)
            attacker.playSound(attacker.location, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.2f)
        }
        
        // C. Executioner (Low HP Bonus)
        skillManager.getExecuteDamage(attacker)?.let { (threshold, multiplier) ->
            if (defender.health / defender.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)!!.value < threshold) {
                damage *= multiplier
                effectSystem.playExecuteEffect(attacker, defender)
                attacker.playSound(attacker.location, Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 1.0f, 1.5f)
            }
        }
        
        // D. Berserker (Low Player HP Bonus)
        skillManager.getBerserkerBonus(attacker)?.let { (threshold, bonus) ->
            if (attacker.health / attacker.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)!!.value < threshold) {
                damage *= (1.0 + bonus)
                // Visual is handled by toggle aura, but we can add a sound cue on hit here
            }
        }

        // Apply modified damage
        event.damage = damage
        
        // E. Life Leech (After final damage calc)
        val leechPercent = skillManager.getLifeLeechPercent(attacker)
        if (leechPercent > 0) {
            val healAmount = damage * leechPercent
            if (healAmount > 0) {
                val newHealth = (attacker.health + healAmount).coerceAtMost(attacker.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)!!.value)
                attacker.health = newHealth
                effectSystem.playLeechEffect(attacker, defender, healAmount)
            }
        }
        
        // F. Siege Damage Bonus (if attacking a siege mob)
        // Check if target is a siege mob
        if (defender.persistentDataContainer.has(org.bukkit.NamespacedKey(plugin, "siege_role"), org.bukkit.persistence.PersistentDataType.STRING)) {
            val siegeMult = skillManager.getSiegeDamageMultiplier(attacker)
            if (siegeMult > 1.0) {
                event.damage = event.damage * siegeMult
            }
        }
    }
    
    // Handle Fall Damage (NoFall)
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageEvent) {
        if (event.cause == EntityDamageEvent.DamageCause.FALL) {
            val player = event.entity as? Player ?: return
            
            if (skillManager.hasNoFallDamage(player)) {
                event.isCancelled = true
                effectSystem.playNoFallDamageEffect(player)
            }
        }
    }
}
