# Knesset API: plan section 7 items checked live (2026-11-19)

Fetched sequentially through a web fetcher. Raw findings:

- **Date literal in $filter works**: `KNS_CommitteeSession?$filter=StartDate ge 2026-11-19T00:00:00%2B02:00&$orderby=StartDate&$top=3&$select=Id,StartDate,CommitteeID` returned 3 rows (2026-11-23T10:00:00+02:00 ...). `$select` is honored.
- **`ne null` works**: `KNS_PersonToPosition?$filter=KnessetNum eq 25 and FactionID ne null&$expand=KNS_Person`. Row fields: Id, PersonID, PositionID, KnessetNum, StartDate, FinishDate, GovMinistryID, GovMinistryName, DutyDesc, FactionID, FactionName, GovernmentNum, CommitteeID, CommitteeName, IsCurrent, LastUpdatedDate. KNS_Person fields: Id, LastName, FirstName, GenderID, GenderDesc, Email, IsCurrent, LastUpdatedDate.
- **Vote**: latest KNS_PlenumVote Id 46726, fields: Id, VoteDateTime, SessionID, ItemID, Ordinal, VoteMethodID/Desc, VoteStatusCode/Desc, VoteTitle, VoteSubject, IsNoConfidenceInGov, LastUpdatedDate, ForOptionID/Desc, AgainstOptionID/Desc.
- **Vote results**: `KNS_PlenumVoteResult?$filter=VoteID eq 46726&$top=100` gave 53 rows, no nextLink. Fields: Id, MkId, VoteID, VoteDate, ResultCode, ResultDesc, LastUpdatedDate, LastName, FirstName, SessionID, ItemID. Seen codes: 7 בעד, 8 נגד (planning also saw 6 נוכח). Other codes (abstain) not observed in this sample.
- **MkId = KNS_Person.Id: confirmed** (MkId 30601 = KNS_Person(30601), same name).
- **Nested expand cap**: `KNS_PlenumVote(46726)?$expand=VoteResults` failed with a server error. Fetch results with the separate filtered query instead.
- **V_Lobbyists?$top=2**: failed again with a 4xx. Keep the section hidden on failure.
- **KNS_MkSiteCode**: rows like {Id 1, KnsID 405, SiteId 12}. Member page URL template still unconfirmed.
- **Service document**: 48 sets as listed in PLAN 1.3 (spellings KNS_BroadcastCommitteSession, KNS_IsraelLawClassificiation, KNS_DocumentQuerie as returned).
- **Bill with expand** (`KNS_Bill?...&$expand=KNS_BillInitiator($expand=KNS_Person),KNS_DocumentBill,KNS_Status`) works. `CommitteeID` can be -1 (means none). KNS_Status expands as an object. FilePath has backslashes: `https://fs.knesset.gov.il/25\law\25_lst_9292448.pdf`; normalize `\` to `/`. ApplicationDesc seen: DOC (for .docx), PDF.
- **Current Knesset**: KNS_KnessetDates IsCurrent → KnessetNum 26, Name "העשרים ושש", PlenumStart 2026-11-10.
