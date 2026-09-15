package com.anry88.purrrfolio.i18n

object Messages {
    private val texts: Map<String, Pair<String, String>> = mapOf(
        "welcome" to (
            "🐾 *Purrrfolio* — a cozy collectible card game about kawaii cats.\n\n" +
                "Collect cards, open fluffy packs, complete themed sets and trade duplicates.\n\n" +
                "Main commands:\n" +
                "/collection — your collection\n" +
                "/pack — open a pack\n" +
                "/trade — random trade\n" +
                "/market — card market\n" +
                "/language — change language\n" +
                "/help — help" to
            "🐾 *Purrrfolio* — уютная коллекционная игра про кавайных котиков.\n\n" +
                "Собирай карточки, открывай пушистые наборы, завершай тематические коллекции и обменивай дубликаты.\n\n" +
                "Основные команды:\n" +
                "/collection — твоя коллекция\n" +
                "/pack — открыть набор\n" +
                "/trade — случайный обмен\n" +
                "/market — биржа карточек\n" +
                "/language — сменить язык\n" +
                "/help — справка"
            ),
        "help" to (
            "*Purrrfolio commands*\n\n" +
                "/start — register and choose language\n" +
                "/collection — list your cards and duplicates\n" +
                "/pack — open a pack (3 cards)\n" +
                "/trade — random trade duplicates\n" +
                "/market — list and trade cards\n" +
                "/language — change language" to
            "*Команды Purrrfolio*\n\n" +
                "/start — регистрация и выбор языка\n" +
                "/collection — список карточек и дубликатов\n" +
                "/pack — открыть набор (3 карточки)\n" +
                "/trade — случайный обмен дубликатами\n" +
                "/market — выставить и обменять карточки\n" +
                "/language — сменить язык"
            ),
        "menu.collection" to ("🗂 Collection" to "🗂 Коллекция"),
        "menu.pack" to ("🎁 Pack" to "🎁 Набор"),
        "menu.trade" to ("🎲 Random Trade" to "🎲 Случайный обмен"),
        "menu.market" to ("🏪 Market" to "🏪 Биржа"),
        "menu.language" to ("🌐 Language" to "🌐 Язык"),
        "language.title" to ("🌐 Choose your language:" to "🌐 Выбери язык:"),
        "language.changed" to ("Language set to English." to "Язык изменён на русский."),
        "pack.noPacks" to (
            "No packs available. Get free packs every 23 hours or buy with Telegram Stars." to
                "Нет доступных наборов. Бесплатные наборы каждые 23 часа или покупка за Telegram Stars."
            ),
        "pack.opening" to (
            "Opening a pack..." to
                "Открываю набор..."
            ),
        "pack.opened" to ("Pack opened!" to "Набор открыт!"),
        "pack.newCard" to (" ✨ NEW" to " ✨ НОВАЯ"),
        "pack.starter" to (
            "🎁 You received %s starter packs! Open them to begin your collection." to
                "🎁 Ты получил %s стартовых наборов! Открой их, чтобы начать коллекцию."
            ),
        "pack.freeAvailable" to (
            "🎁 Your free pack is ready! Use /pack to open it." to
                "🎁 Твой бесплатный набор готов! Используй /pack, чтобы открыть его."
            ),
        "pack.nextFreeIn" to (
            "Next free pack in %s hours." to
                "Следующий бесплатный набор через %s часов."
            ),
        "collection.empty" to (
            "Your collection is empty. Open your first /pack!" to
                "Твоя коллекция пока пуста. Открой свой первый /pack!"
            ),
        "collection.title" to ("🗂 *Your collection* (%s / %s)" to "🗂 *Твоя коллекция* (%s / %s)"),
        "collection.more" to ("...and %s more cards." to "...и еще %s карт."),
        "profile.title" to ("👤 *%s*" to "👤 *%s*"),
        "profile.player" to ("Player" to "Игрок"),
        "profile.unique" to ("🃏 Unique cards: %s / %s" to "🃏 Уникальных карточек: %s / %s"),
        "profile.packs" to ("� Available packs: %s" to "� Доступных наборов: %s"),
        "trade.hint" to (
            "Random Trade: offer a duplicate to the pool and get a random different card in return.\nYou need at least 2 copies of a card." to
                "Случайный обмен: предложи дубликат в пул и получи случайную другую карточку взамен.\nНужен минимум 2 копии одной карточки."
            ),
        "trade.addedToPool" to (
            "Card added to trade pool. Waiting for a match..." to
                "Карточка добавлена в пул обмена. Ожидание пары..."
            ),
        "trade.matched" to (
            "🎉 Trade matched! You received a new card." to
                "🎉 Обмен совершен! Ты получил новую карточку."
            ),
        "trade.noDuplicates" to (
            "No duplicates available for trade." to
                "Нет дубликатов для обмена."
            ),
        "market.hint" to (
            "Market: list your duplicates and offer them for other players' cards.\nList a card to start trading." to
                "Биржа: выставь свои дубликаты и предложи их за карточки других игроков.\nВыставь карточку, чтобы начать торговлю."
            ),
        "market.listed" to (
            "Card listed on market." to
                "Карточка выставлена на биржу."
            ),
        "market.noListings" to (
            "No active listings on market." to
                "Нет активных лотов на бирже."
            ),
        "unknownCommand" to ("Unknown command. Send /help." to "Неизвестная команда. Напиши /help."),
        "unknownText" to ("Use /help to see available commands." to "Используй /help, чтобы увидеть доступные команды."),
        "callback.underDevelopment" to ("This section is under development." to "Раздел в разработке."),
        "gallery.back" to ("🔙 Back" to "🔙 Назад"),
        "gallery.viewCards" to ("🖼 View cards" to "🖼 Смотреть карточки"),
        "gallery.owned" to ("In collection: ×%s" to "В коллекции: ×%s"),
        "gallery.missing" to ("Not collected yet" to "Ещё не собрана"),
        "gallery.counter" to ("%s / %s" to "%s / %s"),
        "cmd.start" to ("Open the menu" to "Открыть меню"),
        "cmd.pack" to ("Open a pack" to "Открыть набор"),
        "cmd.collection" to ("View cat collection" to "Посмотреть коллекцию котиков"),
        "cmd.trade" to ("Random trade" to "Случайный обмен"),
        "cmd.market" to ("Card market" to "Биржа карточек"),
        "cmd.language" to ("Switch language" to "Сменить язык"),
        "cmd.help" to ("Help" to "Помощь"),
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