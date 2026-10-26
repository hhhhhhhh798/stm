package com.stream.master;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.Locale;

/**
 * One TV series as returned by GET api/series (list) - see formatShow() in the server's index.js.
 * "vid" is the series TMDB id. The server also sends its internal row "id": it is deliberately NOT read.
 * Every field is optional; missing fields never break parsing.
 */
public class Series implements Serializable {

    @SerializedName(value = "vid", alternate = {"tmdb_id"})
    private String vid;

    @SerializedName(value = "title", alternate = {"name", "original_name"})
    private String title;

    @SerializedName(value = "image", alternate = {"poster", "poster_url", "poster_path", "cover"})
    private String image;

    @SerializedName(value = "description", alternate = {"overview", "plot", "summary"})
    private String description;

    @SerializedName(value = "rating", alternate = {"vote_average", "imdb_rating", "score"})
    private String rating;

    @SerializedName(value = "year", alternate = {"first_air_date", "release_date", "date"})
    private String year;

    public String getVid() { return vid == null ? "" : vid.trim(); }
    public String getTitle() { return title == null ? "" : TextSanitizer.clean(title.trim()); }
    public String getImage() { return image == null ? "" : image.trim(); }
    public String getDescription() { return description == null ? "" : TextSanitizer.clean(description.trim()); }
    public String getRating() { return formatRating(rating); }
    public String getYear() { return yearOf(year); }

    /** "7.84" -> "7.8"; empty / zero / not a number -> "". */
    public static String formatRating(String raw) {
        if (raw == null) return "";
        try {
            float value = Float.parseFloat(raw.trim().replace(',', '.'));
            return value > 0f ? String.format(Locale.US, "%.1f", value) : "";
        } catch (Exception e) {
            return "";
        }
    }

    /** "1999-10-20" -> "1999"; "1999" -> "1999"; anything else -> "". */
    public static String yearOf(String raw) {
        if (raw == null) return "";
        String t = raw.trim();
        return (t.length() >= 4 && t.substring(0, 4).matches("\\d{4}")) ? t.substring(0, 4) : "";
    }
}
