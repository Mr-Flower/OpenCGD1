package it.cgd1.sveglia.data

/**
 * [tag] vuoto = segui la lingua del dispositivo. L'ordine è quello mostrato: inglese per primo,
 * poi le altre in ordine alfabetico del nome nativo.
 */
enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    ENGLISH("en"),
    SPANISH("es"),
    FRENCH("fr"),
    ITALIAN("it");

    companion object {
        fun fromTag(tag: String): AppLanguage =
            entries.firstOrNull { it != SYSTEM && tag.substringBefore('-').equals(it.tag, ignoreCase = true) } ?: SYSTEM
    }
}
