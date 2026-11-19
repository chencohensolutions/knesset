package com.chencohensolutions.knesset.data.repo

/** One property of an entity type from `$metadata`. */
data class FieldDef(val name: String, val type: String) {
    val isString get() = type == "String"
    val isDate get() = type == "DateTimeOffset" || type == "DateTime" || type == "Date"
    val isBool get() = type == "Boolean"
    val isNumber get() = type.startsWith("Int") || type == "Byte" || type == "Decimal" || type == "Double"
}

/** Reads entity sets and their fields from the EDMX. No XML library: the document is regular. */
object MetadataParser {
    /** Fields that are never requested or shown (plan 7.8). */
    val HIDDEN_FIELDS = setOf("IdentityNumber")

    fun parse(xml: String): Map<String, List<FieldDef>> {
        val types = Regex("""<EntityType Name="(\w+)"[^>]*>(.*?)</EntityType>""", RegexOption.DOT_MATCHES_ALL).findAll(xml).associate { m ->
            m.groupValues[1] to Regex("""<Property Name="(\w+)" Type="Edm\.(\w+)"""").findAll(m.groupValues[2])
                .map { FieldDef(it.groupValues[1], it.groupValues[2]) }
                .filter { it.name !in HIDDEN_FIELDS }
                .toList()
        }
        return Regex("""<EntitySet Name="(\w+)" EntityType="[\w.]*?\.(\w+)"""").findAll(xml).associate { m ->
            m.groupValues[1] to (types[m.groupValues[2]] ?: emptyList())
        }
    }

    /** Where a foreign key field points, for links in the generic detail view. */
    fun target(field: String): String? = when (field) {
        "Id" -> null
        "PersonID", "MkId", "InitiatorPersonID", "MinisterPersonID", "KnsID" -> "KNS_Person"
        "BillID", "MainBillID", "SplitBillID", "UnionBillID", "LawID" -> "KNS_Bill"
        "CommitteeID", "ParentCommitteeID", "RecommendCommitteeID", "ParticipantCommitteeID" -> "KNS_Committee"
        "FactionID" -> "KNS_Faction"
        "GovMinistryID" -> "KNS_GovMinistry"
        "StatusID" -> "KNS_Status"
        "PositionID" -> "KNS_Position"
        "ItemTypeID" -> "KNS_ItemType"
        "SessionID", "PlenumSessionID" -> "KNS_PlenumSession"
        "CommitteeSessionID" -> "KNS_CommitteeSession"
        "VoteID" -> "KNS_PlenumVote"
        "IsraelLawID", "ParentLawID", "IsraelLawReplacedID", "AuthorizingLawID", "MajorAuthorizingLawID" -> "KNS_IsraelLaw"
        "SecondaryLawID", "SecondaryLawId", "SecChildID", "SecParentID", "SecMainID" -> "KNS_SecondaryLaw"
        "AgendaID", "LeadingAgendaID" -> "KNS_Agenda"
        "QueryID" -> "KNS_Query"
        "LawCorrectionID" -> "KNS_LawCorrections"
        "LobbyistID" -> "V_Lobbyists"
        else -> null
    }

    /** A readable title for a raw row. */
    fun titleOf(row: Map<String, String?>): String {
        listOf("Name", "VoteTitle", "FullName", "Desc", "Description", "DocumentName", "GroupTypeDesc", "ResultDesc", "BroadcastUrl", "TableName").forEach { k ->
            row[k]?.takeIf { it.isNotBlank() && it != "null" }?.let { return it }
        }
        val name = listOfNotNull(row["FirstName"], row["LastName"]).filter { it != "null" }.joinToString(" ")
        if (name.isNotBlank()) return name
        return "Id ${row["Id"]}"
    }
}
