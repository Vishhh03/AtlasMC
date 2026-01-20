package com.projectatlas.dialogue

import com.projectatlas.AtlasPlugin
import com.projectatlas.npc.NPC
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages cinematic dialogues using Text Display entities to mimic the Typewriter plugin feel.
 */
class CinematicDialogueManager(private val plugin: AtlasPlugin) : Listener {

    private val activeSessions = ConcurrentHashMap<UUID, DialogueSession>()

    data class DialogueSession(
        val player: Player,
        val npc: NPC?,
        val npcEntity: org.bukkit.entity.Entity?,
        val dialogue: Dialogue,
        var currentLineIndex: Int = 0,
        var task: BukkitTask? = null,
        var isTyping: Boolean = false,
        var currentText: String = "",
        val wasAIEnabled: Boolean = true,
        var isFading: Boolean = false,
        var fadeoutTicks: Int = 0
    )

    fun startCinematicDialogue(player: Player, npc: NPC?, dialogue: Dialogue) {
        stopDialogue(player)

        // 1. NPC Looking Logic (Keep this)
        var npcEntity: org.bukkit.entity.Entity? = null
        var wasAIEnabled = true
        
        if (npc != null) {
            val loc = npc.getLocation(plugin)
            if (loc != null) {
                npcEntity = loc.world.getNearbyEntities(loc, 2.0, 2.0, 2.0)
                    .find { it.persistentDataContainer.has(org.bukkit.NamespacedKey(plugin, "atlas_npc"), org.bukkit.persistence.PersistentDataType.STRING) 
                            || it.persistentDataContainer.has(org.bukkit.NamespacedKey(plugin, "wandering_npc"), org.bukkit.persistence.PersistentDataType.STRING) }
                
                if (npcEntity != null) {
                    val dir = player.location.toVector().subtract(npcEntity.location.toVector()).normalize()
                    val targetLoc = npcEntity.location.clone()
                    targetLoc.direction = dir
                    npcEntity.teleport(targetLoc)
                    
                    if (npcEntity is org.bukkit.entity.LivingEntity) {
                        wasAIEnabled = npcEntity.hasAI()
                        npcEntity.setAI(false)
                    }
                }
            }
        }
        
        // 2. Start Session (No Display Entity needed for UI mode)
        val session = DialogueSession(player, npc, npcEntity, dialogue, 0, null, false, "", wasAIEnabled)
        activeSessions[player.uniqueId] = session
        
        typeLine(session)
    }

