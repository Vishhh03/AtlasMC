package com.projectatlas.survival

import com.projectatlas.AtlasPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityRegainHealthEvent
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.bukkit.event.player.PlayerBedLeaveEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.UUID

/**
 * Hardcore Survival Manager
 * - Nerfs food healing
 * - Implements rest/sleep healing system
 * - Adds custom healing items
 */
class SurvivalManager(private val plugin: AtlasPlugin) : Listener {

    private val healingItemKey = NamespacedKey(plugin, "healing_item")
    private val healingCooldowns = mutableMapOf<UUID, Long>()
    
    init {
        // Ocean & Hypothermia Task
        plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            plugin.server.onlinePlayers.forEach { handleOceanMechanics(it) }
        }, 40L, 40L)
    }

    private fun handleOceanMechanics(player: Player) {
        if (player.gameMode == org.bukkit.GameMode.CREATIVE || player.gameMode == org.bukkit.GameMode.SPECTATOR) return
        
        val loc = player.location
        val biome = loc.block.biome
        
        // 1. Hypothermia (Cold Water)
        val isCold = biome == org.bukkit.block.Biome.FROZEN_OCEAN || 
                     biome == org.bukkit.block.Biome.DEEP_FROZEN_OCEAN ||
                     biome == org.bukkit.block.Biome.COLD_OCEAN ||
                     biome == org.bukkit.block.Biome.DEEP_COLD_OCEAN ||
                     biome == org.bukkit.block.Biome.SNOWY_BEACH ||
                     biome == org.bukkit.block.Biome.ICE_SPIKES

        if (isCold && player.isInWater) {
             val hasSkill = plugin.skillTreeManager.hasColdResistance(player)
             // Simple armor check: If wearing Leather Chestplate
             val chest = player.inventory.chestplate
             val hasLeather = chest?.type == Material.LEATHER_CHESTPLATE
             
             if (!hasSkill && !hasLeather) {
                 val currentFreeze = player.freezeTicks
                 val maxFreeze = player.maxFreezeTicks
                 player.freezeTicks = (currentFreeze + 50).coerceAtMost(maxFreeze) // Increase freezing fast
                 
                 if (player.freezeTicks >= maxFreeze) {
                     player.damage(1.0)
                     player.sendActionBar(Component.text("❄ You are freezing! Get out! ❄", NamedTextColor.AQUA))
                 }
             } else {
                 // Recover warmth if protected
                 if (player.freezeTicks > 0) player.freezeTicks = (player.freezeTicks - 30).coerceAtLeast(0)
             }
        }
        
        // 2. Skills
        if (player.isInWater) {
             // Swim Speed
             val swimLevel = plugin.skillTreeManager.getSwimSpeedLevel(player)
             if (swimLevel > 0) {
                 player.addPotionEffect(PotionEffect(PotionEffectType.DOLPHINS_GRACE, 60, swimLevel - 1, false, false))
             }
             
             // Water Breathing (Iron Lungs)
             if (plugin.skillTreeManager.hasWaterBreathing(player)) {
                 player.addPotionEffect(PotionEffect(PotionEffectType.CONDUIT_POWER, 60, 0, false, false))
                 player.remainingAir = player.maximumAir
             }
        }
    }
    
    // Bed quality ratings for healing
    enum class BedQuality(val healAmount: Double, val displayName: String) {
        BASIC(4.0, "Rough Rest"),      // Wool beds
        COMFORT(6.0, "Restful Sleep"), // Dyed beds
        LUXURY(10.0, "Rejuvenating Slumber") // Special beds (future)
    }
    
    // ═══════════════════════════════════════════════════════════════
    // TIERED HEALING ITEMS - Balanced for no food healing
    // ═══════════════════════════════════════════════════════════════
    
    enum class HealingItem(
        val tier: Int,
        val healAmount: Double,
        val cooldownMs: Long,
        val displayName: String,
        val material: Material,
        val color: NamedTextColor,
        val clearsDebuffs: Boolean = false,
        val bonusEffect: PotionEffectType? = null
    ) {
        // Tier 1 - Basic (Early game, easy to craft)
        BANDAGE(1, 3.0, 5000, "Bandage", Material.PAPER, NamedTextColor.WHITE),
        HERBAL_POULTICE(1, 4.0, 6000, "Herbal Poultice", Material.FERN, NamedTextColor.GREEN),
        
        // Tier 2 - Common (Mid-early game)
        HEALING_SALVE(2, 6.0, 8000, "Healing Salve", Material.HONEYCOMB, NamedTextColor.YELLOW),
        HERBAL_REMEDY(2, 5.0, 7000, "Herbal Remedy", Material.SWEET_BERRIES, NamedTextColor.GREEN, clearsDebuffs = true),
        
        // Tier 3 - Quality (Mid game, requires effort)
        MEDICAL_KIT(3, 10.0, 12000, "Medical Kit", Material.CHEST, NamedTextColor.AQUA, clearsDebuffs = true),
        REGENERATION_DRAUGHT(3, 8.0, 10000, "Regeneration Draught", Material.POTION, NamedTextColor.LIGHT_PURPLE, bonusEffect = PotionEffectType.REGENERATION),
        
        // Tier 4 - Rare (Late game, expensive materials)
        SURGEON_KIT(4, 16.0, 15000, "Surgeon's Kit", Material.SHULKER_BOX, NamedTextColor.GOLD, clearsDebuffs = true),
        PHOENIX_ELIXIR(4, 12.0, 20000, "Phoenix Elixir", Material.DRAGON_BREATH, NamedTextColor.RED, clearsDebuffs = true, bonusEffect = PotionEffectType.FIRE_RESISTANCE),
        
        // Tier 5 - Legendary (End game, very rare)
        DIVINE_RESTORATION(5, 20.0, 30000, "Divine Restoration", Material.NETHER_STAR, NamedTextColor.LIGHT_PURPLE, clearsDebuffs = true, bonusEffect = PotionEffectType.ABSORPTION)
    }

    init {
        registerRecipes()
    }

    private fun registerRecipes() {
        // ═══════════════════════════════════════════════════════════
        // TIER 1 - Early Game (Basic materials)
        // ═══════════════════════════════════════════════════════════
        
        // Bandage: 3 Paper + 1 String
        registerShapelessRecipe("bandage", HealingItem.BANDAGE,
            Material.PAPER to 2, Material.STRING to 1
        )
        
        // Herbal Poultice: 2 Fern + 1 Dirt (mud poultice)  
        registerShapelessRecipe("herbal_poultice", HealingItem.HERBAL_POULTICE,
            Material.FERN to 2, Material.CLAY_BALL to 1
        )
        
        // ═══════════════════════════════════════════════════════════
        // TIER 2 - Mid-Early Game (Some processing)
        // ═══════════════════════════════════════════════════════════
        
        // Healing Salve: Honeycomb + Bowl + Sugar
        registerShapelessRecipe("healing_salve", HealingItem.HEALING_SALVE,
            Material.HONEYCOMB to 1, Material.BOWL to 1, Material.SUGAR to 1
        )
        
        // Herbal Remedy: Sweet Berries + Red Mushroom + Dandelion
        registerShapelessRecipe("herbal_remedy", HealingItem.HERBAL_REMEDY,
            Material.SWEET_BERRIES to 2, Material.RED_MUSHROOM to 1, Material.DANDELION to 1
        )
        
        // ═══════════════════════════════════════════════════════════
        // TIER 3 - Mid Game (Complex crafting)
        // ═══════════════════════════════════════════════════════════
        
        // Medical Kit: Shaped recipe
        val medkitKey = NamespacedKey(plugin, "recipe_medical_kit")
        if (plugin.server.getRecipe(medkitKey) == null) {
            val recipe = org.bukkit.inventory.ShapedRecipe(medkitKey, createHealingItem(HealingItem.MEDICAL_KIT))
            recipe.shape("PSP", "BHB", "III")
            recipe.setIngredient('P', Material.PAPER)
            recipe.setIngredient('S', Material.STRING)
            recipe.setIngredient('B', Material.GLASS_BOTTLE)
            recipe.setIngredient('H', Material.HONEY_BOTTLE)
            recipe.setIngredient('I', Material.IRON_INGOT)
            plugin.server.addRecipe(recipe)
        }
        
        // Regeneration Draught: Ghast Tear + Glistering Melon + Glass Bottle
        registerShapelessRecipe("regen_draught", HealingItem.REGENERATION_DRAUGHT,
            Material.GHAST_TEAR to 1, Material.GLISTERING_MELON_SLICE to 1, Material.GLASS_BOTTLE to 1
        )
        
        // ═══════════════════════════════════════════════════════════
        // TIER 4 - Late Game (Expensive materials)
        // ═══════════════════════════════════════════════════════════
        
        // Surgeon's Kit: Shaped recipe with gold and diamonds
        val surgeonKey = NamespacedKey(plugin, "recipe_surgeon_kit")
        if (plugin.server.getRecipe(surgeonKey) == null) {
            val recipe = org.bukkit.inventory.ShapedRecipe(surgeonKey, createHealingItem(HealingItem.SURGEON_KIT))
            recipe.shape("GDG", "SCS", "GEG")
            recipe.setIngredient('G', Material.GOLD_INGOT)
            recipe.setIngredient('D', Material.DIAMOND)
            recipe.setIngredient('S', Material.SHEARS)
            recipe.setIngredient('C', Material.CHEST)
            recipe.setIngredient('E', Material.EMERALD)
            plugin.server.addRecipe(recipe)
        }
        
        // Phoenix Elixir: Dragon's Breath + Blaze Powder + Golden Apple
        registerShapelessRecipe("phoenix_elixir", HealingItem.PHOENIX_ELIXIR,
            Material.DRAGON_BREATH to 1, Material.BLAZE_POWDER to 2, Material.GOLDEN_APPLE to 1
        )
        
        // ═══════════════════════════════════════════════════════════
        // TIER 5 - Legendary (Very rare materials)
        // ═══════════════════════════════════════════════════════════
        
        // Divine Restoration: Nether Star + Enchanted Golden Apple + Totem Fragment
        val divineKey = NamespacedKey(plugin, "recipe_divine_restoration")
        if (plugin.server.getRecipe(divineKey) == null) {
            val recipe = org.bukkit.inventory.ShapedRecipe(divineKey, createHealingItem(HealingItem.DIVINE_RESTORATION))
            recipe.shape("EGE", "GNG", "EGE")
            recipe.setIngredient('E', Material.EMERALD_BLOCK)
            recipe.setIngredient('G', Material.GOLD_BLOCK)
            recipe.setIngredient('N', Material.NETHER_STAR)
            plugin.server.addRecipe(recipe)
        }
    }
    
    private fun registerShapelessRecipe(name: String, item: HealingItem, vararg ingredients: Pair<Material, Int>) {
        val key = NamespacedKey(plugin, "recipe_$name")
        if (plugin.server.getRecipe(key) != null) return
        
        val recipe = org.bukkit.inventory.ShapelessRecipe(key, createHealingItem(item))
        ingredients.forEach { (material, count) ->
            recipe.addIngredient(count, material)
        }
        plugin.server.addRecipe(recipe)
    }
    
    // ══════════════════════════════════════════════════════════════
    // FOOD QUALITY TIERS - Better food = buffs, not just more hunger
    // ══════════════════════════════════════════════════════════════
    
    enum class FoodQuality(val displayName: String, val color: NamedTextColor) {
        POOR("Poor", NamedTextColor.GRAY),           // Raw/basic foods
        COMMON("Common", NamedTextColor.WHITE),      // Simple cooked foods
        QUALITY("Quality", NamedTextColor.GREEN),    // Stews, bread, pies
        GOURMET("Gourmet", NamedTextColor.GOLD)      // Golden foods, complex recipes
    }
    
    private val foodQualityMap = mapOf<Material, FoodQuality>(
        // POOR - Raw and basic foods (no buffs)
        Material.APPLE to FoodQuality.POOR,
        Material.MELON_SLICE to FoodQuality.POOR,
        Material.SWEET_BERRIES to FoodQuality.POOR,
        Material.GLOW_BERRIES to FoodQuality.POOR,
        Material.DRIED_KELP to FoodQuality.POOR,
        Material.ROTTEN_FLESH to FoodQuality.POOR,
        Material.SPIDER_EYE to FoodQuality.POOR,
        Material.POISONOUS_POTATO to FoodQuality.POOR,
        Material.BEEF to FoodQuality.POOR,
        Material.PORKCHOP to FoodQuality.POOR,
        Material.CHICKEN to FoodQuality.POOR,
        Material.MUTTON to FoodQuality.POOR,
        Material.RABBIT to FoodQuality.POOR,
        Material.COD to FoodQuality.POOR,
        Material.SALMON to FoodQuality.POOR,
        Material.TROPICAL_FISH to FoodQuality.POOR,
        Material.PUFFERFISH to FoodQuality.POOR,
        
        // COMMON - Simple cooked foods (+Speed I, 30s)
        Material.COOKED_BEEF to FoodQuality.COMMON,
        Material.COOKED_PORKCHOP to FoodQuality.COMMON,
        Material.COOKED_CHICKEN to FoodQuality.COMMON,
        Material.COOKED_MUTTON to FoodQuality.COMMON,
        Material.COOKED_RABBIT to FoodQuality.COMMON,
        Material.COOKED_COD to FoodQuality.COMMON,
        Material.COOKED_SALMON to FoodQuality.COMMON,
        Material.BAKED_POTATO to FoodQuality.COMMON,
        Material.COOKIE to FoodQuality.COMMON,
        Material.CARROT to FoodQuality.COMMON,
        Material.POTATO to FoodQuality.COMMON,
        
        // QUALITY - Complex foods (+Strength I, +Speed I, 45s)
        Material.BREAD to FoodQuality.QUALITY,
        Material.PUMPKIN_PIE to FoodQuality.QUALITY,
        Material.BEETROOT_SOUP to FoodQuality.QUALITY,
        Material.MUSHROOM_STEW to FoodQuality.QUALITY,
        Material.RABBIT_STEW to FoodQuality.QUALITY,
        Material.SUSPICIOUS_STEW to FoodQuality.QUALITY,
        Material.HONEY_BOTTLE to FoodQuality.QUALITY,
        Material.CAKE to FoodQuality.QUALITY,
        
        // GOURMET - Premium foods (+Strength I, +Speed I, +Resistance I, 60s)
        Material.GOLDEN_APPLE to FoodQuality.GOURMET,
        Material.ENCHANTED_GOLDEN_APPLE to FoodQuality.GOURMET,
        Material.GOLDEN_CARROT to FoodQuality.GOURMET
    )
    
    // ══════════════════════════════════════════════════════════════
    // DISABLE SATURATION HEALING - Food only fills hunger
    // ══════════════════════════════════════════════════════════════
    
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onHealthRegen(event: EntityRegainHealthEvent) {
        if (event.entity !is Player) return
        
        // COMPLETELY disable saturation-based healing
        if (event.regainReason == EntityRegainHealthEvent.RegainReason.SATIATED) {
            event.isCancelled = true
            return
        }
        
        // Disable eating-based healing (shouldn't happen in vanilla, but just in case)
        if (event.regainReason == EntityRegainHealthEvent.RegainReason.EATING) {
            event.isCancelled = true
            return
        }
        
        // Allow other healing sources (potions, healing items, etc.)
    }
    
    // ══════════════════════════════════════════════════════════════
    // FOOD CONSUMPTION - Apply buffs based on quality
    // ══════════════════════════════════════════════════════════════
    
    @EventHandler
    fun onFoodConsume(event: PlayerItemConsumeEvent) {
        val player = event.player
        val item = event.item
        
        val quality = foodQualityMap[item.type] ?: FoodQuality.POOR
        
        // Apply buffs based on food quality
        when (quality) {
            FoodQuality.POOR -> {
                // No buffs for poor quality food
            }
            FoodQuality.COMMON -> {
                player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 600, 0, false, false, true)) // 30s Speed I
                player.sendMessage(Component.text("✦ Well Fed (+Speed)", NamedTextColor.GREEN))
            }
            FoodQuality.QUALITY -> {
                player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 900, 0, false, false, true)) // 45s
                player.addPotionEffect(PotionEffect(PotionEffectType.STRENGTH, 900, 0, false, false, true))
                player.sendMessage(Component.text("✦ Well Fed (+Speed, +Strength)", NamedTextColor.GREEN))
            }
            FoodQuality.GOURMET -> {
                player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 1200, 0, false, false, true)) // 60s
                player.addPotionEffect(PotionEffect(PotionEffectType.STRENGTH, 1200, 0, false, false, true))
                player.addPotionEffect(PotionEffect(PotionEffectType.RESISTANCE, 1200, 0, false, false, true))
                player.sendMessage(Component.text("✦ Gourmet Meal (+Speed, +Strength, +Resistance)", NamedTextColor.GOLD))
            }
        }
    }
    
    // ══════════════════════════════════════════════════════════════
    // STARVATION PENALTIES - Low hunger = debuffs
    // ══════════════════════════════════════════════════════════════
    
    @EventHandler
    fun onFoodChange(event: FoodLevelChangeEvent) {
        val player = event.entity as? Player ?: return
        val newLevel = event.foodLevel
        
        // Apply penalties for low hunger
        if (newLevel <= 6) {
            // Very hungry - Slowness + Weakness
            player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 100, 0, false, false, true))
            player.addPotionEffect(PotionEffect(PotionEffectType.WEAKNESS, 100, 0, false, false, true))
            
            if (newLevel <= 3) {
                player.sendMessage(Component.text("⚠ You are starving! Find food quickly!", NamedTextColor.RED))
            }
        }
    }
    
    // ══════════════════════════════════════════════════════════════
    // COMBAT HUNGER DRAIN - Fighting costs hunger
    // ══════════════════════════════════════════════════════════════
    
    @EventHandler
    fun onPlayerAttack(event: EntityDamageByEntityEvent) {
        val attacker = event.damager as? Player ?: return
        
        // Attacking drains a small amount of hunger (0.5 per hit)
        val currentFood = attacker.foodLevel
        val currentSat = attacker.saturation
        
        if (currentSat > 0) {
            attacker.saturation = (currentSat - 0.5f).coerceAtLeast(0f)
        } else if (currentFood > 0) {
            // Small chance to lose a hunger point on attack when saturation is empty
            if (Math.random() < 0.1) {
                attacker.foodLevel = (currentFood - 1).coerceAtLeast(0)
            }
        }
    }
    
    // ══════════════════════════════════════════════════════════════
    // REST/SLEEP HEALING SYSTEM - Time-based resting
    // ══════════════════════════════════════════════════════════════
    
    private val bedEnterTimes = mutableMapOf<UUID, Long>()
    private val bedTypes = mutableMapOf<UUID, Material>()
    
    // Heal rate: HP per second of resting
    private val REST_HEAL_RATE = 0.5 // 0.5 HP per second = 10 HP per 20 seconds
    private val MIN_REST_TIME_MS = 3000L // Minimum 3 seconds to get any benefit
    
    @EventHandler
    fun onBedEnter(event: org.bukkit.event.player.PlayerBedEnterEvent) {
        if (event.bedEnterResult != org.bukkit.event.player.PlayerBedEnterEvent.BedEnterResult.OK) return
        
        val player = event.player
        bedEnterTimes[player.uniqueId] = System.currentTimeMillis()
        bedTypes[player.uniqueId] = event.bed.type
        
        player.sendMessage(Component.text("💤 Resting... (healing over time)", NamedTextColor.GRAY, TextDecoration.ITALIC))
    }
    
    @EventHandler
    fun onBedLeave(event: PlayerBedLeaveEvent) {
        val player = event.player
        val bed = event.bed
        
        val enterTime = bedEnterTimes.remove(player.uniqueId) ?: return
        bedTypes.remove(player.uniqueId)
        
        val timeSpentMs = System.currentTimeMillis() - enterTime
        val timeSpentSeconds = timeSpentMs / 1000.0
        
        // Determine bed quality
        val quality = when (bed.type) {
            Material.WHITE_BED -> BedQuality.BASIC
            Material.RED_BED, Material.BLUE_BED, Material.GREEN_BED,
            Material.YELLOW_BED, Material.ORANGE_BED, Material.PURPLE_BED,
            Material.PINK_BED, Material.CYAN_BED, Material.LIME_BED,
            Material.LIGHT_BLUE_BED, Material.MAGENTA_BED, Material.BROWN_BED,
            Material.GRAY_BED, Material.LIGHT_GRAY_BED -> BedQuality.COMFORT
            Material.BLACK_BED -> BedQuality.LUXURY
            else -> BedQuality.BASIC
        }
        
        // Quality multiplier for rest heal rate
        val qualityMultiplier = when (quality) {
            BedQuality.BASIC -> 1.0
            BedQuality.COMFORT -> 1.5
            BedQuality.LUXURY -> 2.0
        }
        
        // Skill bonus
        val skillMultiplier = plugin.skillTreeManager.getRestMultiplier(player)
        
        val currentHealth = player.health
        val maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
        
        // Check if night was skipped (full sleep bonus)
        val world = player.world
        val didNightSkip = world.time < 1000
        
        if (didNightSkip) {
            // FULL SLEEP - bonus healing + buffs
            val fullSleepHeal = quality.healAmount * skillMultiplier
            val newHealth = (currentHealth + fullSleepHeal).coerceAtMost(maxHealth)
            player.health = newHealth
            
            // Clear negative effects
            player.removePotionEffect(PotionEffectType.POISON)
            player.removePotionEffect(PotionEffectType.HUNGER)
            player.removePotionEffect(PotionEffectType.WEAKNESS)
            player.removePotionEffect(PotionEffectType.SLOWNESS)
            
            // Apply well-rested buff
            player.addPotionEffect(PotionEffect(PotionEffectType.REGENERATION, 200, 0, false, false))
            
            player.sendMessage(Component.text("☽ ${quality.displayName} ☽", NamedTextColor.AQUA, TextDecoration.ITALIC))
            player.sendMessage(Component.text("  Restored ${fullSleepHeal.toInt()} health (full night's rest)", NamedTextColor.GREEN))
            player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f)
            
        } else if (timeSpentMs >= MIN_REST_TIME_MS) {
            // PARTIAL REST - heal based on time spent
            val restHeal = (timeSpentSeconds * REST_HEAL_RATE * qualityMultiplier * skillMultiplier).coerceAtMost(quality.healAmount * skillMultiplier)
            
            if (restHeal >= 0.5) { // Only heal if at least 0.5 HP
                val newHealth = (currentHealth + restHeal).coerceAtMost(maxHealth)
                player.health = newHealth
                
                val timeDisplay = if (timeSpentSeconds >= 60) {
                    "${(timeSpentSeconds / 60).toInt()}m ${(timeSpentSeconds % 60).toInt()}s"
                } else {
                    "${timeSpentSeconds.toInt()}s"
                }
                
                player.sendMessage(Component.text("💤 Rested for $timeDisplay", NamedTextColor.GRAY))
                player.sendMessage(Component.text("  Restored ${String.format("%.1f", restHeal)} health", NamedTextColor.GREEN))
                player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1f)
            } else {
                player.sendMessage(Component.text("💤 Too brief to recover much...", NamedTextColor.GRAY))
            }
        } else {
            player.sendMessage(Component.text("💤 Rest longer to recover health", NamedTextColor.GRAY))
        }
    }
    
    // ══════════════════════════════════════════════════════════════
    // CUSTOM HEALING ITEMS - Tiered System
    // ══════════════════════════════════════════════════════════════
    
    fun createHealingItem(type: HealingItem, amount: Int = 1): ItemStack {
        val item = ItemStack(type.material, amount)
        val meta = item.itemMeta ?: return item
        
        // Tier-based name color
        val tierStars = "★".repeat(type.tier) + "☆".repeat(5 - type.tier)
        
        meta.displayName(Component.text(type.displayName, type.color, TextDecoration.BOLD))
        
        val lore = mutableListOf<Component>()
        lore.add(Component.text("Tier ${ type.tier } ", NamedTextColor.GRAY)
            .append(Component.text(tierStars, NamedTextColor.GOLD)))
        lore.add(Component.empty())
        lore.add(Component.text("Right-click to use", NamedTextColor.DARK_GRAY))
        lore.add(Component.empty())
        lore.add(Component.text("❤ Restores ${type.healAmount.toInt()} HP", NamedTextColor.GREEN))
        lore.add(Component.text("⏱ Cooldown: ${type.cooldownMs / 1000}s", NamedTextColor.GRAY))
        
        if (type.clearsDebuffs) {
            lore.add(Component.text("✦ Clears negative effects", NamedTextColor.AQUA))
        }
        if (type.bonusEffect != null) {
            val effectName = type.bonusEffect.key.key.replace("_", " ").replaceFirstChar { it.uppercase() }
            lore.add(Component.text("✦ Grants $effectName", NamedTextColor.LIGHT_PURPLE))
        }
        
        lore.add(Component.empty())
        lore.add(Component.text("Healing Item", NamedTextColor.DARK_PURPLE, TextDecoration.ITALIC))
        
        meta.lore(lore)
        meta.persistentDataContainer.set(healingItemKey, PersistentDataType.STRING, type.name)
        
        item.itemMeta = meta
        return item
    }
    
    @EventHandler
    fun onItemUse(event: PlayerInteractEvent) {
        val item = event.item ?: return
        val meta = item.itemMeta ?: return
        
        val healingType = meta.persistentDataContainer.get(healingItemKey, PersistentDataType.STRING) ?: return
        
        // It's a healing item!
        event.isCancelled = true
        
        val player = event.player
        val type = try { HealingItem.valueOf(healingType) } catch (e: Exception) { return }
        
        // Check cooldown
        val now = System.currentTimeMillis()
        val lastUse = healingCooldowns[player.uniqueId] ?: 0L
        val remainingCooldown = (lastUse + type.cooldownMs) - now
        
        if (remainingCooldown > 0) {
            val secondsLeft = (remainingCooldown / 1000.0).let { String.format("%.1f", it) }
            player.sendMessage(Component.text("⏱ Healing on cooldown! Wait ${secondsLeft}s", NamedTextColor.RED))
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 0.5f, 1f)
            return
        }
        
        val currentHealth = player.health
        val maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
        
        // Already at full health?
        if (currentHealth >= maxHealth) {
            player.sendMessage(Component.text("You're already at full health!", NamedTextColor.YELLOW))
            return
        }
        
        // Apply healing
        val newHealth = (currentHealth + type.healAmount).coerceAtMost(maxHealth)
        player.health = newHealth
        
        // Set cooldown
        healingCooldowns[player.uniqueId] = now
        
        // Consume item
        if (item.amount > 1) {
            item.amount = item.amount - 1
        } else {
            player.inventory.setItemInMainHand(ItemStack(Material.AIR))
        }
        
        // Effects message
        val healedAmount = (newHealth - currentHealth).toInt()
        player.sendMessage(Component.text("✚ Used ${type.displayName} (+$healedAmount HP)", type.color))
        player.playSound(player.location, Sound.ENTITY_GENERIC_DRINK, 1f, 1f)
        
        // Clear debuffs if applicable
        if (type.clearsDebuffs) {
            player.removePotionEffect(PotionEffectType.POISON)
            player.removePotionEffect(PotionEffectType.WITHER)
            player.removePotionEffect(PotionEffectType.SLOWNESS)
            player.removePotionEffect(PotionEffectType.WEAKNESS)
            player.removePotionEffect(PotionEffectType.MINING_FATIGUE)
            player.removePotionEffect(PotionEffectType.NAUSEA)
            player.removePotionEffect(PotionEffectType.BLINDNESS)
            player.removePotionEffect(PotionEffectType.HUNGER)
            player.sendMessage(Component.text("  ✦ Negative effects cleared!", NamedTextColor.AQUA))
        }
        
        // Apply bonus effect if applicable
        if (type.bonusEffect != null) {
            val duration = when (type.tier) {
                1, 2 -> 200 // 10 seconds
                3 -> 400 // 20 seconds
                4 -> 600 // 30 seconds
                5 -> 1200 // 60 seconds
                else -> 200
            }
            val amplifier = if (type.tier >= 4) 1 else 0
            player.addPotionEffect(PotionEffect(type.bonusEffect, duration, amplifier, false, true, true))
            val effectName = type.bonusEffect.key.key.replace("_", " ").replaceFirstChar { it.uppercase() }
            player.sendMessage(Component.text("  ✦ Gained $effectName!", NamedTextColor.LIGHT_PURPLE))
        }
        
        // Play tier-appropriate sound
        when (type.tier) {
            1, 2 -> player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f)
            3 -> player.playSound(player.location, Sound.BLOCK_BREWING_STAND_BREW, 0.7f, 1f)
            4 -> player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f)
            5 -> player.playSound(player.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.5f, 1f)
        }
    }
    
    // ══════════════════════════════════════════════════════════════
    // GIVE COMMANDS (for /atlas survival)
    // ══════════════════════════════════════════════════════════════
    
    fun giveHealingItem(player: Player, typeName: String, amount: Int = 1): Boolean {
        val type = try { 
            HealingItem.valueOf(typeName.uppercase()) 
        } catch (e: Exception) { 
            player.sendMessage(Component.text("Unknown healing item: $typeName", NamedTextColor.RED))
            player.sendMessage(Component.text("Available: ${HealingItem.entries.joinToString { it.name }}", NamedTextColor.GRAY))
            return false 
        }
        
        val item = createHealingItem(type, amount)
        player.inventory.addItem(item)
        player.sendMessage(Component.text("Received $amount x ${type.displayName}", NamedTextColor.GREEN))
        return true
    }
}
