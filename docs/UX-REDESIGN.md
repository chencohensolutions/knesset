# UX redesign: modern, clean, colorful, smooth

Written from the screenshots in `docs/screenshots/` (commit 596a80b). Goal from the owner: the app should feel more modern and clean, more colorful and vibrant, with smooth transitions, and it must be right in Hebrew right-to-left.

Scope: the UI layer only (`ui/theme`, `ui/components`, the nav host, and screen composables). No change to repositories, DTOs, queries or caching. All existing tests keep passing.

## 1. What is wrong today

Seen in the screenshots:

1. Everything is the stock Material look: one muted blue, gray-lilac cards on a lilac-white page. Nothing tells the five sections apart.
2. Cards are large gray blocks, and lists inside them use divider lines and chevrons, so screens read as dense tables.
3. Section tabs truncate ("הצעות לס…") because four fixed tabs do not fit.
4. Filters open as dropdown menus that cover most of the screen (status filter, Knesset selector with 26 rows).
5. Detail screens are label and value tables with underlined blue links.
6. The vote result, the most visual data in the app, is a thin 4dp bar and two small numbers.
7. Loading is a spinner, empty states are a gray icon with one line, and nothing animates between screens.
8. The title is repeated: truncated in the top bar and again in full right below it.

## 2. Design language

Name: **Civic Vivid**. White, airy surfaces. Color comes from the content: each section has its own hue, each item kind has an icon in that hue, and results and statuses use strong semantic colors. Large rounded shapes, generous spacing, one friendly Hebrew typeface.

### 2.1 Color

Define these as tokens in `ui/theme` (a `KnessetColors` class provided through a CompositionLocal, next to the Material `ColorScheme`). No screen may hardcode a color.

**Section hues.** Each has a gradient (start to end, drawn along the reading direction) and a solid accent.

| Section | Gradient | Accent (light) | Accent (dark) | Soft container (light) |
|---|---|---|---|---|
| Home (היום) | `#4F46E5` to `#7C3AED` | `#4F46E5` | `#A5B4FC` | `#EEF2FF` |
| Legislation (חקיקה) | `#2563EB` to `#06B6D4` | `#2563EB` | `#7DD3FC` | `#E0F2FE` |
| Plenum (מליאה) | `#DB2777` to `#F97316` | `#DB2777` | `#F9A8D4` | `#FCE7F3` |
| Committees (ועדות) | `#0D9488` to `#22C55E` | `#0D9488` | `#5EEAD4` | `#CCFBF1` |
| People (אנשים) | `#EA580C` to `#F59E0B` | `#C2410C` | `#FDBA74` | `#FFEDD5` |

In dark theme the soft container is the accent at 16% over the surface.

**Neutrals**

| Token | Light | Dark |
|---|---|---|
| background | `#F6F7FB` | `#0E0F1A` |
| surface (cards, sheets) | `#FFFFFF` | `#171929` |
| surfaceRaised | `#FFFFFF` | `#1F2236` |
| surfaceMuted (fields, skeletons) | `#EEF0F7` | `#262A42` |
| outline | `#E3E6F0` | `#2E3350` |
| textPrimary | `#14162B` | `#ECEDF7` |
| textSecondary | `#5B6078` | `#A3A8C3` |

Material mapping: `primary` is the Home accent, `secondary` the Legislation accent, `tertiary` the Plenum accent, so stock components stay on palette.

**Semantic**

| Meaning | Light | Dark |
|---|---|---|
| for, passed, in force | `#16A34A` | `#4ADE80` |
| against, rejected, repealed | `#E11D48` | `#FB7185` |
| abstained, pending, postponed | `#D97706` | `#FBBF24` |
| present, absent, unknown | `#64748B` | `#94A3B8` |
| in progress | section accent | section accent |

Color never carries meaning alone: every result has a text label and a number.

