package com.gathin.tvhome;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * Everything tunable about the quiet daily message: the words, where it sits, and how it looks.
 * The overlay service reads the same file so the two copies can never drift apart.
 */
final class MessageConfig {

    /** The message shown today. Add another line here and the day-of-year picker will alternate them. */
    static final List<String> MESSAGES = Collections.singletonList("you are what you consume");

    /** Where the message sits on screen. */
    enum Position { BOTTOM_CENTER, BOTTOM_RIGHT }

    static final Position MESSAGE_POSITION = Position.BOTTOM_CENTER;

    static final float TEXT_SIZE_SP = 15f;
    static final float LETTER_SPACING_EM = 0.08f;
    static final float TEXT_ALPHA = 0.5f;
    static final float SHADOW_DX_DP = 0f;
    static final float SHADOW_DY_DP = 1f;
    static final float SHADOW_RADIUS_DP = 4f;
    static final float SHADOW_ALPHA = 0.5f;

    /** Bottom margin as a fraction of the screen height. About one 15sp line lower than the old 4%. */
    static final float BOTTOM_MARGIN_FRACTION = 0.012f;
    /** Only used if MESSAGE_POSITION is BOTTOM_RIGHT. */
    static final float SIDE_MARGIN_DP = 72f;

    /** The overlay gets a slow, almost invisible drift so it never sits on exact pixels. */
    static final long OVERLAY_SHIFT_INTERVAL_MS = 5 * 60 * 1000L;
    static final int OVERLAY_SHIFT_MAX_PX = 2;

    static String messageForToday() {
        return MESSAGES.get(LocalDate.now().getDayOfYear() % MESSAGES.size());
    }

    private MessageConfig() {
    }
}
