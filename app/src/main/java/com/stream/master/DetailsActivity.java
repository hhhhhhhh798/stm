package com.stream.master;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Typeface;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;

public class DetailsActivity extends BaseActivity {

    private Button btnFav;
    private Button btnPlay;
    private SharedPreferences prefs;

    private String vid;
    private String title;
    private String img;
    private String description;
    
    private String rating;
    private String year;
    private String quality;
    private String genre;
    private String director;
    private String duration;
    private String language;
    
    private boolean isExpanded = false;
    private AdManager adManager;
    private LinearLayout moreDetailsContainer;
    private CastAdapter castAdapter;
    private ReviewAdapter reviewAdapter;
    private final List<Cast> castList = new ArrayList<>();
    private final List<Review> reviewList = new ArrayList<>();

    private RecyclerView relatedRecyclerView;
    private MovieAdapter relatedAdapter;
    private List<Movie> relatedList = new ArrayList<>();
    private Intent relatedDetailIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Intent intent = new Intent();
        relatedDetailIntent = new Intent(DetailsActivity.this, DetailsActivity.class);
super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_details);
        adManager = new AdManager(this);

        Movie movie = (Movie) getIntent().getSerializableExtra(Constants.KEY_MOVIE);
        if (movie != null) {
            title = TextSanitizer.clean(movie.getTitle());
            img = movie.getImage();
            vid = movie.getVid();
            description = TextSanitizer.clean(movie.getDescription());
            
            rating = TextSanitizer.clean(movie.getRating());
            year = TextSanitizer.clean(movie.getYear());
            quality = TextSanitizer.clean(movie.getQuality());
            genre = TextSanitizer.clean(movie.getGenre());
            director = TextSanitizer.clean(movie.getDirector());
            duration = TextSanitizer.clean(movie.getDuration());
            language = TextSanitizer.clean(movie.getLanguage());
        } else {
            // محاولة جلب البيانات من الـ extras بشكل منفصل
            title = TextSanitizer.clean(getIntent().getStringExtra("TITLE"));
            img = getIntent().getStringExtra("IMG");
            vid = getIntent().getStringExtra("VID");
            description = TextSanitizer.clean(getIntent().getStringExtra("DESC"));
            // يمكنك إضافة المزيد هنا إذا كانت تمرر في الـ intent
        }

        director = cleanDirector(director);

        ImageView poster = findViewById(R.id.detailPoster);
        TextView titleText = findViewById(R.id.detailTitle);
        TextView descText = findViewById(R.id.detailDescription);
        TextView tvReadMore = findViewById(R.id.tvReadMore);
        
        TextView tvRating = findViewById(R.id.tvRating);
        TextView tvYear = findViewById(R.id.tvYear);
        TextView tvQualityChip = findViewById(R.id.tvQualityChip);
        
        TextView tvDirector = findViewById(R.id.tvDirector);
        TextView tvDuration = findViewById(R.id.tvDuration);
        TextView tvQuality = findViewById(R.id.tvQuality);
        TextView tvLanguage = findViewById(R.id.tvLanguage);

        moreDetailsContainer = findViewById(R.id.moreDetailsContainer);

        RecyclerView castRecyclerView = findViewById(R.id.castRecyclerView);
        castRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        castRecyclerView.addItemDecoration(new HorizontalMovieSpacingDecoration(
                getResources().getDimensionPixelSize(R.dimen.movie_card_shelf_gap)
        ));
        castAdapter = new CastAdapter(castList);
        castRecyclerView.setAdapter(castAdapter);

        RecyclerView reviewsRecyclerView = findViewById(R.id.reviewsRecyclerView);
        reviewsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        reviewAdapter = new ReviewAdapter(reviewList);
        reviewsRecyclerView.setAdapter(reviewAdapter);

        btnPlay = findViewById(R.id.btnPlay);
        btnFav = findViewById(R.id.btnFav);
        View btnPlayCircle = findViewById(R.id.btnPlayCircle);
        if (btnPlayCircle != null) btnPlayCircle.setOnClickListener(v -> btnPlay.performClick());
        FrameLayout btnBack = findViewById(R.id.btnBack);
        LinearLayout buttonsLayout = findViewById(R.id.buttonsLayout);

        relatedRecyclerView = findViewById(R.id.relatedRecyclerView);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        relatedRecyclerView.setLayoutManager(layoutManager);

        // Keep the same shared 14dp spacing used by every horizontal movie shelf.
        // This prevents the "You May Also Like" cards from touching each other.
        relatedRecyclerView.addItemDecoration(
                new HorizontalMovieSpacingDecoration(
                        getResources().getDimensionPixelSize(R.dimen.movie_card_shelf_gap)
                )
        );

        relatedAdapter = new MovieAdapter(relatedList, new MovieAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(Movie movie, View view) {
                relatedDetailIntent.putExtra(Constants.KEY_MOVIE, movie);
                startActivity(relatedDetailIntent);
            }
        }, this);

        relatedRecyclerView.setAdapter(relatedAdapter);

        prefs = getSharedPreferences("FAVS", MODE_PRIVATE);

        hydrateMissingMetadata();

        btnBack.setOnClickListener(v -> finish());

        titleText.setText(title != null ? title : "");
        Glide.with(this).load(img).into(poster);

        String notAvailable = getString(R.string.not_available);

        String displayRating = Movie.formatRating(rating);
        RatingStar.bindOrFallback(tvRating, displayRating, notAvailable);
        tvYear.setText(year != null && !year.isEmpty() ? year : notAvailable);
        tvQualityChip.setText(quality != null && !quality.isEmpty() ? quality : notAvailable);

        tvDirector.setText(director != null && !director.isEmpty() ? director : notAvailable);
        tvDuration.setText(duration != null && !duration.isEmpty() ? duration : notAvailable);
        tvQuality.setText(quality != null && !quality.isEmpty() ? quality : notAvailable);
        // Language = language spoken in the film (from api/detail). The list APIs return the APP language here, so it is not shown.
        tvLanguage.setText("—");

        if (description != null && !description.isEmpty()) {
            descText.setText(description);
            descText.post(() -> {
                if (descText.getLineCount() > 4) {
                    tvReadMore.setVisibility(View.VISIBLE);
                }
            });
        } else {
            descText.setText(getString(R.string.no_description));
        }

        tvReadMore.setOnClickListener(v -> {
            if (!isExpanded) {
                descText.setMaxLines(Integer.MAX_VALUE);
                tvReadMore.setText(getString(R.string.read_less));
                isExpanded = true;
            } else {
                descText.setMaxLines(4);
                tvReadMore.setText(getString(R.string.read_more));
                isExpanded = false;
            }
        });

        // Two tabs: "Details" (cast) and "More info" (facts, genres, reviews).
        final View tabDetails = findViewById(R.id.tabDetails);
        final View tabMoreInfo = findViewById(R.id.tabMoreInfo);
        final View paneDetails = findViewById(R.id.tabDetailsPane);
        final View paneMore = findViewById(R.id.tabMorePane);
        tabDetails.setSelected(true);
        tabDetails.setOnClickListener(v -> {
            tabDetails.setSelected(true);
            tabMoreInfo.setSelected(false);
            paneDetails.setVisibility(View.VISIBLE);
            paneMore.setVisibility(View.GONE);
        });
        tabMoreInfo.setOnClickListener(v -> {
            tabMoreInfo.setSelected(true);
            tabDetails.setSelected(false);
            paneMore.setVisibility(View.VISIBLE);
            paneDetails.setVisibility(View.GONE);
        });

        if (vid != null && prefs.contains(vid)) {
            saveOrUpdateFav(false);
        }

        updateFavButtonUI();

        final Intent playerIntent = new Intent(this, PlayerActivity.class);

        // التحقق من حالة الاستئناف عند البدء
        SharedPreferences resumePrefs = getSharedPreferences("StreamMasterResumePrefs", MODE_PRIVATE);
        long savedPosition = resumePrefs.getLong("pos_" + vid, 0);
        if (savedPosition > 5000) {
            btnPlay.setText(R.string.resume_play);
        } else {
            btnPlay.setText(R.string.play_now);
        }

        btnPlay.setOnClickListener(v -> {
            if (vid != null) {
                // User-driven ad event: opening a movie from Details.
                adManager.trigger("details_play");
                Intent playIntent = new Intent(DetailsActivity.this, PlayerActivity.class);
                playIntent.putExtra("VID", vid);
                playIntent.putExtra("TITLE", title);
                playIntent.putExtra("IMG", img);
                playIntent.putExtra("DESC", description);
                playIntent.putExtra("RATING", rating);
                playIntent.putExtra("YEAR", year);
                playIntent.putExtra("QUALITY", quality);
                playIntent.putExtra("GENRE", genre);
                playIntent.putExtra("DIRECTOR", director);
                playIntent.putExtra("DURATION", duration);
                playIntent.putExtra("LANGUAGE", language);
                startActivity(playIntent);
            }
        });

        btnFav.setOnClickListener(v -> {
            if (vid == null || vid.isEmpty()) return;

            if (prefs.contains(vid)) {
                prefs.edit().remove(vid).apply();
                showCustomToast(getString(R.string.removed_from_list));
            } else {
                saveOrUpdateFav(true);
                showCustomToast(getString(R.string.added_to_list));
            }
            updateFavButtonUI();
        });

        applyAnimations(poster, titleText, buttonsLayout, descText);

        if (title != null && !title.isEmpty()) {
            fetchRelatedMovies();
        }
        if (!isEmpty(vid)) {
            fetchMovieDetails();
            fetchTrailer(); // independent of the details request: a failure here never affects the page
        }
    }

    // ------------------------------------------------------------------
    // Trailer (api/trailer) - lazy, independent, cached in memory per movie id
    // ------------------------------------------------------------------
    private static final java.util.Map<String, String> TRAILER_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private String trailerUrl;

    private void setTrailerState(int state) { // 0 = hidden, 1 = loading, 2 = ready
        View button = findViewById(R.id.btnTrailer);
        View progress = findViewById(R.id.trailerProgress);
        View icon = findViewById(R.id.trailerIcon);
        if (button == null) return;
        button.setVisibility(state == 0 ? View.GONE : View.VISIBLE);
        button.setEnabled(state == 2);
        button.setAlpha(state == 1 ? 0.6f : 1f);
        if (progress != null) progress.setVisibility(state == 1 ? View.VISIBLE : View.GONE);
        if (icon != null) icon.setVisibility(state == 1 ? View.GONE : View.VISIBLE);
    }

    private void showTrailerReady(String url) {
        trailerUrl = url;
        setTrailerState(2);
        View button = findViewById(R.id.btnTrailer);
        if (button != null) button.setOnClickListener(v -> openTrailer(trailerUrl));
    }

    private void fetchTrailer() {
        String cached = TRAILER_CACHE.get(vid);
        if (cached != null) { showTrailerReady(cached); return; }

        setTrailerState(1);
        OkHttpClient client = SecureNetworkClient.INSTANCE.getClient(this);
        retrofit2.Retrofit retrofit = new retrofit2.Retrofit.Builder()
                .baseUrl(Constants.BASE_URL.endsWith("/") ? Constants.BASE_URL : Constants.BASE_URL + "/")
                .client(client)
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                .build();

        retrofit.create(ApiService.class).getTrailer(vid, getDetailsApiLang()).enqueue(new retrofit2.Callback<TrailerResponse>() {
            @Override
            public void onResponse(retrofit2.Call<TrailerResponse> call, retrofit2.Response<TrailerResponse> response) {
                if (isFinishing() || isDestroyed()) return;
                // TODO(server): confirm the api/trailer response shape (see TrailerResponse).
                String url = (response.isSuccessful() && response.body() != null) ? response.body().resolveUrl() : null;
                if (url == null) { setTrailerState(0); return; } // no trailer -> hide the button
                TRAILER_CACHE.put(vid, url);
                showTrailerReady(url);
            }

            @Override
            public void onFailure(retrofit2.Call<TrailerResponse> call, Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                setTrailerState(0);
            }
        });
    }

    /** YouTube app when installed, otherwise the browser; never crashes. */
    private void openTrailer(String url) {
        if (isEmpty(url)) return;
        android.net.Uri uri = android.net.Uri.parse(url);
        try {
            startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                    .setPackage("com.google.android.youtube"));
        } catch (android.content.ActivityNotFoundException notInstalled) {
            try {
                startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, uri));
            } catch (Exception e) {
                showCustomToast(getString(R.string.trailer_open_failed));
            }
        } catch (Exception e) {
            showCustomToast(getString(R.string.trailer_open_failed));
        }
    }

    private boolean isEmpty(String value) { return value == null || value.trim().isEmpty(); }

    private void showSpokenLanguage(String label) {
        TextView view = findViewById(R.id.tvLanguage);
        if (view != null) view.setText(isEmpty(label) ? getString(R.string.not_available) : label);
    }

    /** A "director" identical to the movie title is bad data from the server - treat it as missing. */
    private String cleanDirector(String value) {
        if (isEmpty(value)) return "";
        String v = value.trim();
        if (!isEmpty(title) && v.equalsIgnoreCase(title.trim())) return "";
        return v;
    }

    private String getDetailsApiLang() {
        String lang = getCurrentLang();
        return ("ar".equals(lang) || "es".equals(lang) || "en".equals(lang)) ? lang : "en";
    }

    private void fetchMovieDetails() {
        OkHttpClient client = SecureNetworkClient.INSTANCE.getClient(this);
        retrofit2.Retrofit retrofit = new retrofit2.Retrofit.Builder()
                .baseUrl(Constants.BASE_URL.endsWith("/") ? Constants.BASE_URL : Constants.BASE_URL + "/")
                .client(client)
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                .build();

        ApiService service = retrofit.create(ApiService.class);
        service.getMovieDetails(vid, getDetailsApiLang()).enqueue(new retrofit2.Callback<DetailResponse>() {
            @Override
            public void onResponse(retrofit2.Call<DetailResponse> call, retrofit2.Response<DetailResponse> response) {
                if (isFinishing() || isDestroyed()) return;

                if (!response.isSuccessful() || response.body() == null) {
                    showSpokenLanguage("");
                    return;
                }

                DetailResponse body = response.body();

                if (!body.hasData()) {
                    showSpokenLanguage("");
                    return;
                }


                applyDetailResponse(body);
            }

            @Override
            public void onFailure(retrofit2.Call<DetailResponse> call, Throwable t) {
                showSpokenLanguage("");
            }
        });
    }

    private void applyDetailResponse(DetailResponse data) {
        if (data == null) return;

        runOnUiThread(() -> {
            TextView tagline = findViewById(R.id.tvTagline);
            TextView budget = findViewById(R.id.tvBudget);
            TextView boxOffice = findViewById(R.id.tvBoxOffice);
            String na = getString(R.string.not_available);

            tagline.setText(isEmpty(data.getTagline()) ? na : TextSanitizer.clean(data.getTagline()));
            budget.setText(isEmpty(data.getBudget()) ? na : formatMoney(TextSanitizer.clean(data.getBudget())));

            showSpokenLanguage(data.getSpokenLanguageLabel(java.util.Locale.forLanguageTag(getCurrentLang())));

            // Director: list endpoints rarely include it, so fill it from api/detail when it is still empty.
            String apiDirector = cleanDirector(TextSanitizer.clean(data.getDirector()));
            if (isEmpty(director) && !isEmpty(apiDirector)) {
                director = apiDirector;
                ((TextView) findViewById(R.id.tvDirector)).setText(director);
            }
            boxOffice.setText(isEmpty(data.getBoxOffice()) ? na : formatMoney(TextSanitizer.clean(data.getBoxOffice())));

            castList.clear();
            castList.addAll(data.getCast());
            castAdapter.notifyDataSetChanged();
            findViewById(R.id.castEmpty).setVisibility(castList.isEmpty() ? View.VISIBLE : View.GONE);

            reviewList.clear();
            reviewList.addAll(data.getReviews());
            reviewAdapter.notifyDataSetChanged();
            findViewById(R.id.reviewsEmpty).setVisibility(reviewList.isEmpty() ? View.VISIBLE : View.GONE);

            populateChips((ChipGroup) findViewById(R.id.genresChipGroup), data.getGenres());
            List<String> spoken = data.getSpokenLanguages();
            populateChips((ChipGroup) findViewById(R.id.spokenLanguagesChipGroup), spoken);
            int spokenVisibility = spoken.isEmpty() ? View.GONE : View.VISIBLE;
            findViewById(R.id.spokenLanguagesTitle).setVisibility(spokenVisibility);
            findViewById(R.id.spokenLanguagesChipGroup).setVisibility(spokenVisibility);
            populateChips((ChipGroup) findViewById(R.id.productionCompaniesChipGroup), data.getProductionCompanies());
            populateChips((ChipGroup) findViewById(R.id.productionCountriesChipGroup), data.getProductionCountries());
        });
    }

    /**
     * Formats financial values with thousands separators without changing the
     * underlying value returned by the API. Examples: 225000000 -> 225,000,000.
     */
    private String formatMoney(String value) {
        if (isEmpty(value)) return getString(R.string.not_available);

        try {
            String normalized = value.trim().replace(",", "");
            java.math.BigDecimal number = new java.math.BigDecimal(normalized);

            java.text.DecimalFormatSymbols symbols =
                    java.text.DecimalFormatSymbols.getInstance(java.util.Locale.US);
            java.text.DecimalFormat formatter = new java.text.DecimalFormat("#,##0.##", symbols);
            formatter.setGroupingUsed(true);
            formatter.setParseBigDecimal(true);

            return formatter.format(number);
        } catch (Exception ignored) {
            // Keep the original API value if it is not a numeric amount.
            return value;
        }
    }

    private void populateChips(ChipGroup group, List<String> values) {
        group.removeAllViews();
        for (String value : values) {
            if (isEmpty(value)) continue;
            Chip chip = new Chip(this);
            chip.setTypeface(getDetailTypeface(false));
            chip.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
            chip.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            chip.setText(TextSanitizer.clean(value));
            chip.setTextColor(getResources().getColor(R.color.text_primary));
            chip.setTextSize(14);
            chip.setCheckable(false);
            chip.setClickable(false);
            chip.setChipBackgroundColorResource(R.color.chip_surface);
            chip.setChipStrokeWidth(0f);
            chip.setChipMinHeight(dp(40));
            chip.setMinHeight(dp(40));
            chip.setTextStartPadding(dp(6));
            chip.setTextEndPadding(dp(6));
            chip.setEnsureMinTouchTargetSize(false);
            group.addView(chip);
        }
    }


    private Typeface getDetailTypeface(boolean bold) {
        String lang = getCurrentLang();
        int fontRes;
        if ("ar".equals(lang)) {
            fontRes = bold ? R.font.stream_kufi_arabic_bold : R.font.stream_kufi_arabic_regular;
        } else if ("es".equals(lang)) {
            fontRes = bold ? R.font.stream_noto_sans_es_bold : R.font.stream_noto_sans_es_regular;
        } else {
            fontRes = R.font.stream_arial;
        }
        Typeface base = ResourcesCompat.getFont(this, fontRes);
        return Typeface.create(base, bold ? Typeface.BOLD : Typeface.NORMAL);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void hydrateMissingMetadata() {
        boolean missing = isEmpty(rating) || isEmpty(year) || isEmpty(quality) || isEmpty(genre)
                || isEmpty(director) || isEmpty(duration) || isEmpty(language);
        if (!missing || (isEmpty(vid) && isEmpty(title))) return;

        OkHttpClient client = SecureNetworkClient.INSTANCE.getClient(this);
        retrofit2.Retrofit retrofit = new retrofit2.Retrofit.Builder()
                .baseUrl(Constants.BASE_URL.endsWith("/") ? Constants.BASE_URL : Constants.BASE_URL + "/")
                .client(client)
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                .build();
        ApiService service = retrofit.create(ApiService.class);
        service.searchMovies(title != null ? title : vid, getCurrentLang(), 1, 10)
                .enqueue(new retrofit2.Callback<MovieResponse>() {
                    @Override public void onResponse(retrofit2.Call<MovieResponse> call, retrofit2.Response<MovieResponse> response) {
                        if (!response.isSuccessful() || response.body() == null || response.body().getResults() == null) return;
                        Movie match = null;
                        for (Movie candidate : response.body().getResults()) {
                            if (candidate == null) continue;
                            if (!isEmpty(vid) && vid.equals(candidate.getVid())) { match = candidate; break; }
                            if (match == null && !isEmpty(title) && title.equalsIgnoreCase(candidate.getTitle())) match = candidate;
                        }
                        if (match == null) return;
                        if (isEmpty(title)) title = TextSanitizer.clean(match.getTitle());
                        if (isEmpty(img)) img = match.getImage();
                        if (isEmpty(description)) description = TextSanitizer.clean(match.getDescription());
                        if (isEmpty(rating)) rating = TextSanitizer.clean(match.getRating());
                        if (isEmpty(year)) year = TextSanitizer.clean(match.getYear());
                        if (isEmpty(quality)) quality = TextSanitizer.clean(match.getQuality());
                        if (isEmpty(genre)) genre = TextSanitizer.clean(match.getGenre());
                        if (isEmpty(director)) director = cleanDirector(TextSanitizer.clean(match.getDirector()));
                        if (isEmpty(duration)) duration = TextSanitizer.clean(match.getDuration());
                        if (isEmpty(language)) language = TextSanitizer.clean(match.getLanguage());
                        runOnUiThread(() -> {
                            String na = getString(R.string.not_available);
                            String rr = Movie.formatRating(rating);
                            RatingStar.bindOrFallback((TextView)findViewById(R.id.tvRating), rr, na);
                            ((TextView)findViewById(R.id.tvYear)).setText(isEmpty(year)?na:year);
                            ((TextView)findViewById(R.id.tvQualityChip)).setText(isEmpty(quality)?na:quality);
                            ((TextView)findViewById(R.id.tvDirector)).setText(isEmpty(director)?na:director);
                            ((TextView)findViewById(R.id.tvDuration)).setText(isEmpty(duration)?na:duration);
                            ((TextView)findViewById(R.id.tvQuality)).setText(isEmpty(quality)?na:quality);
                            ((TextView)findViewById(R.id.detailTitle)).setText(TextSanitizer.clean(title==null?"":title));
                            ((TextView)findViewById(R.id.detailDescription)).setText(isEmpty(description)?getString(R.string.no_description):TextSanitizer.clean(description));
                            Glide.with(DetailsActivity.this).load(img).into((ImageView)findViewById(R.id.detailPoster));
                            if (prefs.contains(vid)) saveOrUpdateFav(false);
                        });
                    }
                    @Override public void onFailure(retrofit2.Call<MovieResponse> call, Throwable t) { }
                });
    }

    // ============================================================
    // 🛡️ تم التعديل: استخدام SecureNetworkClient بدلاً من NetworkClient
    // ============================================================
    private void fetchRelatedMovies() {
        // ✅ استخدام SecureNetworkClient مع SSL Pinning
        OkHttpClient client = SecureNetworkClient.INSTANCE.getClient(this);

        ApiService apiService = new retrofit2.Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .client(client)  // ✅ العميل الآمن
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                .build()
                .create(ApiService.class);

        apiService.getRelatedMovies(vid, getCurrentLang(), 1, 10).enqueue(new retrofit2.Callback<MovieResponse>() {
            @Override
            public void onResponse(retrofit2.Call<MovieResponse> call, retrofit2.Response<MovieResponse> response) {
                if (isFinishing() || isDestroyed()) return;
                List<Movie> list = new ArrayList<>();
                if (response.isSuccessful() && response.body() != null) {
                    List<Movie> bodyList = response.body().getResults();
                    if (bodyList != null) {
                        list = bodyList;
                    }
                }
                relatedList.clear();
                relatedList.addAll(list);
                relatedAdapter.notifyDataSetChanged();
            }

            @Override
            public void onFailure(retrofit2.Call<MovieResponse> call, Throwable t) {
                // Keep production builds silent; the UI already handles this failure.
            }
        });
    }

    private void saveOrUpdateFav(boolean isNewAdd) {
        try {
            String existingJson = prefs.getString(vid, "{}");
            JSONObject rootObj = new JSONObject(existingJson);

            JSONObject langObj = new JSONObject();
            langObj.put("title", title != null ? title : "");
            langObj.put("img", img != null ? img : "");
            langObj.put("desc", description != null ? description : "");
            langObj.put("rating", rating != null ? rating : "");
            langObj.put("year", year != null ? year : "");
            langObj.put("quality", quality != null ? quality : "");
            langObj.put("genre", genre != null ? genre : "");
            langObj.put("director", director != null ? director : "");
            langObj.put("duration", duration != null ? duration : "");
            langObj.put("language", language != null ? language : "");

            rootObj.put(getCurrentLang(), langObj);

            prefs.edit().putString(vid, rootObj.toString()).apply();
        } catch (Exception e) {
            // Keep production builds silent; the UI already handles this failure.
        }
    }

    private void applyAnimations(ImageView poster, TextView titleText, LinearLayout buttonsLayout, TextView descText) {
        poster.setAlpha(0f);
        poster.setScaleX(1.05f);
        poster.setScaleY(1.05f);
        poster.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(400).start();

        titleText.setTranslationY(30f);
        titleText.setAlpha(0f);
        titleText.animate().translationY(0f).alpha(1f).setDuration(400).setStartDelay(100).start();

        buttonsLayout.setAlpha(0f);
        buttonsLayout.animate().alpha(1f).setDuration(400).setStartDelay(200).start();

        descText.setAlpha(0f);
        descText.animate().alpha(1f).setDuration(400).setStartDelay(300).start();
    }

    private void updateFavButtonUI() {
        if (prefs != null && vid != null && prefs.contains(vid)) {
            btnFav.setText(getString(R.string.in_watchlist));
            btnFav.setBackgroundResource(R.drawable.bg_outline_button_active);
        } else {
            btnFav.setText(getString(R.string.add_to_watchlist));
            btnFav.setBackgroundResource(R.drawable.bg_outline_button);
        }
    }
    @Override
    protected void onDestroy() {
        if (adManager != null) adManager.release();
        super.onDestroy();
    }

}