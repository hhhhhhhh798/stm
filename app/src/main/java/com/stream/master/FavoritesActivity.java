package com.stream.master;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityOptionsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class FavoritesActivity extends BaseActivity {

    private RecyclerView recyclerView;
    private MovieAdapter adapter;
    private List<Movie> favList = new ArrayList<>();
    private List<Movie> filteredList = new ArrayList<>();
    private BottomNavigationView bottomNav;
    private TextView tvCount;
    private LinearLayout emptyStateLayout, layoutLatest;
    private CardView cardLatest;
    private ImageView ivLatestImg;
    private TextView tvLatestTitle;
    private EditText etSearchFav;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
setContentView(R.layout.activity_favorites);

        recyclerView = findViewById(R.id.favRecyclerView);
        tvCount = findViewById(R.id.tvCount);
        emptyStateLayout = findViewById(R.id.emptyStateLayout);
        layoutLatest = findViewById(R.id.layoutLatest);
        cardLatest = findViewById(R.id.cardLatest);
        ivLatestImg = findViewById(R.id.ivLatestImg);
        tvLatestTitle = findViewById(R.id.tvLatestTitle);
        etSearchFav = findViewById(R.id.etSearchFav);
        prefs = getSharedPreferences("FAVS", Context.MODE_PRIVATE);
        
        ImageView btnBack = findViewById(R.id.btnBack);
        ImageView btnSort = findViewById(R.id.btnSort);
        ImageView btnClearAll = findViewById(R.id.btnClearAll);
        Button btnGoHome = findViewById(R.id.btnGoHome);

        recyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        try {
            recyclerView.addItemDecoration(new GridSpacingItemDecoration(3, dp(8), dp(14), true));
        } catch (Exception e) {
            // Keep production builds silent; the UI already handles this failure.
        }

        adapter = new MovieAdapter(filteredList, new MovieAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(Movie movie, View sharedImageView) {
                Intent intent = new Intent(FavoritesActivity.this, DetailsActivity.class);
                intent.putExtra(Constants.KEY_MOVIE, movie);

                ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                    FavoritesActivity.this, 
                    sharedImageView, 
                    movie.getVid()
                );

                startActivity(intent, options.toBundle());
            }
        }, this);
        
        recyclerView.setAdapter(adapter);

        btnBack.setOnClickListener(v -> finish());

        btnGoHome.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });

        btnSort.setOnClickListener(v -> showSortDialog());

        btnClearAll.setOnClickListener(v -> {
            if (favList.isEmpty()) {
                showCustomToast(getString(R.string.list_empty_toast));
                return;
            }
            AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(getString(R.string.delete_all_title))
                .setMessage(getString(R.string.delete_all_message))
                .setPositiveButton(getString(R.string.delete_confirm), (d, which) -> {
                    prefs.edit().clear().apply();
                    loadFavorites();
                    showCustomToast(getString(R.string.all_deleted_toast));
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .create();
            showStyledDialog(dialog);
        });

        etSearchFav.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterFavorites(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // Bottom navigation is owned centrally by BaseActivity.
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadFavorites();
    }

    private void loadFavorites() {
        favList.clear();
        final SharedPreferences prefs = getSharedPreferences("FAVS", Context.MODE_PRIVATE);
        Map<String, ?> allEntries = prefs.getAll();
        String currentLang = getCurrentLang();

        for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
            String vid = entry.getKey();
            String jsonVal = entry.getValue().toString();

            if (!vid.isEmpty() && !jsonVal.isEmpty()) {
                try {
                    JSONObject rootObj = new JSONObject(jsonVal);
                    JSONObject targetObj = null;

                    if (rootObj.has(currentLang)) {
                        targetObj = rootObj.getJSONObject(currentLang);
                    } else {
                        Iterator<String> keys = rootObj.keys();
                        if (keys.hasNext()) {
                            targetObj = rootObj.getJSONObject(keys.next());
                        }
                    }

                    if (targetObj != null) {
                        String title = targetObj.optString("title", "");
                        String image = targetObj.optString("img", "");
                        String desc = targetObj.optString("desc", getString(R.string.no_description));
                        Movie movie = new Movie(title, image, vid, desc);
                        movie.setRating(targetObj.optString("rating", ""));
                        movie.setYear(targetObj.optString("year", ""));
                        movie.setQuality(targetObj.optString("quality", ""));
                        movie.setGenre(targetObj.optString("genre", ""));
                        movie.setDirector(targetObj.optString("director", ""));
                        movie.setDuration(targetObj.optString("duration", ""));
                        movie.setLanguage(targetObj.optString("language", ""));
                        favList.add(movie);
                    }
                } catch (Exception e) {
                    String[] data = jsonVal.split("\\|");
                    String title = data.length > 0 ? data[0] : "";
                    String image = data.length > 1 ? data[1] : "";
                    String desc = data.length > 2 ? data[2] : getString(R.string.no_description);
                    favList.add(new Movie(title, image, vid, desc));
                }
            }
        }

        filterFavorites(etSearchFav.getText().toString());
    }

    private void filterFavorites(String query) {
        filteredList.clear();
        for (Movie m : favList) {
            if (m.getTitle().toLowerCase().contains(query.toLowerCase())) {
                filteredList.add(m);
            }
        }

        tvCount.setText(String.format(getString(R.string.fav_count_format), favList.size()));

        if (favList.isEmpty()) {
            emptyStateLayout.setVisibility(View.VISIBLE);
            layoutLatest.setVisibility(View.GONE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyStateLayout.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            
            if (query.isEmpty() && !favList.isEmpty()) {
                layoutLatest.setVisibility(View.VISIBLE);
                Movie latest = favList.get(favList.size() - 1);
                tvLatestTitle.setText(latest.getTitle());
                com.bumptech.glide.Glide.with(this).load(latest.getImage()).into(ivLatestImg);
                
                cardLatest.setOnClickListener(v -> {
                    Intent intent = new Intent(FavoritesActivity.this, DetailsActivity.class);
                    intent.putExtra(Constants.KEY_MOVIE, latest);

                    ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                        FavoritesActivity.this, 
                        ivLatestImg, 
                        latest.getVid()
                    );
                    startActivity(intent, options.toBundle());
                });
            } else {
                layoutLatest.setVisibility(View.GONE);
            }
        }

        if (adapter != null) {
            adapter.notifyDataSetChanged();
            recyclerView.setLayoutAnimation(AnimationUtils.loadLayoutAnimation(this, R.anim.layout_animation_fall_down));
        }
    }

    private void showStyledDialog(AlertDialog dialog) {
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                dialog.getWindow().getDecorView().setBackgroundResource(R.drawable.bg_comic_surface);
            }
            int peach = getColor(R.color.comic_peach);
            if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(peach);
            if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null) dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(peach);
            if (dialog.getListView() != null) {
                dialog.getListView().setDivider(null);
                dialog.getListView().setPadding(dp(8), dp(8), dp(8), dp(8));
                for (int i = 0; i < dialog.getListView().getChildCount(); i++) {
                    dialog.getListView().getChildAt(i).setBackgroundResource(R.drawable.dialog_item_selector);
                }
            }
        });
        dialog.show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void showSortDialog() {
        final String[] options = {getString(R.string.sort_newest), getString(R.string.sort_oldest), getString(R.string.sort_alphabetical)};
        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(getString(R.string.sort_title))
            .setItems(options, (d, which) -> {
                if (which == 0) {
                    loadFavorites();
                } else if (which == 1) {
                    Collections.reverse(favList);
                    filterFavorites(etSearchFav.getText().toString());
                } else if (which == 2) {
                    favList.sort((m1, m2) -> m1.getTitle().compareToIgnoreCase(m2.getTitle()));
                    filterFavorites(etSearchFav.getText().toString());
                }
                showCustomToast(getString(R.string.sort_success));
            })
            .create();
        showStyledDialog(dialog);
    }

}
