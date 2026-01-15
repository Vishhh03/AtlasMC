package com.projectatlas.economy

import com.projectatlas.AtlasPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.block.Barrel
import org.bukkit.block.Chest
import org.bukkit.block.Sign
import org.bukkit.block.TileState
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.SignChangeEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.UUID

/**
 * Player Market System (Chest Shops)
 * - Players create shops using Chests/Barrels and Signs.
 * - Format:
 *   [SHOP]
 *   <Price>
 *   <Description>
 * - The item to sell is the FIRST item in the chest (auto-detected).
 */
class MarketManager(private val plugin: AtlasPlugin) : Listener {

    private val shopKey = NamespacedKey(plugin, "market_shop")
    private val priceKey = NamespacedKey(plugin, "market_price")
    private val ownerKey = NamespacedKey(plugin, "market_owner")

    // ══════════════════════════════════════════════════════════════
    // SHOP CREATION
    // ══════════════════════════════════════════════════════════════

    @EventHandler
    fun onSignChange(event: SignChangeEvent) {
        val lines = event.lines()
        val header = (lines[0] as? Component)?.let { 
            net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(it) 
        } ?: ""

        if (!header.equals("[SHOP]", ignoreCase = true)) return

        val player = event.player
        val block = event.block
        
        // 1. Check if sign is attached to a container
        val attachedBlock = when (val data = block.blockData) {
            is org.bukkit.block.data.type.WallSign -> block.getRelative(data.facing.oppositeFace)
            else -> block.getRelative(org.bukkit.block.BlockFace.DOWN)
        }

        if (attachedBlock.type != Material.CHEST && attachedBlock.type != Material.BARREL) {
            player.sendMessage(Component.text("Shop signs must be placed on a Chest or Barrel!", NamedTextColor.RED))
            return
        }

        // 2. Parse Price
        val priceText = (lines[1] as? Component)?.let {
            net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(it)
        } ?: ""
        
        val price = priceText.toDoubleOrNull()
        if (price == null || price <= 0) {
            player.sendMessage(Component.text("Invalid price! Line 2 must be a number.", NamedTextColor.RED))
            return
        }

        // 3. Mark as Shop
        event.line(0, Component.text("[SHOP]", NamedTextColor.BLUE, TextDecoration.BOLD))
        event.line(1, Component.text("$price g", NamedTextColor.GOLD))
        event.line(3, Component.text(player.name, NamedTextColor.GRAY))

        val signState = block.state as Sign
        signState.persistentDataContainer.set(shopKey, PersistentDataType.BYTE, 1)
        signState.persistentDataContainer.set(priceKey, PersistentDataType.DOUBLE, price)
        signState.persistentDataContainer.set(ownerKey, PersistentDataType.STRING, player.uniqueId.toString())
        signState.update()

        player.sendMessage(Component.text("Shop created! Put items in the chest.", NamedTextColor.GREEN))
        player.sendMessage(Component.text("The first item in the chest determines what is sold.", NamedTextColor.GRAY))
    }

    // ══════════════════════════════════════════════════════════════
    // TRANSACTION HANDLING
    // ══════════════════════════════════════════════════════════════

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        val block = event.clickedBlock ?: return

        // Only handle Sign interactions
        if (block.state !is Sign) return
        val sign = block.state as Sign

        // Is it a shop?
        if (!sign.persistentDataContainer.has(shopKey, PersistentDataType.BYTE)) return

        val player = event.player
        val price = sign.persistentDataContainer.get(priceKey, PersistentDataType.DOUBLE) ?: 0.0
        val ownerIdStr = sign.persistentDataContainer.get(ownerKey, PersistentDataType.STRING) ?: return
        val ownerUuid = UUID.fromString(ownerIdStr)

        // Prevent buying from self
        if (player.uniqueId == ownerUuid) {
            player.sendMessage(Component.text("You can't buy from your own shop!", NamedTextColor.RED))
            // Maybe open settings?
            return
        }

