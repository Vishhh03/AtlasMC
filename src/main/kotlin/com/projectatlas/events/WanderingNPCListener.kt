package com.projectatlas.events

import com.projectatlas.AtlasPlugin
import com.projectatlas.npc.NPC
import com.projectatlas.npc.NPCType
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.entity.Villager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.persistence.PersistentDataType
import org.bukkit.NamespacedKey
import java.util.Random
import java.util.UUID

class WanderingNPCListener(private val plugin: AtlasPlugin) : Listener {

    private val random = Random()
    private val wandererKey = NamespacedKey(plugin, "wandering_npc")

    init {
        // Run checks every minute (1200 ticks)
        plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            attemptSpawnWanderers()
        }, 1200L, 1200L)
    }

    private fun attemptSpawnWanderers() {
        plugin.server.onlinePlayers.forEach { player ->
            if (player.world.environment != org.bukkit.World.Environment.NORMAL) return@forEach
            if (plugin.cityManager.getCityAt(player.chunk) != null) return@forEach // Only in wilderness
            
            // 20% chance per player per minute
            if (random.nextDouble() > 0.2) return@forEach
            
            spawnWandererNear(player)
        }
    }
    
    private fun spawnWandererNear(player: Player) {
        val world = player.world
        val offsetX = (random.nextInt(30) - 15)
        val offsetZ = (random.nextInt(30) - 15)
        // Ensure somewhat far
        if (Math.abs(offsetX) < 10 && Math.abs(offsetZ) < 10) return
        
        val spawnLoc = player.location.add(offsetX.toDouble(), 0.0, offsetZ.toDouble())
        spawnLoc.y = world.getHighestBlockYAt(spawnLoc).toDouble() + 1
        
        val type = if (random.nextBoolean()) NPCType.QUEST_GIVER else NPCType.MERCHANT
        val name = if (type == NPCType.QUEST_GIVER) "Lost Scout" else "Traveling Merchant"
        
        world.spawn(spawnLoc, Villager::class.java) { villager ->
            villager.customName(Component.text(name, NamedTextColor.AQUA).decorate(TextDecoration.ITALIC))
            villager.isCustomNameVisible = true
            villager.profession = if (type == NPCType.MERCHANT) Villager.Profession.NITWIT else Villager.Profession.CARTOGRAPHER
            villager.persistentDataContainer.set(wandererKey, PersistentDataType.STRING, type.name)
            
            // Despawn after 2 minutes
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                if (villager.isValid) {
                    villager.remove()
                }
            }, 2400L)
        }
        
        player.sendMessage(Component.text("A wanderer ($name) was seen nearby at ${spawnLoc.blockX}, ${spawnLoc.blockY}, ${spawnLoc.blockZ}!", NamedTextColor.GREEN))
    }
    
    @EventHandler
    fun onInteract(event: PlayerInteractEntityEvent) {
        val entity = event.rightClicked
        if (!entity.persistentDataContainer.has(wandererKey, PersistentDataType.STRING)) return
        
        event.isCancelled = true
        val player = event.player
        val typeStr = entity.persistentDataContainer.get(wandererKey, PersistentDataType.STRING)
        val name = entity.customName()?.let { (it as? net.kyori.adventure.text.TextComponent)?.content() } ?: "Wanderer"
        
        // 1. Check if we are turning in a quest
        plugin.questManager.checkQuestCompletion(player, name)
        
        // 2. Create Temp NPC for Dialogue
        val tempId = "temp_${UUID.randomUUID()}"
        val type = try { NPCType.valueOf(typeStr!!) } catch (e: Exception) { NPCType.QUEST_GIVER }
        
        val tempNPC = NPC(
            id = tempId,
            name = name,
            type = type,
            worldName = player.world.name, // Fixed parameter Name
            x = entity.location.x,
            y = entity.location.y,
            z = entity.location.z
        )
        
        plugin.npcManager.registerTempNPC(tempNPC)
        plugin.dialogueManager.openDialogue(player, tempNPC)
    }
}
