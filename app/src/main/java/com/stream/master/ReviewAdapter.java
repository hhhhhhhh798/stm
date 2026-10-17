package com.stream.master;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.util.List;

public class ReviewAdapter extends RecyclerView.Adapter<ReviewAdapter.Holder> {
    private final List<Review> items;
    public ReviewAdapter(List<Review> items) { this.items = items; }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_review, parent, false));
    }
    @Override public void onBindViewHolder(@NonNull Holder h, int position) {
        Review r=items.get(position);
        String author=value(r.getAuthorName(), h.itemView.getContext().getString(R.string.anonymous_reviewer));
        h.author.setText(TextSanitizer.clean(author));
        String rating=Movie.formatRating(r.getRating());
        RatingStar.bind(h.rating, rating.isEmpty() ? "—" : rating);
        h.date.setText(TextSanitizer.clean(value(r.getDate(), h.itemView.getContext().getString(R.string.not_available))));
        h.content.setText(TextSanitizer.clean(value(r.getContent(), h.itemView.getContext().getString(R.string.no_review_content))));
        Glide.with(h.avatar).load(r.getAvatarUrl()).placeholder(R.drawable.bg_drawer_avatar).error(R.drawable.bg_drawer_avatar).into(h.avatar);
    }
    @Override public int getItemCount() { return items.size(); }
    private static String value(String s,String f){return s==null||s.trim().isEmpty()?f:s;}
    static class Holder extends RecyclerView.ViewHolder {
        ImageView avatar; TextView author,rating,date,content;
        Holder(View v){super(v); avatar=v.findViewById(R.id.reviewAvatar);author=v.findViewById(R.id.reviewAuthor);rating=v.findViewById(R.id.reviewRating);date=v.findViewById(R.id.reviewDate);content=v.findViewById(R.id.reviewContent);}
    }
}
