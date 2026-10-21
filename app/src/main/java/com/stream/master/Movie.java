package com.stream.master;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

public class Movie implements Serializable {
    @SerializedName("title")
    private String title;
    @SerializedName("image")
    private String image;
    @SerializedName("vid")
    private String vid;
    @SerializedName("description")
    private String description;
    @SerializedName("isFavorite")
    private boolean isFavorite = false;

    @SerializedName("rating")
    private String rating;
    @SerializedName("year")
    private String year;
    @SerializedName("quality")
    private String quality;
    @SerializedName("genre")
    private String genre;
    @SerializedName("director")
    private String director;
    @SerializedName("duration")
    private String duration;
    @SerializedName("language")
    private String language;
    @SerializedName("watchProgress")
    private float watchProgress;

    // UI-only flag used by the Home screen while remote movie data is loading.
    // transient keeps it out of Gson/API serialization.
    private transient boolean skeletonPlaceholder = false;

    public Movie() {
    }

    public Movie(String title, String image, String vid, String description) {
        this.title = title;
        this.image = image;
        this.vid = vid;
        this.description = description;
    }

    public String getTitle() { return title; }
    public String getImage() { return image; }
    public String getVid() { return vid; }
    public String getDescription() { return description; }
    public boolean isFavorite() { return isFavorite; }

    public void setTitle(String title) { this.title = title; }
    public void setImage(String image) { this.image = image; }
    public void setVid(String vid) { this.vid = vid; }
    public void setDescription(String description) { this.description = description; }
    public void setFavorite(boolean favorite) { isFavorite = favorite; }

    public String getRating() { return rating; }
    public String getYear() { return year; }
    public String getQuality() { return quality; }
    public String getGenre() { return genre; }
    public String getDirector() { return director; }
    public String getDuration() { return duration; }
    public String getLanguage() { return language; }

    public void setRating(String rating) { this.rating = rating; }
    public void setYear(String year) { this.year = year; }
    public void setQuality(String quality) { this.quality = quality; }
    public void setGenre(String genre) { this.genre = genre; }
    public void setDirector(String director) { this.director = director; }
    public void setDuration(String duration) { this.duration = duration; }
    public void setLanguage(String language) { this.language = language; }

    public static String formatRating(String value) {
        if (value == null) return "";
        String cleaned = value.trim().replace(',', '.');
        if (cleaned.isEmpty()) return "";
        try {
            // Accept values such as 8.543, "8.5/10", or "IMDb 8.543".
            java.util.regex.Matcher matcher =
                    java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)?)").matcher(cleaned);
            if (!matcher.find()) return "";
            double number = Double.parseDouble(matcher.group(1));
            if (number < 0) number = 0;
            if (number > 10) number = 10;
            return String.format(java.util.Locale.US, "%.1f", number);
        } catch (Exception ignored) {
            return "";
        }
    }

    public String getDisplayRating() { return formatRating(rating); }
    public float getWatchProgress() { return watchProgress; }
    public void setWatchProgress(float progress) { watchProgress = Math.max(0f, Math.min(1f, progress)); }

    public boolean isSkeletonPlaceholder() { return skeletonPlaceholder; }
    public void setSkeletonPlaceholder(boolean placeholder) { skeletonPlaceholder = placeholder; }
}