        // Find Container
        val data = block.blockData
        val containerBlock = if (data is org.bukkit.block.data.type.WallSign) {
            block.getRelative(data.facing.oppositeFace)
        } else {
            block.getRelative(org.bukkit.block.BlockFace.DOWN)
        }

        val container = containerBlock.state as? org.bukkit.inventory.InventoryHolder ?: return
        val inventory = container.inventory

        // 1. Identify Item (First non-null item)
        val sampleItem = inventory.contents.firstOrNull { it != null && it.type != Material.AIR }
        
        if (sampleItem == null) {
            player.sendMessage(Component.text("This shop is out of stock!", NamedTextColor.RED))
            return
        }

        // 2. Check Buyer Balance
        val buyerProfile = plugin.identityManager.getPlayer(player.uniqueId) ?: return
        if (buyerProfile.balance < price) {
            player.sendMessage(Component.text("Insufficient funds! Cost: $price g", NamedTextColor.RED))
            return
        }

        // 3. Execute Transaction
        // Remove item from chest
        if (!removeItem(inventory, sampleItem)) {
            player.sendMessage(Component.text("Shop loop error (Stock changed mid-transaction).", NamedTextColor.RED))
            return
        }

        // Transfer Money
        buyerProfile.balance -= price
        
        // Tax Logic: 5% Global Tax (Sink) + City Tax
        var postTaxIncome = price
        val marketTax = price * 0.05
        postTaxIncome -= marketTax
        
        val city = plugin.cityManager.getCityAt(block.chunk)
        if (city != null) {
            val baseCityTax = price * (city.taxRate / 100.0)
            val infraBonus = city.infrastructure.getMarketTaxBonus() // +5% per level, etc.
            
            // City gets Base + Bonus (Bonus is generated, not taken from seller)
            val totalRevenue = baseCityTax * (1.0 + infraBonus)
            city.treasury += totalRevenue
            
            postTaxIncome -= baseCityTax
            plugin.cityManager.saveCity(city)
        }

        // Pay seller
        val sellerProfile = plugin.identityManager.getPlayer(ownerUuid)
        if (sellerProfile != null) {
            sellerProfile.balance += postTaxIncome
            // Optionally notify if online
            val seller = plugin.server.getPlayer(ownerUuid)
            seller?.sendMessage(Component.text("Shop: Sold ${sampleItem.type.name} to ${player.name} for $price g (${String.format("%.1f", postTaxIncome)} net)", NamedTextColor.GREEN))
            plugin.identityManager.saveProfile(ownerUuid) // Save seller immediately
        } else {
            // Offline seller handling
            val offlineProfile = plugin.identityManager.loadOfflineProfile(ownerUuid)
            if (offlineProfile != null) {
                offlineProfile.balance += postTaxIncome
                plugin.identityManager.saveOfflineProfile(offlineProfile)
            }
        }
        
        plugin.identityManager.saveProfile(player.uniqueId) // Save buyer

        // Give Item to Buyer
        val leftovers = player.inventory.addItem(sampleItem)
        if (leftovers.isNotEmpty()) {
            leftovers.values.forEach { player.world.dropItemNaturally(player.location, it) }
            player.sendMessage(Component.text("Inventory full! Item dropped.", NamedTextColor.YELLOW))
        }

        player.sendMessage(Component.text("Purchased ${sampleItem.amount}x ${sampleItem.type.name} for $price g", NamedTextColor.GREEN))
        player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f)
        
        // Update Sign Line 2 to show item name
        sign.line(2, Component.text(sampleItem.type.name.take(15), NamedTextColor.BLACK))
        sign.update()
    }

    private fun removeItem(inv: Inventory, item: ItemStack): Boolean {
        // Tries to remove ONE stack matching the sample item
        // Exact match check
        for ((index, slotItem) in inv.withIndex()) {
            if (slotItem != null && slotItem.isSimilar(item)) {
                inv.setItem(index, null) // Remove the whole stack found
                return true
            }
        }
        return false
    }
}
