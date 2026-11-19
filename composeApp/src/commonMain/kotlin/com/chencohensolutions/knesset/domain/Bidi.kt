package com.chencohensolutions.knesset.domain

/**
 * Unicode isolates for text the app inserts into Hebrew sentences (numbers, dates, URLs, emails, Latin
 * names), so neutral characters like '/', '-', '.', ')' stay attached to their run (docs 6).
 */
object Bidi {
    private const val FSI = '⁨' // first strong isolate
    private const val LRI = '⁦' // left-to-right isolate
    private const val PDI = '⁩' // pop directional isolate

    /** Direction from the content itself (dates, numbers, mixed names). */
    fun isolate(text: String): String = "$FSI$text$PDI"

    /** Always left to right (URLs, emails, field names). */
    fun ltr(text: String): String = "$LRI$text$PDI"

    /** Removes isolate marks, for comparisons and sharing. */
    fun strip(text: String): String = text.filterNot { it == FSI || it == LRI || it == PDI }
}
