package com.stream.master;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import androidx.drawerlayout.widget.DrawerLayout;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.CompositePageTransformer;
import androidx.viewpager2.widget.MarginPageTransformer;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.AppBarLayout;

import me.relex.circleindicator.CircleIndicator3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import okhttp3.OkHttpClient;
import org.json.JSONObject;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeFragment extends BaseFragment {

    /**
     * Set by SettingsActivity.clearWatchHistory() right after it clears the resume/history
     * SharedPreferences entries. onResume() below consumes (and resets) this flag to run a
     * targeted refreshContinueWatching() - not a full home rebuild, which onResume() otherwise
     * deliberately skips (see its own comment) to preserve RecyclerView/scroll state on normal
     * back-navigation. Without this, "Continue Watching" kept showing already-cleared entries
     * until the user left Home and came back a second time.
     */
    static boolean watchHistoryClearedPending = false;

    // Registers its ActivityResultLauncher here, in onCreate() - must happen before the Fragment
    // reaches STARTED, same rule as PlayerActivity's mic-permission launcher.
    private AppUpdateManager updateManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        updateManager = new AppUpdateManager(this);
    }

    private RecyclerView mainRecyclerView;
    private ShelfAdapter shelfAdapter;
    private final List<CategoryShelf> allShelves = new ArrayList<>();

    private ViewPager2 viewPager;
    private CircleIndicator3 indicator;
    private HeroAdapter heroAdapter = null;
    private boolean isBannerInitialized = false;

    /*
     * AdManager is now process-scoped internally.
     *
     * IMPORTANT:
     * There is intentionally NO Activity-local advertisement timer here.
     *
     * The old:
     *
     *     adHandler
     *     automaticAdRunnable
     *
     * have been removed.
     *
     * AdManager itself owns the global automatic scheduler.
     */

    private final Handler sliderHandler =
            new Handler(Looper.getMainLooper());

    private AppBarLayout appBar;
    private View heroContainer = null;
    private final float[] p = {0f};
    private DrawerLayout drawerLayout;
    private final Runnable sliderRunnable = new Runnable() {
        @Override
        public void run() {

            if (viewPager == null
                    || viewPager.getAdapter() == null) {
                return;
            }

            int count =
                    viewPager.getAdapter().getItemCount();

            if (count <= 1) {
                return;
            }

            int next =
                    viewPager.getCurrentItem() + 1;

            if (next >= count) {
                next = 0;
            }

            viewPager.setCurrentItem(next, true);

            sliderHandler.postDelayed(
                    this,
                    5000
            );
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        rootView = inflater.inflate(R.layout.fragment_home, container, false);

        /*
         * AdManager starts its process-wide automatic scheduler here.
         *
         * Do NOT create another advertisement timer in onStart(),
         * onResume(), onPause(), or onStop().
         */

        Constants.loadConfig(requireContext());

        drawerLayout = requireActivity().findViewById(R.id.drawerLayout);

        View globalSearch = v(R.id.globalSearchButton);
        if (globalSearch != null) globalSearch.setOnClickListener(v -> {
            if (requireActivity() instanceof MainActivity) ((MainActivity) requireActivity()).openSearch();
        });

        View btnOpenDrawerTab =
                v(R.id.btnOpenDrawerTab);

        if (btnOpenDrawerTab != null) {

            boolean isRTL =
                    getResources()
                            .getConfiguration()
                            .getLayoutDirection()
                            == View.LAYOUT_DIRECTION_RTL;

            btnOpenDrawerTab.setScaleX(
                    isRTL ? 1f : -1f
            );

            final float[] dX = {0f};

            btnOpenDrawerTab.setOnTouchListener(
                    (v, event) -> {

                        switch (event.getAction()) {

                            case MotionEvent.ACTION_DOWN:

                                dX[0] =
                                        v.getX()
                                                - event.getRawX();

                                return true;

                            case MotionEvent.ACTION_MOVE: {

                                float nX =
                                        event.getRawX()
                                                + dX[0];

                                if (Math.abs(nX) > 50) {

                                    if (drawerLayout != null) {

                                        drawerLayout.openDrawer(
                                                androidx.core.view.GravityCompat.START
                                        );
                                    }
                                }

                                return true;
                            }

                            case MotionEvent.ACTION_UP:

                                if (drawerLayout != null) {

                                    drawerLayout.openDrawer(
                                            androidx.core.view.GravityCompat.START
                                    );
                                }

                                v.performClick();

                                return true;
                        }

                        return false;
                    }
            );
        }

        viewPager =
                v(R.id.viewPager);

        heroContainer =
                v(R.id.heroContainer);

        /*
         * ============================================================
         * HERO BANNER TRANSFORMER
         *
         * The old TranslationX was removed because it caused the
         * banner cards to visually overlap differently depending
         * on RTL/LTR language direction.
         *
         * MarginPageTransformer creates a real, fixed 8dp gap
         * between the banner cards.
         * ============================================================
         */

        CompositePageTransformer transformer =
                new CompositePageTransformer();

        transformer.addTransformer(
                new MarginPageTransformer(
                        Math.round(
                                8f
                                        * getResources()
                                        .getDisplayMetrics()
                                        .density
                        )
                )
        );

        transformer.addTransformer(
                (page, position) -> {

                    float abs =
                            Math.min(
                                    1f,
                                    Math.abs(position)
                            );

                    page.setAlpha(
                            1f - (abs * 0.14f)
                    );

                    page.setScaleY(
                            1f - (abs * 0.035f)
                    );

                    page.setScaleX(
                            1f - (abs * 0.012f)
                    );

                    page.setTranslationY(
                            abs * 5f
                    );
                }
        );

        viewPager.setPageTransformer(
                transformer
        );

        viewPager.setOverScrollMode(
                View.OVER_SCROLL_NEVER
        );

        viewPager.setOnTouchListener(
                (v, event) -> {

                    if (
                            event.getAction()
                                    == MotionEvent.ACTION_DOWN
                    ) {

                        sliderHandler.removeCallbacks(
                                sliderRunnable
                        );

                    } else if (
                            event.getAction()
                                    == MotionEvent.ACTION_UP
                                    || event.getAction()
                                    == MotionEvent.ACTION_CANCEL
                    ) {

                        sliderHandler.removeCallbacks(
                                sliderRunnable
                        );


                        sliderHandler.postDelayed(
                                sliderRunnable,
                                5000
                        );
                    }

                    return false;
                }
        );

        viewPager.registerOnPageChangeCallback(
                new ViewPager2.OnPageChangeCallback() {

                    @Override
                    public void onPageSelected(
                            int position
                    ) {

                        sliderHandler.removeCallbacks(
                                sliderRunnable
                        );

                        sliderHandler.postDelayed(
                                sliderRunnable,
                                5000
                        );
                    }
                }
        );

        indicator =
                v(R.id.indicator);

        // Bottom navigation is owned centrally by BaseActivity.

        mainRecyclerView =
                v(R.id.recyclerView);

        mainRecyclerView.setLayoutManager(
                new LinearLayoutManager(requireContext())
        );

        mainRecyclerView.setHasFixedSize(
                true
        );

        mainRecyclerView.setItemViewCacheSize(
                20
        );

        shelfAdapter =
                new ShelfAdapter(
                        allShelves,
                        requireContext(),
                        new ShelfAdapter.OnShelfItemClickListener() {

                            @Override
                            public void onMovieClick(
                                    Movie movie,
                                    View sharedImageView
                            ) {

                                Intent intent =
                                        new Intent(
                                                requireContext(),
                                                DetailsActivity.class
                                        );

                                intent.putExtra(
                                        Constants.KEY_MOVIE,
                                        movie
                                );

                                startActivity(intent);
                            }

                            @Override
                            public void onCategoryClick(
                                    String categoryName
                            ) {

                                if (
                                        categoryName == null
                                                || categoryName
                                                .trim()
                                                .isEmpty()
                                                || "continue_watching"
                                                .equalsIgnoreCase(
                                                        categoryName
                                                )
                                ) {

                                    return;
                                }

                                if (requireActivity() instanceof MainActivity) {
                                    ((MainActivity) requireActivity()).openCategory(categoryName.trim());
                                }
                            }
                        },

                        shelf -> {

                            shelf.setCurrentPage(
                                    shelf.getCurrentPage() + 1
                            );

                            loadCategoryPagination(
                                    shelf.getTitle(),
                                    shelf.getCurrentPage()
                            );
                        }
                );

        mainRecyclerView.setAdapter(
                shelfAdapter
        );

        // Build the real Home structure immediately. The card/hero layouts are
        // the same ones used for live content; only remote data is left empty.
        initializeHomeSkeleton();

        homeAttempt = 0;
        homeShelvesLoaded = false;
        startHomeLoad();

        // GitHub/update URLs are now runtime configuration from /api/config.
        // Wait for the authenticated config attempt before building Retrofit.
        RemoteConfigManager.refresh(requireContext(), this::checkForUpdates);

        // The STREAM MASTER logo fades out as the home list scrolls (hero collapses).
        // Only logoText fades; the search button is a separate view and always stays.
        final View logo = v(R.id.logoText);
        appBar = v(R.id.appBar);
        if (logo != null && appBar != null) {
            appBar.addOnOffsetChangedListener((layout, verticalOffset) -> {
                int range = layout.getTotalScrollRange();
                if (range <= 0) return;
                float fadeDistance = range * 0.35f; // fully faded after 35% of the hero collapse
                logo.setAlpha(1f - Math.min(1f, Math.abs(verticalOffset) / fadeDistance));
            });
        }

        return rootView;
    }

    @Override
    public void onPause() {

        super.onPause();

        /*
         * Only the banner slider belongs to MainActivity.
         *
         * Advertisement scheduling is NOT touched here.
         */

        sliderHandler.removeCallbacks(
                sliderRunnable
        );
    }

    @Override
    public void onResume() {

        super.onResume();

        if (watchHistoryClearedPending) {
            watchHistoryClearedPending = false;
            refreshForVisibility();
        }

        // Do not rebuild Home on every onResume. The Activity is kept alive by
        // REORDER_TO_FRONT and retains its RecyclerView/scroll state.
        if (viewPager != null
                && viewPager.getAdapter() != null) {

            sliderHandler.postDelayed(
                    sliderRunnable,
                    5000
            );
        }
    }

    private String getFormattedBaseUrl() {

        String url =
                Constants.BASE_URL;

        if (url != null
                && !url.endsWith("/")) {

            url += "/";
        }

        return url;
    }

    private void initializeHomeSkeleton() {
        // Hero: use the actual hero item so its card, border and buttons are
        // already visible before the network returns.
        Movie heroPlaceholder = new Movie();
        heroPlaceholder.setSkeletonPlaceholder(true);
        List<Movie> heroPlaceholders = new ArrayList<>();
        heroPlaceholders.add(heroPlaceholder);

        heroAdapter = new HeroAdapter(requireContext(), heroPlaceholders);
        viewPager.setAdapter(heroAdapter);
        viewPager.setOffscreenPageLimit(1);

        if (indicator != null) {
            // There is no meaningful slide count until the real banner arrives.
            indicator.setVisibility(View.GONE);
        }

        // Shelves: show the category names that Home already supports and the
        // same real poster-card frames used by MovieAdapter. Five blank cards
        // are enough to establish the final horizontal geometry without fake
        // movie metadata.
        String[] preferredOrder = {
                "trending",
                "action",
                "drama",
                "crime",
                "animation",
                "comedy"
        };

        allShelves.clear();
        for (String category : preferredOrder) {
            List<Movie> placeholders = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                Movie placeholder = new Movie();
                placeholder.setSkeletonPlaceholder(true);
                placeholders.add(placeholder);
            }
            allShelves.add(new CategoryShelf(category, placeholders));
        }

        if (shelfAdapter != null) {
            shelfAdapter.notifyDataSetChanged();
        }
    }

    // ---- Home loading with automatic retry -------------------------------------------
    // The hero banner and shelves used to be requested exactly once. Any failure (token not
    // ready, 401/429/503, timeout) was silently ignored, so the skeleton stayed forever.
    private static final int HOME_MAX_RETRIES = 4;
    private int homeAttempt = 0;
    private boolean homeShelvesLoaded = false;

    private final Runnable homeWatchdog = () -> {
        if (!isAdded()) return;
        if (isBannerInitialized && homeShelvesLoaded) return;
        if (++homeAttempt > HOME_MAX_RETRIES) return;
        startHomeLoad();
    };

    private void startHomeLoad() {
        if (!isAdded()) return;
        // Check again later; cancelled implicitly once both parts have loaded.
        sliderHandler.removeCallbacks(homeWatchdog);
        sliderHandler.postDelayed(homeWatchdog, 5000L + 3000L * homeAttempt);

        SecureNetworkClient.ensureTokenAvailable(
                requireContext(),
                isSuccess -> {
                    if (!isAdded() || getActivity() == null) return;
                    getActivity().runOnUiThread(() -> {
                        if (!isAdded()) return;
                        if (isSuccess) {
                            loadUnifiedHomeData();
                        } else if (homeAttempt == 0) {
                            showCustomToast(getString(R.string.network_connection_failed));
                        }
                    });
                }
        );
    }

    private void loadUnifiedHomeData() {

        OkHttpClient client =
                SecureNetworkClient.getClient(requireContext());

        ApiService apiService =
                new retrofit2.Retrofit.Builder()
                        .baseUrl(
                                getFormattedBaseUrl()
                        )
                        .client(client)
                        .addConverterFactory(
                                retrofit2.converter.gson
                                        .GsonConverterFactory
                                        .create()
                        )
                        .build()
                        .create(ApiService.class);

        String currentLang =
                getCurrentLang();

        apiService.getBannerMovies(
                currentLang,
                1,
                10
        ).enqueue(
                new Callback<MovieResponse>() {

                    @Override
                    public void onResponse(
                            Call<MovieResponse> call,
                            Response<MovieResponse> response
                    ) {

                        if (!isAdded()
                                ) {
                            return;
                        }

                        if (response.isSuccessful()
                                && response.body() != null) {

                            List<Movie> bannerMovies =
                                    response.body()
                                            .getResults();

                            if (bannerMovies != null
                                    && !bannerMovies.isEmpty()) {

                                requireActivity().runOnUiThread(() -> {

                                    isBannerInitialized =
                                            true;

                                    if (heroAdapter == null) {
                                        heroAdapter = new HeroAdapter(
                                                requireContext(),
                                                bannerMovies
                                        );
                                        viewPager.setAdapter(heroAdapter);
                                    } else {
                                        heroAdapter.setMovies(bannerMovies);
                                    }

                                    if (indicator != null) {
                                        indicator.setVisibility(View.VISIBLE);
                                        indicator.setViewPager(viewPager);
                                    }

                                    viewPager.setOffscreenPageLimit(3);

                                    sliderHandler.removeCallbacks(sliderRunnable);
                                    sliderHandler
                                            .postDelayed(
                                                    sliderRunnable,
                                                    5000
                                            );
                                });
                            }
                        }
                    }

                    @Override
                    public void onFailure(
                            Call<MovieResponse> call,
                            Throwable t
                    ) {
                    }
                }
        );

        apiService.getHomeMovies(
                currentLang,
                10
        ).enqueue(
                new Callback<HomeResponse>() {

                    @Override
                    public void onResponse(
                            Call<HomeResponse> call,
                            Response<HomeResponse> response
                    ) {

                        if (!isAdded()
                                ) {
                            return;
                        }

                        if (response.isSuccessful()
                                && response.body() != null) {

                            HomeResponse homeResponse =
                                    response.body();

                            Map<String, List<Movie>> homeData =
                                    homeResponse.getData();

                            if (homeData != null
                                    && !homeData.isEmpty()) {

                                homeShelvesLoaded = true;

                                requireActivity().runOnUiThread(() -> {

                                    // Skeleton placeholders occupy allShelves right now (see
                                    // initializeHomeSkeleton()). Remember how many so the real
                                    // data below can replace them row-by-row instead of the old
                                    // clear()+notifyDataSetChanged() combo, which told the
                                    // RecyclerView "everything changed" and made the whole page
                                    // blink/reload instead of each shelf just filling in.
                                    int previousShelfCount = allShelves.size();

                                    allShelves.clear();

                                    addContinueWatchingShelf();

                                    String[] preferredOrder = {
                                            "trending",
                                            "action",
                                            "drama",
                                            "crime",
                                            "animation",
                                            "comedy"
                                    };

                                    for (
                                            String cat
                                            : preferredOrder
                                    ) {

                                        List<Movie> movies =
                                                homeData.get(cat);

                                        if (
                                                movies != null
                                                        && !movies.isEmpty()
                                        ) {

                                            allShelves.add(
                                                    new CategoryShelf(
                                                            cat,
                                                            new ArrayList<>(
                                                                    movies
                                                            )
                                                    )
                                            );
                                        }
                                    }

                                    for (
                                            Map.Entry<
                                                    String,
                                                    List<Movie>
                                                    > entry
                                            : homeData.entrySet()
                                    ) {

                                        String catKey =
                                                entry.getKey();

                                        boolean exists =
                                                false;

                                        for (
                                                String pCat
                                                : preferredOrder
                                        ) {

                                            if (
                                                    pCat.equalsIgnoreCase(
                                                            catKey
                                                    )
                                            ) {

                                                exists = true;
                                                break;
                                            }
                                        }

                                        if (
                                                !exists
                                                        && entry.getValue() != null
                                                        && !entry.getValue()
                                                        .isEmpty()
                                        ) {

                                            allShelves.add(
                                                    new CategoryShelf(
                                                            catKey,
                                                            new ArrayList<>(
                                                                    entry.getValue()
                                                            )
                                                    )
                                            );
                                        }
                                    }

                                    int newShelfCount = allShelves.size();
                                    int changed = Math.min(previousShelfCount, newShelfCount);
                                    if (changed > 0) shelfAdapter.notifyItemRangeChanged(0, changed);
                                    if (newShelfCount > previousShelfCount) {
                                        shelfAdapter.notifyItemRangeInserted(previousShelfCount, newShelfCount - previousShelfCount);
                                    } else if (newShelfCount < previousShelfCount) {
                                        shelfAdapter.notifyItemRangeRemoved(newShelfCount, previousShelfCount - newShelfCount);
                                    }

                                });
                            }
                        }
                    }

                    @Override
                    public void onFailure(
                            Call<HomeResponse> call,
                            Throwable t
                    ) {
                    }
                }
        );
    }

    public void refreshForVisibility() {
        // This method can be called from a FragmentTransaction callback.
        // Never touch requireActivity() while the Fragment is detached.
        if (!isAdded() || getView() == null) return;
        refreshContinueWatching();
    }

    private void refreshContinueWatching() {

        if (!isAdded() || getView() == null) return;

        for (
                int i = allShelves.size() - 1;
                i >= 0;
                i--
        ) {

            if (
                    "continue_watching"
                            .equalsIgnoreCase(
                                    allShelves
                                            .get(i)
                                            .getTitle()
                            )
            ) {

                allShelves.remove(i);
            }
        }

        addContinueWatchingShelf();

        if (shelfAdapter != null) {

            shelfAdapter.notifyDataSetChanged();
        }
    }

    private void addContinueWatchingShelf() {

        SharedPreferences prefs =
                requireActivity().getSharedPreferences(
                        "StreamMasterResumePrefs",
                        android.content.Context.MODE_PRIVATE
                );

        List<JSONObject> history =
                new ArrayList<>();

        for (
                Map.Entry<String, ?> entry
                : prefs.getAll().entrySet()
        ) {

            String key =
                    entry.getKey();

            if (
                    key == null
                            || !key.startsWith("history_")
            ) {

                continue;
            }

            try {

                JSONObject obj =
                        new JSONObject(
                                String.valueOf(
                                        entry.getValue()
                                )
                        );

                String vid =
                        obj.optString(
                                "vid",
                                ""
                        );

                long savedPosition =
                        prefs.getLong(
                                "pos_" + vid,
                                0L
                        );

                long recordedPosition =
                        obj.optLong(
                                "positionMs",
                                0L
                        );

                if (
                        !vid.isEmpty()
                                && Math.max(
                                savedPosition,
                                recordedPosition
                        ) > 1000L
                ) {

                    history.add(obj);
                }

            } catch (Exception ignored) {
            }
        }

        history.sort(
                (a, b) ->
                        Long.compare(
                                b.optLong(
                                        "updatedAt",
                                        0L
                                ),
                                a.optLong(
                                        "updatedAt",
                                        0L
                                )
                        )
        );

        List<Movie> movies =
                new ArrayList<>();

        for (
                int i = 0;
                i < Math.min(
                        10,
                        history.size()
                );
                i++
        ) {

            JSONObject o =
                    history.get(i);

            Movie m =
                    new Movie(
                            o.optString(
                                    "title",
                                    ""
                            ),
                            o.optString(
                                    "img",
                                    ""
                            ),
                            o.optString(
                                    "vid",
                                    ""
                            ),
                            o.optString(
                                    "desc",
                                    ""
                            )
                    );

            m.setRating(
                    o.optString(
                            "rating",
                            ""
                    )
            );

            m.setYear(
                    o.optString(
                            "year",
                            ""
                    )
            );

            m.setQuality(
                    o.optString(
                            "quality",
                            ""
                    )
            );

            m.setGenre(
                    o.optString(
                            "genre",
                            ""
                    )
            );

            m.setDirector(
                    o.optString(
                            "director",
                            ""
                    )
            );

            m.setDuration(
                    o.optString(
                            "duration",
                            ""
                    )
            );

            m.setLanguage(
                    o.optString(
                            "language",
                            ""
                    )
            );

            long position =
                    prefs.getLong(
                            "pos_" + m.getVid(),
                            0L
                    );

            long duration =
                    o.optLong(
                            "durationMs",
                            0L
                    );

            if (duration > 0) {

                m.setWatchProgress(
                        (float) position
                                / (float) duration
                );
            }

            movies.add(m);
        }

        if (!movies.isEmpty()) {

            allShelves.add(
                    0,
                    new CategoryShelf(
                            "continue_watching",
                            movies
                    )
            );
        }
    }

    private void loadCategoryPagination(
            String cat,
            int page
    ) {

        OkHttpClient client =
                SecureNetworkClient.getClient(requireContext());

        ApiService apiService =
                new retrofit2.Retrofit.Builder()
                        .baseUrl(
                                getFormattedBaseUrl()
                        )
                        .client(client)
                        .addConverterFactory(
                                retrofit2.converter.gson
                                        .GsonConverterFactory
                                        .create()
                        )
                        .build()
                        .create(ApiService.class);

        apiService.getCategoryMovies(
                cat,
                getCurrentLang(),
                page,
                15
        ).enqueue(
                new Callback<MovieResponse>() {

                    @Override
                    public void onResponse(
                            Call<MovieResponse> call,
                            Response<MovieResponse> response
                    ) {

                        if (
                                !isAdded()
                                        
                        ) {

                            return;
                        }

                        if (
                                response.isSuccessful()
                                        && response.body() != null
                        ) {

                            List<Movie> movies =
                                    response.body()
                                            .getResults();

                            if (
                                    movies != null
                                            && !movies.isEmpty()
                            ) {

                                requireActivity().runOnUiThread(() -> {

                                    for (
                                            CategoryShelf s
                                            : allShelves
                                    ) {

                                        if (
                                                s.getTitle()
                                                        .equalsIgnoreCase(
                                                                cat
                                                        )
                                        ) {

                                            s.getMovies()
                                                    .addAll(
                                                            movies
                                                    );

                                            shelfAdapter
                                                    .notifyDataSetChanged();

                                            break;
                                        }
                                    }
                                });
                            }
                        }
                    }

                    @Override
                    public void onFailure(
                            Call<MovieResponse> call,
                            Throwable t
                    ) {
                    }
                }
        );
    }

    private void checkForUpdates() {

        int installedVersion = 1;

        try {
            PackageInfo pInfo =
                    requireActivity().getPackageManager()
                            .getPackageInfo(
                                    requireActivity().getPackageName(),
                                    0
                            );
            installedVersion = pInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return;
        }

        final int currentVersion = installedVersion;

        // Prefer the authenticated server-provided URL. Fall back to the
        // native GitHub URL only for compatibility with older deployments.
        String updateUrl = RemoteConfigManager.getString(
                "update_json_url",
                Constants.GITHUB_UPDATE_JSON
        );

        if (!isValidHttpUrl(updateUrl)) {
            return;
        }

        String baseForRetrofit = updateUrl;
        int slash = baseForRetrofit.lastIndexOf('/');
        if (slash >= 0) {
            baseForRetrofit = baseForRetrofit.substring(0, slash + 1);
        }
        if (!isValidHttpUrl(baseForRetrofit)) {
            return;
        }

        OkHttpClient customClient =
                new OkHttpClient.Builder()
                        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                        .retryOnConnectionFailure(true)
                        .build();

        ApiService apiService =
                new retrofit2.Retrofit.Builder()
                        .baseUrl(baseForRetrofit)
                        .client(customClient)
                        .addConverterFactory(
                                retrofit2.converter.gson
                                        .GsonConverterFactory
                                        .create()
                        )
                        .build()
                        .create(ApiService.class);

        apiService.checkUpdateAt(updateUrl).enqueue(
                new Callback<UpdateInfo>() {

                    @Override
                    public void onResponse(
                            Call<UpdateInfo> call,
                            Response<UpdateInfo> response
                    ) {
                        if (!isAdded()) return;

                        if (response.body() != null) {
                            UpdateInfo info = response.body();
                            if (info.latest_version_code > currentVersion) {
                                String lang = java.util.Locale
                                        .getDefault()
                                        .getLanguage();
                                String message = info.getMessage(lang);
                                requireActivity().runOnUiThread(() ->
                                        showUpdateDialog(message, info)
                                );
                            }
                        }
                    }

                    @Override
                    public void onFailure(
                            Call<UpdateInfo> call,
                            Throwable t
                    ) {
                        // Update checking must never crash the Home screen.
                    }
                }
        );
    }

    private static boolean isValidHttpUrl(String value) {
        if (value == null || value.trim().isEmpty()) return false;
        try {
            java.net.URI uri = new java.net.URI(value.trim());
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) ||
                    "https".equalsIgnoreCase(scheme)) &&
                    uri.getHost() != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void showUpdateDialog(
            String message,
            UpdateInfo info
    ) {

        if (
                !isAdded()
                        
        ) {

            return;
        }

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.update_available_title))
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(getString(R.string.update_now), (d, which) -> {
                    // Prefer the in-app download+install flow (see AppUpdateManager); only fall
                    // back to opening Telegram if update.json is missing/malformed apk_url/sha256
                    // (e.g. an older release published before this feature existed).
                    if (info.hasInAppUpdate() && updateManager != null) {
                        updateManager.start(info);
                    } else if (isValidHttpUrl(info.telegram_url)) {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(info.telegram_url));
                        startActivity(intent);
                    }
                })
                .setNegativeButton(getString(R.string.update_later), (d, which) -> d.dismiss())
                .create();
        showStyledDialog(dialog);
    }

    private void showStyledDialog(AlertDialog dialog) {
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                dialog.getWindow().getDecorView().setBackgroundResource(R.drawable.bg_comic_surface);
            }
            int peach = requireContext().getColor(R.color.comic_peach);
            if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(peach);
            if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null) dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(peach);
        });
        dialog.show();
    }

    private String getCategoryResourceName(
            String key
    ) {

        if (key == null) {

            return "";
        }

        int resId = 0;

        switch (
                key.toLowerCase()
        ) {

            case "latest":

                resId =
                        R.string.category_latest;

                break;

            case "action":

                resId =
                        R.string.category_action;

                break;

            case "horror":

                resId =
                        R.string.category_horror;

                break;

            case "scifi":
            case "sci-fi":

                resId =
                        R.string.category_scifi;

                break;

            case "general":

                resId =
                        R.string.category_general;

                break;

            case "trending":

                resId =
                        R.string.trending_movies;

                break;
        }

        if (resId != 0) {

            try {

                return getString(resId);

            } catch (Exception e) {

                return key;
            }
        }

        return key;
    }

    /*
     * ============================================================
     * IMPORTANT:
     *
     * There are intentionally NO advertisement lifecycle methods
     * here anymore.
     *
     * Removed:
     *
     * onStart() -> Activity-local ad timer
     * onStop()  -> cancel Activity-local ad timer
     * onDestroy() -> release AdManager
     *
     * AdManager now owns the automatic scheduler for the whole
     * application process.
     *
     * This prevents:
     *
     * MainActivity
     *      ↓
     * DetailsActivity
     *      ↓
     * PlayerActivity
     *
     * from cancelling or restarting the advertisement scheduler.
     * ============================================================
     */

    @Override
    public void onDestroy() {

        /*
         * Only clean resources that belong specifically to
         * MainActivity.
         *
         * Do NOT call adManager.release().
         */

        sliderHandler.removeCallbacksAndMessages(
                null
        );

        if (updateManager != null) {
            updateManager.cancel();
            updateManager = null;
        }

        super.onDestroy();
    }
}
