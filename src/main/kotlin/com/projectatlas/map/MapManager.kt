package com.projectatlas.map

import com.projectatlas.AtlasPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.*
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.util.*

/**
 * Local Area Map - Shows surroundings in a GUI
 * Opens with M key (drop key while sneaking) or /atlas map
 */
class MapManager(private val plugin: AtlasPlugin) : Listener {

    private val MAP_SIZE = 9 // 9x6 grid
    private val MAP_RADIUS = 4 // chunks around player
    
    private val openMaps = mutableSetOf<UUID>()
    
    /**
     * Hotkey: Press Q while sneaking to open map
     */
    @EventHandler
    fun onDropItem(event: PlayerDropItemEvent) {
        val player = event.player
        
        // Shift + Q = Open Map
        if (player.isSneaking) {
            event.isCancelled = true
            event.itemDrop.remove()
            openMap(player)
        }
    }
    
    /**
     * Open the local area map for a player
     */
    fun openMap(player: Player) {
        val inv = Bukkit.createInventory(null, 54, Component.text("🗺 Local Map", NamedTextColor.DARK_GREEN, TextDecoration.BOLD))
        
        renderMap(player, inv)
        
        player.openInventory(inv)
        openMaps.add(player.uniqueId)
        player.playSound(player.location, Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f)
    }
    
    private fun renderMap(player: Player, inv: Inventory) {
        val playerChunk = player.location.chunk
        val world = player.world
        
        // Map is 9 wide x 6 tall (54 slots)
        // Center is slot 22 (row 2, col 4) - roughly center
        // Each slot = 1 chunk (16x16 blocks)
        
        for (row in 0 until 6) {
            for (col in 0 until 9) {
                val slot = row * 9 + col
                
                // Calculate chunk offset from player
                val offsetX = col - 4 // -4 to +4
                val offsetZ = row - 3 // -3 to +2
                
                val chunkX = playerChunk.x + offsetX
                val chunkZ = playerChunk.z + offsetZ
                
                val item = getChunkItem(world, chunkX, chunkZ, player, offsetX == 0 && offsetZ == 0)
                inv.setItem(slot, item)
            }
        }
        
        // Add legend in bottom row
        // Actually the map fills all 54 slots, so no room for legend
        // We can use lore on items to explain
    }
    
