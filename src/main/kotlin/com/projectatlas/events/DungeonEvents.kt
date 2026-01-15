package com.projectatlas.events

import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class DungeonCompleteEvent(val player: Player, val difficulty: Int, val success: Boolean) : Event() {
    
    override fun getHandlers(): HandlerList {
        return handlers
    }

    companion object {
        private val handlers = HandlerList()
        @JvmStatic
        fun getHandlerList(): HandlerList {
            return handlers
        }
    }
}
