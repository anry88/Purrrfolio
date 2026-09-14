package com.anry88.purrrfolio.i18n

object Messages {
    private val texts: Map<String, Pair<String, String>> = mapOf(
        "welcome" to (
            "🐾 *Purrrfolio* — a cozy collectible card game about kawaii cats.\n\n" +
                "Collect cards, open fluffy packs, complete themed sets and trade duplicates with friends.\n\n" +
                "Main commands:\n" +
                "/collection — your collection\n" +
                "/pack — open a pack for fish 🐟\n" +
                "/themes — theme progress\n" +
                "/trade — trade duplicates\n" +
                "/market — card market\n" +
                "/daily — daily reward\n" +
                "/language — change language\n" +
                "/help — help" to
            "🐾 *Purrrfolio* — уютная коллекционная игра про кавайных котиков.\n\n" +
                "Собирай карточки, открывай пушистые наборы, завершай тематические коллекции и обменивай дубликаты с друзьями.\n\n" +
                "Основные команды:\n" +
                "/collection — твоя коллекция\n" +
                "/pack — открыть набор за рыбки 🐟\n" +
                "/themes — прогресс по темам\n" +
                "/trade — обмен дубликатами\n" +
                "/market — биржа карточек\n" +
                "/daily — ежедневная награда\n" +
                "/language — сменить язык\n" +
                "/help — справка"
            ),
        "help" to (
            "*Purrrfolio commands*\n\n" +
                "/start — register and open the menu\n" +
                "/collection — list your cards and duplicates\n" +
                "/pack — open a Fluffy Pack (3 cards)\n" +
                "/themes — theme progress and set bonuses\n" +
                "/trade @username card_id — offer a 1:1 trade\n" +
                "/market list card_id price — list a duplicate on the market\n" +
                "/market browse — view active listings\n" +
                "/daily — claim daily fish\n" +
                "/profile — balance and stats\n" +
                "/language — change language" to
            "*Команды Purrrfolio*\n\n" +
                "/start — регистрация и меню\n" +
                "/collection — список карточек и дубликатов\n" +
                "/pack — открыть «Пушистый набор» (3 карточки)\n" +
                "/themes — прогресс по темам и бонусы за полную коллекцию\n" +
                "/trade @username card_id — предложить обмен 1:1\n" +
                "/market list card_id price — выставить дубликат на биржу\n" +
                "/market browse — посмотреть активные лоты\n" +
                "/daily — получить ежедневные рыбки\n" +
                "/profile — баланс и статистика\n" +
                "/language — сменить язык"
            ),
        "menu.collection" to ("🗂 Collection" to "🗂 Коллекция"),
        "menu.pack" to ("🎁 Pack" to "🎁 Набор"),
        "menu.themes" to ("📚 Themes" to "📚 Темы"),
        "menu.trade" to ("🤝 Trade" to "🤝 Обмен"),
        "menu.market" to ("🏪 Market" to "🏪 Биржа"),
        "menu.profile" to ("👤 Profile" to "👤 Профиль"),
        "menu.language" to ("🌐 Language" to "🌐 Язык"),
        "language.title" to ("🌐 Choose your language:" to "🌐 Выбери язык:"),
        "language.changed" to ("Language set to English." to "Язык изменён на русский."),
        "pack.insufficient" to (
            "Not enough fish! A pack costs %s 🐟, you have %s 🐟." to
                "Недостаточно рыбок! Набор стоит %s 🐟, а у тебя %s 🐟."
            ),
        "pack.opening" to (
            "Opening a pack... Spent %s 🐟 (balance: %s 🐟)" to
                "Открываю набор... Списано %s 🐟 (остаток: %s 🐟)"
            ),
        "pack.opened" to ("Pack opened!" to "Набор открыт!"),
        "pack.newCard" to (" ✨ NEW" to " ✨ НОВАЯ"),
        "collection.empty" to (
            "Your collection is empty. Open your first /pack!" to
                "Твоя коллекция пока пуста. Открой свой первый /pack!"
            ),
        "collection.title" to ("🗂 *Your collection* (%s / %s)" to "🗂 *Твоя коллекция* (%s / %s)"),
        "collection.more" to ("...and %s more cards." to "...и еще %s карт."),
        "themes.title" to ("📚 *Collection themes*" to "📚 *Темы коллекции*"),
        "profile.title" to ("👤 *%s*" to "👤 *%s*"),
        "profile.player" to ("Player" to "Игрок"),
        "profile.fish" to ("🐟 Fish: %s" to "🐟 Рыбки: %s"),
        "profile.unique" to ("🃏 Unique cards: %s / %s" to "🃏 Уникальных карточек: %s / %s"),
        "profile.themes" to ("🎯 Completed themes: %s / %s" to "🎯 Завершённых тем: %s / %s"),
        "trade.hint" to (
            "Trade duplicates: `/trade @username sleepy` — offer a card to a friend.\nYou need at least 2 copies of a card." to
                "Обмен дубликатами: `/trade @username sleepy` — предложить карточку другу.\nНужен минимум 2 копии одной карточки."
            ),
        "market.hint" to (
            "Market: `/market list sleepy 40` — sell a duplicate.\n`/market browse` — view listings." to
                "Биржа: `/market list sleepy 40` — продать дубликат.\n`/market browse` — посмотреть лоты."
            ),
        "daily.already" to (
            "Daily reward already claimed! Come back in %s h." to
                "Ежедневная награда уже получена! Возвращайся через %s ч."
            ),
        "daily.reward" to (
            "Daily reward: +%s 🐟\nYour balance: %s 🐟" to
                "Ежедневная награда: +%s 🐟\nТвой баланс: %s 🐟"
            ),
        "unknownCommand" to ("Unknown command. Send /help." to "Неизвестная команда. Напиши /help."),
        "unknownText" to ("Use /help to see available commands." to "Используй /help, чтобы увидеть доступные команды."),
        "callback.underDevelopment" to ("This section is under development." to "Раздел в разработке."),
        "themeStatus.claimed" to ("✅ bonus claimed" to "✅ бонус получен"),
        "themeStatus.completed" to ("🎁 bonus available" to "🎁 бонус доступен"),
        "themeStatus.inProgress" to ("in progress" to "в процессе"),
    )

    fun t(key: String, locale: GameLocale, vararg args: Any?): String {
        val (en, ru) = texts[key]
            ?: throw IllegalArgumentException("Unknown message key: $key")
        val template = when (locale) {
            GameLocale.RU -> ru
            GameLocale.EN -> en
        }
        return if (args.isEmpty()) template else String.format(template, *args)
    }
}