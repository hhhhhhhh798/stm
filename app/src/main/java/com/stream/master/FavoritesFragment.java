package com.stream.master;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
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


import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class FavoritesFragment extends BaseFragment {

    private RecyclerView recyclerView;
    private MovieAdapter adapter;
    private List<Movie> favList = new ArrayList<>();
    private List<Movie> filteredList = new ArrayList<>();
    private TextView tvCount;
    private LinearLayout emptyStateLayout, layoutLatest;
    private CardView cardLatest;
    private ImageView ivLatestImg;
    private TextView tvLatestTitle;
    private EditText etSearchFav;
    private SharedPreferences prefs;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_favorites, container, false);

        recyclerView = v(R.id.favRecyclerView);
        tvCount = v(R.id.tvCount);
        emptyStateLayout = v(R.id.emptyStateLayout);
        layoutLatest = v(R.id.layoutLatest);
        cardLatest = v(R.id.cardLatest);
        ivLatestImg = v(R.id.ivLatestImg);
        tvLatestTitle = v(R.id.tvLatestTitle);
        etSearchFav = v(R.id.etSearchFav);
        prefs = requireActivity().getSharedPreferences("FAVS", Context.MODE_PRIVATE);
        
        ImageView btnBack = v(R.id.btnBack);
        ImageView btnSort = v(R.id.btnSort);
        ImageView btnClearAll = v(R.id.btnClearAll);
        Button btnGoHome = v(R.id.btnGoHome);

        recyclerView.setLayoutManager(new GridLayoutManager(ctx(), ctx().getResources().getInteger(R.integer.page_grid_span)));
        try {
            recyclerView.addItemDecoration(new GridSpacingItemDecoration(ctx().getResources().getInteger(R.integer.page_grid_span), dp(8), dp(14), true));
        } catch (Exception e) {
            // Keep production builds silent; the UI already handles this failure.
        }

        adapter = new MovieAdapter(filteredList, new MovieAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(Movie movie, View sharedImageView) {
                Intent intent = new Intent(requireActivity(), DetailsActivity.class);
                intent.putExtra(Constants.KEY_MOVIE, movie);

                ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                    requireActivity(), 
                    sharedImageView, 
                    movie.getVid()
                );

                startActivity(intent, options.toBundle());
            }
        }, ctx());
        
        adapter.setGridStyle(true);
        recyclerView.setAdapter(adapter);

        btnBack.setOnClickListener(v -> { if (getActivity() != null) getActivity().onBackPressed(); });

        btnGoHome.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).showHomeFromButton();
        });

        btnSort.setOnClickListener(v -> showSortDialog());

        btnClearAll.setOnClickListener(v -> {
            if (favList.isEmpty()) {
                showCustomToast(getString(R.string.list_empty_toast));
                return;
            }
            AlertDialog dialog = new AlertDialog.Builder(ctx(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
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

        loadFavorites();
        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadFavorites();
    }

    public void refreshForVisibility() {
        if (!isAdded() || getView() == null) return;
        if (etSearchFav != null) loadFavorites();
    }

    private void loadFavorites() {
        if (!isAdded() || getView() == null) return;
        favList.clear();
        final SharedPreferences prefs = requireActivity().getSharedPreferences("FAVS", Context.MODE_PRIVATE);
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
                    Intent intent = new Intent(requireActivity(), DetailsActivity.class);
                    intent.putExtra(Constants.KEY_MOVIE, latest);

                    ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                        requireActivity(), 
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
            recyclerView.setLayoutAnimation(AnimationUtils.loadLayoutAnimation(ctx(), R.anim.layout_animation_fall_down));
        }
    }

    private void showStyledDialog(AlertDialog dialog) {
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                dialog.getWindow().getDecorView().setBackgroundResource(R.drawable.bg_comic_surface);
            }
            int peach = ctx().getColor(R.color.comic_peach);
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

    protected int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    // 0 = newest first (default), 1 = oldest first, 2 = alphabetical
    private int sortMode = 0;

    /**
     * Sort dialog: title + single-choice list (radio indicator shows the current choice),
     * 56dp rows, no per-row borders, one dialog surface (Material "simple dialog" pattern).
     */
    private void showSortDialog() {
        final String[] options = {getString(R.string.sort_newest), getString(R.string.sort_oldest), getString(R.string.sort_alphabetical)};
        final int peach = ctx().getColor(R.color.comic_peach);
        final int muted = ctx().getColor(R.color.text_muted);
        final int primary = ctx().getColor(R.color.text_primary);

        android.widget.LinearLayout root = new android.widget.LinearLayout(ctx());
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(8), dp(12), dp(12));

        android.widget.TextView title = new android.widget.TextView(ctx());
        title.setText(getString(R.string.sort_title));
        title.setTextColor(primary);
        title.setTextSize(20);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
        title.setPadding(dp(12), dp(16), dp(12), dp(12));
        root.addView(title, new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT));

        final AlertDialog dialog = new AlertDialog.Builder(ctx(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setView(root).create();

        for (int i = 0; i < options.length; i++) {
            final int mode = i;
            final boolean selected = (i == sortMode);

            android.widget.LinearLayout row = new android.widget.LinearLayout(ctx());
            row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(56));
            row.setPadding(dp(12), 0, dp(12), 0);
            row.setBackgroundResource(R.drawable.bg_row_ripple);
            row.setClickable(true);
            row.setFocusable(true);

            android.widget.ImageView radio = new android.widget.ImageView(ctx());
            radio.setImageResource(selected ? R.drawable.ic_radio_checked : R.drawable.ic_radio_unchecked);
            radio.setColorFilter(selected ? peach : muted, android.graphics.PorterDuff.Mode.SRC_IN);
            row.addView(radio, new android.widget.LinearLayout.LayoutParams(dp(24), dp(24)));

            android.widget.TextView label = new android.widget.TextView(ctx());
            label.setText(options[i]);
            label.setTextColor(primary);
            label.setTextSize(16);
            label.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
            android.widget.LinearLayout.LayoutParams labelLp =
                    new android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            labelLp.setMarginStart(dp(16));
            row.addView(label, labelLp);

            row.setOnClickListener(v -> {
                dialog.dismiss();
                sortMode = mode;
                if (mode == 0) {
                    loadFavorites();
                } else if (mode == 1) {
                    Collections.reverse(favList);
                    filterFavorites(etSearchFav.getText().toString());
                } else {
                    favList.sort((m1, m2) -> m1.getTitle().compareToIgnoreCase(m2.getTitle()));
                    filterFavorites(etSearchFav.getText().toString());
                }
                showCustomToast(getString(R.string.sort_success));
            });
            root.addView(row, new android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                dialog.getWindow().getDecorView().setBackgroundResource(R.drawable.bg_dialog);
                dialog.getWindow().setDimAmount(0.6f);
            }
        });
        dialog.show();
    }

}
