# Knesset app: API research, UX design and implementation plan

Status: plan only. Nothing in this document is implemented yet.
Target: a Kotlin Multiplatform app (Compose Multiplatform UI) that ships for Android first.
Audience: the engineer (or agent) who implements the app.

Contents

1. API research
2. Product scope and UX
3. App logic
4. Architecture and tech stack
5. Milestones with acceptance criteria
6. Build, CI and getting the APK onto a phone
7. Risks and open questions

Legend used throughout: **[verified]** means observed in a live response on 2026-11-19. **[unverified]** means inferred from metadata or prior knowledge and must be checked by the implementer against the live API before relying on it.

---

## 1. API research

### 1.1 Services

The Knesset publishes three public, read-only, unauthenticated OData services.

| Service | Base URL | Protocol | Use in this app |
|---|---|---|---|
| ParliamentInfo v4 | `https://knesset.gov.il/OdataV4/ParliamentInfo/` | OData 4.0, JSON | **Primary and only runtime source.** 48 entity sets, includes votes and lobbyists. |
| ParliamentInfo v3 | `https://knesset.gov.il/Odata/ParliamentInfo.svc/` | OData 3, XML or `$format=json` | Legacy. 38 entity sets. Not used, except as reference. Only v3 has `KNS_Law` and `KNS_DocumentLaw`. |
| Votes v3 | `https://knesset.gov.il/Odata/Votes.svc/` | OData 3 | Legacy vote views. Superseded by `KNS_PlenumVote` and `KNS_PlenumVoteResult` in v4. Not used. |

Documents (protocols, bill texts, replies) are plain files on `https://fs.knesset.gov.il/...`, referenced by `FilePath` fields.

There is no write API, no auth, no API key, and no push or webhook. Everything is polling.

### 1.2 Protocol behavior of the v4 service

All **[verified]** unless marked.

- Service document: `GET /` returns `{"@odata.context", "value":[{"name","kind":"EntitySet","url"}]}`.
- Metadata: `GET /$metadata` returns EDMX XML, namespace `OdataService.DAL.ParliamentInfo`. No functions or actions.
- Collection response: `{"@odata.context": "...", "@odata.count": N (only when asked), "value": [...], "@odata.nextLink": "..." (only when more rows exist)}`.
- Single entity: `GET /KNS_Bill(5)` returns the entity fields at top level, with no `value` wrapper.
- Every entity type has the integer key `Id` (v3 used `BillID`, `PersonID`, and so on). Foreign keys keep their long names (`BillID`, `PersonID`, `CommitteeID`).
- **Server page cap is 100 rows.** `$top=150` returns 100 rows plus a `nextLink`. A request with no `$top` returns 100 rows and `nextLink` of the form `...?$skip=100`.
- `nextLink` is quirky: for `$skip=100&$top=150` it returned `$top=50&$skip=200`, and it drops `$count`. **Do not follow `nextLink`. Page with explicit `$top` and `$skip`.**
- Supported and verified query options: `$filter` (`eq`, `and`, `contains(Field,'text')`, `ne null` **[unverified]**), `$orderby ... desc`, `$top`, `$skip`, `$select`, `$count=true`, `$expand` (single, collection, nested like `KNS_BillInitiator($expand=KNS_Person)`, and with options like `VoteResults($top=3)`).
- Dates are ISO 8601 with an Israel offset that follows DST, for example `2026-11-16T13:01:33.717+02:00` and `2026-07-28T00:00:00+03:00`. Fractional seconds have variable length (0 to 3 digits). Date-only values are midnight local time.
- Text is Hebrew, raw UTF-8. **The API has no language parameter and no translations.** All names and descriptions are Hebrew only.
- Nulls are common on almost every field. Treat every non-key field as nullable.
- **Geo restriction:** one request from outside Israel returned HTTP 200 with a short `text/html` page saying the site is temporarily inaccessible from that location. The client must treat a non-JSON 200 as an error of kind "blocked by location".
- **Rate limiting:** bursts of parallel requests were refused. Keep concurrency low (see 3.4).
- `V_Lobbyists` and `V_LobbyistsClients` are listed in the service document, but both test requests failed with a 4xx. Treat lobbyists as an optional feature that hides itself when the endpoint fails. **[unverified]**

### 1.3 Entity catalogue (v4)

Row counts are from `$count=true` on 2026-11-19 where shown.

**Legislation**

