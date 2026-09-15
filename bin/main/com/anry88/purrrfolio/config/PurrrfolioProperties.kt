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
    val botUsername: String = "purrrfolio_bot",
    val pollingEnabled: Boolean = false,
)

data class EconomyProperties(
    val starterFish: Int = 100,
    val dailyFish: Int = 25,
    val packCostFish: Int = 50,
    val cardsPerPack: Int = 3,
)
