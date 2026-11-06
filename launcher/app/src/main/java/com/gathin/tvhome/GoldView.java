package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Color;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.TypefaceSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;

/**
 * The three-line gold block. It deliberately has no background and takes no focus: it sits over
 * the wallpaper like the other right-side labels.
 */
final class GoldView extends LinearLayout {

    private final TextView line1;
    private final TextView line2;
    private final TextView line3;

    GoldView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setGravity(Gravity.END);
        setFocusable(false);
        setFocusableInTouchMode(false);
        setClickable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        line1 = makeLine(context, GoldConfig.LINE1_ALPHA);
        line2 = makeLine(context, GoldConfig.LINE2_ALPHA);
        line3 = makeLine(context, GoldConfig.LINE3_ALPHA);
        addView(line1);
        addView(line2);
        addView(line3);
        render(null);
    }

    /** The Inputs label's actual text size, so the scales stay relative to it. */
    void setBaseTextSizePx(float px) {
        if (px <= 0) return;
        line1.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px * GoldConfig.LINE1_SCALE);
        line2.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px * GoldConfig.LINE2_SCALE);
        line3.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px * GoldConfig.LINE3_SCALE);
    }

    void render(GoldRate rate) {
        line1.setText(getContext().getString(R.string.gold_line1,
                GoldConfig.karatLabel(GoldConfig.GOLD_PURITY)));

        boolean stale = rate != null && GoldConfig.isStale(rate.sessionDate);
        if (rate == null || stale) {
            line2.setText(GoldConfig.NO_VALUE);
            line3.setVisibility(GONE);
            return;
        }

        line2.setText(priceWithFallbackSymbol(rate.valuePerTenGramInr));
        String date = GoldConfig.formatSessionDate(rate.sessionDate, LocalDate.now(GoldConfig.IST));
        if (date.isEmpty()) {
            line3.setVisibility(GONE);
            return;
        }
        line3.setText(getContext().getString(R.string.gold_line3,
                rate.session.name(), date));
        line3.setVisibility(VISIBLE);
    }

    /**
     * Outfit has no rupee glyph, and Android's automatic fallback is not guaranteed for a custom
     * typeface. Give only the currency symbol the system sans family; the digits stay Outfit.
     */
    private static CharSequence priceWithFallbackSymbol(long value) {
        String price = GoldConfig.formatPrice(value);
        if (!price.startsWith(GoldConfig.CURRENCY)) return price;
        SpannableString text = new SpannableString(price);
        text.setSpan(new TypefaceSpan(GoldConfig.CURRENCY_FONT),
                0, GoldConfig.CURRENCY.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }

    private static TextView makeLine(Context context, float alpha) {
        TextView view = new TextView(context);
        view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20f);   // replaced by setBaseTextSizePx
        view.setTypeface(context.getResources().getFont(R.font.outfit_regular));
        view.setTextColor(Color.argb(Math.round(255f * alpha), 255, 255, 255));
        view.setGravity(Gravity.END);
        view.setIncludeFontPadding(false);
        view.setSingleLine(true);
        view.setFocusable(false);
        view.setFocusableInTouchMode(false);
        view.setClickable(false);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        view.setShadowLayer(
                dp(context, GoldConfig.SHADOW_RADIUS_DP),
                dp(context, GoldConfig.SHADOW_DX_DP),
                dp(context, GoldConfig.SHADOW_DY_DP),
                GoldConfig.SHADOW_COLOR);
        return view;
    }

    private static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
