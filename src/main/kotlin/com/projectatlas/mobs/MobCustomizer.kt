package com.projectatlas.mobs

import com.projectatlas.AtlasPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.attribute.Attribute
import org.bukkit.entity.*
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import kotlin.math.min

/**
 * MobCustomizer - Centralized mob behavior customization
 * Provides pre-configured mob templates for different roles:
 * - ELITE: Stronger than normal, glowing visual
 * - BOSS: Massive health pool, high damage
 * - RARE: Special spawns with unique drops
 * - GUARD: City defense mobs (future feature)
 * - DUNGEON: Scaled for dungeon tiers
 */
class MobCustomizer(private val plugin: AtlasPlugin) {
    
    private val mobTypeKey = NamespacedKey(plugin, "mob_type")
    private val mobTierKey = NamespacedKey(plugin, "mob_tier")
    private val bossNameKey = NamespacedKey(plugin, "boss_name")
    
    enum class MobType {
        NORMAL,
        ELITE,
        BOSS,
        RARE,
        GUARD,
        DUNGEON
    }
    
    /**
     * Apply a mob template to an entity
     */
    fun customize(mob: LivingEntity, type: MobType, tier: Int = 1): LivingEntity {
        when (type) {
            MobType.ELITE -> createElite(mob, tier)
            MobType.BOSS -> createBoss(mob, tier)
            MobType.RARE -> createRare(mob, tier)
            MobType.GUARD -> createGuard(mob, tier)
            MobType.DUNGEON -> createDungeon(mob, tier)
            MobType.NORMAL -> {} // No modifications
        }
        
        // Tag for identification
        tagMob(mob, "mob_type", type.name)
        tagMob(mob, "mob_tier", tier.toString())
        
        return mob
    }
    
    /**
     * Create an ELITE mob (2x health, glowing, speed boost)
     */
    private fun createElite(mob: LivingEntity, tier: Int) {
        // Health scaling: 2x base health * tier modifier
        val baseHealth = mob.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.baseValue ?: 20.0
        val newHealth = baseHealth * (2.0 + tier * 0.5)
        mob.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.baseValue = newHealth
        mob.health = newHealth
        
        // Damage boost
        mob.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE)?.let { attr ->
            attr.baseValue = (attr.baseValue ?: 1.0) * (1.0 + tier  * 0.2)
        }
        
        // Visual identity
        mob.customName(Component.text("⚡ Elite ${mob.type.name.replace("_", " ")} ⚡", NamedTextColor.YELLOW))
        mob.isCustomNameVisible = true
        
