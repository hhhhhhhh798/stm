package com.stream.master;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.util.List;

public class ContinueWatchingAdapter extends RecyclerView.Adapter<ContinueWatchingAdapter.VH> {
    private final List<Movie> movies;
    private final Context context;
    private final ShelfAdapter.OnShelfItemClickListener listener;

    public ContinueWatchingAdapter(List<Movie> movies, Context context, ShelfAdapter.OnShelfItemClickListener listener) {
        this.movies = movies;
        this.context = context;
        this.listener = listener;
        setHasStableIds(true);
    }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_continue, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull VH h, int position) {
        if (movies == null || position < 0 || position >= movies.size()) return;
        Movie movie = movies.get(position);
        if (movie == null) return;
        h.title.setText(movie.getTitle());
        String meta = movie.getYear();
        if (movie.getQuality() != null && !movie.getQuality().trim().isEmpty()) {
            meta = (meta == null || meta.isEmpty()) ? movie.getQuality() : meta + "  •  " + movie.getQuality();
        }
        h.meta.setText(meta == null ? "" : meta);
        h.progress.setProgress(Math.max(0, Math.min(100, (int)(movie.getWatchProgress() * 100f))));
        Glide.with(context).load(movie.getImage()).diskCacheStrategy(DiskCacheStrategy.ALL).centerCrop().into(h.image);

        View.OnClickListener open = v -> {
            Intent intent = new Intent(context, PlayerActivity.class);
            intent.putExtra("VID", movie.getVid());
            intent.putExtra("TITLE", movie.getTitle());
            intent.putExtra("IMG", movie.getImage());
            intent.putExtra("DESC", movie.getDescription());
            intent.putExtra("RATING", movie.getRating());
            intent.putExtra("YEAR", movie.getYear());
            intent.putExtra("QUALITY", movie.getQuality());
            intent.putExtra("GENRE", movie.getGenre());
            intent.putExtra("DIRECTOR", movie.getDirector());
            intent.putExtra("DURATION", movie.getDuration());
            intent.putExtra("LANGUAGE", movie.getLanguage());
            context.startActivity(intent);
        };
        h.itemView.setOnClickListener(open);
        h.play.setOnClickListener(open);
    }

    @Override public int getItemCount() { return movies == null ? 0 : movies.size(); }
    @Override public long getItemId(int position) { return movies.get(position).getVid() == null ? position : movies.get(position).getVid().hashCode(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView image; TextView title, meta; ProgressBar progress; View play;
        VH(View v) {
            super(v);
            image=v.findViewById(R.id.continueImage); title=v.findViewById(R.id.continueTitle);
            meta=v.findViewById(R.id.continueMeta); progress=v.findViewById(R.id.continueProgress); play=v.findViewById(R.id.continuePlay);
        }
    }
}
