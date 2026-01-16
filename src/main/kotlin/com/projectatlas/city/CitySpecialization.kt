package com.projectatlas.city

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

enum class CitySpecialization(
    val displayName: String,
    val description: String,
    val color: NamedTextColor
) {
    NONE("None", "No specialization selected.", NamedTextColor.GRAY),
    
    ARCANE_SANCTUM(
        "Arcane Sanctum", 
        "Focuses on magic and stability. \nPassive Threat Reduction (-5%/hr). \nLower Spell Cooldowns.", 
        NamedTextColor.AQUA
    ),
    
    INDUSTRIAL_FORGE(
        "Industrial Forge", 
        "Focuses on economy and production. \nPassively generates Gold based on population. \nCheaper Upgrades.", 
        NamedTextColor.GOLD
    ),
    
    MILITARY_BASTION(
        "Military Bastion", 
        "Focuses on defense and conquest. \nDefenders are 2x stronger. \nSieges are easier to repel.", 
        NamedTextColor.RED
    );
}
