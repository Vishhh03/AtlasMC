package com.projectatlas.events

import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class DungeonCompleteEvent(val player: Player, val difficulty: Int, val success: Boolean) : Event() {
    companion object {
        private val handlers = HandlerList()
        @JvmStatic fun getHandlerList(): HandlerList = handlers
    }
    
    override fun getHandlers(): HandlerList = handlers
}