        // Effects
        mob.addPotionEffect(PotionEffect(PotionEffectType.GLOWING, 999999, 0, false, false))
        mob.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 999999, 0, false, false))
        
        // Persistence
        mob.removeWhenFarAway = false
        mob.isPersistent = true
        
        // Equipment based on tier
        equipMob(mob, tier)
    }
    
    /**
     * Create a BOSS mob (massive health, high damage, knockback resistance)
     */
    private fun createBoss(mob: LivingEntity, tier: Int = 1) {
        // Massive health pool
        val bossHealth = 200.0 + (tier * 100.0) // 300, 400, 500...
        mob.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.baseValue = bossHealth
        mob.health = bossHealth
        
        // High damage
        mob.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE)?.baseValue = 10.0 + (tier * 5.0)
        
        // Knockback resistance (can't be pushed around)
        mob.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE)?.baseValue = 1.0
        
        // Movement speed
        mob.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED)?.baseValue = 0.35
        
        // Armor
        mob.getAttribute(Attribute.GENERIC_ARMOR)?.baseValue = 10.0 + (tier * 2.0)
        
        // Visual identity
        mob.customName(Component.text("☠ BOSS: ${mob.type.name.replace("_", " ")} ☠", NamedTextColor.DARK_RED, TextDecoration.BOLD))
        mob.isCustomNameVisible = true
        
        // Effects
        mob.addPotionEffect(PotionEffect(PotionEffectType.GLOWING, 999999, 0, false, false))
        mob.addPotionEffect(PotionEffect(PotionEffectType.FIRE_RESISTANCE, 999999, 0, false, false))
        
        // Persistence
        mob.removeWhenFarAway = false
        mob.isPersistent = true
        
        // Full diamond gear
        equipMob(mob, 5)
    }
    
    /**
     * Create a RARE spawn mob (unique visual, better drops)
     */
    private fun createRare(mob: LivingEntity, tier: Int) {
        // Moderate health boost
        val baseHealth = mob.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.baseValue ?: 20.0
        val newHealth = baseHealth * (1.5 + tier * 0.3)
        mob.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.baseValue = newHealth
        mob.health = newHealth
        
        // Visual identity
        mob.customName(Component.text("✦ Rare ${mob.type.name.replace("_", " ")} ✦", NamedTextColor.LIGHT_PURPLE))
        mob.isCustomNameVisible = true
        
        // Glowing + particle effects
        mob.addPotionEffect(PotionEffect(PotionEffectType.GLOWING, 999999, 0, false, false))
        
        // Persistence
        mob.removeWhenFarAway = false
        mob.isPersistent = true
        
        // Equipment
        equipMob(mob, tier)
    }
    
    /**
     * Create a GUARD mob (city defense, future feature)
     */
    private fun createGuard(mob: LivingEntity, tier: Int) {
        // Moderate stats
        val baseHealth = mob.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.baseValue ?: 20.0
        mob.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.baseValue = baseHealth * 1.5
        mob.health = baseHealth * 1.5
        
        // Visual identity
        mob.customName(Component.text("⚔ City Guard ⚔", NamedTextColor.BLUE))
        mob.isCustomNameVisible = true
        
        // Equipment
        equipMob(mob, 3)
        
        // Persistence
        mob.removeWhenFarAway = false
        mob.isPersistent = true
    }
    
    /**
     * Create a DUNGEON mob (scaled for dungeon tier)
     */
    private fun createDungeon(mob: LivingEntity, tier: Int) {
        // Health scaling: Base * (1.0 + Tier * 0.4)
        val baseHealth = mob.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.baseValue ?: 20.0
        val newHealth = baseHealth * (1.0 + tier * 0.4)
        mob.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.baseValue = newHealth
        mob.health = newHealth
        
        // Damage scaling: Base * (1.0 + Tier * 0.2)
        mob.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE)?.let { attr ->
            attr.baseValue = (attr.baseValue ?: 1.0) * (1.0 + tier * 0.2)
        }
        
        // Equipment based on tier
        equipMob(mob, tier)
        
        // Persistence (won't despawn in dungeon)
        mob.removeWhenFarAway = false
        mob.isPersistent = true
    }
    
    /**
     * Equip a mob with armor and weapons based on tier
     */
    private fun equipMob(mob: LivingEntity, tier: Int) {
        val equipment = mob.equipment ?: return
        
        when (tier) {
            1 -> {
                // Leather armor
                equipment.helmet = ItemStack(Material.LEATHER_HELMET)
                equipment.chestplate = ItemStack(Material.LEATHER_CHESTPLATE)
                equipment.setItemInMainHand(ItemStack(Material.WOODEN_SWORD))
            }
            2 -> {
                // Chainmail/Gold mix
                equipment.helmet = ItemStack(Material.CHAINMAIL_HELMET)
                equipment.chestplate = ItemStack(Material.CHAINMAIL_CHESTPLATE)
                equipment.setItemInMainHand(ItemStack(Material.STONE_SWORD))
            }
            3 -> {
                // Iron armor
                equipment.helmet = ItemStack(Material.IRON_HELMET)
                equipment.chestplate = ItemStack(Material.IRON_CHESTPLATE)
                equipment.leggings = ItemStack(Material.IRON_LEGGINGS)
                equipment.boots = ItemStack(Material.IRON_BOOTS)
                equipment.setItemInMainHand(ItemStack(Material.IRON_SWORD))
            }
            4 -> {
                // Diamond armor (partial)
                equipment.helmet = ItemStack(Material.DIAMOND_HELMET)
                equipment.chestplate = ItemStack(Material.DIAMOND_CHESTPLATE)
                equipment.leggings = ItemStack(Material.IRON_LEGGINGS)
                equipment.boots = ItemStack(Material.IRON_BOOTS)
                equipment.setItemInMainHand(ItemStack(Material.DIAMOND_SWORD))
            }
            5 -> {
                // Full diamond armor
                equipment.helmet = ItemStack(Material.DIAMOND_HELMET)
                equipment.chestplate = ItemStack(Material.DIAMOND_CHESTPLATE)
                equipment.leggings = ItemStack(Material.DIAMOND_LEGGINGS)
                equipment.boots = ItemStack(Material.DIAMOND_BOOTS)
                equipment.setItemInMainHand(ItemStack(Material.DIAMOND_SWORD))
            }
        }
        
        // Set drop chances (very low for tier-based gear)
        equipment.helmetDropChance = 0.05f
        equipment.chestplateDropChance = 0.05f
        equipment.leggingsDropChance = 0.05f
        equipment.bootsDropChance = 0.05f
        equipment.itemInMainHandDropChance = 0.1f
    }
    
    /**
     * Tag a mob with persistent data
     */
    private fun tagMob(mob: LivingEntity, key: String, value: String) {
        val namespacedKey = NamespacedKey(plugin, key)
        mob.persistentDataContainer.set(namespacedKey, PersistentDataType.STRING, value)
    }
    
    /**
     * Get a mob's tag value
     */
    fun getMobTag(mob: LivingEntity, key: String): String? {
        val namespacedKey = NamespacedKey(plugin, key)
        return mob.persistentDataContainer.get(namespacedKey, PersistentDataType.STRING)
    }
    
    /**
     * Check if a mob is a specific type
     */
    fun isMobType(mob: LivingEntity, type: MobType): Boolean {
        return getMobTag(mob, "mob_type") == type.name
    }
    
    /**
     * Get a mob's tier (default 1 if not set)
     */
    fun getMobTier(mob: LivingEntity): Int {
        return getMobTag(mob, "mob_tier")?.toIntOrNull() ?: 1
    }
    
    /**
     * Create a custom boss with AI behavior
     * Example: A boss that summons minions at low health
     */
    fun createCustomBoss(location: org.bukkit.Location, bossName: String, entityType: EntityType = EntityType.ZOMBIE): LivingEntity? {
        val mob = location.world.spawnEntity(location, entityType) as? LivingEntity ?: return null
        
        // Apply boss template
        createBoss(mob, 3)
        
        // Custom name
        mob.customName(Component.text("☠ $bossName ☠", NamedTextColor.DARK_RED, TextDecoration.BOLD))
        tagMob(mob, "boss_name", bossName)
        
        // Start AI behavior
        startBossAI(mob, bossName)
        
        return mob
    }
    
    /**
     * Boss AI - Example behavior for phase-based combat
     */
    private fun startBossAI(boss: LivingEntity, name: String) {
        object : BukkitRunnable() {
            private var phase = 1
            private var tickCounter = 0
            
            override fun run() {
                if (!boss.isValid || boss.isDead) {
                    cancel()
                    return
                }
                
                tickCounter++
                
                // Phase transitions based on health
                val healthPercent = boss.health / (boss.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: 1.0)
                
                when {
                    healthPercent < 0.25 -> phase = 3
                    healthPercent < 0.5 -> phase = 2
                    else -> phase = 1
                }
                
                // Phase-specific behavior
                when (phase) {
                    1 -> normalPhase(boss)
                    2 -> enragedPhase(boss)
                    3 -> desperatePhase(boss)
                }
            }
            
            private fun normalPhase(boss: LivingEntity) {
                // Normal attack patterns
                // Every 10 seconds, apply strength buff
                if (tickCounter % 200 == 0) {
                    boss.addPotionEffect(PotionEffect(PotionEffectType.STRENGTH, 100, 0))
                }
            }
            
            private fun enragedPhase(boss: LivingEntity) {
                // Faster movement when below 50% health
                boss.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED)?.baseValue = 0.4
                boss.addPotionEffect(PotionEffect(PotionEffectType.STRENGTH, 999999, 1, false, false))
                
                // Summon minions every 20 seconds
                if (tickCounter % 400 == 0) {
                    summonMinions(boss, 2)
                }
            }
            
            private fun desperatePhase(boss: LivingEntity) {
                // Extremely dangerous when below 25%
                boss.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED)?.baseValue = 0.5
                boss.addPotionEffect(PotionEffect(PotionEffectType.STRENGTH, 999999, 2, false, false))
                boss.addPotionEffect(PotionEffect(PotionEffectType.REGENERATION, 999999, 1, false, false))
                
                // Frequent minion summons
                if (tickCounter % 200 == 0) {
                    summonMinions(boss, 3)
                }
            }
            
            private fun summonMinions(boss: LivingEntity, count: Int) {
                repeat(count) {
                    val minion = boss.world.spawnEntity(boss.location.add(
                        (Math.random() - 0.5) * 3,
                        0.0,
                        (Math.random() - 0.5) * 3
                    ), EntityType.ZOMBIE) as? LivingEntity
                    
                    minion?.let {
                        it.customName(Component.text("Summoned Minion", NamedTextColor.GRAY))
                        it.isCustomNameVisible = true
                        
                        // Set same target as boss
                        if (boss is Mob && boss.target != null) {
                            (it as? Mob)?.target = boss.target
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L) // Run every second
    }
    
    /**
     * Apply special mob-specific behaviors
     */
    fun applySpecialBehavior(mob: LivingEntity) {
        when (mob) {
            is Creeper -> {
                // Charged creeper with increased explosion radius
                mob.isPowered = true
                mob.explosionRadius = 5
                mob.maxFuseTicks = 20
            }
            is Skeleton -> {
                // Give skeletons better weapons at higher tiers
                if (getMobTier(mob) >= 3) {
                    mob.equipment?.setItemInMainHand(ItemStack(Material.BOW))
                    mob.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 999999, 1, false, false))
                }
            }
            is Zombie -> {
                // Baby zombies are faster and more dangerous
                if (Math.random() < 0.1 && getMobTier(mob) >= 2) {
                    mob.isBaby = true
                    mob.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED)?.baseValue = 0.5
                }
                
                // At higher tiers, zombies get extra speed
                if (getMobTier(mob) >= 3) {
                    mob.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 999999, 1, false, false))
                }
            }
            is Slime -> {
                // Larger slimes at higher tiers
                mob.size = min(10, getMobTier(mob) * 2)
            }
        }
    }
}
