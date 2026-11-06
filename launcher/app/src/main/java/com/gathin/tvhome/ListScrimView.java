package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * CleanFrame List Layout corner vignettes (scrims).
 *
 * Full-screen overlay drawn above the wallpaper and below all text.
 * Uses unclipped radial gradients with 0 alpha clamp so there is never
 * any visible edge, line, or bounding box.
 */
public class ListScrimView extends View {

    /** Peak opacity of the top-right scrim (tunable constant, default 0.35). */
    public static final float SCRIM_PEAK_ALPHA = 0.35f;

    /** Peak opacity of the bottom-right utility scrim (0.45). */
    public static final float BOTTOM_SCRIM_PEAK_ALPHA = 0.45f;

    private final Paint topPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bottomPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int lastWidth = 0;
    private int lastHeight = 0;

    /**
     * The only pixels each gradient can touch. Both fade to zero alpha at their radius, so drawing
     * these rects is pixel-identical to drawing the screen but shades ~75% fewer pixels.
     */
    private final RectF topBounds = new RectF();
    private final RectF bottomBounds = new RectF();

    public ListScrimView(Context context) {
        super(context);
        init();
    }

    public ListScrimView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ListScrimView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setWillNotDraw(false);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        lastWidth = w;
        lastHeight = h;
        rebuildShaders();
    }

    /**
     * Optional hook when wallpaper changes: triggers shader rebuild if needed.
     */
    public void updateFromWallpaper(@Nullable Bitmap bmp) {
        rebuildShaders();
        invalidate();
    }

    private void rebuildShaders() {
        if (lastWidth <= 0 || lastHeight <= 0) return;
        float density = getResources().getDisplayMetrics().density;

        // Top-right: centered exactly on the top-right corner (lastWidth, 0).
        // 325dp reaches 650px on a 1080p/320dpi TV, so the look is unchanged there and scales elsewhere.
        float radiusTop = 325f * density;
        buildTopRadialShader(topPaint, lastWidth, 0f, radiusTop, SCRIM_PEAK_ALPHA);
        topBounds.set(lastWidth - radiusTop - 1f, -1f, lastWidth + 1f, radiusTop + 1f);

        // Bottom-right: centered on the bottom-right corner (lastWidth, lastHeight)
        // 400dp inward, 350dp upward.
        float rxBot = 400f * density;
        float ryBot = 350f * density;
        buildBottomRadialShader(bottomPaint, lastWidth, lastHeight, rxBot, ryBot, BOTTOM_SCRIM_PEAK_ALPHA);
        bottomBounds.set(lastWidth - rxBot - 1f, lastHeight - ryBot - 1f, lastWidth + 1f, lastHeight + 1f);
    }

    private static void buildTopRadialShader(Paint paint, float cx, float cy, float radius, float peakAlpha) {
        // Stops as explicitly requested:
        // 0%   -> rgba(0,0,0, peakAlpha) [0.35]
        // 40%  -> rgba(0,0,0, 0.18)
        // 75%  -> rgba(0,0,0, 0.05)
        // 100% -> rgba(0,0,0, 0.00)
        float ratio = peakAlpha / 0.35f;
        int a0 = Color.argb(Math.round(255f * peakAlpha), 0, 0, 0);
        int a1 = Color.argb(Math.round(255f * 0.18f * ratio), 0, 0, 0);
        int a2 = Color.argb(Math.round(255f * 0.05f * ratio), 0, 0, 0);
        int a3 = Color.argb(0, 0, 0, 0);

        int[] colors = new int[]{a0, a1, a2, a3};
        float[] stops = new float[]{0.0f, 0.40f, 0.75f, 1.0f};

        RadialGradient rg = new RadialGradient(cx, cy, radius, colors, stops, Shader.TileMode.CLAMP);
        paint.setShader(rg);
    }

    private static void buildBottomRadialShader(Paint paint, float cx, float cy, float rx, float ry, float peakAlpha) {
        int a0 = Color.argb(Math.round(255f * peakAlpha), 0, 0, 0);
        int a1 = Color.argb(Math.round(255f * peakAlpha * 0.72f), 0, 0, 0);
        int a2 = Color.argb(Math.round(255f * peakAlpha * 0.38f), 0, 0, 0);
        int a3 = Color.argb(Math.round(255f * peakAlpha * 0.12f), 0, 0, 0);
        int a4 = Color.argb(0, 0, 0, 0);

        int[] colors = new int[]{a0, a1, a2, a3, a4};
        float[] stops = new float[]{0.0f, 0.35f, 0.65f, 0.88f, 1.0f};

        RadialGradient rg = new RadialGradient(0f, 0f, 1.0f, colors, stops, Shader.TileMode.CLAMP);
        Matrix matrix = new Matrix();
        matrix.setScale(rx, ry);
        matrix.postTranslate(cx, cy);
        rg.setLocalMatrix(matrix);
        paint.setShader(rg);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (lastWidth <= 0 || lastHeight <= 0) return;

        // Each shader is CLAMP and already transparent past its radius, so the rects below clip
        // nothing visible; they just stop the GPU shading the 3.1M pixels that would stay clear.
        canvas.drawRect(topBounds, topPaint);
        canvas.drawRect(bottomBounds, bottomPaint);
    }
}
