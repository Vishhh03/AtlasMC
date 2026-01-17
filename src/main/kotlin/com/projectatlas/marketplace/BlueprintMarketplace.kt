package com.projectatlas.marketplace

import com.projectatlas.AtlasPlugin
import com.projectatlas.schematic.SchematicManager
import com.projectatlas.schematic.Schematic
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.*
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitTask
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Blueprint Marketplace - Consolidated with SchematicManager for block storage.
 * BlueprintMarketplace handles marketplace logic (pricing, purchases, listings).
 * SchematicManager handles actual block data storage/loading.
 */
class BlueprintMarketplace(private val plugin: AtlasPlugin, private val schematicManager: SchematicManager) : Listener {

    data class Blueprint(
        val id: UUID = UUID.randomUUID(),
        val name: String,
        val creatorUUID: UUID,
        val creatorName: String,
        val price: Double,
        val width: Int,
        val height: Int,
        val length: Int,
        val blockCount: Int,
        val createdAt: Long = System.currentTimeMillis(),
        var salesCount: Int = 0,
        var totalRevenue: Double = 0.0,
        var isListed: Boolean = true
    )

    data class SelectionSession(
        var pos1: Location? = null,
        var pos2: Location? = null
    )

    data class PreviewSession(
        val blueprint: Blueprint,
        val baseLocation: Location,
        var previewBlocks: MutableSet<Location> = mutableSetOf(),
        var taskId: BukkitTask? = null
    )

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val blueprints = ConcurrentHashMap<UUID, Blueprint>()
    private val selectionSessions = ConcurrentHashMap<UUID, SelectionSession>()
    private val previewSessions = ConcurrentHashMap<UUID, PreviewSession>()
    private val purchasedBlueprints = ConcurrentHashMap<UUID, MutableSet<UUID>>()
    
    private val dataFolder = File(plugin.dataFolder, "blueprints")
    private val listingsFile = File(dataFolder, "listings.json")
    private val purchasesFile = File(dataFolder, "purchases.json")

    private val ignoredMaterials = setOf(
        Material.AIR, Material.CAVE_AIR, Material.VOID_AIR,
        Material.BARRIER, Material.LIGHT
    )

    private val protectedMaterials = setOf(
        Material.BEDROCK, Material.END_PORTAL, Material.END_PORTAL_FRAME,
        Material.NETHER_PORTAL, Material.END_GATEWAY
    )

    init {
        dataFolder.mkdirs()
        loadData()
    }

    // ============ SELECTION WAND ============

    fun giveSelectionWand(player: Player) {
        val wand = ItemStack(Material.GOLDEN_AXE)
        val meta = wand.itemMeta!!
        meta.displayName(Component.text("✦ Blueprint Wand ✦", NamedTextColor.GOLD, TextDecoration.BOLD))
        meta.lore(listOf(
            Component.text("Left-click: Set Position 1", NamedTextColor.YELLOW),
            Component.text("Right-click: Set Position 2", NamedTextColor.YELLOW),
            Component.text("", NamedTextColor.GRAY),
            Component.text("Use /atlas blueprint capture <name> <price>", NamedTextColor.GRAY)
        ))
        meta.setCustomModelData(8500)
        wand.itemMeta = meta
        player.inventory.addItem(wand)
        player.sendMessage(Component.text("Blueprint Wand received! Use it to select your build area.", NamedTextColor.GREEN))
    }

    @EventHandler
    fun onWandUse(event: PlayerInteractEvent) {
        val item = event.item ?: return
        val meta = item.itemMeta ?: return
        if (!meta.hasCustomModelData() || meta.customModelData != 8500) return
        
        val block = event.clickedBlock ?: return
        val player = event.player
        event.isCancelled = true

        val session = selectionSessions.getOrPut(player.uniqueId) { SelectionSession() }

        when (event.action) {
            Action.LEFT_CLICK_BLOCK -> {
                session.pos1 = block.location
                player.sendMessage(Component.text("Position 1 set: ${block.x}, ${block.y}, ${block.z}", NamedTextColor.GREEN))
                showSelectionParticles(player, session)
            }
            Action.RIGHT_CLICK_BLOCK -> {
                session.pos2 = block.location
                player.sendMessage(Component.text("Position 2 set: ${block.x}, ${block.y}, ${block.z}", NamedTextColor.GREEN))
                showSelectionParticles(player, session)
            }
            else -> {}
        }

        if (session.pos1 != null && session.pos2 != null) {
            val dims = getSelectionDimensions(session)
            player.sendMessage(Component.text("Selection: ${dims.first} x ${dims.second} x ${dims.third} blocks", NamedTextColor.YELLOW))
        }
    }

