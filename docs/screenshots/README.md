# Screenshots (work in progress)

Captured 2026-11-19 on an Android emulator (Pixel, 1080x2424), Hebrew UI, light theme, live API, from commit 606c3e9. The capture is partial: People, search, favorites, Data Explorer, settings, About, dark theme, English and the screen recordings are still to do.

| File | Screen | How to reach it |
|---|---|---|
| 01-home.png, 02-home-scrolled.png | Home dashboard | Launch |
| 03-bills-list.png | Bills list | חקיקה |
| 04-bills-filter-status-open.png | Status filter menu open | חקיקה > סטטוס |
| 05/06-bill-detail-*.png | Bill detail, top and scrolled | Tap a bill |
| 07-laws-list-k25.png, 08-law-detail.png | Laws (Knesset 25) | חקיקה > חוקים |
| 09-secondary-list.png, 10-secondary-detail.png | Secondary legislation | חקיקה > חקיקת משנה |
| 11-votes-list.png … 14-vote-members-by-faction.png | Votes and vote detail by result and by faction | מליאה |
| 15-sittings-list.png … 17-sitting-detail-videos.png | Sittings, agenda, Knesset Channel videos | מליאה > ישיבות |
| 18-video-player-portrait.png | Video player | Tap a video part |
| 19–22 | Motions and questions (Knesset 25) | מליאה > הצעות לסדר / שאילתות |
| 23–27 | Committees list, detail, calendar, session detail | ועדות |
| 60-loading-startup.png | Loading state | Cold start |
| 62/63-empty-state-*.png | Empty states (Knesset 26 has no laws or motions yet) | |
| 70-knesset-selector-open.png | Knesset selector | Top bar chip |

## UI problems noticed so far

- Section tab labels are cut off ("הצעות לס…" in Plenum); four tabs do not fit at phone width.
- Small labels built in the data layer stay Hebrew in the English UI ("כנסת 26", "חוק יסוד").
- A sitting's agenda is collapsed to 10 rows, so the videos section is far down the page; the first scroll after load can stall while sections are still arriving.
- Committee member rows show a dangling date range ("–11/11/2026" reads as "from" without a label).
- First load of the home dashboard and of member screens takes several seconds with only a spinner.
