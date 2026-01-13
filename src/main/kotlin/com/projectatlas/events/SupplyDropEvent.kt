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
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.entity.Zombie
import org.bukkit.entity.Skeleton
import org.bukkit.entity.Firework
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.attribute.Attribute
import java.util.Random



class SupplyDropEvent(private val plugin: AtlasPlugin) {
    private val random = Random()

    fun trigger() {
        val world = plugin.server.worlds.firstOrNull() ?: return
        val centerX = 0
        val centerZ = 0
        val range = plugin.configManager.supplyDropRadius

        // 1. Find Location (avoid claimed city territory and water)
        var x: Int
        var z: Int
        var targetBlock: org.bukkit.block.Block
        var attempts = 0
        val maxAttempts = 20
        
        do {
            x = centerX + (random.nextInt(range * 2) - range)
            z = centerZ + (random.nextInt(range * 2) - range)
            val highestBlock = world.getHighestBlockAt(x, z)
            targetBlock = highestBlock.location.add(0.0, 1.0, 0.0).block
            attempts++
            
            val claimedCity = plugin.cityManager.getCityAt(targetBlock.chunk)
            val groundBlock = highestBlock
            val isWater = groundBlock.type == Material.WATER || 
                          groundBlock.type == Material.LAVA ||
                          groundBlock.type == Material.SEAGRASS ||
                          groundBlock.type == Material.TALL_SEAGRASS ||
                          groundBlock.type == Material.KELP ||
                          groundBlock.type == Material.KELP_PLANT ||
                          groundBlock.isLiquid
            
            if (claimedCity == null && !isWater) break
            
        } while (attempts < maxAttempts)
        
        // Final validation
        val groundBlock = world.getHighestBlockAt(targetBlock.x, targetBlock.z)
        val isInWater = groundBlock.type == Material.WATER || groundBlock.isLiquid
        
        if (plugin.cityManager.getCityAt(targetBlock.chunk) != null || isInWater) {
            plugin.logger.info("Supply drop aborted: could not find valid land location after $maxAttempts attempts")
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
        // 6. Tiered System
        val tierRoll = random.nextDouble()
        val tier = when {
            tierRoll < 0.6 -> DropTier.COMMON     // 60%
            tierRoll < 0.9 -> DropTier.RARE       // 30%
            else -> DropTier.LEGENDARY            // 10%
        }
        
        spawnTieredLoot(chest, tier)
        spawnTieredGuards(world, dropLocation, tier)
        
        // 7. Firework Launch (Color coded by tier)
        val firework = world.spawn(dropLocation.clone().add(0.0, 1.0, 0.0), Firework::class.java)
        val fireworkMeta = firework.fireworkMeta
        fireworkMeta.addEffect(
            FireworkEffect.builder()
                .with(FireworkEffect.Type.BALL_LARGE)
                .withColor(tier.color, Color.WHITE)
                .withFade(tier.color)
                .trail(true)
                .flicker(true)
                .build()
        )
        fireworkMeta.power = 2
        firework.fireworkMeta = fireworkMeta

        // 8. Broadcast
        val biomeName = targetBlock.biome.key.key.replace("_", " ")
        plugin.server.broadcast(Component.empty())
        plugin.server.broadcast(Component.text("═══════════════════════════════", NamedTextColor.GOLD))
        plugin.server.broadcast(Component.text("  ⚠ SUPPLY DROP DETECTED ⚠", NamedTextColor.RED))
        plugin.server.broadcast(Component.text("  Type: ${tier.displayName}", tier.textColor))
        plugin.server.broadcast(Component.text("  Location: Somewhere in a $biomeName...", NamedTextColor.YELLOW))
        plugin.server.broadcast(Component.text("  Hint: ${x - (x % 100)}, ${z - (z % 100)} (Approx)", NamedTextColor.GRAY))
        plugin.server.broadcast(Component.text("  Guarded by hostiles!", NamedTextColor.RED))
        plugin.server.broadcast(Component.text("═══════════════════════════════", NamedTextColor.GOLD))
        plugin.server.broadcast(Component.empty())
        
        // Play global sound
        plugin.server.onlinePlayers.forEach { player ->
            player.playSound(player.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f)
        }
        
        plugin.logger.info("Supply drop ($tier) spawned at $x, ${targetBlock.y}, $z")
    }
    
    // ========== TIER LOGIC ==========
    
    enum class DropTier(val displayName: String, val color: Color, val textColor: NamedTextColor) {
        COMMON("Common Drop", Color.GRAY, NamedTextColor.GRAY),
        RARE("Rare Drop", Color.AQUA, NamedTextColor.AQUA),
        LEGENDARY("LEGENDARY DROP", Color.ORANGE, NamedTextColor.GOLD)
    }

    private fun spawnTieredLoot(chest: Chest, tier: DropTier) {
        val inv = chest.inventory
        
        when (tier) {
            DropTier.COMMON -> {
                if (random.nextBoolean()) inv.addItem(ItemStack(Material.IRON_INGOT, random.nextInt(8) + 4))
                if (random.nextBoolean()) inv.addItem(ItemStack(Material.BREAD, random.nextInt(10) + 5))
                if (random.nextBoolean()) inv.addItem(ItemStack(Material.GOLD_INGOT, random.nextInt(5) + 2))
                if (random.nextInt(10) < 3) inv.addItem(ItemStack(Material.DIAMOND, 1))
                inv.addItem(ItemStack(Material.EXPERIENCE_BOTTLE, random.nextInt(4) + 1))
            }
            DropTier.RARE -> {
                inv.addItem(ItemStack(Material.DIAMOND, random.nextInt(3) + 2))
                inv.addItem(ItemStack(Material.GOLDEN_APPLE, random.nextInt(2) + 1))
                inv.addItem(ItemStack(Material.IRON_BLOCK, 1))
                inv.addItem(ItemStack(Material.TNT, random.nextInt(5) + 2))
                inv.addItem(ItemStack(Material.EXPERIENCE_BOTTLE, random.nextInt(8) + 4))
                if (random.nextBoolean()) inv.addItem(ItemStack(Material.TURTLE_SCUTE, 1))
            }
            DropTier.LEGENDARY -> {
                // Special: Siege Banner (Rare!)
                if (random.nextDouble() < 0.3) {
                    val banner = ItemStack(Material.RED_BANNER)
                    val meta = banner.itemMeta
                    meta.displayName(Component.text("Siege Banner", NamedTextColor.RED, net.kyori.adventure.text.format.TextDecoration.BOLD))
                    meta.lore(listOf(
                        Component.text("Use on an enemy city to trigger a Siege!", NamedTextColor.GRAY),
                        Component.text("Consumable", NamedTextColor.DARK_GRAY)
                    ))
                    banner.itemMeta = meta
                    inv.addItem(banner)
                }
                inv.addItem(ItemStack(Material.DIAMOND_BLOCK, random.nextInt(2) + 1))
                inv.addItem(ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1))
                inv.addItem(ItemStack(Material.NETHERITE_SCRAP, random.nextInt(2) + 1))
                inv.addItem(ItemStack(Material.TOTEM_OF_UNDYING, 1))
                inv.addItem(ItemStack(Material.EXPERIENCE_BOTTLE, 16))
                inv.addItem(ItemStack(Material.END_CRYSTAL, 1))
            }
        }
    }