    private fun getChunkItem(world: World, chunkX: Int, chunkZ: Int, player: Player, isPlayerHere: Boolean): ItemStack {
        val chunkKey = "${world.name}:$chunkX,$chunkZ"
        
        // Check for various features at this chunk
        val city = plugin.cityManager.getCityAt(world.getChunkAt(chunkX, chunkZ))
        val playerProfile = plugin.identityManager.getPlayer(player.uniqueId)
        val isOwnCity = city?.id == playerProfile?.cityId
        
        // Check for players in this chunk
        val playersInChunk = world.players.filter { 
            it.location.chunk.x == chunkX && it.location.chunk.z == chunkZ && it.uniqueId != player.uniqueId
        }
        
        // Check for NPCs
        val npcsNearby = hasNPCsInChunk(world, chunkX, chunkZ)
        
        // Check for quest boards (approximate - just check if near any tracked board)
        val hasQuestBoard = hasQuestBoardNearChunk(world, chunkX, chunkZ)
        
        // Check for death location
        val deathLoc = getDeathLocationInChunk(player, world, chunkX, chunkZ)
        
        // Determine material and color
        val (material, name, lore) = when {
            isPlayerHere -> {
                Triple(
                    Material.PLAYER_HEAD,
                    Component.text("📍 YOU ARE HERE", NamedTextColor.AQUA, TextDecoration.BOLD),
                    listOf(
                        Component.text("X: ${player.location.blockX}, Z: ${player.location.blockZ}", NamedTextColor.WHITE),
                        Component.text("Chunk: $chunkX, $chunkZ", NamedTextColor.GRAY)
                    )
                )
            }
            deathLoc != null -> {
                Triple(
                    Material.SKELETON_SKULL,
                    Component.text("☠ Death Location", NamedTextColor.RED, TextDecoration.BOLD),
                    listOf(
                        Component.text("Your items are here!", NamedTextColor.YELLOW),
                        Component.text("X: ${deathLoc.blockX}, Z: ${deathLoc.blockZ}", NamedTextColor.WHITE)
                    )
                )
            }
            playersInChunk.isNotEmpty() -> {
                Triple(
                    Material.LIME_CONCRETE,
                    Component.text("👥 Players Here", NamedTextColor.GREEN),
                    playersInChunk.map { Component.text("• ${it.name}", NamedTextColor.WHITE) }
                )
            }
            hasQuestBoard -> {
                Triple(
                    Material.OAK_SIGN,
                    Component.text("📜 Quest Board", NamedTextColor.GOLD),
                    listOf(Component.text("Quest available nearby", NamedTextColor.GRAY))
                )
            }
            npcsNearby -> {
                Triple(
                    Material.VILLAGER_SPAWN_EGG,
                    Component.text("🧑 NPC Nearby", NamedTextColor.LIGHT_PURPLE),
                    listOf(Component.text("Interact with NPCs here", NamedTextColor.GRAY))
                )
            }
            city != null && isOwnCity -> {
                Triple(
                    Material.GREEN_STAINED_GLASS_PANE,
                    Component.text("🏠 ${city.name}", NamedTextColor.GREEN),
                    listOf(
                        Component.text("Your city territory", NamedTextColor.GRAY),
                        Component.text("Protected zone", NamedTextColor.GREEN)
                    )
                )
            }
            city != null -> {
                Triple(
                    Material.RED_STAINED_GLASS_PANE,
                    Component.text("⚔ ${city.name}", NamedTextColor.RED),
                    listOf(
                        Component.text("Foreign territory", NamedTextColor.GRAY),
                        Component.text("You cannot build here", NamedTextColor.RED)
                    )
                )
            }
            else -> {
                // Wilderness - check biome for flavor
                val biome = world.getBiome(chunkX * 16 + 8, 64, chunkZ * 16 + 8)
                val biomeName = biome.key.key.replace("_", " ").lowercase()
                    .replaceFirstChar { it.uppercase() }
                
                val material = when {
                    biomeName.contains("ocean", true) || biomeName.contains("river", true) -> Material.BLUE_STAINED_GLASS_PANE
                    biomeName.contains("desert", true) || biomeName.contains("badlands", true) -> Material.YELLOW_STAINED_GLASS_PANE
                    biomeName.contains("forest", true) || biomeName.contains("taiga", true) -> Material.LIME_STAINED_GLASS_PANE
                    biomeName.contains("snow", true) || biomeName.contains("frozen", true) -> Material.WHITE_STAINED_GLASS_PANE
                    biomeName.contains("jungle", true) -> Material.GREEN_STAINED_GLASS_PANE
                    biomeName.contains("swamp", true) -> Material.BROWN_STAINED_GLASS_PANE
                    biomeName.contains("mountain", true) || biomeName.contains("hill", true) -> Material.GRAY_STAINED_GLASS_PANE
                    else -> Material.LIGHT_GRAY_STAINED_GLASS_PANE
                }
                
                Triple(
                    material,
                    Component.text("🌍 Wilderness", NamedTextColor.GRAY),
                    listOf(
                        Component.text("Biome: $biomeName", NamedTextColor.WHITE),
                        Component.text("Chunk: $chunkX, $chunkZ", NamedTextColor.DARK_GRAY)
                    )
                )
            }
        }
        
        val item = ItemStack(material)
        val meta = item.itemMeta!!
        meta.displayName(name)
        meta.lore(lore)
        item.itemMeta = meta
        return item
    }
    
    private fun hasNPCsInChunk(world: World, chunkX: Int, chunkZ: Int): Boolean {
        // Check if any villager-type entities are in this chunk area
        val centerX = chunkX * 16 + 8
        val centerZ = chunkZ * 16 + 8
        
        return world.getNearbyEntities(
            Location(world, centerX.toDouble(), 64.0, centerZ.toDouble()),
            16.0, 64.0, 16.0
        ).any { it.type == org.bukkit.entity.EntityType.VILLAGER }
    }
    
    private fun hasQuestBoardNearChunk(world: World, chunkX: Int, chunkZ: Int): Boolean {
        val centerX = chunkX * 16 + 8
        val centerZ = chunkZ * 16 + 8
        
        // Check blocks in chunk for signs (quest boards)
        // This is expensive, so we just return false for now
        // In a full implementation, we'd track quest board locations
        return false
    }
    
    private fun getDeathLocationInChunk(player: Player, world: World, chunkX: Int, chunkZ: Int): Location? {
        // Access QoLManager's death locations (we need to expose this)
        // For now, we can check via compass target if it's set
        val compassTarget = player.compassTarget
        if (compassTarget.world == world) {
            val targetChunkX = compassTarget.blockX shr 4
            val targetChunkZ = compassTarget.blockZ shr 4
            if (targetChunkX == chunkX && targetChunkZ == chunkZ) {
                // Check if this is actually a death location (not spawn)
                if (compassTarget != world.spawnLocation) {
                    return compassTarget
                }
            }
        }
        return null
    }
    
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        
        if (!openMaps.contains(player.uniqueId)) return
        
        // Cancel all clicks in map
        event.isCancelled = true
        
        // Optionally: clicking on a chunk could set a waypoint or show more info
        val item = event.currentItem ?: return
        val meta = item.itemMeta ?: return
        
        // Play click sound
        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.5f, 1f)
    }
    
    @EventHandler
    fun onInventoryClose(event: org.bukkit.event.inventory.InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        openMaps.remove(player.uniqueId)
    }
}