| Entity set | Key fields | Notes |
|---|---|---|
| `KNS_Bill` (60,291) | KnessetNum, Name, TypeID/TypeDesc, SubTypeID/SubTypeDesc (פרטית, ממשלתית, ועדה), PrivateNumber, Number, CommitteeID, StatusID, PostponementReasonID/Desc, PublicationDate, PublicationSeriesID/Desc, PublicationSeriesFirstCallID/Desc, MagazineNumber (string), PageNumber (string), IsContinuationBill, SummaryLaw, LastUpdatedDate | Nav: KNS_BillInitiator, KNS_BillHistoryInitiator, KNS_BillName, KNS_BillSplit, KNS_BillUnion, KNS_BillUnion_Main, KNS_DocumentBill, KNS_LawCorrections, KNS_Committee, KNS_Status |
| `KNS_BillInitiator` | BillID, PersonID, IsInitiator, Ordinal | IsInitiator true means initiator, false means joined signer. Nav: KNS_Bill, KNS_Person |
| `KNS_BillHistoryInitiator` | BillID, PersonID, IsInitiator, StartDate, EndDate, ReasonID/Desc | Initiators who left or were replaced |
| `KNS_BillName` | BillID, Name, NameHistoryTypeID/Desc | Name history of a bill |
| `KNS_BillSplit` | MainBillID, SplitBillID, Name | Bill splits |
| `KNS_BillUnion` | MainBillID, UnionBillID | Bill merges |
| `KNS_DocumentBill` | BillID, GroupTypeID/Desc, ApplicationID/Desc, FilePath | Bill texts per stage |
| `KNS_IsraelLaw` (2,024) | KnessetNum, Name, IsBasicLaw, IsFavoriteLaw, IsBudgetLaw, PublicationDate, LatestPublicationDate, LawValidityID/Desc, ValidityStartDate(+Notes), ValidityFinishDate(+Notes) | The statute book ("parent laws"). Nav: KNS_DocumentIsraelLaw, KNS_IsraelLawBinding, KNS_IsraelLawClassificiation (sic), KNS_IsraelLawMinistry, KNS_IsraelLawName, KNS_LawBinding, KNS_IsraelLawLawCorrections |
| `KNS_IsraelLawName` | IsraelLawID, LawID, LawTypeID, Name | Name history of a law |
| `KNS_IsraelLawClassificiation` | IsraelLawID, ClassificiationID/Desc | Topic classification |
| `KNS_IsraelLawMinistry` | IsraelLawID, MinistryCategoryID/Desc | Responsible ministry |
| `KNS_IsraelLawBinding` | IsraelLawID, IsraelLawReplacedID, LawID, LawTypeID | Law replaced by law |
| `KNS_LawBinding` | LawID, IsraelLawID, ParentLawID, LawTypeID, LawParentTypeID, BindingType/Desc, AmendmentType/Desc, PageNumber, IsTempLegislation, IsSecondaryAmendment, CorrectionNumber, ParagraphNumber | Links a bill (LawID) to the law it amends (IsraelLawID) |
| `KNS_LawCorrections` | BillID, CorrectionTypeID/Desc, IsKnessetInvolvement, CommitteeID, CorrectionStatusID/Desc, VoteDate, PublicationDate, PublicationSeriesID/Desc, MagazineNumber, PageNumber, CommencementDate | Error corrections |
| `KNS_IsraelLawLawCorrections` | LawCorrectionID, IsraelLawID | Join table |
| `KNS_DocumentIsraelLaw` | IsraelLawID, GroupType, Application, FilePath | Law documents |
| `KNS_SecondaryLaw` (60,363) | KnessetNum, Name, TypeID/TypeDesc, StatusID/StatusName, ClassificationID/Desc, KnessetInvolvementID/Desc, CommitteeID, CompletionCauseID/Desc, PostponementReasonID/Desc, PublicationSeriesID/Desc, MagazineNumber, PageNumber, PublicationDate, MajorAuthorizingLawID, CommitteeReceivedDate, CommitteeApprovalDate, ApprovalDateWithoutDiscussion, SecretaryReceivedDate, PlenumApprovalDate, IsAmmendingLawOriginal, IsEmergency, IsCurrent | Regulations and other actions under law. Nav: KNS_Committee, KNS_DocumentSecondaryLaw, KNS_SecToSecBinding (3 variants), KNS_SecLawAuthorizingLaw, KNS_SecLawRegulator |
| `KNS_SecLawAuthorizingLaw` | AuthorizingLawID, SecondaryLawID | Which law authorizes the regulation |
| `KNS_SecLawRegulator` | SecondaryLawID, RegulatorTypeID/Desc, RegulatorID/Desc | Who issued it |
| `KNS_SecToSecBinding` | SecChildID, SecParentID, SecMainID (+TypeIDs), BindingTypeID/Desc, AmendmentTypeID/Desc, IsTempLegislation, IsSecondaryAmendment, CorrectionNumber, ParagraphNumber | Regulation amends regulation |
| `KNS_DocumentSecondaryLaw` | SecondaryLawId, GroupType, Application, FilePath | |

**Plenum**

| Entity set | Key fields | Notes |
|---|---|---|
| `KNS_PlenumSession` | Number, KnessetNum, Name, StartDate, FinishDate, IsSpecialMeeting | Nav: KNS_PlmSessionItem, KNS_DocumentPlenumSession, KNS_PlenumVote |
| `KNS_PlmSessionItem` | ItemID, PlenumSessionID, ItemTypeID/Desc, Ordinal, Name, StatusID, IsDiscussion | Agenda of a sitting. `ItemTypeID` + `ItemID` point to a bill, query, motion and so on (see KNS_ItemType) |
| `KNS_DocumentPlenumSession` | PlenumSessionID, GroupTypeDesc (for example "דברי הכנסת"), ApplicationDesc, FilePath | Protocols |
| `KNS_PlenumVote` (36,171) | VoteDateTime, SessionID, ItemID, Ordinal, VoteMethodID/Desc (1 אלקטרונית, 2 שמית), VoteStatusCode/Desc (7 מפורסם), VoteTitle, VoteSubject, IsNoConfidenceInGov, ForOptionID/Desc, AgainstOptionID/Desc | Nav: KNS_PlenumSession, `VoteResults` (collection) |
| `KNS_PlenumVoteResult` (1,953,990) | MkId, VoteID, VoteDate, ResultCode/ResultDesc (seen: 7 בעד, 6 נוכח; others exist), LastName, FirstName, SessionID, ItemID | Nav: `Vote`, KNS_Person. `MkId` is the person id **[unverified, strongly implied by the KNS_Person navigation]** |
| `KNS_Agenda` (22,072) | Number, KnessetNum, Name, ClassificationID/Desc, SubTypeID/Desc (for example "דיון מהיר"), StatusID, InitiatorPersonID, LeadingAgendaID, GovRecommendationID/Desc, PresidentDecisionDate, PostopenmentReasonID/Desc (sic), CommitteeID, RecommendCommitteeID, MinisterPersonID | Motions for the agenda. Nav: KNS_DocumentAgenda, KNS_Committee, KNS_RecommendCommittee, KNS_Person, KNS_PersonMinister, KNS_Status |
| `KNS_DocumentAgenda` | AgendaID, GroupType, Application, FilePath | |
| `KNS_Query` (42,824) | Number, KnessetNum, Name, TypeID/TypeDesc (for example "דחופה"), StatusID, PersonID, GovMinistryID, SubmitDate, ReplyMinisterDate, ReplyDatePlanned | Parliamentary questions. Nav: KNS_Person, KNS_GovMinistry, KNS_Status, KNS_DocumentQuery |
| `KNS_DocumentQuerie` (sic, set name; type is KNS_DocumentQuery) | QueryID, GroupType, Application, FilePath | Question and reply files |

