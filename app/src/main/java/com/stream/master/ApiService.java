package com.stream.master;

import com.google.gson.JsonObject;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.Url;

public interface ApiService {

    @GET("api/detail")
    Call<DetailResponse> getMovieDetails(
            @Query("vid") String movieId,
            @Query("lang") String lang
    );

    // Trailer (server: YouTube Data API, key in the YOUTUBE_API_KEY environment variable).
    // 404 / 503 simply mean "no trailer" and the app hides the button.
    @GET("api/trailer")
    Call<TrailerResponse> getTrailer(
            @Query("vid") String movieId,
            @Query("lang") String lang
    );

    // Series (implemented in the server's index.js, tables tv_shows + episodes):
    //   list   -> api/series?lang=&page=&limit=[&cat=][&query=]
    //   detail -> api/series?vid=<series TMDB id>&lang=   (seasons + episodes)
    @GET("api/series")
    Call<SeriesResponse> getSeriesList(
            @Query("lang") String lang,
            @Query("page") int page,
            @Query("limit") int limit
    );

    // Same endpoint, with the server's built-in text filter (searches name_ar/name_en/name_es/original_name).
    @GET("api/series")
    Call<SeriesResponse> getSeriesList(
            @Query("lang") String lang,
            @Query("page") int page,
            @Query("limit") int limit,
            @Query("query") String query
    );

    @GET("api/series")
    Call<SeriesDetailResponse> getSeriesDetails(
            @Query("vid") String seriesId,
            @Query("lang") String lang
    );

    @GET("api/home")
    Call<HomeResponse> getHomeMovies(
            @Query("lang") String lang,
            @Query("limit") int limit
    );

    @GET("api/banner")
    Call<MovieResponse> getBannerMovies(
            @Query("lang") String lang,
            @Query("page") int page,
            @Query("limit") int limit
    );

    @GET("api/similar")
    Call<MovieResponse> getRelatedMovies(
            @Query("vid") String movieId,
            @Query("lang") String lang,
            @Query("page") int page,
            @Query("limit") int limit
    );

    @GET("api/category")
    Call<MovieResponse> getCategoryMovies(
            @Query("cat") String cat,
            @Query("lang") String lang,
            @Query("page") int page,
            @Query("limit") int limit
    );

    @GET("api/stream")
    Call<StreamResponse> getMovieStream(@Query("vid") String movieId);

    // One episode of a series: vid = the SERIES TMDB id, s = season, e = episode.
    @GET("api/stream")
    Call<StreamResponse> getEpisodeStream(
            @Query("vid") String seriesId,
            @Query("type") String type,
            @Query("s") int season,
            @Query("e") int episode
    );

    @GET("api/stream/resolve")
    Call<StreamResponse> resolveStreamSession(@Query("ticket") String ticket);

    @GET("api/config")
    Call<JsonObject> getRemoteConfig();

    @GET("api/subtitles")
    Call<SubtitleSearchResponse> getSubtitles(
            @Query("tmdb_id") String tmdbId,
            @Query("type") String type,
            @Query("season") Integer season,
            @Query("episode") Integer episode
    );

    @GET("api/search")
    Call<MovieResponse> searchMovies(
            @Query("query") String query,
            @Query("lang") String lang,
            @Query("page") int page,
            @Query("limit") int limit
    );

    @GET("github/provider.json")
    Call<String> getProviderJson();

    @GET("github/callback.json")
    Call<String> getCallbackJson();

    @GET("github/providers/{providerName}")
    Call<String> getProviderCode(@Path("providerName") String providerName);

    @GET("github/update.json")
    Call<UpdateInfo> checkUpdate();

    @GET
    Call<UpdateInfo> checkUpdateAt(@Url String url);

    @GET
    Call<ForceUpdateInfo> checkForceUpdateAt(@Url String url);

    @GET
    Call<TokenResponse> getToken(@Url String url);
}