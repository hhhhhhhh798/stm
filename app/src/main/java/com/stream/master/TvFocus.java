package com.stream.master;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Remote-control (D-pad) focus support. Every clickable view gets a visible focus ring and a small
 * zoom, and RecyclerView items are decorated as they attach. Only ever used when TvUtils.isTv().
 * Idempotent: a view that was already prepared is skipped.
 */
public final class TvFocus {
    private TvFocus() {}

    public static void prepare(View v) {
        if (v == null) return;
        if (v.getTag(R.id.tv_focus_done) != null && !(v instanceof ViewGroup)) return;

        if (v instanceof RecyclerView) {
            hookRecycler((RecyclerView) v);
            return;
        }
        if (v.hasOnClickListeners() || v.isClickable()) decorate(v);

        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) prepare(g.getChildAt(i));
        }
    }

    private static void hookRecycler(RecyclerView rv) {
        if (rv.getTag(R.id.tv_focus_done) != null) return;
        rv.setTag(R.id.tv_focus_done, Boolean.TRUE);
        rv.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        rv.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override public void onChildViewAttachedToWindow(View child) { prepare(child); }
            @Override public void onChildViewDetachedFromWindow(View child) { }
        });
        for (int i = 0; i < rv.getChildCount(); i++) prepare(rv.getChildAt(i));
    }

    private static void decorate(View v) {
        if (v instanceof EditText) return;
        if (v.getTag(R.id.tv_focus_done) != null) return;
        v.setTag(R.id.tv_focus_done, Boolean.TRUE);

        v.setFocusable(true);
        v.setFocusableInTouchMode(false);

        Context ctx = v.getContext();
        Drawable ring = ContextCompat.getDrawable(ctx, R.drawable.tv_focus_ring);
        if (ring != null) {
            Drawable existing = v.getForeground();
            if (existing == null) v.setForeground(ring);
            else v.setForeground(new LayerDrawable(new Drawable[]{existing, ring}));
        }

        if (v.getOnFocusChangeListener() == null) {
            v.setOnFocusChangeListener((view, hasFocus) -> {
                float s = hasFocus && isSmall(view) ? 1.05f : 1f;
                view.animate().scaleX(s).scaleY(s).setDuration(120).start();
            });
        }
    }

    /** Zoom only cards/buttons, never large panels (they would clip against their parent). */
    private static boolean isSmall(View v) {
        int screenW = v.getResources().getDisplayMetrics().widthPixels;
        return v.getWidth() > 0 && v.getWidth() < screenW * 0.45f;
    }
}
