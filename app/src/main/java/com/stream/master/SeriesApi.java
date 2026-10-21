package com.stream.master;

import android.content.Context;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/** Same client / base URL / converter as every other endpoint in the app. */
final class SeriesApi {
    private SeriesApi() {}

    /** Returns null when no base URL is configured. */
    static ApiService create(Context context) {
        String base = Constants.BASE_URL;
        if (base == null || base.isEmpty()) return null;
        if (!base.endsWith("/")) base += "/";
        OkHttpClient client = SecureNetworkClient.INSTANCE.getClient(context);
        return new Retrofit.Builder()
                .baseUrl(base)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService.class);
    }
}