    private fun spawnTieredGuards(world: org.bukkit.World, center: org.bukkit.Location, tier: DropTier) {
        val guardCount = when (tier) {
            DropTier.COMMON -> 4
            DropTier.RARE -> 6
            DropTier.LEGENDARY -> 10
        }
        
        for (i in 0 until guardCount) {
            val offsetX = (random.nextInt(8) - 4).toDouble()
            val offsetZ = (random.nextInt(8) - 4).toDouble()
            
            // Safe spawn logic: Get highest block at offset
            val spawnX = center.blockX + offsetX.toInt()
            val spawnZ = center.blockZ + offsetZ.toInt()
            val spawnY = world.getHighestBlockYAt(spawnX, spawnZ) + 1.0
            
            val spawnLoc = org.bukkit.Location(world, spawnX.toDouble() + 0.5, spawnY, spawnZ.toDouble() + 0.5)
            
            val entityType = when (tier) {
                DropTier.COMMON -> if (random.nextBoolean()) Zombie::class.java else Skeleton::class.java
                DropTier.RARE -> when (random.nextInt(3)) {
                    0 -> org.bukkit.entity.Vindicator::class.java
                    1 -> org.bukkit.entity.Stray::class.java
                    else -> org.bukkit.entity.Witch::class.java
                }
                DropTier.LEGENDARY -> when (random.nextInt(5)) {
                    0 -> org.bukkit.entity.WitherSkeleton::class.java
                    1 -> org.bukkit.entity.Ravager::class.java
                    2 -> org.bukkit.entity.Evoker::class.java
                    3 -> org.bukkit.entity.PiglinBrute::class.java
                    else -> org.bukkit.entity.Illusioner::class.java
                }
            }
            
            world.spawn(spawnLoc, entityType).apply {
                customName(Component.text("${tier.displayName} Guardian", NamedTextColor.RED).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD))
                isCustomNameVisible = true
                removeWhenFarAway = false
                persistentDataContainer.set(org.bukkit.NamespacedKey.fromString("atlas_guard")!!, org.bukkit.persistence.PersistentDataType.BYTE, 1.toByte())
                
                // --- ATTRIBUTES & BUFFS ---
                val maxHealth = when(tier) {
                    DropTier.COMMON -> 30.0
                    DropTier.RARE -> 60.0
                    DropTier.LEGENDARY -> 100.0
                }
                getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH)?.baseValue = maxHealth
                health = maxHealth
                
                getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE)?.baseValue = when(tier) {
                    DropTier.COMMON -> 6.0
                    DropTier.RARE -> 12.0
                    DropTier.LEGENDARY -> 18.0 // Hits hard
                }
                
