package com.stream.master;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class ShelfAdapter extends RecyclerView.Adapter<ShelfAdapter.ShelfViewHolder> {

    private List<CategoryShelf> shelves;
    private Context context;
    private OnShelfItemClickListener listener;
    private OnLoadMoreListener loadMoreListener;

    public interface OnShelfItemClickListener {
        void onMovieClick(Movie movie, View sharedImageView);
        void onCategoryClick(String categoryName);
    }

    public interface OnLoadMoreListener {
        void onLoadMore(CategoryShelf shelf);
    }

    public ShelfAdapter(List<CategoryShelf> shelves, Context context, OnShelfItemClickListener listener, OnLoadMoreListener loadMoreListener) {
        this.shelves = shelves;
        this.context = context;
        this.listener = listener;
        this.loadMoreListener = loadMoreListener;
    }

    @NonNull
    @Override
    public ShelfViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_shelf, parent, false);
        return new ShelfViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ShelfViewHolder holder, int position) {
        CategoryShelf shelf = shelves.get(position);
        if (shelf == null) return;

        String shelfKey = shelf.getTitle() == null ? "" : shelf.getTitle().trim();
        holder.title.setText(getLocalizedCategoryTitle(context, shelfKey));
        if (holder.subtitle != null) {
            boolean continueWatching = "continue_watching".equalsIgnoreCase(shelfKey);
            holder.subtitle.setVisibility(continueWatching ? View.VISIBLE : View.GONE);
            holder.subtitle.setText(continueWatching ? getContinueSubtitle(context) : "");
        }
        if (holder.sectionDivider != null) {
            holder.sectionDivider.setVisibility(position < getItemCount() - 1 ? View.VISIBLE : View.GONE);
        }
        View.OnClickListener categoryClick = v -> {
            String key = shelf.getTitle();
            if (key == null || "continue_watching".equalsIgnoreCase(key)) return;
            if (listener != null) listener.onCategoryClick(key);
        };
        holder.title.setOnClickListener(categoryClick);
        if (holder.arrow != null) holder.arrow.setOnClickListener(categoryClick);

        if (shelf.getMovies() != null) {
            RecyclerView.Adapter<?> adapter;
            if ("continue_watching".equalsIgnoreCase(shelf.getTitle())) {
                adapter = new ContinueWatchingAdapter(shelf.getMovies(), context, listener);
            } else {
                adapter = new MovieAdapter(shelf.getMovies(), (movie, sharedImageView) -> {
                    if (listener != null) {
                        listener.onMovieClick(movie, sharedImageView);
                    }
                }, context);
            }
            holder.recyclerView.setAdapter(adapter);
            
            holder.recyclerView.clearOnScrollListeners();
            holder.recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    if (dx > 0 && !recyclerView.canScrollHorizontally(1)) {
                        if (loadMoreListener != null) {
                            loadMoreListener.onLoadMore(shelf);
                        }
                    }
                }
            });
        } else {
            // A recycled shelf holder must never keep the adapter from the
            // previously bound shelf.
            holder.recyclerView.setAdapter(null);
        }
    }


    private String getContinueSubtitle(Context context) {
        try {
            return context.getString(R.string.continue_watching_subtitle);
        } catch (Exception ignored) { return ""; }
    }

    private String getLocalizedCategoryTitle(Context context, String key) {
        if (key == null) return "";
        String k = key.trim().toLowerCase(java.util.Locale.ROOT);
        int id = 0;
        if ("continue_watching".equals(k)) {
            try {
                int resourceId = context.getResources().getIdentifier(
                        "continue_watching", "string", context.getPackageName());
                if (resourceId != 0) return context.getString(resourceId);
            } catch (Exception ignored) {}
            return "Continue Watching";
        }
        else if ("trending".equals(k)) id = R.string.trending_movies;
        else if ("action".equals(k)) id = R.string.category_action;
        else if ("latest".equals(k)) id = R.string.category_latest;
        else if ("horror".equals(k)) id = R.string.category_horror;
        else if ("scifi".equals(k) || "sci-fi".equals(k)) id = R.string.category_scifi;
        else if ("general".equals(k)) id = R.string.category_general;
        else if ("drama".equals(k)) id = R.string.category_drama;
        else if ("crime".equals(k)) id = R.string.category_crime;
        else if ("animation".equals(k)) id = R.string.category_animation;
        else if ("comedy".equals(k)) id = R.string.category_comedy;
        if (id != 0) {
            try { return context.getString(id); } catch (Exception ignored) {}
        }
        return key;
    }

    @Override
    public int getItemCount() { 
        return (shelves != null) ? shelves.size() : 0; 
    }

    public static class ShelfViewHolder extends RecyclerView.ViewHolder {
        TextView title;
        TextView subtitle;
        RecyclerView recyclerView;
        View sectionDivider;
        ImageView arrow;

        public ShelfViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.shelfTitle);
            subtitle = itemView.findViewById(R.id.shelfSubtitle);
            recyclerView = itemView.findViewById(R.id.shelfRecyclerView);
            sectionDivider = itemView.findViewById(R.id.sectionDivider);
            arrow = itemView.findViewById(R.id.shelfArrow);

            LinearLayoutManager layoutManager = new LinearLayoutManager(
                    itemView.getContext(), 
                    LinearLayoutManager.HORIZONTAL, 
                    false
            );
            layoutManager.setInitialPrefetchItemCount(4);
            
            recyclerView.setLayoutManager(layoutManager);
            recyclerView.addItemDecoration(new HorizontalMovieSpacingDecoration(
                    Math.round(8f * itemView.getResources().getDisplayMetrics().density)
            ));
            recyclerView.setHasFixedSize(true);
            recyclerView.setNestedScrollingEnabled(false);
        }
    }
}