**Committees**

| Entity set | Key fields | Notes |
|---|---|---|
| `KNS_Committee` (423 current) | Name, CategoryID/Desc, KnessetNum, CommitteeTypeID/Desc, AdditionalTypeID/Desc (for example "קבועה"), Email, StartDate, FinishDate, ParentCommitteeID, CommitteeParentName, IsCurrent | `IsCurrent` is loose (423 rows, some from Knesset 15). Filter also on KnessetNum or FinishDate. Nav: KNS_CommitteeSession, KNS_Bill, KNS_Agenda, KNS_AgendaRecomnded (sic), KNS_JointCommittee, KNS_JointCommitteeParticipant, KNS_SecondaryLaw, KNS_LawCorrections |
| `KNS_JointCommittee` | CommitteeID, ParticipantCommitteeID | |
| `KNS_CommitteeSession` | Number, KnessetNum, TypeID/Desc (פתוחה...), CommitteeID, StatusID/Desc, Location, SessionUrl, BroadcastUrl, StartDate, FinishDate, Note | Includes future sessions. `SessionUrl` is an `http://main.knesset.gov.il/...` page. Nav: KNS_Committee, KNS_Status, KNS_CmtSiteCode, KNS_CmtSessionItem, KNS_DocumentCommitteeSession |
| `KNS_CmtSessionItem` | ItemID, CommitteeSessionID, Ordinal, StatusID, Name, ItemTypeID | Session agenda |
| `KNS_DocumentCommitteeSession` | CommitteeSessionID, GroupTypeDesc, DocumentName, Application, FilePath | Protocols and background material |
| `KNS_BroadcastCommitteSession` (sic) | BroadcastId, BroadcastUrl | Sampled rows were all null. Low value |
| `KNS_CmtSiteCode` | KnsID, SiteId | Maps a committee id to its id on the public website |

**People and bodies**

| Entity set | Key fields | Notes |
|---|---|---|
| `KNS_Person` | LastName, FirstName, GenderID/Desc (250 נקבה, 251 זכר), Email, IsCurrent | **`IsCurrent eq true` returns 131 rows and includes non-MK office holders.** Do not use it as the MK list. `Email` sometimes holds a name instead of an address, so validate before making it tappable. Nav: KNS_PersonToPosition, KNS_PlenumVoteResult, KNS_Query, KNS_Agenda, KNS_AgendaMinister, KNS_BillInitiator, KNS_BillHistoryInitiator |
| `KNS_PersonToPosition` | PersonID, PositionID, KnessetNum, StartDate, FinishDate, GovMinistryID/Name, DutyDesc, FactionID/Name, GovernmentNum, CommitteeID/Name, IsCurrent | Every role a person held: faction member (PositionID 54 "חבר/ת סיעה"), committee member (42 "חבר ועדה"), minister and so on. Nav: KNS_Position, KNS_Person, KNS_Faction, KNS_GovMinistry |
| `KNS_Position` | Description, GenderID/Desc | Lookup of positions |
| `KNS_Faction` (12 current) | Name, KnessetNum, StartDate, FinishDate, IsCurrent | Contains a placeholder row (Id 911, "אין נתונים", KnessetNum 1). Filter on KnessetNum as well |
| `KNS_GovMinistry` | Name, IsActive, CategoryID/Name, GovID | |
| `KNS_MkSiteCode` | KnsID, SiteId | Maps a person id to the MK id on the public website |
| `V_Lobbyists` | IdentityNumber, FullName, PermitTypeValue, Key, CorporationName, IsIndependent, CorpNumber, PracticeFramework, IsMemberInFaction, MemberInFaction | **[unverified]** fields from a summarized metadata tail, endpoint failed in tests. **Never display `IdentityNumber`.** |
| `V_LobbyistsClients` | LobbyistID, ClientID, Name, ClientsNames | **[unverified]** |

**Lookups**

