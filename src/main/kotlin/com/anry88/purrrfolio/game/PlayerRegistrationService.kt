package com.anry88.purrrfolio.game

import com.anry88.purrrfolio.config.PurrrfolioProperties
import com.anry88.purrrfolio.models.User
import com.anry88.purrrfolio.repository.PackLedgerRepository
import com.anry88.purrrfolio.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

data class PlayerRegistrationResult(
    val user: User,
    val created: Boolean,
)

@Service
class PlayerRegistrationService(
    private val properties: PurrrfolioProperties,
    private val userRepository: UserRepository,
    private val packLedgerRepository: PackLedgerRepository,
    transactionManager: PlatformTransactionManager,
) {
    private val transactionTemplate = TransactionTemplate(transactionManager)

    /** Creates the player and their starter entitlement in one transaction. */
    fun registerIfMissing(telegramUserId: Long, language: String, registrationSource: String): PlayerRegistrationResult =
        transactionTemplate.execute {
            val created = userRepository.createIfAbsent(telegramUserId, language, registrationSource)
            if (created != null) {
                packLedgerRepository.addPacks(
                    created.id,
                    STARTER_PACK_SOURCE,
                    properties.economy.starterPacks,
                )
                PlayerRegistrationResult(created, created = true)
            } else {
                val existing = checkNotNull(userRepository.findByTelegramUserId(telegramUserId)) {
                    "Conflicting player registration was not visible after INSERT conflict"
                }
                PlayerRegistrationResult(existing, created = false)
            }
        } ?: error("Player registration transaction returned no result")

    companion object {
        const val STARTER_PACK_SOURCE = "starter"
    }
}
