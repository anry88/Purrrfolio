package com.anry88.purrrfolio.i18n

object Messages {
    private val texts: Map<String, Pair<String, String>> = mapOf(
        "welcome" to (
            "🐾 *Purrrfolio* — a cozy collectible card game about kawaii cats.\n\n" +
                "Collect cards, open fluffy packs, complete themed sets and trade duplicates.\n\n" +
                "Main commands:\n" +
                "/collection — your collection\n" +
                "/pack — open a pack\n" +
                "/freecard — claim your free card\n" +
                "/craft — craft packs from duplicates\n" +
                "/buy — get more packs with Telegram Stars\n" +
                "/trade — random trade\n" +
                "/market — card market\n" +
                "/language — change language\n" +
                "/help — help\n\n" +
                "📢 *News & updates:*\n" +
                "🇷🇺 Russian channel: [Purrrfolio RU](https://t.me/Purrrfolio_Ru)\n" +
                "🇬🇧 English channel: [Purrrfolio EN](https://t.me/Purrrfolio_En)" to
            "🐾 *Purrrfolio* — уютная коллекционная игра про кавайных котиков.\n\n" +
                "Собирай карточки, открывай пушистые наборы, завершай тематические коллекции и обменивай дубликаты.\n\n" +
                "Основные команды:\n" +
                "/collection — твоя коллекция\n" +
                "/pack — открыть набор\n" +
                "/freecard — забрать бесплатную карточку\n" +
                "/craft — скрафтить наборы из дубликатов\n" +
                "/buy — купить наборы за Telegram Stars\n" +
                "/trade — случайный обмен\n" +
                "/market — биржа карточек\n" +
                "/language — сменить язык\n" +
                "/help — справка\n\n" +
                "📢 *Новости и обновления:*\n" +
                "🇷🇺 Русский канал: [Purrrfolio RU](https://t.me/Purrrfolio_Ru)\n" +
                "🇬🇧 Английский канал: [Purrrfolio EN](https://t.me/Purrrfolio_En)"
            ),
        "help" to (
            "*Purrrfolio commands*\n\n" +
                "/start — register and choose language\n" +
                "/collection — list your cards and duplicates\n" +
                "/pack — open a pack (3 cards)\n" +
                "/freecard — claim a free card (every 3 hours)\n" +
                "/craft — melt duplicates into packs (15 pts = 1 pack)\n" +
                "/buy — buy packs with Telegram Stars\n" +
                "/trade — random trade duplicates\n" +
                "/market — list and trade cards\n" +
                "/language — change language\n" +
                "/paysupport — payment support\n\n" +
                "You can also send simple words:\n" +
                "• pack / card pack\n" +
                "• craft\n" +
                "• market / marketplace\n" +
                "• card, cat, kitty, kitten — free card\n" +
                "The buttons below do the same.\n\n" +
                "✨ *Special collections*\n" +
                "• Calendar Cats: a month card drops only in that month; a season card only during its season.\n" +
                "• Friends: drops only when a pack or free card is requested from a group or supergroup chat.\n" +
                "Special cards keep the normal odds of their rarity.\n\n" +
                "📢 *News & updates:*\n" +
                "🇷🇺 Russian channel: [Purrrfolio RU](https://t.me/Purrrfolio_Ru)\n" +
                "🇬🇧 English channel: [Purrrfolio EN](https://t.me/Purrrfolio_En)" to
            "*Команды Purrrfolio*\n\n" +
                "/start — регистрация и выбор языка\n" +
                "/collection — список карточек и дубликатов\n" +
                "/pack — открыть набор (3 карточки)\n" +
                "/freecard — забрать бесплатную карточку (каждые 3 часа)\n" +
                "/craft — плавить дубликаты в наборы (15 очков = 1 набор)\n" +
                "/buy — купить наборы за Telegram Stars\n" +
                "/trade — случайный обмен дубликатами\n" +
                "/market — выставить и обменять карточки\n" +
                "/language — сменить язык\n" +
                "/paysupport — поддержка платежей\n\n" +
                "Можно отправлять и простые слова:\n" +
                "• набор / пак\n" +
                "• крафт\n" +
                "• биржа\n" +
                "• карточка, котик, кот, котейка, кошка, котёнок — бесплатная карточка\n" +
                "Кнопки ниже делают то же самое.\n\n" +
                "✨ *Спешл-коллекции*\n" +
                "• Месяцы и сезоны: карточка месяца выпадает только в этот месяц, карточка времени года — только в его месяцы.\n" +
                "• Друзья: выпадает только при запросе набора или бесплатной карточки из группового чата или супергруппы.\n" +
                "Шанс спешл-карточки остаётся обычным для её редкости.\n\n" +
                "📢 *Новости и обновления:*\n" +
                "🇷🇺 Русский канал: [Purrrfolio RU](https://t.me/Purrrfolio_Ru)\n" +
                "🇬🇧 Английский канал: [Purrrfolio EN](https://t.me/Purrrfolio_En)"
            ),
        "menu.collection" to ("🗂 Collection" to "🗂 Коллекция"),
        "raffle.result" to (
            "🎉 *Daily pack raffle!*\n\n%s members in this chat → %s up for grabs!\n\nOur winners:\n%s\n\nOpen your prize with /pack!" to
                "🎉 *Ежедневный розыгрыш паков!*\n\nУчастников в чате: %s → разыгрывается %s!\n\nНаши победители:\n%s\n\nОткрой приз через /pack!"
            ),
        "menu.pack" to ("🎁 Pack" to "🎁 Набор"),
        "menu.packCount" to ("🎁 Pack (%s)" to "🎁 Набор (%s)"),
        "menu.trade" to ("🎲 Random Trade" to "🎲 Случайный обмен"),
        "menu.market" to ("🏪 Market" to "🏪 Биржа"),
        "menu.language" to ("🌐 Language" to "🌐 Язык"),
        "menu.buy" to ("⭐ Buy packs" to "⭐ Купить наборы"),
        "menu.freecard" to ("🎁 Free card" to "🎁 Карточка"),
        "menu.craft" to ("🛠 Craft" to "🛠 Крафт"),
        "language.title" to ("🌐 Choose your language:" to "🌐 Выбери язык:"),
        "language.changed" to ("Language set to English." to "Язык изменён на русский."),
        "pack.noPacks" to (
            "No packs left. Grab more with /buy using Telegram Stars. Free single cards live separately: /freecard." to
                "Наборы закончились. Возьми ещё через /buy за Telegram Stars. Бесплатные карточки отдельно: /freecard."
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
        "card.special" to ("✨ *SPECIAL CARD*" to "✨ *СПЕШЛ-КАРТОЧКА*"),
        "card.specialTag" to ("✨ SPECIAL" to "✨ СПЕШЛ"),
        "card.nextFreeIn" to (
            "Next free card in %s h %s min." to
                "Следующая бесплатная карточка через %s ч %s мин."
            ),
        "craft.title" to (
            "🛠 *Pack crafter*\n\nBalance: %s / %s pts\nCommon = 1, Uncommon = 2, Rare = 3, Epic = 4, Legendary/Mythic = 5.\n15 pts build 1 pack, leftovers carry over." to
                "🛠 *Крафтер наборов*\n\nБаланс: %s / %s очков\nОбычная = 1, необычная = 2, редкая = 3, эпическая = 4, легендарная/мифическая = 5.\n15 очков = 1 набор, остаток переносится."
            ),
        "craft.pickCard" to (
            "Pick a duplicate to melt into points (one copy stays in your collection):" to
                "Выбери дубликат для переплавки в очки (одна копия остаётся в коллекции):"
            ),
        "craft.addButton" to ("+%s %s" to "+%s %s"),
        "craft.added" to (
            "Melted %s (+%s pts). Balance: %s / %s pts." to
                "Переплавлено: %s (+%s очков). Баланс: %s / %s очков."
            ),
        "craft.crafted" to (
            "🎁 Crafted %s pack! Open it with /pack." to
                "🎁 Скрафчено наборов: %s! Открой через /pack."
            ),
        "craft.noDuplicates" to (
            "No duplicates available for crafting." to
                "Нет дубликатов для крафта."
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
            "💳 *Payment support*\n\nUse /paysupport to select a purchase and send a refund request for admin review." to
                "💳 *Поддержка платежей*\n\nИспользуй /paysupport, чтобы выбрать покупку и отправить запрос на возврат администратору."
            ),
        "paysupport.empty" to ("No refundable purchases found." to "Покупок, доступных для возврата, не найдено."),
        "paysupport.list" to (
            "Choose a purchase for refund with /paysupport <ID> <reason>:\n%s" to
                "Выбери покупку для возврата командой /paysupport <ID> <причина>:\n%s"
            ),
        "paysupport.paymentRow" to ("%s: %s packs — %s XTR" to "%s: %s наборов — %s XTR"),
        "paysupport.invalid" to (
            "Invalid format. Use /paysupport <ID> <reason>." to
                "Неверный формат. Используй /paysupport <ID> <причина>."
            ),
        "paysupport.notFound" to ("Purchase or request not found." to "Покупка или запрос не найдены."),
        "paysupport.unavailable" to (
            "Payment support is temporarily unavailable. Please try again later." to
                "Поддержка платежей временно недоступна. Попробуй позже."
            ),
        "paysupport.submitted" to (
            "Request #%s has been sent to the admins." to
                "Запрос #%s отправлен администрации."
            ),
        "paysupport.alreadySubmitted" to (
            "A support request already exists for this purchase: #%s." to
                "Для этой покупки уже создан запрос #%s."
            ),
        "paysupport.answerInvalid" to (
            "Provide your reply with /answer <ID> <message>." to
                "Отправь ответ командой /answer <ID> <ответ>."
            ),
        "paysupport.answerSent" to ("Your reply has been sent to the admins." to "Ответ отправлен администрации."),
        "paysupport.refunded" to (
            "Your request #%s was approved. The Stars payment has been refunded." to
                "Твой запрос #%s одобрен. Платёж в Stars возвращён."
            ),
        "paysupport.rejected" to (
            "Request #%s was rejected: %s" to
                "Запрос #%s отклонён: %s"
            ),
        "paysupport.ask" to (
            "An admin asks about request #%s: %s\nReply with /answer %s <message>." to
                "Администратор уточняет по запросу #%s: %s\nОтветь командой /answer %s <ответ>."
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
        "collection.reward" to (
            "🎉 *Collection complete:* %s\nFree packs added: %s. If new cards are added to this collection, completing the expanded set will earn another pack." to
                "🎉 *Коллекция собрана:* %s\nНачислено бесплатных наборов: %s. Если в коллекцию добавят новые карты, за повторное завершение расширенного набора ты получишь ещё один пак."
            ),
        "profile.title" to ("👤 *%s*" to "👤 *%s*"),
        "profile.player" to ("Player" to "Игрок"),
        "profile.unique" to ("🃏 Unique cards: %s / %s" to "🃏 Уникальных карточек: %s / %s"),
        "profile.packs" to ("🎁 Available packs: %s" to "🎁 Доступных наборов: %s"),
        "themeStatus.claimed" to ("claimed" to "получен"),
        "themeStatus.completed" to ("completed!" to "собрана!"),
        "themeStatus.inProgress" to ("in progress" to "в процессе"),
        "themes.title" to ("📚 *Themes*" to "📚 *Темы*"),
        "trade.title" to ("🎲 *Random Trade*" to "🎲 *Случайный обмен*"),
        "trade.hint" to (
            "🎲 *Random Trade*\n\nChoose a duplicate. It stays in the pool until another player adds a different card; then the exchange happens automatically and both players are notified." to
                "🎲 *Случайный обмен*\n\nВыбери дубликат. Он останется в пуле, пока другой игрок не добавит другую карточку; затем обмен произойдёт автоматически и оба игрока получат уведомление."
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
        "trade.returnButton" to ("↩️ Return %s" to "↩️ Вернуть %s"),
        "trade.returned" to (
            "Card returned to your collection: %s" to
                "Карточка возвращена в коллекцию: %s"
            ),
        "market.browse" to (
            "🔍 Browse others' cards" to
                "🔍 Смотреть чужие карточки"
            ),
        "trade.offerButton" to ("🎲 Trade %s" to "🎲 Обменять %s"),
        "market.hint" to (
            "🏪 *Market*\n\n1. List a duplicate.\n2. Browse another player's card.\n3. Choose one of your listings to offer.\nThe owner accepts or rejects the exchange." to
                "🏪 *Биржа*\n\n1. Выставь дубликат.\n2. Выбери карточку другого игрока.\n3. Предложи за неё один из своих лотов.\nВладелец примет или отклонит обмен."
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
            "🎴 *%s*\n%s %s\n📚 Collection: %s%s" to
                "🎴 *%s*\n%s %s\n📚 Коллекция: %s%s"
            ),
        "market.specialCollection" to ("\n✨ Special collection" to "\n✨ Спешл-коллекция"),
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
            "✅ Trade complete! You received: %s" to
                "✅ Обмен завершён! Ты получил: %s"
            ),
        "market.offerRejectedByYou" to (
            "Trade offer rejected. The other player has been notified." to
                "Предложение отклонено. Второй участник получил уведомление."
            ),
        "market.offerRejectedNotice" to (
            "❌ Your trade offer was rejected." to
                "❌ Твоё предложение обмена отклонено."
            ),
        "market.settlementFailed" to (
            "This offer is no longer available. Open /market to see current listings." to
                "Это предложение уже недоступно. Открой /market, чтобы посмотреть актуальные лоты."
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
        "callback.expired" to (
            "This button is no longer active. Use the menu below to continue." to
                "Эта кнопка больше не активна. Продолжи через меню ниже."
            ),
        "gallery.back" to ("🔙 Back" to "🔙 Назад"),
        "gallery.viewCards" to ("🖼 View cards" to "🖼 Смотреть карточки"),
        "gallery.owned" to ("In collection: ×%s" to "В коллекции: ×%s"),
        "gallery.missing" to ("Not collected yet" to "Ещё не собрана"),
        "gallery.counter" to ("%s / %s" to "%s / %s"),
        "gallery.trade" to ("🎲 Random Trade" to "🎲 Случайный обмен"),
        "gallery.listMarket" to ("🏪 List for Trade" to "🏪 Выставить на биржу"),
        "cmd.start" to ("Open the menu" to "Открыть меню"),
        "cmd.pack" to ("Open a pack" to "Открыть набор"),
        "cmd.freecard" to ("Claim a free card" to "Забрать бесплатную карточку"),
        "cmd.craft" to ("Craft packs from duplicates" to "Скрафтить наборы из дубликатов"),
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
