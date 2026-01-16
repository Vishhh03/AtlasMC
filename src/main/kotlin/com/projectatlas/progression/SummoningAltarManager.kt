package com.projectatlas.progression

import com.projectatlas.AtlasPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Display
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitRunnable
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Summoning Altar Manager
 * 
 * Allows players to build altars to summon/enter boss arenas together.
 * Supports "Custom PNG" visuals via ItemDisplay entities.
 */
class SummoningAltarManager(private val plugin: AtlasPlugin) : Listener {

    data class Altar(
        val id: UUID = UUID.randomUUID(),
        val centerLocation: Location,
        val ownerUUID: UUID,
        val cityId: UUID? = null,
        var isActive: Boolean = false,
        var portalEntity: UUID? = null
    )

    private val altars = ConcurrentHashMap<Location, Altar>()
    private val cooldowns = ConcurrentHashMap<UUID, Long>()

    // ══════════════════════════════════════════════════════════════
    // CREATION & INTERACTION
    // ══════════════════════════════════════════════════════════════

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        val block = event.clickedBlock ?: return
        val player = event.player

        // Check if interacting with Crying Obsidian (Center of Altar)
        if (block.type == Material.CRYING_OBSIDIAN) {
            val location = block.location

            // Is this an existing altar?
            val altar = altars[location]
            if (altar != null) {
                // Toggle/Use Altar
                handleAltarInteraction(player, altar)
                return
            }

            // Try to create new altar
            if (player.isSneaking && player.inventory.itemInMainHand.type == Material.DIAMOND) {
                if (checkMultiblock(location)) {
                    createAltar(player, location)
                }
            }
        }
    }

    private fun checkMultiblock(center: Location): Boolean {
        // Simple 3x3 platform verification
        // B B B
        // B C B  (C = Center/Crying Obsidian)
        // B B B
        val world = center.world
        val x = center.blockX
        val y = center.blockY
        val z = center.blockZ

        for (dx in -1..1) {
            for (dz in -1..1) {
                if (dx == 0 && dz == 0) continue // Skip center (already checked)
                val block = world.getBlockAt(x + dx, y, z + dz)
                if (block.type != Material.POLISHED_BLACKSTONE_BRICKS && block.type != Material.OBSIDIAN) {
                    return false
                }
            }
        }
        return true
    }

    private fun createAltar(player: Player, location: Location) {
        val profile = plugin.identityManager.getPlayer(player.uniqueId)
        // cityId in profile is String?, need UUID?
        val cityIdUUID = profile?.cityId?.let { try { UUID.fromString(it) } catch (e: Exception) { null } }

        val altar = Altar(
            centerLocation = location,
            ownerUUID = player.uniqueId,
            cityId = cityIdUUID
        )
        
        altars[location] = altar
        
        player.sendMessage(Component.text("Summoning Altar created!", NamedTextColor.GREEN))
        player.sendMessage(Component.text("Right-click with Nether Star to activate portal.", NamedTextColor.GRAY))
        player.playSound(location, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f)
        
        // Permanent visual marker
        location.world.spawnParticle(Particle.END_ROD, location.clone().add(0.5, 1.0, 0.5), 20, 0.5, 0.5, 0.5, 0.05)
    }

    private fun handleAltarInteraction(player: Player, altar: Altar) {
        // Security Check
        val isAdmin = player.hasPermission("atlas.admin")
        val isOwner = player.uniqueId == altar.ownerUUID
        
        val profile = plugin.identityManager.getPlayer(player.uniqueId)
        val playerCityId = profile?.cityId?.let { try { UUID.fromString(it) } catch (e: Exception) { null } }
        val isCityMember = altar.cityId != null && playerCityId == altar.cityId
        
        if (!isAdmin && !isOwner && !isCityMember) {
            player.sendMessage(Component.text("You cannot use this altar.", NamedTextColor.RED))
            return
        }

        if (altar.isActive) {
            deactivateAltar(altar)
            player.sendMessage(Component.text("Altar deactivated.", NamedTextColor.YELLOW))
        } else {
            // Requirement to activate: Nether Star (placeholder for specialized key)
            if (player.inventory.itemInMainHand.type == Material.NETHER_STAR) {
               activateAltar(altar)
               player.sendMessage(Component.text("Portal Opened! Step in to enter the arena.", NamedTextColor.LIGHT_PURPLE))
            } else {
                player.sendMessage(Component.text("Requires a Nether Star to activate.", NamedTextColor.RED))
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // ACTIVATION & VISUALS
    // ══════════════════════════════════════════════════════════════

    private fun activateAltar(altar: Altar) {
        altar.isActive = true
        
        val loc = altar.centerLocation.clone().add(0.5, 1.5, 0.5)
        
        // Spawn Display Entity for "Custom PNG"
        val display = loc.world.spawn(loc, ItemDisplay::class.java) { d ->
            val item = ItemStack(Material.PAPER)
            item.editMeta { m -> 
                m.setCustomModelData(1001) // Assume 1001 is our Portal PNG in resource pack
                m.displayName(Component.text("Portal")) 
            }
            d.setItemStack(item)
            d.setBillboard(Display.Billboard.CENTER)
            d.setDisplayWidth(2.0f)
            d.setDisplayHeight(3.0f)
            
            val t = d.transformation
            t.scale.set(1.5f, 2.5f, 1.0f)
            d.setTransformation(t)
            
            d.isPersistent = false 
        }
        
        altar.portalEntity = display.uniqueId
        
        loc.world.playSound(loc, Sound.BLOCK_PORTAL_TRIGGER, 1.0f, 0.5f)
        loc.world.spawnParticle(Particle.DRAGON_BREATH, loc, 50, 0.5, 1.0, 0.5, 0.05)
    }

    private fun deactivateAltar(altar: Altar) {
        altar.isActive = false
        if (altar.portalEntity != null) {
            val entity = Bukkit.getEntity(altar.portalEntity!!)
            entity?.remove()
            altar.portalEntity = null
        }
    }

    // ══════════════════════════════════════════════════════════════
    // PORTAL LOGIC
    // ══════════════════════════════════════════════════════════════

    @EventHandler
    fun onMove(event: PlayerMoveEvent) {
         if (!event.hasChangedBlock()) return
         val player = event.player
         val loc = player.location
         
         // performance optimization: only check nearby altars? 
         // For now, simple loop (assuming few active altars)
         altars.values.filter { it.isActive }.forEach { altar ->
             if (altar.centerLocation.world == loc.world && altar.centerLocation.distance(loc) < 1.5) {
                 // Player entered portal
                 // Check permissions
                 val isOwner = player.uniqueId == altar.ownerUUID
                 
                 val profile = plugin.identityManager.getPlayer(player.uniqueId)
                 val playerCityId = profile?.cityId?.let { try { UUID.fromString(it) } catch (e: Exception) { null } }
                 val isCityMember = altar.cityId != null && playerCityId == altar.cityId
                 
                 if (isOwner || isCityMember || player.hasPermission("atlas.admin")) {
                     teleportToArena(player, altar)
                 } else {
                     player.setVelocity(player.location.direction.multiply(-1).setY(0.5))
                     player.sendMessage(Component.text("This portal rejects you.", NamedTextColor.RED))
                 }
             }
         }
    }

    private fun teleportToArena(player: Player, altar: Altar) {
        val cooldown = cooldowns[player.uniqueId] ?: 0L
        if (System.currentTimeMillis() - cooldown < 5000) return // 5s debounce
        cooldowns[player.uniqueId] = System.currentTimeMillis()

        // Teleport Logic
        // For prototype: Teleport 100 blocks UP in current world, creating a glass platform
        // In production: Teleport to "Boss World"
        
        val era = plugin.progressionManager.getPlayerEra(player)
        
        player.sendMessage(Component.text("⚔ warp: Boss Arena (Era ${era.ordinal})", NamedTextColor.LIGHT_PURPLE))
        player.playSound(player.location, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f)
        
        // Simple mock arena: 100 blocks above altar
        val arenaLoc = altar.centerLocation.clone().add(0.0, 50.0, 0.0)
        
        // Ensure platform
        for (x in -5..5) {
            for (z in -5..5) {
                arenaLoc.clone().add(x.toDouble(), -1.0, z.toDouble()).block.type = Material.BLACK_STAINED_GLASS
            }
        }
        
        player.teleport(arenaLoc.add(0.0, 1.0, 0.0))
    }
}
