# Knesset video-by-member — research spec

Researched 2026-11-19 (Opus 4.8) for the "find videos by member" feature. All endpoints below were probed live via a web fetcher (the cloud sandbox cannot reach knesset.gov.il directly). This is a research spec — no app code. It plugs into the existing `VideoLinkSource` interface.

## TL;DR (what's feasible)

| Goal | Feasibility | Source |
|---|---|---|
| **Committee session → video URL** | ✅ Direct, API-keyed | `video.knesset.gov.il` Wowza server; filename = committee-session `Id` |
| **Member → their committee videos** | ✅ Via API joins | `KNS_PersonToPosition` → `KNS_CommitteeSession` → video URL above |
| **Plenum sitting → video URL** | 🟡 Date-matched scrape | `knesset.tv` (Knesset Channel) Bynet HLS; no API, match by date |
| **Member → plenum videos they spoke in** | 🟡 Coarse | member → plenum sessions (protocol/agenda) → date → knesset.tv |
| **Per-speaker jump-to-timestamp ("MK X spoke at 42:10")** | ❌ Not publicly available | No public API; player's speaker list is behind bot-protection |

**Bottom line for the UI:** "videos by member" can show *videos of the sessions a member belongs to / participated in (whole video)*. It **cannot** jump to the member's segment — there is no public per-speaker timecode index.

---

## 1. Committee videos — the clean, API-driven path (recommended primary)

### 1.1 The id mapping (confirmed)

The official streaming server names each committee recording by the **committee session `Id`** — the same `Id` you already get from `KNS_CommitteeSession` in OData.

- `KNS_CommitteeSession.Id` values are 7-digit, currently in the `2,24x,xxx` range (live examples today: `2247410`, `2247329`, `2247193`, `2247316`, `2247314`).
- Search-engine-indexed video files on the server, same range: `CmtSession_2212246`, `2214601`, `2220560`, `2220876`, `2223254`, `2224253`, `2229432`.
- `KNS_BroadcastCommitteSession.Id` shares the same key space (rows `2247003`–`2247410`), confirming `Id` is the join key. **Caveat:** its `BroadcastId` and `BroadcastUrl` columns are **always `null`** in practice — the API does *not* hand you the URL; you construct it. (`KNS_CommitteeSession.BroadcastUrl` is also always null; `?$filter=BroadcastUrl ne null` returns 0 rows.)

### 1.2 Video URL templates (Wowza server, `video.knesset.gov.il`)

```
# MPEG-DASH
https://video.knesset.gov.il/KnsVod/_definst_/mp4:archive/CMT/CmtSession_{SESSION_ID}.mp4/manifest.mpd

# HLS (recommended for Android/ExoPlayer; standard Wowza output alongside DASH)
https://video.knesset.gov.il/KnsVod/_definst_/mp4:archive/CMT/CmtSession_{SESSION_ID}.mp4/playlist.m3u8
```
`{SESSION_ID}` = `KNS_CommitteeSession.Id`.
A cache-busted DASH variant is also seen in the wild (`manifest_w310875234.mpd`); the base `manifest.mpd` / `playlist.m3u8` is what to request.

**Unverified:** I could not HTTP-probe these manifests — the host's robots.txt refused the automated fetcher (see §4). Their existence/format is confirmed only from search-engine indexing of the `.mpd` URLs. The HLS (`playlist.m3u8`) variant is inferred from standard Wowza behavior, not directly observed. **The Mac build thread should curl one to confirm** (e.g. `CmtSession_2220560.mp4/playlist.m3u8`) and check which format + resolutions come back.

### 1.3 "Does this session have a video?"

There is **no reliable API flag.** `KNS_BroadcastCommitteSession` rows exist even for *future* sessions (e.g. `2247410` is 2026-12-08), so row existence ≠ recording exists. Recommended app behavior:
- Build the URL optimistically for past committee sessions, attempt to load, and treat 404/invalid-manifest as "no video." Cache the negative result.
- Optionally do a lightweight HEAD/GET of the manifest before showing a Play button.

### 1.4 Member → committee videos (API join, no scraping)

1. `KNS_PersonToPosition?$filter=PersonID eq {MK_ID} and KnessetNum eq {N} and CommitteeID ne null` → the committees the member sits on (`CommitteeID`, `IsCurrent`).
2. For each `CommitteeID`: `KNS_CommitteeSession?$filter=CommitteeID eq {CID}&$orderby=StartDate desc&$select=Id,StartDate,CommitteeID` → sessions.
3. For each session `Id`: build the video URL (§1.2).

