package com.stream.master;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StreamResponse {

    @SerializedName("status")
    private String status;

    @SerializedName("stream_session")
    private String streamSession;

    @SerializedName("source")
    private String source;

    @SerializedName("data")
    private MovieData data;

    @SerializedName(value = "stream_url", alternate = {"streaming_url"})
    private String rootStreamUrl;

    @SerializedName("extracted_link")
    private String rootExtractedLink;

    @SerializedName("headers")
    private Map<String, String> rootHeaders;

    @SerializedName("referer")
    private String rootReferer;

    @SerializedName("subtitles")
    private List<Subtitle> rootSubtitles;

    @SerializedName("streams")
    private List<StreamItem> rootStreams;

    private transient boolean dataResolved = false;


    public String getStatus() {
        return status != null ? status : "";
    }

    public String getStreamSession() { return streamSession != null ? streamSession : ""; }

    public String getSource() {
        return source != null ? source : "";
    }


    public synchronized MovieData getData() {

        if (data == null) {
            data = new MovieData();
        }

        if (dataResolved) {
            return data;
        }

        if (isBlank(data.streamUrl)) {
            if (!isBlank(rootStreamUrl)) {
                data.streamUrl = rootStreamUrl;
            } else if (!isBlank(rootExtractedLink)) {
                data.streamUrl = rootExtractedLink;
            }
        }

        if (data.headers == null || data.headers.isEmpty()) {
            if (rootHeaders != null && !rootHeaders.isEmpty()) {
                data.headers = new HashMap<>(rootHeaders);
            } else {
                data.headers = new HashMap<>();
            }
        }

        if (!data.headers.containsKey("Referer")) {
            String refererCandidate = !isBlank(data.referer) ? data.referer : rootReferer;
            if (!isBlank(refererCandidate)) {
                data.headers.put("Referer", refererCandidate);
            }
        }

        if ((data.subtitles == null || data.subtitles.isEmpty())
                && rootSubtitles != null
                && !rootSubtitles.isEmpty()) {
            data.subtitles = new ArrayList<>(rootSubtitles);
        }

        if ((data.streams == null || data.streams.isEmpty())
                && rootStreams != null
                && !rootStreams.isEmpty()) {
            data.streams = new ArrayList<>(rootStreams);
        }

        dataResolved = true;
        return data;
    }


    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty() || "null".equalsIgnoreCase(s.trim());
    }


    // ============================================================
    // MovieData
    // ============================================================

    public static class MovieData {

        @SerializedName("tmdb_id")
        private String tmdbId;

        @SerializedName("title")
        private String title;

        @SerializedName("poster")
        private String poster;

        @SerializedName("overview")
        private String overview;

        @SerializedName("stream_url")
        private String streamUrl;

        @SerializedName("subtitle_url")
        private String subtitleUrl;

        @SerializedName("subtitles")
        private List<Subtitle> subtitles;

        @SerializedName("streams")
        private List<StreamItem> streams;

        @SerializedName("duration")
        private int duration;

        @SerializedName("headers")
        private Map<String, String> headers;

        @SerializedName("referer")
        private String referer;

        private transient List<Subtitle> resolvedSubtitles;


        public String getTmdbId() {
            return tmdbId != null ? tmdbId : "";
        }

        public String getTitle() {
            return title != null ? title : "";
        }

        public String getPoster() {
            return poster != null ? poster : "";
        }

        public String getOverview() {
            return overview != null ? overview : "";
        }

        public String getStreamUrl() {
            return isBlank(streamUrl) ? "" : streamUrl;
        }

        public String getSubtitleUrl() {
            return isBlank(subtitleUrl) ? "" : subtitleUrl;
        }

        public void setSubtitleUrl(String subtitleUrl) {
            this.subtitleUrl = subtitleUrl;
        }

        public List<Subtitle> getSubtitles() {
            if (resolvedSubtitles == null) {
                resolvedSubtitles = dedupeSubtitles(subtitles);
            }
            return new ArrayList<>(resolvedSubtitles);
        }

        public void setSubtitles(List<Subtitle> subtitles) {
            this.subtitles = subtitles;
            this.resolvedSubtitles = null;
        }

        private static List<Subtitle> dedupeSubtitles(List<Subtitle> source) {
            List<Subtitle> result = new ArrayList<>();
            if (source == null || source.isEmpty()) {
                return result;
            }
            LinkedHashSet<String> seenKeys = new LinkedHashSet<>();
            for (Subtitle sub : source) {
                if (sub == null || sub.getUrl().isEmpty()) {
                    continue;
                }
                String key = sub.getUrl().trim().toLowerCase(Locale.US) + "|" + sub.getLanguage();
                if (seenKeys.add(key)) {
                    result.add(sub);
                }
            }
            return result;
        }

        public List<StreamItem> getStreams() {
            if (streams == null) {
                return new ArrayList<>();
            }
            return new ArrayList<>(streams);
        }

        public void setStreams(List<StreamItem> streams) {
            this.streams = streams;
        }

        public int getDuration() {
            return Math.max(duration, 0);
        }

        public Map<String, String> getHeaders() {
            if (headers == null) {
                return new HashMap<>();
            }
            return new HashMap<>(headers);
        }

        public String getReferer() {
            return isBlank(referer) ? null : referer;
        }
    }


    // ============================================================
    // StreamItem
    // ============================================================

    public static class StreamItem {

        @SerializedName("url")
        private String url;

        @SerializedName("quality")
        private String quality;

        @SerializedName("width")
        private int width;

        @SerializedName("height")
        private int height;

        @SerializedName("bandwidth")
        private long bandwidth;

        @SerializedName(value = "source", alternate = {"provider"})
        private String source;

        @SerializedName("headers")
        private Map<String, String> headers;


        public String getUrl() {
            return isBlank(url) ? "" : url;
        }

        public String getQuality() {
            return normalizeQuality(quality);
        }

        public int getWidth() {
            return Math.max(width, 0);
        }

        public int getHeight() {
            return Math.max(height, 0);
        }

        public long getBandwidth() {
            return Math.max(bandwidth, 0L);
        }

    public String getSource() {
            return source != null ? source : "";
        }

        public Map<String, String> getHeaders() {
            if (headers == null) {
                return new HashMap<>();
            }
            return new HashMap<>(headers);
        }

        private static String normalizeQuality(String raw) {
            if (raw == null) return "";
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) return "";

            String lower = trimmed.toLowerCase(Locale.US);

            switch (lower) {
                case "sd":
                    return "480p";
                case "hd":
                    return "720p";
                case "fhd":
                case "full hd":
                case "fullhd":
                    return "1080p";
                case "qhd":
                case "2k":
                    return "1440p";
                case "4k":
                case "uhd":
                    return "2160p";
                default:
                    break;
            }

            int i = 0;
            while (i < lower.length() && Character.isDigit(lower.charAt(i))) {
                i++;
            }
            if (i > 0) {
                return lower.substring(0, i) + "p";
            }

            return "";
        }
    }


    // ============================================================
    // Subtitle
    // ============================================================

    public static class Subtitle {

        @SerializedName("url")
        private String url;

        @SerializedName("label")
        private String label;

        @SerializedName("language")
        private String language;

        @SerializedName("format")
        private String format;


        //  Setters
        public void setUrl(String url) {
            this.url = url;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public void setLanguage(String language) {
            this.language = language;
        }

        public void setFormat(String format) {
            this.format = format;
        }


        public String getUrl() {
            if (isBlank(url)) return "";
            return url.trim().replace(" ", "%20");
        }


        public String getLabel() {
            if (label != null && !label.trim().isEmpty()) {
                return label.trim();
            }

            String normalizedLanguage = getLanguage();
            if ("ar".equals(normalizedLanguage)) return "العربية";
            if ("en".equals(normalizedLanguage)) return "English";
            if ("fr".equals(normalizedLanguage)) return "Français";
            if ("es".equals(normalizedLanguage)) return "Español";

            return "Subtitle";
        }


        public String getLanguage() {
            if (language == null) {
                return "und";
            }

            String lang = language.trim().toLowerCase(Locale.US);
            if (lang.isEmpty() || "null".equals(lang)) {
                return "und";
            }

            if (lang.equals("ar") || lang.equals("ara") || lang.equals("arabic") || lang.startsWith("ar-")) {
                return "ar";
            }

            if (lang.equals("en") || lang.equals("eng") || lang.equals("english") || lang.startsWith("en-")) {
                return "en";
            }

            if (lang.equals("fr") || lang.equals("fra") || lang.equals("fre") || lang.equals("french") || lang.startsWith("fr-")) {
                return "fr";
            }

            if (lang.equals("es") || lang.equals("spa") || lang.equals("spanish") || lang.startsWith("es-")) {
                return "es";
            }

            String base = lang.contains("-") ? lang.substring(0, lang.indexOf('-')) : lang;
            if (base.length() >= 2 && base.length() <= 3 && isAlpha(base)) {
                return base;
            }

            return "und";
        }

        private static boolean isAlpha(String s) {
            for (int i = 0; i < s.length(); i++) {
                if (!Character.isLetter(s.charAt(i))) {
                    return false;
                }
            }
            return true;
        }


        public String getFormat() {
            if (format != null && !format.trim().isEmpty()) {
                String f = format.trim().toLowerCase(Locale.US);
                if (f.contains("vtt")) return "vtt";
                if (f.contains("srt") || f.contains("subrip")) return "srt";
                return f;
            }

            String cleanUrl = getUrl().toLowerCase(Locale.US);
            int queryIndex = cleanUrl.indexOf('?');
            if (queryIndex != -1) {
                cleanUrl = cleanUrl.substring(0, queryIndex);
            }

            if (cleanUrl.endsWith(".vtt")) {
                return "vtt";
            }
            
            return "srt";
        }
    }
}
