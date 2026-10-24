package com.stream.master;

import com.google.gson.annotations.SerializedName;

public class TokenResponse {
    @SerializedName("success") private boolean success;
    @SerializedName("token") private String token;
    @SerializedName("access_token") private String accessToken;
    @SerializedName("refresh_token") private String refreshToken;
    @SerializedName("expires_in") private long expiresIn;
    @SerializedName("message") private String message;

    public boolean isSuccess() { return success; }
    public String getToken() { return token; }
    public String getAccessToken() { return accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public long getExpiresIn() { return expiresIn; }
    public String getMessage() { return message; }
}
