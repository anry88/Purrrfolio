package com.anry88.purrrfolio.game

import com.anry88.purrrfolio.config.PurrrfolioProperties
import com.anry88.purrrfolio.models.User
import com.anry88.purrrfolio.repository.PackLedgerRepository
import com.anry88.purrrfolio.repository.ReferralRewardRepository
import com.anry88.purrrfolio.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.OffsetDateTime
import java.time.ZoneId

data class PlayerRegistrationResult(
    val user: User,
    val created: Boolean,
    val referralReward: ReferralRewardResult? = null,
)

data class ReferralRewardResult(
    val referrer: User,
    val packsEach: Int,
    val referrerRewarded: Boolean,
)

@Service
class PlayerRegistrationService(
    private val properties: PurrrfolioProperties,
    private val userRepository: UserRepository,
    private val packLedgerRepository: PackLedgerRepository,
    private val referralRewardRepository: ReferralRewardRepository,
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
                PlayerRegistrationResult(
                    user = created,
                    created = true,
                    referralReward = grantReferralReward(created, registrationSource),
                )
            } else {
                val existing = checkNotNull(userRepository.findByTelegramUserId(telegramUserId)) {
                    "Conflicting player registration was not visible after INSERT conflict"
                }
                PlayerRegistrationResult(existing, created = false)
            }
        } ?: error("Player registration transaction returned no result")

    private fun grantReferralReward(created: User, registrationSource: String): ReferralRewardResult? {
        val packsEach = properties.economy.referralBonusPacks
        if (packsEach <= 0) return null

        val referrerId = registrationSource
            .takeIf { it.startsWith(REFERRAL_PREFIX) }
            ?.removePrefix(REFERRAL_PREFIX)
            ?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
            ?.toLongOrNull()
            ?.takeIf { it > 0 && it != created.id }
            ?: return null

        val referrer = userRepository.findById(referrerId) ?: return null
        check(userRepository.lockById(referrer.id)) { "Referrer disappeared during registration" }

        val monthlyLimit = properties.economy.referralMonthlyLimit
        val monthStart = OffsetDateTime.now(ZoneId.of(properties.gameTimezone))
            .withDayOfMonth(1)
            .toLocalDate()
            .atStartOfDay(ZoneId.of(properties.gameTimezone))
            .toOffsetDateTime()
        val referrerRewarded = monthlyLimit > 0 &&
            referralRewardRepository.countForReferrerSince(referrer.id, monthStart) < monthlyLimit

        if (!referralRewardRepository.claim(created.id, referrer.id, packsEach, referrerRewarded)) return null

        packLedgerRepository.addPacks(created.id, REFERRAL_JOINER_PACK_SOURCE, packsEach)
        if (referrerRewarded) {
            packLedgerRepository.addPacks(referrer.id, REFERRAL_REFERRER_PACK_SOURCE, packsEach)
        }
        return ReferralRewardResult(
            referrer = referrer,
            packsEach = packsEach,
            referrerRewarded = referrerRewarded,
        )
    }

    companion object {
        const val STARTER_PACK_SOURCE = "starter"
        const val REFERRAL_JOINER_PACK_SOURCE = "referral_joiner"
        const val REFERRAL_REFERRER_PACK_SOURCE = "referral_referrer"
        private const val REFERRAL_PREFIX = "ref_"
    }
}
