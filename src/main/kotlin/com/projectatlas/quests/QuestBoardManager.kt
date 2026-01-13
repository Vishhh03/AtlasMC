package com.projectatlas.quests

import com.projectatlas.AtlasPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.*
import org.bukkit.block.Barrel
import org.bukkit.block.Sign
import org.bukkit.block.sign.Side
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * World-based Quest Board System
 * - Quest boards spawn in the world
 * - Players interact with boards to get quests
 * - Turn-in chests next to boards accept quest items
 * - Items disappear on turn-in (consumed)
 */
class QuestBoardManager(private val plugin: AtlasPlugin) : Listener {

    private val questBoardKey = NamespacedKey(plugin, "quest_board")
    private val turnInChestKey = NamespacedKey(plugin, "quest_turnin")
    private val questIdKey = NamespacedKey(plugin, "quest_id")
    
    // Track board locations -> Quest offered
    private val boardQuests = ConcurrentHashMap<Location, Quest>()
    
    // Track player's quest source location (where to turn in)
    private val questSources = ConcurrentHashMap<UUID, Location>()
    
    /**
     * Spawn a quest board at a location with a random quest
     */
    fun spawnQuestBoard(location: Location, difficulty: Difficulty? = null): Boolean {
        val world = location.world ?: return false
        
        // Get a random quest based on difficulty (or any)
        val quest = if (difficulty != null) {
            plugin.questManager.getQuestByDifficulty(difficulty)
        } else {
            plugin.questManager.getQuestTemplates().random()
        } ?: return false
        
        // Place the quest board (sign on a post)
        val signLoc = location.clone()
        signLoc.block.type = Material.OAK_SIGN
        
        // Set sign text
        val sign = signLoc.block.state as? Sign ?: return false
        val side = sign.getSide(Side.FRONT)
        side.line(0, Component.text("═══════════", NamedTextColor.GOLD))
        side.line(1, Component.text("QUEST", NamedTextColor.RED, TextDecoration.BOLD))
        side.line(2, Component.text(quest.name.take(15), NamedTextColor.DARK_AQUA))
        side.line(3, Component.text("Right-Click", NamedTextColor.GRAY))
        
        // Store quest data in sign
        sign.persistentDataContainer.set(questBoardKey, PersistentDataType.BYTE, 1)
        sign.persistentDataContainer.set(questIdKey, PersistentDataType.STRING, quest.id)
        sign.update()
        
        // Place turn-in barrel next to sign
        val barrelLoc = location.clone().add(1.0, 0.0, 0.0)
        barrelLoc.block.type = Material.BARREL
        val barrel = barrelLoc.block.state as? Barrel ?: return false
        barrel.customName(Component.text("Quest Turn-In", NamedTextColor.GOLD))
        barrel.persistentDataContainer.set(turnInChestKey, PersistentDataType.BYTE, 1)
        barrel.persistentDataContainer.set(questIdKey, PersistentDataType.STRING, quest.id)
        barrel.update()
        
        // Track
        boardQuests[signLoc] = quest
        
        plugin.logger.info("Quest board spawned: ${quest.name} at ${location.blockX}, ${location.blockY}, ${location.blockZ}")
        return true
    }
    
    /**
     * Handle player clicking on quest board
     */
    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        val block = event.clickedBlock ?: return
        val player = event.player
        
