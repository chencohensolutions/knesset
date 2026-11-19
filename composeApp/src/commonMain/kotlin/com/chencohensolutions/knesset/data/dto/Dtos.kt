package com.chencohensolutions.knesset.data.dto

import com.chencohensolutions.knesset.core.odata.ODataInstant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Generated from the live $metadata on 2026-11-19. Every field except Id is nullable (plan 3.1).
// Field names mirror the API one to one, including its typos.

@Serializable
data class AgendaDto(
    @SerialName("Id") val id: Int,
    @SerialName("Number") val number: Int? = null,
    @SerialName("ClassificationID") val classificationID: Int? = null,
    @SerialName("ClassificationDesc") val classificationDesc: String? = null,
    @SerialName("LeadingAgendaID") val leadingAgendaID: Int? = null,
    @SerialName("KnessetNum") val knessetNum: Int? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("SubTypeID") val subTypeID: Int? = null,
    @SerialName("SubTypeDesc") val subTypeDesc: String? = null,
    @SerialName("StatusID") val statusID: Int? = null,
    @SerialName("InitiatorPersonID") val initiatorPersonID: Int? = null,
    @SerialName("GovRecommendationID") val govRecommendationID: Int? = null,
    @SerialName("GovRecommendationDesc") val govRecommendationDesc: String? = null,
    @SerialName("PresidentDecisionDate") val presidentDecisionDate: ODataInstant? = null,
    @SerialName("PostopenmentReasonID") val postopenmentReasonID: Int? = null,
    @SerialName("PostopenmentReasonDesc") val postopenmentReasonDesc: String? = null,
    @SerialName("CommitteeID") val committeeID: Int? = null,
    @SerialName("RecommendCommitteeID") val recommendCommitteeID: Int? = null,
    @SerialName("MinisterPersonID") val ministerPersonID: Int? = null,
    @SerialName("LastUpdatedDate") val lastUpdatedDate: ODataInstant? = null,
    @SerialName("KNS_DocumentAgenda") val documentAgenda: List<DocumentDto> = emptyList(),
    @SerialName("KNS_Committee") val committee: CommitteeDto? = null,
    @SerialName("KNS_RecommendCommittee") val recommendCommittee: CommitteeDto? = null,
    @SerialName("KNS_Person") val person: PersonDto? = null,
    @SerialName("KNS_PersonMinister") val personMinister: PersonDto? = null,
    @SerialName("KNS_Status") val status: StatusDto? = null,
)

@Serializable
data class BillDto(
    @SerialName("Id") val id: Int,
    @SerialName("KnessetNum") val knessetNum: Int? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("TypeID") val typeID: Int? = null,
    @SerialName("TypeDesc") val typeDesc: String? = null,
    @SerialName("SubTypeID") val subTypeID: Int? = null,
    @SerialName("SubTypeDesc") val subTypeDesc: String? = null,
    @SerialName("PrivateNumber") val privateNumber: Int? = null,
    @SerialName("CommitteeID") val committeeID: Int? = null,
    @SerialName("StatusID") val statusID: Int? = null,
    @SerialName("Number") val number: Int? = null,
    @SerialName("PostponementReasonID") val postponementReasonID: Int? = null,
    @SerialName("PostponementReasonDesc") val postponementReasonDesc: String? = null,
    @SerialName("PublicationDate") val publicationDate: ODataInstant? = null,
    @SerialName("PublicationSeriesID") val publicationSeriesID: Int? = null,
    @SerialName("PublicationSeriesDesc") val publicationSeriesDesc: String? = null,
    @SerialName("PublicationSeriesFirstCallID") val publicationSeriesFirstCallID: Int? = null,
    @SerialName("PublicationSeriesFirstCallDesc") val publicationSeriesFirstCallDesc: String? = null,
    @SerialName("MagazineNumber") val magazineNumber: String? = null,
    @SerialName("PageNumber") val pageNumber: String? = null,
    @SerialName("IsContinuationBill") val isContinuationBill: Boolean? = null,
    @SerialName("SummaryLaw") val summaryLaw: String? = null,
    @SerialName("LastUpdatedDate") val lastUpdatedDate: ODataInstant? = null,
    @SerialName("KNS_BillInitiator") val billInitiator: List<BillInitiatorDto> = emptyList(),
    @SerialName("KNS_BillHistoryInitiator") val billHistoryInitiator: List<BillHistoryInitiatorDto> = emptyList(),
    @SerialName("KNS_BillName") val billName: List<BillNameDto> = emptyList(),
    @SerialName("KNS_DocumentBill") val documentBill: List<DocumentDto> = emptyList(),
    @SerialName("KNS_Status") val status: StatusDto? = null,
    @SerialName("KNS_Committee") val committee: CommitteeDto? = null,
    @SerialName("KNS_BillSplit") val billSplit: List<BillSplitDto> = emptyList(),
    @SerialName("KNS_BillUnion") val billUnion: List<BillUnionDto> = emptyList(),
    @SerialName("KNS_BillUnion_Main") val billUnionMain: List<BillUnionDto> = emptyList(),
    @SerialName("KNS_LawCorrections") val lawCorrections: List<LawCorrectionsDto> = emptyList(),
)

