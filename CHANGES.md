# Stream Master – design unification (changelog)

## Tokens (values/colors.xml, values/dimens.xml)
| Token | Value | Why |
|---|---|---|
| card_border (color) | @color/comic_peach_dim = #C89A79 | existing project colour, closest to reference frame tone |
| card_border_width | 3dp | reference frame ≈ 8px on 1080px screen / 2.75 density ≈ 2.9dp → 3dp |
| card_overlay_inset | 8dp | border 3dp + gap 5dp (badges/bookmark never touch the frame) |
| movie_card_radius | 12dp (existing) | outer radius of frame = card clip radius; inner radius = 12-3 = 9dp |
| line_default | #5C493D | most-used existing hairline colour, now one token |
| radius_panel / radius_tag / chip_height | 12dp / 8dp / 36dp | one radius + height per element class |

## Not touched
PlayerActivity, activity_player.xml, HomeFragment, fragment_home.xml, item_shelf.xml, ShelfAdapter,
bg_home_watch.xml, legacy Activities (CategoryActivity / SearchActivity / FavoritesActivity, not started anywhere).

## Home (only what was requested)
* item_hero.xml: black ▶ (ic_play, #17110E) added inside "Watch now" (button became a LinearLayout, same size 124x44dp, same background/text style). HeroAdapter: field type Button -> View (only View APIs were used).
* item_home_movie.xml / item_continue.xml: frame 1dp -> 3dp comic_peach_dim; opaque badges (no alpha).

## Pages
* Movies: filter chips (Most watched / Top rated / Latest / Suggested), filter button (category dialog), 2-column cards with title + year.
* Favorites: same 2-column card, panel/empty-state in the shared style.
* Movie details: Details / More info tabs (replaces the "see more" toggle), unified tags/panels, cast photo frame.
* Search: back button, trending-searches chips, category cards (real poster per category), results in the same 2-column card.

---
# Update 2

## 1) One rating star
* `res/drawable/ic_rating_star.xml` = the ONLY star (Material "star" path), colour token `rating_star` (#FFD84A) in colors.xml.
* `RatingStar.java` (new): helper for Java code (`bind`, `bindOrFallback`, `icon`). Size/colour are the only parameters.
* Replaced text "★": `DetailsActivity` (2 places, top chip), `ReviewAdapter` (review rating).
* Removed the peach `drawableTint` on the star in `item_home_movie`, `item_movie`, `item_grid_movie` (all stars now identical). `item_hero` already used the drawable.
* Removed unused old colour `movie_rating_star`; the old two-tone hand-drawn star path is gone.

## 2) Info row
* Genre chip (`tvGenreChip`) removed from `activity_details.xml` + its 3 usages in `DetailsActivity`. Genres remain in More Info.
* Genre/production chips: app font (already) + `TEXT_DIRECTION_FIRST_STRONG` / `TEXT_ALIGNMENT_VIEW_START`.
* Row is now a `ChipGroup` (wraps on small screens).

## 3) Trailer button
* `btnTrailer` (bg_tag, 36dp, YouTube icon `ic_youtube` tinted with `@color/comic_peach`, text `@string/trailer` in en/ar/es).
* States: hidden -> loading (spinner, disabled) -> ready (enabled) / no trailer or error (hidden).

## 4) api/trailer
* `ApiService.getTrailer(vid, lang)` -> `GET api/trailer?vid=..&lang=..` (same params as `api/detail`).
* `TrailerResponse` (new): nullable `url` or `key` (+ alternates) -> `resolveUrl()` builds `https://www.youtube.com/watch?v=KEY`; only http(s) URLs / valid keys accepted.
* `DetailsActivity.fetchTrailer()`: same OkHttp client / BASE_URL / Retrofit as `fetchMovieDetails()`, independent + lazy, in-memory cache per movie id.
* TODO(server) comments in `ApiService` and `TrailerResponse` and `DetailsActivity`.

## 5) Tap
* `openTrailer()`: YouTube app (`com.google.android.youtube`) -> browser fallback -> custom toast `trailer_open_failed`. `<queries>` entry added to the manifest.

## Extra requests
* Search: page padding moved from the whole page into individual blocks; category strip + results now reach the screen edge and scroll under it (no clipping).
* Home: `logoText` fades with the hero collapse (`HomeFragment`, AppBarLayout offset listener, fully gone after 35% of the collapse). Search button untouched.

---
# Update 3
* Movies/Favorites/Search grids: 2 -> 3 columns via ONE constant `res/values/integers.xml` (`page_grid_span`); compact text (title 12sp / year 10sp); overlay badges identical to Home cards.
* Bookmark: circular background removed from `item_grid_movie` and `item_home_movie` (same as "You may also like"); `bg_bookmark_solid.xml` deleted (unused).
* Details info panel (Director / Duration / Quality / Language): rebuilt as a 2x2 grid with hairline dividers, start-aligned, 20dp page margin, ids unchanged.
* Director: `DetailResponse` now reads `director` / `directors` / `crew[job=Director]` / `credits.crew` from api/detail (list APIs don't carry it); `DetailsActivity` fills `tvDirector` when empty and logs `DETAIL_API: director in detail response: ...`. TODO(server): confirm real field.

---
# Update 4
* Cards (Home / related / grids): one scrim `bg_card_scrim` (soft top + bottom shade inside the card); rating = star + number only (no background, 12sp bold, soft text shadow); `bg_badge_solid` deleted.
* Movies/Favorites/Search grid card = exact clone of the Home card (`item_grid_movie`), width fills the cell, 2:3 ratio (=100x150dp on a 360dp phone), no title/year strip.
* Details: info panel with icons (person / schedule / hd / language); More Info rebuilt in the reference order (facts panel, Genres, Production Companies, Production Countries, Spoken Languages [hidden if empty], Reviews); bigger chips; review card (avatar, name, star+rating, clock+date, text); director equal to the title is discarded (`cleanDirector`).
* Favorites sort dialog: single-choice list with radio indicators, 56dp rows, one surface (`bg_dialog`), current sort remembered.
* Player options sheet (`showPremiumOptionDialog`): icon + label + value + chevron rows for the settings menu, radio rows for choices, 48dp close button with a correctly centred Material "close" icon (the old icon path was off-centre). Old drawables deleted.

---
# Update 5
* Details: Director / Duration / Quality / Language = plain label-value rows stacked under each other (no box, no icons); Tagline / Budget / Box office use the same rows.
* Language = language spoken in the film: `DetailResponse.getSpokenLanguageLabel()` (spoken_languages -> original_language, shown in the UI language). The list APIs return the app language, so that value is no longer displayed. Shows "—" while loading and "not available" if the server sends nothing. TODO(server): confirm field names.
* Human-made pass (see DESIGN.md, checked by tools/design_lint.py):
  - outlines removed from panels/tags/chips/inputs/dialogs -> tonal surfaces (`panel_surface` #171412, `chip_surface` #1A1613, `sheet_surface` #1C1815); only the poster frame keeps its border;
  - type scale 12/14/16/20/24/28 on Details, Movies, Favorites, Search, cast/review/category items, Downloads, Settings;
  - hard-coded text colours -> tokens in 13 layouts (pure white -> #FFF8F2, cold greys -> warm tokens);
  - glyph icon "⌕" -> vector icon; emoji removed from update title; unused "See More ▼/▲" strings removed;
  - cast names in text colour, start-aligned, no photo frame; Favorites filler banner removed.

---
# Update 6 – Series
* New tab "Series" (5th bottom-nav item, `nav_series`, icon `ic_series`) -> `SeriesFragment` (paged grid, Home-style cards, empty / error / retry states, auto-reload when the tab is shown again while empty).
* `SeriesDetailsActivity`: poster, title, rating + year, description (read more), season chips (only if >1 season), episode list. No cast / more-info.
* `item_episode`: tonal row, 16:9 thumbnail + accent play circle, `S01·E01 · 25m · date`, 3-line summary.
* Data: `Series`, `SeriesResponse`, `SeriesDetailResponse` (Season / Episode), `SeriesApi`, `ApiService.getSeriesList` / `getSeriesDetails` (both `GET api/series`). All fields optional, alternates accepted, robust to missing / odd data.
* Player (minimal): extra `TYPE = "tv"` -> straight to `api/stream?vid=<episode vid>` (movie JS providers skipped, not saved to movie "continue watching"). Everything else in the player is untouched.
* TODO(server) assumptions are written in `ApiService`, `SeriesResponse`, `SeriesDetailResponse`.
* Logic of the series models is covered by a JVM test (season grouping / ordering, wrapper, empty & hostile input) – it passed; Gson parsing and all UI are untested.

---
# Update 7 – server connection (server: index.js + server-changes.patch)
Server (Express + sql.js, tables `tv_shows` / `episodes`):
* `GET /api/series` (list: lang/page/limit/cat/query, only working shows that have episodes) and `GET /api/series?vid=` or `/api/series/:id` (series + seasons + episodes; season 0 and rows without valid numbers are left out; the internal `filecode` is never exposed).
* `GET /api/trailer?vid=[&type=movie|tv]` -> YouTube Data API, key from the environment variable **YOUTUBE_API_KEY** (optional: without it 503 and the app hides the button; the key is never logged or returned). Cached 7 days (misses 6 h), concurrent lookups share one request, a quota error pauses lookups 30 min.
* `GET /api/stream?vid=<series tmdb_id>&type=tv&s=&e=`: tv branch validates s/e and never resolves the id through `movies` (ids can collide). Movie behaviour unchanged.
* Subtitles: `/api/subtitles` already supports type=tv + season + episode (SubDL) - no server change needed.
* Bug fixed: `getLangField()` falls back to the movie TITLE, so a movie with no director showed its title as "director" (also tagline / genre). Those fields now use `pickLang()` and are empty when the DB has nothing.
App:
* `ApiService.getEpisodeStream`; the player sends (series TMDB id, season, episode) to `api/stream`, uses the series TMDB id for subtitles, keeps a per-episode resume key, and writes no movie "continue watching" entry for episodes.
* Series models read `vid` (series TMDB id) strictly - the server's internal row `id` is ignored.