        // Check if it's a quest board (sign)
        if (block.state is Sign) {
            val sign = block.state as Sign
            val isQuestBoard = sign.persistentDataContainer.get(questBoardKey, PersistentDataType.BYTE) == 1.toByte()
            if (!isQuestBoard) return
            
            event.isCancelled = true
            
            val questId = sign.persistentDataContainer.get(questIdKey, PersistentDataType.STRING) ?: return
            val quest = plugin.questManager.getQuestTemplates().find { it.id == questId } ?: return
            
            // Check if player already has a quest
            if (plugin.questManager.hasActiveQuest(player)) {
                player.sendMessage(Component.text("You already have an active quest! Complete or abandon it first.", NamedTextColor.RED))
                player.sendMessage(Component.text("Use /atlas quest abandon to cancel.", NamedTextColor.GRAY))
                return
            }
            
            // Accept the quest
            showQuestDetails(player, quest, block.location)
        }
    }
    
    private fun showQuestDetails(player: Player, quest: Quest, boardLocation: Location) {
        player.sendMessage(Component.empty())
        player.sendMessage(Component.text("═══════════════════════════════════", NamedTextColor.GOLD))
        player.sendMessage(Component.text("  📜 ${quest.name}", NamedTextColor.AQUA, TextDecoration.BOLD))
        player.sendMessage(Component.text("  ${quest.difficulty.displayName}", getDifficultyColor(quest.difficulty)))
        player.sendMessage(Component.empty())
        player.sendMessage(Component.text("  \"${quest.description}\"", NamedTextColor.WHITE, TextDecoration.ITALIC))
        player.sendMessage(Component.empty())
        player.sendMessage(Component.text("  Objective: ", NamedTextColor.YELLOW).append(
            Component.text(getObjectiveText(quest.objective), NamedTextColor.WHITE)
        ))
        player.sendMessage(Component.text("  Reward: ${quest.reward.toInt()}g", NamedTextColor.GREEN))
        if (quest.timeLimitSeconds != null) {
            player.sendMessage(Component.text("  Time Limit: ${quest.timeLimitSeconds / 60}m", NamedTextColor.RED))
        }
        player.sendMessage(Component.empty())
        player.sendMessage(Component.text("  Hint: ${quest.hint}", NamedTextColor.GRAY, TextDecoration.ITALIC))
        player.sendMessage(Component.empty())
        player.sendMessage(Component.text("  [CLICK THE SIGN AGAIN TO ACCEPT]", NamedTextColor.GREEN, TextDecoration.BOLD))
        player.sendMessage(Component.text("═══════════════════════════════════", NamedTextColor.GOLD))
        player.sendMessage(Component.empty())
        
        // Store pending acceptance
        pendingAcceptance[player.uniqueId] = Pair(quest, boardLocation)
        
        player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f)
    }
    
    private val pendingAcceptance = ConcurrentHashMap<UUID, Pair<Quest, Location>>()
    
    @EventHandler
    fun onInteractAccept(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        val block = event.clickedBlock ?: return
        val player = event.player
        
        val pending = pendingAcceptance[player.uniqueId] ?: return
        
        // Check if clicking same sign
        if (block.state is Sign) {
            val sign = block.state as Sign
            val questId = sign.persistentDataContainer.get(questIdKey, PersistentDataType.STRING)
            if (questId == pending.first.id) {
                event.isCancelled = true
                pendingAcceptance.remove(player.uniqueId)
                
                // Accept quest
                plugin.questManager.startQuest(player, pending.first)
                questSources[player.uniqueId] = pending.second
                
                player.sendMessage(Component.text("✓ Quest Accepted: ${pending.first.name}", NamedTextColor.GREEN, TextDecoration.BOLD))
                player.sendMessage(Component.text("Return here to turn in when complete!", NamedTextColor.YELLOW))
                player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f)
            }
        }
    }
    
    /**
     * Handle turn-in chest closing - check for quest items and consume them
     */
    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        val holder = event.inventory.holder
        
        // Check if it's a quest turn-in barrel
        if (holder !is Barrel) return
        val barrel = holder
        
        val isTurnIn = barrel.persistentDataContainer.get(turnInChestKey, PersistentDataType.BYTE) == 1.toByte()
        if (!isTurnIn) return
        
        // Get player's active quest
        val activeQuest = plugin.questManager.getActiveQuest(player) ?: return
        val quest = activeQuest.quest
        val objective = quest.objective
        
        // Only handle FetchItem quests for chest turn-in
        if (objective !is QuestObjective.FetchItem) {
            // Return items if not a fetch quest
            returnItemsToPlayer(player, barrel)
            player.sendMessage(Component.text("This quest doesn't require item turn-in!", NamedTextColor.YELLOW))
            return
        }
        
        // Check if barrel contains the required items
        val requiredMaterial = objective.material
        val requiredCount = objective.count
        
        var foundCount = 0
        val itemsToRemove = mutableListOf<Int>()
        
        for ((slot, item) in barrel.inventory.withIndex()) {
            if (item != null && item.type == requiredMaterial) {
                foundCount += item.amount
                itemsToRemove.add(slot)
            }
        }
        
        if (foundCount >= requiredCount) {
            // SUCCESS! Consume items and complete quest
            var remaining = requiredCount
            for (slot in itemsToRemove) {
                val item = barrel.inventory.getItem(slot) ?: continue
                if (item.amount <= remaining) {
                    remaining -= item.amount
                    barrel.inventory.setItem(slot, null) // Consume entirely
                } else {
                    item.amount -= remaining
                    barrel.inventory.setItem(slot, item)
                    remaining = 0
                }
                if (remaining <= 0) break
            }
            
            // Return excess items
            returnItemsToPlayer(player, barrel)
            
            // Complete the quest
            activeQuest.progress = requiredCount
            plugin.questManager.completeQuestManual(player)
            
            player.sendMessage(Component.text("✓ Items delivered! Quest complete!", NamedTextColor.GREEN, TextDecoration.BOLD))
            player.playSound(player.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f)
            
            questSources.remove(player.uniqueId)
        } else {
            // Not enough items - return them
            returnItemsToPlayer(player, barrel)
            player.sendMessage(Component.text("Need $requiredCount ${requiredMaterial.name}, you placed $foundCount.", NamedTextColor.RED))
        }
    }
    
    private fun returnItemsToPlayer(player: Player, barrel: Barrel) {
        for (item in barrel.inventory.contents) {
            if (item != null && item.type != Material.AIR) {
                val leftover = player.inventory.addItem(item)
                if (leftover.isNotEmpty()) {
                    // Drop on ground if inventory full
                    leftover.values.forEach { player.world.dropItemNaturally(player.location, it) }
                }
            }
        }
        barrel.inventory.clear()
    }
    
    private fun getObjectiveText(objective: QuestObjective): String {
        return when (objective) {
            is QuestObjective.KillMobs -> "Kill ${objective.count} ${objective.mobType.name.lowercase().replace("_", " ")}s"
            is QuestObjective.KillAnyMobs -> "Kill ${objective.count} hostile mobs"
            is QuestObjective.FetchItem -> "Collect ${objective.count} ${objective.material.name.lowercase().replace("_", " ")}"
            is QuestObjective.FindNPC -> "Find ${objective.npcName}"
            is QuestObjective.VisitBiome -> "Visit the ${objective.biomeName.replace("_", " ")}"
            is QuestObjective.TravelDistance -> "Travel ${objective.blocks} blocks"
            is QuestObjective.SurviveTime -> "Survive ${objective.seconds / 60} minutes"
            is QuestObjective.MineBlocks -> "Mine ${objective.count} ${objective.material.name.lowercase().replace("_", " ")}"
            is QuestObjective.FishItems -> "Catch ${objective.count} fish"
            is QuestObjective.TameAnimals -> "Tame ${objective.count} animals"
            is QuestObjective.TradeWithVillager -> "Complete ${objective.count} villager trades"
            is QuestObjective.CraftItems -> "Craft ${objective.count} ${objective.material.name.lowercase().replace("_", " ")}"
            is QuestObjective.ReachLocation -> "Reach ${objective.locationName}"
            is QuestObjective.KillHorde -> "Survive ${objective.waveCount} waves"
        }
    }
    
    private fun getDifficultyColor(difficulty: Difficulty): NamedTextColor {
        return when (difficulty) {
            Difficulty.EASY -> NamedTextColor.GREEN
            Difficulty.MEDIUM -> NamedTextColor.YELLOW
            Difficulty.HARD -> NamedTextColor.RED
            Difficulty.NIGHTMARE -> NamedTextColor.DARK_PURPLE
        }
    }
    
    /**
     * Spawn quest boards periodically near players in the wilderness
     */
    fun spawnRandomQuestBoard(player: Player) {
        val loc = player.location.clone()
        val random = Random()
        
        // Find a safe spot 20-40 blocks away
        val angle = random.nextDouble() * Math.PI * 2
        val distance = random.nextInt(20) + 20
        val offsetX = (Math.cos(angle) * distance).toInt()
        val offsetZ = (Math.sin(angle) * distance).toInt()
        
        val spawnLoc = Location(
            loc.world,
            (loc.blockX + offsetX).toDouble(),
            loc.world?.getHighestBlockYAt(loc.blockX + offsetX, loc.blockZ + offsetZ)?.toDouble() ?: return,
            (loc.blockZ + offsetZ).toDouble()
        ).add(0.0, 1.0, 0.0)
        
        // Don't spawn in cities
        if (plugin.cityManager.getCityAt(spawnLoc.chunk) != null) return
        
        // Random difficulty weighted towards easier quests in early game
        val difficulty = when (random.nextInt(10)) {
            in 0..4 -> Difficulty.EASY      // 50%
            in 5..7 -> Difficulty.MEDIUM    // 30%
            in 8..8 -> Difficulty.HARD      // 10%
            else -> Difficulty.NIGHTMARE    // 10%
        }
        
        spawnQuestBoard(spawnLoc, difficulty)
        
        player.sendMessage(Component.text("You notice a quest board nearby...", NamedTextColor.YELLOW, TextDecoration.ITALIC))
        player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 1f)
    }
}