@Serializable
data class BillHistoryInitiatorDto(
    @SerialName("Id") val id: Int,
    @SerialName("BillID") val billID: Int? = null,
    @SerialName("PersonID") val personID: Int? = null,
    @SerialName("IsInitiator") val isInitiator: Boolean? = null,
    @SerialName("StartDate") val startDate: ODataInstant? = null,
    @SerialName("EndDate") val endDate: ODataInstant? = null,
    @SerialName("ReasonID") val reasonID: Int? = null,
    @SerialName("ReasonDesc") val reasonDesc: String? = null,
    @SerialName("LastUpdatedDate") val lastUpdatedDate: ODataInstant? = null,
    @SerialName("KNS_Person") val person: PersonDto? = null,
)

@Serializable
data class BillInitiatorDto(
    @SerialName("Id") val id: Int,
    @SerialName("BillID") val billID: Int? = null,
    @SerialName("PersonID") val personID: Int? = null,
    @SerialName("IsInitiator") val isInitiator: Boolean? = null,
    @SerialName("Ordinal") val ordinal: Int? = null,
    @SerialName("LastUpdatedDate") val lastUpdatedDate: ODataInstant? = null,
    @SerialName("KNS_Person") val person: PersonDto? = null,
    @SerialName("KNS_Bill") val bill: BillDto? = null,
)

@Serializable
data class BillNameDto(
    @SerialName("Id") val id: Int,
    @SerialName("BillID") val billID: Int? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("NameHistoryTypeID") val nameHistoryTypeID: Int? = null,
    @SerialName("NameHistoryTypeDesc") val nameHistoryTypeDesc: String? = null,
    @SerialName("LastUpdatedDate") val lastUpdatedDate: ODataInstant? = null,
)

@Serializable
data class BillSplitDto(
    @SerialName("Id") val id: Int,
    @SerialName("MainBillID") val mainBillID: Int? = null,
    @SerialName("SplitBillID") val splitBillID: Int? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("LastUpdatedDate") val lastUpdatedDate: ODataInstant? = null,
)

@Serializable
data class BillUnionDto(
    @SerialName("Id") val id: Int,
    @SerialName("MainBillID") val mainBillID: Int? = null,
    @SerialName("UnionBillID") val unionBillID: Int? = null,
    @SerialName("LastUpdatedDate") val lastUpdatedDate: ODataInstant? = null,
)

@Serializable
data class CmtSessionItemDto(
    @SerialName("Id") val id: Int,
    @SerialName("ItemID") val itemID: Int? = null,
    @SerialName("CommitteeSessionID") val committeeSessionID: Int? = null,
    @SerialName("Ordinal") val ordinal: Int? = null,
    @SerialName("StatusID") val statusID: Int? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("ItemTypeID") val itemTypeID: Int? = null,
    @SerialName("LastUpdatedDate") val lastUpdatedDate: ODataInstant? = null,
)

@Serializable
data class CmtSiteCodeDto(
    @SerialName("Id") val id: Int,
    @SerialName("KnsID") val knsID: Int? = null,
    @SerialName("SiteId") val siteId: Int? = null,
)

