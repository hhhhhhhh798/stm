package com.stream.master;

import android.content.Intent;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

public class CategoryFragment extends BaseFragment {
    private final List<Movie> movies = new ArrayList<>();
    private MovieAdapter adapter;
    private GridLayoutManager layoutManager;
    private ProgressBar progress, loadMoreProgress;
    private TextView emptyText, titleText;
    private String categoryKey = "trending";
    private int page = 1;
    private boolean loading = false, hasMore = true;
    // "Top rated" chip: same trending feed, ordered by rating on the client.
    private boolean sortByRating = false;
    private TextView chipTrending, chipTopRated, chipLatest, chipSuggested;
    private static final String[] FILTER_KEYS = {"trending", "latest", "general", "action", "scifi", "drama", "crime", "horror", "animation", "comedy"};

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_category, container, false);
        Bundle args = getArguments();
        if (args != null && args.getString("CATEGORY_KEY") != null) categoryKey = args.getString("CATEGORY_KEY").trim();
        titleText = v(R.id.categoryTitle); progress = v(R.id.categoryProgress); loadMoreProgress = v(R.id.categoryLoadMoreProgress); emptyText = v(R.id.categoryEmpty);
        ImageView back = v(R.id.categoryBack); RecyclerView recycler = v(R.id.categoryRecyclerView);
        titleText.setText(getLocalizedCategoryTitle(categoryKey));
        back.setOnClickListener(x -> { if (getActivity() instanceof MainActivity) ((MainActivity)getActivity()).showHomeFromButton(); });
        View search = v(R.id.globalSearchButton); if (search != null) search.setOnClickListener(x -> { if (getActivity() instanceof MainActivity) ((MainActivity)getActivity()).openSearch(); });
        layoutManager = new GridLayoutManager(ctx(), ctx().getResources().getInteger(R.integer.page_grid_span)); recycler.setLayoutManager(layoutManager); recycler.setHasFixedSize(true); recycler.setItemViewCacheSize(12); recycler.setClipToPadding(false);
        recycler.addItemDecoration(new GridSpacingItemDecoration(ctx().getResources().getInteger(R.integer.page_grid_span), dp(8), dp(14), true));
        adapter = new MovieAdapter(movies, (movie, sharedView) -> { Intent i = new Intent(ctx(), DetailsActivity.class); i.putExtra(Constants.KEY_MOVIE, movie); startActivity(i); }, ctx());
        adapter.setGridStyle(true);
        recycler.setAdapter(adapter);
        setupChips();
        View filter = v(R.id.categoryFilter); if (filter != null) filter.setOnClickListener(x -> showCategoryDialog());
        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() { @Override public void onScrolled(@NonNull RecyclerView rv,int dx,int dy){ super.onScrolled(rv,dx,dy); if(dy<=0||loading||!hasMore)return; int total=layoutManager.getItemCount(); int last=layoutManager.findLastVisibleItemPosition(); if(last>=total-6)loadPage(page+1); }});
        loadPage(1);
        return rootView;
    }

    public void setCategoryKey(String key) {
        String next = key == null ? "trending" : key.trim();
        if (next.isEmpty()) next = "trending";
        if (next.equalsIgnoreCase(categoryKey)) return;
        categoryKey = next; sortByRating = false; page = 1; loading = false; hasMore = true; movies.clear();
        updateChips();
        if (titleText != null) titleText.setText(getLocalizedCategoryTitle(categoryKey));
        if (adapter != null) adapter.notifyDataSetChanged();
        loadPage(1);
    }

    private void loadPage(int requestedPage) {
        if (loading || !hasMore || categoryKey.isEmpty()) return; loading=true; boolean first=movies.isEmpty(); if(first&&progress!=null)progress.setVisibility(View.VISIBLE); else if(loadMoreProgress!=null)loadMoreProgress.setVisibility(View.VISIBLE);
        OkHttpClient client=SecureNetworkClient.INSTANCE.getClient(ctx()); String base=Constants.BASE_URL; if(base==null||base.isEmpty()){loading=false;showEmpty();return;} if(!base.endsWith("/"))base+="/";
        ApiService service=new retrofit2.Retrofit.Builder().baseUrl(base).client(client).addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create()).build().create(ApiService.class);
        service.getCategoryMovies(categoryKey,getCurrentLang(),requestedPage,24).enqueue(new Callback<MovieResponse>(){
            @Override public void onResponse(Call<MovieResponse> call,Response<MovieResponse> response){ if(!isAdded())return; loading=false; if(progress!=null)progress.setVisibility(View.GONE);if(loadMoreProgress!=null)loadMoreProgress.setVisibility(View.GONE); List<Movie> result=null;if(!response.isSuccessful()){if(requestedPage==1&&movies.isEmpty())showError();hasMore=false;return;} if(response.body()!=null)result=response.body().getResults(); if(result==null||result.isEmpty()){if(requestedPage==1)showEmpty();hasMore=false;return;} if(requestedPage==1)movies.clear();movies.addAll(result);if(sortByRating)sortMoviesByRating();page=requestedPage;hasMore=result.size()>=24;emptyText.setVisibility(View.GONE);adapter.notifyDataSetChanged(); }
            @Override public void onFailure(Call<MovieResponse> call,Throwable t){loading=false;if(progress!=null)progress.setVisibility(View.GONE);if(loadMoreProgress!=null)loadMoreProgress.setVisibility(View.GONE);if(movies.isEmpty())showError();}
        });
    }
    private void setupChips() {
        chipTrending = v(R.id.chipTrending); chipTopRated = v(R.id.chipTopRated);
        chipLatest = v(R.id.chipLatest); chipSuggested = v(R.id.chipSuggested);
        if (chipTrending != null) chipTrending.setOnClickListener(x -> selectFilter("trending", false));
        if (chipTopRated != null) chipTopRated.setOnClickListener(x -> selectFilter("trending", true));
        if (chipLatest != null) chipLatest.setOnClickListener(x -> selectFilter("latest", false));
        if (chipSuggested != null) chipSuggested.setOnClickListener(x -> selectFilter("general", false));
        updateChips();
    }

    private void selectFilter(String key, boolean byRating) {
        if (key.equalsIgnoreCase(categoryKey) && byRating == sortByRating) return;
        categoryKey = key; sortByRating = byRating;
        if (titleText != null) titleText.setText(getLocalizedCategoryTitle(categoryKey));
        page = 1; loading = false; hasMore = true; movies.clear();
        if (adapter != null) adapter.notifyDataSetChanged();
        updateChips();
        loadPage(1);
    }

    private void updateChips() {
        boolean trending = "trending".equalsIgnoreCase(categoryKey);
        if (chipTrending != null) chipTrending.setSelected(trending && !sortByRating);
        if (chipTopRated != null) chipTopRated.setSelected(trending && sortByRating);
        if (chipLatest != null) chipLatest.setSelected("latest".equalsIgnoreCase(categoryKey));
        if (chipSuggested != null) chipSuggested.setSelected("general".equalsIgnoreCase(categoryKey));
    }

    private void showCategoryDialog() {
        String[] labels = new String[FILTER_KEYS.length];
        for (int i = 0; i < FILTER_KEYS.length; i++) labels[i] = getLocalizedCategoryTitle(FILTER_KEYS[i]);
        AlertDialog dialog = new AlertDialog.Builder(ctx(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.filter_by_category)
                .setItems(labels, (d, which) -> selectFilter(FILTER_KEYS[which], false))
                .create();
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                dialog.getWindow().getDecorView().setBackgroundResource(R.drawable.bg_panel);
            }
        });
        dialog.show();
    }

    private void sortMoviesByRating() {
        java.util.Collections.sort(movies, (a, b) -> Float.compare(ratingOf(b), ratingOf(a)));
    }

    private static float ratingOf(Movie m) {
        try {
            String r = m == null ? null : m.getRating();
            if (r == null) return 0f;
            return Float.parseFloat(r.trim().replace(',', '.'));
        } catch (Exception e) {
            return 0f;
        }
    }

    /** Network/server error is NOT the same as "no movies": say so and let the user tap to retry. */
    private void showError(){
        if(emptyText==null)return;
        emptyText.setText(getString(R.string.network_connection_failed)+"\n\n"+getString(R.string.retry));
        emptyText.setVisibility(View.VISIBLE);
        emptyText.setOnClickListener(v->{emptyText.setVisibility(View.GONE);emptyText.setOnClickListener(null);loading=false;hasMore=true;loadPage(1);});
    }
    private void showEmpty(){if(emptyText!=null){emptyText.setText(R.string.category_empty);emptyText.setVisibility(View.VISIBLE);}}
    private String getLocalizedCategoryTitle(String key){String k=key==null?"":key.trim().toLowerCase(java.util.Locale.ROOT);int id=0;if("trending".equals(k))id=R.string.trending_movies;else if("action".equals(k))id=R.string.category_action;else if("latest".equals(k))id=R.string.category_latest;else if("horror".equals(k))id=R.string.category_horror;else if("scifi".equals(k)||"sci-fi".equals(k))id=R.string.category_scifi;else if("general".equals(k))id=R.string.category_general;else if("drama".equals(k))id=R.string.category_drama;else if("crime".equals(k))id=R.string.category_crime;else if("animation".equals(k))id=R.string.category_animation;else if("comedy".equals(k))id=R.string.category_comedy;return id==0?key:getString(id);}
}