This yields "videos of committees this member belongs to." It is **membership-based, not speaking-based** — the API has no per-session MK attendance/speaker entity. True "sessions where this member spoke" needs protocol parsing (`KNS_DocumentCommitteeSession` → the .doc/.docx protocol lists speakers; see §3), which gives the speaker list but **no video timecodes**.

---

## 2. Plenum videos — date-matched scrape of the Knesset Channel (knesset.tv)

### 2.1 The data API has nothing for plenum video

`KNS_PlenumSession` fields (complete): `Id, Number, KnessetNum, Name, StartDate, FinishDate, IsSpecialMeeting, LastUpdatedDate`. Nav: `KNS_DocumentPlenumSession, KNS_PlenumVote, KNS_PlmSessionItem`. **No broadcast/video field, and there is no plenum broadcast entity** anywhere in the 56-entity-set service document. The official `video.knesset.gov.il` server shows only an `archive/CMT/` (committee) folder in all indexing — no plenum folder was found.

### 2.2 Plenum video lives on knesset.tv (Bynet CDN HLS)

The Knesset Channel ("ערוץ כנסת", ch. 99) site hosts plenum VOD:

- **Listing:** `https://www.knesset.tv/parliament/` (+ `?page=N` "load more"). Each item → `https://www.knesset.tv/parliament/programs/{PROGRAM_ID}/` (numeric `PROGRAM_ID`, no slug). Listing text carries the start date/time `DD.MM.YY HH:MM`.
- **Program page** carries the stream, as the `src` of the player link:
  ```
  https://api.bynetcdn.com/Redirector/knesset/manifest/{STREAM_ID}/HLS/playlist.m3u8
  ```
  Example: program `95901` → `KNS030943-03`; program `95902` → `KNS030943-04`.