| Entity set | Notes |
|---|---|
| `KNS_KnessetDates` | KnessetNum, Name, Assembly, Plenum, PlenumStart, PlenumFinish, IsCurrent. One row per plenum term. Current: Knesset 26, started 2026-11-10 |
| `KNS_Status` (82 rows) | Desc, TypeID/TypeDesc, OrderTransition, IsActive. Status vocabulary shared by bills (36 values), queries, agendas, sessions, items and secondary laws. Some `Desc` values are null |
| `KNS_ItemType` (8 rows) | 1 שאילתה → KNS_Query, 2 הצעת חוק → KNS_Bill, 4 הצעה לסדר היום → KNS_Agenda, 5 ישיבת מליאה → KNS_PlenumSession, 12 ישיבת ועדה → KNS_CommitteSession (sic), 6000 פעולה על פי חוק → KNS_SecondaryLaw, 6002 חוק בן חיצוני → KNS_Bill, 6003 חוק אב → KNS_IsraelLaw |

### 1.4 Documents

- `FilePath` is a full URL on `fs.knesset.gov.il`, for example `https://fs.knesset.gov.il/26/Plenum/26_ptm_16255398.doc` and `https://fs.knesset.gov.il/25/parliamentquestion/25_pq_13945857.pdf`.
- Some paths contain backslashes (`fs.knesset.gov.il/26\law\...pdf`) and may lack a scheme. Normalize: replace `\` with `/`, prepend `https://` if missing, upgrade `http://` to `https://`.
- `ApplicationDesc` gives the format (`PDF`, `DOC`, others possible). `GroupTypeDesc` gives the meaning (for example "הצעת חוק לדיון מוקדם", "דברי הכנסת").
- Many items have both a DOC and a PDF version. Prefer PDF.

### 1.5 Useful queries

Replace `K` with a Knesset number. All values must be URL-encoded. Single quotes inside search text are doubled.

```
Current Knesset:      KNS_KnessetDates?$filter=IsCurrent eq true
All Knesset terms:    KNS_KnessetDates?$orderby=KnessetNum desc,Plenum desc
MKs of a Knesset:     KNS_PersonToPosition?$filter=KnessetNum eq K and FactionID ne null&$expand=KNS_Person&$top=100&$skip=0
Factions:             KNS_Faction?$filter=KnessetNum eq K&$orderby=Name
Person roles:         KNS_PersonToPosition?$filter=PersonID eq P&$orderby=StartDate desc&$expand=KNS_Position
Bills list:           KNS_Bill?$filter=KnessetNum eq K&$orderby=LastUpdatedDate desc&$top=50&$skip=0&$count=true&$select=Id,Name,SubTypeDesc,StatusID,LastUpdatedDate,KnessetNum
Bill search:          ...$filter=contains(Name,'חינוך') and KnessetNum eq K
Bill detail:          KNS_Bill(ID)?$expand=KNS_BillInitiator($expand=KNS_Person),KNS_Status,KNS_Committee,KNS_DocumentBill,KNS_BillName
Bills by MK:          KNS_BillInitiator?$filter=PersonID eq P&$expand=KNS_Bill&$orderby=Id desc
Votes list:           KNS_PlenumVote?$orderby=VoteDateTime desc&$top=50&$count=true
Votes of a sitting:   KNS_PlenumVote?$filter=SessionID eq S&$orderby=Ordinal
Vote breakdown:       KNS_PlenumVoteResult?$filter=VoteID eq V&$top=100&$skip=0   (2 pages, up to 120 rows)
MK voting record:     KNS_PlenumVoteResult?$filter=MkId eq P&$orderby=VoteDate desc&$expand=Vote&$top=50
Plenum sittings:      KNS_PlenumSession?$filter=KnessetNum eq K&$orderby=StartDate desc
Sitting detail:       KNS_PlenumSession(ID)?$expand=KNS_PlmSessionItem,KNS_DocumentPlenumSession
Committee sessions:   KNS_CommitteeSession?$filter=CommitteeID eq C&$orderby=StartDate desc&$expand=KNS_CmtSessionItem
Upcoming sessions:    KNS_CommitteeSession?$filter=StartDate ge 2026-11-19T00:00:00%2B02:00&$orderby=StartDate&$expand=KNS_Committee
Queries by ministry:  KNS_Query?$filter=GovMinistryID eq M and KnessetNum eq K&$orderby=SubmitDate desc
```

The date literal form in `$filter` (`StartDate ge <DateTimeOffset>`) is **[unverified]**. Verify first. The `+` in the offset must be encoded as `%2B`.

---

## 2. Product scope and UX

### 2.1 Principles

- Read-only civic browser. No accounts, no backend of our own, no analytics.
- Hebrew first, right-to-left. Data is Hebrew only, so the UI chrome is Hebrew by default with an English chrome option. Data is never translated.
- Simple: five tabs, one list pattern, one detail pattern, one filter pattern, reused everywhere.
- Complete: every one of the 48 entity sets is reachable, either through a designed screen or through the generic Data Explorer (2.8).

### 2.2 Navigation map

Bottom navigation with five tabs. In RTL the first tab sits on the right.

| Tab | Hebrew label | Sections (top tabs inside the screen) |
|---|---|---|
| Home | היום | Dashboard |
| Legislation | חקיקה | Bills, Laws, Secondary legislation |
| Plenum | מליאה | Votes, Sittings, Motions, Questions |
| Committees | ועדות | Committees, Session calendar |
| People | אנשים | Members, Factions, Ministries, Lobbyists |

Top app bar on every root screen: title, **Knesset selector chip** (for example "הכנסת ה-26"), search icon, overflow menu (Favorites, Data Explorer, Settings, About).