@Serializable
data class CommitteeDto(
    @SerialName("Id") val id: Int,
    @SerialName("Name") val name: String? = null,
    @SerialName("CategoryID") val categoryID: Int? = null,
    @SerialName("CategoryDesc") val categoryDesc: String? = null,
    @SerialName("KnessetNum") val knessetNum: Int? = null,
    @SerialName("CommitteeTypeID") val committeeTypeID: Int? = null,
    @SerialName("CommitteeTypeDesc") val committeeTypeDesc: String? = null,
    @SerialName("Email") val email: String? = null,
    @SerialName("StartDate") val startDate: ODataInstant? = null,
    @SerialName("FinishDate") val finishDate: ODataInstant? = null,
    @SerialName("AdditionalTypeID") val additionalTypeID: Int? = null,
    @SerialName("AdditionalTypeDesc") val additionalTypeDesc: String? = null,
    @SerialName("ParentCommitteeID") val parentCommitteeID: Int? = null,
    @SerialName("CommitteeParentName") val committeeParentName: String? = null,
    @SerialName("IsCurrent") val isCurrent: Boolean? = null,
    @SerialName("LastUpdatedDate") val lastUpdatedDate: ODataInstant? = null,
    @SerialName("KNS_JointCommittee") val jointCommittee: List<JointCommitteeDto> = emptyList(),
    @SerialName("KNS_JointCommitteeParticipant") val jointCommitteeParticipant: List<JointCommitteeDto> = emptyList(),
)

@Serializable
data class CommitteeSessionDto(
    @SerialName("Id") val id: Int,
    @SerialName("Number") val number: Int? = null,
    @SerialName("KnessetNum") val knessetNum: Int? = null,
    @SerialName("TypeID") val typeID: Int? = null,
    @SerialName("TypeDesc") val typeDesc: String? = null,
    @SerialName("CommitteeID") val committeeID: Int? = null,
    @SerialName("StatusID") val statusID: Int? = null,
    @SerialName("StatusDesc") val statusDesc: String? = null,
    @SerialName("Location") val location: String? = null,
    @SerialName("SessionUrl") val sessionUrl: String? = null,
    @SerialName("BroadcastUrl") val broadcastUrl: String? = null,
    @SerialName("StartDate") val startDate: ODataInstant? = null,
    @SerialName("FinishDate") val finishDate: ODataInstant? = null,
    @SerialName("Note") val note: String? = null,
    @SerialName("LastUpdatedDate") val lastUpdatedDate: ODataInstant? = null,
    @SerialName("KNS_Committee") val committee: CommitteeDto? = null,
    @SerialName("KNS_CmtSessionItem") val cmtSessionItem: List<CmtSessionItemDto> = emptyList(),
    @SerialName("KNS_DocumentCommitteeSession") val documentCommitteeSession: List<DocumentDto> = emptyList(),
)

@Serializable
data class FactionDto(
    @SerialName("Id") val id: Int,
    @SerialName("Name") val name: String? = null,
    @SerialName("KnessetNum") val knessetNum: Int? = null,
    @SerialName("StartDate") val startDate: ODataInstant? = null,
    @SerialName("FinishDate") val finishDate: ODataInstant? = null,
    @SerialName("IsCurrent") val isCurrent: Boolean? = null,
    @SerialName("LastUpdatedDate") val lastUpdatedDate: ODataInstant? = null,
)

@Serializable
data class GovMinistryDto(
    @SerialName("Id") val id: Int,
    @SerialName("Name") val name: String? = null,
    @SerialName("IsActive") val isActive: Boolean? = null,
    @SerialName("LastUpdatedDate") val lastUpdatedDate: ODataInstant? = null,
    @SerialName("CategoryID") val categoryID: Int? = null,
    @SerialName("CategoryName") val categoryName: String? = null,
    @SerialName("GovID") val govID: Int? = null,
)

@Serializable
data class IsraelLawDto(
    @SerialName("Id") val id: Int,
    @SerialName("KnessetNum") val knessetNum: Int? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("IsBasicLaw") val isBasicLaw: Boolean? = null,
    @SerialName("IsFavoriteLaw") val isFavoriteLaw: Boolean? = null,
    @SerialName("PublicationDate") val publicationDate: ODataInstant? = null,
    @SerialName("LatestPublicationDate") val latestPublicationDate: ODataInstant? = null,
    @SerialName("IsBudgetLaw") val isBudgetLaw: Boolean? = null,
    @SerialName("LawValidityID") val lawValidityID: Int? = null,
    @SerialName("LawValidityDesc") val lawValidityDesc: String? = null,
    @SerialName("ValidityStartDate") val validityStartDate: ODataInstant? = null,
    @SerialName("ValidityStartDateNotes") val validityStartDateNotes: String? = null,
    @SerialName("ValidityFinishDate") val validityFinishDate: ODataInstant? = null,
    @SerialName("ValidityFinishDateNotes") val validityFinishDateNotes: String? = null,
    @SerialName("LastUpdatedDate") val lastUpdatedDate: ODataInstant? = null,
    @SerialName("KNS_DocumentIsraelLaw") val documentIsraelLaw: List<DocumentDto> = emptyList(),
    @SerialName("KNS_IsraelLawBinding") val israelLawBinding: List<IsraelLawBindingDto> = emptyList(),
    @SerialName("KNS_IsraelLawClassificiation") val israelLawClassificiation: List<IsraelLawClassificiationDto> = emptyList(),
    @SerialName("KNS_IsraelLawMinistry") val israelLawMinistry: List<IsraelLawMinistryDto> = emptyList(),
    @SerialName("KNS_IsraelLawName") val israelLawName: List<IsraelLawNameDto> = emptyList(),
)

