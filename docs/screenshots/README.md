# Screenshots before the redesign

Captured 2026-11-19 on an Android emulator (Pixel, 1080x2424, Android 16), live API, from commit 606c3e9. Hebrew UI and light theme unless the name says `dark` or `en`. Knesset 26 is selected unless the name says `k25`. No PNG optimizer is installed on this Mac, so the files are as captured (14 MB). The screen recordings were skipped to save time.

## Files

| File | Screen | How to reach it |
|---|---|---|
| 01-home, 02-home-scrolled | Home dashboard | Launch |
| 03-bills-list | Bills list | חקיקה |
| 04-bills-filter-status-open | Status filter menu open | חקיקה > סטטוס |
| 05-bill-detail-top, 06-bill-detail-scrolled | Bill detail | Tap a bill |
| 07-laws-list-k25, 08-law-detail | Laws, Knesset 25 | חקיקה > חוקים |
| 09-secondary-list, 10-secondary-detail | Secondary legislation, Knesset 25 | חקיקה > חקיקת משנה |
| 11-votes-list | Votes | מליאה |
| 12-vote-detail-top, 13-vote-members-by-result, 14-vote-members-by-faction | Vote detail and members grouped two ways | Tap a vote |
| 15-sittings-list, 16-sitting-detail-agenda, 17-sitting-detail-videos | Sittings, agenda, Knesset Channel videos | מליאה > ישיבות > 18/11/2026 |
| 18-video-player-portrait | Video player | Tap a video part |
| 19-motions-list, 20-motion-detail | Motions, Knesset 25 | מליאה > הצעות לסדר |
| 21-questions-list, 22-question-detail | Questions, Knesset 25 | מליאה > שאילתות |
| 23-committees-list, 24/25-committee-detail | Committees | ועדות |
| 26-session-calendar, 27-committee-session-detail | Session calendar and a session | ועדות > יומן ישיבות |
| 28-members-by-faction, 29-members-a-to-z | Members | אנשים |
| 30 to 36-member-* | Member detail tabs: overview, roles, bills, votes, videos, questions, motions | Tap נפתלי בנט |
| 37-member-videos-committees, 38-member-videos-plenum | Member videos | Member > סרטונים > ישיבות ועדה מצולמות |
| 39-factions-list, 40-faction-detail | Factions | אנשים > סיעות |
| 41-ministries-list-placeholders, 41b-ministries-active, 42-ministry-detail | Ministries | אנשים > משרדים |
| 43-people-videos-section | Videos section, pick a member | אנשים > סרטונים |
| 44-search-empty, 45/46-search-results | Global search ("26") | Search icon |
| 47-home-with-favorites-strip, 48-overflow-menu, 49-favorites | Favorites | Star an item; ⋮ > מועדפים |
| 50-explorer-sets, 51-explorer-entity-list, 52-explorer-filter-builder, 53/54-explorer-entity-detail | Data Explorer | ⋮ > סייר הנתונים |
| 55-settings, 56-about | Settings, About | ⋮ |
| 62-empty-state-laws-k26, 63-empty-state-motions-k26 | Empty states | Knesset 26 has no laws or motions yet |
| 64-offline-banner-bills | Offline banner over cached data | Airplane mode, pull to refresh |
| 65-error-offline-state | Error state, nothing cached | Airplane mode, open an uncached set |
| 70-knesset-selector-open | Knesset selector | Top bar chip |
| 80 to 87-dark-* | Dark theme: home, bills, bill, vote, calendar, members, member, sitting videos | Settings > כהה |
| 90 to 93-en-* | English UI with Hebrew data: home, bills, bill, members | Settings > English |

## Problems noticed while capturing

Layout and content
- Four section tabs do not fit at phone width: "הצעות לס…" is cut in Plenum (19, 21). The member detail tab row scrolls and cuts the first and last tabs (30 to 36).
- The ministries list starts with many "אין נתונים" placeholder rows (41); placeholder ministries should be filtered out like the placeholder faction.
- A ministry detail can be empty apart from its title (42).
- The search field does not show the typed text after a search runs (45); only the clear button is visible.
- Small labels built in the data layer stay Hebrew in the English UI: "כנסת 26", "פרטית", "חוק יסוד" (91, 92).
- Committee member rows show a bare range "–11/11/2026" with no "from" label (24, 25).
- Every surface is the same grey card on a white page; sections, rows and chips have little hierarchy, and status chips are the only colour.
- Long agenda lists push the videos section far down a sitting page (16, 17); the agenda collapses to 10 rows but the "show all" button is easy to miss.

