package com.stream.master;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/** FrameLayout that always keeps a 2:3 (width:height) poster ratio. */
public class PosterFrameLayout extends FrameLayout {
    private static final float HEIGHT_PER_WIDTH = 1.5f;

    public PosterFrameLayout(Context context) { super(context); }
    public PosterFrameLayout(Context context, AttributeSet attrs) { super(context, attrs); }
    public PosterFrameLayout(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = Math.round(width * HEIGHT_PER_WIDTH);
        super.onMeasure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }
}
