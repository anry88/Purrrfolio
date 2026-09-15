package com.anry88.purrrfolio.i18n

enum class GameLocale(val code: String) {
    EN("en"),
    RU("ru");

    companion object {
        fun fromCode(code: String?): GameLocale =
            if (code?.equals("ru", ignoreCase = true) == true) RU else EN

        fun fromTelegramLanguageCode(languageCode: String?): GameLocale =
            if (languageCode?.startsWith("ru", ignoreCase = true) == true) RU else EN
    }
}