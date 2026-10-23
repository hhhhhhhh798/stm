package com.stream.master;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Maps /api/turn-credentials. `urls` is empty (not null, not an error) when the server hasn't
 * configured TURN_SECRET/TURN_HOST yet - see that route's own doc. Callers should treat an empty
 * or missing `urls` as "no TURN available right now", not as a failure.
 */
public class TurnCredentialsResponse {
    @SerializedName("status")
    public String status;

    @SerializedName("data")
    public Data data;

    public static class Data {
        @SerializedName("ttl")
        public long ttl;

        @SerializedName("username")
        public String username;

        @SerializedName("credential")
        public String credential;

        @SerializedName("urls")
        public List<String> urls;
    }

    public boolean hasTurnServer() {
        return data != null
                && data.urls != null
                && !data.urls.isEmpty()
                && data.username != null
                && data.credential != null;
    }
}