@Serializable
data class IsraelLawBindingDto(
    @SerialName("Id") val id: Int,
    @SerialName("IsraelLawID") val israelLawID: Int? = null,
    @SerialName("IsraelLawReplacedID") val israelLawReplacedID: Int? = null,
    @SerialName("LawID") val lawID: Int? = null,
    @SerialName("LawTypeID") val lawTypeID: Int? = null,
    @SerialName("LastUpdatedDate") val lastUpdatedDate: ODataInstant? = null,
)

@Serializable
data class IsraelLawClassificiationDto(
    @SerialName("Id") val id: Int,
    @SerialName("IsraelLawID") val israelLawID: Int? = null,
    @SerialName("ClassificiationID") val classificiationID: Int? = null,
    @SerialName("ClassificiationDesc") val classificiationDesc: String? = null,
    @SerialName("LastUpdatedDate") val lastUpdatedDate: ODataInstant? = null,
)

@Serializable
data class IsraelLawLawCorrectionsDto(
    @SerialName("Id") val id: Int,
    @SerialName("LawCorrectionID") val lawCorrectionID: Int? = null,
    @SerialName("IsraelLawID") val israelLawID: Int? = null,
    @SerialName("LastUpdatedBy") val lastUpdatedBy: Int? = null,
    @SerialName("LastUpdatedDate") val lastUpdatedDate: ODataInstant? = null,
)

@Serializable
data class IsraelLawMinistryDto(
    @SerialName("Id") val id: Int,
    @SerialName("IsraelLawID") val israelLawID: Int? = null,
    @SerialName("LastUpdatedDate") val lastUpdatedDate: ODataInstant? = null,
    @SerialName("MinistryCategoryID") val ministryCategoryID: Int? = null,
    @SerialName("MinistryCategoryDesc") val ministryCategoryDesc: String? = null,
)

@Serializable
data class IsraelLawNameDto(
    @SerialName("Id") val id: Int,
    @SerialName("IsraelLawID") val israelLawID: Int? = null,
    @SerialName("LawID") val lawID: Int? = null,
    @SerialName("LawTypeID") val lawTypeID: Int? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("LastUpdatedDate") val lastUpdatedDate: ODataInstant? = null,
)

@Serializable
data class ItemTypeDto(
    @SerialName("Id") val id: Int,
    @SerialName("Desc") val desc: String? = null,
    @SerialName("TableName") val tableName: String? = null,
)

@Serializable
data class JointCommitteeDto(
    @SerialName("Id") val id: Int,
    @SerialName("CommitteeID") val committeeID: Int? = null,
    @SerialName("ParticipantCommitteeID") val participantCommitteeID: Int? = null,
    @SerialName("LastUpdatedDate") val lastUpdatedDate: ODataInstant? = null,
    @SerialName("KNS_Committee") val committee: CommitteeDto? = null,
    @SerialName("KNS_CommitteeParticipant") val committeeParticipant: CommitteeDto? = null,
)

@Serializable
data class KnessetDatesDto(
    @SerialName("Id") val id: Int,
    @SerialName("KnessetNum") val knessetNum: Int? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("Assembly") val assembly: Int? = null,
    @SerialName("Plenum") val plenum: Int? = null,
    @SerialName("PlenumStart") val plenumStart: ODataInstant? = null,
    @SerialName("PlenumFinish") val plenumFinish: ODataInstant? = null,
    @SerialName("IsCurrent") val isCurrent: Boolean? = null,
    @SerialName("LastUpdatedDate") val lastUpdatedDate: ODataInstant? = null,
)

