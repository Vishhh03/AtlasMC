package com.projectatlas.classes

import com.projectatlas.AtlasPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

class ClassManager(private val plugin: AtlasPlugin) {

    companion object {
        const val CLASS_CHANGE_COOLDOWN_MS = 24 * 60 * 60 * 1000L // 24 hours in milliseconds
        const val CLASS_CHANGE_COST = 500.0 // Cost to change class
    }

    private val classes = mapOf(
        "Vanguard" to ClassDefinition("Vanguard", "Tanky warrior", 30.0, 0.2f),
        "Scout" to ClassDefinition("Scout", "Fast explorer", 20.0, 0.3f),
        "Medic" to ClassDefinition("Medic", "Regenerates health", 20.0, 0.2f)
    )

    data class ClassDefinition(
        val name: String,
        val description: String,
        val maxHealth: Double,
        val walkSpeed: Float
    )

    fun getAvailableClasses(): List<String> = classes.keys.toList()
    
    fun getClassChangeCost(): Double = CLASS_CHANGE_COST
    fun getCooldownHours(): Int = (CLASS_CHANGE_COOLDOWN_MS / (60 * 60 * 1000)).toInt()

    /**
     * Check if player can change class (cooldown + already has class)
     */
    fun canChangeClass(player: Player): ClassChangeResult {
        val profile = plugin.identityManager.getPlayer(player.uniqueId) ?: return ClassChangeResult.Error("Profile not found")
        
        // First class is free!
        if (profile.playerClass == null) {
            return ClassChangeResult.Success
        }
        
        // Check cooldown
        val now = System.currentTimeMillis()
        val timeSinceChange = now - profile.lastClassChange
        if (timeSinceChange < CLASS_CHANGE_COOLDOWN_MS) {
            val remainingMs = CLASS_CHANGE_COOLDOWN_MS - timeSinceChange
            val remainingHours = (remainingMs / (60 * 60 * 1000)).toInt()
            val remainingMins = ((remainingMs % (60 * 60 * 1000)) / (60 * 1000)).toInt()
            return ClassChangeResult.OnCooldown(remainingHours, remainingMins)
        }
        
        // Check cost
        if (profile.balance < CLASS_CHANGE_COST) {
            return ClassChangeResult.InsufficientFunds(CLASS_CHANGE_COST, profile.balance)
        }
        
        return ClassChangeResult.Success
    }

    fun setClass(player: Player, className: String, bypassChecks: Boolean = false): Boolean {
        val def = classes[className] ?: return false
        val profile = plugin.identityManager.getPlayer(player.uniqueId) ?: return false
        
        // Skip if same class
        if (profile.playerClass == className) {
            player.sendMessage(Component.text("You are already a $className!", NamedTextColor.YELLOW))
            return false
        }
        
        if (!bypassChecks) {
            // Check if this is NOT their first class (first is free)
            if (profile.playerClass != null) {
                val check = canChangeClass(player)
                when (check) {
                    is ClassChangeResult.OnCooldown -> {
                        player.sendMessage(Component.text("Class change on cooldown! ${check.hours}h ${check.minutes}m remaining.", NamedTextColor.RED))
                        return false
                    }
                    is ClassChangeResult.InsufficientFunds -> {
                        player.sendMessage(Component.text("Class change costs ${CLASS_CHANGE_COST}. You have ${check.current}.", NamedTextColor.RED))
                        return false
                    }
                    is ClassChangeResult.Error -> {
                        player.sendMessage(Component.text(check.message, NamedTextColor.RED))
                        return false
                    }
                    ClassChangeResult.Success -> {
                        // Deduct cost
                        profile.balance -= CLASS_CHANGE_COST
                        player.sendMessage(Component.text("Paid $CLASS_CHANGE_COST for class change.", NamedTextColor.GOLD))
                    }
                }
            }
        }
        
        // Update Profile
        profile.playerClass = def.name
        profile.lastClassChange = System.currentTimeMillis()
        plugin.identityManager.saveProfile(player.uniqueId)

        applyClassEffects(player)
        return true
    }

    fun applyClassEffects(player: Player) {
        val profile = plugin.identityManager.getPlayer(player.uniqueId) ?: return
        val className = profile.playerClass ?: return
        val def = classes[className] ?: return

        // 1. Max Health
        player.getAttribute(Attribute.MAX_HEALTH)?.baseValue = def.maxHealth
        
        // 2. Walk Speed
        player.walkSpeed = def.walkSpeed

        // 3. Passive Effects (Medic)
        if (className == "Medic") {
            player.addPotionEffect(PotionEffect(PotionEffectType.REGENERATION, PotionEffect.INFINITE_DURATION, 0, false, false))
        } else {
             if (player.hasPotionEffect(PotionEffectType.REGENERATION)) {
                 player.removePotionEffect(PotionEffectType.REGENERATION)
             }
        }
    }

    sealed class ClassChangeResult {
        object Success : ClassChangeResult()
        data class OnCooldown(val hours: Int, val minutes: Int) : ClassChangeResult()
        data class InsufficientFunds(val required: Double, val current: Double) : ClassChangeResult()
        data class Error(val message: String) : ClassChangeResult()
    }
}
