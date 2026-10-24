package com.stream.master;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.JsonParseException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Series tab. Paged grid/list from GET api/series (the server also accepts &query= for
 * the built-in search box, filtering by name in all three languages).
 *  - no series / endpoint not found (404) / unknown response shape -> friendly "no series right now" state
 *  - real network or server errors                                   -> error state with Retry
 * The list is reloaded automatically every time the tab is shown again while it is empty,
 * so it starts working the moment the server is ready. MainActivity also pre-adds this
 * fragment (hidden) right at app start so the first page is already loading in the
 * background before the user ever opens the tab.
 */
public class SeriesFragment extends BaseFragment {
    private static final int PAGE_SIZE = 24;
    private static final String VIEW_PREFS = "SeriesViewPrefs";
    private static final String KEY_LIST_MODE = "list_mode";

    private final List<Series> items = new ArrayList<>();
    private final Set<String> knownIds = new HashSet<>();
    private SeriesAdapter adapter;
    private ProgressBar progress, loadMore;
    private View stateView, retryButton;
    private TextView stateTitle, stateSub;
    private RecyclerView recycler;
    private GridLayoutManager gridLayoutManager;
    private LinearLayoutManager listLayoutManager;
    private GridSpacingItemDecoration gridDecoration;
    private ImageView viewToggle;
    private EditText searchBox;