@Serializable
data class LawBindingDto(
    @SerialName("Id") val id: Int,
    @SerialName("LawID") val lawID: Int? = null,
    @SerialName("IsraelLawID") val israelLawID: Int? = null,
    @SerialName("ParentLawID") val parentLawID: Int? = null,
    @SerialName("LawTypeID") val lawTypeID: Int? = null,
    @SerialName("LawParentTypeID") val lawParentTypeID: Int? = null,
    @SerialName("BindingType") val bindingType: Int? = null,
    @SerialName("BindingTypeDesc") val bindingTypeDesc: String? = null,
    @SerialName("PageNumber") val pageNumber: String? = null,
    @SerialName("AmendmentType") val amendmentType: Int? = null,
    @SerialName("AmendmentTypeDesc") val amendmentTypeDesc: String? = null,
    @SerialName("LastUpdatedDate") val lastUpdatedDate: ODataInstant? = null,
    @SerialName("IsTempLegislation") val isTempLegislation: Boolean? = null,
    @SerialName("IsSecondaryAmendment") val isSecondaryAmendment: Boolean? = null,
    @SerialName("CorrectionNumber") val correctionNumber: Int? = null,
    @SerialName("ParagraphNumber") val paragraphNumber: String? = null,
)

@Serializable
data class LawCorrectionsDto(
    @SerialName("Id") val id: Int,
    @SerialName("BillID") val billID: Int? = null,
    @SerialName("CorrectionTypeID") val correctionTypeID: Int? = null,
    @SerialName("CorrectionTypeDesc") val correctionTypeDesc: String? = null,
    @SerialName("IsKnessetInvolvement") val isKnessetInvolvement: Boolean? = null,
    @SerialName("CommitteeID") val committeeID: Int? = null,
    @SerialName("CorrectionStatusID") val correctionStatusID: Int? = null,
    @SerialName("CorrectionStatusDesc") val correctionStatusDesc: String? = null,
    @SerialName("VoteDate") val voteDate: ODataInstant? = null,
    @SerialName("PublicationDate") val publicationDate: ODataInstant? = null,
    @SerialName("PublicationSeriesID") val publicationSeriesID: Int? = null,
    @SerialName("PublicationSeriesDesc") val publicationSeriesDesc: String? = null,
    @SerialName("MagazineNumber") val magazineNumber: String? = null,
    @SerialName("PageNumber") val pageNumber: String? = null,
    @SerialName("CommencementDate") val commencementDate: ODataInstant? = null,
    @SerialName("LastUpdatedBy") val lastUpdatedBy: Int? = null,
    @SerialName("LastUpdatedDate") val lastUpdatedDate: ODataInstant? = null,
    @SerialName("CreatedDate") val createdDate: ODataInstant? = null,
)

@Serializable
data class MkSiteCodeDto(
    @SerialName("Id") val id: Int,
    @SerialName("KnsID") val knsID: Int? = null,
    @SerialName("SiteId") val siteId: Int? = null,
)

@Serializable
data class PersonDto(
    @SerialName("Id") val id: Int,
    @SerialName("LastName") val lastName: String? = null,
    @SerialName("FirstName") val firstName: String? = null,
    @SerialName("GenderID") val genderID: Int? = null,
    @SerialName("GenderDesc") val genderDesc: String? = null,
    @SerialName("Email") val email: String? = null,
    @SerialName("IsCurrent") val isCurrent: Boolean? = null,
    @SerialName("LastUpdatedDate") val lastUpdatedDate: ODataInstant? = null,
)

@Serializable
data class PersonToPositionDto(
    @SerialName("Id") val id: Int,
    @SerialName("PersonID") val personID: Int? = null,
    @SerialName("PositionID") val positionID: Int? = null,
    @SerialName("KnessetNum") val knessetNum: Int? = null,
    @SerialName("StartDate") val startDate: ODataInstant? = null,
    @SerialName("FinishDate") val finishDate: ODataInstant? = null,
    @SerialName("GovMinistryID") val govMinistryID: Int? = null,
    @SerialName("GovMinistryName") val govMinistryName: String? = null,
    @SerialName("DutyDesc") val dutyDesc: String? = null,
    @SerialName("FactionID") val factionID: Int? = null,
    @SerialName("FactionName") val factionName: String? = null,
    @SerialName("GovernmentNum") val governmentNum: Int? = null,
    @SerialName("CommitteeID") val committeeID: Int? = null,
    @SerialName("CommitteeName") val committeeName: String? = null,
    @SerialName("IsCurrent") val isCurrent: Boolean? = null,
    @SerialName("LastUpdatedDate") val lastUpdatedDate: ODataInstant? = null,
    @SerialName("KNS_Person") val person: PersonDto? = null,
    @SerialName("KNS_Position") val position: PositionDto? = null,
)