**Faction colors.** A fixed palette of 12 vivid hues (`#4F46E5 #DB2777 #0D9488 #EA580C #2563EB #7C3AED #16A34A #E11D48 #0891B2 #CA8A04 #9333EA #475569`), assigned by `factionId mod 12` and stable across screens. Used for member avatars, faction dots and the faction view of a vote.

**Contrast.** Body text is at least 4.5:1. White text on a gradient is allowed only at 20sp and above (3:1). Test both themes.

### 2.2 Type

Bundle **Rubik** (variable font, Hebrew and Latin, SIL Open Font License) in `composeResources/font` and use it everywhere.

| Style | Size / line | Weight | Use |
|---|---|---|---|
| display | 30 / 36 | 700 | Section title in the hero header |
| title | 22 / 28 | 700 | Detail screen title |
| headline | 18 / 24 | 600 | Card and section headings |
| body | 16 / 24 | 400 | Row titles, paragraphs |
| label | 14 / 20 | 500 | Chips, buttons, tab labels |
| caption | 12 / 16 | 500 | Meta lines |
| number | 34 / 38 | 700, tabular figures | Vote counts, stats |

Nothing below 12sp. Layouts must hold at font scale 1.3.

### 2.3 Shape, space, depth

- Radius: cards 20dp, inner tiles 14dp, chips and buttons full pill, sheets 28dp on the top corners.
- Spacing scale: 4, 8, 12, 16, 24, 32. Screen side gutter 16dp. Space between cards 12dp.
- Depth: cards are white with a 1dp outline in light theme and a tonal step in dark theme. Shadows only on floating things: the bottom bar, sheets, the player controls.
- No divider lines inside cards. Separate rows with 12dp of space.
- No trailing chevrons on rows. The whole card is the touch target and has a pressed state (scale to 0.98 and a ripple in the section accent).

## 3. Components

All in `ui/components`, so every screen (including the Advanced screen built in another branch) picks them up.

1. **HeroHeader** (root tabs). Section gradient behind the status bar, large display title, the Knesset pill and the search and overflow icons in white. The content sits on a background sheet with 28dp top corners that overlaps the gradient by 24dp. On scroll it collapses to a 56dp bar in the gradient with the title at headline size. Uses a nested scroll connection, not a fixed height spacer.
2. **DetailHeader** (detail screens). Same gradient, by the kind of item. Holds back, favorite, share. The full title appears once, in the header, up to 4 lines, then collapses to one ellipsized line. Below it: the status chip and one meta line.
3. **FloatingNavBar**. A pill-shaped bar floating 12dp above the bottom edge with a soft shadow, five items. The selected item shows an accent-tinted pill behind the icon and its label, and unselected items show the icon only. The pill slides between items with a spring. Lists add bottom content padding so nothing hides behind it.
4. **SectionTabs**. Scrollable pill tabs (no truncation): selected is filled with the section accent and white text, others are text on transparent. The row scrolls so the selected tab is visible. Swiping the pager moves the pill continuously.
5. **SearchPill**. Filled, pill-shaped, 48dp, `surfaceMuted`, leading search icon, trailing clear button when not empty.
6. **FilterBar and FilterSheet**. One "סינון" button with a count badge, followed by removable chips for active filters, then the sort button. Tapping opens a modal bottom sheet with every filter for that list: single choice as a chip group, long lists (statuses, committees) as a searchable checklist, dates as a range picker, with "apply" and "clear all". This replaces all dropdown menus.
7. **ItemCard**. The list row: a 40dp rounded-square icon tile in the section soft container with the kind icon in the accent, the title (body, up to 3 lines), a meta line (caption), and status chip. Variants add a trailing date block or a mini vote bar.
8. **StatusChip**. Pill with a 6dp colored dot and text, tinted by a category derived from the status (in progress, passed, stopped, neutral).
9. **FactTiles**. Replaces label and value tables on detail screens: a two column grid of small tiles (caption label above, body value below). Long values span both columns. Links are rows with a leading icon in the accent, not underlined text.
10. **SectionCard**. White card with a headline, an optional count badge and an optional "הכל" text button. Holds at most 5 rows, then a "show all N" row that expands in place with an animation.
11. **VoteBar and VoteSummary**. A 12dp rounded split bar (for, against, abstained, other) with big tabular numbers and labels under it. In lists, a 6dp version with the two numbers.
12. **Hemicycle**. For vote detail: 120 seats drawn as dots in a half circle on a Canvas (5 rows), colored by result, or by faction when that toggle is on. Seats are ordered by faction so blocks sit together. Tapping a block highlights that group in the member list. Mirrors correctly in RTL (the order follows the reading direction).
13. **Avatar**. Circle with the member's initials on the faction color, 40dp in rows and 72dp in the member header with a 3dp white ring.
14. **Skeletons**. Shimmering placeholders shaped like ItemCard, FactTiles and the home cards, in `surfaceMuted`. Used for every first load. The spinner stays only for paging at the list end.
15. **EmptyState and ErrorState**. A simple two-tone illustration built from vector shapes in the section colors, a headline, one explanatory line, and an action. Example for laws in Knesset 26: "אין עדיין חוקים בכנסת ה-26" with a button "הצגת הכנסת ה-25". Errors: offline, blocked by location, server, each with retry.
16. **KnessetPicker**. A bottom sheet with a grid of number tiles (4 per row, newest first), the current Knesset marked, plus "all". Replaces the 26 row dropdown.
17. **DocumentRow**. A tile with a colored format badge (PDF red, DOC blue), the document meaning, and an open icon.
18. **VideoCard**. 16:9 rounded thumbnail area (poster when available, otherwise the Plenum gradient with a play glyph), title, date and a duration or part label, plus the download state.

