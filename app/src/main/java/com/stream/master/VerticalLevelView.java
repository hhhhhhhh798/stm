package com.stream.master;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class VerticalLevelView extends View {
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF fillRect = new RectF();

    private int max = 100;
    private int progress = 50;

    public VerticalLevelView(Context context) { super(context); init(); }
    public VerticalLevelView(Context context, @Nullable AttributeSet attrs) { super(context, attrs); init(); }
    public VerticalLevelView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }

    private void init() {
        fillPaint.setColor(Color.rgb(242, 198, 165));
        highlightPaint.setColor(Color.argb(70, 255, 255, 255));
        setWillNotDraw(false);
    }

    public void setMax(int max) { this.max = Math.max(1, max); invalidate(); }
    public int getMax() { return max; }
    public void setProgress(int progress) { this.progress = Math.max(0, Math.min(max, progress)); invalidate(); }
    public int getProgress() { return progress; }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float inset = Math.max(2f, getWidth() * 0.08f);
        float radius = Math.min(getWidth() * 0.38f, 12f);
        float left = inset, right = getWidth() - inset, top = 2f, bottom = getHeight() - 2f;

        float fraction = max <= 0 ? 0f : progress / (float) max;
        float fillHeight = (bottom - top) * fraction;
        if (fillHeight > 0f) {
            float fillTop = bottom - fillHeight;
            fillRect.set(left, fillTop, right, bottom);
            canvas.drawRoundRect(fillRect, radius, radius, fillPaint);

            float h = Math.min(dp(2), fillHeight);
            if (h > 0f) {
                RectF highlight = new RectF(left + 2, fillTop + 2, right - 2, fillTop + 2 + h);
                canvas.drawRoundRect(highlight, 2, 2, highlightPaint);
            }
        }
    }

    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
}