    private int page = 1;
    private boolean loading = false;
    private boolean hasMore = true;
    private String currentQuery = "";
    private Call<SeriesResponse> activeCall;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_series, container, false);

        progress = v(R.id.seriesProgress);
        loadMore = v(R.id.seriesLoadMore);
        stateView = v(R.id.seriesState);
        stateTitle = v(R.id.seriesStateTitle);
        stateSub = v(R.id.seriesStateSub);
        retryButton = v(R.id.seriesRetry);
        viewToggle = v(R.id.seriesViewToggle);
        searchBox = v(R.id.editSeriesSearch);

        View back = v(R.id.seriesBack);
        back.setOnClickListener(x -> {
            if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).showHomeFromButton();
        });

        int span = ctx().getResources().getInteger(R.integer.page_grid_span);
        recycler = v(R.id.seriesRecyclerView);
        gridLayoutManager = new GridLayoutManager(ctx(), span);
        listLayoutManager = new LinearLayoutManager(ctx());
        gridDecoration = new GridSpacingItemDecoration(span, dp(8), dp(14), true);

        adapter = new SeriesAdapter(items, series -> {
            Intent intent = new Intent(ctx(), SeriesDetailsActivity.class);
            intent.putExtra(SeriesDetailsActivity.EXTRA_SERIES, series);
            startActivity(intent);
        });
        recycler.setAdapter(adapter);

        boolean startInListMode = ctx().getSharedPreferences(VIEW_PREFS, 0).getBoolean(KEY_LIST_MODE, false);
        applyViewMode(startInListMode);

        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0 || loading || !hasMore) return;
                RecyclerView.LayoutManager lm = rv.getLayoutManager();
                if (!(lm instanceof LinearLayoutManager)) return;
                int total = lm.getItemCount();
                int last = ((LinearLayoutManager) lm).findLastVisibleItemPosition();
                if (total > 0 && last >= total - 6) loadPage(page + 1);
            }
        });

        viewToggle.setOnClickListener(x -> applyViewMode(!adapter.isListMode()));

        searchBox.setOnEditorActionListener((tv, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                submitSearch(searchBox.getText().toString().trim());
                return true;
            }
            return false;
        });
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                // Only auto-trigger when the box goes back to empty (clearing a search);
                // typed queries are submitted explicitly via the keyboard's search action,
                // so we are not firing a request on every keystroke.
                String text = s.toString().trim();
                if (text.isEmpty() && !currentQuery.isEmpty()) submitSearch("");
            }
        });

        retryButton.setOnClickListener(x -> reload());
        loadPage(1);
        return rootView;
    }

    private void applyViewMode(boolean listMode) {
        adapter.setListMode(listMode);
        if (listMode) {
            recycler.removeItemDecoration(gridDecoration);
            recycler.setLayoutManager(listLayoutManager);
            viewToggle.setImageResource(R.drawable.ic_view_grid);
        } else {
            recycler.setLayoutManager(gridLayoutManager);
            recycler.removeItemDecoration(gridDecoration); // avoid a duplicate add if toggled twice
            recycler.addItemDecoration(gridDecoration);
            viewToggle.setImageResource(R.drawable.ic_view_list);
        }
        ctx().getSharedPreferences(VIEW_PREFS, 0).edit().putBoolean(KEY_LIST_MODE, listMode).apply();
    }

    private void submitSearch(String query) {
        if (query.equals(currentQuery)) return;
        currentQuery = query;
        reload();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        // Tab shown again and nothing loaded yet (server was not ready): try again.
        if (!hidden && items.isEmpty() && !loading) reload();
    }

    @Override
    public void onDestroyView() {
        if (activeCall != null) activeCall.cancel();
        activeCall = null;
        super.onDestroyView();
    }

    private void reload() {
        page = 1;
        hasMore = true;
        loading = false;
        knownIds.clear();
        items.clear();
        if (adapter != null) adapter.notifyDataSetChanged();
        loadPage(1);
    }

    private void loadPage(final int requestedPage) {
        if (loading || !hasMore) return;
        ApiService api = SeriesApi.create(ctx());
        if (api == null) { showState(false); return; }

        loading = true;
        final boolean first = items.isEmpty();
        stateView.setVisibility(View.GONE);
        if (first) progress.setVisibility(View.VISIBLE); else loadMore.setVisibility(View.VISIBLE);

        activeCall = currentQuery.isEmpty()
                ? api.getSeriesList(getCurrentLang(), requestedPage, PAGE_SIZE)
                : api.getSeriesList(getCurrentLang(), requestedPage, PAGE_SIZE, currentQuery);
        activeCall.enqueue(new Callback<SeriesResponse>() {
            @Override
            public void onResponse(@NonNull Call<SeriesResponse> call, @NonNull Response<SeriesResponse> response) {
                if (!isAdded()) return;
                finishLoading();

                if (!response.isSuccessful()) {
                    hasMore = false;
                    // 404 / 204 = endpoint or content not there yet -> "no series", anything else -> error
                    if (items.isEmpty()) showState(response.code() != 404 && response.code() != 204 && response.code() != 501);
                    return;
                }

                List<Series> fresh = response.body() == null ? new ArrayList<>() : response.body().getResults();
                int start = items.size();
                for (Series s : fresh) {
                    if (knownIds.add(s.getVid())) items.add(s);
                }
                int added = items.size() - start;
                if (added > 0) {
                    page = requestedPage;
                    adapter.notifyItemRangeInserted(start, added);
                } else {
                    hasMore = false; // empty page, or the server repeated the same page
                }
                if (items.isEmpty()) showState(false);
            }

            @Override
            public void onFailure(@NonNull Call<SeriesResponse> call, @NonNull Throwable t) {
                if (!isAdded() || call.isCanceled()) return;
                finishLoading();
                hasMore = false;
                boolean unknownShape = t instanceof JsonParseException || t.getCause() instanceof JsonParseException;
                if (items.isEmpty()) showState(!unknownShape);
            }
        });
    }

    private void finishLoading() {
        loading = false;
        progress.setVisibility(View.GONE);
        loadMore.setVisibility(View.GONE);
    }

    /** error=false -> "no series right now"; error=true -> "could not load" + Retry. */
    private void showState(boolean error) {
        stateTitle.setText(error ? R.string.series_load_failed : R.string.series_empty_title);
        stateSub.setText(error ? R.string.network_connection_failed : R.string.series_empty_sub);
        retryButton.setVisibility(error ? View.VISIBLE : View.GONE);
        stateView.setVisibility(View.VISIBLE);
    }
}
