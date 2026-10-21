package com.stream.master;

import android.os.Bundle;
import android.view.View;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends BaseActivity {
    private BottomNavigationView bottomNav;
    private Fragment currentFragment;
    private String searchReturnTag = "home";
    private BottomNavigationView.OnItemSelectedListener bottomNavListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bottomNav = findViewById(R.id.bottomNav);
        setupDrawer();
        setupNavigation();

        if (savedInstanceState == null) {
            showFragment(R.id.nav_home, new HomeFragment(), "home");
            prewarmSeries();
        } else {
            FragmentManager fm = getSupportFragmentManager();
            Fragment restored = fm.findFragmentByTag("home");
            Fragment categories = fm.findFragmentByTag("categories");
            Fragment favorites = fm.findFragmentByTag("favorites");
            Fragment series = fm.findFragmentByTag("series");
            Fragment search = fm.findFragmentByTag("search");
            if (search != null && search.isVisible()) currentFragment = search;
            else if (series != null && series.isVisible()) currentFragment = series;
            else if (favorites != null && favorites.isVisible()) currentFragment = favorites;
            else if (categories != null && categories.isVisible()) currentFragment = categories;
            else if (restored != null && restored.isVisible()) currentFragment = restored;
            if (currentFragment == null) showFragment(R.id.nav_home, new HomeFragment(), "home");
            syncSelectedItem();
        }
        handleIntent(getIntent());
    }

    private void setupDrawer() {
        View settings = findViewById(R.id.navSettings);
        if (settings != null) settings.setOnClickListener(v -> {
            androidx.drawerlayout.widget.DrawerLayout drawer = findViewById(R.id.drawerLayout);
            if (drawer != null) drawer.closeDrawers();
            startActivity(new android.content.Intent(this, SettingsActivity.class));
        });
        View joinRoom = findViewById(R.id.navJoinRoom);
        if (joinRoom != null) joinRoom.setOnClickListener(v -> {
            androidx.drawerlayout.widget.DrawerLayout drawer = findViewById(R.id.drawerLayout);
            if (drawer != null) drawer.closeDrawers();
            RoomJoinFlow.show(this, null);
        });
    }

    private void setupNavigation() {
        if (bottomNav == null) return;
        bottomNavListener = item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) { showFragment(id, new HomeFragment(), "home"); return true; }
            if (id == R.id.nav_categories) { showFragment(id, new CategoryFragment(), "categories"); return true; }
            if (id == R.id.nav_series) { showFragment(id, new SeriesFragment(), "series"); return true; }
            if (id == R.id.nav_list) { showFragment(id, new FavoritesFragment(), "favorites"); return true; }
            if (id == R.id.nav_menu) {
                openMenuDrawer();
                return false;
            }
            return false;
        };
        bottomNav.setOnItemSelectedListener(bottomNavListener);
    }

    /**
     * Adds the Series tab in the background, hidden, right at app start, so its first page
     * (GET api/series) is already loading/loaded by the time the user taps the Series tab
     * instead of only starting the request at that point. showFragment() below looks a
     * fragment up by tag before creating a new one, so tapping the tab later just shows
     * this same instance rather than duplicating it.
     */
    private void prewarmSeries() {
        FragmentManager fm = getSupportFragmentManager();
        if (fm.findFragmentByTag("series") != null) return;
        Fragment series = new SeriesFragment();
        fm.beginTransaction()
                .add(R.id.mainFragmentContainer, series, "series")
                .hide(series)
                .commitNowAllowingStateLoss();
    }

    private void showFragment(int navId, Fragment fragment, String state) {
        FragmentManager fm = getSupportFragmentManager();
        String tag = tagFor(navId);
        Fragment existing = fm.findFragmentByTag(tag);
        if (existing != null) fragment = existing;
        if (fragment == null) {
            if ("home".equals(tag)) fragment = new HomeFragment();
            else if ("categories".equals(tag)) fragment = new CategoryFragment();
            else if ("favorites".equals(tag)) fragment = new FavoritesFragment();
            else if ("series".equals(tag)) fragment = new SeriesFragment();
            else return;
        }

        androidx.fragment.app.FragmentTransaction tx = fm.beginTransaction()
                .setReorderingAllowed(true)
                .setCustomAnimations(R.anim.fragment_enter, R.anim.fragment_exit, R.anim.fragment_pop_enter, R.anim.fragment_pop_exit);
        if (currentFragment != null && currentFragment != fragment) tx.hide(currentFragment);
        if (!fragment.isAdded()) tx.add(R.id.mainFragmentContainer, fragment, tag);
        tx.show(fragment);

        // IMPORTANT: run visibility refresh only after the FragmentTransaction
        // has actually attached the Fragment to this Activity. Calling
        // requireActivity() before commit causes "Fragment ... not attached"
        // crashes during the initial launch.
        final Fragment targetFragment = fragment;
        tx.runOnCommit(() -> {
            if (!targetFragment.isAdded()) return;
            if (targetFragment instanceof HomeFragment) {
                ((HomeFragment) targetFragment).refreshForVisibility();
            } else if (targetFragment instanceof FavoritesFragment) {
                ((FavoritesFragment) targetFragment).refreshForVisibility();
            }
        });
        tx.commit();
        currentFragment = fragment;
        getSharedPreferences("StreamMasterNavigation", MODE_PRIVATE).edit().putString("current_primary", state).apply();
        setBottomNavSelectionSilently(navId);
    }

    /**
     * Changes the visual selection without firing OnItemSelectedListener.
     *
     * Calling setSelectedItemId() from inside the listener re-enters the
     * listener synchronously. That was causing an infinite navigation loop
     * and StackOverflowError during startup/navigation.
     */
    private void setBottomNavSelectionSilently(int navId) {
        if (bottomNav == null) return;
        if (bottomNav.getSelectedItemId() == navId) return;

        bottomNav.setOnItemSelectedListener(null);
        bottomNav.setSelectedItemId(navId);
        bottomNav.setOnItemSelectedListener(bottomNavListener);
    }

    private String tagFor(int id) {
        if (id == R.id.nav_home) return "home";
        if (id == R.id.nav_categories) return "categories";
        if (id == R.id.nav_series) return "series";
        return "favorites";
    }

    public void openMenuDrawer() {
        androidx.drawerlayout.widget.DrawerLayout drawer = findViewById(R.id.drawerLayout);
        if (drawer != null) drawer.openDrawer(androidx.core.view.GravityCompat.START);
    }

    public void openSearch() {
        FragmentManager fm = getSupportFragmentManager();
        if (!(currentFragment instanceof SearchFragment) && currentFragment != null && currentFragment.getTag() != null) {
            searchReturnTag = currentFragment.getTag();
        }
        Fragment search = fm.findFragmentByTag("search");
        if (search == null) search = new SearchFragment();
        androidx.fragment.app.FragmentTransaction tx = fm.beginTransaction()
                .setReorderingAllowed(true)
                .setCustomAnimations(R.anim.fragment_enter, R.anim.fragment_exit, R.anim.fragment_pop_enter, R.anim.fragment_pop_exit);
        if (currentFragment != null && currentFragment != search) tx.hide(currentFragment);
        if (!search.isAdded()) tx.add(R.id.mainFragmentContainer, search, "search");
        tx.show(search).commit();
        currentFragment = search;
        setBottomNavSelectionSilently(-1);
    }

    public void openCategory(String category) {
        FragmentManager fm = getSupportFragmentManager();
        Fragment categoryFragment = fm.findFragmentByTag("categories");
        if (categoryFragment == null) {
            categoryFragment = new CategoryFragment();
            Bundle args = new Bundle(); args.putString("CATEGORY_KEY", category); categoryFragment.setArguments(args);
        } else if (categoryFragment instanceof CategoryFragment) {
            ((CategoryFragment) categoryFragment).setCategoryKey(category);
        }
        androidx.fragment.app.FragmentTransaction tx = fm.beginTransaction()
                .setReorderingAllowed(true)
                .setCustomAnimations(R.anim.fragment_enter, R.anim.fragment_exit, R.anim.fragment_pop_enter, R.anim.fragment_pop_exit);
        if (currentFragment != null && currentFragment != categoryFragment) tx.hide(currentFragment);
        if (!categoryFragment.isAdded()) tx.add(R.id.mainFragmentContainer, categoryFragment, "categories");
        tx.show(categoryFragment).commit();
        currentFragment = categoryFragment;
        setBottomNavSelectionSilently(R.id.nav_categories);
        getSharedPreferences("StreamMasterNavigation", MODE_PRIVATE).edit().putString("current_primary", "categories").apply();
    }

    public void showHomeFromButton() {
        showFragment(R.id.nav_home, null, "home");
    }

    private void syncSelectedItem() {
        String s = getSharedPreferences("StreamMasterNavigation", MODE_PRIVATE).getString("current_primary", "home");
        if (bottomNav == null) return;
        if ("favorites".equals(s)) setBottomNavSelectionSilently(R.id.nav_list);
        else if ("series".equals(s)) setBottomNavSelectionSilently(R.id.nav_series);
        else if ("categories".equals(s)) setBottomNavSelectionSilently(R.id.nav_categories);
        else if ("home".equals(s)) setBottomNavSelectionSilently(R.id.nav_home);
        else setBottomNavSelectionSilently(-1);
    }

    private void handleIntent(android.content.Intent intent) {
        if (intent != null && intent.getBooleanExtra("OPEN_SEARCH", false)) {
            openSearch();
            intent.removeExtra("OPEN_SEARCH");
        }
        // Invite link (streammaster://room/CODE) forwarded by SplashActivity.
        if (intent != null && intent.hasExtra("ROOM_CODE")) {
            String code = intent.getStringExtra("ROOM_CODE");
            intent.removeExtra("ROOM_CODE");
            RoomJoinFlow.show(this, code);
        }
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    public void onBackPressed() {
        androidx.drawerlayout.widget.DrawerLayout drawer = findViewById(R.id.drawerLayout);
        if (drawer != null && drawer.isDrawerOpen(androidx.core.view.GravityCompat.START)) { drawer.closeDrawers(); return; }
        if (currentFragment instanceof SearchFragment) {
            FragmentManager fm = getSupportFragmentManager();
            Fragment target = fm.findFragmentByTag(searchReturnTag);
            if (target == null) target = fm.findFragmentByTag("home");
            if (target instanceof HomeFragment) showFragment(R.id.nav_home, target, "home");
            else if (target instanceof CategoryFragment) showFragment(R.id.nav_categories, target, "categories");
            else if (target instanceof FavoritesFragment) showFragment(R.id.nav_list, target, "favorites");
            else showHomeFromButton();
            return;
        }
        // Not on Home (Movies / Series / Favorites / Menu): first Back returns to Home.
        if (!(currentFragment instanceof HomeFragment)) {
            showHomeFromButton();
            return;
        }
        // TV: a single Back on Home leaves the app (standard remote behaviour).
        if (TvUtils.isTv(this)) { finishAffinity(); return; }
        // On Home: press Back twice within 2s to leave the app completely.
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastBackPressMs <= BACK_EXIT_WINDOW_MS) {
            finishAffinity();
            return;
        }
        lastBackPressMs = now;
        android.widget.Toast.makeText(this, R.string.press_back_again_to_exit, android.widget.Toast.LENGTH_SHORT).show();
    }

    private static final long BACK_EXIT_WINDOW_MS = 2000L;
    private long lastBackPressMs = 0L;
}