@Serializable
data class PlenumSessionDto(
    @SerialName("Id") val id: Int,
    @SerialName("Number") val number: Int? = null,
    @SerialName("KnessetNum") val knessetNum: Int? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("StartDate") val startDate: ODataInstant? = null,
    @SerialName("FinishDate") val finishDate: ODataInstant? = null,
    @SerialName("IsSpecialMeeting") val isSpecialMeeting: Boolean? = null,
    @SerialName("LastUpdatedDate") val lastUpdatedDate: ODataInstant? = null,
    @SerialName("KNS_PlmSessionItem") val plmSessionItem: List<PlmSessionItemDto> = emptyList(),
    @SerialName("KNS_DocumentPlenumSession") val documentPlenumSession: List<DocumentDto> = emptyList(),
)

@Serializable
data class PlenumVoteDto(
    @SerialName("Id") val id: Int,
    @SerialName("VoteDateTime") val voteDateTime: ODataInstant? = null,
    @SerialName("SessionID") val sessionID: Int? = null,
    @SerialName("ItemID") val itemID: Int? = null,
    @SerialName("Ordinal") val ordinal: Int? = null,
    @SerialName("VoteMethodID") val voteMethodID: Int? = null,
    @SerialName("VoteMethodDesc") val voteMethodDesc: String? = null,
    @SerialName("VoteStatusCode") val voteStatusCode: Int? = null,
    @SerialName("VoteStatusDesc") val voteStatusDesc: String? = null,
    @SerialName("VoteTitle") val voteTitle: String? = null,
    @SerialName("VoteSubject") val voteSubject: String? = null,
    @SerialName("IsNoConfidenceInGov") val isNoConfidenceInGov: Boolean? = null,
    @SerialName("LastUpdatedDate") val lastUpdatedDate: ODataInstant? = null,
    @SerialName("ForOptionID") val forOptionID: Int? = null,
    @SerialName("ForOptionDesc") val forOptionDesc: String? = null,
    @SerialName("AgainstOptionID") val againstOptionID: Int? = null,
    @SerialName("AgainstOptionDesc") val againstOptionDesc: String? = null,
    @SerialName("KNS_PlenumSession") val plenumSession: PlenumSessionDto? = null,
)

@Serializable
data class PlenumVoteResultDto(
    @SerialName("Id") val id: Int,
    @SerialName("MkId") val mkId: Int? = null,
    @SerialName("VoteID") val voteID: Int? = null,
    @SerialName("VoteDate") val voteDate: ODataInstant? = null,
    @SerialName("ResultCode") val resultCode: Int? = null,
    @SerialName("ResultDesc") val resultDesc: String? = null,
    @SerialName("LastUpdatedDate") val lastUpdatedDate: ODataInstant? = null,
    @SerialName("LastName") val lastName: String? = null,
    @SerialName("FirstName") val firstName: String? = null,
    @SerialName("SessionID") val sessionID: Int? = null,
    @SerialName("ItemID") val itemID: Int? = null,
    @SerialName("Vote") val vote: PlenumVoteDto? = null,
)

@Serializable
data class PlmSessionItemDto(
    @SerialName("Id") val id: Int,
    @SerialName("ItemID") val itemID: Int? = null,
    @SerialName("PlenumSessionID") val plenumSessionID: Int? = null,
    @SerialName("ItemTypeID") val itemTypeID: Int? = null,
    @SerialName("ItemTypeDesc") val itemTypeDesc: String? = null,
    @SerialName("Ordinal") val ordinal: Long? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("StatusID") val statusID: Int? = null,
    @SerialName("IsDiscussion") val isDiscussion: Int? = null,
    @SerialName("LastUpdatedDate") val lastUpdatedDate: ODataInstant? = null,
)

@Serializable
data class PositionDto(
    @SerialName("Id") val id: Int,
    @SerialName("Description") val description: String? = null,
    @SerialName("GenderID") val genderID: Int? = null,
    @SerialName("GenderDesc") val genderDesc: String? = null,
    @SerialName("LastUpdatedDate") val lastUpdatedDate: ODataInstant? = null,
)

