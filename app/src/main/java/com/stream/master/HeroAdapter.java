package com.stream.master;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import org.json.JSONObject;
import java.util.List;

public class HeroAdapter extends RecyclerView.Adapter<HeroAdapter.HeroViewHolder> {
    private List<Movie> movies;
    private SharedPreferences prefs;

    public HeroAdapter(Context context, List<Movie> movies) { 
        this.movies = movies; 
        this.prefs = context.getSharedPreferences("FAVS", Context.MODE_PRIVATE);
        setHasStableIds(true);
    }

    public void setMovies(List<Movie> movies) {
        this.movies = movies;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public HeroViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_hero, parent, false);

        RecyclerView.LayoutParams params =
                new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT);

        view.setLayoutParams(params);

        return new HeroViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HeroViewHolder holder, int position) {
        if (movies != null && movies.get(position) != null) {
            Movie movie = movies.get(position);
            Context context = holder.itemView.getContext();

            if (movie.isSkeletonPlaceholder()) {
                // Keep the real hero card, border, layout and buttons visible
                // while remote content is loading. Only data-dependent fields
                // are empty/hidden.
                holder.itemView.animate().cancel();
                holder.itemView.setAlpha(1f);
                holder.itemView.setScaleX(1f);
                holder.itemView.setScaleY(1f);
                Glide.with(context).clear(holder.imageView);
                holder.imageView.setImageDrawable(null);

                if (holder.heroTitle != null) {
                    holder.heroTitle.setText("");
                    holder.heroTitle.setVisibility(View.GONE);
                }
                if (holder.heroDesc != null) {
                    holder.heroDesc.setText("");
                    holder.heroDesc.setVisibility(View.GONE);
                }
                if (holder.heroRating != null) {
                    holder.heroRating.setText("");
                    holder.heroRating.setVisibility(View.GONE);
                }
                if (holder.btnWatch != null) {
                    holder.btnWatch.setEnabled(false);
                    holder.btnWatch.setAlpha(0.72f);
                    holder.btnWatch.setOnClickListener(null);
                }
                if (holder.btnMyList != null) {
                    holder.btnMyList.setEnabled(false);
                    holder.btnMyList.setAlpha(0.72f);
                    holder.btnMyList.setOnClickListener(null);
                }
                holder.itemView.setOnClickListener(null);
                holder.itemView.setClickable(false);
                return;
            }

            // Reset loading-only state for recycled hero holders.
            holder.itemView.setClickable(true);
            if (holder.heroTitle != null) holder.heroTitle.setVisibility(View.VISIBLE);
            if (holder.heroDesc != null) holder.heroDesc.setVisibility(View.VISIBLE);
            if (holder.heroRating != null) holder.heroRating.setVisibility(View.VISIBLE);
            if (holder.btnWatch != null) {
                holder.btnWatch.setEnabled(true);
                holder.btnWatch.setAlpha(1f);
            }
            if (holder.btnMyList != null) {
                holder.btnMyList.setEnabled(true);
                holder.btnMyList.setAlpha(1f);
            }

            if (holder.heroTitle != null) {
                holder.heroTitle.setText(movie.getTitle());
            }
            if (holder.heroDesc != null) {
                holder.heroDesc.setText(movie.getDescription() != null ? movie.getDescription() : "");
            }
            if (holder.heroRating != null) {
                String value = movie.getDisplayRating();
                String ratingLabel = value.isEmpty() ? "—" : value;
                SpannableString ratingSpan = new SpannableString(ratingLabel);
                ratingSpan.setSpan(new ForegroundColorSpan(android.graphics.Color.rgb(255, 214, 72)),
                        0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                holder.heroRating.setText(ratingSpan);
            }

            Glide.with(context)
                    .load(movie.getImage())
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .centerCrop()
                    .into(holder.imageView);

            holder.itemView.setAlpha(0f);
            holder.itemView.setScaleX(0.95f);
            holder.itemView.setScaleY(0.95f);

            holder.itemView.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(350)
                    .start();

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(context, DetailsActivity.class);
                intent.putExtra(Constants.KEY_MOVIE, movie);
                context.startActivity(intent);
            });

            if (holder.btnWatch != null) {
                holder.btnWatch.setOnClickListener(v -> {
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
                });
            }

            if (holder.btnMyList != null) {
                
                if (prefs.contains(movie.getVid())) {
                    holder.btnMyList.setText(context.getString(R.string.in_watchlist));
                } else {
                    holder.btnMyList.setText(context.getString(R.string.add_to_watchlist));
                }

                holder.btnMyList.setOnClickListener(v -> {
                    if (prefs.contains(movie.getVid())) {
                        prefs.edit().remove(movie.getVid()).apply();
                        holder.btnMyList.setText(context.getString(R.string.add_to_watchlist));
                        if (context instanceof BaseActivity) {
                            ((BaseActivity)context).showCustomToast(context.getString(R.string.removed_from_list));
                        }
                    } else {
                        saveFavJson(context, prefs, movie);
                        holder.btnMyList.setText(context.getString(R.string.in_watchlist));
                        if (context instanceof BaseActivity) {
                            ((BaseActivity)context).showCustomToast(context.getString(R.string.added_to_list));
                        }
                    }
                });
            }
        }
    }

    private void saveFavJson(Context context, SharedPreferences prefs, Movie movie) {
        try {
            SharedPreferences settings = context.getSharedPreferences("SettingsPrefs", Context.MODE_PRIVATE);
            String currentLang = settings.getString("My_Lang", "ar");

            String existingJson = prefs.getString(movie.getVid(), "{}");
            JSONObject rootObj = new JSONObject(existingJson);

            JSONObject langObj = new JSONObject();
            langObj.put("title", movie.getTitle() != null ? movie.getTitle() : "");
            langObj.put("img", movie.getImage() != null ? movie.getImage() : "");
            langObj.put("desc", movie.getDescription() != null ? movie.getDescription() : "");
            langObj.put("rating", movie.getRating() != null ? movie.getRating() : "");
            langObj.put("year", movie.getYear() != null ? movie.getYear() : "");
            langObj.put("quality", movie.getQuality() != null ? movie.getQuality() : "");
            langObj.put("genre", movie.getGenre() != null ? movie.getGenre() : "");
            langObj.put("director", movie.getDirector() != null ? movie.getDirector() : "");
            langObj.put("duration", movie.getDuration() != null ? movie.getDuration() : "");
            langObj.put("language", movie.getLanguage() != null ? movie.getLanguage() : "");

            rootObj.put(currentLang, langObj);

            prefs.edit().putString(movie.getVid(), rootObj.toString()).apply();
        } catch (Exception e) {
            // Keep production builds silent; the UI already handles this failure.
        }
    }

    @Override
    public int getItemCount() { 
        return (movies == null) ? 0 : Math.min(movies.size(), 5); 
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    static class HeroViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        TextView heroTitle;
        TextView heroDesc;
        TextView heroRating;
        View btnWatch;
        Button btnMyList;

        HeroViewHolder(View v) { 
            super(v); 
            imageView = v.findViewById(R.id.heroImage);
            heroTitle = v.findViewById(R.id.heroTitle);
            heroDesc = v.findViewById(R.id.heroDesc);
            heroRating = v.findViewById(R.id.heroRating);
            btnWatch = v.findViewById(R.id.btnWatch);
            btnMyList = v.findViewById(R.id.btnMyList);
        }
    }
}
