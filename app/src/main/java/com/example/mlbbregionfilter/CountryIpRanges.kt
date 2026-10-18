package com.example.mlbbregionfilter

object CountryIpRanges {

    /*
     * Временная структура.
     *
     * Здесь будут храниться CIDR-диапазоны:
     *
     * "UA" -> ["1.2.3.0/24", "5.6.0.0/16"]
     *
     * Пока реальные диапазоны не добавляем.
     * Их подключим отдельным этапом из актуальной базы.
     */

    val countries = mapOf(
        "Украина" to emptyList<String>(),
        "Италия" to emptyList<String>(),
        "Германия" to emptyList<String>(),
        "Польша" to emptyList<String>(),
        "Франция" to emptyList<String>(),
        "Нидерланды" to emptyList<String>(),
        "Румыния" to emptyList<String>(),
        "Турция" to emptyList<String>()
    )
}
