package com.stream.master;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

/**
 * Series list, with two interchangeable layouts sharing the same data:
 *  - grid  (default): the same poster card as Home / Movies (item_grid_movie)
 *  - list           : poster + title + rating/year + description (item_series_list)
 * Call setListMode(true/false) to switch; the fragment owns which one is active.
 */
public class SeriesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_GRID = 0;
    private static final int VIEW_LIST = 1;

    public interface OnSeriesClick { void onClick(Series series); }

    private final List<Series> items;
    private final OnSeriesClick listener;
    private boolean listMode = false;

    public SeriesAdapter(List<Series> items, OnSeriesClick listener) {
        this.items = items;
        this.listener = listener;
    }

    /** Switches the row layout for every item. Safe to call at any time; refreshes the list itself. */
    public void setListMode(boolean listMode) {
        if (this.listMode == listMode) return;
        this.listMode = listMode;
        notifyDataSetChanged();
    }

    public boolean isListMode() { return listMode; }

    @Override
    public int getItemViewType(int position) {
        return listMode ? VIEW_LIST : VIEW_GRID;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        if (viewType == VIEW_LIST) {
            return new ListHolder(LayoutInflater.from(context).inflate(R.layout.item_series_list, parent, false));
        }
        return new GridHolder(LayoutInflater.from(context).inflate(R.layout.item_grid_movie, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        final Series series = items.get(position);
        if (holder instanceof ListHolder) {
            bindList((ListHolder) holder, series);
        } else if (holder instanceof GridHolder) {
            bindGrid((GridHolder) holder, series);
        }
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(series);
        });
    }

    private void bindGrid(GridHolder holder, Series series) {
        holder.poster.setContentDescription(series.getTitle());
        loadPoster(holder.poster, series.getImage());

        String rating = series.getRating();
        holder.rating.setText(rating);
        holder.rating.setVisibility(rating.isEmpty() ? View.GONE : View.VISIBLE);

        // Series have no favourites / watch-progress in this version.
        holder.bookmark.setVisibility(View.GONE);
        holder.progress.setVisibility(View.GONE);
    }

    private void bindList(ListHolder holder, Series series) {
        holder.poster.setContentDescription(series.getTitle());
        loadPoster(holder.poster, series.getImage());

        holder.title.setText(series.getTitle());

        String rating = series.getRating();
        holder.rating.setText(rating);
        holder.rating.setVisibility(rating.isEmpty() ? View.GONE : View.VISIBLE);

        String year = series.getYear();
        holder.year.setText(year);
        holder.year.setVisibility(year.isEmpty() ? View.GONE : View.VISIBLE);

        String description = series.getDescription();
        holder.description.setText(description);
        holder.description.setVisibility(description.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void loadPoster(ImageView poster, String image) {
        if (image == null || image.isEmpty()) {
            Glide.with(poster).clear(poster);
            poster.setImageDrawable(null);
        } else {
            Glide.with(poster).load(image).centerCrop().into(poster);
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class GridHolder extends RecyclerView.ViewHolder {
        final ImageView poster, bookmark;
        final TextView rating;
        final View progress;

        GridHolder(@NonNull View itemView) {
            super(itemView);
            poster = itemView.findViewById(R.id.posterImageView);
            bookmark = itemView.findViewById(R.id.heartImageView);
            rating = itemView.findViewById(R.id.ratingTextView);
            progress = itemView.findViewById(R.id.watchProgress);
        }
    }

    static class ListHolder extends RecyclerView.ViewHolder {
        final ImageView poster;
        final TextView title, rating, year, description;

        ListHolder(@NonNull View itemView) {
            super(itemView);
            poster = itemView.findViewById(R.id.seriesListPoster);
            title = itemView.findViewById(R.id.seriesListTitle);
            rating = itemView.findViewById(R.id.seriesListRating);
            year = itemView.findViewById(R.id.seriesListYear);
            description = itemView.findViewById(R.id.seriesListDescription);
        }
    }
}
