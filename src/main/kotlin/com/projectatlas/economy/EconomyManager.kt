package com.projectatlas.economy

import com.projectatlas.identity.IdentityManager
import java.util.UUID

class EconomyManager(private val identityManager: IdentityManager) {

    fun getBalance(uuid: UUID): Double {
        return identityManager.getPlayer(uuid)?.balance ?: 0.0
    }

    fun hasBalance(uuid: UUID, amount: Double): Boolean {
        return getBalance(uuid) >= amount
    }

    fun deposit(uuid: UUID, amount: Double) {
        val profile = identityManager.getPlayer(uuid) ?: return
        profile.balance += amount
    }

    fun withdraw(uuid: UUID, amount: Double): Boolean {
        val profile = identityManager.getPlayer(uuid) ?: return false
        if (profile.balance < amount) return false
        profile.balance -= amount
        return true
    }
    
    fun transfer(from: UUID, to: UUID, amount: Double): Boolean {
        if (!withdraw(from, amount)) return false
        deposit(to, amount)
        return true
    }
}