@Serializable
data class QueryDto(
    @SerialName("Id") val id: Int,
    @SerialName("Number") val number: Int? = null,
    @SerialName("KnessetNum") val knessetNum: Int? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("TypeID") val typeID: Int? = null,
    @SerialName("TypeDesc") val typeDesc: String? = null,
    @SerialName("StatusID") val statusID: Int? = null,
    @SerialName("PersonID") val personID: Int? = null,
    @SerialName("GovMinistryID") val govMinistryID: Int? = null,
    @SerialName("SubmitDate") val submitDate: ODataInstant? = null,
    @SerialName("ReplyMinisterDate") val replyMinisterDate: ODataInstant? = null,
    @SerialName("ReplyDatePlanned") val replyDatePlanned: ODataInstant? = null,
    @SerialName("LastUpdatedDate") val lastUpdatedDate: ODataInstant? = null,
    @SerialName("KNS_DocumentQuery") val documentQuery: List<DocumentDto> = emptyList(),
    @SerialName("KNS_Person") val person: PersonDto? = null,
    @SerialName("KNS_GovMinistry") val govMinistry: GovMinistryDto? = null,
    @SerialName("KNS_Status") val status: StatusDto? = null,
)

@Serializable
data class SecLawAuthorizingLawDto(
    @SerialName("Id") val id: Int,
    @SerialName("AuthorizingLawID") val authorizingLawID: Int? = null,
    @SerialName("SecondaryLawID") val secondaryLawID: Int? = null,
    @SerialName("LastUpdatedDate") val lastUpdatedDate: ODataInstant? = null,
)

@Serializable
data class SecLawRegulatorDto(
    @SerialName("Id") val id: Int,
    @SerialName("SecondaryLawID") val secondaryLawID: Int? = null,
    @SerialName("RegulatorTypeID") val regulatorTypeID: Int? = null,
    @SerialName("RegulatorTypeDesc") val regulatorTypeDesc: String? = null,
    @SerialName("RegulatorID") val regulatorID: Int? = null,
    @SerialName("RegulatorDesc") val regulatorDesc: String? = null,
    @SerialName("LastUpdatedDate") val lastUpdatedDate: ODataInstant? = null,
)

@Serializable
data class SecondaryLawDto(
    @SerialName("Id") val id: Int,
    @SerialName("KnessetNum") val knessetNum: Int? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("CompletionCauseID") val completionCauseID: Int? = null,
    @SerialName("CompletionCauseDesc") val completionCauseDesc: String? = null,
    @SerialName("PostponementReasonID") val postponementReasonID: Int? = null,
    @SerialName("PostponementReasonDesc") val postponementReasonDesc: String? = null,
    @SerialName("KnessetInvolvementID") val knessetInvolvementID: Int? = null,
    @SerialName("KnessetInvolvementDesc") val knessetInvolvementDesc: String? = null,
    @SerialName("CommitteeID") val committeeID: Int? = null,
    @SerialName("PublicationSeriesID") val publicationSeriesID: Int? = null,
    @SerialName("PublicationSeriesDesc") val publicationSeriesDesc: String? = null,
    @SerialName("MagazineNumber") val magazineNumber: String? = null,
    @SerialName("PageNumber") val pageNumber: String? = null,
    @SerialName("PublicationDate") val publicationDate: ODataInstant? = null,
    @SerialName("MajorAuthorizingLawID") val majorAuthorizingLawID: Int? = null,
    @SerialName("CommitteeReceivedDate") val committeeReceivedDate: ODataInstant? = null,
    @SerialName("CommitteeApprovalDate") val committeeApprovalDate: ODataInstant? = null,
    @SerialName("ApprovalDateWithoutDiscussion") val approvalDateWithoutDiscussion: ODataInstant? = null,
    @SerialName("IsAmmendingLawOriginal") val isAmmendingLawOriginal: Boolean? = null,
    @SerialName("ClassificationID") val classificationID: Int? = null,
    @SerialName("ClassificationDesc") val classificationDesc: String? = null,
    @SerialName("IsEmergency") val isEmergency: Boolean? = null,
    @SerialName("SecretaryReceivedDate") val secretaryReceivedDate: ODataInstant? = null,
    @SerialName("PlenumApprovalDate") val plenumApprovalDate: ODataInstant? = null,
    @SerialName("TypeID") val typeID: Int? = null,
    @SerialName("TypeDesc") val typeDesc: String? = null,
    @SerialName("StatusID") val statusID: Int? = null,
    @SerialName("StatusName") val statusName: String? = null,
    @SerialName("IsCurrent") val isCurrent: Boolean? = null,
    @SerialName("LastUpdatedDate") val lastUpdatedDate: ODataInstant? = null,
    @SerialName("KNS_DocumentSecondaryLaw") val documentSecondaryLaw: List<DocumentDto> = emptyList(),
    @SerialName("KNS_SecLawRegulator") val secLawRegulator: List<SecLawRegulatorDto> = emptyList(),
    @SerialName("KNS_SecLawAuthorizingLaw") val secLawAuthorizingLaw: List<SecLawAuthorizingLawDto> = emptyList(),
    @SerialName("KNS_SecToSecBinding") val secToSecBinding: List<SecToSecBindingDto> = emptyList(),
    @SerialName("KNS_SecToSecBindingParent") val secToSecBindingParent: List<SecToSecBindingDto> = emptyList(),
    @SerialName("KNS_Committee") val committee: CommitteeDto? = null,
)

