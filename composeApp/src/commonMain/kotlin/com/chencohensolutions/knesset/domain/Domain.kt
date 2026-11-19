package com.chencohensolutions.knesset.domain

import com.chencohensolutions.knesset.data.dto.DocumentDto
import com.chencohensolutions.knesset.data.dto.PlenumVoteResultDto
import com.chencohensolutions.knesset.ui.Kind

/** Maps `ItemTypeID` + `ItemID` (agenda items, vote items, law bindings) to a destination (plan 3.6). */
object ItemRefs {
    fun kindOf(itemTypeId: Int?): Kind? = when (itemTypeId) {
        1 -> Kind.QUESTION
        2, 6002 -> Kind.BILL
        4 -> Kind.MOTION
        5 -> Kind.SITTING
        12 -> Kind.COMMITTEE_SESSION
        6000 -> Kind.SECONDARY_LAW
        6003 -> Kind.LAW
        else -> null
    }
}

/** A document ready to open. */
data class Doc(val url: String, val meaning: String?, val format: String?, val name: String?)

object Documents {
    /** `\` becomes `/`, a missing scheme becomes https, and http is upgraded (plan 1.4). */
    fun normalizeUrl(raw: String?): String? {
        var s = raw?.trim()?.replace('\\', '/') ?: return null
        if (s.isEmpty()) return null
        s = when {
            s.startsWith("https://", ignoreCase = true) -> s
            s.startsWith("http://", ignoreCase = true) -> "https://" + s.substring(7)
            s.startsWith("//") -> "https:$s"
            else -> "https://" + s.trimStart('/')
        }
        return s.replace(" ", "%20")
    }

    fun format(doc: DocumentDto): String? =
        doc.applicationDesc?.uppercase()?.trim() ?: doc.filePath?.substringAfterLast('.', "")?.uppercase()?.takeIf { it.isNotBlank() }

    /** PDF first, then by meaning, dropping rows without a usable path. */
    fun prepare(docs: List<DocumentDto>): List<Doc> = docs
        .mapNotNull { d -> normalizeUrl(d.filePath)?.let { Doc(it, d.groupTypeDesc, format(d), d.documentName) } }
        .distinctBy { it.url }
        .sortedWith(compareBy<Doc>({ if (it.format == "PDF") 0 else 1 }, { it.meaning ?: "" }))
}

enum class StatusTone { PASSED, STOPPED, IN_PROGRESS, NEUTRAL }

/** A small local mapping from status wording to a color tone (plan 2.3). */
object StatusStyles {
    private val passed = listOf("התקבל", "אושר", "פורסם", "נענה", "הסתיים", "בתוקף", "התקיימה")
    private val stopped = listOf("נעצר", "הוסר", "נדחה", "נדחתה", "בוטל", "בוטלה", "לא אושר", "הוקפא", "נמשכ", "פקע", "חזר")

    fun tone(desc: String?): StatusTone {
        val d = desc?.trim().orEmpty()
        if (d.isEmpty()) return StatusTone.NEUTRAL
        return when {
            stopped.any { d.contains(it) } -> StatusTone.STOPPED
            passed.any { d.contains(it) } -> StatusTone.PASSED
            else -> StatusTone.IN_PROGRESS
        }
    }
}

object People {
    fun fullName(first: String?, last: String?): String = listOfNotNull(first?.trim(), last?.trim()).filter { it.isNotEmpty() }.joinToString(" ")

    fun initials(first: String?, last: String?): String =
        listOfNotNull(first?.trim()?.firstOrNull(), last?.trim()?.firstOrNull()).joinToString("")

    private val emailRegex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    /** The Email field sometimes holds a name; only real addresses are shown as tappable. */
    fun validEmail(raw: String?): String? = raw?.trim()?.takeIf { emailRegex.matches(it) }

    const val MEMBER_PAGE = "https://main.knesset.gov.il/mk/apps/mk/mk-personal-details/"
    fun memberPage(siteId: Int?): String? = siteId?.let { MEMBER_PAGE + it }
}

/** Vote totals computed on the device from result rows (plan 2.6). */
data class VoteTally(val byResult: List<Pair<String, Int>>, val total: Int) {
    val forCount get() = byResult.firstOrNull { it.first == FOR }?.second ?: 0
    val againstCount get() = byResult.firstOrNull { it.first == AGAINST }?.second ?: 0
    val abstainCount get() = byResult.firstOrNull { it.first == ABSTAIN }?.second ?: 0

    companion object {
        const val FOR = "בעד"
        const val AGAINST = "נגד"
        const val ABSTAIN = "נמנע"

        /** Result codes seen on the live API: 6 נוכח, 7 בעד, 8 נגד, 9 נמנע, 10 לא נכח, 11 הצביע. */
        private val order = listOf(7, 8, 9, 6, 11, 10)

        fun of(rows: List<PlenumVoteResultDto>): VoteTally {
            val unique = rows.distinctBy { it.id }
            val groups = unique.groupBy { (it.resultCode ?: -1) to (it.resultDesc?.trim() ?: "?") }
            val sorted = groups.entries.sortedBy { (key, _) -> order.indexOf(key.first).let { if (it < 0) 100 + key.first else it } }
            return VoteTally(sorted.map { it.key.second to it.value.size }, unique.size)
        }
    }
}
