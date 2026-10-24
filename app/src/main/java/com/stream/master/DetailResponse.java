package com.stream.master;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class DetailResponse implements Serializable {

    @SerializedName("status")
    private String status;

    // Server response: { "status": "success", "data": { ... } }
    @SerializedName("data")
    private DetailData data;

    // Fallback if the server ever returns the detail object directly.
    @SerializedName("tagline") private String tagline;
    @SerializedName("budget") private String budget;
    @SerializedName("revenue") private String revenue;
    @SerializedName("box_office") private String boxOffice;
    @SerializedName("cast") private List<Cast> cast;
    @SerializedName("reviews") private List<Review> reviews;
    @SerializedName("genres") private List<JsonElement> genres;
    @SerializedName("production_companies") private List<JsonElement> productionCompanies;
    @SerializedName("production_countries") private List<JsonElement> productionCountries;
    @SerializedName("spoken_languages") private List<JsonElement> spokenLanguages;
    @SerializedName("original_language") private String originalLanguage;

    // Director: the list APIs usually do not carry it, so it is read from api/detail.
    // TODO(server): confirm which of these the backend really sends (string / list / TMDB-style crew).
    @SerializedName(value = "director", alternate = {"directed_by", "director_name"}) private JsonElement director;
    @SerializedName("directors") private JsonElement directors;
    @SerializedName("crew") private List<JsonElement> crew;
    @SerializedName("credits") private JsonElement credits;

    public DetailResponse() {}

    public String getStatus() {
        return status;
    }

    public boolean hasData() {
        return data != null || tagline != null || budget != null || revenue != null
                || boxOffice != null || cast != null || reviews != null
                || genres != null || productionCompanies != null
                || productionCountries != null || director != null || crew != null
                || spokenLanguages != null || originalLanguage != null;
    }

    public String getTagline() {
        return data != null ? data.tagline : tagline;
    }

    public String getBudget() {
        return data != null ? data.budget : budget;
    }

    public String getBoxOffice() {
        if (data != null) {
            if (data.boxOffice != null && !data.boxOffice.isEmpty()) {
                return data.boxOffice;
            }
            return data.revenue;
        }
        return boxOffice != null && !boxOffice.isEmpty() ? boxOffice : revenue;
    }

    /** Director name(s) found in the detail response, or "" when the server sends none. */
    public String getDirector() {
        return data != null
                ? extractDirector(data.director, data.directors, data.crew, data.credits)
                : extractDirector(director, directors, crew, credits);
    }

    public List<Cast> getCast() {
        return data != null ? safeCast(data.cast) : safeCast(cast);
    }

    public List<Review> getReviews() {
        return data != null ? safeReviews(data.reviews) : safeReviews(reviews);
    }

    public List<String> getGenres() {
        return data != null ? toNames(data.genres) : toNames(genres);
    }

    public List<String> getProductionCompanies() {
        return data != null
                ? toNames(data.productionCompanies)
                : toNames(productionCompanies);
    }

    /**
     * Language(s) spoken IN the film (not the app language), written in the UI language.
     * Uses spoken_languages, then original_language. Returns "" when the server sends neither.
     * TODO(server): confirm the field names (spoken_languages / original_language).
     */
    public String getSpokenLanguageLabel(java.util.Locale uiLocale) {
        List<JsonElement> source = data != null ? data.spokenLanguages : spokenLanguages;
        List<String> labels = new ArrayList<>();
        if (source != null) {
            for (JsonElement e : source) {
                String label = languageLabel(e, uiLocale);
                if (!label.isEmpty() && !labels.contains(label)) labels.add(label);
                if (labels.size() >= 3) break;
            }
        }
        if (labels.isEmpty()) {
            String code = data != null ? data.originalLanguage : originalLanguage;
            String label = languageFromCode(code, uiLocale);
            if (!label.isEmpty()) labels.add(label);
        }
        StringBuilder out = new StringBuilder();
        for (String l : labels) {
            if (out.length() > 0) out.append(", ");
            out.append(l);
        }
        return out.toString();
    }

    private static String languageLabel(JsonElement e, java.util.Locale ui) {
        if (e == null || e.isJsonNull()) return "";
        try {
            if (e.isJsonPrimitive()) {
                String v = e.getAsString().trim();
                return v.matches("[A-Za-z]{2,3}") ? languageFromCode(v, ui) : v;
            }
            if (e.isJsonObject()) {
                JsonObject o = e.getAsJsonObject();
                if (o.has("iso_639_1") && !o.get("iso_639_1").isJsonNull()) {
                    String fromCode = languageFromCode(o.get("iso_639_1").getAsString(), ui);
                    if (!fromCode.isEmpty()) return fromCode;
                }
                for (String key : new String[]{"english_name", "name"}) {
                    if (o.has(key) && !o.get(key).isJsonNull()) {
                        String v = o.get(key).getAsString().trim();
                        if (!v.isEmpty()) return v;
                    }
                }
            }
        } catch (Exception ignored) {
            // malformed item: skip
        }
        return "";
    }

    private static String languageFromCode(String code, java.util.Locale ui) {
        if (code == null || code.trim().isEmpty()) return "";
        String c = code.trim();
        String name = java.util.Locale.forLanguageTag(c.toLowerCase(java.util.Locale.ROOT)).getDisplayLanguage(ui);
        if (name == null || name.isEmpty() || name.equalsIgnoreCase(c)) return c.toUpperCase(java.util.Locale.ROOT);
        return name.substring(0, 1).toUpperCase(ui) + name.substring(1);
    }

    public List<String> getSpokenLanguages() {
        return data != null ? toNames(data.spokenLanguages) : toNames(spokenLanguages);
    }

    public List<String> getProductionCountries() {
        return data != null
                ? toNames(data.productionCountries)
                : toNames(productionCountries);
    }

    private static String extractDirector(JsonElement director, JsonElement directors,
                                          List<JsonElement> crew, JsonElement credits) {
        List<String> names = new ArrayList<>();
        collectNames(director, names);
        if (names.isEmpty()) collectNames(directors, names);
        if (names.isEmpty()) collectDirectorsFromCrew(crew, names);
        if (names.isEmpty() && credits != null && credits.isJsonObject()) {
            JsonElement nested = credits.getAsJsonObject().get("crew");
            if (nested != null && nested.isJsonArray()) {
                List<JsonElement> list = new ArrayList<>();
                for (JsonElement e : nested.getAsJsonArray()) list.add(e);
                collectDirectorsFromCrew(list, names);
            }
        }
        StringBuilder out = new StringBuilder();
        java.util.Set<String> seen = new java.util.LinkedHashSet<>(names);
        int count = 0;
        for (String n : seen) {
            if (count++ >= 3) break;
            if (out.length() > 0) out.append(", ");
            out.append(n);
        }
        return out.toString();
    }

    /** Accepts "Name", ["Name", ...], {"name": "Name"} or a list of those. */
    private static void collectNames(JsonElement e, List<String> out) {
        if (e == null || e.isJsonNull()) return;
        try {
            if (e.isJsonPrimitive()) {
                String s = e.getAsString();
                if (s != null && !s.trim().isEmpty()) out.add(s.trim());
            } else if (e.isJsonArray()) {
                for (JsonElement item : e.getAsJsonArray()) collectNames(item, out);
            } else if (e.isJsonObject()) {
                JsonObject o = e.getAsJsonObject();
                if (o.has("name") && !o.get("name").isJsonNull()) {
                    String s = o.get("name").getAsString();
                    if (s != null && !s.trim().isEmpty()) out.add(s.trim());
                }
            }
        } catch (Exception ignored) {
            // A malformed item must not break the page.
        }
    }

    /** TMDB-style crew: [{"name": "...", "job": "Director"}, ...] */
    private static void collectDirectorsFromCrew(List<JsonElement> crew, List<String> out) {
        if (crew == null) return;
        for (JsonElement e : crew) {
            try {
                if (e == null || !e.isJsonObject()) continue;
                JsonObject o = e.getAsJsonObject();
                JsonElement job = o.get("job");
                if (job == null || job.isJsonNull() || !"director".equalsIgnoreCase(job.getAsString().trim())) continue;
                collectNames(o.get("name"), out);
            } catch (Exception ignored) {
                // skip malformed crew entry
            }
        }
    }

    private static List<Cast> safeCast(List<Cast> source) {
        return source != null ? source : new ArrayList<>();
    }

    private static List<Review> safeReviews(List<Review> source) {
        return source != null ? source : new ArrayList<>();
    }

    private static List<String> toNames(List<JsonElement> source) {
        List<String> out = new ArrayList<>();
        if (source == null) return out;

        for (JsonElement e : source) {
            if (e == null || e.isJsonNull()) continue;

            try {
                if (e.isJsonPrimitive()) {
                    String s = e.getAsString();
                    if (s != null && !s.trim().isEmpty()) {
                        out.add(s.trim());
                    }
                } else if (e.isJsonObject()) {
                    JsonObject o = e.getAsJsonObject();
                    String[] keys = {
                            "name",
                            "title",
                            "label",
                            "country",
                            "iso_3166_1"
                    };

                    for (String key : keys) {
                        if (o.has(key) && !o.get(key).isJsonNull()) {
                            String s = o.get(key).getAsString();
                            if (s != null && !s.trim().isEmpty()) {
                                out.add(s.trim());
                                break;
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
                // Ignore a malformed item without breaking the rest of the details.
            }
        }

        return out;
    }

    public static class DetailData implements Serializable {
        @SerializedName("tagline") private String tagline;
        @SerializedName("budget") private String budget;
        @SerializedName("revenue") private String revenue;
        @SerializedName("box_office") private String boxOffice;

        @SerializedName("cast") private List<Cast> cast;
        @SerializedName("reviews") private List<Review> reviews;
        @SerializedName("genres") private List<JsonElement> genres;
        @SerializedName("production_companies") private List<JsonElement> productionCompanies;
        @SerializedName("production_countries") private List<JsonElement> productionCountries;
        @SerializedName("spoken_languages") private List<JsonElement> spokenLanguages;
        @SerializedName("original_language") private String originalLanguage;
        @SerializedName(value = "director", alternate = {"directed_by", "director_name"}) private JsonElement director;
        @SerializedName("directors") private JsonElement directors;
        @SerializedName("crew") private List<JsonElement> crew;
        @SerializedName("credits") private JsonElement credits;
    }
}
