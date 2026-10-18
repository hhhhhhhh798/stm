package com.stream.master;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ProgressBar;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.GridLayoutManager;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import java.util.List;
import org.json.JSONObject;

public class MovieAdapter extends RecyclerView.Adapter<MovieAdapter.ViewHolder> {
    private List<Movie> movieList;
    private OnItemClickListener listener;
    private SharedPreferences prefs;
    private Context context;
    // Page grids (Movies / Favorites / Search): same design as the Home card, width fills the grid cell (2:3).
    private boolean gridStyle = false;

    public MovieAdapter setGridStyle(boolean gridStyle) {
        this.gridStyle = gridStyle;
        return this;
    }

    public interface OnItemClickListener { 
        void onItemClick(Movie movie, View view); 
    }

    public MovieAdapter(List<Movie> movieList, OnItemClickListener listener, Context context) {
        this.movieList = movieList;
        this.listener = listener;
        this.context = context;
        this.prefs = context.getSharedPreferences("FAVS", Context.MODE_PRIVATE);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutId = gridStyle
                ? R.layout.item_grid_movie
                : ((context instanceof MainActivity)
                    ? R.layout.item_home_movie
                    : R.layout.item_movie);
        View view = LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false);
        if (gridStyle) {
            // The grid card is sized by the GridLayoutManager span (width) and by
            // PosterFrameLayout (2:3 poster), so no fixed dp size is forced here.
            return new ViewHolder(view);
        }

        // The shared card is used by horizontal shelves and every grid.
        // In grids, derive BOTH dimensions from the available width so the
        // poster always stays exactly 2:3 and never overflows small screens.
        if (parent instanceof RecyclerView
                && ((RecyclerView) parent).getLayoutManager() instanceof GridLayoutManager) {
            GridLayoutManager grid = (GridLayoutManager) ((RecyclerView) parent).getLayoutManager();
            int spanCount = Math.max(1, grid.getSpanCount());
            // Use the same compact poster geometry across every grid.
            // Never stretch a card to fill a grid cell.
            ViewGroup.LayoutParams lp = view.getLayoutParams();
            lp.width = parent.getResources().getDimensionPixelSize(R.dimen.movie_card_width);
            lp.height = parent.getResources().getDimensionPixelSize(R.dimen.movie_card_height);
            view.setLayoutParams(lp);
        } else {
            ViewGroup.LayoutParams lp = view.getLayoutParams();
            if (context instanceof MainActivity) {
                float density = parent.getResources().getDisplayMetrics().density;
                lp.width = Math.round(100f * density);
                lp.height = Math.round(150f * density);
            } else {
                lp.width = parent.getResources().getDimensionPixelSize(R.dimen.movie_card_width);
                lp.height = parent.getResources().getDimensionPixelSize(R.dimen.movie_card_height);
            }
            view.setLayoutParams(lp);
        }
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Movie movie = movieList.get(position);
        
        if (movie == null) return;

        // The Home screen uses the real movie-card layout even before the API
        // responds. A skeleton card therefore keeps its actual border/shape
        // but deliberately contains no fake title, rating, image or favorite
        // state. Those elements appear only when the real Movie arrives.
        if (movie.isSkeletonPlaceholder()) {
            holder.itemView.animate().cancel();
            holder.itemView.setAlpha(1f);
            holder.itemView.setScaleX(1f);
            holder.itemView.setScaleY(1f);

            if (holder.titleTextView != null) {
                holder.titleTextView.setText("");
                holder.titleTextView.setVisibility(View.GONE);
            }
            if (holder.ratingTextView != null) {
                holder.ratingTextView.setText("");
                holder.ratingTextView.setVisibility(View.GONE);
            }
            if (holder.heartImageView != null) {
                holder.heartImageView.setVisibility(View.GONE);
                holder.heartImageView.setOnClickListener(null);
            }
            if (holder.watchProgress != null) {
                holder.watchProgress.setVisibility(View.GONE);
            }
            if (holder.imageView != null) {
                Glide.with(context).clear(holder.imageView);
                holder.imageView.setImageDrawable(null);
            }
            holder.itemView.setOnClickListener(null);
            holder.itemView.setClickable(false);
            return;
        }

        // Reset all loading-only state because RecyclerView reuses holders.
        holder.itemView.setClickable(true);
        if (holder.titleTextView != null) holder.titleTextView.setVisibility(View.VISIBLE);
        if (holder.heartImageView != null) holder.heartImageView.setVisibility(View.VISIBLE);