The Knesset selector is global state. Changing it re-filters every list that has a `KnessetNum`. It offers every Knesset number found in `KNS_KnessetDates`, plus "all". Default is the current Knesset.

### 2.3 Shared patterns

**List screen.** Search field (filters by name, server side), a row of filter chips, a result count from `$count`, a sort menu, then an infinitely paged list. Pull to refresh. States: loading skeleton, empty ("לא נמצאו תוצאות"), error with retry, offline banner when showing cached data.

**Row.** Title (up to 3 lines, Hebrew), one line of metadata (type, status chip, date), optional leading avatar for people.

**Detail screen.** Header (title, status chip, key facts), then sections as cards. Related items are tappable rows that navigate to their own detail. Top bar actions: favorite (star), share (text plus a link), open on knesset.gov.il when a site link is known.

**Documents section.** One row per document: meaning (`GroupTypeDesc`), format badge (PDF or DOC), tap opens the normalized URL through the platform URI handler (browser or viewer app). PDF is listed first.

**Status chip.** Text from `KNS_Status.Desc` resolved through the cached lookup. Color by a small local mapping per status type (in progress, passed, stopped), neutral for unknown ids.

### 2.4 Home (היום)

A dashboard for the selected Knesset, each card with a "show all" link into the matching list.

1. Today and upcoming: next plenum sitting and the next 5 committee sessions (time, committee, first agenda item).
2. Latest votes: 5 most recent `KNS_PlenumVote` with for and against totals.
3. Recently updated bills: 5 by `LastUpdatedDate desc`.
4. New laws: 3 latest `KNS_IsraelLaw` by `PublicationDate desc`.
5. Favorites: horizontal strip, hidden when empty.

When the selected Knesset is not the current one, cards 1 and 2 switch to "last activity of that Knesset".

### 2.5 Legislation (חקיקה)

**Bills** (`KNS_Bill`)
- Filters: bill kind (`SubTypeDesc`: private, government, committee), status (multi-select from `KNS_Status` where TypeID is 2), committee, initiator (pick an MK), continuation bill only, date range on `LastUpdatedDate`.
- Sort: last updated (default), publication date, name.
- Detail: all names over time (`KNS_BillName`), type and numbers (private number, bill number, publication series, magazine, page), status with a short stage indicator, summary (`SummaryLaw`), initiators and joined signers (`KNS_BillInitiator` ordered by `Ordinal`, each links to the MK), former initiators (`KNS_BillHistoryInitiator`), handling committee, documents, related bills (splits and unions), laws it amends (`KNS_LawBinding` where `LawID` is this bill), corrections (`KNS_LawCorrections`), and plenum votes whose `ItemID` equals this bill id.

**Laws** (`KNS_IsraelLaw`)
- Filters: basic laws, budget laws, validity (`LawValidityDesc`), classification, ministry, publication date range.
- Detail: validity dates and notes, classifications, ministries, name history, documents, amending bills (`KNS_LawBinding` by `IsraelLawID`, each links to a bill), replaced or replacing laws (`KNS_IsraelLawBinding`), corrections.

**Secondary legislation** (`KNS_SecondaryLaw`)
- Filters: type, status, classification, committee, emergency only, Knesset involvement, date range.
- Detail: the timeline of dates (received, committee approval, plenum approval, publication), regulators (`KNS_SecLawRegulator`), authorizing laws (`KNS_SecLawAuthorizingLaw`, links to laws), related regulations (`KNS_SecToSecBinding`), documents.

### 2.6 Plenum (מליאה)

**Votes** (`KNS_PlenumVote`)
- Filters: date range, method (electronic, roll call), no-confidence only, text search on `VoteTitle`.
- Row: title, date, a thin for/against bar.
- Detail: title, subject, date, method, the option wording (`ForOptionDesc`, `AgainstOptionDesc`), totals grouped by `ResultDesc`, then members grouped by result, with a toggle to group by faction. Each member links to the MK. Link to the sitting and to the item voted on (resolved through `ItemID`).
- Totals are computed on the device from `KNS_PlenumVoteResult`, because the vote entity carries no totals.

**Sittings** (`KNS_PlenumSession`)
- Filters: date range, special sittings only.
- Detail: start and end, agenda items in `Ordinal` order (`KNS_PlmSessionItem`, each resolves to its bill, query or motion through `ItemTypeID` + `ItemID`), votes held in the sitting, protocol documents.

**Motions** (`KNS_Agenda`)
- Filters: sub type, status, classification, initiator, committee.
- Detail: initiator, responding minister, government recommendation, president decision date, committee and recommended committee, leading motion and joined motions (`LeadingAgendaID`), documents.

**Questions** (`KNS_Query`)
- Filters: type (regular, urgent, direct), status, ministry, asking MK, submit date range, answered or not (`ReplyMinisterDate`).
- Detail: asker, ministry, dates (submitted, planned reply, replied), documents (question and reply).

### 2.7 Committees and People

**Committees** (`KNS_Committee`)
- Filters: category, type, permanent or special (`AdditionalTypeDesc`), active only. Sub committees are nested under their parent.
- Detail: facts (type, dates, email, parent), members (`KNS_PersonToPosition` by `CommitteeID` and Knesset, grouped by `DutyDesc` so the chair comes first), joint committee partners, sessions (upcoming first, then past), bills in the committee, motions, secondary legislation.

