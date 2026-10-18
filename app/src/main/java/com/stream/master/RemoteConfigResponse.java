package com.stream.master;

import com.google.gson.annotations.SerializedName;

/** Authenticated, MAC-signed remote configuration envelope. */
public class RemoteConfigResponse {
    @SerializedName("success") private boolean success;
    @SerializedName("payload_b64") private String payloadB64;
    @SerializedName("signature") private String signature;
    @SerializedName("message") private String message;

    public boolean isSuccess() { return success; }
    public String getPayloadB64() { return payloadB64 != null ? payloadB64 : ""; }
    public String getSignature() { return signature != null ? signature : ""; }
    public String getMessage() { return message != null ? message : ""; }
}
