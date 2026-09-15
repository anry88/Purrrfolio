package com.anry88.purrrfolio.i18n

import com.anry88.purrrfolio.catalog.CardDefinition
import com.anry88.purrrfolio.catalog.PackDefinition
import com.anry88.purrrfolio.catalog.ThemeDefinition

fun CardDefinition.nameFor(locale: GameLocale): String = if (locale == GameLocale.RU) nameRu else nameEn

fun ThemeDefinition.nameFor(locale: GameLocale): String = if (locale == GameLocale.RU) nameRu else nameEn

fun PackDefinition.nameFor(locale: GameLocale): String = if (locale == GameLocale.RU) nameRu else nameEn