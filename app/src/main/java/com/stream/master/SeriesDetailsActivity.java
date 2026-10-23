package com.stream.master;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Series page: poster, title, rating + year, description, then seasons and episodes.
 * Deliberately no cast / "more info" - a series only needs rating, description and its episodes.
 * Data: the Series that was tapped is shown immediately; api/series?vid= then fills the episodes.
 */
public class SeriesDetailsActivity extends BaseActivity {

    public static final String EXTRA_SERIES = "SERIES";

    private Series series;
    private String seriesTitle = "";
    private String seriesImage = "";
    private String seriesRating = "";
    private String seriesYear = "";

    private final List<SeriesDetailResponse.Season> seasons = new ArrayList<>();
    private final List<TextView> seasonChipViews = new ArrayList<>();
    private EpisodeAdapter episodeAdapter;
    private ProgressBar episodesProgress;
    private TextView episodesEmpty, descriptionView, readMoreView;
    private View seasonScroll;
    private LinearLayout seasonChips;
    private Call<SeriesDetailResponse> activeCall;
    private AdManager adManager;
    private boolean descriptionExpanded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_series_details);
        adManager = new AdManager(this);

        series = (Series) getIntent().getSerializableExtra(EXTRA_SERIES);
        if (series == null) { finish(); return; }

        seriesTitle = series.getTitle();
        seriesImage = series.getImage();
        seriesRating = series.getRating();
        seriesYear = series.getYear();

        findViewById(R.id.seriesDetailsBack).setOnClickListener(v -> finish());
        episodesProgress = findViewById(R.id.episodesProgress);
        episodesEmpty = findViewById(R.id.episodesEmpty);
        descriptionView = findViewById(R.id.seriesDescription);
        readMoreView = findViewById(R.id.seriesReadMore);
        seasonScroll = findViewById(R.id.seasonScroll);
        seasonChips = findViewById(R.id.seasonChips);

        RecyclerView episodes = findViewById(R.id.episodesRecyclerView);
        episodes.setLayoutManager(new LinearLayoutManager(this));
        episodeAdapter = new EpisodeAdapter(this::playEpisode);
        episodeAdapter.setFallbackImage(seriesImage);
        episodeAdapter.setSeriesVid(series.getVid());
        episodes.setAdapter(episodeAdapter);

        readMoreView.setOnClickListener(v -> {
            descriptionExpanded = !descriptionExpanded;
            descriptionView.setMaxLines(descriptionExpanded ? Integer.MAX_VALUE : 4);
            readMoreView.setText(descriptionExpanded ? R.string.read_less : R.string.read_more);
        });

        bindHeader(series.getDescription());
        loadEpisodes();
    }

    private void bindHeader(String description) {
        ((TextView) findViewById(R.id.seriesTitle)).setText(seriesTitle);

        ImageView poster = findViewById(R.id.seriesPoster);
        if (!seriesImage.isEmpty()) Glide.with(this).load(seriesImage).centerCrop().into(poster);

        TextView rating = findViewById(R.id.seriesRating);
        if (seriesRating.isEmpty()) {
            rating.setVisibility(View.GONE);
        } else {
            RatingStar.bind(rating, seriesRating);
            rating.setVisibility(View.VISIBLE);
        }
        TextView year = findViewById(R.id.seriesYear);
        year.setText(seriesYear);
        year.setVisibility(seriesYear.isEmpty() ? View.GONE : View.VISIBLE);

        boolean has = description != null && !description.isEmpty();
        descriptionView.setText(has ? description : getString(R.string.no_description));
        descriptionView.post(() -> readMoreView.setVisibility(descriptionView.getLineCount() > 4 || descriptionExpanded ? View.VISIBLE : View.GONE));
    }

    private void loadEpisodes() {
        ApiService api = SeriesApi.create(this);
        if (api == null || series.getVid().isEmpty()) { showNoEpisodes(); return; }

        episodesProgress.setVisibility(View.VISIBLE);
        activeCall = api.getSeriesDetails(series.getVid(), getCurrentLang());
        activeCall.enqueue(new Callback<SeriesDetailResponse>() {
            @Override
            public void onResponse(@NonNull Call<SeriesDetailResponse> call, @NonNull Response<SeriesDetailResponse> response) {
                if (isFinishing() || isDestroyed()) return;
                episodesProgress.setVisibility(View.GONE);
                if (!response.isSuccessful() || response.body() == null) {
                    showNoEpisodes();
                    return;
                }
                SeriesDetailResponse body = response.body();

                // Fill anything the list item did not carry.
                if (seriesImage.isEmpty()) seriesImage = body.getImage();
                if (seriesRating.isEmpty()) seriesRating = body.getRating();
                if (seriesYear.isEmpty()) seriesYear = body.getYear();
                if (seriesTitle.isEmpty()) seriesTitle = body.getTitle();
                String description = series.getDescription().isEmpty() ? body.getDescription() : series.getDescription();
                bindHeader(description);
                episodeAdapter.setFallbackImage(seriesImage);

                seasons.clear();
                seasons.addAll(body.getSeasons());
                if (seasons.isEmpty()) { showNoEpisodes(); return; }
                buildSeasonChips();
                selectSeason(0);
            }

            @Override
            public void onFailure(@NonNull Call<SeriesDetailResponse> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed() || call.isCanceled()) return;
                episodesProgress.setVisibility(View.GONE);
                showNoEpisodes();
            }
        });
    }

    private void showNoEpisodes() {
        episodesProgress.setVisibility(View.GONE);
        seasonScroll.setVisibility(View.GONE);
        episodeAdapter.submit(null);
        episodesEmpty.setVisibility(View.VISIBLE);
    }

    private void buildSeasonChips() {
        seasonChips.removeAllViews();
        seasonChipViews.clear();
        // A single season needs no selector.
        seasonScroll.setVisibility(seasons.size() > 1 ? View.VISIBLE : View.GONE);
        for (int i = 0; i < seasons.size(); i++) {
            final int index = i;
            SeriesDetailResponse.Season season = seasons.get(i);
            TextView chip = new TextView(this);
            String label = season.getTitle().isEmpty()
                    ? getString(R.string.series_season_n, season.getNumber())
                    : season.getTitle();
            chip.setText(label);
            chip.setTextSize(14);
            chip.setTypeface(chip.getTypeface(), android.graphics.Typeface.BOLD);
            chip.setTextColor(getColorStateList(R.color.chip_text));
            chip.setBackgroundResource(R.drawable.bg_filter_chip);
            chip.setGravity(Gravity.CENTER);
            chip.setSingleLine(true);
            chip.setPadding(dp(16), 0, dp(16), 0);
            chip.setClickable(true);
            chip.setFocusable(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
            lp.setMarginEnd(dp(8));
            chip.setOnClickListener(v -> selectSeason(index));
            seasonChips.addView(chip, lp);
            seasonChipViews.add(chip);
        }
    }

    private void selectSeason(int index) {
        if (index < 0 || index >= seasons.size()) return;
        for (int i = 0; i < seasonChipViews.size(); i++) seasonChipViews.get(i).setSelected(i == index);
        List<SeriesDetailResponse.Episode> list = seasons.get(index).getEpisodes();
        episodesEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        episodeAdapter.submit(list);
    }

    private void playEpisode(SeriesDetailResponse.Episode episode) {
        if (series.getVid().isEmpty() || episode.getSeasonNumber() < 1 || episode.getEpisodeNumber() < 1) {
            showCustomToast(getString(R.string.series_episode_unavailable));
            return;
        }
        adManager.trigger("details_play");

        String code = String.format(Locale.US, "S%02d\u00B7E%02d", episode.getSeasonNumber(), episode.getEpisodeNumber());
        String episodeTitle = episode.getTitle().isEmpty() ? code : code + "  " + episode.getTitle();

        // Unique per episode: the player stores the resume position under this key.
        String episodeKey = !episode.getVid().isEmpty()
                ? episode.getVid()
                : String.format(Locale.US, "%s_s%de%d", series.getVid(), episode.getSeasonNumber(), episode.getEpisodeNumber());

        Intent play = new Intent(this, PlayerActivity.class);
        play.putExtra("VID", episodeKey);
        play.putExtra("TMDB_ID", series.getVid());                    // series TMDB id: stream + subtitles use it
        play.putExtra("TITLE", seriesTitle.isEmpty() ? episodeTitle : seriesTitle + " \u00B7 " + episodeTitle);
        play.putExtra("IMG", !episode.getImage().isEmpty() ? episode.getImage() : seriesImage);
        play.putExtra("DESC", episode.getDescription());
        play.putExtra("RATING", seriesRating);
        play.putExtra("YEAR", seriesYear);
        play.putExtra("TYPE", "tv");                                  // player: api/stream?type=tv&s=&e=, no movie providers, no continue-watching
        play.putExtra("SEASON", episode.getSeasonNumber());
        play.putExtra("EPISODE", episode.getEpisodeNumber());
        startActivity(play);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The user may just have come back from watching an episode: refresh the
        // watched-so-far bar/label on every visible row instead of leaving it stale.
        if (episodeAdapter != null) episodeAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onDestroy() {
        if (activeCall != null) activeCall.cancel();
        if (adManager != null) adManager.release();
        super.onDestroy();
    }
}
