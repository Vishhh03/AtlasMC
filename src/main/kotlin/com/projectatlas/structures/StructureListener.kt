package com.projectatlas.structures

import com.projectatlas.AtlasPlugin
import com.projectatlas.structures.StructureHealthManager.DamagePhase
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.scheduler.BukkitRunnable
import java.util.UUID

class StructureListener(private val plugin: AtlasPlugin) : Listener {

    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        val block = event.block
        val player = event.player
        
        // Creative mode bypass
        if (player.gameMode == GameMode.CREATIVE) return
        
        val structureId = plugin.structureHealthManager.findStructureAt(block.location) ?: return
        val health = plugin.structureHealthManager.getHealth(structureId) ?: return

        // If ruined, allow breaking (cleanup)
        if (health.isRuined) return

        // Block belongs to a active structure -> Cancel break and deal damage
        event.isCancelled = true
        
        // Damage calculation (e.g. Pickaxe deals more?)
        val tool = player.inventory.itemInMainHand
        val damage = if (tool.type.name.contains("PICKAXE") || tool.type.name.contains("AXE")) 10.0 else 2.0
        
        applyStructureDamage(structureId, damage, block.location)
        player.sendActionBar(Component.text("Structure Health: ${(health.currentHealth/health.maxHealth * 100).toInt()}% (${health.getPhase()})", NamedTextColor.RED))
    }

    @EventHandler
    fun onEntityExplode(event: EntityExplodeEvent) {
        val blocks = event.blockList().toList()
        if (blocks.isEmpty()) return
        
        val hitStructures = mutableSetOf<UUID>()
        
        val iterator = event.blockList().iterator()
        while (iterator.hasNext()) {
            val block = iterator.next()
            val structureId = plugin.structureHealthManager.findStructureAt(block.location)
            
            if (structureId != null) {
                val health = plugin.structureHealthManager.getHealth(structureId)
                if (health != null && !health.isRuined) {
                    iterator.remove() // Don't break the block if active
                    hitStructures.add(structureId)
                }
            }
        }
        
        // Deal massive damage for explosions
        hitStructures.forEach { id ->
            applyStructureDamage(id, 50.0, event.location)
        }
    }
    
    private fun applyStructureDamage(id: UUID, amount: Double, location: org.bukkit.Location) {
        val health = plugin.structureHealthManager.getHealth(id) ?: return
        val existingRuined = health.isRuined
        
        val newPhase = plugin.structureHealthManager.damageStructure(id, amount) ?: return
        
        // Feedback
        location.world.playSound(location, Sound.BLOCK_ANVIL_LAND, 0.5f, 0.5f)
        location.world.spawnParticle(Particle.CRIT, location.add(0.5, 0.5, 0.5), 10)
        
        // Check if just ruined
        if (newPhase == DamagePhase.RUINED && !existingRuined) {
             location.world.playSound(location, Sound.ENTITY_IRON_GOLEM_DEATH, 1.0f, 0.5f)
             location.world.spawnParticle(Particle.EXPLOSION_EMITTER, location, 1)
             
             // VISUAL DESTRUCTION
             // Randomly set blocks in radius to AIR or COBBLESTONE to simulate ruin
             val structLoc = plugin.structureHealthManager.findStructureLocation(id)
             if (structLoc != null) {
                val r = structLoc.radius.toInt()
                val center = structLoc.center
                for (x in -r..r) {
                    for (y in 0..6) { // Height assumption
                        for (z in -r..r) {
                            val b = center.clone().add(x.toDouble(), y.toDouble(), z.toDouble()).block
                            if (!b.type.isAir) {
                                if (Math.random() < 0.3) b.type = Material.AIR // Destroy 30%
                                else if (Math.random() < 0.3) b.type = Material.MOSSY_COBBLESTONE // Ruin 30%
                            }
                        }
                    }
                }
             }
        }
        
        // Updates based on phase
        if (newPhase == DamagePhase.CRITICAL) {
             location.world.spawnParticle(Particle.LARGE_SMOKE, location, 5)
        }
    }
}
