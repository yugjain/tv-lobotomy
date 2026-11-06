package com.gathin.tvhome;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/** Every tunable value for the gold block and the IBJA refresh rules. */
final class GoldConfig {

    // ---- what to show ---------------------------------------------------------------------------
    /** One of 999, 995, 916, 750, 585. 999 shows as 24K, 916 as 22K, everything else as the number. */
    static final int GOLD_PURITY = 999;
    static final int[] GOLD_PURITY_OPTIONS = {999, 995, 916, 750, 585};

    static final float LINE1_SCALE = 0.8f;
    static final float LINE2_SCALE = 1.1f;
    static final float LINE3_SCALE = 0.7f;
    static final float LINE1_ALPHA = 0.7f;
    static final float LINE2_ALPHA = 0.95f;
    static final float LINE3_ALPHA = 0.6f;

    /** Vertical gap between the bottom of the block and the top of the Inputs view. */
    static final float GAP_ABOVE_INPUTS_DP = 40f;
    static final float SHADOW_DX_DP = 0f;
    static final float SHADOW_DY_DP = 2f;
    static final float SHADOW_RADIUS_DP = 8f;
    static final int SHADOW_COLOR = 0x73000000;

    static final String CURRENCY = "₹";
    static final String CURRENCY_FONT = "sans-serif";
    static final String NO_VALUE = "—";

    // ---- where the rates come from ---------------------------------------------------------------
    static final String DIRECT_URL = "https://www.ibjarates.com";
    static final String API_URL = "https://ibja-api.vercel.app/latest";
    static final String USER_AGENT = "RateTV-Personal/1.0";
    static final int TIMEOUT_MS = 8000;
    /** One retry per provider, in addition to the first attempt. */
    static final int RETRY_COUNT = 1;

    // ---- refresh rules ---------------------------------------------------------------------------
    static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    static final LocalTime[] PUBLISH_TIMES_IST = {
            LocalTime.of(12, 5), LocalTime.of(17, 5)
    };
    static final int RETRY_MINUTES = 10;
    static final int RETRY_WINDOW_MINUTES = 90;
    static final int SAFETY_REFRESH_HOURS = 6;
    static final int MAX_REQUESTS_PER_DAY = 12;
    static final int STALE_DAYS = 5;

    // ---- sanity ----------------------------------------------------------------------------------
    static final long SANITY_MIN_PER_TEN_GRAM = 50_000L;
    static final long SANITY_MAX_PER_TEN_GRAM = 500_000L;
    static final double MAX_JUMP_FRACTION = 0.05;

    // ---- cached value ----------------------------------------------------------------------------
    static final String PREFS = "gold_rate";
    static final String KEY_VALUE = "last_value";
    static final String KEY_SESSION = "last_session";
    static final String KEY_DATE = "last_date";
    static final String KEY_SUCCESS_MS = "last_success_ms";
    static final String KEY_ATTEMPT_MS = "last_attempt_ms";
    static final String KEY_REQUEST_DAY = "request_day";
    static final String KEY_REQUEST_COUNT = "request_count";
    static final String KEY_PURITY = "last_purity";

    static String karatLabel(int purity) {
        if (purity == 999) return "24K";
        if (purity == 916) return "22K";
        return String.valueOf(purity);
    }

    static String formatPrice(long valuePerTenGramInr) {
        // The JDK/ICU en-IN locale does not always apply the lakh grouping (it can fall back to
        // 137,524), so apply the Indian pattern directly: last three digits, then groups of two.
        String digits = Long.toString(Math.max(0L, valuePerTenGramInr));
        if (digits.length() > 3) {
            String lastThree = digits.substring(digits.length() - 3);
            String rest = digits.substring(0, digits.length() - 3);
            StringBuilder grouped = new StringBuilder();
            int first = rest.length() % 2;
            if (first == 0) first = 2;
            grouped.append(rest, 0, Math.min(first, rest.length()));
            for (int i = first; i < rest.length(); i += 2) {
                grouped.append(',').append(rest, i, Math.min(i + 2, rest.length()));
            }
            digits = grouped + "," + lastThree;
        }
        return CURRENCY + digits;
    }

    static String formatSessionDate(String raw, LocalDate fallback) {
        LocalDate date = parseDate(raw);
        if (date == null) date = fallback;
        return date == null ? "" : date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()));
    }

    /** Accepts ISO, the site's dd/MM/yyyy, and a few human forms. Returns null when it cannot tell. */
    static LocalDate parseDate(String raw) {
        if (raw == null) return null;
        String text = raw.trim();
        if (text.isEmpty()) return null;
        String[] patterns = {"yyyy-MM-dd", "dd/MM/yyyy", "d MMM yyyy", "d MMMM yyyy", "d MMM", "d MMMM"};
        for (String pattern : patterns) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH);
                if (pattern.equals("d MMM") || pattern.equals("d MMMM")) {
                    MonthDay monthDay = MonthDay.parse(text, formatter);
                    return monthDay.atYear(LocalDate.now(IST).getYear());
                }
                return LocalDate.parse(text, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    static boolean isStale(String sessionDate) {
        LocalDate date = parseDate(sessionDate);
        if (date == null) return false;
        return ChronoUnit.DAYS.between(date, LocalDate.now(IST)) > STALE_DAYS;
    }

    private GoldConfig() {
    }
}
