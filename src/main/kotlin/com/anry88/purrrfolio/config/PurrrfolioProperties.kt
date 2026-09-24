package com.anry88.purrrfolio.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "purrrfolio")
data class PurrrfolioProperties(
    val publicBaseUrl: String = "http://localhost:8080",
    val gameTimezone: String = "UTC",
    val telegram: TelegramProperties = TelegramProperties(),
    val economy: EconomyProperties = EconomyProperties(),
)

data class TelegramProperties(
    val botToken: String = "",
    val webhookSecret: String = "",
    val botUsername: String = "PurrrfolioBot",
    val pollingEnabled: Boolean = false,
    val adminTgId: Long = 0,
    val paymentPayloadSecret: String = "",
)

data class EconomyProperties(
    val starterPacks: Int = 3,
    val referralBonusPacks: Int = 5,
    val referralMonthlyLimit: Int = 10,
    val freePackIntervalHours: Int = 23,
    val freeCardIntervalHours: Int = 3,
    val cardsPerPack: Int = 3,
    val starsPricing: StarsPricing = StarsPricing(),
)

data class StarsPricing(
    val onePack: Int = 5,
    val threePacks: Int = 12,
    val fivePacks: Int = 16,
    val tenPacks: Int = 25,
)
