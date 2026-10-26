package com.stream.master;

import com.google.gson.annotations.SerializedName;

/**
 * Response of GET api/trailer.
 *
 * The server answers { "status": "success", "key": "XXXX", "url": "https://www.youtube.com/watch?v=XXXX", "data": {...} }
 * (any ONE of url / key is enough, both nullable). 404 / 503 mean "no trailer" and the button stays hidden.
 * A few alternative field names are accepted as well. Unknown/missing fields are ignored.
 */
public class TrailerResponse {

    @SerializedName(value = "url", alternate = {"trailer_url", "trailerUrl", "youtube_url", "youtubeUrl", "link"})
    private String url;

    @SerializedName(value = "key", alternate = {"youtube_key", "youtubeKey", "video_key", "videoKey"})
    private String key;

    public String getUrl() { return url; }
    public String getKey() { return key; }

    /** Final link to open, or null when the response carries no usable trailer. */
    public String resolveUrl() {
        if (url != null) {
            String u = url.trim();
            if (u.matches("(?i)^https?://\\S+$")) return u;
        }
        if (key != null) {
            String k = key.trim();
            if (k.matches("^[A-Za-z0-9_-]{6,32}$")) return "https://www.youtube.com/watch?v=" + k;
        }
        return null;
    }
}
