package com.stream.master;

import android.graphics.Rect;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Shared V5 grid spacing: 14dp horizontally and 16dp vertically.
 * The offsets are split evenly so the visual gap stays stable on all spans.
 */
public class GridSpacingItemDecoration extends RecyclerView.ItemDecoration {

    private final int spanCount;
    private final int horizontalGap;
    private final int verticalGap;
    private final boolean includeEdge;

    /** Backwards-compatible constructor: same spacing on both axes. */
    public GridSpacingItemDecoration(int spanCount, int spacing, boolean includeEdge) {
        this(spanCount, spacing, spacing, includeEdge);
    }

    public GridSpacingItemDecoration(int spanCount, int horizontalGap, int verticalGap, boolean includeEdge) {
        this.spanCount = Math.max(1, spanCount);
        this.horizontalGap = Math.max(0, horizontalGap);
        this.verticalGap = Math.max(0, verticalGap);
        this.includeEdge = includeEdge;
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                               @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        int position = parent.getChildAdapterPosition(view);
        if (position == RecyclerView.NO_POSITION) return;

        int column = position % spanCount;
        int halfHorizontal = horizontalGap / 2;
        int halfVertical = verticalGap / 2;

        // Equal half-gaps keep the requested distance between adjacent cards.
        outRect.left = halfHorizontal;
        outRect.right = horizontalGap - halfHorizontal;

        if (position < spanCount) {
            outRect.top = includeEdge ? halfVertical : 0;
        } else {
            outRect.top = halfVertical;
        }

        outRect.bottom = halfVertical;
        if (!includeEdge && position < spanCount) {
            outRect.top = 0;
        }
    }
}
