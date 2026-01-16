package com.projectatlas.npc

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.projectatlas.AtlasPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.event.inventory.InventoryClickEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.block.Biome
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.entity.Villager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.persistence.PersistentDataType
import org.bukkit.NamespacedKey
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class NPCManager(private val plugin: AtlasPlugin) : Listener {

    private val npcs = ConcurrentHashMap<String, NPC>()
    private val tempNpcs = ConcurrentHashMap<String, NPC>() // In-memory only
    private val spawnedEntities = ConcurrentHashMap<String, Entity>()
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val dataFile = File(plugin.dataFolder, "npcs.json")
    private val npcKey = NamespacedKey(plugin, "atlas_npc")

    init {
        loadNPCs()
    }

    fun spawnNPC(npc: NPC, location: Location): Entity? {
        npc.setLocation(location)
        npcs[npc.id] = npc
        
        val entity = location.world.spawn(location, Villager::class.java) { villager ->
            villager.customName(Component.text(npc.name, NamedTextColor.GOLD).decorate(TextDecoration.BOLD))
            villager.isCustomNameVisible = true
            villager.setAI(false)
            villager.isInvulnerable = true
            villager.isSilent = true
            villager.profession = when (npc.type) {
                NPCType.MERCHANT -> Villager.Profession.WEAPONSMITH
                NPCType.QUEST_GIVER -> Villager.Profession.CLERIC
                NPCType.GUARD -> Villager.Profession.ARMORER
                NPCType.ARCHER -> Villager.Profession.FLETCHER
            }
            
            // Biome Aware Skin
            val biome = location.block.biome
            villager.villagerType = when {
                biome.name.contains("DESERT") -> Villager.Type.DESERT
                biome.name.contains("JUNGLE") -> Villager.Type.JUNGLE
                biome.name.contains("SWAMP") -> Villager.Type.SWAMP
                biome.name.contains("SAVANNA") -> Villager.Type.SAVANNA
                biome.name.contains("SNOW") || biome.name.contains("ICE") -> Villager.Type.SNOW
                biome.name.contains("TAIGA") -> Villager.Type.TAIGA
                else -> Villager.Type.PLAINS
            }
            villager.persistentDataContainer.set(npcKey, PersistentDataType.STRING, npc.id)
        }
        
        spawnedEntities[npc.id] = entity
        saveNPCs()
        return entity
    }

    fun despawnNPC(npcId: String) {
        spawnedEntities.remove(npcId)?.remove()
        npcs.remove(npcId)
        tempNpcs.remove(npcId)
        saveNPCs()
    }
    
    fun registerTempNPC(npc: NPC) {
        tempNpcs[npc.id] = npc
    }

    fun getNPC(id: String): NPC? = npcs[id] ?: tempNpcs[id]

    @EventHandler
    fun onInteract(event: PlayerInteractEntityEvent) {
        val entity = event.rightClicked
        val npcId = entity.persistentDataContainer.get(npcKey, PersistentDataType.STRING) ?: return
        
        event.isCancelled = true
        val npc = npcs[npcId] ?: return
        val player = event.player
        
        // 1. Check if we are turning in a quest
        plugin.questManager.checkQuestCompletion(player, npc.name)
        
        // Route through Dialogue System
        plugin.dialogueManager.openDialogue(player, npc)
    }

    fun openMerchantMenu(player: Player, npc: NPC) {
        val inv = Bukkit.createInventory(null, 45, Component.text("${npc.name} - Daily Shop", NamedTextColor.GOLD))
        
        // Seed random based on Day + NPC ID so it rotates daily but stays same for everyone that day
        val seed = (player.world.fullTime / 24000) + npc.id.hashCode()
        val random = kotlin.random.Random(seed)
        
        // Loot Table (Material, Price, Slot Chance)
        val commonTiers = listOf(
            Material.BREAD to 5, Material.COOKED_BEEF to 8, Material.ARROW to 2, 
            Material.OAK_LOG to 4, Material.COAL to 5, Material.TORCH to 1,
            Material.IRON_INGOT to 25, Material.GOLD_INGOT to 50, Material.LAPIS_LAZULI to 10
        )
        val rareTiers = listOf(
            Material.DIAMOND to 400, Material.EMERALD to 150, Material.ENDER_PEARL to 100,
            Material.BLAZE_ROD to 120, Material.GHAST_TEAR to 200, Material.SLIME_BALL to 80,
            Material.EXPERIENCE_BOTTLE to 60, Material.GOLDEN_APPLE to 250
        )
        val epicTiers = listOf(
            Material.NETHERITE_SCRAP to 1200, Material.TOTEM_OF_UNDYING to 4500,
            Material.ENCHANTED_GOLDEN_APPLE to 3000, Material.NETHER_STAR to 10000,
            Material.SHULKER_SHELL to 1500, Material.ELYTRA to 15000
        )
        
        // Populate 10-15 random items
        val slots = (0 until 45).toMutableList()
        var itemsAdded = 0
        
        // 1. Always some Common
        repeat(random.nextInt(5) + 5) {
            val (mat, price) = commonTiers.random(random)
            val slot = slots.removeAt(random.nextInt(slots.size))
            inv.setItem(slot, createShopItem(mat, mat.name.replace("_", " "), price))
        }
        
        // 2. Chance for Rare (50% chance for each slot attempt)
        repeat(random.nextInt(3) + 2) {
            if (random.nextBoolean()) {
                val (mat, price) = rareTiers.random(random)
                if (slots.isNotEmpty()) {
                    val slot = slots.removeAt(random.nextInt(slots.size))
                    inv.setItem(slot, createShopItem(mat, mat.name.replace("_", " "), price))
                }
            }
        }
        
        // 3. Rare Chance for Epic (1 item per day max, 20% chance)
        if (random.nextDouble() < 0.20) {
            val (mat, price) = epicTiers.random(random)
            if (slots.isNotEmpty()) {
                val slot = slots.removeAt(random.nextInt(slots.size))
                inv.setItem(slot, createShopItem(mat, "§5§l" + mat.name.replace("_", " "), price))
            }
        }
        
        player.openInventory(inv)
    }

    private fun openQuestMenu(player: Player, npc: NPC) {
        plugin.guiManager.openQuestMenu(player)
    }

    private fun createShopItem(material: Material, name: String, price: Int): org.bukkit.inventory.ItemStack {
        val item = org.bukkit.inventory.ItemStack(material)
        val meta = item.itemMeta
        meta.displayName(Component.text(name, NamedTextColor.YELLOW))
        meta.lore(listOf(
            Component.text("Price: ${price}g", NamedTextColor.GOLD),
            Component.text("----------------", NamedTextColor.DARK_GRAY),
            Component.text("Stock refreshes daily", NamedTextColor.GRAY)
        ))
        meta.persistentDataContainer.set(NamespacedKey(plugin, "shop_price"), PersistentDataType.INTEGER, price)
        item.itemMeta = meta
        return item
    }

    private fun loadNPCs() {
        if (!dataFile.exists()) return
        try {
            val type = object : TypeToken<List<NPC>>() {}.type
            val loaded: List<NPC> = gson.fromJson(dataFile.readText(), type) ?: return
            loaded.forEach { npcs[it.id] = it }
            plugin.logger.info("Loaded ${npcs.size} NPCs")
        } catch (e: Exception) {
            plugin.logger.severe("Failed to load NPCs: ${e.message}")
        }
    }

    private fun saveNPCs() {
        try {
            dataFile.writeText(gson.toJson(npcs.values.toList()))
        } catch (e: Exception) {
            plugin.logger.severe("Failed to save NPCs: ${e.message}")
        }
    }

    fun respawnAllNPCs() {
        npcs.values.forEach { npc ->
            val loc = npc.getLocation(plugin) ?: return@forEach
            val entity = loc.world.spawn(loc, Villager::class.java) { villager ->
                villager.customName(Component.text(npc.name, NamedTextColor.GOLD).decorate(TextDecoration.BOLD))
                villager.isCustomNameVisible = true
                villager.setAI(false)
                villager.isInvulnerable = true
                villager.profession = when (npc.type) {
                    NPCType.MERCHANT -> Villager.Profession.WEAPONSMITH
                    NPCType.QUEST_GIVER -> Villager.Profession.CLERIC
                    NPCType.GUARD -> Villager.Profession.ARMORER
                    NPCType.ARCHER -> Villager.Profession.FLETCHER
                }
                
                // Biome Aware Skin
                val biome = loc.block.biome
                villager.villagerType = when {
                    biome.name.contains("DESERT") -> Villager.Type.DESERT
                    biome.name.contains("JUNGLE") -> Villager.Type.JUNGLE
                    biome.name.contains("SWAMP") -> Villager.Type.SWAMP
                    biome.name.contains("SAVANNA") -> Villager.Type.SAVANNA
                    biome.name.contains("SNOW") || biome.name.contains("ICE") -> Villager.Type.SNOW
                    biome.name.contains("TAIGA") -> Villager.Type.TAIGA
                    else -> Villager.Type.PLAINS
                }
                villager.persistentDataContainer.set(npcKey, PersistentDataType.STRING, npc.id)
            }
            spawnedEntities[npc.id] = entity
        }
    }
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val view = event.view
        val title = PlainTextComponentSerializer.plainText().serialize(view.title())
        
        if (!title.contains("Shop")) return
        
        event.isCancelled = true // Prevent moving items
        
        val clickedItem = event.currentItem ?: return
        if (clickedItem.type == Material.AIR) return
        val player = event.whoClicked as? Player ?: return
        
        // Check which inventory was clicked
        if (event.clickedInventory == event.view.topInventory) {
            // BUY
            val meta = clickedItem.itemMeta ?: return
            val price = meta.persistentDataContainer.get(NamespacedKey(plugin, "shop_price"), PersistentDataType.INTEGER) ?: return
            
            if (plugin.economyManager.withdraw(player.uniqueId, price.toDouble())) {
                // Give Item
                val give = clickedItem.clone()
                // Remove price tag lore
                val lore = give.lore() ?: mutableListOf()
                if (lore.size >= 2) {
                   val newLore = lore.dropLast(2)
                   give.lore(newLore)
                }
                
                // Add to inventory
                val left = player.inventory.addItem(give)
                if (left.isNotEmpty()) {
                    player.sendMessage(Component.text("Inventory full!", NamedTextColor.RED))
                    plugin.economyManager.deposit(player.uniqueId, price.toDouble()) // Refund
                    return
                }
                
                player.sendMessage(Component.text("Purchased item for ${price}g.", NamedTextColor.GREEN))
                player.playSound(player.location, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f)
            } else {
                player.sendMessage(Component.text("Insufficient funds! You need ${price}g.", NamedTextColor.RED))
                player.playSound(player.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f)
            }
        } else {
            // SELL
            // Find price in Top Inventory
            var buyPrice = 0
             // iterate top inventory
             for (shopItem in event.view.topInventory.contents) {
                 if (shopItem != null && shopItem.type == clickedItem.type) {
                     val p = shopItem.itemMeta?.persistentDataContainer?.get(NamespacedKey(plugin, "shop_price"), PersistentDataType.INTEGER)
                     if (p != null) {
                         buyPrice = p
                         break
                     }
                 }
             }
             
             if (buyPrice > 0) {
                 // 80% Buy Back
                 val sellPrice = (buyPrice * 0.8).toInt().coerceAtLeast(1)
                 val amount = clickedItem.amount
                 val total = sellPrice * amount
                 
                 plugin.economyManager.deposit(player.uniqueId, total.toDouble())
                 event.clickedInventory?.setItem(event.slot, org.bukkit.inventory.ItemStack(Material.AIR))
                 
                 player.sendMessage(Component.text("Sold $amount items for ${total}g.", NamedTextColor.GREEN))
                 player.playSound(player.location, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f)
             } else {
                 player.sendMessage(Component.text("This merchant doesn't want that.", NamedTextColor.RED))
             }
        }
    }
}
