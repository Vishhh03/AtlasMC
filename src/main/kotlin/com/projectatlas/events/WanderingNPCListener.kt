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
            
            val isCave = player.location.y < 50 && player.location.block.lightFromSky < 5
            val roll = random.nextDouble()
            
            if (isCave) {
                // Cave Events (30% chance)
                if (roll < 0.10) spawnSpiderSwarm(player)
                else if (roll < 0.20) spawnUndeadMiner(player)
                else if (roll < 0.30) spawnMinersCache(player)
            } else {
                // Surface Events (35% chance)
                when {
                    roll < 0.10 -> spawnWandererNear(player)   // 10%
                    roll < 0.20 -> spawnBanditAmbush(player)   // 10%
                    roll < 0.30 -> spawnTreasureCache(player)  // 10%
                    roll < 0.35 -> spawnQuestBoard(player)     // 5%
                }
            }
        }
    }
    
    // --- Cave Events ---
    
    private fun spawnSpiderSwarm(player: Player) {
        val spawnLoc = getSafeSpawnLocation(player) ?: return
        
        player.sendMessage(Component.text("You hear skittering in the dark...", NamedTextColor.DARK_PURPLE).decorate(TextDecoration.ITALIC))
        player.playSound(player.location, Sound.ENTITY_SPIDER_AMBIENT, 1.0f, 1.5f)
        
        repeat(random.nextInt(3) + 3) { // 3-5 Spiders
            player.world.spawn(spawnLoc, org.bukkit.entity.CaveSpider::class.java) { spider ->
                spider.customName(Component.text("Deep Crawler", NamedTextColor.DARK_RED))
                scheduleDespawn(spider, 2400L)
            }
        }
    }
    
    private fun spawnUndeadMiner(player: Player) {
        val spawnLoc = getSafeSpawnLocation(player) ?: return
        
        player.sendMessage(Component.text("A fallen miner rises...", NamedTextColor.GOLD))
        player.playSound(player.location, Sound.ENTITY_ZOMBIE_AMBIENT, 1.0f, 0.5f)
        
        player.world.spawn(spawnLoc, org.bukkit.entity.Zombie::class.java) { zombie ->
            zombie.customName(Component.text("Undead Miner", NamedTextColor.GOLD))
            zombie.isCustomNameVisible = true
            zombie.equipment.helmet = ItemStack(Material.IRON_HELMET)
            zombie.equipment.chestplate = ItemStack(Material.CHAINMAIL_CHESTPLATE)
            zombie.equipment.setItemInMainHand(ItemStack(Material.IRON_PICKAXE))
            
            // Drop raw gold/iron chance
            zombie.equipment.setItemInOffHand(ItemStack(Material.RAW_GOLD, 3))
            zombie.equipment.itemInOffHandDropChance = 0.5f
            
            scheduleDespawn(zombie, 3600L)
        }
    }
    
    private fun spawnMinersCache(player: Player) {
        val spawnLoc = getSafeSpawnLocation(player) ?: return
        spawnLoc.block.type = Material.BARREL
        
        val barrel = spawnLoc.block.state as? org.bukkit.block.Barrel ?: return
        
        // Cave Loot
        val loot = listOf(
            ItemStack(Material.COAL, random.nextInt(16) + 5),
            ItemStack(Material.TORCH, random.nextInt(32) + 10),
            ItemStack(Material.RAW_IRON, random.nextInt(10) + 2),
            ItemStack(Material.TNT, random.nextInt(2) + 1),
            ItemStack(Material.GOLDEN_APPLE, 1)
        )
        loot.forEach { 
            if (random.nextBoolean()) barrel.inventory.addItem(it)
        }
        
        barrel.update()
        player.world.spawnParticle(Particle.END_ROD, spawnLoc.clone().add(0.5, 1.0, 0.5), 10)
        player.sendMessage(Component.text("You spot a lost supply cache!", NamedTextColor.YELLOW))
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
        player.playSound(player.location, Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f)
    }
    
    // --- 2. Bandit Ambush (Level-Scaled Difficulty) ---
    private fun spawnBanditAmbush(player: Player) {
        val spawnLoc = getSafeSpawnLocation(player) ?: return
        
        val playerLevel = plugin.identityManager.getPlayer(player.uniqueId)?.level ?: 1
        
        player.sendMessage(Component.text("You hear rustling in the bushes...", NamedTextColor.RED).decorate(TextDecoration.ITALIC))
        player.playSound(player.location, Sound.ENTITY_ILLUSIONER_PREPARE_BLINDNESS, 1.0f, 0.5f)
        
        // Scale enemy type based on player level
        val (mobCount, mobName, mobColor) = when {
            playerLevel <= 5 -> Triple(2, "Thug", NamedTextColor.GRAY)
            playerLevel <= 10 -> Triple(3, "Bandit", NamedTextColor.RED)
            else -> Triple(3, "Elite Bandit", NamedTextColor.DARK_RED)
        }
        
        repeat(mobCount) {
            when {
                playerLevel <= 5 -> {
                    // Easy: Zombies with wooden weapons
                    player.world.spawn(spawnLoc, org.bukkit.entity.Zombie::class.java) { mob ->
                        mob.customName(Component.text(mobName, mobColor))
                        mob.isCustomNameVisible = true
                        mob.equipment.setItemInMainHand(ItemStack(Material.WOODEN_SWORD))
                        mob.isBaby = false
                        scheduleDespawn(mob, 2400L)
                    }
                }
                playerLevel <= 10 -> {
                    // Medium: Husks with stone weapons
                    player.world.spawn(spawnLoc, org.bukkit.entity.Husk::class.java) { mob ->
                        mob.customName(Component.text(mobName, mobColor))
                        mob.isCustomNameVisible = true
                        mob.equipment.setItemInMainHand(ItemStack(Material.STONE_AXE))
                        mob.equipment.helmet = ItemStack(Material.LEATHER_HELMET)
                        scheduleDespawn(mob, 2400L)
                    }
                }
                else -> {
                    // Hard: Vindicators with iron axes
                    player.world.spawn(spawnLoc, Vindicator::class.java) { mob ->
                        mob.customName(Component.text(mobName, mobColor))
                        mob.isCustomNameVisible = true
                        mob.equipment.setItemInMainHand(ItemStack(Material.IRON_AXE))
                        scheduleDespawn(mob, 2400L)
                    }
                }
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
    
    // --- Quest Board Spawning ---
    private fun spawnQuestBoard(player: Player) {
        plugin.questBoardManager.spawnRandomQuestBoard(player)
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
