package com.stream.master;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * GET api/series?vid={seriesId}&lang=  ->  one series with its seasons / episodes.
 *
 * Shape produced by the server (all fields optional; the {"data": {...}} wrapper is what the server sends):
 * {
 *   "vid": "1399", "title": "...", "image": "...", "description": "...", "rating": "8.4", "year": "2011",
 *   "seasons": [ { "season": 1, "title": "Season 1",
 *                  "episodes": [ { "vid": "<episode id>", "episode": 1, "title": "...", "description": "...",
 *                                  "image": "...", "duration": "25m", "air_date": "1999-10-20" } ] } ]
 * }
 * A flat "episodes": [ { "season": 1, "episode": 1, ... } ] list is accepted too.
 * An episode is NOT streamed by its own id: it is (series vid, season, episode) -> api/stream?vid=&type=tv&s=&e=.
 * The episode "vid" ("<tmdb>_s1e3") is only a stable unique key (used to store the resume position).
 */
public class SeriesDetailResponse {

    @SerializedName("data")
    private SeriesDetailResponse data;

    @SerializedName(value = "vid", alternate = {"tmdb_id"}) private String vid;
    @SerializedName(value = "title", alternate = {"name", "original_name"}) private String title;
    @SerializedName(value = "image", alternate = {"poster", "poster_url", "poster_path", "cover"}) private String image;
    @SerializedName(value = "description", alternate = {"overview", "plot", "summary"}) private String description;
    @SerializedName(value = "rating", alternate = {"vote_average", "imdb_rating", "score"}) private String rating;
    @SerializedName(value = "year", alternate = {"first_air_date", "release_date", "date"}) private String year;
    @SerializedName("seasons") private List<Season> seasons;
    @SerializedName("episodes") private List<Episode> episodes;

    private SeriesDetailResponse body() {
        return (data != null && data.hasContent()) ? data : this;
    }

    private boolean hasContent() {
        return (seasons != null && !seasons.isEmpty()) || (episodes != null && !episodes.isEmpty())
                || title != null || description != null;
    }

    public String getTitle() { SeriesDetailResponse b = body(); return b.title == null ? "" : TextSanitizer.clean(b.title.trim()); }
    public String getImage() { SeriesDetailResponse b = body(); return b.image == null ? "" : b.image.trim(); }
    public String getDescription() { SeriesDetailResponse b = body(); return b.description == null ? "" : TextSanitizer.clean(b.description.trim()); }
    public String getRating() { return Series.formatRating(body().rating); }
    public String getYear() { return Series.yearOf(body().year); }

    /** Seasons in order, each with its episodes in order. Seasons without episodes are dropped. */
    public List<Season> getSeasons() {
        SeriesDetailResponse b = body();
        Map<Integer, Season> bySeason = new LinkedHashMap<>();

        if (b.seasons != null) {
            for (Season s : b.seasons) {
                if (s == null) continue;
                int n = s.getNumber();
                Season target = bySeason.get(n);
                if (target == null) { target = new Season(n, s.getTitle()); bySeason.put(n, target); }
                for (Episode e : s.getEpisodes()) target.add(e, n);
            }
        }
        if (b.episodes != null) {
            for (Episode e : b.episodes) {
                if (e == null) continue;
                int n = e.getSeasonNumber() > 0 ? e.getSeasonNumber() : 1;
                Season target = bySeason.get(n);
                if (target == null) { target = new Season(n, ""); bySeason.put(n, target); }
                target.add(e, n);
            }
        }

        List<Season> out = new ArrayList<>();
        for (Season s : bySeason.values()) {
            if (!s.episodes.isEmpty()) {
                Collections.sort(s.episodes, Comparator.comparingInt(Episode::getEpisodeNumber));
                out.add(s);
            }
        }
        Collections.sort(out, Comparator.comparingInt(Season::getNumber));
        return out;
    }

    // ------------------------------------------------------------------

    public static class Season {
        @SerializedName(value = "season", alternate = {"season_number", "number"}) private String number;
        @SerializedName(value = "title", alternate = {"name"}) private String title;
        @SerializedName("episodes") private List<Episode> episodes = new ArrayList<>();
        private transient int resolvedNumber = -1;

        public Season() {}
        Season(int number, String title) { this.resolvedNumber = number; this.title = title; }

        public int getNumber() {
            if (resolvedNumber >= 0) return resolvedNumber;
            int n = parseInt(number, 1);
            resolvedNumber = n > 0 ? n : 1;
            return resolvedNumber;
        }
        public String getTitle() { return title == null ? "" : title.trim(); }
        public List<Episode> getEpisodes() { return episodes == null ? new ArrayList<>() : episodes; }
        void add(Episode e, int seasonNumber) {
            if (e == null) return;
            if (episodes == null) episodes = new ArrayList<>();
            e.forceSeason(seasonNumber);
            episodes.add(e);
        }
    }

    public static class Episode {
        @SerializedName("vid") private String vid;
        @SerializedName(value = "season", alternate = {"season_number"}) private String season;
        @SerializedName(value = "episode", alternate = {"episode_number", "number"}) private String episode;
        @SerializedName(value = "title", alternate = {"name"}) private String title;
        @SerializedName(value = "description", alternate = {"overview", "plot", "summary"}) private String description;
        @SerializedName(value = "image", alternate = {"still", "still_path", "thumbnail", "poster"}) private String image;
        @SerializedName("duration") private String duration;
        @SerializedName(value = "air_date", alternate = {"date", "released", "release_date"}) private String airDate;
        private transient int forcedSeason = -1;

        void forceSeason(int s) { forcedSeason = s; }

        public String getVid() { return vid == null ? "" : vid.trim(); }
        public int getSeasonNumber() { return forcedSeason > 0 ? forcedSeason : Math.max(0, parseInt(season, 0)); }
        public int getEpisodeNumber() { return Math.max(0, parseInt(episode, 0)); }
        public String getTitle() { return title == null ? "" : TextSanitizer.clean(title.trim()); }
        public String getDescription() { return description == null ? "" : TextSanitizer.clean(description.trim()); }
        public String getImage() { return image == null ? "" : image.trim(); }
        public String getAirDate() { return airDate == null ? "" : airDate.trim(); }

        /** "25" -> "25m"; "25m" / "25 min" stay as the server wrote them; empty -> "". */
        public String getDurationLabel() {
            if (duration == null) return "";
            String d = duration.trim();
            if (d.isEmpty() || d.equals("0")) return "";
            return d.matches("\\d+") ? d + "m" : d;
        }

        /** "S01·E02" (season/episode codes are not translated, like every streaming app). */
        public String getCode() {
            return String.format(Locale.US, "S%02d\u00B7E%02d", getSeasonNumber(), getEpisodeNumber());
        }
    }

    private static int parseInt(String raw, int fallback) {
        if (raw == null) return fallback;
        try { return (int) Float.parseFloat(raw.trim()); } catch (Exception e) { return fallback; }
    }
}