    private fun showSelectionParticles(player: Player, session: SelectionSession) {
        val pos1 = session.pos1 ?: return
        val pos2 = session.pos2 ?: return
        player.spawnParticle(Particle.END_ROD, pos1.clone().add(0.5, 0.5, 0.5), 5, 0.1, 0.1, 0.1, 0.01)
        player.spawnParticle(Particle.END_ROD, pos2.clone().add(0.5, 0.5, 0.5), 5, 0.1, 0.1, 0.1, 0.01)
    }

    private fun getSelectionDimensions(session: SelectionSession): Triple<Int, Int, Int> {
        val pos1 = session.pos1!!
        val pos2 = session.pos2!!
        return Triple(
            kotlin.math.abs(pos2.blockX - pos1.blockX) + 1,
            kotlin.math.abs(pos2.blockY - pos1.blockY) + 1,
            kotlin.math.abs(pos2.blockZ - pos1.blockZ) + 1
        )
    }

    // ============ CAPTURE BLUEPRINT ============

    fun captureBlueprint(player: Player, name: String, price: Double): Boolean {
        val session = selectionSessions[player.uniqueId]
        if (session?.pos1 == null || session.pos2 == null) {
            player.sendMessage(Component.text("You must select two positions first! Use the Blueprint Wand.", NamedTextColor.RED))
            return false
        }

        if (session.pos1!!.world != session.pos2!!.world) {
            player.sendMessage(Component.text("Both positions must be in the same world!", NamedTextColor.RED))
            return false
        }

        if (price < plugin.configManager.blueprintMinPrice) {
            player.sendMessage(Component.text("Minimum price is ${plugin.configManager.blueprintMinPrice}g!", NamedTextColor.RED))
            return false
        }

        val dims = getSelectionDimensions(session)
        val maxSize = plugin.configManager.blueprintMaxDimension
        if (dims.first > maxSize || dims.second > maxSize || dims.third > maxSize) {
            player.sendMessage(Component.text("Maximum size is ${maxSize}x${maxSize}x${maxSize}!", NamedTextColor.RED))
            return false
        }

        val totalBlocks = dims.first * dims.second * dims.third
        if (totalBlocks > plugin.configManager.blueprintMaxBlocks) {
            player.sendMessage(Component.text("Too many blocks! Maximum is ${plugin.configManager.blueprintMaxBlocks}.", NamedTextColor.RED))
            return false
        }

        if (blueprints.values.any { it.name.equals(name, true) && it.creatorUUID == player.uniqueId }) {
            player.sendMessage(Component.text("You already have a blueprint with that name!", NamedTextColor.RED))
            return false
        }

        // Count non-air blocks for validation
        val pos1 = session.pos1!!
        val pos2 = session.pos2!!
        val world = pos1.world!!
        val minX = minOf(pos1.blockX, pos2.blockX)
        val minY = minOf(pos1.blockY, pos2.blockY)
        val minZ = minOf(pos1.blockZ, pos2.blockZ)
        val maxX = maxOf(pos1.blockX, pos2.blockX)
        val maxY = maxOf(pos1.blockY, pos2.blockY)
        val maxZ = maxOf(pos1.blockZ, pos2.blockZ)

        var capturedCount = 0
        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    val block = world.getBlockAt(x, y, z)
                    if (block.type !in ignoredMaterials) {
                        capturedCount++
                    }
                }
            }
        }

        if (capturedCount < plugin.configManager.blueprintMinBlocks) {
            player.sendMessage(Component.text("Too few blocks! Need at least ${plugin.configManager.blueprintMinBlocks} non-air blocks.", NamedTextColor.RED))
            return false
        }

        // Create blueprint metadata
        val blueprintId = UUID.randomUUID()
        val blueprint = Blueprint(
            id = blueprintId,
            name = name,
            creatorUUID = player.uniqueId,
            creatorName = player.name,
            price = price,
            width = dims.first,
            height = dims.second,
            length = dims.third,
            blockCount = capturedCount
        )

        // Use SchematicManager to save the actual blocks
        schematicManager.setPos1(player, pos1)
        schematicManager.setPos2(player, pos2)
        
        if (!schematicManager.saveSchematic("bp_${blueprintId}", player)) {
            player.sendMessage(Component.text("Failed to save schematic data!", NamedTextColor.RED))
            return false
        }

        blueprints[blueprint.id] = blueprint
        saveData()
        selectionSessions.remove(player.uniqueId)

        player.sendMessage(Component.empty())
        player.sendMessage(Component.text("  BLUEPRINT CAPTURED", NamedTextColor.GREEN, TextDecoration.BOLD))
        player.sendMessage(Component.text("  Name: $name", NamedTextColor.YELLOW))
        player.sendMessage(Component.text("  Size: ${dims.first} x ${dims.second} x ${dims.third}", NamedTextColor.GRAY))
        player.sendMessage(Component.text("  Blocks: $capturedCount", NamedTextColor.GRAY))
        player.sendMessage(Component.text("  Price: ${price}g", NamedTextColor.GOLD))
        player.sendMessage(Component.text("  Your blueprint is now listed on the marketplace!", NamedTextColor.GREEN))
        player.sendMessage(Component.empty())
        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f)

        return true
    }

    // ============ MARKETPLACE ============

    fun listBlueprints(player: Player, page: Int = 1) {
        val listed = blueprints.values.filter { it.isListed }.sortedByDescending { it.salesCount }
        val pageSize = 8
        val totalPages = maxOf(1, (listed.size + pageSize - 1) / pageSize)
        val startIdx = (page - 1) * pageSize

        player.sendMessage(Component.text("  BLUEPRINT MARKETPLACE (Page $page/$totalPages)", NamedTextColor.GOLD, TextDecoration.BOLD))

        if (listed.isEmpty()) {
            player.sendMessage(Component.text("No blueprints for sale yet!", NamedTextColor.GRAY))
            return
        }

        listed.drop(startIdx).take(pageSize).forEach { bp ->
            val owned = purchasedBlueprints[player.uniqueId]?.contains(bp.id) ?: false
            val ownedTag = if (owned) " §a[OWNED]" else ""
            val byYou = if (bp.creatorUUID == player.uniqueId) " §6[YOURS]" else ""
            
            player.sendMessage(Component.text("  ${bp.name}$ownedTag$byYou", NamedTextColor.YELLOW))
            player.sendMessage(Component.text("    ${bp.width}x${bp.height}x${bp.length} | ${bp.blockCount} blocks | ${bp.price}g | ${bp.salesCount} sales", NamedTextColor.GRAY))
            player.sendMessage(Component.text("    By: ${bp.creatorName}", NamedTextColor.DARK_GRAY))
        }

        player.sendMessage(Component.text("Use: /atlas blueprint preview <name> | /atlas blueprint buy <name>", NamedTextColor.YELLOW))
    }

    fun getMyBlueprints(player: Player) {
        val mine = blueprints.values.filter { it.creatorUUID == player.uniqueId }
        
        player.sendMessage(Component.text("  YOUR BLUEPRINTS", NamedTextColor.GOLD, TextDecoration.BOLD))
        
        if (mine.isEmpty()) {
            player.sendMessage(Component.text("You haven't created any blueprints yet!", NamedTextColor.GRAY))
            return
        }

        var totalEarnings = 0.0
        mine.forEach { bp ->
            val status = if (bp.isListed) "§aListed" else "§cUnlisted"
            player.sendMessage(Component.text("  ${bp.name} [$status§r]", NamedTextColor.YELLOW))
            player.sendMessage(Component.text("    ${bp.price}g | ${bp.salesCount} sales | Earned: ${bp.totalRevenue}g", NamedTextColor.GRAY))
            totalEarnings += bp.totalRevenue
        }
        
        player.sendMessage(Component.text("Total Earnings: ${totalEarnings}g", NamedTextColor.GOLD))
    }

    // ============ PREVIEW SYSTEM ============

    fun startPreview(player: Player, blueprintName: String): Boolean {
        val blueprint = blueprints.values.find { it.name.equals(blueprintName, true) }
        if (blueprint == null) {
            player.sendMessage(Component.text("Blueprint not found!", NamedTextColor.RED))
            return false
        }

        val schematic = schematicManager.loadSchematic("bp_${blueprint.id}")
        if (schematic == null) {
            player.sendMessage(Component.text("Schematic data missing!", NamedTextColor.RED))
            return false
        }

        cancelPreview(player)

        val baseLocation = player.location.block.location
        val session = PreviewSession(blueprint, baseLocation)

        session.taskId = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            if (!player.isOnline) {
                cancelPreview(player)
                return@Runnable
            }

            schematic.blocks.forEach { block ->
                val loc = baseLocation.clone().add(block.x.toDouble() + 0.5, block.y.toDouble() + 0.5, block.z.toDouble() + 0.5)
                val existing = loc.block
                val hasCollision = existing.type !in ignoredMaterials

                val particle = if (hasCollision) Particle.DUST else Particle.END_ROD
                if (hasCollision) {
                    player.spawnParticle(particle, loc, 1, Particle.DustOptions(Color.RED, 0.5f))
                } else {
                    player.spawnParticle(particle, loc, 1, 0.0, 0.0, 0.0, 0.0)
                }

                session.previewBlocks.add(loc)
            }
        }, 0L, 10L)

        previewSessions[player.uniqueId] = session

        player.sendMessage(Component.empty())
        player.sendMessage(Component.text("  PREVIEW MODE", NamedTextColor.AQUA, TextDecoration.BOLD))
        player.sendMessage(Component.text("  Blueprint: ${blueprint.name}", NamedTextColor.YELLOW))
        player.sendMessage(Component.text("  Size: ${blueprint.width} x ${blueprint.height} x ${blueprint.length}", NamedTextColor.GRAY))
        player.sendMessage(Component.text("  White = clear | Red = collision", NamedTextColor.GRAY))
        player.sendMessage(Component.text("  Move around and use /atlas blueprint place to confirm", NamedTextColor.GREEN))
        player.sendMessage(Component.text("  Use /atlas blueprint cancel to exit preview", NamedTextColor.YELLOW))
        player.sendMessage(Component.empty())

        return true
    }

    fun cancelPreview(player: Player) {
        previewSessions.remove(player.uniqueId)?.taskId?.cancel()
    }

    // ============ PURCHASE & PLACE ============

    fun purchaseBlueprint(player: Player, blueprintName: String): Boolean {
        val blueprint = blueprints.values.find { it.name.equals(blueprintName, true) }
        if (blueprint == null) {
            player.sendMessage(Component.text("Blueprint not found!", NamedTextColor.RED))
            return false
        }

        if (blueprint.creatorUUID == player.uniqueId) {
            player.sendMessage(Component.text("You can't buy your own blueprint!", NamedTextColor.RED))
            return false
        }

        if (purchasedBlueprints[player.uniqueId]?.contains(blueprint.id) == true) {
            player.sendMessage(Component.text("You already own this blueprint!", NamedTextColor.RED))
            return false
        }

        val profile = plugin.identityManager.getPlayer(player.uniqueId)
        if (profile == null || profile.balance < blueprint.price) {
            player.sendMessage(Component.text("Insufficient funds! Need ${blueprint.price}g", NamedTextColor.RED))
            return false
        }

        profile.balance -= blueprint.price
        plugin.identityManager.saveProfile(player.uniqueId)

        val creatorShare = blueprint.price * (plugin.configManager.blueprintCreatorRevenue / 100.0)
        val creatorProfile = plugin.identityManager.getPlayer(blueprint.creatorUUID)
        if (creatorProfile != null) {
            creatorProfile.balance += creatorShare
            plugin.identityManager.saveProfile(blueprint.creatorUUID)
            
            Bukkit.getPlayer(blueprint.creatorUUID)?.sendMessage(
                Component.text("💰 ${player.name} purchased your blueprint '${blueprint.name}'! +${creatorShare}g", NamedTextColor.GOLD)
            )
        }

        blueprint.salesCount++
        blueprint.totalRevenue += creatorShare

        purchasedBlueprints.getOrPut(player.uniqueId) { mutableSetOf() }.add(blueprint.id)
        saveData()

        player.sendMessage(Component.empty())
        player.sendMessage(Component.text("  PURCHASE COMPLETE", NamedTextColor.GREEN, TextDecoration.BOLD))
        player.sendMessage(Component.text("  Blueprint: ${blueprint.name}", NamedTextColor.YELLOW))
        player.sendMessage(Component.text("  Cost: ${blueprint.price}g", NamedTextColor.GOLD))
        player.sendMessage(Component.text("  You can now place this blueprint unlimited times!", NamedTextColor.GREEN))
        player.sendMessage(Component.text("  Use: /atlas blueprint preview ${blueprint.name}", NamedTextColor.YELLOW))
        player.sendMessage(Component.empty())
        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f)

        return true
    }

    fun placeBlueprint(player: Player): Boolean {
        val session = previewSessions[player.uniqueId]
        if (session == null) {
            player.sendMessage(Component.text("You must preview a blueprint first!", NamedTextColor.RED))
            return false
        }

        val blueprint = session.blueprint
        val isOwner = blueprint.creatorUUID == player.uniqueId
        val hasPurchased = purchasedBlueprints[player.uniqueId]?.contains(blueprint.id) == true
        if (!isOwner && !hasPurchased) {
            player.sendMessage(Component.text("You must purchase this blueprint first!", NamedTextColor.RED))
            return false
        }

        val schematic = schematicManager.loadSchematic("bp_${blueprint.id}")
        if (schematic == null) {
            player.sendMessage(Component.text("Schematic data missing!", NamedTextColor.RED))
            return false
        }

        val baseLocation = player.location.block.location
        val world = baseLocation.world!!

        val collisions = mutableListOf<String>()
        val protectedHits = mutableListOf<String>()

        schematic.blocks.forEach { block ->
            val targetBlock = world.getBlockAt(baseLocation.blockX + block.x, baseLocation.blockY + block.y, baseLocation.blockZ + block.z)
            
            if (targetBlock.type in protectedMaterials) {
                protectedHits.add("${targetBlock.x}, ${targetBlock.y}, ${targetBlock.z}")
            } else if (targetBlock.type !in ignoredMaterials) {
                collisions.add("${targetBlock.type}")
            }
        }

        if (protectedHits.isNotEmpty()) {
            player.sendMessage(Component.text("Cannot place here! Protected blocks in the way:", NamedTextColor.RED))
            player.sendMessage(Component.text(protectedHits.take(3).joinToString(", "), NamedTextColor.GRAY))
            return false
        }

        if (collisions.isNotEmpty()) {
            val uniqueTypes = collisions.toSet()
            player.sendMessage(Component.text("⚠ Warning: ${collisions.size} blocks will be replaced:", NamedTextColor.YELLOW))
            player.sendMessage(Component.text(uniqueTypes.take(5).joinToString(", "), NamedTextColor.GRAY))
            player.sendMessage(Component.text("Use /atlas blueprint force to place anyway", NamedTextColor.YELLOW))
            return false
        }

        return doPlaceBlueprint(player, blueprint, baseLocation)
    }

    fun forcePlaceBlueprint(player: Player): Boolean {
        val session = previewSessions[player.uniqueId]
        if (session == null) {
            player.sendMessage(Component.text("No active preview!", NamedTextColor.RED))
            return false
        }

        return doPlaceBlueprint(player, session.blueprint, player.location.block.location)
    }

    private fun doPlaceBlueprint(player: Player, blueprint: Blueprint, baseLocation: Location): Boolean {
        val schematic = schematicManager.loadSchematic("bp_${blueprint.id}")
        if (schematic == null) {
            player.sendMessage(Component.text("Schematic data missing!", NamedTextColor.RED))
            return false
        }

        val world = baseLocation.world!!
        var placedCount = 0
        var failedCount = 0

        schematic.blocks.forEach { block ->
            val targetBlock = world.getBlockAt(baseLocation.blockX + block.x, baseLocation.blockY + block.y, baseLocation.blockZ + block.z)
            
            if (targetBlock.type in protectedMaterials) {
                failedCount++
                return@forEach
            }

            try {
                val blockData = Bukkit.createBlockData(block.blockData)
                targetBlock.blockData = blockData
                placedCount++
            } catch (e: Exception) {
                try {
                    targetBlock.type = block.material
                    placedCount++
                } catch (e2: Exception) {
                    failedCount++
                }
            }
        }

        cancelPreview(player)

        player.sendMessage(Component.empty())
        player.sendMessage(Component.text("  BLUEPRINT PLACED", NamedTextColor.GREEN, TextDecoration.BOLD))
        player.sendMessage(Component.text("  ${blueprint.name}", NamedTextColor.YELLOW))
        player.sendMessage(Component.text("  Placed: $placedCount blocks" + if (failedCount > 0) " (${failedCount} failed)" else "", NamedTextColor.GRAY))
        player.sendMessage(Component.empty())
        player.playSound(player.location, Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f)

        return true
    }

    // ============ MANAGEMENT ============

    fun unlistBlueprint(player: Player, name: String): Boolean {
        val blueprint = blueprints.values.find { it.name.equals(name, true) && it.creatorUUID == player.uniqueId }
        if (blueprint == null) {
            player.sendMessage(Component.text("Blueprint not found or you don't own it!", NamedTextColor.RED))
            return false
        }

        blueprint.isListed = !blueprint.isListed
        saveData()

        val status = if (blueprint.isListed) "listed" else "unlisted"
        player.sendMessage(Component.text("Blueprint '${name}' is now $status.", NamedTextColor.YELLOW))
        return true
    }

    fun deleteBlueprint(player: Player, name: String): Boolean {
        val blueprint = blueprints.values.find { it.name.equals(name, true) && it.creatorUUID == player.uniqueId }
        if (blueprint == null) {
            player.sendMessage(Component.text("Blueprint not found or you don't own it!", NamedTextColor.RED))
            return false
        }

        blueprints.remove(blueprint.id)
        saveData()

        player.sendMessage(Component.text("Blueprint '${name}' deleted.", NamedTextColor.YELLOW))
        return true
    }

    // ============ PERSISTENCE ============

    private fun saveData() {
        try {
            listingsFile.writeText(gson.toJson(blueprints.values.toList()))
            
            val purchaseData = purchasedBlueprints.mapKeys { it.key.toString() }
                .mapValues { it.value.map { id -> id.toString() } }
            purchasesFile.writeText(gson.toJson(purchaseData))
        } catch (e: Exception) {
            plugin.logger.warning("Failed to save blueprint data: ${e.message}")
        }
    }

    private fun loadData() {
        try {
            if (listingsFile.exists()) {
                val type = object : TypeToken<List<Blueprint>>() {}.type
                val list: List<Blueprint> = gson.fromJson(listingsFile.readText(), type)
                list.forEach { blueprints[it.id] = it }
                plugin.logger.info("Loaded ${blueprints.size} blueprints")
            }

            if (purchasesFile.exists()) {
                val type = object : TypeToken<Map<String, List<String>>>() {}.type
                val data: Map<String, List<String>> = gson.fromJson(purchasesFile.readText(), type)
                data.forEach { (playerStr, bpList) ->
                    val playerId = UUID.fromString(playerStr)
                    val bpIds = bpList.map { UUID.fromString(it) }.toMutableSet()
                    purchasedBlueprints[playerId] = bpIds
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("Failed to load blueprint data: ${e.message}")
        }
    }

    fun findBlueprint(name: String): Blueprint? {
        return blueprints.values.find { it.name.equals(name, true) }
    }
}
