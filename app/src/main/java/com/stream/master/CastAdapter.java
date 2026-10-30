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

public class CastAdapter extends RecyclerView.Adapter<CastAdapter.Holder> {
    private final List<Cast> items;
    public CastAdapter(List<Cast> items) { this.items = items; }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_cast, parent, false));
    }
    @Override public void onBindViewHolder(@NonNull Holder h, int position) {
        Cast item = items.get(position);
        h.name.setText(TextSanitizer.clean(value(item.getName(), h.itemView.getContext().getString(R.string.not_available))));
        h.character.setText(TextSanitizer.clean(value(item.getCharacter(), "")));
        Glide.with(h.image).load(item.getImageUrl()).placeholder(R.drawable.bg_cast_image_placeholder).error(R.drawable.bg_cast_image_placeholder).into(h.image);
    }
    @Override public int getItemCount() { return items.size(); }
    private static String value(String s, String fallback) { return s == null || s.trim().isEmpty() ? fallback : s; }

    static class Holder extends RecyclerView.ViewHolder {
        ImageView image; TextView name, character;
        Holder(View v) {
            super(v);
            image=v.findViewById(R.id.castImage); name=v.findViewById(R.id.castName); character=v.findViewById(R.id.castCharacter);
        }
    }
}
