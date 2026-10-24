package com.stream.master;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

public class SubtitleSearchResponse {
    @SerializedName("status")
    private String status;

    @SerializedName("subtitles")
    private List<SubtitleItem> subtitles;

    public boolean isSuccess() {
        return "success".equalsIgnoreCase(status) || "ok".equalsIgnoreCase(status);
    }

    public List<SubtitleItem> getSubtitles() {
        return subtitles != null ? subtitles : new ArrayList<>();
    }

    public static class SubtitleItem {
        @SerializedName(value = "language", alternate = {"lang"}) private String language;
        @SerializedName("label") private String label;
        @SerializedName("release_name") private String releaseName;
        @SerializedName("format") private String format;
        @SerializedName("url") private String url;
        @SerializedName(value = "download_url", alternate = {"downloadUrl"}) private String downloadUrl;
        @SerializedName("hi") private boolean hearingImpaired;
        @SerializedName("season") private int season;
        @SerializedName("episode") private int episode;

        public String getLanguage() { return language == null ? "und" : language.trim().toLowerCase(); }
        public String getLabel() {
            if (label != null && !label.trim().isEmpty()) return label.trim();
            if (releaseName != null && !releaseName.trim().isEmpty()) return releaseName.trim();
            String l = getLanguage();
            if ("ar".equals(l)) return "العربية";
            if ("en".equals(l)) return "English";
            if ("es".equals(l)) return "Español";
            if ("fr".equals(l)) return "Français";
            return l.toUpperCase();
        }
        public String getUrl() {
            String u = (downloadUrl != null && !downloadUrl.trim().isEmpty()) ? downloadUrl : url;
            return u == null ? "" : u.trim();
        }
        public String getFormat() { return format == null ? "srt" : format.trim().toLowerCase(); }
        public boolean isHearingImpaired() { return hearingImpaired; }
        public int getSeason() { return season; }
        public int getEpisode() { return episode; }
    }
}
