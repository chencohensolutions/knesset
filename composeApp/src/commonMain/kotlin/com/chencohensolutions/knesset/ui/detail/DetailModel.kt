package com.chencohensolutions.knesset.ui.detail

import com.chencohensolutions.knesset.domain.Doc
import com.chencohensolutions.knesset.domain.VoteTally
import com.chencohensolutions.knesset.ui.Kind
import org.jetbrains.compose.resources.StringResource
import kotlin.time.Instant

/** Everything a detail screen shows, built in the data layer and rendered by one composable. */
data class DetailContent(
    val kind: Kind?,
    val id: Int,
    val title: String,
    val status: String? = null,
    val subtitle: String? = null,
    val sections: List<Section> = emptyList(),
    /** Optional tabs below the header; each has its own sections (member detail). */
    val tabs: List<DetailTab> = emptyList(),
    val webUrl: String? = null,
    val shareUrl: String? = null,
    val lastUpdated: Instant? = null,
    /** Short text stored with a favorite. */
    val favoriteSubtitle: String? = null,
    /** Initials and faction for the big avatar in a member header. */
    val avatar: Pair<String, Int?>? = null,
    /** Bill stage tracker: reached stage index and whether the bill stopped. */
    val stage: Pair<Int, Boolean>? = null,
)

data class DetailTab(val title: StringResource, val sections: List<Section>)

data class Fact(val label: StringResource, val value: String, val route: Any? = null, val url: String? = null)

data class LinkItem(
    val title: String,
    val subtitle: String? = null,
    val route: Any? = null,
    val url: String? = null,
    val initials: String? = null,
    val status: String? = null,
    /** for, against, abstain counts for a thin vote bar. */
    val bar: Triple<Int, Int, Int>? = null,
)

data class ActionLink(val label: StringResource, val url: String)

sealed interface Section {
    val title: StringResource?
    val isEmpty: Boolean

    data class Facts(override val title: StringResource?, val facts: List<Fact>) : Section {
        override val isEmpty get() = facts.isEmpty()
    }

    data class Text(override val title: StringResource?, val text: String?) : Section {
        override val isEmpty get() = text.isNullOrBlank()
    }

    data class Links(
        override val title: StringResource?,
        val items: List<LinkItem>,
        val countInTitle: Boolean = true,
        /** A "show all" destination (Home cards). */
        val showAll: Any? = null,
        /** Shown instead of hiding the section when it is empty. */
        val showWhenEmpty: Boolean = false,
        /** Shown as a horizontal carousel of compact cards (Home). */
        val carousel: Boolean = false,
    ) : Section {
        override val isEmpty get() = items.isEmpty() && !showWhenEmpty
    }

    /** Items in named groups, with an optional second grouping the user can switch to. */
    data class Groups(
        override val title: StringResource?,
        val groups: List<Pair<String, List<LinkItem>>>,
        val alternateLabel: StringResource? = null,
        val alternate: List<Pair<String, List<LinkItem>>> = emptyList(),
        val primaryLabel: StringResource? = null,
    ) : Section {
        override val isEmpty get() = groups.isEmpty()
    }

    data class Documents(override val title: StringResource?, val docs: List<Doc>) : Section {
        override val isEmpty get() = docs.isEmpty()
    }

    data class Tally(
        override val title: StringResource?,
        val tally: VoteTally,
        /** One result per seat, ordered so groups sit together, for the hemicycle. */
        val seatResults: List<String> = emptyList(),
    ) : Section {
        override val isEmpty get() = tally.total == 0
    }

    data class Actions(override val title: StringResource?, val links: List<ActionLink>) : Section {
        override val isEmpty get() = links.isEmpty()
    }
}

fun factsOf(vararg facts: Fact?): List<Fact> = facts.filterNotNull().filter { it.value.isNotBlank() }

fun fact(label: StringResource, value: Any?, route: Any? = null, url: String? = null): Fact? {
    val text = when (value) {
        null -> null
        is Boolean -> null
        is Instant -> com.chencohensolutions.knesset.core.odata.IsraelTime.dateTime(value)
        else -> value.toString().trim().takeIf { it.isNotEmpty() && it != "-1" }
    } ?: return null
    return Fact(label, text, route, url)
}
