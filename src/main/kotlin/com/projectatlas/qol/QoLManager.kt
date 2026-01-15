package com.projectatlas.qol

import com.projectatlas.AtlasPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import net.kyori.adventure.bossbar.BossBar
import org.bukkit.*
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.*
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.scheduler.BukkitRunnable
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Quality of Life Manager - Convenience features without making the game easier
 */
class QoLManager(private val plugin: AtlasPlugin) : Listener {

    // Player preferences are now managed by IdentityManager (Settings)
    // Removed legacy maps: damageNumbersEnabled, scoreboardEnabled
    
    // Death location tracking
    private val deathLocations = ConcurrentHashMap<UUID, Location>()
    private val deathTimestamps = ConcurrentHashMap<UUID, Long>()
    
    // AFK tracking
    private val lastActivity = ConcurrentHashMap<UUID, Long>()
    private val afkPlayers = ConcurrentHashMap.newKeySet<UUID>()
    
    // Kill stats
    private val killStats = ConcurrentHashMap<UUID, MutableMap<String, Int>>()
    private val pvpKills = ConcurrentHashMap<UUID, Int>()
    private val pvpDeaths = ConcurrentHashMap<UUID, Int>()
    
    // Combo tracking
    private val comboTargets = ConcurrentHashMap<UUID, UUID>()
    private val comboCounts = ConcurrentHashMap<UUID, Int>()
    private val comboTimestamps = ConcurrentHashMap<UUID, Long>()
    
    init {
        // AFK checker (runs every 30 seconds)
        object : BukkitRunnable() {
            override fun run() {
                checkAFK()
            }
        }.runTaskTimer(plugin, 600L, 600L)
        
        // Scoreboard updater (runs every second)
        object : BukkitRunnable() {
            override fun run() {
                updateScoreboards()
            }
        }.runTaskTimer(plugin, 20L, 20L)
    }

    // ═══════════════════════════════════════════════════════════════
    // DEATH COORDINATES
    // ═══════════════════════════════════════════════════════════════
    
    private val deathCompassKey = NamespacedKey(plugin, "death_compass")
    
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.entity
        val location = player.location
        
        // Skip death compass for dungeon deaths (inventory is preserved)
        val isDungeonDeath = location.world.name == "world_the_end" || event.keepInventory
        if (isDungeonDeath) {
            // Track PvP deaths only
            if (event.entity.killer != null) {
                pvpDeaths[player.uniqueId] = (pvpDeaths[player.uniqueId] ?: 0) + 1
                val killer = event.entity.killer!!
                pvpKills[killer.uniqueId] = (pvpKills[killer.uniqueId] ?: 0) + 1
            }
            return // Don't track death location for dungeons
        }
        
        // Only track if player dropped items
        val hasDrops = event.drops.isNotEmpty()
        
        deathLocations[player.uniqueId] = location
        deathTimestamps[player.uniqueId] = System.currentTimeMillis()
        
