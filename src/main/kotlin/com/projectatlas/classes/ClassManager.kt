package com.projectatlas.classes

import com.projectatlas.AtlasPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

class ClassManager(private val plugin: AtlasPlugin) {

    // Definition of available classes
    private val classes = mapOf(
        "Vanguard" to ClassDefinition("Vanguard", "Tanky warrior", 30.0, 0.2f),
        "Scout" to ClassDefinition("Scout", "Fast explorer", 20.0, 0.3f), // Default speed is ~0.2
        "Medic" to ClassDefinition("Medic", "Regenerates health", 20.0, 0.2f)
    )

    data class ClassDefinition(
        val name: String,
        val description: String,
        val maxHealth: Double,
        val walkSpeed: Float
    )

    fun getAvailableClasses(): List<String> = classes.keys.toList()

    fun setClass(player: Player, className: String): Boolean {
        val def = classes[className] ?: return false
        
        // Update Profile
        val profile = plugin.identityManager.getPlayer(player.uniqueId) ?: return false
        profile.playerClass = def.name
        plugin.identityManager.saveProfile(player.uniqueId)

        applyClassEffects(player)
        return true
    }

    fun applyClassEffects(player: Player) {
        val profile = plugin.identityManager.getPlayer(player.uniqueId) ?: return
        val className = profile.playerClass ?: return
        val def = classes[className] ?: return

        // 1. Max Health
        player.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.baseValue = def.maxHealth
        
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
}