                getAttribute(org.bukkit.attribute.Attribute.GENERIC_KNOCKBACK_RESISTANCE)?.baseValue = when(tier) {
                    DropTier.COMMON -> 0.2
                    DropTier.RARE -> 0.5
                    DropTier.LEGENDARY -> 1.0 // Unmovable
                }
                
                // --- EQUIPMENT ---
                if (this is org.bukkit.entity.LivingEntity) {
                    equipment?.apply {
                        itemInMainHandDropChance = 0.0f
                        itemInOffHandDropChance = 0.0f
                        helmetDropChance = 0.1f
                        chestplateDropChance = 0.1f
                        leggingsDropChance = 0.1f
                        bootsDropChance = 0.1f
                        
                        when (tier) {
                            DropTier.COMMON -> {
                                helmet = ItemStack(Material.IRON_HELMET)
                                chestplate = ItemStack(Material.CHAINMAIL_CHESTPLATE)
                                leggings = ItemStack(Material.CHAINMAIL_LEGGINGS)
                                boots = ItemStack(Material.IRON_BOOTS)
                                setItemInMainHand(ItemStack(Material.IRON_SWORD))
                            }
                            DropTier.RARE -> {
                                helmet = ItemStack(Material.DIAMOND_HELMET)
                                chestplate = ItemStack(Material.IRON_CHESTPLATE).apply { addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.PROTECTION, 2) }
                                leggings = ItemStack(Material.IRON_LEGGINGS)
                                boots = ItemStack(Material.DIAMOND_BOOTS)
                                setItemInMainHand(ItemStack(Material.DIAMOND_SWORD).apply { addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.SHARPNESS, 2) })
                            }
                            DropTier.LEGENDARY -> {
                                helmet = ItemStack(Material.NETHERITE_HELMET).apply { addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.PROTECTION, 4) }
                                chestplate = ItemStack(Material.NETHERITE_CHESTPLATE).apply { addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.THORNS, 3) }
                                leggings = ItemStack(Material.NETHERITE_LEGGINGS)
                                boots = ItemStack(Material.NETHERITE_BOOTS).apply { addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.FEATHER_FALLING, 4) }
                                setItemInMainHand(ItemStack(Material.NETHERITE_SWORD).apply { 
                                    addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.SHARPNESS, 5)
                                    addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.FIRE_ASPECT, 2)
                                })
                                setItemInOffHand(ItemStack(Material.SHIELD))
                            }
                        }
                    }
                    
                    // --- EFFECTS ---
                    addPotionEffect(PotionEffect(PotionEffectType.GLOWING, PotionEffect.INFINITE_DURATION, 0, false, false))
                    if (tier == DropTier.RARE) {
                        addPotionEffect(PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 0))
                    }
                    if (tier == DropTier.LEGENDARY) {
                        addPotionEffect(PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 1))
                        addPotionEffect(PotionEffect(PotionEffectType.FIRE_RESISTANCE, PotionEffect.INFINITE_DURATION, 0))
                        addPotionEffect(PotionEffect(PotionEffectType.STRENGTH, PotionEffect.INFINITE_DURATION, 0)) // Strength I
                        addPotionEffect(PotionEffect(PotionEffectType.RESISTANCE, PotionEffect.INFINITE_DURATION, 0)) // Resistance I
                    }
                    
                    // --- AGGRO ---
                    // Target nearest player within 50 blocks
                    world.getNearbyEntities(spawnLoc, 50.0, 50.0, 50.0).find { it is Player && it.gameMode == GameMode.SURVIVAL }?.let { target ->
                        if (this is org.bukkit.entity.Mob) {
                            this.target = target as org.bukkit.entity.LivingEntity
                        }
                    }
                }
            }
        }
    }
}