**Session calendar** (`KNS_CommitteeSession`)
- An agenda view by day across all committees, starting today, with a date picker and a committee filter.
- Session detail: committee, time, location, type, status, note, agenda items (linked), documents, and buttons for the session page (`SessionUrl`) and the broadcast (`BroadcastUrl`) when present.

**Members** (`KNS_Person` through `KNS_PersonToPosition`)
- The list for a Knesset comes from faction membership rows, de-duplicated by person and grouped by faction (toggle: alphabetical). Search covers first and last name.
- Filters: faction, gender, currently serving.
- Detail tabs: Overview (current faction, current roles, email when valid, link to the public site through `KNS_MkSiteCode`), Roles (full history by Knesset), Bills (initiated and joined), Votes (record with result per vote, plus a small summary of counts per result), Questions, Motions.
- No photos in version 1. Use an initials avatar. The API has no photo field.

**Factions** (`KNS_Faction`): list for the Knesset with member count. Detail: dates and members.

**Ministries** (`KNS_GovMinistry`): list with active filter. Detail: ministers and deputies over time (`KNS_PersonToPosition` by `GovMinistryID`), questions addressed to the ministry, laws under its responsibility.

**Lobbyists** (`V_Lobbyists`, `V_LobbyistsClients`): list with search by name or corporation. Detail: permit type, corporation, practice framework, faction membership flag, clients. The section is hidden when the endpoint fails. `IdentityNumber` is never requested (`$select` excludes it) and never shown.

### 2.8 Search, Favorites, Data Explorer, Settings

**Global search.** One field, results grouped by kind with the top 5 of each and a "show all" link that opens the matching list with the search applied. Kinds: members, bills, laws, committees, votes, motions, questions, secondary legislation. Recent searches are kept locally.

**Favorites.** Star any bill, law, member, committee, vote, question, motion or regulation. Stored on the device. The Favorites screen groups by kind and shows an "updated" dot when the item's `LastUpdatedDate` is newer than when the user last opened it (checked on refresh).

**Data Explorer.** This guarantees full coverage of the API. It lists every entity set from the service document. Opening one shows a generic paged list with a generic filter builder (field, operator, value, built from `$metadata`), sort by any field, and a generic detail view of all fields as key and value rows. Fields named `...ID` that match a known entity become links. Fields ending in `FilePath` or `Url` become open buttons. This covers the join and lookup tables that have no designed screen (`KNS_Position`, `KNS_ItemType`, `KNS_Status`, `KNS_CmtSiteCode`, `KNS_BroadcastCommitteSession` and so on).

**Settings.** UI language (Hebrew, English), theme (system, light, dark), default Knesset (current or a fixed number), clear cache, About (data source credit to the Knesset, last sync time, app version).

### 2.9 Hebrew and RTL rules

- Layout direction follows the UI language: RTL for Hebrew, LTR for English. Use start and end, never left and right. Use auto-mirrored icons for back and chevrons.
- Data text is always Hebrew, so data `Text` composables set `TextDirection.Content` and align to start, which keeps Hebrew correct inside an English chrome.
- Dates display in `Asia/Jerusalem` time regardless of the device zone, formatted `dd/MM/yyyy` and `HH:mm`.
- Numbers, bill numbers and URLs embedded in Hebrew text are wrapped in bidi isolates where they are concatenated by the app.
- Hebrew names contain `"` (gershayim, for example `התשפ"ד`). This needs no escaping in OData. Only `'` is doubled.
- Bundle a font with full Hebrew coverage only if the system font shows gaps. The Android system font is fine.

---

## 3. App logic

### 3.1 OData client

One small client in common code, no OData library.

```kotlin
class ODataQuery(val entitySet: String) {
    var key: Long? = null
    val filters = mutableListOf<String>()      // joined with " and "
    var orderBy: String? = null
    var top: Int? = null
    var skip: Int? = null
    var select: List<String> = emptyList()
    var expand: List<String> = emptyList()
    var count: Boolean = false
    fun toUrl(base: String): String             // percent-encodes every value
}

@Serializable class ODataPage<T>(
    @SerialName("@odata.count") val count: Long? = null,
    val value: List<T>,
)
```

- Filter helpers build safe fragments: `eq(field, Int)`, `eq(field, Boolean)`, `contains(field, text)` (doubles `'`), `ge/le(field, Instant)`, `isNotNull(field)`, `anyOf(field, ints)` (expands to `(f eq 1 or f eq 2)`).
- JSON config: `ignoreUnknownKeys = true`, `explicitNulls = false`, `coerceInputValues = true`. Every DTO field except `Id` is nullable with a default.
- Dates: a custom serializer that parses ISO offsets with 0 to 3 fractional digits into `kotlinx.datetime.Instant`.
- Response guard: if the status is 200 but the content type is not JSON, or the body starts with `<`, raise `KnessetError.BlockedOrUnavailable`. Map 429 and 503 to `RateLimited`, timeouts and IO to `Offline`, other 4xx/5xx to `Server(code)`.

### 3.2 Paging

- Page size 50. Request `$top=50&$skip=n`. Stop when a page has fewer than 50 rows.
- `$count=true` only on the first page. Keep the count in list state.
- Never follow `@odata.nextLink`.
- Lists always carry `$select` with only the row fields. Details fetch the full entity.
- A stable sort needs a tiebreaker: append `,Id desc` to every `$orderby`.

### 3.3 Search and filter behavior

