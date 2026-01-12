package com.projectatlas.history

import com.projectatlas.AtlasPlugin
import com.projectatlas.city.City
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

data class CityHistory(
    val cityId: String,
    val events: MutableList<HistoryEvent> = mutableListOf()
)

data class HistoryEvent(
    val timestamp: Long,
    val description: String,
    val type: EventType
)

enum class EventType {
    FOUNDING,
    SIEGE,
    POLITICS,
    ECONOMY,
    MEMBER
}

class HistoryManager(private val plugin: AtlasPlugin) {
    private val histories = ConcurrentHashMap<String, CityHistory>()
    private val gson = Gson()
    private val historyDir = File(plugin.dataFolder, "history")

    init {
        if (!historyDir.exists()) historyDir.mkdirs()
        loadAll()
    }

    fun logEvent(cityId: String, description: String, type: EventType) {
        val history = histories.computeIfAbsent(cityId) { CityHistory(cityId) }
        val event = HistoryEvent(System.currentTimeMillis(), description, type)
        history.events.add(event)
        saveHistory(history)
    }

    fun getHistory(cityId: String): List<HistoryEvent> {
        return histories[cityId]?.events ?: emptyList()
    }

    private fun saveHistory(history: CityHistory) {
        val file = File(historyDir, "${history.cityId}.json")
        try {
            file.writeText(gson.toJson(history))
        } catch (e: Exception) {
            plugin.logger.severe("Failed to save history for city ${history.cityId}: ${e.message}")
        }
    }

    private fun loadAll() {
        if (!historyDir.exists()) return
        historyDir.listFiles { _, name -> name.endsWith(".json") }?.forEach { file ->
            try {
                val history = gson.fromJson(file.readText(), CityHistory::class.java)
                histories[history.cityId] = history
            } catch (e: Exception) {
                plugin.logger.warning("Failed to load history file ${file.name}: ${e.message}")
            }
        }
    }
}
