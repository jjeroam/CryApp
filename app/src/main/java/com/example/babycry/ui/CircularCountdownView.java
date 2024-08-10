package com.example.babycry.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class CircularCountdownView extends View {

    private Paint paint;
    private float progress = 0;
    private int radius;
    private int strokeWidth;


    public CircularCountdownView(Context context) {
        super(context);
        init();
    }

    public CircularCountdownView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CircularCountdownView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(20); // Default stroke width
        paint.setColor(getResources().getColor(android.R.color.holo_blue_light));
        paint.setAntiAlias(true);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // Calculate radius and stroke width
        radius = Math.min(w, h) / 2 - (int) paint.getStrokeWidth();
        strokeWidth = (int) paint.getStrokeWidth();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2;
        float cy = getHeight() / 2;

        // Draw the background circle
        paint.setColor(getResources().getColor(android.R.color.darker_gray));
        canvas.drawCircle(cx, cy, radius, paint);

        // Draw the progress circle
        paint.setColor(getResources().getColor(android.R.color.system_on_primary_container_light));
        canvas.drawArc(cx - radius, cy - radius, cx + radius, cy + radius,
                -90, progress * 360 / 100, false, paint);
    }

    public void setProgress(float progress) {
        this.progress = progress;
        invalidate();
    }
}