- **Sittings are split into parts** (חלק א/ב/ג/ד). One sitting day = several consecutive programs/streams sharing a base (`KNS030943-01..-04`). To show "the plenum of 18.11.26" you collect all that day's parts.
- Player wrapper (for reference only; don't scrape it — robots-disallowed, §4): `https://watch.knesset.tv?src={HLS_URL}&live=false&poster=...&autoplay=true`.

### 2.3 Mapping `KNS_PlenumSession` → knesset.tv program (by date)

There is **no shared id.** The knesset.tv `PROGRAM_ID` (e.g. `95901`) is unrelated to `KNS_PlenumSession.Id` (e.g. `9626`). The only join is **date**:
1. From OData: `KNS_PlenumSession.StartDate` (date of the sitting).
2. Scrape `https://www.knesset.tv/parliament/` listing, which shows each program's `DD.MM.YY HH:MM`.
3. Match by date → collect that day's parts → each program page → extract the Bynet HLS URL.

This is a **fuzzy, scrape-dependent mapping** — brittle if knesset.tv changes markup. There is no clean JSON API (knesset.tv `/wp-json/` is blocked). Recommend caching and a graceful "video may be unavailable" fallback.

### 2.4 Committee videos on knesset.tv (secondary alternative to §1)

knesset.tv also carries committee VOD: `https://www.knesset.tv/committees/{slug}/video/{id}/` (listing at `/committees/{slug}/vod/`, shows date+time). Stream is Bynet HLS, filename encodes committee+date+time, e.g. `V_Ksafim-171126-1032-part1_fixed_...`. **No OData session id on the page** — only committee name + date/time + one MK tag (chair/presenter). Prefer §1 (official server, API-keyed) for committees; this is a fallback if a given `CmtSession_*` file is missing.

---

## 3. Per-speaker index / timestamps — NOT available publicly

The requested "player has a speakers list" feature does **not** have a reachable public backing:
- **knesset.tv** committee and plenum pages show **no** speakers list (`דוברים`), chapters, or per-segment timestamps — only one MK tag (the chair/presenter), title, and a published date.
- **The official on-site viewer** (`main.knesset.gov.il/.../CommitteeTVarchive.aspx`) is a **JavaScript shell behind Reblaze bot-protection** (`kramericaindustries.ac_v2.lib.js`, `window.rbzns`, `winsocks()`). The broadcast list and any speaker/timecode panel render client-side only after the anti-bot challenge passes; a plain HTTP client gets an empty `<body>`. Same shell on `m.knesset.gov.il/Apps/*` (the mobile-app backend). Its speaker-timecode data endpoint, if any, was **not discoverable** by static fetch.
- **Knesset Corpus** (arXiv 2405.18115) = text protocols only; speakers matched to MKs by name, turns numbered, but **no video and no per-turn timestamps**.

**Options for a future "by member within a video" feature (all require extra work, flag to hen):**
- (a) Capture the on-site viewer's XHR in a real browser/WebView to find the speaker-timecode JSON — fragile, anti-bot, possibly against ToS.
- (b) Parse committee/plenum **protocols** (`KNS_DocumentCommitteeSession` / `KNS_DocumentPlenumSession` → .doc/.docx, `FilePath` on `fs.knesset.gov.il`, normalize `\`→`/`) for the speaker list — gives *who spoke* but **not** where in the video.
- (c) Treat it as out of scope for v1.

---

## 4. robots.txt, terms, rate limits, geo-block

- **`video.knesset.gov.il`** — robots.txt **refused the automated fetcher** (bot disallowed). This governs *crawlers*; a media player streaming a manifest is ordinary playback, not crawling. Still: don't crawl/enumerate this host; only request the specific manifest for a session the user opened.
- **`knesset.gov.il`** (OData + main site) — robots.txt unreadable (main site is behind the Reblaze anti-bot). **The OData API itself is not behind the JS shell** and returns JSON directly: service document at `https://knesset.gov.il/OdataV4/ParliamentInfo/` lists **56 entity sets** (HTTP 200). 
- **`www.knesset.tv`** — `robots.txt`: `Allow: /`; disallows only `*videoUrl=*`, `*/KnessetPlayer/*`, `/committees?hdyQ`. **So scraping the `/parliament/programs/{id}/` and `/committees/{slug}/video/{id}/` pages is permitted**; just don't fetch the `watch.knesset.tv`/player query-param URLs. No `Crawl-delay` specified — still be polite (cache, low rate).
- **Geo-block / WAF (important):** the OData API intermittently returns a 4-language HTML block page — *"The site is temporarily inaccessible from your location"* — for some query shapes while nearby queries succeed (observed: `KNS_PlenumSession?$orderby=StartDate desc&$top=3` blocked, but `$top=1` and the service doc OK, seconds apart). This is a WAF/geo throttle seen from outside Israel / under automated load. **On hen's phone in Israel this is generally fine**; the app should still detect the HTML block page (Content-Type `text/html` instead of `application/json`), back off, and retry. Keep request rate modest; the server page cap is 100 rows and nextLink should not be followed (per existing plan).

---

## 5. Recommended implementation into `VideoLinkSource`

Define a `ScrapedVideoLinkSource` (or extend the interface) with two resolvers:

1. **`committeeSessionVideo(sessionId: Long): VideoRef?`** — pure construction, no network until play:
   `https://video.knesset.gov.il/KnsVod/_definst_/mp4:archive/CMT/CmtSession_$sessionId.mp4/playlist.m3u8`. Validate on demand (HEAD/GET manifest); cache presence.
2. **`plenumSessionVideos(startDate: LocalDate): List<VideoRef>`** — scrape `knesset.tv/parliament/` listing, date-match, collect each part's program page, extract the `api.bynetcdn.com/.../HLS/playlist.m3u8`. Cache aggressively; degrade gracefully.

`VideoRef` = `{ hlsUrl, title, dateTime, posterUrl?, source }`. Use HLS for ExoPlayer. Member screen: list committee videos (via §1.4 API join) + plenum videos (date-matched) the member relates to. **Do not promise per-speaker seeking** — there is no public timecode index (§3).

### Open items for the build thread to verify on the Mac (can reach knesset.gov.il)
1. `curl` a committee manifest to confirm HLS works and see renditions: `…/CmtSession_2220560.mp4/playlist.m3u8` and `…/manifest.mpd`.
2. Confirm older/other-Knesset committee sessions are present (how far back the `archive/CMT/` goes).
3. Confirm whether any plenum recordings exist on `video.knesset.gov.il` under a non-`CMT` folder (none found remotely).
4. Sanity-check the geo-block does not trip for normal in-app query volumes.