## 4. Screens

**Home.** Hero header with today's date in Hebrew under the title. Then:
- A "now" card in the Home gradient: the next plenum sitting or, when one is in progress, a live marker, with time and a button.
- Upcoming committee sessions as a horizontal carousel of compact cards (time large, committee name, first agenda item), snapping per card.
- Latest votes as cards with the 12dp vote bar and both numbers.
- Recently updated bills and new laws as ItemCards.
- Favorites as a row of chips with avatars or kind icons.
Sections appear with a short stagger on first load.

**Lists (all sections).** HeroHeader, SectionTabs, SearchPill, FilterBar, a result count that animates when it changes, then ItemCards with 12dp gaps. The session calendar gets a horizontal week strip (Sunday first) under the tabs with a dot on days that have sessions, and sticky day headers in the list.

**Bill, law, regulation detail.** DetailHeader in the Legislation gradient. A horizontal stage tracker for bills (preliminary, first reading, committee, second and third reading, published), with reached stages filled and the current one pulsing once. Then FactTiles, the summary, initiators as avatar rows, documents, related items.

**Vote detail.** DetailHeader in the Plenum gradient, VoteSummary, Hemicycle, a segmented control (by result, by faction), then members as avatar rows grouped under sticky headers with counts.

**Sitting and session detail.** The videos section moves above the agenda when videos exist. The agenda shows 5 rows then expands in place. Session detail shows time and location as tiles and the site and broadcast links as two filled tonal buttons.

**Member detail.** DetailHeader in the People gradient with the 72dp avatar, name, faction chip and current role. Scrollable pill tabs below. The voting record tab starts with a small summary bar of the member's results.

**Committee detail.** DetailHeader in the Committees gradient. Members as a horizontally scrolling avatar strip with the chair first and labelled. Fix the dangling date range: write "מאז 11/11/2026" or "עד 11/11/2026", or both dates.

**Video player.** Black stage, 16:9, rounded corners in portrait. Controls fade in on tap: play, seek 10 seconds, a seek bar in the Plenum accent, time, fullscreen, download. Below the player: title, date, the other parts as a horizontal strip of VideoCards.

