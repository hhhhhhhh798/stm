# Stream Master – design rules

Why this file exists: generic ("AI-looking") UI comes from averages – the same radius, padding, borders
and font sizes on everything, boxes inside boxes, an outline around every element, and no decisions about
what matters most. These rules force decisions. `tools/design_lint.py` checks the mechanical ones.

## Hierarchy first
* One primary thing per screen (poster / title / the action). Everything else steps back with colour and size, not with boxes.
* Information lists (director, duration, quality, language, tagline, budget, box office) are plain
  **label / value rows**: muted label (112dp column) + primary value. No panel, no border, no icon per row.

## Surfaces
* Background `netflix_background`. Panels, inputs, tags, chips = **tonal fill** (`panel_surface`, `chip_surface`), never an outline.
* Sheets / dialogs = lifted tonal surface (`sheet_surface`), 16dp corners, no outline.
* The only outline in the app is the **poster frame** (3dp `card_border`) and dividers/tab underline. Selection is shown by fill or colour.

## Type scale (sp): 12 · 14 · 16 · 20 · 24 · 28
12 captions/labels · 14 body · 16 row titles/emphasis · 20 section titles · 24 page titles · 28 display.
Dark theme: text is `text_primary` (#FFF8F2, not pure white) → `text_secondary` → `text_muted`; no hard-coded text colours.

## Colour
* Accent (`comic_peach`) is for the one thing to act on or the current selection – not for decoration.
* No cold greys (blue-tinted) in a warm palette.

## Shape & rhythm (intentional variation)
* Poster cards 12dp · tags 8dp · filter chips pill · sheets 16dp. Spacing on a 4/8dp grid; 28dp between sections, 12dp title→content.
* Text is start-aligned. Centre only short empty-state blocks.

## Icons & copy
* Vector icons only (Material). No glyphs (⌕ ★ ▼ ›) or emoji as icons or in copy.
* No filler copy that repeats the page title (the old "all your favourites here" banner was removed).

## Series
* Series cards = the Home poster card (same frame, scrim, star + rating). Series page = rating, year, description and episodes only
  (no cast, no "more info").
* Episode row: tonal surface, 16:9 thumbnail with the single accent play circle, code · duration · date in muted text, 3-line summary.
* Empty / error states are part of the page, never a crash: "no series right now" (no data, 404, unknown response shape) vs
  "could not load" + Retry (network / server error).

## Not migrated yet (unchanged on purpose)
Home (hero, shelves, bottom bar), player controls, navigation drawer, legacy activities that are never started.