- Typing is debounced 400 ms. Minimum 2 characters. A new query cancels the in-flight one.
- Name search is `contains(Name,'text')`. For people, split the input on spaces and require every token in first or last name: `(contains(FirstName,'t') or contains(LastName,'t'))` joined with `and`.
- Filters combine with `and`. Multi-select values within one filter combine with `or`.
- The Knesset selector adds `KnessetNum eq K` wherever the entity has that field. "All" removes it.
- Filter state lives in the screen's view model, survives rotation, and is reflected in a visible "clear all" chip row.
- Global search runs the kinds one after another through the shared request limiter and fills groups as they arrive.
- Filter option lists (statuses, committees, ministries, factions) come from the cached lookups, never hardcoded.

### 3.4 Networking limits

- At most 2 requests in flight (a semaphore in the client). Timeout 20 s.
- Retry idempotent GETs up to 2 times with backoff of 1 s and 3 s on timeout, 429 and 5xx. Honor `Retry-After`.
- Prefer one `$expand` request over several small ones, but keep expands to small collections. Vote results and long histories are fetched as their own paged calls.
- User agent: a generic app name and version. No personal details.

### 3.5 Caching and offline

Storage is SQLDelight with three tables.

| Table | Columns | Purpose |
|---|---|---|
| `http_cache` | url (pk), body, fetched_at | Response cache for every GET |
| `favorite` | kind, id (pk together), title, subtitle, added_at, seen_updated_at | Favorites |
| `recent_search` | text (pk), used_at | Search history |

Settings (language, theme, default Knesset) use multiplatform-settings or DataStore.

Policy is stale-while-revalidate: emit the cached body at once if present, then refetch when it is older than its time to live, and emit again. On network failure keep the cached data and show the offline banner.

| Data | Time to live |
|---|---|
| Lookups: KnessetDates, Status, ItemType, Position, GovMinistry, Faction, service document, metadata | 7 days |
| Any query pinned to a past Knesset | 30 days |
| Detail screens | 1 hour |
| Lists and search results of the current Knesset | 15 minutes |
| Home dashboard, upcoming sessions, latest votes | 5 minutes |

Pull to refresh bypasses the time to live. The cache is trimmed to 50 MB by oldest `fetched_at`. "Clear cache" empties `http_cache` only.

Startup sequence: load the current Knesset and the lookups (from cache when fresh), then show Home. If the first ever launch has no network, show a full-screen error with retry.

### 3.6 Resolving cross references

A single `ItemRef(itemTypeId, itemId)` resolver maps agenda items and vote items to a destination using the cached `KNS_ItemType` table: 1 → question, 2 and 6002 → bill, 4 → motion, 5 → plenum sitting, 12 → committee session, 6000 → secondary law, 6003 → law. Unknown types render as plain text with no link.

### 3.7 Links to the public website

- Committee session: `SessionUrl` from the data (upgrade to https).
- Member page: built from `KNS_MkSiteCode.SiteId`. The URL template must be confirmed on the live site before it ships. **[unverified]** If unconfirmed, omit the button.
- Share action: item title plus the best available link (document URL or site URL), otherwise the title only.

---

## 4. Architecture and tech stack

### 4.1 Stack

Use the latest stable version of each at implementation time and pin them in `gradle/libs.versions.toml`.

| Concern | Choice |
|---|---|
| Language and build | Kotlin Multiplatform, Gradle Kotlin DSL, version catalog |
| UI | Compose Multiplatform, Material 3 |
| Navigation | JetBrains Navigation Compose (multiplatform), type-safe routes |
| State | `ViewModel` (multiplatform lifecycle) + `StateFlow`, one immutable UI state per screen |
| HTTP | Ktor client (OkHttp engine on Android), ContentNegotiation |
| JSON | kotlinx.serialization |
| Dates | kotlinx-datetime |
| Storage | SQLDelight (Android driver) |
| Settings | multiplatform-settings |
| DI | Koin |
| Tests | kotlin.test, kotlinx-coroutines-test, Ktor `MockEngine`, Turbine |
| Strings | Compose Multiplatform resources, `values/` (Hebrew default) and `values-en/` |

Android: `minSdk 26`, target and compile the latest stable SDK, application id `com.chencohensolutions.knesset`, permission `INTERNET` only.

Targets: `androidTarget()` now. Keep all logic and UI in `commonMain` so an iOS target can be added later. Only the SQLDelight driver, the Ktor engine, the URI opener and the share action are platform specific (`expect`/`actual`).

### 4.2 Module and package layout

A single Gradle module keeps the build simple.

```
composeApp/
  src/commonMain/kotlin/com/chencohensolutions/knesset/
    App.kt                      root composable, theme, RTL, nav host
    di/                         Koin modules
    core/
      odata/                    ODataQuery, ODataClient, ODataPage, errors, date serializer
      cache/                    CachedFetcher (stale-while-revalidate), TTL policy
      paging/                   Pager<T> (page size 50, state machine)
      platform/                 expect: UrlOpener, Sharer, DatabaseDriverFactory
    data/
      dto/                      one @Serializable class per entity set
      repo/                     LegislationRepo, PlenumRepo, CommitteeRepo, PeopleRepo,
                                LookupRepo, FavoritesRepo, ExplorerRepo
    domain/                     ItemRef resolver, vote tally, document URL normalizer, status styles
    ui/
      components/               ListScaffold, FilterChipsRow, StatusChip, DocumentRow,
                                SectionCard, KnessetSelector, ErrorState, EmptyState
      home/ legislation/ plenum/ committees/ people/
      search/ favorites/ explorer/ settings/
      theme/
  src/commonMain/sqldelight/    Knesset.sq
  src/commonMain/composeResources/values/strings.xml, values-en/strings.xml
  src/commonTest/               unit tests + JSON fixtures
  src/androidMain/              MainActivity, actual implementations, manifest
```