RTL and mixed text
- About (56): the URL inside the Hebrew sentence has its trailing "/" moved to the front ("/https://knesset.gov.il/…"). URLs, emails and numbers concatenated into Hebrew need bidi isolation.
- Date ranges "start–end" with an open end render as "–10/11/2026"; the dash sits at the visual left, which reads as a range ending on that date.
- The question title "(PrEP) ל-HIV" (22) keeps the Latin parts in the right order, but the parenthesis placement depends on the first strong character of each line.

Performance and states
- Home, member detail and member videos show only a spinner for several seconds on first load; there are no skeleton rows.
- The first scroll on a sitting page can stall while sections are still arriving.
- Transitions are the navigation library defaults (fade); there is no directional slide matching RTL.

# After the redesign (`redesign/`)

Captured from the redesign build (R1 to R5) on the same emulator, live API. File names match the "before" set where the screen exists in both, so the two can be compared side by side. New names: `04-bills-filter-sheet-open` (filters moved to a bottom sheet), `57-downloads`, `95` to `97` at font scale 1.3.

## RTL pass: what was checked

- Alignment: every row, card and tile uses start and end; Hebrew data uses content direction, so it stays right-aligned inside the English UI too (91, 92, 93).
- Mirrored as they should be: the back arrow, link arrows on fact tiles, the bill stage tracker (preliminary on the right), the Home carousel (starts on the right), pill tabs and the pager swipe, the hemicycle seat order, header gradients (start to end), filter chip rows (scroll from the right), the bottom nav order (Today on the right).
- Not mirrored, on purpose: play, rewind and fast-forward icons, the video seek bar and time (the player is forced left to right), the "for" segment of vote bars stays at the start side.
- Long titles ellipsize at the end side (the left in Hebrew) in collapsed headers (06, 13, 17).
- Navigation: a forward push slides in from the left in Hebrew and back reverses it; predictive back is enabled.
- Unit tests: `RtlAndContrastTest` covers bidi isolation, worded date ranges, hemicycle mirroring and contrast of every text and header color pair in both themes.

## RTL and visual problems found, and what changed

| Problem | Before | After | Fix |
|---|---|---|---|
| URL in the About text lost its trailing "/" to the front of the line | 56-about | redesign/56-about | URL wrapped in a left-to-right isolate (`Bidi.ltr`) |
| Open date ranges rendered as "–11/11/2026" | 24/25-committee-detail | redesign/24, 30 | Written as "מאז 11/11/2026" / "עד 01/07/2027", dates isolated |
| File size rendered as "MB 424" inside Hebrew | (new screen) | redesign/57-downloads | Sizes isolated left to right |
| Explorer filter chips mixed a Latin field name with Hebrew values | 51 | redesign/51 | Field and operator isolated left to right, the value isolated by content |
| Section tabs truncated ("הצעות לס…") | 19, 21 | redesign/11, 15 | Scrollable pill tabs, nothing truncated |
| Typed search text invisible | 45-search-results | redesign/45 | Search moved to a pill under the header |
| Ministries list full of "אין נתונים" rows | 41-ministries-list-placeholders | redesign/41 | Placeholder rows filtered on the server |
| White titles on the light ends of gradients below 3:1 | n/a | all headers | A measured dark veil on headers; contrast tested |
| Hemicycle looked scattered for small votes | (new) | redesign/12 | Rows scale with the seat count |

## Still open

- Ministries now start with many historical "בלי תיק" (minister without portfolio) rows, one per government. Grouping by name would need a design decision.
- No shared-element morph from list card to detail header; the directional slide is used instead (the fallback the spec names).
- No week strip on the session calendar yet; days are sticky headers in the list.
- The Home "now" card is a regular card, not a live gradient card.
- At font scale 1.3 some chips and pills truncate with an ellipsis (95); nothing is clipped.
- Reduced motion: Compose scales all animations by the system animator duration scale, so with animations turned off they become instant; not separately verified on a device.
