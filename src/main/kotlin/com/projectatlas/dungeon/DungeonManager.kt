package com.projectatlas.dungeon

import com.projectatlas.AtlasPlugin
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.*
import org.bukkit.attribute.Attribute
import org.bukkit.block.BlockFace
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitTask
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import com.projectatlas.achievements.AchievementManager

/**
 * Dungeon Instance System - Private challenge realms with objectives and rewards!
 */
class DungeonManager(private val plugin: AtlasPlugin) : Listener {

    enum class DungeonType(
        val displayName: String,
        val description: String,
        val icon: Material,
        val difficulty: Int, // 1-5 stars
        val timeLimit: Int, // seconds
        val goldReward: Double,
        val xpReward: Long
    ) {
        SHADOW_CAVERN("Shadow Cavern", "Navigate the dark maze and slay the boss", Material.COAL_BLOCK, 2, 600, 1500.0, 200),
        INFERNAL_PIT("Infernal Pit", "Survive 5 waves of hellish creatures", Material.NETHERRACK, 3, 480, 2500.0, 350),
        FROZEN_DEPTHS("Frozen Depths", "Find and destroy 3 ice crystals", Material.BLUE_ICE, 2, 420, 2000.0, 250),
        VOID_ARENA("Void Arena", "Defeat the Ender Champion", Material.END_STONE, 4, 300, 4000.0, 500),
        ANCIENT_TEMPLE("Ancient Temple", "Solve puzzles and claim the artifact", Material.CHISELED_STONE_BRICKS, 3, 540, 3000.0, 400),
        PIRATE_COVE("Pirate's Cove", "Loot the treasure while fighting undead pirates", Material.CHEST, 2, 360, 2200.0, 280),
        BLOOD_MOON("Blood Moon Arena", "Survive as long as possible against endless hordes", Material.REDSTONE_BLOCK, 5, 900, 5000.0, 600),
        DRAGONS_LAIR("Dragon's Lair", "Slay the ancient dragon and claim its hoard", Material.DRAGON_EGG, 5, 600, 8000.0, 800)
    }

    enum class DungeonModifier(val displayName: String, val description: String, val rewardMultiplier: Double) {
        NONE("Normal", "Standard difficulty", 1.0),
        HARDCORE("Hardcore", "No respawns, 2x rewards", 2.0),
        SPEED_RUN("Speed Run", "Half time limit, 1.5x rewards", 1.5),
        NIGHTMARE("Nightmare", "3x mob spawns, 2.5x rewards", 2.5),
        ELITE("Elite", "Buffed enemies, 3x rewards", 3.0)
    }

    data class DungeonInstance(
        val id: UUID = UUID.randomUUID(),
        val type: DungeonType,
        val modifier: DungeonModifier = DungeonModifier.NONE,
        val players: MutableSet<UUID> = mutableSetOf(),
        val spawnedMobs: MutableSet<UUID> = mutableSetOf(),
        var currentWave: Int = 0,
        var objectiveProgress: Int = 0,
        var objectiveTarget: Int = 1,
        var startTime: Long = System.currentTimeMillis(),
        var completed: Boolean = false,
        var failed: Boolean = false,
        var bossBar: BossBar? = null,
        var taskId: BukkitTask? = null,
        val returnLocations: MutableMap<UUID, Location> = mutableMapOf(),
        var arenaCenter: Location? = null
    ) {
        // Scaling factor based on party size (1 player = 1.0, 4 players = ~1.8)
        fun getScalingFactor(): Double {
            val count = players.size.coerceAtLeast(1)
            return 1.0 + (count - 1) * 0.25 // Each extra player adds 25% difficulty
        }
        
        fun getMobMultiplier(): Int {
            val base = when (modifier) {
                DungeonModifier.NIGHTMARE -> 3
                DungeonModifier.ELITE -> 2
                else -> 1
            }
            return (base * getScalingFactor()).toInt().coerceAtLeast(1)
        }
        
        fun getTimeLimit(): Int {
            return when (modifier) {
                DungeonModifier.SPEED_RUN -> type.timeLimit / 2
                else -> type.timeLimit
            }
        }
        
        fun getRewardMultiplier(): Double {
            return modifier.rewardMultiplier * (1.0 + (players.size - 1) * 0.1) // Small bonus per extra player
        }
    }

