package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

/** Builds the one TextView the launcher and the overlay both show, so the style has a single source. */
final class MessageView {

    static TextView create(Context context) {
        TextView view = new TextView(context);
        view.setText(MessageConfig.messageForToday());
        view.setTextSize(MessageConfig.TEXT_SIZE_SP);
        view.setTypeface(context.getResources().getFont(R.font.outfit_regular));
        view.setLetterSpacing(MessageConfig.LETTER_SPACING_EM);
        view.setTextColor(textColor());
        view.setGravity(Gravity.CENTER);
        view.setIncludeFontPadding(false);
        view.setSingleLine(true);
        view.setShadowLayer(
                dp(context, MessageConfig.SHADOW_RADIUS_DP),
                dp(context, MessageConfig.SHADOW_DX_DP),
                dp(context, MessageConfig.SHADOW_DY_DP),
                shadowColor());
        // The message is decoration: it must never take D-pad focus, clicks, or accessibility focus.
        view.setFocusable(false);
        view.setFocusableInTouchMode(false);
        view.setClickable(false);
        view.setLongClickable(false);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return view;
    }

    static int bottomMarginPx(int heightPx) {
        return Math.round(heightPx * MessageConfig.BOTTOM_MARGIN_FRACTION);
    }

    static int sideMarginPx(Context context) {
        return dp(context, MessageConfig.SIDE_MARGIN_DP);
    }

    static int textColor() {
        return Color.argb(alpha(MessageConfig.TEXT_ALPHA), 255, 255, 255);
    }

    static int shadowColor() {
        return Color.argb(alpha(MessageConfig.SHADOW_ALPHA), 0, 0, 0);
    }

    private static int alpha(float value) {
        return Math.max(0, Math.min(255, Math.round(255f * value)));
    }

    private static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private MessageView() {
    }
}
