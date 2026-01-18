package com.projectatlas.items

import com.projectatlas.AtlasPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

/**
 * Manages custom item interactions and mechanics.
 */
class CustomItemManager(private val plugin: AtlasPlugin) : Listener {
    
    companion object {
        // Item identifiers (matched by display name)
        const val HEALING_SALVE = "Healing Salve"
        const val SPIRIT_TOTEM = "Spirit Totem"
        const val EXPLORER_COMPASS = "Explorer Compass"
        const val DUNGEON_KEY = "Dungeon Key"
        
        // Weapons
        const val HOLLOW_KNIGHT_BLADE = "Hollow Knight Blade"
        const val WARDEN_FLAME_SWORD = "Warden Flame Sword"
        const val DRAGON_SLAYER = "Dragon Slayer"
        const val ENDER_SENTINEL_SCYTHE = "Ender Sentinel Scythe"
    }
    
    private val explorerCompassCooldowns = mutableMapOf<java.util.UUID, Long>()
    
    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return
        if (event.hand != EquipmentSlot.HAND) return
        
        val item = event.item ?: return
        val player = event.player
        val itemName = getItemName(item) ?: return
        
        when (itemName) {
            HEALING_SALVE -> {
                event.isCancelled = true
                useHealingSalve(player, item)
            }
            EXPLORER_COMPASS -> {
                event.isCancelled = true
                useExplorerCompass(player)
            }
            HOLLOW_KNIGHT_BLADE -> useHollowKnightDash(player)
            WARDEN_FLAME_SWORD -> useWardenSonicBoom(player)
            DRAGON_SLAYER -> useDragonRoar(player)
            ENDER_SENTINEL_SCYTHE -> useEnderTeleport(player)
        }
    }
    
    @EventHandler
    fun onPlayerDamage(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        
        // Check if this would be fatal
        if (player.health - event.finalDamage > 0) return
        
        // Look for Spirit Totem in inventory
        val totem = findItemInInventory(player, SPIRIT_TOTEM)
        if (totem != null) {
            event.isCancelled = true
            useSpiritTotem(player, totem)
        }
    }
    
    private fun useHealingSalve(player: Player, item: ItemStack) {
        // Consume one
        if (item.amount > 1) {
            item.amount -= 1
        } else {
            player.inventory.setItemInMainHand(null)
        }
        
        // Apply effects
        player.addPotionEffect(PotionEffect(PotionEffectType.REGENERATION, 200, 1)) // Regen II for 10s
        player.addPotionEffect(PotionEffect(PotionEffectType.INSTANT_HEALTH, 1, 1)) // Instant Health II
        
        // Visual/audio feedback
        player.world.spawnParticle(Particle.HEART, player.location.add(0.0, 1.0, 0.0), 10, 0.5, 0.5, 0.5, 0.1)
        player.playSound(player.location, Sound.ENTITY_PLAYER_BURP, 1f, 1.2f)
        player.playSound(player.location, Sound.BLOCK_BREWING_STAND_BREW, 0.5f, 1.5f)
        
        player.sendMessage(Component.text("✚ ", NamedTextColor.GREEN)
            .append(Component.text("Used ", NamedTextColor.GRAY))
            .append(Component.text("Healing Salve", NamedTextColor.GREEN, TextDecoration.BOLD))
            .append(Component.text(" - Regeneration applied!", NamedTextColor.GRAY)))
        
        plugin.logger.info("[CustomItem] ${player.name} used Healing Salve")
    }
    
    private fun useSpiritTotem(player: Player, item: ItemStack) {
        // Consume totem
        if (item.amount > 1) {
            item.amount -= 1
        } else {
            val slot = player.inventory.first(item)
            if (slot >= 0) player.inventory.setItem(slot, null)
        }
        
        // Restore health
        player.health = 1.0
        
        // Apply protective effects
        player.addPotionEffect(PotionEffect(PotionEffectType.ABSORPTION, 200, 3)) // Absorption IV for 10s
        player.addPotionEffect(PotionEffect(PotionEffectType.RESISTANCE, 100, 1)) // Resistance II for 5s
        player.addPotionEffect(PotionEffect(PotionEffectType.REGENERATION, 100, 1)) // Regen II for 5s
        player.addPotionEffect(PotionEffect(PotionEffectType.FIRE_RESISTANCE, 200, 0)) // Fire Res for 10s
        
        // Visual/audio feedback (totem animation)
        player.world.spawnParticle(Particle.TOTEM_OF_UNDYING, player.location.add(0.0, 1.0, 0.0), 100, 1.0, 1.0, 1.0, 0.5)
        player.playSound(player.location, Sound.ITEM_TOTEM_USE, 1f, 1f)
        
        player.sendMessage(Component.text(""))
        player.sendMessage(Component.text("👻 ", NamedTextColor.AQUA)
            .append(Component.text("Spirit Totem", NamedTextColor.AQUA, TextDecoration.BOLD))
            .append(Component.text(" saved you from death!", NamedTextColor.WHITE)))
        player.sendMessage(Component.text(""))
        
        plugin.logger.info("[CustomItem] ${player.name} was saved by Spirit Totem")
    }
    
    private fun useExplorerCompass(player: Player) {
        // Check cooldown (30 seconds)
        val cooldownEnd = explorerCompassCooldowns[player.uniqueId] ?: 0L
        val now = System.currentTimeMillis()
        
        if (now < cooldownEnd) {
            val remaining = (cooldownEnd - now) / 1000
            player.sendMessage(Component.text("⏱ ", NamedTextColor.RED)
                .append(Component.text("Cooldown: ${remaining}s remaining", NamedTextColor.GRAY)))
            return
        }
        
        // Find nearest structure
        val structures = listOf(
            org.bukkit.generator.structure.Structure.VILLAGE_PLAINS,
            org.bukkit.generator.structure.Structure.VILLAGE_DESERT,
            org.bukkit.generator.structure.Structure.VILLAGE_SAVANNA,
            org.bukkit.generator.structure.Structure.VILLAGE_SNOWY,
            org.bukkit.generator.structure.Structure.VILLAGE_TAIGA,
            org.bukkit.generator.structure.Structure.PILLAGER_OUTPOST,
            org.bukkit.generator.structure.Structure.MANSION,
            org.bukkit.generator.structure.Structure.MONUMENT,
            org.bukkit.generator.structure.Structure.STRONGHOLD
        )
        
        var nearestLoc: org.bukkit.Location? = null
        var nearestDist = Double.MAX_VALUE
        var nearestName = ""
        
        for (structure in structures) {
            try {
                val result = player.world.locateNearestStructure(player.location, structure, 10000, false)
                if (result != null) {
                    val dist = player.location.distance(result.location)
                    if (dist < nearestDist) {
                        nearestDist = dist
                        nearestLoc = result.location
                        nearestName = structure.key.key.replace("_", " ").replaceFirstChar { it.uppercase() }
                    }
                }
            } catch (e: Exception) {
                // Structure not found in this world
            }
        }
        
        if (nearestLoc != null) {
            // Set cooldown
            explorerCompassCooldowns[player.uniqueId] = now + 30000 // 30 seconds
            
            // Update compass target (lodestone)
            player.compassTarget = nearestLoc
            
            player.playSound(player.location, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1f)
            
            player.sendMessage(Component.text("🧭 ", NamedTextColor.GOLD)
                .append(Component.text("Explorer Compass", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(" found: ", NamedTextColor.GRAY))
                .append(Component.text(nearestName, NamedTextColor.YELLOW)))
            player.sendMessage(Component.text("   Distance: ", NamedTextColor.GRAY)
                .append(Component.text("${nearestDist.toInt()} blocks", NamedTextColor.WHITE)))
            
            plugin.logger.info("[CustomItem] ${player.name} located $nearestName at ${nearestDist.toInt()} blocks")
        } else {
            player.sendMessage(Component.text("🧭 ", NamedTextColor.RED)
                .append(Component.text("No structures found nearby.", NamedTextColor.GRAY)))
        }
    }
    
    // Helper functions
    private fun getItemName(item: ItemStack): String? {
        val meta = item.itemMeta ?: return null
        val displayName = meta.displayName() ?: return null
        // Extract plain text from Component
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(displayName)
    }
    
    private fun findItemInInventory(player: Player, itemName: String): ItemStack? {
        for (item in player.inventory.contents) {
            if (item != null && getItemName(item) == itemName) {
                return item
            }
        }
        return null
    }
    
    // Create custom items (for commands/loot)
    fun createHealingSalve(amount: Int = 1): ItemStack {
        return ItemStack(Material.HONEY_BOTTLE, amount).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text(HEALING_SALVE, NamedTextColor.GREEN, TextDecoration.BOLD))
                lore(listOf(
                    Component.text("A soothing medicinal salve", NamedTextColor.GRAY),
                    Component.text(""),
                    Component.text("Right-click to use:", NamedTextColor.YELLOW),
                    Component.text("✚ Instant Health II", NamedTextColor.RED),
                    Component.text("✚ Regeneration II (10s)", NamedTextColor.LIGHT_PURPLE),
                    Component.text(""),
                    Component.text("Consumable", NamedTextColor.DARK_GRAY, TextDecoration.ITALIC)
                ))
            }
        }
    }
    
    fun createSpiritTotem(): ItemStack {
        return ItemStack(Material.TOTEM_OF_UNDYING).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text(SPIRIT_TOTEM, NamedTextColor.AQUA, TextDecoration.BOLD))
                lore(listOf(
                    Component.text("Contains the essence of fallen spirits", NamedTextColor.GRAY),
                    Component.text(""),
                    Component.text("Passive Effect:", NamedTextColor.YELLOW),
                    Component.text("👻 Prevents death when in inventory", NamedTextColor.WHITE),
                    Component.text(""),
                    Component.text("On activation:", NamedTextColor.YELLOW),
                    Component.text("✦ Absorption IV (10s)", NamedTextColor.GOLD),
                    Component.text("✦ Resistance II (5s)", NamedTextColor.GRAY),
                    Component.text("✦ Regeneration II (5s)", NamedTextColor.LIGHT_PURPLE),
                    Component.text(""),
                    Component.text("Single Use", NamedTextColor.DARK_GRAY, TextDecoration.ITALIC)
                ))
            }
        }
    }
    
    fun createExplorerCompass(): ItemStack {
        return ItemStack(Material.COMPASS).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text(EXPLORER_COMPASS, NamedTextColor.GOLD, TextDecoration.BOLD))
                lore(listOf(
                    Component.text("An ancient compass imbued with magic", NamedTextColor.GRAY),
                    Component.text(""),
                    Component.text("Right-click to use:", NamedTextColor.YELLOW),
                    Component.text("🧭 Locates the nearest structure", NamedTextColor.WHITE),
                    Component.text(""),
                    Component.text("Cooldown: 30 seconds", NamedTextColor.DARK_GRAY, TextDecoration.ITALIC)
                ))
            }
        }
    }
    
    fun createDungeonKey(): ItemStack {
        return ItemStack(Material.TRIPWIRE_HOOK).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text(DUNGEON_KEY, NamedTextColor.DARK_RED, TextDecoration.BOLD))
                lore(listOf(
                    Component.text("A key to unlock dungeon entrances", NamedTextColor.GRAY),
                    Component.text(""),
                    Component.text("Use: Right-click a dungeon portal", NamedTextColor.YELLOW),
                    Component.text(""),
                    Component.text("Consumable", NamedTextColor.DARK_GRAY, TextDecoration.ITALIC)
                ))
            }
        }
    }

    // --- Weapon Abilities ---

    @EventHandler
    fun onPlayerAttack(event: org.bukkit.event.entity.EntityDamageByEntityEvent) {
        val player = event.damager as? Player ?: return
        val item = player.inventory.itemInMainHand
        val itemName = getItemName(item) ?: return
        
        // List of custom weapons that have durability mechanics
        if (itemName !in listOf(HOLLOW_KNIGHT_BLADE, WARDEN_FLAME_SWORD, DRAGON_SLAYER, ENDER_SENTINEL_SCYTHE)) return
        
        val meta = item.itemMeta as? org.bukkit.inventory.meta.Damageable ?: return
        val maxDurability = item.type.maxDurability
        
        // Check if "broken" (1 durability left)
        if (meta.damage >= maxDurability - 1) {
            event.damage = 1.0 // Punch damage
            player.playSound(player.location, Sound.BLOCK_ANVIL_LAND, 0.5f, 1.5f)
            player.sendMessage(Component.text("Your weapon is broken and ineffective!", NamedTextColor.RED))
        }
    }

    private val weaponCooldowns = mutableMapOf<String, Long>()

    private fun checkWeaponUse(player: Player, weaponName: String, cooldownSeconds: Int, durabilityCost: Int): Boolean {
        // 1. Cooldown Check
        val key = "${player.uniqueId}_$weaponName"
        val now = System.currentTimeMillis()
        val cooldownEnd = weaponCooldowns[key] ?: 0L
        
        if (now < cooldownEnd) {
            val remaining = String.format("%.1f", (cooldownEnd - now) / 1000.0)
            player.sendActionBar(Component.text("$weaponName Cooldown: ${remaining}s", NamedTextColor.RED))
            player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f)
            return false
        }
        
        // 2. Durability Check
        val item = player.inventory.itemInMainHand
        val meta = item.itemMeta as? org.bukkit.inventory.meta.Damageable
        
        if (meta != null && durabilityCost > 0) {
            val maxDurability = item.type.maxDurability
            val currentDamage = meta.damage
            
            // If applying cost would break it (or leave less than 1 durability)
            if (currentDamage + durabilityCost >= maxDurability - 1) {
                player.sendMessage(Component.text("Weapon is too damaged to use ability!", NamedTextColor.RED))
                player.playSound(player.location, Sound.ITEM_SHIELD_BREAK, 1f, 0.5f)
                return false
            }
            
            // Apply durability cost
            meta.damage = currentDamage + durabilityCost
            item.itemMeta = meta
            player.inventory.setItemInMainHand(item)
        }
        
        // Success
        weaponCooldowns[key] = now + (cooldownSeconds * 1000L)
        return true
    }

    private fun useHollowKnightDash(player: Player) {
        val cooldown = plugin.configManager.hollowKnightDashCooldown
        val durability = plugin.configManager.hollowKnightDashDurability
        if (!checkWeaponUse(player, HOLLOW_KNIGHT_BLADE, cooldown, durability)) return
        
        // Dash logic
        val direction = player.location.direction.clone().setY(0).normalize().multiply(1.5)
        player.velocity = player.velocity.add(direction)
        
        // Effects
        player.world.spawnParticle(Particle.SOUL_FIRE_FLAME, player.location, 20, 0.5, 0.5, 0.5, 0.1)
        player.playSound(player.location, "projectatlas:item.hollow_knight.dash", 1f, 0.8f)
        
        // Damage enemies in path
        val damage = plugin.configManager.hollowKnightDashDamage
        player.getNearbyEntities(3.0, 3.0, 3.0).forEach { entity ->
            if (entity is org.bukkit.entity.LivingEntity && entity != player) {
                entity.damage(damage, player)
                entity.world.spawnParticle(Particle.SWEEP_ATTACK, entity.location, 1)
            }
        }
    }

    private fun useWardenSonicBoom(player: Player) {
        val cooldown = plugin.configManager.wardenSonicBoomCooldown
        val durability = plugin.configManager.wardenSonicBoomDurability
        if (!checkWeaponUse(player, WARDEN_FLAME_SWORD, cooldown, durability)) return
        
        // Sonic Boom logic
        val origin = player.eyeLocation
        val direction = origin.direction.normalize()
        val range = plugin.configManager.wardenSonicBoomRange
        val damage = plugin.configManager.wardenSonicBoomDamage
        
        player.playSound(origin, "projectatlas:item.warden_sword.beam", 1f, 1f)
        
        // Beam
        for (i in 0..range) {
            val point = origin.clone().add(direction.clone().multiply(i.toDouble()))
            player.world.spawnParticle(Particle.SONIC_BOOM, point, 1)
            
            // Hit detection
            point.getNearbyEntities(1.5, 1.5, 1.5).forEach { entity ->
                if (entity is org.bukkit.entity.LivingEntity && entity != player) {
                    entity.damage(damage, player) // High damage
                    entity.velocity = direction.clone().multiply(1.5).setY(0.5) // Knockback
                }
            }
        }
    }

    private fun useDragonRoar(player: Player) {
        val cooldown = plugin.configManager.dragonRoarCooldown
        val durability = plugin.configManager.dragonRoarDurability
        if (!checkWeaponUse(player, DRAGON_SLAYER, cooldown, durability)) return
        
        player.playSound(player.location, "projectatlas:item.dragon_slayer.roar", 1f, 1f)
        player.world.spawnParticle(Particle.EXPLOSION_EMITTER, player.location, 1)
        
        val damage = plugin.configManager.dragonRoarDamage
        val knockback = plugin.configManager.dragonRoarKnockback
        
        // Knockback AOE
        player.getNearbyEntities(6.0, 6.0, 6.0).forEach { entity ->
            if (entity is org.bukkit.entity.LivingEntity && entity != player) {
                entity.damage(damage, player)
                val dir = entity.location.toVector().subtract(player.location.toVector()).normalize()
                entity.velocity = dir.multiply(knockback).setY(0.5)
            }
        }
        
        // Buff
        player.addPotionEffect(PotionEffect(PotionEffectType.STRENGTH, 100, 1)) // Str II for 5s
    }

    private fun useEnderTeleport(player: Player) {
        val cooldown = plugin.configManager.enderTeleportCooldown
        val durability = plugin.configManager.enderTeleportDurability
        if (!checkWeaponUse(player, ENDER_SENTINEL_SCYTHE, cooldown, durability)) return
        
        val range = plugin.configManager.enderTeleportRange
        val targetBlock = player.getTargetBlockExact(range)
        val targetLoc = if (targetBlock != null && !targetBlock.type.isSolid) {
            targetBlock.location
        } else {
             // If no block found or solid, try just air location in front
             val loc = player.location.add(player.location.direction.multiply(range))
             if (loc.block.type.isSolid) player.location else loc
        }
        
        // Ensure safe landing
        val safeLoc = targetLoc.clone()
        if (safeLoc.block.type.isSolid) safeLoc.add(0.0, 1.0, 0.0)
        
        // Don't teleport into walls
        if (safeLoc.block.type.isSolid) {
             player.sendMessage(Component.text("Cannot teleport there!", NamedTextColor.RED))
             return
        }
        
        player.world.spawnParticle(Particle.PORTAL, player.location, 30, 0.5, 1.0, 0.5)
        player.playSound(player.location, "projectatlas:item.ender_scythe.teleport", 1f, 1f)
        
        player.teleport(safeLoc.setDirection(player.location.direction))
        
        player.playSound(player.location, "projectatlas:item.ender_scythe.teleport", 1f, 1f)
        player.world.spawnParticle(Particle.END_ROD, player.location, 20, 0.5, 0.5, 0.5, 0.1)
    }
}
