package com.projectatlas.dungeon

import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.EntityType

/**
 * Defines the visual and atmospheric theme of a dungeon.
 */
enum class DungeonTheme(
    val wall: Material,
    val floor: Material,
    val ceiling: Material,
    val detail: Material, // Pillars, trim, etc.
    val liquid: Material,
    val particle: Particle?,
    val ambientSound: Sound,
    val mobs: List<EntityType>,
    val boss: EntityType
) {
    CRYPT(
        wall = Material.DEEPSLATE_TILES,
        floor = Material.POLISHED_DEEPSLATE,
        ceiling = Material.COBBLED_DEEPSLATE,
        detail = Material.CRACKED_DEEPSLATE_TILES,
        liquid = Material.WATER,
        particle = Particle.ASH, // Gloom
        ambientSound = Sound.AMBIENT_CAVE,
        mobs = listOf(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER),
        boss = EntityType.WITHER_SKELETON
    ),
    INFERNAL(
        wall = Material.NETHER_BRICKS,
        floor = Material.RED_NETHER_BRICKS,
        ceiling = Material.NETHERRACK,
        detail = Material.LAVA,
        liquid = Material.LAVA,
        particle = Particle.DRIPPING_LAVA,
        ambientSound = Sound.AMBIENT_NETHER_WASTES_MOOD,
        mobs = listOf(EntityType.ZOMBIFIED_PIGLIN, EntityType.BLAZE, EntityType.MAGMA_CUBE),
        boss = EntityType.GHAST
    ),
    TEMPLE(
        wall = Material.STONE_BRICKS,
        floor = Material.MOSSY_STONE_BRICKS,
        ceiling = Material.STONE,
        detail = Material.CHISELED_STONE_BRICKS,
        liquid = Material.WATER,
        particle = Particle.SPORE_BLOSSOM_AIR,
        ambientSound = Sound.MUSIC_DISC_RELIC, // Ancient feel
        mobs = listOf(EntityType.STRAY, EntityType.HUSK, EntityType.CAVE_SPIDER),
        boss = EntityType.IRON_GOLEM // Corrupted guardian
    )
}

/**
 * Represents a single node in the dungeon graph.
 */
enum class RoomType {
    ENTRANCE,
    HALLWAY,
    COMBAT_ARENA,
    TRAP_ROOM,
    PUZZLE_ROOM,
    TREASURE_ROOM,
    BOSS_ROOM
}

data class DungeonRoom(
    val x: Int, // Grid X
    val z: Int, // Grid Z
    val type: RoomType,
    var cleared: Boolean = false,
    var active: Boolean = false
)
