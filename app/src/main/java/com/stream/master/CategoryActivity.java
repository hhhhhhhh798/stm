package com.stream.master;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Dedicated category browser.
 * Categories are filtered by the API category endpoint, never by SearchActivity.
 */
public class CategoryActivity extends BaseActivity {

    private final List<Movie> movies = new ArrayList<>();
    private MovieAdapter adapter;
    private GridLayoutManager layoutManager;
    private ProgressBar progress;
    private ProgressBar loadMoreProgress;
    private TextView emptyText;
    private TextView titleText;
    private String categoryKey = "";
    private int page = 1;
    private boolean loading = false;
    private boolean hasMore = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category);

        categoryKey = getIntent().getStringExtra("CATEGORY_KEY");
        if (categoryKey == null) categoryKey = "";
        categoryKey = categoryKey.trim();

        titleText = findViewById(R.id.categoryTitle);
        progress = findViewById(R.id.categoryProgress);
        loadMoreProgress = findViewById(R.id.categoryLoadMoreProgress);
        emptyText = findViewById(R.id.categoryEmpty);
        ImageView back = findViewById(R.id.categoryBack);
        RecyclerView recycler = findViewById(R.id.categoryRecyclerView);

        titleText.setText(getLocalizedCategoryTitle(categoryKey));
        back.setOnClickListener(v -> finish());

        layoutManager = new GridLayoutManager(this, 3);
        recycler.setLayoutManager(layoutManager);
        recycler.setHasFixedSize(true);
        recycler.setItemViewCacheSize(12);
        recycler.setClipToPadding(false);
        recycler.addItemDecoration(new GridSpacingItemDecoration(3, dp(8), dp(14), true));

        adapter = new MovieAdapter(movies, (movie, sharedView) -> {
            android.content.Intent intent = new android.content.Intent(this, DetailsActivity.class);
            intent.putExtra(Constants.KEY_MOVIE, movie);
            startActivity(intent);
        }, this);
        recycler.setAdapter(adapter);

        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                super.onScrolled(rv, dx, dy);
                if (dy <= 0 || loading || !hasMore) return;
                int total = layoutManager.getItemCount();
                int last = layoutManager.findLastVisibleItemPosition();
                if (last >= total - 6) loadPage(page + 1);
            }
        });

        if (categoryKey.isEmpty()) {
            showEmpty();
        } else {
            loadPage(1);
        }
    }

    private void loadPage(int requestedPage) {
        if (loading || !hasMore || categoryKey.isEmpty()) return;
        loading = true;
        boolean isFirstLoad = movies.isEmpty();
        if (isFirstLoad) {
            if (progress != null) progress.setVisibility(View.VISIBLE);
        } else {
            if (loadMoreProgress != null) loadMoreProgress.setVisibility(View.VISIBLE);
        }

        OkHttpClient client = SecureNetworkClient.INSTANCE.getClient(this);
        String base = Constants.BASE_URL;
        if (base == null || base.isEmpty()) {
            loading = false;
            showEmpty();
            return;
        }
        if (!base.endsWith("/")) base += "/";

        ApiService service = new retrofit2.Retrofit.Builder()
                .baseUrl(base)
                .client(client)
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                .build()
                .create(ApiService.class);

        service.getCategoryMovies(categoryKey, getCurrentLang(), requestedPage, 24)
                .enqueue(new Callback<MovieResponse>() {
                    @Override
                    public void onResponse(Call<MovieResponse> call, Response<MovieResponse> response) {
                        loading = false;
                        if (progress != null) progress.setVisibility(View.GONE);
                        if (loadMoreProgress != null) loadMoreProgress.setVisibility(View.GONE);
                        if (isFinishing() || isDestroyed()) return;

                        List<Movie> result = null;
                        if (response.isSuccessful() && response.body() != null) {
                            result = response.body().getResults();
                        }

                        if (result == null || result.isEmpty()) {
                            if (requestedPage == 1) showEmpty();
                            hasMore = false;
                            return;
                        }

                        if (requestedPage == 1) {
                            movies.clear();
                        }
                        movies.addAll(result);
                        page = requestedPage;
                        hasMore = result.size() >= 24;
                        emptyText.setVisibility(View.GONE);
                        adapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onFailure(Call<MovieResponse> call, Throwable t) {
                        loading = false;
                        if (progress != null) progress.setVisibility(View.GONE);
                        if (loadMoreProgress != null) loadMoreProgress.setVisibility(View.GONE);
                        if (movies.isEmpty()) showEmpty();
                    }
                });
    }

    private void showEmpty() {
        if (emptyText != null) {
            emptyText.setText(getString(R.string.category_empty));
            emptyText.setVisibility(View.VISIBLE);
        }
    }

    private String getLocalizedCategoryTitle(String key) {
        String k = key == null ? "" : key.trim().toLowerCase(java.util.Locale.ROOT);
        int id = 0;
        if ("trending".equals(k)) id = R.string.trending_movies;
        else if ("action".equals(k)) id = R.string.category_action;
        else if ("latest".equals(k)) id = R.string.category_latest;
        else if ("horror".equals(k)) id = R.string.category_horror;
        else if ("scifi".equals(k) || "sci-fi".equals(k)) id = R.string.category_scifi;
        else if ("general".equals(k)) id = R.string.category_general;
        else if ("drama".equals(k)) id = R.string.category_drama;
        else if ("crime".equals(k)) id = R.string.category_crime;
        else if ("animation".equals(k)) id = R.string.category_animation;
        else if ("comedy".equals(k)) id = R.string.category_comedy;
        if (id != 0) {
            try { return getString(id); } catch (Exception ignored) { }
        }
        return key;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
