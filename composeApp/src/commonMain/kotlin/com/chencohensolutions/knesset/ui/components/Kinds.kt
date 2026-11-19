package com.chencohensolutions.knesset.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.chencohensolutions.knesset.resources.*
import com.chencohensolutions.knesset.ui.Kind
import com.chencohensolutions.knesset.ui.list.ListContent
import com.chencohensolutions.knesset.ui.specFor
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

fun kindLabel(kind: Kind): StringResource = when (kind) {
    Kind.BILL -> Res.string.section_bills
    Kind.LAW -> Res.string.section_laws
    Kind.SECONDARY_LAW -> Res.string.section_secondary
    Kind.VOTE -> Res.string.section_votes
    Kind.SITTING -> Res.string.section_sittings
    Kind.MOTION -> Res.string.section_motions
    Kind.QUESTION -> Res.string.section_questions
    Kind.COMMITTEE -> Res.string.section_committees
    Kind.COMMITTEE_SESSION -> Res.string.section_calendar
    Kind.MEMBER -> Res.string.section_members
    Kind.FACTION -> Res.string.section_factions
    Kind.MINISTRY -> Res.string.section_ministries
    Kind.LOBBYIST -> Res.string.section_lobbyists
}

/** A list of one kind with a search already applied (search "show all", Home "show all"). */
@Composable
fun KindListScreen(kindName: String, query: String) {
    val kind = Kind.entries.firstOrNull { it.name == kindName } ?: return
    val spec = specFor(kind) ?: return
    Scaffold(topBar = { DetailTopBar(stringResource(kindLabel(kind))) }) { padding ->
        ListContent(rememberListVm(spec, "kind$kindName$query", initialQuery = query), Modifier.padding(padding))
    }
}
