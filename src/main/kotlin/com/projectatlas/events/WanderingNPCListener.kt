package com.projectatlas.events

import com.projectatlas.AtlasPlugin
import com.projectatlas.npc.NPC
import com.projectatlas.npc.NPCType
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.entity.Villager
import org.bukkit.entity.Vindicator
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.NamespacedKey
import java.util.Random
import java.util.UUID

class WanderingNPCListener(private val plugin: AtlasPlugin) : Listener {

    private val random = Random()
    private val wandererKey = NamespacedKey(plugin, "wandering_npc")

    init {
        // Run checks every 30 seconds (600 ticks)
        plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            checkForEvents()
        }, 600L, 600L)
    }

    private fun checkForEvents() {
        plugin.server.onlinePlayers.forEach { player ->
            if (player.world.environment != org.bukkit.World.Environment.NORMAL) return@forEach
            // Only triggers in Wilderness (Not in a city)
            if (plugin.cityManager.getCityAt(player.chunk) != null) return@forEach
            
            val roll = random.nextDouble()
            
            // 30% chance of event every 30s
            when {
                roll < 0.10 -> spawnWandererNear(player) // 10%
                roll < 0.20 -> spawnBanditAmbush(player) // 10%
                roll < 0.30 -> spawnTreasureCache(player) // 10%
            }
        }
    }
    
    // --- 1. Wandering NPC ---
    private fun spawnWandererNear(player: Player) {
        val spawnLoc = getSafeSpawnLocation(player) ?: return
        
        val type = if (random.nextBoolean()) NPCType.QUEST_GIVER else NPCType.MERCHANT
        val name = if (type == NPCType.QUEST_GIVER) "Lost Scout" else "Traveling Merchant"
        
        player.world.spawn(spawnLoc, Villager::class.java) { villager ->
            villager.customName(Component.text(name, NamedTextColor.AQUA).decorate(TextDecoration.ITALIC))
            villager.isCustomNameVisible = true
            villager.profession = if (type == NPCType.MERCHANT) Villager.Profession.NITWIT else Villager.Profession.CARTOGRAPHER
            villager.persistentDataContainer.set(wandererKey, PersistentDataType.STRING, type.name)
            
            // Biome skin logic handled by NPCManager or automatic? 
            // Vanilla villagers adapt to biome automatically on spawn!
            
            // Despawn after 3 minutes
            scheduleDespawn(villager, 3600L)
        }
        
        player.sendMessage(Component.text("You spot a traveler nearby...", NamedTextColor.GREEN))
        player.playSound(player.location, Sound.ENTITY_VILLAGER_AMBIENT, 1.0f, 1.0f)
    }
    
    // --- 2. Bandit Ambush ---
    private fun spawnBanditAmbush(player: Player) {
        val spawnLoc = getSafeSpawnLocation(player) ?: return
        
        player.sendMessage(Component.text("You hear rustling in the bushes...", NamedTextColor.RED).decorate(TextDecoration.ITALIC))
        player.playSound(player.location, Sound.ENTITY_ILLUSIONER_PREPARE_BLINDNESS, 1.0f, 0.5f)
        
        // Spawn 3 Bandits
        repeat(3) {
            player.world.spawn(spawnLoc, Vindicator::class.java) { bandit ->
                bandit.customName(Component.text("Bandit", NamedTextColor.RED))
                bandit.isCustomNameVisible = true
                bandit.equipment.setItemInMainHand(ItemStack(Material.IRON_AXE))
                scheduleDespawn(bandit, 2400L) // Despawn after 2m
            }
        }
    }
    
    // --- 3. Treasure Cache ---
    private fun spawnTreasureCache(player: Player) {
        val spawnLoc = getSafeSpawnLocation(player) ?: return
        spawnLoc.block.type = Material.BARREL
        
        val barrel = spawnLoc.block.state as? org.bukkit.block.Barrel ?: return
        
        // Populate loot
        val loot = listOf(
            ItemStack(Material.BREAD, random.nextInt(5) + 1),
            ItemStack(Material.IRON_INGOT, random.nextInt(3) + 1),
            ItemStack(Material.GOLD_NUGGET, random.nextInt(8) + 1),
            ItemStack(Material.EMERALD, random.nextInt(2) + 1),
            ItemStack(Material.APPLE, random.nextInt(3) + 1)
        )
        loot.forEach { 
            if (random.nextBoolean()) barrel.inventory.addItem(it)
        }
        
        // Rare loot
        if (random.nextDouble() < 0.1) {
            barrel.inventory.addItem(ItemStack(Material.DIAMOND, 1))
        }
        
        barrel.update()
        
        // Visuals
        player.world.spawnParticle(Particle.END_ROD, spawnLoc.clone().add(0.5, 1.0, 0.5), 10)
        player.sendMessage(Component.text("You notice something hidden nearby...", NamedTextColor.GOLD))
    }
    
    // --- Helpers ---
    
    private fun getSafeSpawnLocation(player: Player): org.bukkit.Location? {
        val world = player.world
        val offsetX = (random.nextInt(30) - 15)
        val offsetZ = (random.nextInt(30) - 15)
        
        if (Math.abs(offsetX) < 10 && Math.abs(offsetZ) < 10) return null // Too close
        
        val baseLoc = player.location.add(offsetX.toDouble(), 0.0, offsetZ.toDouble())
        val y = world.getHighestBlockYAt(baseLoc)
        val ground = world.getBlockAt(baseLoc.blockX, y - 1, baseLoc.blockZ)
        
        if (ground.type == Material.WATER || ground.type == Material.LAVA) return null
        
        return org.bukkit.Location(world, baseLoc.x, y + 1.0, baseLoc.z)
    }

    private fun scheduleDespawn(entity: org.bukkit.entity.Entity, delayTicks: Long) {
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (entity.isValid) entity.remove()
        }, delayTicks)
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
            worldName = player.world.name,
            x = entity.location.x,
            y = entity.location.y,
            z = entity.location.z
        )
        
        plugin.npcManager.registerTempNPC(tempNPC)
        plugin.dialogueManager.openDialogue(player, tempNPC)
    }
}