    private val activeInstances = ConcurrentHashMap<UUID, DungeonInstance>() // Player UUID -> Instance
    private val instancesByUUID = ConcurrentHashMap<UUID, DungeonInstance>() // Instance UUID -> Instance

    fun enterDungeon(player: Player, type: DungeonType, modifier: DungeonModifier = DungeonModifier.NONE): Boolean {
        if (activeInstances.containsKey(player.uniqueId)) {
            player.sendMessage(Component.text("You're already in a dungeon!", NamedTextColor.RED))
            return false
        }

        // Get party members (or just the player if solo)
        val partyMembers = plugin.partyManager.getOnlinePartyMembers(player)
        
        // Check if any party member is already in a dungeon
        for (member in partyMembers) {
            if (activeInstances.containsKey(member.uniqueId)) {
                player.sendMessage(Component.text("${member.name} is already in a dungeon!", NamedTextColor.RED))
                return false
            }
        }

        // Create instance with modifier
        val instance = DungeonInstance(type = type, modifier = modifier)
        
        // Add all party members
        for (member in partyMembers) {
            instance.players.add(member.uniqueId)
            instance.returnLocations[member.uniqueId] = member.location.clone()
            activeInstances[member.uniqueId] = instance
        }
        
        instancesByUUID[instance.id] = instance

        // Teleport to dungeon area
        val dungeonWorld = getDungeonWorld()
        val spawnLoc = findSafeDungeonSpawn(dungeonWorld, instance)
        instance.arenaCenter = spawnLoc.clone()

        // Create boss bar
        val bar = BossBar.bossBar(
            Component.text("⚔ ${type.displayName} ⚔", NamedTextColor.RED),
            1.0f,
            BossBar.Color.PURPLE,
            BossBar.Overlay.NOTCHED_10
        )
        instance.bossBar = bar

        // Teleport all players and show bar
        for (member in partyMembers) {
            member.teleport(spawnLoc.clone().add((partyMembers.indexOf(member) * 2).toDouble(), 0.0, 0.0))
            member.showBossBar(bar)
            
            // Announce
            member.sendMessage(Component.empty())
            member.sendMessage(Component.text("═══════════════════════════════", NamedTextColor.DARK_PURPLE))
            member.sendMessage(Component.text("  ⚔ DUNGEON: ${type.displayName.uppercase()} ⚔", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
            member.sendMessage(Component.text("  ${type.description}", NamedTextColor.GRAY))
            member.sendMessage(Component.text("  Difficulty: ${"★".repeat(type.difficulty)}${"☆".repeat(5 - type.difficulty)}", NamedTextColor.GOLD))
            if (modifier != DungeonModifier.NONE) {
                member.sendMessage(Component.text("  Modifier: ${modifier.displayName} (${modifier.description})", NamedTextColor.AQUA))
            }
            member.sendMessage(Component.text("  Party Size: ${instance.players.size} | Scaling: ${String.format("%.0f", instance.getScalingFactor() * 100)}%", NamedTextColor.YELLOW))
            member.sendMessage(Component.text("  Time Limit: ${instance.getTimeLimit() / 60}:${String.format("%02d", instance.getTimeLimit() % 60)}", NamedTextColor.YELLOW))
            member.sendMessage(Component.text("═══════════════════════════════", NamedTextColor.DARK_PURPLE))
            member.sendMessage(Component.empty())
            
            member.playSound(member.location, Sound.BLOCK_PORTAL_TRAVEL, 0.5f, 1.5f)
        }

        // Build arena after teleport
        buildDungeonArena(instance, spawnLoc)

        // Start dungeon logic
        startDungeonLogic(instance)

        return true
    }

    private fun getDungeonWorld(): World {
        // Use the_end or create a dedicated world
        return plugin.server.getWorld("world_the_end") 
            ?: plugin.server.worlds.first()
    }

    private fun findSafeDungeonSpawn(world: World, instance: DungeonInstance): Location {
        // Generate a unique location based on instance ID to avoid overlap
        val hash = instance.id.hashCode()
        val x = (hash % 10000) * 100.0 + 10000
        val z = ((hash / 10000) % 10000) * 100.0 + 10000
        val y = 100.0
        return Location(world, x, y, z)
    }

    private fun buildDungeonArena(instance: DungeonInstance, center: Location) {
        val world = center.world ?: return
        val radius = 25
        val height = 15

        plugin.server.scheduler.runTask(plugin, Runnable {
            // Build based on dungeon type
            val floorMaterial = when (instance.type) {
                DungeonType.SHADOW_CAVERN -> Material.DEEPSLATE
                DungeonType.INFERNAL_PIT, DungeonType.BLOOD_MOON -> Material.NETHER_BRICKS
                DungeonType.FROZEN_DEPTHS -> Material.PACKED_ICE
                DungeonType.VOID_ARENA, DungeonType.DRAGONS_LAIR -> Material.END_STONE_BRICKS
                DungeonType.ANCIENT_TEMPLE -> Material.STONE_BRICKS
                DungeonType.PIRATE_COVE -> Material.OAK_PLANKS
            }

            val wallMaterial = when (instance.type) {
                DungeonType.SHADOW_CAVERN -> Material.BLACKSTONE
                DungeonType.INFERNAL_PIT, DungeonType.BLOOD_MOON -> Material.MAGMA_BLOCK
                DungeonType.FROZEN_DEPTHS -> Material.BLUE_ICE
                DungeonType.VOID_ARENA, DungeonType.DRAGONS_LAIR -> Material.OBSIDIAN
                DungeonType.ANCIENT_TEMPLE -> Material.MOSSY_STONE_BRICKS
                DungeonType.PIRATE_COVE -> Material.DARK_OAK_PLANKS
            }

            // Clear and build arena
            for (x in -radius..radius) {
                for (z in -radius..radius) {
                    val loc = center.clone().add(x.toDouble(), 0.0, z.toDouble())
                    
                    // Floor
                    loc.block.type = floorMaterial
                    
                    // Clear above
                    for (y in 1..height) {
                        loc.clone().add(0.0, y.toDouble(), 0.0).block.type = Material.AIR
                    }
                    
                    // Walls
                    if (x == -radius || x == radius || z == -radius || z == radius) {
                        for (y in 1..height) {
                            loc.clone().add(0.0, y.toDouble(), 0.0).block.type = wallMaterial
                        }
                    }
                    
                    // Ceiling
                    loc.clone().add(0.0, (height + 1).toDouble(), 0.0).block.type = wallMaterial
                    
                    // Below floor 
                    loc.clone().add(0.0, -1.0, 0.0).block.type = Material.BEDROCK
                }
            }

            // Add some light
            for (i in 0 until 8) {
                val angle = Math.PI * 2 * i / 8
                val lx = (Math.cos(angle) * (radius - 5)).toInt()
                val lz = (Math.sin(angle) * (radius - 5)).toInt()
                center.clone().add(lx.toDouble(), 3.0, lz.toDouble()).block.type = when (instance.type) {
                    DungeonType.INFERNAL_PIT -> Material.SHROOMLIGHT
                    DungeonType.FROZEN_DEPTHS -> Material.SEA_LANTERN
                    else -> Material.LANTERN
                }
            }

            // Spawn objectives based on type
            when (instance.type) {
                DungeonType.FROZEN_DEPTHS -> {
                    // Spawn 3 ice crystals (beacons representing crystals)
                    instance.objectiveTarget = 3
                    for (i in 0 until 3) {
                        val angle = Math.PI * 2 * i / 3
                        val cx = (Math.cos(angle) * 15).toInt()
                        val cz = (Math.sin(angle) * 15).toInt()
                        val crystalLoc = center.clone().add(cx.toDouble(), 1.0, cz.toDouble())
                        crystalLoc.block.type = Material.DIAMOND_BLOCK
                        crystalLoc.clone().add(0.0, 1.0, 0.0).block.type = Material.BEACON
                    }
                }
                DungeonType.INFERNAL_PIT -> {
                    instance.objectiveTarget = 5 // 5 waves
                }
                DungeonType.VOID_ARENA -> {
                    instance.objectiveTarget = 1 // 1 boss
                }
                else -> {
                    instance.objectiveTarget = 1
                }
            }
        })
    }

    private fun startDungeonLogic(instance: DungeonInstance) {
        instance.taskId = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            if (instance.completed || instance.failed) {
                instance.taskId?.cancel()
                return@Runnable
            }

            val elapsed = (System.currentTimeMillis() - instance.startTime) / 1000
            val remaining = instance.getTimeLimit() - elapsed

            // Update boss bar
            instance.bossBar?.let { bar ->
                val progress = (remaining.toFloat() / instance.getTimeLimit()).coerceIn(0f, 1f)
                bar.progress(progress)
                bar.name(Component.text("⚔ ${instance.type.displayName} - ${remaining / 60}:${String.format("%02d", remaining % 60)} ⚔", NamedTextColor.RED))
            }

            // Time up?
            if (remaining <= 0) {
                failDungeon(instance, "Time's up!")
                return@Runnable
            }

            // Type-specific logic
            when (instance.type) {
                DungeonType.INFERNAL_PIT -> handleWaveLogic(instance)
                DungeonType.BLOOD_MOON -> handleWaveLogic(instance) // Endless waves
                DungeonType.VOID_ARENA -> handleBossLogic(instance)
                DungeonType.DRAGONS_LAIR -> handleBossLogic(instance) // Boss fight
                DungeonType.SHADOW_CAVERN -> handleMazeLogic(instance)
                DungeonType.FROZEN_DEPTHS -> handleCrystalLogic(instance)
                DungeonType.ANCIENT_TEMPLE -> handleTempleLogic(instance)
                DungeonType.PIRATE_COVE -> handleWaveLogic(instance) // Wave-based
            }
        }, 20L, 20L)
    }

