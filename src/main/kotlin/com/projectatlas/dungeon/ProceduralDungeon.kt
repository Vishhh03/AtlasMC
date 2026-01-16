package com.projectatlas.dungeon

import com.projectatlas.AtlasPlugin
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.Material
import org.bukkit.block.Chest
import org.bukkit.inventory.ItemStack
import java.util.UUID
import kotlin.random.Random

/**
 * Represents a live, procedural dungeon instance.
 */
class ProceduralDungeon(
    val id: UUID,
    val plugin: AtlasPlugin,
    val theme: DungeonTheme,
    val difficulty: Int,
    val rooms: List<DungeonRoom>,
    val players: MutableSet<UUID>,
    val startLocation: Location
) {
    var active: Boolean = true
    private var currentRoomIndex: Int = 0 // Tracks "progress" loosely
    private val completedRooms = mutableSetOf<Int>() // HashCode of rooms or Index
    val spawnLocation = startLocation.clone().add(0.0, 2.0, 0.0)
    var bossEntity: org.bukkit.entity.LivingEntity? = null
    
    // Bossbar
    val bossBar = BossBar.bossBar(
        Component.text("Exploring: ${theme.name.lowercase().replaceFirstChar { it.uppercase() }}", NamedTextColor.RED),
        1.0f,
        BossBar.Color.PURPLE,
        BossBar.Overlay.NOTCHED_10
    )

    init {
        // Start loop
        object : BukkitRunnable() {
            override fun run() {
                if (!active || players.isEmpty()) {
                    cancel()
                    return
                }
                tick()
            }
        }.runTaskTimer(plugin, 20L, 20L)
    }

    fun tick() {
        val onlinePlayers = players.mapNotNull { plugin.server.getPlayer(it) }
        
        // Update BossBar
        onlinePlayers.forEach { it.showBossBar(bossBar) }
        
        // Check Room Logic
        for (player in onlinePlayers) {
            val room = getRoomAt(player.location) ?: continue
            
            if (!room.cleared && !room.active) {
                activateRoom(room, onlinePlayers)
            }
        }
    }
    
    private fun getRoomAt(loc: Location): DungeonRoom? {
        // Map world coords back to grid
        // startLocation is (0,0) in grid terms
        
        val relX = (loc.blockX - startLocation.blockX)
        val relZ = (loc.blockZ - startLocation.blockZ)
        
        // 64 is GRID_SIZE
        val gridX = Math.round(relX.toDouble() / 64.0).toInt()
        val gridZ = Math.round(relZ.toDouble() / 64.0).toInt()
        
        return rooms.find { it.x == gridX && it.z == gridZ }
    }
    
    private val spawnedEntities = mutableListOf<UUID>()

    fun cleanup() {
        active = false
        // Remove bosses
        bossEntity?.let {
             plugin.packetManager.removeBossHealthBar(it)
             it.remove() 
        }
        // Remove all tracked mobs
        spawnedEntities.forEach { uuid ->
            plugin.server.getEntity(uuid)?.remove()
        }
        spawnedEntities.clear()
        
        // Unload chunks? (Optional/Native)
    }

    private fun activateRoom(room: DungeonRoom, players: List<Player>) {
        room.active = true
        
        players.forEach { 
            it.sendMessage(Component.text("entered ${room.type.name}...", NamedTextColor.GRAY))
            it.playSound(it.location, Sound.BLOCK_IRON_DOOR_CLOSE, 1.0f, 0.5f)
        }
        
        // Spawn Mobs logic
        if (room.type == RoomType.COMBAT_ARENA || room.type == RoomType.BOSS_ROOM) {
            spawnMobs(room, players.size)
        } else if (room.type == RoomType.TRAP_ROOM) {
            players.forEach { it.sendMessage(Component.text("⚠ IT'S A TRAP! ⚠", NamedTextColor.RED)) }
            players.forEach { it.playSound(it.location, Sound.ENTITY_TNT_PRIMED, 1.0f, 1.0f) }
            // Ambush!
            spawnMobs(room, players.size) // Uses standard logic but in a hallway
        } else {
            // instant clear for non-combat rooms
            room.cleared = true
            room.active = false
            
            if (room.type == RoomType.TREASURE_ROOM) {
                val center = startLocation.clone().add((room.x * 64).toDouble(), 1.0, (room.z * 64).toDouble())
                val chest = center.block.state as? Chest
                if (chest != null) {
                    fillLoot(chest, difficulty)
                    players.forEach { it.sendMessage(Component.text("You found a Treasure Room!", NamedTextColor.GOLD)) }
                }
            }
            
            players.forEach { it.playSound(it.location, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f) }
        }
    }
    
    private fun spawnMobs(room: DungeonRoom, scaling: Int) {
        val center = startLocation.clone().add((room.x * 64).toDouble(), 1.0, (room.z * 64).toDouble())
        val isBoss = room.type == RoomType.BOSS_ROOM
        // Scaling: 4 base + 2 per player. Boss is just 1. Trap is fewer.
        val mobCount = if (isBoss) 1 else if (room.type == RoomType.TRAP_ROOM) 3 + scaling else 4 + (scaling * 2)
        
        object : BukkitRunnable() {
            var spawned = 0
            val aliveMobs = mutableListOf<UUID>()
            
            override fun run() {
                 if (!active) { cancel(); return }
                 
                 // Cleanup invalid mobs
                 aliveMobs.removeIf { plugin.server.getEntity(it)?.isValid != true }
                 
                 if (aliveMobs.isEmpty() && spawned >= mobCount) {
                     // Room Cleared!
                     room.cleared = true
                     room.active = false
                     players.mapNotNull { plugin.server.getPlayer(it) }.forEach { 
                         it.sendMessage(Component.text("Room Cleared!", NamedTextColor.GREEN))
                         it.playSound(it.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f)
                     }
                     
                     // Spawn Reward Chest
                     if (room.type == RoomType.COMBAT_ARENA || room.type == RoomType.BOSS_ROOM) {
                         val chestLoc = center.clone()
                         chestLoc.block.type = Material.CHEST
                         val chest = chestLoc.block.state as? Chest
                         if (chest != null) {
                             fillLoot(chest, difficulty)
                         }
                     }
                     
                     if (isBoss) finishDungeon(true)
                     cancel()
                     return
                 }
                 
                 // Spawn loop
                 if (spawned < mobCount && aliveMobs.size < 8) { // Max 8 alive at once to prevent lag
                     val type = if (isBoss) theme.boss else theme.mobs.random()
                     
                     val spawnLoc = center.clone().add(
                        (Math.random() - 0.5) * 12,
                        1.0,
                        (Math.random() - 0.5) * 12
                     )
                     
                     // Ensure valid spawn (air check)
                     if (spawnLoc.block.type.isSolid) return 
                     
                     val mob = center.world.spawnEntity(spawnLoc, type)
                     if (mob is org.bukkit.entity.LivingEntity) {
                         mob.removeWhenFarAway = false // PREVENT DESPAWNING
                         equipMob(mob, difficulty, isBoss)
                         
                         if (isBoss) {
                             this@ProceduralDungeon.bossEntity = mob
                             plugin.packetManager.updateBossHealthBar(mob, "☠ ${theme.name} Boss ☠", 1.0)
                         }
                         
                         // ══════════════════════════════════════════════════════════════
                         // ANIMATION INTEGRATION
                         // ══════════════════════════════════════════════════════════════
                         
                         var modelName: String? = null
                         var procConfig = com.projectatlas.animation.ProceduralConfig.HUMANOID
                         
                         when (mob.type) {
                             org.bukkit.entity.EntityType.ZOMBIE,
                             org.bukkit.entity.EntityType.SKELETON,
                             org.bukkit.entity.EntityType.STRAY,
                             org.bukkit.entity.EntityType.HUSK,
                             org.bukkit.entity.EntityType.WITHER_SKELETON,
                             org.bukkit.entity.EntityType.ZOMBIFIED_PIGLIN -> {
                                 modelName = "humanoid"
                                 procConfig = com.projectatlas.animation.ProceduralConfig.HUMANOID
                             }
                             org.bukkit.entity.EntityType.IRON_GOLEM -> {
                                 modelName = "boss_golem"
                                 procConfig = com.projectatlas.animation.ProceduralConfig.GOLEM
                             }
                             org.bukkit.entity.EntityType.SPIDER,
                             org.bukkit.entity.EntityType.CAVE_SPIDER -> {
                                 modelName = "spider_boss"
                                 procConfig = com.projectatlas.animation.ProceduralConfig.BEAST
                             }
                             org.bukkit.entity.EntityType.BLAZE,
                             org.bukkit.entity.EntityType.GHAST,
                             org.bukkit.entity.EntityType.PHANTOM -> {
                                 modelName = "floating_skull" // Placeholder for flying mobs
                                 procConfig = com.projectatlas.animation.ProceduralConfig.FLOATING
                             }
                             else -> {
                                // Default or no model
                                if (theme == DungeonTheme.CRYPT) modelName = "humanoid" // Fallback
                             }
                         }
                         
                         if (modelName != null) {
                             // Restore Armor stats via Attributes (since physical armor is stripped by attachModel)
                             val armorAttr = mob.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ARMOR)
                             if (isBoss) {
                                 armorAttr?.baseValue = 12.0 // Approx Diamond Helm + Chest
                             } else {
                                 if (difficulty >= 3) {
                                     armorAttr?.baseValue = 6.0 // Iron Chest
                                 } else {
                                     armorAttr?.baseValue = 3.0 // Leather Chest
                                 }
                             }
                             
                             plugin.animationSystem.attachModel(mob, modelName)
                             plugin.animationSystem.configureProcedural(mob, procConfig)
                             
                             // Initial animation
                             plugin.animationSystem.playAnimation(mob, "spawn", loop = false)
                             
                             // Queue idle/walk logic (handled by AnimationListener automatically)
                         }
                     }
                     
                     aliveMobs.add(mob.uniqueId)
                     spawnedEntities.add(mob.uniqueId) // Track for global cleanup
                     spawned++
                     
                     // Visuals
                     center.world.spawnParticle(theme.particle ?: Particle.CLOUD, spawnLoc, 10, 0.5, 0.5, 0.5, 0.05)
                 }
            }
        }.runTaskTimer(plugin, 0L, 20L)
    }

    private fun equipMob(entity: org.bukkit.entity.LivingEntity, diff: Int, isBoss: Boolean) {
        // Stats
        val baseHp = if (isBoss) 150.0 else 20.0
        val hpMult = 1.0 + (diff * 0.4) // Lv 1 = 1.4x, Lv 5 = 3.0x
        val finalHp = baseHp * hpMult
        
        entity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH)?.baseValue = finalHp
        entity.health = finalHp
        
        val dmgMult = 1.0 + (diff * plugin.configManager.dungeonMobDamageScaling)
        entity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE)?.baseValue = 4.0 * dmgMult
        
        // Armor
        // Lv 1-2: Leather/Chain, Lv 3-4: Iron, Lv 5: Diamond
        val equip = entity.equipment ?: return
        
        if (isBoss) {
            entity.customName(Component.text("☠ ${theme.name} Boss ☠", NamedTextColor.RED))
            entity.isCustomNameVisible = true
            // Boss Gear
            equip.helmet = org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND_HELMET)
            equip.chestplate = org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND_CHESTPLATE)
            // Add enchants...
        } else {
            // Minion Gear
            if (diff >= 3) {
                 equip.chestplate = org.bukkit.inventory.ItemStack(org.bukkit.Material.IRON_CHESTPLATE)
                 equip.setItemInMainHand(org.bukkit.inventory.ItemStack(org.bukkit.Material.IRON_SWORD))
            } else {
                 equip.chestplate = org.bukkit.inventory.ItemStack(org.bukkit.Material.LEATHER_CHESTPLATE)
                 equip.setItemInMainHand(org.bukkit.inventory.ItemStack(org.bukkit.Material.STONE_SWORD))
            }
        }
        
        // Drop chances (don't drop scaling gear usually)
        equip.itemInMainHandDropChance = 0.05f
        equip.itemInOffHandDropChance = 0.05f
        equip.helmetDropChance = 0.05f
        equip.chestplateDropChance = 0.05f
        equip.leggingsDropChance = 0.05f
        equip.bootsDropChance = 0.05f
    }
    
    private fun fillLoot(chest: Chest, diff: Int) {
        val inv = chest.inventory
        val rand = Random.Default
        
        // Always: Coins/Gold
        inv.addItem(ItemStack(Material.GOLD_NUGGET, rand.nextInt(3, 10 + (diff * 5))))
        
        // Food
        if (rand.nextBoolean()) inv.addItem(ItemStack(Material.COOKED_BEEF, rand.nextInt(1, 5)))
        
        // Rare: Diamonds/Iron
        if (rand.nextDouble() < 0.3 + (diff * 0.1)) {
            inv.addItem(ItemStack(Material.IRON_INGOT, rand.nextInt(1, 4)))
        }
        // Buff: Emeralds/Lapis
        if (rand.nextDouble() < 0.2 + (diff * 0.1)) {
            inv.addItem(ItemStack(Material.EMERALD, rand.nextInt(2, 6)))
        }
        if (rand.nextDouble() < 0.2) {
            inv.addItem(ItemStack(Material.LAPIS_LAZULI, rand.nextInt(4, 12)))
        }
        
        if (diff >= 3 && rand.nextDouble() < 0.1 + (diff * 0.05)) {
            inv.addItem(ItemStack(Material.DIAMOND, rand.nextInt(1, 2 + (diff/2))))
        }
        
        // Boss Loot
        if (diff >= 5 && rand.nextDouble() < 0.2) {
            inv.addItem(ItemStack(Material.NETHERITE_SCRAP))
        }
    }
    
    fun finishDungeon(success: Boolean) {
        val onlinePlayers = players.mapNotNull { plugin.server.getPlayer(it) }
        onlinePlayers.forEach { 
            // Fire completion event
            plugin.server.pluginManager.callEvent(com.projectatlas.events.DungeonCompleteEvent(it, difficulty, success))
            
            it.hideBossBar(bossBar)
            if (success) {
                it.sendMessage(Component.text("🏆 Dungeon Completed! 🏆", NamedTextColor.GOLD))
                plugin.economyManager.deposit(it.uniqueId, plugin.configManager.dungeonCompletionReward)
                // Reduce Global Threat
                plugin.globalThreatManager.onDungeonComplete(true)
            } else {
                it.sendMessage(Component.text("☠ Dungeon Failed", NamedTextColor.RED))
            }
            
            // Teleport out after 10s
             plugin.server.scheduler.runTaskLater(plugin, Runnable {
                 plugin.dungeonManager.leaveDungeon(it)
             }, 200L)
        }
    }
}
