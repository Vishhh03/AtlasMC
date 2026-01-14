package com.projectatlas.city

import org.bukkit.Material

enum class CityWonder(
    val displayName: String,
    val description: String,
    val requirements: Map<Material, Int>,
    val buffDescription: String
) {
    GREAT_LIBRARY(
        "The Great Library",
        "A repository of ancient knowledge.",
        mapOf(
            Material.BOOKSHELF to 1000,
            Material.PAPER to 5000,
            Material.DIAMOND to 100
        ),
        "+20% XP Gain for all members"
    ),
    INDUSTRIAL_FORGE(
        "Industrial Forge",
        "A massive smeltery fueled by magma.",
        mapOf(
            Material.IRON_BLOCK to 500,
            Material.LAVA_BUCKET to 1000,
            Material.OBSIDIAN to 2000
        ),
        "+10% Crafting Speed / Furnace Speed (Simulated)"
    ),
    GOLDEN_VAULT(
        "The Golden Vault",
        "A monument to wealth.",
        mapOf(
            Material.GOLD_BLOCK to 500,
            Material.EMERALD_BLOCK to 200
        ),
        "Daily Interest on Treasury (1%)"
    ),
    WAR_ACADEMY(
        "War Academy",
        "Training grounds for the elite.",
        mapOf(
            Material.NETHERITE_INGOT to 20,
            Material.DIAMOND_SWORD to 50,
            Material.SHIELD to 100
        ),
        "+2 Hearts in City Territory"
    )
}