        String vid = (movie.getVid() != null) ? movie.getVid() : "";

        holder.itemView.setAlpha(0f);
        holder.itemView.setScaleX(0.95f);
        holder.itemView.setScaleY(0.95f);
        holder.itemView.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(180)
                .start();

        String title = (movie.getTitle() != null) ? movie.getTitle() : "فيلم بدون عنوان";
        if (holder.titleTextView != null) holder.titleTextView.setText(title);

        if (holder.watchProgress != null) {
            if (movie.getWatchProgress() > 0f) {
                holder.watchProgress.setVisibility(View.VISIBLE);
                holder.watchProgress.setProgress((int)(movie.getWatchProgress() * 100f));
            } else {
                holder.watchProgress.setVisibility(View.GONE);
            }
        }

        String displayRating = movie.getDisplayRating();
        if (displayRating.isEmpty()) {
            holder.ratingTextView.setVisibility(View.GONE);
        } else {
            holder.ratingTextView.setVisibility(View.VISIBLE);
            holder.ratingTextView.setText(displayRating);
        }

        if (movie.getImage() != null && !movie.getImage().isEmpty()) {
            Glide.with(context)
                .load(movie.getImage())
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .centerCrop()
                .into(holder.imageView);
        } else {
            holder.imageView.setImageResource(android.R.drawable.ic_menu_gallery);
        }

        boolean isFav = prefs.contains(vid);
        if (isFav) {
            holder.heartImageView.setImageResource(R.drawable.ic_bookmark_filled);
            holder.heartImageView.clearColorFilter();
        } else {
            holder.heartImageView.setImageResource(R.drawable.ic_bookmark);
            holder.heartImageView.setColorFilter(
                    androidx.core.content.ContextCompat.getColor(context, R.color.text_primary),
                    android.graphics.PorterDuff.Mode.SRC_IN);
        }

        holder.itemView.setOnClickListener(v -> {
            v.animate()
                .scaleX(0.96f)
                .scaleY(0.96f)
                .setDuration(80)
                .withEndAction(() -> {
                    v.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(80)
                        .withEndAction(() -> {
                            if (listener != null) {
                                listener.onItemClick(movie, v);
                            }
                        })
                        .start();
                })
                .start();
        });
        
        holder.heartImageView.setOnClickListener(v -> {
            if (vid.isEmpty()) return;
            
            SharedPreferences.Editor editor = prefs.edit();
            if (prefs.contains(vid)) {
                editor.remove(vid).apply();
            } else {
                try {
                    String lang = context.getSharedPreferences("SettingsPrefs", Context.MODE_PRIVATE)
                            .getString("My_Lang", "ar");
                    JSONObject root = new JSONObject();
                    JSONObject item = new JSONObject();
                    item.put("title", title);
                    item.put("img", movie.getImage() != null ? movie.getImage() : "");
                    item.put("desc", movie.getDescription() != null ? movie.getDescription() : "");
                    item.put("rating", movie.getRating() != null ? movie.getRating() : "");
                    item.put("year", movie.getYear() != null ? movie.getYear() : "");
                    item.put("quality", movie.getQuality() != null ? movie.getQuality() : "");
                    item.put("genre", movie.getGenre() != null ? movie.getGenre() : "");
                    item.put("director", movie.getDirector() != null ? movie.getDirector() : "");
                    item.put("duration", movie.getDuration() != null ? movie.getDuration() : "");
                    item.put("language", movie.getLanguage() != null ? movie.getLanguage() : "");
                    root.put(lang, item);
                    editor.putString(vid, root.toString()).apply();
                } catch (Exception e) {
                    editor.putString(vid, title + "|" + (movie.getImage() != null ? movie.getImage() : "")).apply();
                }
            }
            notifyItemChanged(holder.getBindingAdapterPosition());
        });
    }

    @Override
    public int getItemCount() { 
        return movieList != null ? movieList.size() : 0; 
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView titleTextView;
        TextView ratingTextView;
        ProgressBar watchProgress;
        ImageView imageView, heartImageView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            titleTextView = itemView.findViewById(R.id.titleTextView);
            imageView = itemView.findViewById(R.id.posterImageView);
            heartImageView = itemView.findViewById(R.id.heartImageView);
            ratingTextView = itemView.findViewById(R.id.ratingTextView);
            watchProgress = itemView.findViewById(R.id.watchProgress);
        }
    }
}
