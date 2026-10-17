package com.stream.master;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;

public class SearchActivity extends BaseActivity {

    private RecyclerView searchRecyclerView;
    private MovieAdapter movieAdapter;
    private List<Movie> searchResults = new ArrayList<>();
    private EditText editSearch;
    private BottomNavigationView bottomNav;
    private ProgressBar progressBar;
    private LinearLayout emptyStateLayout;
    private TextView tvResultHeader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        editSearch = findViewById(R.id.editSearch);
        searchRecyclerView = findViewById(R.id.searchRecyclerView);
        bottomNav = findViewById(R.id.bottomNav);
        progressBar = findViewById(R.id.progressBar);
        emptyStateLayout = findViewById(R.id.emptyStateLayout);
        tvResultHeader = findViewById(R.id.tvResultHeader);

        editSearch.requestFocus();

        // Bottom navigation is owned centrally by BaseActivity.

        searchRecyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        searchRecyclerView.addItemDecoration(
                new GridSpacingItemDecoration(3, dp(8), dp(14), true)
        );

        movieAdapter = new MovieAdapter(searchResults, (movie, sharedImageView) -> {
            Intent intent = new Intent(this, DetailsActivity.class);
            intent.putExtra(Constants.KEY_MOVIE, movie);
            startActivity(intent);
        }, this);

        searchRecyclerView.setAdapter(movieAdapter);

        String category = getIntent().getStringExtra("CATEGORY");
        String initialQuery = getIntent().getStringExtra("QUERY");

        if (category != null && !category.trim().isEmpty()) {
            // التصنيفات تستخدم /api/category وليس /api/search.
            // لا نضع اسم التصنيف داخل مربع البحث حتى لا يتم البحث عن كلمة "Action".
            editSearch.setText("");
            performCategory(category.trim());
        } else if (initialQuery != null && !initialQuery.isEmpty()) {
            editSearch.setText(initialQuery);
            performSearch(initialQuery);
        }

        editSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                String query = editSearch.getText().toString().trim();
                if (!query.isEmpty()) {
                    performSearch(query);
                }
                return true;
            }
            return false;
        });
    }

    private void performCategory(String category) {
        runOnUiThread(() -> {
            progressBar.setVisibility(View.VISIBLE);
            emptyStateLayout.setVisibility(View.GONE);
            searchRecyclerView.setVisibility(View.GONE);
            tvResultHeader.setVisibility(View.GONE);
        });

        OkHttpClient client = SecureNetworkClient.INSTANCE.getClient(this);

        ApiService apiService = new retrofit2.Retrofit.Builder()
                .baseUrl(Constants.BASE_URL.endsWith("/") ? Constants.BASE_URL : Constants.BASE_URL + "/")
                .client(client)
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                .build()
                .create(ApiService.class);

        apiService.getCategoryMovies(
                category,
                getCurrentLang(),
                1,
                40
        ).enqueue(new retrofit2.Callback<MovieResponse>() {
            @Override
            public void onResponse(retrofit2.Call<MovieResponse> call,
                                   retrofit2.Response<MovieResponse> response) {
                progressBar.setVisibility(View.GONE);

                if (response.isSuccessful() && response.body() != null) {
                    searchResults.clear();
                    List<Movie> results = response.body().getResults();

                    if (results != null) {
                        for (Movie movie : results) {
                            if (movie != null
                                    && movie.getVid() != null
                                    && !movie.getVid().isEmpty()
                                    && !"0".equals(movie.getVid())) {
                                searchResults.add(movie);
                            }
                        }
                    }

                    if (searchResults.isEmpty()) {
                        emptyStateLayout.setVisibility(View.VISIBLE);
                        searchRecyclerView.setVisibility(View.GONE);
                        tvResultHeader.setVisibility(View.GONE);
                        showCustomToast(getString(R.string.no_results));
                    } else {
                        emptyStateLayout.setVisibility(View.GONE);
                        searchRecyclerView.setVisibility(View.VISIBLE);
                        tvResultHeader.setVisibility(View.VISIBLE);
                        tvResultHeader.setText(
                                getLocalizedCategoryTitle(category)
                                        + " • "
                                        + searchResults.size()
                                );
                    }

                    movieAdapter.notifyDataSetChanged();
                } else {
                    emptyStateLayout.setVisibility(View.VISIBLE);
                    searchRecyclerView.setVisibility(View.GONE);
                    showCustomToast(getString(R.string.server_error));
                }
            }

            @Override
            public void onFailure(retrofit2.Call<MovieResponse> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                emptyStateLayout.setVisibility(View.VISIBLE);
                searchRecyclerView.setVisibility(View.GONE);
                showCustomToast(getString(R.string.server_error));
            }
        });
    }

    private String getLocalizedCategoryTitle(String key) {
        if (key == null) return "";
        String k = key.trim().toLowerCase(java.util.Locale.ROOT);
        int id = 0;
        if ("action".equals(k)) id = R.string.category_action;
        else if ("drama".equals(k)) id = R.string.category_drama;
        else if ("crime".equals(k)) id = R.string.category_crime;
        else if ("horror".equals(k)) id = R.string.category_horror;
        else if ("animation".equals(k)) id = R.string.category_animation;
        else if ("comedy".equals(k)) id = R.string.category_comedy;
        else if ("latest".equals(k)) id = R.string.category_latest;
        else if ("scifi".equals(k) || "sci-fi".equals(k)) id = R.string.category_scifi;
        else if ("general".equals(k)) id = R.string.category_general;
        else if ("trending".equals(k)) id = R.string.trending_movies;
        if (id != 0) {
            try { return getString(id); } catch (Exception ignored) {}
        }
        return key;
    }

    public void performSearch(String query) {
        runOnUiThread(() -> {
            progressBar.setVisibility(View.VISIBLE);
            emptyStateLayout.setVisibility(View.GONE);
            searchRecyclerView.setVisibility(View.GONE);
            tvResultHeader.setVisibility(View.GONE);
        });

        // ✅ تم التعديل: استخدام SecureNetworkClient بدلاً من NetworkClient
        OkHttpClient client = SecureNetworkClient.INSTANCE.getClient(this);

        ApiService apiService = new retrofit2.Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .client(client)  // ✅ استخدام العميل الآمن
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                .build()
                .create(ApiService.class);

        apiService.searchMovies(query, getCurrentLang(), 1, 20).enqueue(new retrofit2.Callback<MovieResponse>() {
            @Override
            public void onResponse(retrofit2.Call<MovieResponse> call, retrofit2.Response<MovieResponse> response) {
                progressBar.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    searchResults.clear();
                    List<Movie> results = response.body().getResults();
                    if (results != null) {
                        for (Movie movie : results) {
                            if (movie.getVid() != null && !movie.getVid().isEmpty() && !movie.getVid().equals("0")) {
                                searchResults.add(movie);
                            }
                        }
                    }

                    if (searchResults.isEmpty()) {
                        emptyStateLayout.setVisibility(View.VISIBLE);
                        searchRecyclerView.setVisibility(View.GONE);
                        tvResultHeader.setVisibility(View.GONE);
                        showCustomToast(getString(R.string.no_results));
                    } else {
                        emptyStateLayout.setVisibility(View.GONE);
                        searchRecyclerView.setVisibility(View.VISIBLE);
                        
                        tvResultHeader.setVisibility(View.VISIBLE);
                        tvResultHeader.setText(String.format(getString(R.string.search_results_format), searchResults.size(), query));
                    }
                    movieAdapter.notifyDataSetChanged();
                } else {
                    emptyStateLayout.setVisibility(View.VISIBLE);
                    showCustomToast(getString(R.string.server_error));
                }
            }

            @Override
            public void onFailure(retrofit2.Call<MovieResponse> call, Throwable t) {
                // Keep production builds silent; the UI already handles this failure.
                progressBar.setVisibility(View.GONE);
                emptyStateLayout.setVisibility(View.VISIBLE);
                showCustomToast(getString(R.string.server_error));
            }
        });
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}