package com.stream.master;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

/**
 * Central helper for the rating star. The icon itself lives in ONE place:
 * res/drawable/ic_rating_star.xml (colour token: @color/rating_star).
 * Layout XML uses that drawable directly; Java code uses this helper.
 */
public final class RatingStar {
    private static final int DEFAULT_SIZE_DP = 14;

    private RatingStar() {}

    /** The star drawable at the given size (dp). Optional tint colour, or 0 to keep the token colour. */
    public static Drawable icon(Context context, int sizeDp, int tintColorOrZero) {
        Drawable d = ContextCompat.getDrawable(context, R.drawable.ic_rating_star);
        if (d == null) return null;
        d = d.mutate();
        int px = Math.round(sizeDp * context.getResources().getDisplayMetrics().density);
        d.setBounds(0, 0, px, px);
        if (tintColorOrZero != 0) d.setTint(tintColorOrZero);
        return d;
    }

    /** Star + rating when a rating exists; otherwise the plain fallback text with no star. */
    public static void bindOrFallback(TextView view, String rating, String fallback) {
        if (rating == null || rating.isEmpty()) {
            view.setText(fallback);
            view.setCompoundDrawablesRelative(null, null, null, null);
        } else {
            bind(view, rating);
        }
    }

    /** Shows "[star] text" in a TextView (star placed at the reading-start side, RTL aware). */
    public static void bind(TextView view, CharSequence text) {
        view.setText(text);
        view.setCompoundDrawablesRelative(icon(view.getContext(), DEFAULT_SIZE_DP, 0), null, null, null);
        view.setCompoundDrawablePadding(Math.round(4 * view.getResources().getDisplayMetrics().density));
    }
}
