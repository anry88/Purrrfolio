package com.anry88.purrrfolio.i18n

object Messages {
    private val texts: Map<String, Pair<String, String>> = mapOf(
        "welcome" to (
            "🐾 *Purrrfolio* — a cozy collectible card game about kawaii cats.\n\n" +
                "Collect cards, open fluffy packs, complete themed sets and trade duplicates.\n\n" +
                "Main commands:\n" +
                "/collection — your collection\n" +
                "/pack — open a pack\n" +
                "/buy — get more packs with Telegram Stars\n" +
                "/trade — random trade\n" +
                "/market — card market\n" +
                "/language — change language\n" +
                "/help — help" to
            "🐾 *Purrrfolio* — уютная коллекционная игра про кавайных котиков.\n\n" +
                "Собирай карточки, открывай пушистые наборы, завершай тематические коллекции и обменивай дубликаты.\n\n" +
                "Основные команды:\n" +
                "/collection — твоя коллекция\n" +
                "/pack — открыть набор\n" +
                "/buy — купить наборы за Telegram Stars\n" +
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
                "/buy — buy packs with Telegram Stars\n" +
                "/trade — random trade duplicates\n" +
                "/market — list and trade cards\n" +
                "/language — change language\n" +
                "/paysupport — payment support" to
            "*Команды Purrrfolio*\n\n" +
                "/start — регистрация и выбор языка\n" +
                "/collection — список карточек и дубликатов\n" +
                "/pack — открыть набор (3 карточки)\n" +
                "/buy — купить наборы за Telegram Stars\n" +
                "/trade — случайный обмен дубликатами\n" +
                "/market — выставить и обменять карточки\n" +
                "/language — сменить язык\n" +
                "/paysupport — поддержка платежей"
            ),
        "menu.collection" to ("🗂 Collection" to "🗂 Коллекция"),
        "menu.pack" to ("🎁 Pack" to "🎁 Набор"),
        "menu.trade" to ("🎲 Random Trade" to "🎲 Случайный обмен"),
        "menu.market" to ("🏪 Market" to "🏪 Биржа"),
        "menu.language" to ("🌐 Language" to "🌐 Язык"),
        "menu.buy" to ("⭐ Buy packs" to "⭐ Купить наборы"),
        "language.title" to ("🌐 Choose your language:" to "🌐 Выбери язык:"),
        "language.changed" to ("Language set to English." to "Язык изменён на русский."),
        "pack.noPacks" to (
            "No packs left. Grab more with /buy using Telegram Stars — or claim your free card below." to
                "Наборы закончились. Возьми ещё через /buy за Telegram Stars — или забери бесплатную карточку ниже."
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
        "card.freeAvailable" to (
            "🎁 Your free card is ready! Tap the button to claim it." to
                "🎁 Бесплатная карточка готова! Нажми кнопку, чтобы забрать её."
            ),
        "card.claim" to ("🎁 Claim free card" to "🎁 Забрать карточку"),
        "card.nextFreeIn" to (
            "Next free card in %s hours." to
                "Следующая бесплатная карточка через %s часов."
            ),
        "buy.title" to (
            "⭐ *Get more packs*\n\n1 pack — 5 Stars\n3 packs — 12 Stars\n5 packs — 16 Stars\n10 packs — 25 Stars\n\nPaid packs are added to your stash and do not open automatically. Use /pack to open them." to
                "⭐ *Купить наборы*\n\n1 набор — 5 Stars\n3 набора — 12 Stars\n5 наборов — 16 Stars\n10 наборов — 25 Stars\n\nКупленные наборы добавляются в запас и не открываются сами. Открой их через /pack."
            ),
        "buy.option1" to ("1 pack — 5 ⭐" to "1 набор — 5 ⭐"),
        "buy.option3" to ("3 packs — 12 ⭐" to "3 набора — 12 ⭐"),
        "buy.option5" to ("5 packs — 16 ⭐" to "5 наборов — 16 ⭐"),
        "buy.option10" to ("10 packs — 25 ⭐" to "10 наборов — 25 ⭐"),
        "buy.invoiceTitle" to ("Purrrfolio packs (%s pcs)" to "Наборы Purrrfolio (%s шт.)"),
        "buy.invoiceDesc" to ("Fluffy card packs. They are added to your stash and open with /pack." to "Пушистые наборы с карточками. Добавляются в запас и открываются через /pack."),
        "buy.success" to (
            "⭐ Payment received! %s packs added. Use /pack to open them." to
                "⭐ Оплата получена! Добавлено наборов: %s. Открой их через /pack."
            ),
        "paysupport.text" to (
            "💳 *Payment support*\n\nIf Stars were charged but packs did not arrive, tell us the date, amount of Stars and your Telegram @username. We check the payment log and credit missing packs or issue a Stars refund." to
                "💳 *Поддержка платежей*\n\nЕсли Stars списались, а наборы не пришли, напиши дату, сумму Stars и свой @username в Telegram. Мы проверим журнал оплат и доначислим наборы или вернём Stars."
            ),
        "collection.empty" to (
            "Your collection is empty. Open your first /pack!" to
                "Твоя коллекция пока пуста. Открой свой первый /pack!"
            ),
        "collection.title" to ("🗂 *Your collection* (%s / %s)" to "🗂 *Твоя коллекция* (%s / %s)"),
        "collection.more" to ("...and %s more cards." to "...и еще %s карт."),
        "collection.page" to ("🗂 *Collections* (page %s)\n\n%s" to "🗂 *Коллекции* (страница %s)\n\n%s"),
        "collection.next" to ("Next ➡️" to "Далее ➡️"),
        "collection.prev" to ("⬅️ Back" to "⬅️ Назад"),
        "collection.openFirst" to ("Open the first card ➡️" to "Открыть первую карточку ➡️"),
        "profile.title" to ("👤 *%s*" to "👤 *%s*"),
        "profile.player" to ("Player" to "Игрок"),
        "profile.unique" to ("🃏 Unique cards: %s / %s" to "🃏 Уникальных карточек: %s / %s"),
        "profile.packs" to ("🎁 Available packs: %s" to "🎁 Доступных наборов: %s"),
        "themeStatus.claimed" to ("claimed" to "получен"),
        "themeStatus.completed" to ("completed!" to "собрана!"),
        "themeStatus.inProgress" to ("in progress" to "в процессе"),
        "themes.title" to ("📚 *Themes*" to "📚 *Темы*"),
        "trade.hint" to (
            "🎲 *Random Trade*\n\nAdd duplicate cards to the pool and automatically exchange them with other players' duplicates." to
                "🎲 *Случайный обмен*\n\nДобавляй дубликаты в пул и автоматически обменивай их на дубликаты других игроков."
            ),
        "trade.added" to (
            "Card added to random trade pool!" to
                "Карточка добавлена в пул случайного обмена!"
            ),
        "trade.addedToPool" to (
            "Card added to trade pool. Waiting for a match..." to
                "Карточка добавлена в пул обмена. Ожидание пары..."
            ),
        "trade.matched" to (
            "Trade matched! You received: %s" to
                "Обмен совершён! Ты получил: %s"
            ),
        "trade.noDuplicates" to (
            "You have no duplicate cards to trade." to
                "У тебя нет дубликатов для обмена."
            ),
        "trade.pickCard" to (
            "Pick a duplicate to trade:" to
                "Выбери дубликат для обмена:"
            ),
        "trade.waiting" to (
            "⏳ Waiting in the pool (swaps automatically when another player's different card arrives):" to
                "⏳ Ждут в пуле (обменяются сами, когда придёт другая карточка другого игрока):"
            ),
        "market.browse" to (
            "🔍 Browse others' cards" to
                "🔍 Смотреть чужие карточки"
            ),
        "trade.offerButton" to ("🎲 Trade %s" to "🎲 Обменять %s"),
        "market.hint" to (
            "Market: list your duplicates and offer them for other players' cards.\nList a card to start trading." to
                "Биржа: выставь свои дубликаты и предложи их за карточки других игроков.\nВыставь карточку, чтобы начать торговлю."
            ),
        "market.listed" to (
            "Card listed on market." to
                "Карточка выставлена на биржу."
            ),
        "market.returned" to (
            "Card returned to your collection." to
                "Карточка возвращена в коллекцию."
            ),
        "market.noListings" to (
            "No active listings on market." to
                "Нет активных лотов на бирже."
            ),
        "market.myListings" to (
            "Your listings:" to
                "Твои лоты:"
            ),
        "market.noDuplicates" to (
            "You don't have duplicates to list." to
                "У тебя нет дубликатов для выставления."
            ),
        "market.selectCard" to (
            "Select a duplicate to list:" to
                "Выбери дубликат для выставления:"
            ),
        "market.cardListing" to (
            "🎴 %s (%s)\n🔄 Offered %s times" to
                "🎴 %s (%s)\n🔄 Предложено %s раз"
            ),
        "market.browsingListings" to (
            "Active market listings:\n\n%s\n\n%s" to
                "Активные лоты на бирже:\n\n%s\n\n%s"
            ),
        "market.browseHint" to (
            "Pick someone's card, then choose one of your listed cards to offer in return." to
                "Выбери чужую карточку, затем предложи одну из своих выставленных карточек взамен."
            ),
        "market.chooseOffer" to (
            "Choose your listed card to offer for %s:" to
                "Выбери свою выставленную карточку для обмена на %s:"
            ),
        "market.offerMade" to (
            "Trade offer sent! Waiting for response..." to
                "Предложение отправлено! Ожидаю ответ..."
            ),
        "market.offerReceived" to (
            "New trade offer for: %s\nOffered: %s\n\nAccept or reject?" to
                "Новое предложение за: %s\nПредлагает: %s\n\nПринять или отклонить?"
            ),
        "market.offerAccepted" to (
            "Trade accepted! Cards exchanged." to
                "Обмен принят! Карточки обменены."
            ),
        "market.offerRejected" to (
            "Trade rejected." to
                "Обмен отклонен."
            ),
        "market.settlementFailed" to (
            "Failed to complete trade. Please contact support." to
                "Не удалось завершить обмен. Свяжись с поддержкой."
            ),
        "market.listButton" to ("🏪 List %s" to "🏪 Выставить %s"),
        "market.returnButton" to ("↩️ Return %s" to "↩️ Вернуть %s"),
        "market.offerButton" to ("🔄 Offer for %s" to "🔄 Предложить за %s"),
        "market.accept" to ("✅ Accept" to "✅ Принять"),
        "market.reject" to ("❌ Reject" to "❌ Отклонить"),
        "error.general" to (
            "An error occurred. Please try again." to
                "Произошла ошибка. Пожалуйста, попробуй ещё раз."
            ),
        "unknownCommand" to ("Unknown command. Send /help." to "Неизвестная команда. Напиши /help."),
        "unknownText" to ("Use /help to see available commands." to "Используй /help, чтобы увидеть доступные команды."),
        "callback.underDevelopment" to ("This section is under development." to "Раздел в разработке."),
        "gallery.back" to ("🔙 Back" to "🔙 Назад"),
        "gallery.viewCards" to ("🖼 View cards" to "🖼 Смотреть карточки"),
        "gallery.owned" to ("In collection: ×%s" to "В коллекции: ×%s"),
        "gallery.missing" to ("Not collected yet" to "Ещё не собрана"),
        "gallery.counter" to ("%s / %s" to "%s / %s"),
        "gallery.trade" to ("🎲 Random Trade" to "🎲 Случайный обмен"),
        "gallery.listMarket" to ("🏪 List for Trade" to "🏪 Выставить на биржу"),
        "cmd.start" to ("Open the menu" to "Открыть меню"),
        "cmd.pack" to ("Open a pack" to "Открыть набор"),
        "cmd.collection" to ("View cat collection" to "Посмотреть коллекцию котиков"),
        "cmd.buy" to ("Buy packs with Stars" to "Купить наборы за Stars"),
        "cmd.trade" to ("Random trade" to "Случайный обмен"),
        "cmd.market" to ("Card market" to "Биржа карточек"),
        "cmd.language" to ("Switch language" to "Сменить язык"),
        "cmd.help" to ("Help" to "Помощь"),
        "cmd.paysupport" to ("Payment support" to "Поддержка платежей"),
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