**Search, favorites, Data Explorer, settings, About.** Same components. Search shows recent searches as chips and results grouped in SectionCards. Data Explorer and settings use the Home hue.

## 5. Motion

Smooth means consistent, short and interruptible. Put durations and easings in one `Motion` object.

| Token | Value |
|---|---|
| short | 150ms |
| medium | 300ms |
| long | 500ms |
| emphasized easing | cubic-bezier(0.2, 0, 0, 1) |
| standard spring | damping ratio 0.8, stiffness 380 |

1. **Forward and back navigation.** Shared axis on X: the new screen slides in 30dp and fades in, the old one slides out and fades, 300ms, emphasized. Direction follows the layout: in Hebrew a forward push enters from the left and back reverses it. Support the predictive back gesture so the screen follows the finger.
2. **List to detail.** A shared element transition (`SharedTransitionLayout`): the tapped ItemCard's container and title morph into the detail header. If it drops frames on a mid-range phone, fall back to rule 1 for that pair.
3. **Bottom tabs.** Fade through (outgoing fades out in 90ms, incoming fades in and scales from 0.96 in 210ms). The header gradient cross-fades between section hues. No sideways slide.
4. **Section tabs.** A horizontal pager, with the pill indicator tracking the swipe.
5. **Lists.** `animateItem()` for placement changes. On first load, the first 8 rows fade and rise 12dp with a 30ms stagger. Paged rows appear without stagger.
6. **Loading.** Skeletons cross-fade to content in 200ms. No layout jump: skeletons have the real row heights.
7. **Data.** Vote bars grow from zero in 500ms and numbers count up when they first appear. Hemicycle seats fill in with a 2ms stagger. The result count animates between values.
8. **Sheets, chips, expanders.** Sheets use the standard spring. Chips animate in and out with size and fade. "Show all" expands with `animateContentSize`.
9. **Headers.** Collapse is driven directly by scroll, with no delay.
10. **Feedback.** Cards scale to 0.98 on press. The favorite star pops (scale 1 to 1.3 to 1) and fills with color. Pull to refresh uses the section accent.
11. **Reduced motion.** When the system animation scale is 0, all of the above become instant cross-fades or nothing.
12. **Performance.** Target 60fps on the test phone, with no animated blur and no animation that triggers layout on every frame of a long list. Keep stable keys on every lazy list.

## 6. Right-to-left rules for the new components

- Gradients run from start to end, so they flip in RTL.
- Mirror: back arrow, the stage tracker order, carousels (start from the right), the week strip (Sunday on the right), pager swipe direction, the hemicycle seat order.
- Do not mirror: play and seek icons, the video seek bar and its time, the vote bar order is "for" at the start side in both directions.
- Hebrew titles with numbers, quotes and brackets: wrap app-inserted numbers and Latin fragments in bidi isolates, and ellipsize at the end side.
- Check every component with a long mixed string in both directions at font scale 1.0 and 1.3.

## 7. Implementation order

1. **R1, tokens.** Colors, type with the Rubik font, shapes, spacing, the `Motion` object, section and kind color lookups, faction palette. Light and dark.
2. **R2, components.** Everything in section 3, each with a preview and, where there is logic (hemicycle seat layout, faction color mapping, status category mapping, stage tracker state), a unit test.
3. **R3, navigation and motion.** Nav host transitions, predictive back, shared elements, tab fade through, list and loading animations.
4. **R4, screens.** Apply to Home, lists, the detail screens, vote detail, member, committee, calendar, player, search, favorites, explorer, settings.
5. **R5, check.** Dark theme, Hebrew RTL and English, font scale 1.3, reduced motion, contrast. Retake the screenshots into `docs/screenshots/redesign/` with the same file names so before and after can be compared, and record the two transition clips again.

Commit after each step, and reinstall on the phone after R4 and R5.
