package com.projectatlas.visual

import com.projectatlas.AtlasPlugin
import net.kyori.adventure.resource.ResourcePackInfo
import net.kyori.adventure.resource.ResourcePackRequest
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerResourcePackStatusEvent
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Resource Pack Manager - Handles server-side resource pack distribution.
 * 
 * This enables "modded-like" visuals (custom models, textures, sounds)
 * without requiring any client-side mod installation.
 */
class ResourcePackManager(private val plugin: AtlasPlugin) : Listener {

    companion object {
        // Configure these in config.yml
        const val DEFAULT_PACK_URL = ""  // Set to your hosted pack URL
        const val DEFAULT_PACK_HASH = "" // SHA-1 hash for caching
        
        // Pack status tracking
        val playerPackStatus = ConcurrentHashMap<UUID, PackStatus>()
    }

    enum class PackStatus {
        PENDING,
        ACCEPTED,
        DECLINED,
        LOADED,
        FAILED
    }

    private var packUrl: String = DEFAULT_PACK_URL
    private var packHash: String = DEFAULT_PACK_HASH
    private var packRequired: Boolean = false

    init {
        loadConfig()
    }

    private fun loadConfig() {
        val config = plugin.config
        packUrl = config.getString("resource-pack.url", DEFAULT_PACK_URL) ?: DEFAULT_PACK_URL
        packHash = config.getString("resource-pack.hash", DEFAULT_PACK_HASH) ?: DEFAULT_PACK_HASH
        packRequired = config.getBoolean("resource-pack.required", false)
        
        // Save defaults if not present
        if (!config.contains("resource-pack.url")) {
            config.set("resource-pack.url", "")
            config.set("resource-pack.hash", "")
            config.set("resource-pack.required", false)
            config.set("resource-pack.prompt-message", "&6Project Atlas &7requires a resource pack for the best experience!")
            plugin.saveConfig()
        }
    }

    /**
     * Send resource pack to player on join.
     */
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        
        // Delay slightly to ensure player is fully loaded
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            sendResourcePack(player)
        }, 20L) // 1 second delay
    }

    /**
     * Track resource pack status.
     */
    @EventHandler
    fun onResourcePackStatus(event: PlayerResourcePackStatusEvent) {
        val player = event.player
        
        when (event.status) {
            PlayerResourcePackStatusEvent.Status.ACCEPTED -> {
                playerPackStatus[player.uniqueId] = PackStatus.ACCEPTED
                player.sendMessage(Component.text("Downloading Atlas resource pack...", NamedTextColor.GRAY))
            }
            PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED -> {
                playerPackStatus[player.uniqueId] = PackStatus.LOADED
                player.sendMessage(Component.text("✓ Resource pack loaded!", NamedTextColor.GREEN))
            }
            PlayerResourcePackStatusEvent.Status.DECLINED -> {
                playerPackStatus[player.uniqueId] = PackStatus.DECLINED
                if (packRequired) {
                    player.kick(Component.text("This server requires the resource pack.", NamedTextColor.RED))
                } else {
                    player.sendMessage(Component.text("⚠ Resource pack declined. Some visuals may not display correctly.", NamedTextColor.YELLOW))
                }
            }
            PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD -> {
                playerPackStatus[player.uniqueId] = PackStatus.FAILED
                player.sendMessage(Component.text("⚠ Resource pack download failed. Try /atlas reloadpack", NamedTextColor.RED))
            }
            else -> {}
        }
    }

    /**
     * Send the resource pack to a player.
     */
    fun sendResourcePack(player: Player) {
        if (packUrl.isBlank()) {
            // No pack configured - that's okay for development
            return
        }

        playerPackStatus[player.uniqueId] = PackStatus.PENDING

        try {
            // Use Adventure API for resource pack
            val uri = URI.create(packUrl)
            val packInfo = ResourcePackInfo.resourcePackInfo()
                .uri(uri)
                .hash(packHash)
                .build()
            
            val request = ResourcePackRequest.resourcePackRequest()
                .packs(packInfo)
                .prompt(Component.text("Project Atlas requires this pack for custom visuals!", NamedTextColor.GOLD))
                .required(packRequired)
                .build()
            
            player.sendResourcePacks(request)
        } catch (e: Exception) {
            plugin.logger.warning("Failed to send resource pack to ${player.name}: ${e.message}")
        }
    }

    /**
     * Check if player has the pack loaded.
     */
    fun hasPackLoaded(player: Player): Boolean {
        return playerPackStatus[player.uniqueId] == PackStatus.LOADED
    }

    /**
     * Reload pack for a player.
     */
    fun reloadPack(player: Player) {
        sendResourcePack(player)
    }

    /**
     * Update pack URL at runtime (for admins).
     */
    fun setPackUrl(url: String, hash: String) {
        this.packUrl = url
        this.packHash = hash
        
        plugin.config.set("resource-pack.url", url)
        plugin.config.set("resource-pack.hash", hash)
        plugin.saveConfig()
    }
}