    fun typeLine(session: DialogueSession) {
        val fullText = if (session.currentLineIndex == 0) {
             "${session.dialogue.speakerName}: ${session.dialogue.text}"
        } else {
            showOptions(session)
            return
        }

        session.isTyping = true
        session.currentText = ""
        var charIndex = 0
        
        session.task?.cancel()
        
        // Faster Typewriter: 3 chars per tick
        session.task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            if (!session.isTyping) return@Runnable

            // Add up to 3 characters per tick
            repeat(3) {
                if (charIndex < fullText.length) {
                    session.currentText += fullText[charIndex]
                    charIndex++
                }
            }
            
            // Update Action Bar
            session.player.sendActionBar(Component.text(session.currentText, NamedTextColor.GOLD))
            
            // Sound every tick
            if (charIndex < fullText.length) {
                session.player.playSound(session.player.location, Sound.BLOCK_NOTE_BLOCK_HAT, 0.3f, 1.8f)
            } else {
                session.isTyping = false
                session.task?.cancel()
                
                // Auto-advance to options immediately
                proceedToOptions(session)
            }
        }, 0L, 1L) 
    }

    private fun proceedToOptions(session: DialogueSession) {
        session.currentLineIndex++
        showOptions(session)
    }

    private fun showOptions(session: DialogueSession) {
        session.isTyping = false
        
        // Action Bar is too small for options list, so we prompt them to look at Chat
        session.player.sendActionBar(Component.text("Select an option (Check Chat)", NamedTextColor.YELLOW, TextDecoration.BOLD))
        
        session.player.sendMessage(Component.empty())
        session.player.sendMessage(Component.text(" Select an option:", NamedTextColor.GOLD))
        session.dialogue.options.forEachIndexed { index, option -> 
             session.player.sendMessage(
                 Component.text(" ${index+1}. ", NamedTextColor.GRAY)
                 .append(Component.text("[${option.text}]", option.color, TextDecoration.BOLD))
                 .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(option.command))
                 .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(Component.text(option.hoverText)))
             )
        }
        session.player.sendMessage(Component.empty())
        
        // Auto-end session with fadeout if no option selected for a long time? 
        // No, let's keep it open until they choose.
    }

    @EventHandler
    fun onChat(event: AsyncPlayerChatEvent) {
        val session = activeSessions[event.player.uniqueId] ?: return
        if (session.isTyping) return
        
        // Check if options are displayed
        if (session.currentLineIndex > 0) {
           val msg = event.message
           val num = msg.trim().toIntOrNull()
           
           if (num != null && num > 0 && num <= session.dialogue.options.size) {
               event.isCancelled = true
               val option = session.dialogue.options[num - 1]
               
               // Run command on main thread
               Bukkit.getScheduler().runTask(plugin, Runnable {
                   event.player.chat(option.command) // Execute command as player
               })
           }
        }
    }
    
    fun stopDialogue(player: Player, instant: Boolean = true) {
        val session = activeSessions[player.uniqueId] ?: return
        
        session.task?.cancel()
        
        if (!instant) {
            // Trigger Fadeout (Linger)
            if (!session.isFading) {
                session.isFading = true
                session.fadeoutTicks = 60 // 3 seconds linger
                return // Don't remove session yet
            }
        }
        
        // Actually Stop
        activeSessions.remove(player.uniqueId)
        
        // Restore NPC AI
        val npcEntity = session.npcEntity
        if (npcEntity is org.bukkit.entity.LivingEntity && npcEntity.isValid) {
             if (session.wasAIEnabled) {
                 npcEntity.setAI(true)
             }
        }
        
        // Clear Action Bar
        player.sendActionBar(Component.empty())
    }

    @EventHandler
    fun onSneak(event: PlayerToggleSneakEvent) {
        if (!event.isSneaking) return // Only on sneak down
        
        val session = activeSessions[event.player.uniqueId] ?: return
        
        if (session.isTyping) {
            // Instant finish (Skip) calling proceedToOptions directly
            session.isTyping = false
            session.task?.cancel()
            
            // Show full text quickly before options? 
            // The user wants to see options click, so let's just go straight to options
            // But usually 'skip' means 'show full text', then 'next' goes to options.
            // Since we are auto-advancing, 'Skip' -> 'Show Options' is fine.
            proceedToOptions(session)
        }
    }
    
    fun tick() {
        val iterator = activeSessions.values.iterator()
        while (iterator.hasNext()) {
            val session = iterator.next()
            
            // 0. Safety Checks
            if (session.npcEntity != null) {
                // Check if dead
                if (!session.npcEntity.isValid || session.npcEntity.isDead) {
                    session.player.sendActionBar(Component.empty())
                    iterator.remove() // Silent close
                    continue
                }
                
                // Check distance
                if (session.distanceToPlayer() > 10.0) {
                     stopDialogue(session.player, instant = true)
                     continue // stopDialogue removes from map? No, stopDialogue uses separate lookup. 
                     // We should be careful here. stopDialogue modifies activeSessions.
                     // Since we are iterating, we must use iterator.remove() OR handle differently.
                     // But stopDialogue uses activeSessions.remove(player.uniqueId).
                     // This will cause ConcurrentModificationException if we use map.values.iterator() directly?
                     // activeSessions is ConcurrentHashMap, so keySet iterator is weakly consistent.
                     // But let's be safe.
                }
            }
            // Also check player online/world match?
            if (!session.player.isOnline || (session.npcEntity != null && session.player.world != session.npcEntity.world)) {
                iterator.remove()
                continue
            }
            
            // 1. NPC Tracking (Face player)
            if (session.npcEntity != null && session.npcEntity.isValid) {
                 val dir = session.player.location.toVector().subtract(session.npcEntity.location.toVector()).normalize()
                 val loc = session.npcEntity.location.clone()
                 loc.direction = dir
                 session.npcEntity.teleport(loc)
            }
            
            // 2. Fadeout Logic
            if (session.isFading) {
                session.fadeoutTicks--
                if (session.fadeoutTicks <= 0) {
                    // Restore AI
                    val npcEntity = session.npcEntity
                    if (npcEntity is org.bukkit.entity.LivingEntity && npcEntity.isValid) {
                         if (session.wasAIEnabled) {
                             npcEntity.setAI(true)
                         }
                    }
                    // Clear UI
                    session.player.sendActionBar(Component.empty())
                    
                    iterator.remove() // End session safely
                }
            }
        }
    }
    
    // Helper extension
    private fun DialogueSession.distanceToPlayer(): Double {
        return npcEntity?.location?.distance(player.location) ?: 0.0
    }
}
