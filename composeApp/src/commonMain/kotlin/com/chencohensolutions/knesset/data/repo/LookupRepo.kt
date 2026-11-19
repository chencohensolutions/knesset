package com.chencohensolutions.knesset.data.repo

import com.chencohensolutions.knesset.core.cache.Ttl
import com.chencohensolutions.knesset.core.odata.ODataQuery
import com.chencohensolutions.knesset.core.odata.ServiceDocument
import com.chencohensolutions.knesset.data.dto.CommitteeDto
import com.chencohensolutions.knesset.data.dto.FactionDto
import com.chencohensolutions.knesset.data.dto.GovMinistryDto
import com.chencohensolutions.knesset.data.dto.ItemTypeDto
import com.chencohensolutions.knesset.data.dto.KnessetDatesDto
import com.chencohensolutions.knesset.data.dto.PositionDto
import com.chencohensolutions.knesset.data.dto.StatusDto
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Small reference tables, cached for a week and memoized in memory. */
class LookupRepo(private val api: Api) {
    private val mutex = Mutex()
    private val memo = mutableMapOf<String, Any>()

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : Any> memo(key: String, force: Boolean, load: suspend () -> T): T {
        if (!force) mutex.withLock { memo[key] }?.let { return it as T }
        val value = load()
        mutex.withLock { memo[key] = value }
        return value
    }

    suspend fun knessetDates(force: Boolean = false): List<KnessetDatesDto> = memo("dates", force) {
        api.all(ODataQuery("KNS_KnessetDates").apply { orderBy = "KnessetNum desc,Plenum desc,Id desc" }, KnessetDatesDto.serializer(), Ttl.LOOKUP, force = force)
    }

    /** The Knesset numbers that have data, newest first, and the current one. */
    suspend fun knessetTerms(force: Boolean = false): Terms {
        val dates = knessetDates(force)
        val numbers = dates.mapNotNull { it.knessetNum }.filter { it > 0 }.distinct().sortedDescending()
        val current = dates.firstOrNull { it.isCurrent == true }?.knessetNum ?: numbers.firstOrNull()
        return Terms(numbers, current, dates)
    }

    data class Terms(val numbers: List<Int>, val current: Int?, val dates: List<KnessetDatesDto>)

    suspend fun statuses(): Map<Int, StatusDto> = memo("status", false) {
        api.all(ODataQuery("KNS_Status").apply { orderBy = "Id" }, StatusDto.serializer(), Ttl.LOOKUP).associateBy { it.id }
    }

    suspend fun itemTypes(): Map<Int, ItemTypeDto> = memo("itemType", false) {
        api.all(ODataQuery("KNS_ItemType"), ItemTypeDto.serializer(), Ttl.LOOKUP).associateBy { it.id }
    }

    suspend fun positions(): Map<Int, PositionDto> = memo("position", false) {
        api.all(ODataQuery("KNS_Position").apply { orderBy = "Id" }, PositionDto.serializer(), Ttl.LOOKUP).associateBy { it.id }
    }

    suspend fun ministries(): List<GovMinistryDto> = memo("ministry", false) {
        api.all(ODataQuery("KNS_GovMinistry").apply { orderBy = "Name,Id" }, GovMinistryDto.serializer(), Ttl.LOOKUP)
    }

    /** Factions of a Knesset, without the "אין נתונים" placeholder row. */
    suspend fun factions(knesset: Int?): List<FactionDto> = memo("faction$knesset", false) {
        val q = ODataQuery("KNS_Faction").apply {
            if (knesset != null) filter("KnessetNum eq $knesset")
            orderBy = "Name,Id"
        }
        api.all(q, FactionDto.serializer(), Ttl.LOOKUP).filter { it.id != PLACEHOLDER_FACTION_ID && (it.knessetNum ?: 0) > 1 }
    }

    suspend fun committees(knesset: Int?): List<CommitteeDto> = memo("committee$knesset", false) {
        val q = ODataQuery("KNS_Committee").apply {
            if (knesset != null) filter("KnessetNum eq $knesset")
            orderBy = "Name,Id"
            select = listOf("Id", "Name", "KnessetNum", "CategoryDesc", "CommitteeTypeDesc", "AdditionalTypeDesc", "ParentCommitteeID", "IsCurrent", "FinishDate")
        }
        api.all(q, CommitteeDto.serializer(), Ttl.LOOKUP)
    }

    suspend fun serviceDocument(): List<String> = memo("service", false) {
        api.decode(ServiceDocument.serializer(), api.text(ODataQuery.BASE_URL, Ttl.LOOKUP)).value
            .filter { it.kind == null || it.kind == "EntitySet" }.map { it.name }
    }

    suspend fun metadataXml(): String = memo("metadata", false) {
        api.text(ODataQuery.BASE_URL + "\$metadata", Ttl.LOOKUP)
    }

    companion object {
        const val PLACEHOLDER_FACTION_ID = 911
    }
}