### 4.3 Layering rules

- UI talks to view models only. View models talk to repositories. Repositories talk to `CachedFetcher`, which talks to `ODataClient` and the database.
- Repositories return `Flow<Resource<T>>` where `Resource` is `Loading`, `Data(value, isStale, fromCache)`, or `Error(kind, cached?)`.
- DTOs mirror the API one to one. Screens use small UI models mapped in the repository, so API quirks (typos in names, nullable everything) stay in the data layer.
- One generic `ListViewModel<Row, Filter>` drives every list screen: it owns query text, filter state, sort, pager and count.

---

## 5. Milestones

Each milestone ends with a working build and passing tests.

**M0. Skeleton.** KMP project, Compose app shell, theme, bottom navigation with five empty tabs, RTL in Hebrew, language switch, CI building a debug APK.
Accept: APK installs and shows five tabs in Hebrew, right to left.

**M1. Data foundation.** `ODataQuery`, `ODataClient`, error mapping, date serializer, request limiter and retry, SQLDelight cache, `CachedFetcher`, `Pager`, `LookupRepo`, the Knesset selector.
Accept: unit tests cover URL building (encoding, quote doubling, `%2B` in offsets), page stop conditions, cap handling, the non-JSON guard, date parsing with 0 to 3 fractional digits and both offsets, and cache freshness rules.

**M2. Legislation.** Bills, laws, secondary legislation: lists, filters, details, documents.
Accept: can find a bill by Hebrew text, filter by status and kind, open its detail with initiators and open a PDF.

**M3. People.** Members by faction, member detail tabs (roles, bills, questions, motions), factions, ministries.
Accept: member list for the current Knesset matches faction membership and has no duplicates.

**M4. Plenum.** Votes with tallies and by-member breakdown, member voting record, sittings with agenda and protocols, motions, questions.
Accept: a vote detail shows totals that equal the number of result rows, grouped by result and by faction.

**M5. Committees.** Committee list and detail, session calendar, session detail with agenda, documents and links.
Accept: the calendar shows upcoming sessions in Israel time and opens a session.

**M6. Home, search, favorites.** Dashboard, global search, favorites with update dots, recent searches, share.
Accept: search for a surname returns members and bills in groups. A starred bill appears on Home.

**M7. Data Explorer, lobbyists, polish.** Generic explorer over all 48 sets, lobbyists section with graceful hiding, settings, About, empty and error states everywhere, accessibility labels, dark theme.
Accept: every entity set in the service document opens in the explorer. With the network off, previously opened screens still render with the offline banner.

**M8. Release.** Signed APK from CI attached to a GitHub Release, short README with install steps and screenshots.

Testing approach: all network tests use `MockEngine` with JSON fixtures stored in `commonTest/resources`. No test calls the live API. Fixtures are captured from the live service by someone who can reach it (see 7).

---

## 6. Build, CI and installing on a phone

- GitHub Actions workflow `android.yml`: on every push and pull request run `./gradlew :composeApp:testDebugUnitTest :composeApp:assembleDebug` on `ubuntu-latest` with JDK 17 and the Gradle cache. Upload the APK as a workflow artifact.
- On a tag `v*`, build a release APK signed with a keystore held in repository secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) and attach it to a GitHub Release. The same key must sign every version, otherwise Android refuses to update over an older install.
- Install path for the owner: open the GitHub Release on the phone, download the APK, allow "install unknown apps" for the browser, install. No Play Store listing is planned.
- The repository owner needs to create the keystore once and add the four secrets. Until then, CI publishes a debug-signed APK, which installs fine but cannot be updated in place across CI runs.

---

## 7. Risks and open questions

1. **Geo restriction.** The API answered at least one foreign request with a "not accessible from your location" page. The app is expected to work on a phone in Israel. Abroad, it will show the blocked state. Cloud build machines and cloud agents may not reach the API at all.
2. **Implementation sandbox access.** The cloud environment used for planning could not reach `knesset.gov.il` directly. The implementer needs either that host allowed in the environment's network settings, or fixtures captured from a machine in Israel. Without one of those, live behavior cannot be verified before the app reaches the phone.
3. **Lobbyist endpoints** failed with 4xx in tests. The feature must hide itself on failure.
4. **Unverified items** to confirm early in M1: date literals in `$filter`, `ne null` filters, `MkId` equals person id, the full set of `ResultCode` values, the member page URL template, and the nested `$expand` cap (whether an expanded collection is also cut at 100 rows).
5. **Data quality.** Placeholder rows ("אין נתונים"), null descriptions, `IsCurrent` flags that are too generous, names in email fields, typos in entity names. The data layer must tolerate all of them.
6. **Large tables.** Vote results have about 2 million rows. Always filter by `VoteID` or `MkId`, never list unfiltered outside the Data Explorer, and there only page by page.
7. **Schema drift.** The service changes without notice (v4 already differs from v3). Unknown JSON keys are ignored and the Data Explorer reads names from the live service document, so new entity sets appear without an app update.
8. **Privacy.** The API exposes emails of people and an identity number for lobbyists. Show official emails only on a person's own detail screen, and never fetch or show identity numbers.
