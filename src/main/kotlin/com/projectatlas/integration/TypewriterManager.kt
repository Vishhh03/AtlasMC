package com.projectatlas.integration

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.logging.Level

/**
 * Manages integration with the Typewriter plugin.
 * Handles detection and interaction via commands or API if available.
 */
object TypewriterManager {
    private const val PLUGIN_NAME = "Typewriter"
    private var typewriterPlugin: Plugin? = null

    fun init() {
        typewriterPlugin = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME)
        if (typewriterPlugin != null) {
            if (typewriterPlugin!!.isEnabled) {
                Bukkit.getLogger().info("Successfully hooked into Typewriter plugin version ${typewriterPlugin!!.description.version}")
                // Analyze capabilities if possible (Reflection)
                analyzeApi()
            } else {
                Bukkit.getLogger().warning("Typewriter plugin detected but DISABLED. It likely crashed on startup. Visuals will be disabled.")
            }
        } else {
            Bukkit.getLogger().warning("Typewriter plugin not found. Visuals will be disabled.")
        }
    }

    private fun analyzeApi() {
        try {
            // Attempt to find the main engine class or API
            // Based on jar inspection: com.projectatlas seems to be OURS, but Typewriter was com.typewritermc...
            val clbm = Class.forName("com.typewritermc.engine.TypewriterPlugin")
            Bukkit.getLogger().info("Found TypewriterPlugin class via reflection.")
            // We can extend this to find methods later
        } catch (e: ClassNotFoundException) {
            Bukkit.getLogger().log(Level.WARNING, "Could not find TypewriterPlugin class, maybe package name differs?", e)
        }
    }

    fun isAvailable(): Boolean = typewriterPlugin != null && typewriterPlugin!!.isEnabled

    /**
     * Starts a Typewriter quest/script for a player through console command.
     * This allows us to trigger the visual aspects while managing logic internally.
     */
    fun startVisuals(player: Player, scriptId: String) {
        if (!isAvailable()) return
        
        // Execute command safely
        // Assuming /typewriter start [script] [player] or similar
        // We will log this for the user to verify the exact command
        Bukkit.getLogger().info("Triggering Typewriter script: $scriptId for ${player.name}")
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "typewriter run $scriptId ${player.name}")
    }
}