        // Track PvP deaths
        if (event.entity.killer != null) {
            pvpDeaths[player.uniqueId] = (pvpDeaths[player.uniqueId] ?: 0) + 1
            val killer = event.entity.killer!!
            pvpKills[killer.uniqueId] = (pvpKills[killer.uniqueId] ?: 0) + 1
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        val deathLoc = deathLocations[player.uniqueId] ?: return
        val timestamp = deathTimestamps[player.uniqueId] ?: return
        
        // Show death coordinates (delayed to appear after respawn)
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            player.sendMessage(Component.empty())
            player.sendMessage(Component.text("═══════════════════════════════", NamedTextColor.GRAY))
            player.sendMessage(Component.text("  ☠ You died at:", NamedTextColor.RED))
            player.sendMessage(Component.text("  X: ${deathLoc.blockX}  Y: ${deathLoc.blockY}  Z: ${deathLoc.blockZ}", NamedTextColor.YELLOW))
            player.sendMessage(Component.text("  World: ${deathLoc.world?.name}", NamedTextColor.GRAY))
            player.sendMessage(Component.empty())
            
            // Set compass target to death location
            player.compassTarget = deathLoc
            
            // Give Death Compass item
            val deathCompass = createDeathCompass(deathLoc)
            player.inventory.addItem(deathCompass)
            
            player.sendMessage(Component.text("  📍 You received a Death Compass!", NamedTextColor.AQUA))
            player.sendMessage(Component.text("  Right-click it to track your death location.", NamedTextColor.GRAY))
            player.sendMessage(Component.text("═══════════════════════════════", NamedTextColor.GRAY))
            player.sendMessage(Component.empty())
            
            player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.5f)
        }, 5L)
        
        // Clear death location after 10 minutes
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (deathTimestamps[player.uniqueId] == timestamp) {
                deathLocations.remove(player.uniqueId)
                deathTimestamps.remove(player.uniqueId)
                if (player.isOnline) {
                    player.compassTarget = player.world.spawnLocation
                    player.sendMessage(Component.text("☠ Death compass expired. The trail has gone cold.", NamedTextColor.GRAY))
                }
            }
        }, 12000L) // 10 minutes
    }
    
    private fun createDeathCompass(deathLoc: Location): ItemStack {
        val compass = ItemStack(Material.COMPASS)
        val meta = compass.itemMeta!!
        
        meta.displayName(Component.text("☠ Death Compass", NamedTextColor.RED, TextDecoration.BOLD))
        meta.lore(listOf(
            Component.empty(),
            Component.text("Your items await...", NamedTextColor.GRAY, TextDecoration.ITALIC),
            Component.empty(),
            Component.text("Death Location:", NamedTextColor.YELLOW),
            Component.text("X: ${deathLoc.blockX}  Y: ${deathLoc.blockY}  Z: ${deathLoc.blockZ}", NamedTextColor.WHITE),
            Component.text("World: ${deathLoc.world?.name}", NamedTextColor.GRAY),
            Component.empty(),
            Component.text("Right-click to update compass target", NamedTextColor.DARK_GRAY),
            Component.text("Expires in 10 minutes", NamedTextColor.DARK_RED)
        ))
        
        // Store death location in item
        meta.persistentDataContainer.set(deathCompassKey, PersistentDataType.STRING, 
            "${deathLoc.world?.name}:${deathLoc.blockX}:${deathLoc.blockY}:${deathLoc.blockZ}")
        
        compass.itemMeta = meta
        return compass
    }
    
    @EventHandler
    fun onDeathCompassUse(event: PlayerInteractEvent) {
        val item = event.item ?: return
        if (item.type != Material.COMPASS) return
        
        val meta = item.itemMeta ?: return
        val locData = meta.persistentDataContainer.get(deathCompassKey, PersistentDataType.STRING) ?: return
        
        // It's a death compass!
        val parts = locData.split(":")
        if (parts.size < 4) return
        
        val worldName = parts[0]
        val x = parts[1].toIntOrNull() ?: return
        val y = parts[2].toIntOrNull() ?: return
        val z = parts[3].toIntOrNull() ?: return
        
        val world = plugin.server.getWorld(worldName) ?: return
        val deathLoc = Location(world, x.toDouble(), y.toDouble(), z.toDouble())
        
        val player = event.player
        player.compassTarget = deathLoc
        
        val distance = player.location.distance(deathLoc).toInt()
        player.sendMessage(Component.text("☠ Compass updated! Death location is $distance blocks away.", NamedTextColor.RED))
        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.5f, 1.2f)
    }

    // ═══════════════════════════════════════════════════════════════
    // TOOL DURABILITY WARNING
    // ═══════════════════════════════════════════════════════════════
    
    @EventHandler
    fun onItemDamage(event: PlayerItemDamageEvent) {
        val item = event.item
        val meta = item.itemMeta as? org.bukkit.inventory.meta.Damageable ?: return
        
        val maxDurability = item.type.maxDurability.toInt()
        if (maxDurability <= 0) return
        
        val currentDurability = maxDurability - meta.damage
        val percentage = (currentDurability.toDouble() / maxDurability) * 100
        
        // Warn at 10%
        if (percentage <= 10 && percentage > 5) {
            event.player.sendMessage(Component.text("⚠ ${item.type.name} is at ${percentage.toInt()}% durability!", NamedTextColor.YELLOW))
            event.player.playSound(event.player.location, Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 0.8f)
        }
        // Critical at 5%
        else if (percentage <= 5) {
            event.player.sendMessage(Component.text("⚠ ${item.type.name} is about to break!", NamedTextColor.RED))
            event.player.playSound(event.player.location, Sound.BLOCK_ANVIL_LAND, 0.3f, 1.5f)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // DAMAGE NUMBERS (TOGGLEABLE)
    // ═══════════════════════════════════════════════════════════════
    
    fun toggleDamageNumbers(player: Player) {
        val profile = plugin.identityManager.getPlayer(player.uniqueId) ?: return
        val current = profile.getSetting("damage_indicators", true)
        val newState = !current
        profile.setSetting("damage_indicators", newState)
        plugin.identityManager.saveProfile(player.uniqueId)
        
        player.sendMessage(Component.text(
            if (newState) "✓ Damage numbers enabled" else "✗ Damage numbers disabled",
            if (newState) NamedTextColor.GREEN else NamedTextColor.RED
        ))
    }
    
    fun isDamageNumbersEnabled(player: Player): Boolean {
        val profile = plugin.identityManager.getPlayer(player.uniqueId) ?: return true
        return profile.getSetting("damage_indicators", true)
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageByEntityEvent) {
        val damager = when (val d = event.damager) {
            is Player -> d
            is org.bukkit.entity.Projectile -> d.shooter as? Player
            else -> null
        } ?: return
        
        if (!isDamageNumbersEnabled(damager)) return
        
        val target = event.entity as? LivingEntity ?: return
        val damage = event.finalDamage
        
        // Display floating damage number
        showDamageNumber(damager, target.location.add(0.0, target.height + 0.5, 0.0), damage)
        
        // Combo tracking
        updateCombo(damager, target)
    }
    
    private fun showDamageNumber(viewer: Player, location: Location, damage: Double) {
        val damageText = String.format("%.1f", damage)
        val color = when {
            damage >= 10 -> NamedTextColor.RED
            damage >= 5 -> NamedTextColor.GOLD
            else -> NamedTextColor.YELLOW
        }
        
        // Spawn armor stand with damage text (brief display)
        val world = location.world ?: return
        val displayLoc = location.clone().add(
            (Math.random() - 0.5) * 0.5,
            Math.random() * 0.3,
            (Math.random() - 0.5) * 0.5
        )
        
        // Use hologram-style display with armor stand
        val armorStand = world.spawn(displayLoc, org.bukkit.entity.ArmorStand::class.java) { stand ->
            stand.isVisible = false
            stand.isMarker = true
            stand.isSmall = true
            stand.setGravity(false)
            stand.isCustomNameVisible = true
            stand.customName(Component.text("❤ $damageText", color, TextDecoration.BOLD))
        }
        
        // Animate upward and remove
        object : BukkitRunnable() {
            var ticks = 0
            override fun run() {
                if (ticks >= 20 || !armorStand.isValid) {
                    armorStand.remove()
                    cancel()
                    return
                }
                armorStand.teleport(armorStand.location.add(0.0, 0.05, 0.0))
                ticks++
            }
        }.runTaskTimer(plugin, 0L, 1L)
    }
    
    private fun updateCombo(damager: Player, target: LivingEntity) {
        val now = System.currentTimeMillis()
        val lastTarget = comboTargets[damager.uniqueId]
        val lastTime = comboTimestamps[damager.uniqueId] ?: 0L
        
        if (lastTarget == target.uniqueId && now - lastTime < 3000) {
            // Continue combo
            val newCombo = (comboCounts[damager.uniqueId] ?: 0) + 1
            comboCounts[damager.uniqueId] = newCombo
            
            if (newCombo >= 3) {
                damager.sendActionBar(Component.text("⚔ COMBO x$newCombo ⚔", NamedTextColor.GOLD))
            }
        } else {
            // Reset combo
            comboCounts[damager.uniqueId] = 1
        }
        
        comboTargets[damager.uniqueId] = target.uniqueId
        comboTimestamps[damager.uniqueId] = now
    }

    // ═══════════════════════════════════════════════════════════════
    // @PLAYER PING SYSTEM
    // ═══════════════════════════════════════════════════════════════
    
    @EventHandler
    fun onChat(event: AsyncPlayerChatEvent) {
        val message = event.message
        
        // Find @mentions
        val mentionPattern = Regex("@(\\w+)")
        val mentions = mentionPattern.findAll(message)
        
        mentions.forEach { match ->
            val targetName = match.groupValues[1]
            val target = plugin.server.getPlayerExact(targetName)
            
            if (target != null && target.isOnline) {
                // Play ping sound to mentioned player
                plugin.server.scheduler.runTask(plugin, Runnable {
                    target.playSound(target.location, Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.2f)
                    target.sendMessage(Component.text("📢 ${event.player.name} mentioned you!", NamedTextColor.AQUA))
                })
            }
        }
        
        // Add timestamp to message format if player wants it (stored in preferences)
        val time = java.time.LocalTime.now()
        val timestamp = String.format("[%02d:%02d] ", time.hour, time.minute)
        event.format = "§7$timestamp§r" + event.format
    }

    // ═══════════════════════════════════════════════════════════════
    // INVENTORY SORTING
    // ═══════════════════════════════════════════════════════════════
    
    fun sortInventory(player: Player) {
        val inventory = player.inventory
        val items = mutableListOf<ItemStack>()
        
        // Collect all items (excluding hotbar and armor)
        for (i in 9 until 36) {
            val item = inventory.getItem(i)
            if (item != null && item.type != Material.AIR) {
                items.add(item.clone())
                inventory.setItem(i, null)
            }
        }
        
        // Sort by type, then by amount
        items.sortWith(compareBy({ it.type.name }, { -it.amount }))
        
        // Stack similar items
        val stacked = mutableListOf<ItemStack>()
        for (item in items) {
            val existing = stacked.find { it.isSimilar(item) && it.amount < it.maxStackSize }
            if (existing != null) {
                val space = existing.maxStackSize - existing.amount
                val toAdd = minOf(space, item.amount)
                existing.amount += toAdd
                item.amount -= toAdd
                if (item.amount > 0) {
                    stacked.add(item)
                }
            } else {
                stacked.add(item)
            }
        }
        
        // Place back
        var slot = 9
        for (item in stacked) {
            if (slot >= 36) break
            inventory.setItem(slot, item)
            slot++
        }
        
        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.5f, 1.2f)
        player.sendMessage(Component.text("✓ Inventory sorted!", NamedTextColor.GREEN))
    }

    // ═══════════════════════════════════════════════════════════════
    // AFK SYSTEM
    // ═══════════════════════════════════════════════════════════════
    
    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        updateActivity(event.player)
    }
    
    @EventHandler
    fun onPlayerChat(event: AsyncPlayerChatEvent) {
        plugin.server.scheduler.runTask(plugin, Runnable {
            updateActivity(event.player)
        })
    }
    
    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        updateActivity(event.player)
    }
    
    private fun updateActivity(player: Player) {
        val wasAFK = afkPlayers.contains(player.uniqueId)
        lastActivity[player.uniqueId] = System.currentTimeMillis()
        
        if (wasAFK) {
            afkPlayers.remove(player.uniqueId)
            plugin.server.broadcast(Component.text("${player.name} is no longer AFK", NamedTextColor.GRAY))
            player.playerListName(Component.text(player.name))
        }
    }
    
    private fun checkAFK() {
        val now = System.currentTimeMillis()
        val afkThreshold = 5 * 60 * 1000L // 5 minutes
        
        plugin.server.onlinePlayers.forEach { player ->
            val last = lastActivity[player.uniqueId] ?: now
            
            if (now - last >= afkThreshold && !afkPlayers.contains(player.uniqueId)) {
                afkPlayers.add(player.uniqueId)
                plugin.server.broadcast(Component.text("${player.name} is now AFK", NamedTextColor.GRAY))
                player.playerListName(Component.text("[AFK] ${player.name}", NamedTextColor.GRAY))
            }
        }
    }
    
    fun isAFK(player: Player): Boolean = afkPlayers.contains(player.uniqueId)

    // ═══════════════════════════════════════════════════════════════
    // SCOREBOARD TOGGLE
    // ═══════════════════════════════════════════════════════════════
    
    fun toggleScoreboard(player: Player) {
        val profile = plugin.identityManager.getPlayer(player.uniqueId) ?: return
        val current = profile.getSetting("scoreboard", true)
        val newState = !current
        profile.setSetting("scoreboard", newState)
        plugin.identityManager.saveProfile(player.uniqueId)
        
        if (newState) {
            player.sendMessage(Component.text("✓ Scoreboard enabled", NamedTextColor.GREEN))
        } else {
            player.scoreboard = Bukkit.getScoreboardManager().newScoreboard
            player.sendMessage(Component.text("✗ Scoreboard disabled", NamedTextColor.RED))
        }
    }
    
    private fun updateScoreboards() {
        plugin.server.onlinePlayers.forEach { player ->
            val profile = plugin.identityManager.getPlayer(player.uniqueId) ?: return@forEach
            if (!profile.getSetting("scoreboard", true)) return@forEach
            
            var scoreboard = player.scoreboard
            if (scoreboard == Bukkit.getScoreboardManager().mainScoreboard) {
                scoreboard = Bukkit.getScoreboardManager().newScoreboard
                player.scoreboard = scoreboard
            }
            
            val objective = scoreboard.getObjective("atlas") 
                ?: scoreboard.registerNewObjective("atlas", "dummy", 
                    Component.text("⚔ Atlas ⚔", NamedTextColor.GOLD, TextDecoration.BOLD))
            
            if (objective.displaySlot != org.bukkit.scoreboard.DisplaySlot.SIDEBAR) {
                objective.displaySlot = org.bukkit.scoreboard.DisplaySlot.SIDEBAR
            }
            
            // Clear previous entries to avoid ghost lines
            scoreboard.entries.forEach { scoreboard.resetScores(it) }
            
            var line = 15
            
            // Balance
            objective.getScore("§6Balance: §f${String.format("%.1f", profile.balance)}g").score = line--
            
            // Reputation
            objective.getScore("§bReputation: §f${profile.reputation}").score = line--
            
            // City
            val cityName = profile.cityId?.let { plugin.cityManager.getCity(it)?.name } ?: "Nomad"
            objective.getScore("§aCity: §f$cityName").score = line--
            
            // Blank line
            objective.getScore("§r").score = line--
            
            // Active Quest
            val activeQuest = plugin.questManager.getActiveQuest(player)
            if (activeQuest != null) {
                val questName = activeQuest.quest.name
                val questProgress = activeQuest.progress
                val questTarget = when (val obj = activeQuest.quest.objective) {
                    is com.projectatlas.quests.QuestObjective.KillMobs -> obj.count
                    is com.projectatlas.quests.QuestObjective.KillHorde -> obj.waveCount * obj.mobsPerWave
                    is com.projectatlas.quests.QuestObjective.FetchItem -> obj.count
                    is com.projectatlas.quests.QuestObjective.KillAnyMobs -> obj.count
                    is com.projectatlas.quests.QuestObjective.MineBlocks -> obj.count
                    is com.projectatlas.quests.QuestObjective.CraftItems -> obj.count
                    is com.projectatlas.quests.QuestObjective.FishItems -> obj.count
                    is com.projectatlas.quests.QuestObjective.TameAnimals -> obj.count
                    else -> 1
                }
                objective.getScore("§eQuest: §f$questName").score = line--
                objective.getScore("§7Progress: $questProgress/$questTarget").score = line--
            }
            
            // Blank line
            objective.getScore("§r§r").score = line--
            
            // PvP Stats
            val kills = pvpKills[player.uniqueId] ?: 0
            val deaths = pvpDeaths[player.uniqueId] ?: 0
            objective.getScore("§cK/D: §f$kills/$deaths").score = line--
            
            // Bounty
            val bounty = plugin.bountyManager.getTotalBounty(player.uniqueId)
            if (bounty > 0) {
                objective.getScore("§4Bounty: §c${bounty}g").score = line--
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // KILL STATISTICS
    // ═══════════════════════════════════════════════════════════════
    
    @EventHandler
    fun onMobKill(event: org.bukkit.event.entity.EntityDeathEvent) {
        val killer = event.entity.killer ?: return
        val mobType = event.entity.type.name
        
        val stats = killStats.getOrPut(killer.uniqueId) { mutableMapOf() }
        stats[mobType] = (stats[mobType] ?: 0) + 1
    }
    
    fun getKillStats(player: Player): Map<String, Int> {
        return killStats[player.uniqueId]?.toMap() ?: emptyMap()
    }
    
    fun getPvPStats(player: Player): Pair<Int, Int> {
        return Pair(pvpKills[player.uniqueId] ?: 0, pvpDeaths[player.uniqueId] ?: 0)
    }

    // ═══════════════════════════════════════════════════════════════
    // HOME/BED REMINDER
    // ═══════════════════════════════════════════════════════════════
    
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        lastActivity[player.uniqueId] = System.currentTimeMillis()
        
        // Show bed location on join
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            val bedLoc = player.bedSpawnLocation
            if (bedLoc != null) {
                player.sendMessage(Component.text("🛏 Your bed is at: ${bedLoc.blockX}, ${bedLoc.blockY}, ${bedLoc.blockZ}", NamedTextColor.GRAY))
            } else {
                player.sendMessage(Component.text("🛏 You don't have a bed set!", NamedTextColor.YELLOW))
            }
        }, 40L)
    }

    // ═══════════════════════════════════════════════════════════════
    // QUICK STACK TO CHESTS
    // ═══════════════════════════════════════════════════════════════
    
    fun quickStack(player: Player) {
        val nearbyChests = player.location.world?.getNearbyEntities(player.location, 5.0, 5.0, 5.0)
            ?.mapNotNull { (it.location.block.state as? org.bukkit.block.Container)?.inventory }
            ?: return
        
        if (nearbyChests.isEmpty()) {
            player.sendMessage(Component.text("No containers nearby!", NamedTextColor.RED))
            return
        }
        
        var deposited = 0
        val inventory = player.inventory
        
        for (i in 9 until 36) {
            val item = inventory.getItem(i) ?: continue
            
            for (chest in nearbyChests) {
                // Check if chest already has this item type
                if (chest.contains(item.type)) {
                    val remaining = chest.addItem(item)
                    if (remaining.isEmpty()) {
                        inventory.setItem(i, null)
                        deposited++
                        break
                    } else {
                        inventory.setItem(i, remaining.values.first())
                    }
                }
            }
        }
        
        if (deposited > 0) {
            player.playSound(player.location, Sound.BLOCK_CHEST_CLOSE, 0.5f, 1.2f)
            player.sendMessage(Component.text("✓ Deposited $deposited stacks to nearby chests!", NamedTextColor.GREEN))
        } else {
            player.sendMessage(Component.text("No matching items to deposit.", NamedTextColor.GRAY))
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // CLEANUP ON QUIT
    // ═══════════════════════════════════════════════════════════════
    
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val uuid = event.player.uniqueId
        afkPlayers.remove(uuid)
        lastActivity.remove(uuid)
        comboTargets.remove(uuid)
        comboCounts.remove(uuid)
        comboTimestamps.remove(uuid)
    }
}
