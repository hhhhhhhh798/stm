package com.stream.master;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Episode rows for one season. Also shows how far the user got into an episode already started. */
public class EpisodeAdapter extends RecyclerView.Adapter<EpisodeAdapter.Holder> {

    public interface OnEpisodeClick { void onClick(SeriesDetailResponse.Episode episode); }

    // Must match PlayerActivity.PREF_NAME / the "pos_<key>" & "dur_<key>" keys it saves there.
    private static final String RESUME_PREF_NAME = "StreamMasterResumePrefs";

    private final List<SeriesDetailResponse.Episode> items = new ArrayList<>();
    private final OnEpisodeClick listener;
    private String fallbackImage = "";
    private String seriesVid = "";

    public EpisodeAdapter(OnEpisodeClick listener) { this.listener = listener; }

    /** Used when an episode has no still of its own. */
    public void setFallbackImage(String image) { fallbackImage = image == null ? "" : image; }

    /** Needed to rebuild an episode's resume key when the server doesn't send episode.vid (see keyFor()). */
    public void setSeriesVid(String vid) { seriesVid = vid == null ? "" : vid; }

    public void submit(List<SeriesDetailResponse.Episode> episodes) {
        items.clear();
        if (episodes != null) items.addAll(episodes);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_episode, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        final SeriesDetailResponse.Episode e = items.get(position);

        String title = e.getTitle();
        if (title.isEmpty()) title = e.getCode();
        h.title.setText(title);

        StringBuilder meta = new StringBuilder(e.getCode());
        String duration = e.getDurationLabel();
        if (!duration.isEmpty()) meta.append("  \u00B7  ").append(duration);
        String date = formatDate(e.getAirDate());
        if (!date.isEmpty()) meta.append("  \u00B7  ").append(date);
        h.meta.setText(meta.toString());

        String description = e.getDescription();
        h.description.setText(description);
        h.description.setVisibility(description.isEmpty() ? View.GONE : View.VISIBLE);

        String image = !e.getImage().isEmpty() ? e.getImage() : fallbackImage;
        if (image.isEmpty()) {
            Glide.with(h.image).clear(h.image);
            h.image.setImageDrawable(null);
        } else {
            Glide.with(h.image).load(image).centerCrop().into(h.image);
        }

        bindWatchProgress(h, e);

        h.itemView.setOnClickListener(v -> { if (listener != null) listener.onClick(e); });
    }

    /** Same key formula SeriesDetailsActivity.playEpisode() uses when it launches the player. */
    private String keyFor(SeriesDetailResponse.Episode e) {
        if (!e.getVid().isEmpty()) return e.getVid();
        if (seriesVid.isEmpty() || e.getSeasonNumber() < 1 || e.getEpisodeNumber() < 1) return "";
        return String.format(Locale.US, "%s_s%de%d", seriesVid, e.getSeasonNumber(), e.getEpisodeNumber());
    }

    private void bindWatchProgress(Holder h, SeriesDetailResponse.Episode e) {
        String key = keyFor(e);
        long position = 0L, total = 0L;
        if (!key.isEmpty()) {
            SharedPreferences prefs = h.itemView.getContext()
                    .getSharedPreferences(RESUME_PREF_NAME, Context.MODE_PRIVATE);
            position = prefs.getLong("pos_" + key, 0L);
            total = prefs.getLong("dur_" + key, 0L);
        }

        if (position > 0 && total > 0) {
            float fraction = Math.max(0f, Math.min(1f, (float) position / (float) total));
            h.progress.setVisibility(View.VISIBLE);
            h.progress.setProgress((int) (fraction * 100f));

            long watchedMin = Math.max(1, position / 60000L);
            long totalMin = Math.max(1, total / 60000L);
            h.watchedLabel.setVisibility(View.VISIBLE);
            h.watchedLabel.setText(h.itemView.getContext()
                    .getString(R.string.episode_watched_progress, watchedMin, totalMin));
        } else {
            h.progress.setVisibility(View.GONE);
            h.watchedLabel.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    /** "1999-10-20" -> localized medium date; anything unparseable is shown as the server wrote it. */
    private static String formatDate(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        try {
            Date parsed = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(raw.length() >= 10 ? raw.substring(0, 10) : raw);
            if (parsed != null) return DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(parsed);
        } catch (Exception ignored) {
            // not an ISO date
        }
        return raw;
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView title, meta, description, watchedLabel;
        final ProgressBar progress;

        Holder(@NonNull View v) {
            super(v);
            image = v.findViewById(R.id.episodeImage);
            title = v.findViewById(R.id.episodeTitle);
            meta = v.findViewById(R.id.episodeMeta);
            description = v.findViewById(R.id.episodeDescription);
            watchedLabel = v.findViewById(R.id.episodeWatchedLabel);
            progress = v.findViewById(R.id.episodeWatchProgress);
        }
    }
}
