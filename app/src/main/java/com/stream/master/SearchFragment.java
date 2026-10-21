package com.stream.master;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import com.bumptech.glide.Glide;

import okhttp3.OkHttpClient;

public class SearchFragment extends BaseFragment {
    private RecyclerView searchRecyclerView;
    private MovieAdapter movieAdapter;
    private final List<Movie> searchResults = new ArrayList<>();
    private EditText editSearch;
    private ProgressBar progressBar;
    private LinearLayout emptyStateLayout;
    private TextView tvResultHeader;
    private View discoverScroll;
    private com.google.android.material.chip.ChipGroup trendingChips;
    private LinearLayout categoriesRow;
    // Only categories that already exist in the app (same keys the API/strings use).
    private static final String[] DISCOVER_CATEGORIES = {"scifi", "action", "drama", "comedy", "crime", "horror", "animation"};

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_search, container, false);
        editSearch = v(R.id.editSearch);
        searchRecyclerView = v(R.id.searchRecyclerView);
        progressBar = v(R.id.progressBar);
        emptyStateLayout = v(R.id.emptyStateLayout);
        tvResultHeader = v(R.id.tvResultHeader);
        discoverScroll = v(R.id.discoverScroll);
        trendingChips = v(R.id.trendingChips);
        categoriesRow = v(R.id.categoriesRow);

        searchRecyclerView.setLayoutManager(new GridLayoutManager(ctx(), ctx().getResources().getInteger(R.integer.page_grid_span)));
        searchRecyclerView.addItemDecoration(new GridSpacingItemDecoration(ctx().getResources().getInteger(R.integer.page_grid_span), dp(8), dp(14), true));

        movieAdapter = new MovieAdapter(searchResults, (movie, sharedImageView) -> {
            Intent intent = new Intent(ctx(), DetailsActivity.class);
            intent.putExtra(Constants.KEY_MOVIE, movie);
            startActivity(intent);
        }, ctx());
        movieAdapter.setGridStyle(true);
        searchRecyclerView.setAdapter(movieAdapter);

        View searchBack = v(R.id.searchBack);
        if (searchBack != null) searchBack.setOnClickListener(x -> { if (getActivity() != null) getActivity().onBackPressed(); });

        View globalSearch = v(R.id.globalSearchButton);
        if (globalSearch != null) globalSearch.setOnClickListener(x -> { if (getActivity() instanceof MainActivity) ((MainActivity)getActivity()).openSearch(); });

        Bundle args = getArguments();
        String category = args == null ? null : args.getString("CATEGORY");
        String initialQuery = args == null ? null : args.getString("QUERY");
        if (category != null && !category.trim().isEmpty()) {
            editSearch.setText("");
            performCategory(category.trim());
        } else if (initialQuery != null && !initialQuery.isEmpty()) {
            editSearch.setText(initialQuery);
            performSearch(initialQuery);
        } else {
            editSearch.requestFocus();
        }

        editSearch.setOnEditorActionListener((vv, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                String query = editSearch.getText().toString().trim();
                if (!query.isEmpty()) performSearch(query);
                return true;
            }
            return false;
        });

        // Emptying the field brings back trending searches + categories.
        editSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable text) {
                if (text == null || text.toString().trim().isEmpty()) {
                    setDiscoverVisible(true);
                    searchRecyclerView.setVisibility(View.GONE);
                    tvResultHeader.setVisibility(View.GONE);
                    emptyStateLayout.setVisibility(View.GONE);
                }
            }
        });

        // Coming from a category / query: keep the results view, otherwise show discover.
        boolean hasInitial = (category != null && !category.trim().isEmpty()) || (initialQuery != null && !initialQuery.isEmpty());
        setDiscoverVisible(!hasInitial);
        loadDiscover();
        return rootView;
    }

    private void setDiscoverVisible(boolean visible) {
        if (discoverScroll != null) discoverScroll.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private ApiService discoverApi() {
        String base = Constants.BASE_URL;
        if (base == null || base.isEmpty()) return null;
        if (!base.endsWith("/")) base += "/";
        OkHttpClient client = SecureNetworkClient.INSTANCE.getClient(ctx());
        return new retrofit2.Retrofit.Builder().baseUrl(base).client(client)
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create()).build().create(ApiService.class);
    }

    /** "Trending searches" = titles of the current trending feed; categories = existing app categories. */
    private void loadDiscover() {
        if (trendingChips == null || categoriesRow == null) return;
        buildCategoryCards();
        ApiService service = discoverApi();
        if (service == null) return;
        service.getCategoryMovies("trending", getCurrentLang(), 1, 10).enqueue(new retrofit2.Callback<MovieResponse>() {
            @Override public void onResponse(retrofit2.Call<MovieResponse> call, retrofit2.Response<MovieResponse> response) {
                if (!isAdded() || !response.isSuccessful() || response.body() == null || response.body().getResults() == null) return;
                trendingChips.removeAllViews();
                int added = 0;
                for (Movie m : response.body().getResults()) {
                    if (m == null || m.getTitle() == null || m.getTitle().trim().isEmpty()) continue;
                    trendingChips.addView(makeChip(m.getTitle().trim()));
                    if (++added >= 8) break;
                }
            }
            @Override public void onFailure(retrofit2.Call<MovieResponse> call, Throwable t) {}
        });
    }

    private TextView makeChip(String text) {
        TextView chip = new TextView(ctx());
        chip.setText(text);
        chip.setTextSize(14);
        chip.setTextColor(ctx().getColorStateList(R.color.chip_text));
        chip.setBackgroundResource(R.drawable.bg_filter_chip);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(16), 0, dp(16), 0);
        chip.setSingleLine(true);
        chip.setEllipsize(android.text.TextUtils.TruncateAt.END);
        chip.setMaxWidth(dp(240));
        chip.setClickable(true);
        chip.setFocusable(true);
        chip.setLayoutParams(new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)));
        chip.setOnClickListener(x -> {
            editSearch.setText(text);
            editSearch.setSelection(text.length());
            performSearch(text);
        });
        return chip;
    }

    private void buildCategoryCards() {
        categoriesRow.removeAllViews();
        ApiService service = discoverApi();
        for (String key : DISCOVER_CATEGORIES) {
            View card = LayoutInflater.from(ctx()).inflate(R.layout.item_category_card, categoriesRow, false);
            TextView label = card.findViewById(R.id.catLabel);
            final ImageView image = card.findViewById(R.id.catImage);
            label.setText(getLocalizedCategoryTitle(key));
            card.setOnClickListener(x -> {
                editSearch.setText("");
                performCategory(key);
            });
            categoriesRow.addView(card);
            if (service != null) {
                service.getCategoryMovies(key, getCurrentLang(), 1, 1).enqueue(new retrofit2.Callback<MovieResponse>() {
                    @Override public void onResponse(retrofit2.Call<MovieResponse> call, retrofit2.Response<MovieResponse> response) {
                        if (!isAdded() || !response.isSuccessful() || response.body() == null) return;
                        java.util.List<Movie> list = response.body().getResults();
                        if (list == null || list.isEmpty() || list.get(0) == null) return;
                        String url = list.get(0).getImage();
                        if (url != null && !url.isEmpty()) Glide.with(image).load(url).centerCrop().into(image);
                    }
                    @Override public void onFailure(retrofit2.Call<MovieResponse> call, Throwable t) {}
                });
            }
        }
    }

    private void performCategory(String category) {
        setDiscoverVisible(false); progressBar.setVisibility(View.VISIBLE);
        emptyStateLayout.setVisibility(View.GONE);
        searchRecyclerView.setVisibility(View.GONE);
        tvResultHeader.setVisibility(View.GONE);
        OkHttpClient client = SecureNetworkClient.INSTANCE.getClient(ctx());
        String base = Constants.BASE_URL;
        if (base == null || base.isEmpty()) { progressBar.setVisibility(View.GONE); return; }
        if (!base.endsWith("/")) base += "/";
        ApiService apiService = new retrofit2.Retrofit.Builder().baseUrl(base).client(client)
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create()).build().create(ApiService.class);
        apiService.getCategoryMovies(category, getCurrentLang(), 1, 40).enqueue(new retrofit2.Callback<MovieResponse>() {
            @Override public void onResponse(retrofit2.Call<MovieResponse> call, retrofit2.Response<MovieResponse> response) {
                if (!isAdded()) return;
                progressBar.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    searchResults.clear();
                    List<Movie> results = response.body().getResults();
                    if (results != null) for (Movie movie : results) if (movie != null && movie.getVid() != null && !movie.getVid().isEmpty() && !"0".equals(movie.getVid())) searchResults.add(movie);
                    if (searchResults.isEmpty()) { emptyStateLayout.setVisibility(View.VISIBLE); searchRecyclerView.setVisibility(View.GONE); tvResultHeader.setVisibility(View.GONE); showCustomToast(getString(R.string.no_results)); }
                    else { emptyStateLayout.setVisibility(View.GONE); searchRecyclerView.setVisibility(View.VISIBLE); tvResultHeader.setVisibility(View.VISIBLE); tvResultHeader.setText(getLocalizedCategoryTitle(category) + " • " + searchResults.size()); }
                    movieAdapter.notifyDataSetChanged();
                } else { emptyStateLayout.setVisibility(View.VISIBLE); searchRecyclerView.setVisibility(View.GONE); showCustomToast(getString(R.string.server_error)); }
            }
            @Override public void onFailure(retrofit2.Call<MovieResponse> call, Throwable t) { if (!isAdded()) return; progressBar.setVisibility(View.GONE); emptyStateLayout.setVisibility(View.VISIBLE); searchRecyclerView.setVisibility(View.GONE); showCustomToast(getString(R.string.server_error)); }
        });
    }

    public void performSearch(String query) {
        setDiscoverVisible(false); progressBar.setVisibility(View.VISIBLE); emptyStateLayout.setVisibility(View.GONE); searchRecyclerView.setVisibility(View.GONE); tvResultHeader.setVisibility(View.GONE);
        OkHttpClient client = SecureNetworkClient.INSTANCE.getClient(ctx());
        String base = Constants.BASE_URL;
        if (base == null || base.isEmpty()) { progressBar.setVisibility(View.GONE); return; }
        if (!base.endsWith("/")) base += "/";
        ApiService apiService = new retrofit2.Retrofit.Builder().baseUrl(base).client(client)
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create()).build().create(ApiService.class);
        apiService.searchMovies(query, getCurrentLang(), 1, 20).enqueue(new retrofit2.Callback<MovieResponse>() {
            @Override public void onResponse(retrofit2.Call<MovieResponse> call, retrofit2.Response<MovieResponse> response) {
                if (!isAdded()) return;
                progressBar.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    searchResults.clear(); List<Movie> results = response.body().getResults();
                    if (results != null) for (Movie movie : results) if (movie != null && movie.getVid() != null && !movie.getVid().isEmpty() && !movie.getVid().equals("0")) searchResults.add(movie);
                    if (searchResults.isEmpty()) { emptyStateLayout.setVisibility(View.VISIBLE); searchRecyclerView.setVisibility(View.GONE); tvResultHeader.setVisibility(View.GONE); showCustomToast(getString(R.string.no_results)); }
                    else { emptyStateLayout.setVisibility(View.GONE); searchRecyclerView.setVisibility(View.VISIBLE); tvResultHeader.setVisibility(View.VISIBLE); tvResultHeader.setText(String.format(getString(R.string.search_results_format), searchResults.size(), query)); }
                    movieAdapter.notifyDataSetChanged();
                } else { emptyStateLayout.setVisibility(View.VISIBLE); showCustomToast(getString(R.string.server_error)); }
            }
            @Override public void onFailure(retrofit2.Call<MovieResponse> call, Throwable t) { if (!isAdded()) return; progressBar.setVisibility(View.GONE); emptyStateLayout.setVisibility(View.VISIBLE); showCustomToast(getString(R.string.server_error)); }
        });
    }

    private String getLocalizedCategoryTitle(String key) {
        if (key == null) return ""; String k = key.trim().toLowerCase(java.util.Locale.ROOT); int id = 0;
        if ("action".equals(k)) id=R.string.category_action; else if ("drama".equals(k)) id=R.string.category_drama; else if ("crime".equals(k)) id=R.string.category_crime; else if ("horror".equals(k)) id=R.string.category_horror; else if ("animation".equals(k)) id=R.string.category_animation; else if ("comedy".equals(k)) id=R.string.category_comedy; else if ("latest".equals(k)) id=R.string.category_latest; else if ("scifi".equals(k)||"sci-fi".equals(k)) id=R.string.category_scifi; else if ("general".equals(k)) id=R.string.category_general; else if ("trending".equals(k)) id=R.string.trending_movies;
        return id == 0 ? key : getString(id);
    }
}
