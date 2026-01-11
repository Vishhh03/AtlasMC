package com.projectatlas.npc

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.projectatlas.AtlasPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
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
        saveNPCs()
    }

    fun getNPC(id: String): NPC? = npcs[id]

    @EventHandler
    fun onInteract(event: PlayerInteractEntityEvent) {
        val entity = event.rightClicked
        val npcId = entity.persistentDataContainer.get(npcKey, PersistentDataType.STRING) ?: return
        
        event.isCancelled = true
        val npc = npcs[npcId] ?: return
        val player = event.player
        
        when (npc.type) {
            NPCType.MERCHANT -> openMerchantMenu(player, npc)
            NPCType.QUEST_GIVER -> openQuestMenu(player, npc)
        }
    }

    private fun openMerchantMenu(player: Player, npc: NPC) {
        val inv = Bukkit.createInventory(null, 27, Component.text("${npc.name} - Shop", NamedTextColor.GOLD))
        
        // Sample merchant items
        inv.setItem(10, createShopItem(Material.DIAMOND, "Diamond", 500))
        inv.setItem(11, createShopItem(Material.GOLDEN_APPLE, "Golden Apple", 100))
        inv.setItem(12, createShopItem(Material.ENDER_PEARL, "Ender Pearl", 75))
        inv.setItem(13, createShopItem(Material.EXPERIENCE_BOTTLE, "XP Bottle", 25))
        inv.setItem(14, createShopItem(Material.IRON_INGOT, "Iron Ingot", 10))
        
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
            Component.text("Click to buy", NamedTextColor.GRAY)
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
                }
                villager.persistentDataContainer.set(npcKey, PersistentDataType.STRING, npc.id)
            }
            spawnedEntities[npc.id] = entity
        }
    }
}