@Serializable
data class SecToSecBindingDto(
    @SerialName("Id") val id: Int,
    @SerialName("SecChildID") val secChildID: Int? = null,
    @SerialName("SecChildTypeID") val secChildTypeID: Int? = null,
    @SerialName("SecParentID") val secParentID: Int? = null,
    @SerialName("SecParentTypeID") val secParentTypeID: Int? = null,
    @SerialName("SecMainID") val secMainID: Int? = null,
    @SerialName("SecMainTypeID") val secMainTypeID: Int? = null,
    @SerialName("BindingTypeID") val bindingTypeID: Int? = null,
    @SerialName("BindingTypeDesc") val bindingTypeDesc: String? = null,
    @SerialName("IsTempLegislation") val isTempLegislation: Boolean? = null,
    @SerialName("IsSecondaryAmendment") val isSecondaryAmendment: Boolean? = null,
    @SerialName("CorrectionNumber") val correctionNumber: Int? = null,
    @SerialName("AmendmentTypeID") val amendmentTypeID: Int? = null,
    @SerialName("AmendmentTypeDesc") val amendmentTypeDesc: String? = null,
    @SerialName("ParagraphNumber") val paragraphNumber: String? = null,
    @SerialName("LastUpdatedDate") val lastUpdatedDate: ODataInstant? = null,
)

@Serializable
data class StatusDto(
    @SerialName("Id") val id: Int,
    @SerialName("Desc") val desc: String? = null,
    @SerialName("TypeID") val typeID: Int? = null,
    @SerialName("TypeDesc") val typeDesc: String? = null,
    @SerialName("OrderTransition") val orderTransition: Int? = null,
    @SerialName("IsActive") val isActive: Boolean? = null,
    @SerialName("LastUpdatedDate") val lastUpdatedDate: ODataInstant? = null,
)

/** `IdentityNumber` is deliberately absent: it is never requested, stored or shown. */
@Serializable
data class LobbyistDto(
    @SerialName("Id") val id: Int,
    @SerialName("FullName") val fullName: String? = null,
    @SerialName("PermitTypeValue") val permitTypeValue: String? = null,
    @SerialName("Key") val key: Int? = null,
    @SerialName("CorporationName") val corporationName: String? = null,
    @SerialName("IsIndependent") val isIndependent: Boolean? = null,
    @SerialName("CorpNumber") val corpNumber: String? = null,
    @SerialName("PracticeFramework") val practiceFramework: String? = null,
    @SerialName("IsMemberInFaction") val isMemberInFaction: String? = null,
    @SerialName("MemberInFaction") val memberInFaction: Boolean? = null,
)

@Serializable
data class LobbyistsClientDto(
    @SerialName("Id") val id: Int,
    @SerialName("LobbyistID") val lobbyistID: Int? = null,
    @SerialName("ClientID") val clientID: Int? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("ClientsNames") val clientsNames: String? = null,
)

/** All KNS_Document* sets share these fields; the parent id field differs and is not needed here. */
@Serializable
data class DocumentDto(
    @SerialName("Id") val id: Int,
    @SerialName("GroupTypeID") val groupTypeId: Int? = null,
    @SerialName("GroupTypeDesc") val groupTypeDesc: String? = null,
    @SerialName("DocumentName") val documentName: String? = null,
    @SerialName("ApplicationID") val applicationId: Int? = null,
    @SerialName("ApplicationDesc") val applicationDesc: String? = null,
    @SerialName("FilePath") val filePath: String? = null,
    @SerialName("LastUpdatedDate") val lastUpdatedDate: ODataInstant? = null,
)