    private fun handleWaveLogic(instance: DungeonInstance) {
        // Check if all mobs from current wave are dead
        val aliveMobs = instance.spawnedMobs.count { mobId ->
            plugin.server.getEntity(mobId)?.let { !it.isDead } ?: false
        }

        if (aliveMobs == 0 && instance.currentWave < instance.objectiveTarget) {
            instance.currentWave++
            spawnWave(instance)
        }

        if (instance.currentWave >= instance.objectiveTarget && aliveMobs == 0) {
            completeDungeon(instance)
        }
    }

    private fun spawnWave(instance: DungeonInstance) {
        val center = instance.arenaCenter ?: return
        val wave = instance.currentWave

        instance.players.mapNotNull { plugin.server.getPlayer(it) }.forEach { player ->
            player.sendMessage(Component.text("═══ WAVE $wave/${instance.objectiveTarget} ═══", NamedTextColor.RED, TextDecoration.BOLD))
            player.playSound(player.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f)
        }

        // Base mob count scaled by party size and modifier
        val baseMobCount = 3 + wave * 2
        val scaledMobCount = (baseMobCount * instance.getMobMultiplier()).coerceAtLeast(3)
        
        val mobTypes = when (instance.type) {
            DungeonType.INFERNAL_PIT -> listOf(EntityType.BLAZE, EntityType.WITHER_SKELETON, EntityType.PIGLIN_BRUTE)
            DungeonType.BLOOD_MOON -> listOf(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER, EntityType.WITCH)
            DungeonType.PIRATE_COVE -> listOf(EntityType.DROWNED, EntityType.ZOMBIE, EntityType.SKELETON)
            else -> listOf(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER)
        }

        // Scale mob health based on party size
        val healthMultiplier = instance.getScalingFactor()
        val isElite = instance.modifier == DungeonModifier.ELITE

        for (i in 0 until scaledMobCount) {
            val angle = Math.random() * Math.PI * 2
            val distance = 8 + Math.random() * 10
            val spawnLoc = center.clone().add(
                Math.cos(angle) * distance,
                1.0,
                Math.sin(angle) * distance
            )

            val mob = center.world?.spawnEntity(spawnLoc, mobTypes.random()) as? LivingEntity ?: continue
            val baseHealth = 20.0 + wave * 5
            mob.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.baseValue = baseHealth * healthMultiplier
            mob.health = mob.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
            
            // Elite mobs get extra effects
            if (isElite) {
                mob.addPotionEffect(PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 0, false, false))
                mob.addPotionEffect(PotionEffect(PotionEffectType.STRENGTH, PotionEffect.INFINITE_DURATION, 0, false, false))
            }
            
            mob.customName(Component.text("${if (isElite) "Elite " else ""}Dungeon ${mob.type.name.lowercase().replaceFirstChar { it.uppercase() }}", NamedTextColor.RED))
            
            instance.spawnedMobs.add(mob.uniqueId)
        }
    }

    private fun handleBossLogic(instance: DungeonInstance) {
        if (instance.objectiveProgress == 0 && instance.currentWave == 0) {
            instance.currentWave = 1
            spawnDungeonBoss(instance)
        }

        // Check if boss is dead
        val bossAlive = instance.spawnedMobs.any { mobId ->
            plugin.server.getEntity(mobId)?.let { !it.isDead } ?: false
        }

        if (!bossAlive && instance.currentWave > 0) {
            completeDungeon(instance)
        }
    }

    private fun spawnDungeonBoss(instance: DungeonInstance) {
        val center = instance.arenaCenter ?: return
        
        val boss = center.world?.spawnEntity(center.clone().add(0.0, 1.0, 0.0), EntityType.ENDERMAN) as? LivingEntity ?: return
        boss.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.baseValue = 300.0
        boss.health = 300.0
        boss.customName(Component.text("☠ Ender Champion ☠", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD))
        boss.isCustomNameVisible = true
        
        // Give boss effects
        boss.addPotionEffect(PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 1, false, false))
        boss.addPotionEffect(PotionEffect(PotionEffectType.STRENGTH, PotionEffect.INFINITE_DURATION, 1, false, false))
        
        instance.spawnedMobs.add(boss.uniqueId)

        instance.players.mapNotNull { plugin.server.getPlayer(it) }.forEach { player ->
            player.sendMessage(Component.text("☠ THE ENDER CHAMPION AWAKENS! ☠", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD))
            player.playSound(player.location, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.5f)
        }
    }

    private fun handleMazeLogic(instance: DungeonInstance) {
        // For maze: spawn mobs periodically and have a boss at the end
        if (instance.currentWave == 0) {
            instance.currentWave = 1
            instance.objectiveTarget = 1
            
            // Spawn the maze boss
            val center = instance.arenaCenter ?: return
            val boss = center.world?.spawnEntity(center.clone().add(20.0, 1.0, 0.0), EntityType.WARDEN) as? LivingEntity
            boss?.customName(Component.text("☠ Shadow Warden ☠", NamedTextColor.DARK_GRAY, TextDecoration.BOLD))
            boss?.isCustomNameVisible = true
            boss?.let { instance.spawnedMobs.add(it.uniqueId) }
        }

        val bossAlive = instance.spawnedMobs.any { plugin.server.getEntity(it)?.let { e -> !e.isDead } ?: false }
        if (!bossAlive) completeDungeon(instance)
    }

    private fun handleCrystalLogic(instance: DungeonInstance) {
        // Crystals are destroyed by breaking the beacon blocks
        // We check if 3 crystals are destroyed via objectiveProgress
        if (instance.objectiveProgress >= instance.objectiveTarget) {
            completeDungeon(instance)
        }

        // Spawn mobs periodically
        if (plugin.server.currentTick % 200 == 0) {
            spawnCrystalGuardians(instance)
        }
    }

    private fun spawnCrystalGuardians(instance: DungeonInstance) {
        val center = instance.arenaCenter ?: return
        for (i in 0 until 3) {
            val angle = Math.random() * Math.PI * 2
            val loc = center.clone().add(Math.cos(angle) * 10, 1.0, Math.sin(angle) * 10)
            val mob = center.world?.spawnEntity(loc, EntityType.STRAY) as? LivingEntity ?: continue
            mob.customName(Component.text("Frost Guardian", NamedTextColor.AQUA))
            instance.spawnedMobs.add(mob.uniqueId)
        }
    }

    private fun handleTempleLogic(instance: DungeonInstance) {
        // Temple has puzzle elements + a boss
        if (instance.currentWave == 0) {
            instance.currentWave = 1
            val center = instance.arenaCenter ?: return
            val boss = center.world?.spawnEntity(center.clone().add(0.0, 1.0, 15.0), EntityType.EVOKER) as? LivingEntity
            boss?.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.baseValue = 200.0
            boss?.health = 200.0
            boss?.customName(Component.text("☠ Temple Guardian ☠", NamedTextColor.DARK_GREEN, TextDecoration.BOLD))
            boss?.isCustomNameVisible = true
            boss?.let { instance.spawnedMobs.add(it.uniqueId) }
        }

        val bossAlive = instance.spawnedMobs.any { plugin.server.getEntity(it)?.let { e -> !e.isDead } ?: false }
        if (!bossAlive) completeDungeon(instance)
    }

    fun completeDungeon(instance: DungeonInstance) {
        if (instance.completed || instance.failed) return
        instance.completed = true
        instance.taskId?.cancel()

        val elapsed = (System.currentTimeMillis() - instance.startTime) / 1000
        val bonus = if (elapsed < instance.type.timeLimit / 2) 1.5 else 1.0

        instance.players.mapNotNull { plugin.server.getPlayer(it) }.forEach { player ->
            player.sendMessage(Component.empty())
            player.sendMessage(Component.text("═══════════════════════════════", NamedTextColor.GOLD))
            player.sendMessage(Component.text("  ✓ DUNGEON COMPLETE!", NamedTextColor.GREEN, TextDecoration.BOLD))
            player.sendMessage(Component.text("  ${instance.type.displayName}", NamedTextColor.YELLOW))
            player.sendMessage(Component.text("  Time: ${elapsed / 60}:${String.format("%02d", elapsed % 60)}", NamedTextColor.GRAY))
            if (bonus > 1.0) {
                player.sendMessage(Component.text("  SPEED BONUS: x1.5!", NamedTextColor.AQUA))
                // Achievement: Speed Run
            // Achievement: Speed Run
                plugin.achievementManager.awardAchievement(player, "dungeon_speed")
            }
            player.sendMessage(Component.text("  Rewards:", NamedTextColor.WHITE))
            player.sendMessage(Component.text("    +${(instance.type.goldReward * bonus).toLong()}g", NamedTextColor.GOLD))
            player.sendMessage(Component.text("    +${(instance.type.xpReward * bonus).toLong()} XP", NamedTextColor.GREEN))
            player.sendMessage(Component.text("═══════════════════════════════", NamedTextColor.GOLD))
            player.sendMessage(Component.empty())

            // Give rewards
            val profile = plugin.identityManager.getPlayer(player.uniqueId)
            if (profile != null) {
                profile.balance += instance.type.goldReward * bonus
                plugin.identityManager.saveProfile(player.uniqueId)
            }
            plugin.identityManager.grantXp(player, (instance.type.xpReward * bonus).toLong())

            // Bonus loot
            player.inventory.addItem(ItemStack(Material.DIAMOND, 2 + instance.type.difficulty))
            if (Math.random() < 0.2) {
                val relic = plugin.relicManager.createRelic(com.projectatlas.relics.RelicManager.RelicType.entries.random())
                player.inventory.addItem(relic)
                player.sendMessage(Component.text("★ BONUS: You found a Relic!", NamedTextColor.LIGHT_PURPLE))
                // Achievement: Relic Hunter
                plugin.achievementManager.awardAchievement(player, "relic_hunter")
            }

            player.playSound(player.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f)
            
            // Achievement: Dungeon Conqueror
            plugin.achievementManager.awardAchievement(player, "dungeon_conqueror")

            // Return after delay
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                leaveDungeon(player)
            }, 100L)
        }

        instance.bossBar?.let { bar ->
            instance.players.mapNotNull { plugin.server.getPlayer(it) }.forEach { it.hideBossBar(bar) }
        }
    }

    fun failDungeon(instance: DungeonInstance, reason: String) {
        if (instance.completed || instance.failed) return
        instance.failed = true
        instance.taskId?.cancel()

        instance.players.mapNotNull { plugin.server.getPlayer(it) }.forEach { player ->
            player.sendMessage(Component.empty())
            player.sendMessage(Component.text("═══════════════════════════════", NamedTextColor.DARK_RED))
            player.sendMessage(Component.text("  ✗ DUNGEON FAILED", NamedTextColor.RED, TextDecoration.BOLD))
            player.sendMessage(Component.text("  $reason", NamedTextColor.GRAY))
            player.sendMessage(Component.text("═══════════════════════════════", NamedTextColor.DARK_RED))
            player.sendMessage(Component.empty())

            player.playSound(player.location, Sound.ENTITY_WITHER_DEATH, 0.5f, 0.5f)

            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                leaveDungeon(player)
            }, 60L)
        }

        instance.bossBar?.let { bar ->
            instance.players.mapNotNull { plugin.server.getPlayer(it) }.forEach { it.hideBossBar(bar) }
        }
    }

    fun leaveDungeon(player: Player) {
        val instance = activeInstances.remove(player.uniqueId) ?: return
        
        // Teleport back
        instance.returnLocations[player.uniqueId]?.let { player.teleport(it) }
        
        // Hide boss bar
        instance.bossBar?.let { player.hideBossBar(it) }
        
        // If no players left, clean up
        instance.players.remove(player.uniqueId)
        if (instance.players.isEmpty()) {
            cleanupInstance(instance)
        }
    }

    private fun cleanupInstance(instance: DungeonInstance) {
        instance.taskId?.cancel()
        instancesByUUID.remove(instance.id)
        
        // Despawn mobs
        instance.spawnedMobs.forEach { mobId ->
            plugin.server.getEntity(mobId)?.remove()
        }
    }

    @EventHandler
    fun onMobDeath(event: EntityDeathEvent) {
        val mobId = event.entity.uniqueId
        
        // Find which instance this mob belongs to
        instancesByUUID.values.find { it.spawnedMobs.contains(mobId) }?.let { instance ->
            instance.spawnedMobs.remove(mobId)
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        activeInstances[event.player.uniqueId]?.let { instance ->
            failDungeon(instance, "Player disconnected")
        }
    }

    fun getAvailableDungeons(): List<DungeonType> = DungeonType.entries

    fun isInDungeon(player: Player): Boolean = activeInstances.containsKey(player.uniqueId)
}
