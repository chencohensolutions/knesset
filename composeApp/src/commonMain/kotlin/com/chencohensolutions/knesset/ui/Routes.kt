package com.chencohensolutions.knesset.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavHostController
import kotlinx.serialization.Serializable

val LocalNav = staticCompositionLocalOf<NavHostController> { error("No NavController") }

// Root tabs
@Serializable data object HomeRoute
@Serializable data object LegislationRoute
@Serializable data object PlenumRoute
@Serializable data object CommitteesRoute
@Serializable data object PeopleRoute

// Details
@Serializable data class BillRoute(val id: Int)
@Serializable data class LawRoute(val id: Int)
@Serializable data class SecondaryLawRoute(val id: Int)
@Serializable data class VoteRoute(val id: Int)
@Serializable data class SittingRoute(val id: Int)
@Serializable data class MotionRoute(val id: Int)
@Serializable data class QuestionRoute(val id: Int)
@Serializable data class CommitteeRoute(val id: Int)
@Serializable data class CommitteeSessionRoute(val id: Int)
@Serializable data class MemberRoute(val id: Int)
@Serializable data class FactionRoute(val id: Int)
@Serializable data class MinistryRoute(val id: Int)
@Serializable data class LobbyistRoute(val id: Int)

/** A standalone list of one kind, opened from search "show all" with the query applied. */
@Serializable data class KindListRoute(val kind: String, val query: String = "")

// Overflow destinations
@Serializable data class SearchRoute(val query: String = "")
@Serializable data object FavoritesRoute
@Serializable data object ExplorerRoute
@Serializable data class ExplorerSetRoute(val name: String)
@Serializable data class ExplorerItemRoute(val name: String, val id: Int)
@Serializable data object SettingsRoute
@Serializable data object AboutRoute

/** Kinds of items that can be opened, starred and searched. */
enum class Kind(val entitySet: String) {
    BILL("KNS_Bill"),
    LAW("KNS_IsraelLaw"),
    SECONDARY_LAW("KNS_SecondaryLaw"),
    VOTE("KNS_PlenumVote"),
    SITTING("KNS_PlenumSession"),
    MOTION("KNS_Agenda"),
    QUESTION("KNS_Query"),
    COMMITTEE("KNS_Committee"),
    COMMITTEE_SESSION("KNS_CommitteeSession"),
    MEMBER("KNS_Person"),
    FACTION("KNS_Faction"),
    MINISTRY("KNS_GovMinistry"),
    LOBBYIST("V_Lobbyists");

    fun route(id: Int): Any = when (this) {
        BILL -> BillRoute(id)
        LAW -> LawRoute(id)
        SECONDARY_LAW -> SecondaryLawRoute(id)
        VOTE -> VoteRoute(id)
        SITTING -> SittingRoute(id)
        MOTION -> MotionRoute(id)
        QUESTION -> QuestionRoute(id)
        COMMITTEE -> CommitteeRoute(id)
        COMMITTEE_SESSION -> CommitteeSessionRoute(id)
        MEMBER -> MemberRoute(id)
        FACTION -> FactionRoute(id)
        MINISTRY -> MinistryRoute(id)
        LOBBYIST -> LobbyistRoute(id)
    }
}
