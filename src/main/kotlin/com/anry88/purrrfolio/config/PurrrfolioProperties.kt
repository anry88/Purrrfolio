package com.anry88.purrrfolio.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "purrrfolio")
data class PurrrfolioProperties(
    val publicBaseUrl: String = "http://localhost:8080",
    val gameTimezone: String = "UTC",
    val telegram: TelegramProperties = TelegramProperties(),
    val economy: EconomyProperties = EconomyProperties(),
    val marketing: MarketingProperties = MarketingProperties(),
    val notifications: NotificationProperties = NotificationProperties(),
)

data class NotificationProperties(
    val enabled: Boolean = true,
    val batchSize: Int = 100,
    val retryMinutes: Long = 5,
    val sendIntervalMs: Long = 250,
) {
    init {
        require(batchSize in 1..1000)
        require(retryMinutes > 0)
        require(sendIntervalMs in 50..10_000)
    }
}

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

data class MarketingProperties(
    val adminToken: String = "",
    val tiktok: TiktokMarketingProperties = TiktokMarketingProperties(),
)

data class TiktokMarketingProperties(
    val clientKey: String = "",
    val clientSecret: String = "",
    val redirectUri: String = "",
    val scopes: String = "user.info.basic,video.upload,video.publish,video.list",
    val demoVideoUrl: String = "",
)
