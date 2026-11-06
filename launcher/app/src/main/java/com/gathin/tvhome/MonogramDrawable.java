package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

import java.util.Locale;

/**
 * The white initial shown for an app that ships no icon at all. Zee 5 declares none, so the system
 * hands out its blank fallback icon, and tinting that made the row a solid white square.
 */
final class MonogramDrawable extends Drawable {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String letter;

    MonogramDrawable(String label, Typeface typeface) {
        String text = label == null ? "" : label.trim();
        int i = 0;
        while (i < text.length() && !Character.isLetterOrDigit(text.charAt(i))) i++;
        letter = i < text.length()
                ? text.substring(i, i + 1).toUpperCase(Locale.getDefault()) : "?";
        paint.setColor(0xF2FFFFFF);
        paint.setTypeface(typeface);
        paint.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        if (b.isEmpty()) return;
        paint.setTextSize(b.height() * 0.82f);
        Paint.FontMetrics fm = paint.getFontMetrics();
        float baseline = b.exactCenterY() - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(letter, b.exactCenterX(), baseline, paint);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
