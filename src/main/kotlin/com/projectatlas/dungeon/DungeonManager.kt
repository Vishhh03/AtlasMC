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
import org.bukkit.event.EventPriority
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerRespawnEvent
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
    private val dungeonCooldowns = ConcurrentHashMap<UUID, Long>()
    private val pendingRespawns = ConcurrentHashMap<UUID, Location>()

    fun enterDungeon(player: Player, type: DungeonType, modifier: DungeonModifier = DungeonModifier.NONE): Boolean {
        if (activeInstances.containsKey(player.uniqueId)) {
            player.sendMessage(Component.text("You're already in a dungeon!", NamedTextColor.RED))
            return false
        }
        
        // Cooldown Check
        val cooldown = dungeonCooldowns[player.uniqueId] ?: 0L
        if (System.currentTimeMillis() < cooldown) {
            val left = (cooldown - System.currentTimeMillis()) / 1000
            player.sendMessage(Component.text("Dungeon Cooldown: ${left}s remaining", NamedTextColor.RED))
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
        val radius = 30 // Larger arena
        val height = 20

        plugin.server.scheduler.runTask(plugin, Runnable {
            when (instance.type) {
                DungeonType.SHADOW_CAVERN -> buildShadowCavern(center, radius, height, instance)
                DungeonType.INFERNAL_PIT -> buildInfernalPit(center, radius, height, instance)
                DungeonType.FROZEN_DEPTHS -> buildFrozenDepths(center, radius, height, instance)
                DungeonType.VOID_ARENA -> buildVoidArena(center, radius, height, instance)
                DungeonType.ANCIENT_TEMPLE -> buildAncientTemple(center, radius, height, instance)
                DungeonType.PIRATE_COVE -> buildPirateCove(center, radius, height, instance)
                DungeonType.BLOOD_MOON -> buildBloodMoonArena(center, radius, height, instance)
                DungeonType.DRAGONS_LAIR -> buildDragonsLair(center, radius, height, instance)
            }
        })
    }
    
    // ══════════════════════════════════════════════════════════════
    // UNIQUE DUNGEON BUILDERS
    // ══════════════════════════════════════════════════════════════
    
    private fun buildShadowCavern(center: Location, radius: Int, height: Int, instance: DungeonInstance) {
        val random = Random()
        
        // Irregular cave shape
        for (x in -radius..radius) {
            for (z in -radius..radius) {
                val dist = Math.sqrt((x * x + z * z).toDouble())
                if (dist > radius) continue
                
                val loc = center.clone().add(x.toDouble(), 0.0, z.toDouble())
                
                // Floor - varied deepslate
                loc.block.type = if (random.nextDouble() < 0.3) Material.DEEPSLATE_TILES else Material.DEEPSLATE
                loc.clone().add(0.0, -1.0, 0.0).block.type = Material.BEDROCK
                
                // Clear interior with varying ceiling height (cave-like)
                val ceilingHeight = height - random.nextInt(5)
                for (y in 1..ceilingHeight) {
                    loc.clone().add(0.0, y.toDouble(), 0.0).block.type = Material.AIR
                }
                
                // Walls with stalactites
                if (dist > radius - 3) {
                    for (y in 1..height) {
                        val wallBlock = loc.clone().add(0.0, y.toDouble(), 0.0).block
                        wallBlock.type = if (random.nextDouble() < 0.2) Material.POLISHED_DEEPSLATE else Material.DEEPSLATE_BRICKS
                    }
                }
                
                // Ceiling
                loc.clone().add(0.0, (ceilingHeight + 1).toDouble(), 0.0).block.type = Material.DEEPSLATE
                
                // Random stalagmites
                if (random.nextDouble() < 0.05 && dist < radius - 5) {
                    val stalagHeight = random.nextInt(3) + 1
                    for (y in 1..stalagHeight) {
                        loc.clone().add(0.0, y.toDouble(), 0.0).block.type = Material.POINTED_DRIPSTONE
                    }
                }
            }
        }
        
        // Scattered soul lanterns for eerie lighting
        for (i in 0 until 12) {
            val angle = Math.PI * 2 * i / 12
            val lx = (Math.cos(angle) * (radius - 8)).toInt()
            val lz = (Math.sin(angle) * (radius - 8)).toInt()
            center.clone().add(lx.toDouble(), 4.0, lz.toDouble()).block.type = Material.SOUL_LANTERN
        }
        
        // Cobwebs
        for (i in 0 until 20) {
            val wx = random.nextInt(radius * 2) - radius
            val wz = random.nextInt(radius * 2) - radius
            if (Math.sqrt((wx * wx + wz * wz).toDouble()) < radius - 5) {
                center.clone().add(wx.toDouble(), (random.nextInt(5) + 2).toDouble(), wz.toDouble()).block.type = Material.COBWEB
            }
        }
        
        instance.objectiveTarget = 1 // Kill the boss
    }
    
    private fun buildInfernalPit(center: Location, radius: Int, height: Int, instance: DungeonInstance) {
        val random = Random()
        
        for (x in -radius..radius) {
            for (z in -radius..radius) {
                val dist = Math.sqrt((x * x + z * z).toDouble())
                if (dist > radius) continue
                
                val loc = center.clone().add(x.toDouble(), 0.0, z.toDouble())
                
                // Floor - mix of nether bricks and magma
                loc.block.type = when {
                    random.nextDouble() < 0.15 -> Material.MAGMA_BLOCK
                    random.nextDouble() < 0.3 -> Material.CRACKED_NETHER_BRICKS
                    else -> Material.NETHER_BRICKS
                }
                loc.clone().add(0.0, -1.0, 0.0).block.type = Material.BEDROCK
                
                // Clear interior
                for (y in 1..height) {
                    loc.clone().add(0.0, y.toDouble(), 0.0).block.type = Material.AIR
                }
                
                // Walls with fire
                if (dist > radius - 2) {
                    for (y in 1..height) {
                        val wallBlock = loc.clone().add(0.0, y.toDouble(), 0.0).block
                        wallBlock.type = if (y % 4 == 0) Material.SHROOMLIGHT else Material.RED_NETHER_BRICKS
                    }
                }
                
                // Ceiling
                loc.clone().add(0.0, (height + 1).toDouble(), 0.0).block.type = Material.NETHER_BRICKS
                
                // Lava pools
                if (random.nextDouble() < 0.02 && dist < radius - 8 && dist > 5) {
                    loc.clone().add(0.0, 0.0, 0.0).block.type = Material.LAVA
                }
            }
        }
        
        // Netherrack pillars with fire
        for (i in 0 until 6) {
            val angle = Math.PI * 2 * i / 6
            val px = (Math.cos(angle) * (radius - 10)).toInt()
            val pz = (Math.sin(angle) * (radius - 10)).toInt()
            for (y in 1..8) {
                center.clone().add(px.toDouble(), y.toDouble(), pz.toDouble()).block.type = Material.NETHERRACK
            }
            center.clone().add(px.toDouble(), 9.0, pz.toDouble()).block.type = Material.FIRE
        }
        
        // Central lava pit (cosmetic)
        for (x in -3..3) {
            for (z in -3..3) {
                if (Math.sqrt((x * x + z * z).toDouble()) < 3) {
                    center.clone().add(x.toDouble(), 0.0, z.toDouble()).block.type = Material.LAVA
                }
            }
        }
        
        instance.objectiveTarget = 5 // 5 waves
    }
    
    private fun buildFrozenDepths(center: Location, radius: Int, height: Int, instance: DungeonInstance) {
        val random = Random()
        
        for (x in -radius..radius) {
            for (z in -radius..radius) {
                val dist = Math.sqrt((x * x + z * z).toDouble())
                if (dist > radius) continue
                
                val loc = center.clone().add(x.toDouble(), 0.0, z.toDouble())
                
                // Floor - ice variants
                loc.block.type = when {
                    random.nextDouble() < 0.2 -> Material.BLUE_ICE
                    random.nextDouble() < 0.5 -> Material.PACKED_ICE
                    else -> Material.ICE
                }
                loc.clone().add(0.0, -1.0, 0.0).block.type = Material.BEDROCK
                
                // Clear interior
                for (y in 1..height) {
                    loc.clone().add(0.0, y.toDouble(), 0.0).block.type = Material.AIR
                }
                
                // Walls
                if (dist > radius - 2) {
                    for (y in 1..height) {
                        loc.clone().add(0.0, y.toDouble(), 0.0).block.type = Material.BLUE_ICE
                    }
                }
                
                // Snow layer on top
                if (random.nextDouble() < 0.3 && dist < radius - 3) {
                    loc.clone().add(0.0, 1.0, 0.0).block.type = Material.SNOW
                }
                
                loc.clone().add(0.0, (height + 1).toDouble(), 0.0).block.type = Material.PACKED_ICE
            }
        }
        
        // Ice spires
        for (i in 0 until 5) {
            val angle = Math.PI * 2 * i / 5
            val sx = (Math.cos(angle) * (radius - 12)).toInt()
            val sz = (Math.sin(angle) * (radius - 12)).toInt()
            for (y in 1..random.nextInt(6) + 4) {
                center.clone().add(sx.toDouble(), y.toDouble(), sz.toDouble()).block.type = Material.BLUE_ICE
            }
        }
        
        // 3 Ice Crystals (objectives)
        instance.objectiveTarget = 3
        for (i in 0 until 3) {
            val angle = Math.PI * 2 * i / 3
            val cx = (Math.cos(angle) * 15).toInt()
            val cz = (Math.sin(angle) * 15).toInt()
            val crystalLoc = center.clone().add(cx.toDouble(), 1.0, cz.toDouble())
            
            // Crystal base
            crystalLoc.block.type = Material.DIAMOND_BLOCK
            crystalLoc.clone().add(0.0, 1.0, 0.0).block.type = Material.BEACON
            crystalLoc.clone().add(0.0, 2.0, 0.0).block.type = Material.SEA_LANTERN
            
            // Surrounding ice
            for (dx in -1..1) {
                for (dz in -1..1) {
                    if (dx != 0 || dz != 0) {
                        crystalLoc.clone().add(dx.toDouble(), 0.0, dz.toDouble()).block.type = Material.BLUE_ICE
                    }
                }
            }
        }
        
        // Sea lanterns for lighting
        for (i in 0 until 10) {
            val angle = Math.PI * 2 * i / 10
            val lx = (Math.cos(angle) * (radius - 6)).toInt()
            val lz = (Math.sin(angle) * (radius - 6)).toInt()
            center.clone().add(lx.toDouble(), 5.0, lz.toDouble()).block.type = Material.SEA_LANTERN
        }
    }
    
    private fun buildVoidArena(center: Location, radius: Int, height: Int, instance: DungeonInstance) {
        val random = Random()
        
        for (x in -radius..radius) {
            for (z in -radius..radius) {
                val dist = Math.sqrt((x * x + z * z).toDouble())
                if (dist > radius) continue
                
                val loc = center.clone().add(x.toDouble(), 0.0, z.toDouble())
                
                // Floor - end stone pattern
                loc.block.type = when {
                    (x + z) % 5 == 0 -> Material.PURPUR_BLOCK
                    random.nextDouble() < 0.1 -> Material.END_STONE_BRICKS
                    else -> Material.END_STONE
                }
                loc.clone().add(0.0, -1.0, 0.0).block.type = Material.BEDROCK
                
                // Clear interior
                for (y in 1..height) {
                    loc.clone().add(0.0, y.toDouble(), 0.0).block.type = Material.AIR
                }
                
                // Obsidian pillars at edges
                if (dist > radius - 2) {
                    for (y in 1..height) {
                        loc.clone().add(0.0, y.toDouble(), 0.0).block.type = Material.OBSIDIAN
                    }
                }
                
                loc.clone().add(0.0, (height + 1).toDouble(), 0.0).block.type = Material.OBSIDIAN
            }
        }
        
        // End rod pillars
        for (i in 0 until 8) {
            val angle = Math.PI * 2 * i / 8
            val px = (Math.cos(angle) * (radius - 8)).toInt()
            val pz = (Math.sin(angle) * (radius - 8)).toInt()
            for (y in 1..12) {
                val block = center.clone().add(px.toDouble(), y.toDouble(), pz.toDouble()).block
                block.type = if (y == 12) Material.END_ROD else Material.PURPUR_PILLAR
            }
        }
        
        // Central platform for boss
        for (x in -4..4) {
            for (z in -4..4) {
                if (Math.sqrt((x * x + z * z).toDouble()) < 4) {
                    center.clone().add(x.toDouble(), 1.0, z.toDouble()).block.type = Material.PURPUR_BLOCK
                }
            }
        }
        
        instance.objectiveTarget = 1 // Kill the boss
    }
    
    private fun buildAncientTemple(center: Location, radius: Int, height: Int, instance: DungeonInstance) {
        val random = Random()
        
        for (x in -radius..radius) {
            for (z in -radius..radius) {
                val dist = Math.sqrt((x * x + z * z).toDouble())
                if (dist > radius) continue
                
                val loc = center.clone().add(x.toDouble(), 0.0, z.toDouble())
                
                // Floor - temple pattern
                loc.block.type = when {
                    (x + z) % 4 == 0 -> Material.CHISELED_STONE_BRICKS
                    random.nextDouble() < 0.15 -> Material.MOSSY_STONE_BRICKS
                    random.nextDouble() < 0.1 -> Material.CRACKED_STONE_BRICKS
                    else -> Material.STONE_BRICKS
                }
                loc.clone().add(0.0, -1.0, 0.0).block.type = Material.BEDROCK
                
                for (y in 1..height) {
                    loc.clone().add(0.0, y.toDouble(), 0.0).block.type = Material.AIR
                }
                
                if (dist > radius - 2) {
                    for (y in 1..height) {
                        loc.clone().add(0.0, y.toDouble(), 0.0).block.type = Material.STONE_BRICKS
                    }
                }
                
                loc.clone().add(0.0, (height + 1).toDouble(), 0.0).block.type = Material.STONE_BRICKS
            }
        }
        
        // Temple pillars
        for (i in 0 until 8) {
            val angle = Math.PI * 2 * i / 8
            val px = (Math.cos(angle) * (radius - 10)).toInt()
            val pz = (Math.sin(angle) * (radius - 10)).toInt()
            for (y in 1..10) {
                center.clone().add(px.toDouble(), y.toDouble(), pz.toDouble()).block.type = Material.QUARTZ_PILLAR
            }
            center.clone().add(px.toDouble(), 11.0, pz.toDouble()).block.type = Material.LANTERN
        }
        
        // Vines
        for (i in 0 until 15) {
            val vx = random.nextInt(radius * 2) - radius
            val vz = random.nextInt(radius * 2) - radius
            if (Math.sqrt((vx * vx + vz * vz).toDouble()) < radius - 3) {
                for (y in height downTo height - 3) {
                    center.clone().add(vx.toDouble(), y.toDouble(), vz.toDouble()).block.type = Material.VINE
                }
            }
        }
        
        // Central altar
        center.clone().add(0.0, 1.0, 0.0).block.type = Material.LODESTONE
        center.clone().add(0.0, 2.0, 0.0).block.type = Material.ENCHANTING_TABLE
        
        instance.objectiveTarget = 1
    }
    
    private fun buildPirateCove(center: Location, radius: Int, height: Int, instance: DungeonInstance) {
        val random = Random()
        
        for (x in -radius..radius) {
            for (z in -radius..radius) {
                val dist = Math.sqrt((x * x + z * z).toDouble())
                if (dist > radius) continue
                
                val loc = center.clone().add(x.toDouble(), 0.0, z.toDouble())
                
                // Floor - sandy/wooden
                loc.block.type = when {
                    dist < 10 -> Material.OAK_PLANKS
                    random.nextDouble() < 0.1 -> Material.GRAVEL
                    else -> Material.SAND
                }
                loc.clone().add(0.0, -1.0, 0.0).block.type = Material.BEDROCK
                
                for (y in 1..height) {
                    loc.clone().add(0.0, y.toDouble(), 0.0).block.type = Material.AIR
                }
                
                if (dist > radius - 2) {
                    for (y in 1..height) {
                        loc.clone().add(0.0, y.toDouble(), 0.0).block.type = Material.DARK_OAK_PLANKS
                    }
                }
                
                loc.clone().add(0.0, (height + 1).toDouble(), 0.0).block.type = Material.OAK_PLANKS
            }
        }
        
        // Treasure chests
        for (i in 0 until 5) {
            val cx = random.nextInt(20) - 10
            val cz = random.nextInt(20) - 10
            center.clone().add(cx.toDouble(), 1.0, cz.toDouble()).block.type = Material.CHEST
        }
        
        // Lanterns
        for (i in 0 until 8) {
            val angle = Math.PI * 2 * i / 8
            val lx = (Math.cos(angle) * (radius - 5)).toInt()
            val lz = (Math.sin(angle) * (radius - 5)).toInt()
            center.clone().add(lx.toDouble(), 4.0, lz.toDouble()).block.type = Material.LANTERN
        }
        
        instance.objectiveTarget = 4 // Waves of pirates
    }
    
    private fun buildBloodMoonArena(center: Location, radius: Int, height: Int, instance: DungeonInstance) {
        val random = Random()
        
        for (x in -radius..radius) {
            for (z in -radius..radius) {
                val dist = Math.sqrt((x * x + z * z).toDouble())
                if (dist > radius) continue
                
                val loc = center.clone().add(x.toDouble(), 0.0, z.toDouble())
                
                // Floor - crimson/dark
                loc.block.type = when {
                    random.nextDouble() < 0.2 -> Material.REDSTONE_BLOCK
                    random.nextDouble() < 0.3 -> Material.CRIMSON_PLANKS
                    else -> Material.CRIMSON_NYLIUM
                }
                loc.clone().add(0.0, -1.0, 0.0).block.type = Material.BEDROCK
                
                for (y in 1..height) {
                    loc.clone().add(0.0, y.toDouble(), 0.0).block.type = Material.AIR
                }
                
                if (dist > radius - 2) {
                    for (y in 1..height) {
                        loc.clone().add(0.0, y.toDouble(), 0.0).block.type = Material.CRYING_OBSIDIAN
                    }
                }
                
                loc.clone().add(0.0, (height + 1).toDouble(), 0.0).block.type = Material.CRIMSON_HYPHAE
            }
        }
        
        // Redstone lamps
        for (i in 0 until 10) {
            val angle = Math.PI * 2 * i / 10
            val lx = (Math.cos(angle) * (radius - 8)).toInt()
            val lz = (Math.sin(angle) * (radius - 8)).toInt()
            center.clone().add(lx.toDouble(), 3.0, lz.toDouble()).block.type = Material.REDSTONE_LAMP
            center.clone().add(lx.toDouble(), 2.0, lz.toDouble()).block.type = Material.REDSTONE_BLOCK // Power it
        }
        
        instance.objectiveTarget = 10 // Endless waves (survive 10 for completion)
    }
    
    private fun buildDragonsLair(center: Location, radius: Int, height: Int, instance: DungeonInstance) {
        val random = Random()
        val largeHeight = height + 10 // Taller ceiling for dragon
        
        for (x in -radius..radius) {
            for (z in -radius..radius) {
                val dist = Math.sqrt((x * x + z * z).toDouble())
                if (dist > radius) continue
                
                val loc = center.clone().add(x.toDouble(), 0.0, z.toDouble())
                
                // Floor - volcanic
                loc.block.type = when {
                    random.nextDouble() < 0.1 -> Material.MAGMA_BLOCK
                    random.nextDouble() < 0.3 -> Material.BLACKSTONE
                    else -> Material.BASALT
                }
                loc.clone().add(0.0, -1.0, 0.0).block.type = Material.BEDROCK
                
                for (y in 1..largeHeight) {
                    loc.clone().add(0.0, y.toDouble(), 0.0).block.type = Material.AIR
                }
                
                if (dist > radius - 3) {
                    for (y in 1..largeHeight) {
                        loc.clone().add(0.0, y.toDouble(), 0.0).block.type = 
                            if (y % 5 == 0) Material.SHROOMLIGHT else Material.BLACKSTONE
                    }
                }
                
                loc.clone().add(0.0, (largeHeight + 1).toDouble(), 0.0).block.type = Material.BLACKSTONE
            }
        }
        
        // Gold hoard in center
        for (x in -5..5) {
            for (z in -5..5) {
                if (random.nextDouble() < 0.5) {
                    center.clone().add(x.toDouble(), 1.0, z.toDouble()).block.type = Material.GOLD_BLOCK
                }
            }
        }
        
        // Obsidian pillars
        for (i in 0 until 6) {
            val angle = Math.PI * 2 * i / 6
            val px = (Math.cos(angle) * (radius - 8)).toInt()
            val pz = (Math.sin(angle) * (radius - 8)).toInt()
            for (y in 1..15) {
                center.clone().add(px.toDouble(), y.toDouble(), pz.toDouble()).block.type = Material.OBSIDIAN
            }
            center.clone().add(px.toDouble(), 16.0, pz.toDouble()).block.type = Material.END_ROD
        }
        
        instance.objectiveTarget = 1 // Kill the dragon
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
        instance.failed = true
        instance.taskId?.cancel()

        // Set Cooldown
        instance.players.forEach { uuid ->
             dungeonCooldowns[uuid] = System.currentTimeMillis() + 300000 // 5 minutes
        }

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

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.entity
        val instance = activeInstances[player.uniqueId] ?: return
        
        if (instance.completed || instance.failed) return // Already processed

        // Save return location for respawn (survives instance cleanup)
        instance.returnLocations[player.uniqueId]?.let {
            pendingRespawns[player.uniqueId] = it
        }

        // Protect Inventory
        event.keepInventory = true
        event.drops.clear()
        event.keepLevel = true
        
        failDungeon(instance, "Player ${player.name} fell in battle!")
        player.sendMessage(Component.text("You died in the dungeon! Items preserved.", NamedTextColor.YELLOW))
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        
        // 1. Check Pending Respawns (From Death)
        if (pendingRespawns.containsKey(player.uniqueId)) {
            pendingRespawns.remove(player.uniqueId)?.let {
                event.respawnLocation = it
            }
            return
        }
        
        // 2. Check Active Instance (Fallback)
        val instance = activeInstances[player.uniqueId] ?: return
        instance.returnLocations[player.uniqueId]?.let { 
            event.respawnLocation = it 
        }
        
        // Ensure cleanup if not already triggered
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            leaveDungeon(player)
        }, 2L)
    }
}
