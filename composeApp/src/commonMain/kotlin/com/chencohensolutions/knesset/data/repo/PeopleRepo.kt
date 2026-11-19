package com.chencohensolutions.knesset.data.repo

import com.chencohensolutions.knesset.core.cache.Ttl
import com.chencohensolutions.knesset.core.odata.F
import com.chencohensolutions.knesset.core.odata.ODataQuery
import com.chencohensolutions.knesset.data.dto.PersonDto
import com.chencohensolutions.knesset.data.dto.PersonToPositionDto
import com.chencohensolutions.knesset.domain.People

/** A member of a Knesset as shown in the members list. */
data class Member(
    val personId: Int,
    val firstName: String?,
    val lastName: String?,
    val factionId: Int?,
    val factionName: String?,
    val genderId: Int?,
    /** Still serving in that faction (no finish date). */
    val serving: Boolean,
) {
    val name get() = People.fullName(firstName, lastName)
}

object MemberLogic {
    /**
     * Members of a Knesset from faction membership rows (plan 2.7): one entry per person. When a person
     * moved between factions, the current (unfinished, latest) membership wins.
     */
    fun fromPositions(rows: List<PersonToPositionDto>): List<Member> = rows
        .filter { it.factionID != null && it.personID != null }
        .groupBy { it.personID!! }
        .map { (pid, list) ->
            val best = list.sortedWith(compareBy<PersonToPositionDto>({ if (it.finishDate == null) 0 else 1 }, { -(it.startDate?.toEpochMilliseconds() ?: 0) })).first()
            val p = list.firstNotNullOfOrNull { it.person }
            Member(pid, p?.firstName, p?.lastName, best.factionID, best.factionName?.trim(), p?.genderID, list.any { it.finishDate == null })
        }
        .sortedWith(compareBy({ it.lastName ?: "" }, { it.firstName ?: "" }))

    fun matches(m: Member, query: String): Boolean {
        val tokens = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return tokens.all { t -> (m.firstName?.contains(t) == true) || (m.lastName?.contains(t) == true) }
    }
}

class PeopleRepo(private val api: Api) {
    suspend fun members(knesset: Int, mode: FetchMode, ttl: Ttl): List<Member> {
        val q = ODataQuery("KNS_PersonToPosition").apply {
            filter(F.eq("KnessetNum", knesset)); filter(F.isNotNull("FactionID"))
            orderBy = "Id"
            expand = listOf("KNS_Person")
        }
        return MemberLogic.fromPositions(Loader(api, mode).all(q, PersonToPositionDto.serializer(), ttl))
    }

    suspend fun person(id: Int, loader: Loader): PersonDto = loader.one("KNS_Person", id, PersonDto.serializer())
}
