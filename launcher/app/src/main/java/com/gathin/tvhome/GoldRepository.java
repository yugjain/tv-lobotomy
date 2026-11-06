package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Owns the IBJA fetch/cache cycle. It runs only while the launcher is visible, keeps the last good
 * value in SharedPreferences, and never puts a partial or out-of-range number on screen.
 */
final class GoldRepository {

    private static final String TAG = "GoldRate";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile boolean visible;

    interface Listener {
        void onGold(GoldRate rate);
    }

    static void start(Context context, Listener listener) {
        visible = true;
        Context app = context.getApplicationContext();
        SharedPreferences preferences = app.getSharedPreferences(GoldConfig.PREFS, Context.MODE_PRIVATE);
        deliver(listener, load(preferences));
        maybeRefresh(app, preferences, listener);
    }

    static void stop() {
        visible = false;
    }

    private static void maybeRefresh(Context context, SharedPreferences preferences, Listener listener) {
        if (!visible) return;

        long now = System.currentTimeMillis();
        long lastAttempt = preferences.getLong(GoldConfig.KEY_ATTEMPT_MS, 0L);
        if (lastAttempt > 0 && now - lastAttempt < GoldConfig.RETRY_MINUTES * 60_000L) return;

        long lastSuccess = preferences.getLong(GoldConfig.KEY_SUCCESS_MS, 0L);
        GoldRate cachedBefore = load(preferences);
        if (!shouldFetch(now, lastSuccess, lastAttempt, cachedBefore)) return;

        preferences.edit().putLong(GoldConfig.KEY_ATTEMPT_MS, now).apply();
        IO.execute(() -> {
            if (!visible) return;
            RequestBudget budget = new RequestBudget(context);
            GoldRate fetched = fetchProviders(budget);
            if (!visible) return;

            GoldRate cached = load(preferences);
            if (fetched == null) {
                Log.w(TAG, "no provider returned a value; keeping cached "
                        + (cached == null ? "none" : cached.valuePerTenGramInr));
                deliver(listener, cached);
                return;
            }

            if (!isSane(fetched, cached)) {
                Log.w(TAG, "rejected value=" + fetched.valuePerTenGramInr
                        + " session=" + fetched.session
                        + " date=" + fetched.sessionDate
                        + "; keeping cached " + (cached == null ? "none" : cached.valuePerTenGramInr));
                deliver(listener, cached);
                return;
            }

            GoldRate saved = withDate(fetched);
            save(preferences, saved);
            Log.i(TAG, "final value=" + saved.valuePerTenGramInr
                    + " session=" + saved.session
                    + " date=" + saved.sessionDate
                    + " source=" + saved.rawText);
            deliver(listener, saved);
        });
    }

    private static boolean shouldFetch(long nowMs, long lastSuccessMs, long lastAttemptMs,
                                       GoldRate cached) {
        if (lastSuccessMs == 0L) return true;
        if (nowMs - lastSuccessMs >= GoldConfig.SAFETY_REFRESH_HOURS * 3_600_000L) return true;

        ZonedDateTime now = Instant.ofEpochMilli(nowMs).atZone(GoldConfig.IST);
        ZonedDateTime publish = latestPublish(now);
        if (publish == null) return false;

        long publishMs = publish.toInstant().toEpochMilli();
        if (publishMs <= lastSuccessMs && coversPublish(cached, publish)) return false;

        long sincePublish = nowMs - publishMs;
        if (sincePublish <= GoldConfig.RETRY_WINDOW_MINUTES * 60_000L) return true;
        // The app was not visible during the retry window. Try once, then wait for the safety refresh.
        return lastAttemptMs < publishMs;
    }

    /** True when the cached value is the print this publish time should have produced. */
    private static boolean coversPublish(GoldRate cached, ZonedDateTime publish) {
        if (cached == null) return false;
        LocalDate cachedDate = GoldConfig.parseDate(cached.sessionDate);
        if (cachedDate == null) return false;
        LocalDate publishDate = publish.toLocalDate();
        if (cachedDate.isBefore(publishDate)) return false;
        if (cachedDate.isAfter(publishDate)) return true;
        LocalTime publishTime = publish.toLocalTime();
        boolean pmPublish = !publishTime.isBefore(GoldConfig.PUBLISH_TIMES_IST[1]);
        return !pmPublish || cached.session == GoldRate.Session.PM;
    }

    private static ZonedDateTime latestPublish(ZonedDateTime now) {
        LocalDate day = now.toLocalDate();
        LocalTime time = now.toLocalTime();
        LocalTime latest = null;
        for (LocalTime publish : GoldConfig.PUBLISH_TIMES_IST) {
            if (!publish.isAfter(time)) latest = publish;
        }
        if (latest != null) return LocalDateTime.of(day, latest).atZone(GoldConfig.IST);
        LocalTime yesterday = GoldConfig.PUBLISH_TIMES_IST[GoldConfig.PUBLISH_TIMES_IST.length - 1];
        return LocalDateTime.of(day.minusDays(1), yesterday).atZone(GoldConfig.IST);
    }

    private static GoldRate fetchProviders(RequestBudget budget) {
        GoldRateProvider[] providers = {new IbjaDirectProvider(), new IbjaApiProvider()};
        for (GoldRateProvider provider : providers) {
            if (!visible) return null;
            GoldRate rate = provider.fetch(budget);
            if (rate != null) {
                Log.i(TAG, "provider=" + provider.name()
                        + " raw=" + rate.rawText
                        + " session=" + rate.session
                        + " value=" + rate.valuePerTenGramInr);
                return rate;
            }
        }
        return null;
    }

    private static boolean isSane(GoldRate fetched, GoldRate cached) {
        if (fetched.valuePerTenGramInr < GoldConfig.SANITY_MIN_PER_TEN_GRAM
                || fetched.valuePerTenGramInr > GoldConfig.SANITY_MAX_PER_TEN_GRAM) {
            return false;
        }
        if (cached != null && cached.valuePerTenGramInr > 0) {
            double jump = Math.abs((double) fetched.valuePerTenGramInr - cached.valuePerTenGramInr)
                    / cached.valuePerTenGramInr;
            return jump <= GoldConfig.MAX_JUMP_FRACTION;
        }
        return true;
    }

    private static GoldRate withDate(GoldRate rate) {
        if (rate.sessionDate != null && !rate.sessionDate.trim().isEmpty()) return rate;
        String today = LocalDate.now(GoldConfig.IST).toString();
        Log.w(TAG, "source gave no rate date; using fetch date " + today);
        return new GoldRate(rate.valuePerTenGramInr, rate.session, today, rate.rawText);
    }

    private static void save(SharedPreferences preferences, GoldRate rate) {
        preferences.edit()
                .putLong(GoldConfig.KEY_VALUE, rate.valuePerTenGramInr)
                .putString(GoldConfig.KEY_SESSION,
                        rate.session == GoldRate.Session.PM ? "PM" : "AM")
                .putString(GoldConfig.KEY_DATE, rate.sessionDate)
                .putLong(GoldConfig.KEY_SUCCESS_MS, System.currentTimeMillis())
                .putInt(GoldConfig.KEY_PURITY, GoldConfig.GOLD_PURITY)
                .apply();
    }

    private static GoldRate load(SharedPreferences preferences) {
        if (preferences.getInt(GoldConfig.KEY_PURITY, GoldConfig.GOLD_PURITY)
                != GoldConfig.GOLD_PURITY) {
            return null;
        }
        long value = preferences.getLong(GoldConfig.KEY_VALUE, 0L);
        if (value <= 0) return null;
        String sessionText = preferences.getString(GoldConfig.KEY_SESSION, "AM");
        GoldRate.Session session = "PM".equals(sessionText)
                ? GoldRate.Session.PM : GoldRate.Session.AM;
        String date = preferences.getString(GoldConfig.KEY_DATE, null);
        return new GoldRate(value, session, date, "cached");
    }

    private static void deliver(Listener listener, GoldRate rate) {
        if (listener == null) return;
        MAIN.post(() -> listener.onGold(rate));
    }

    private GoldRepository() {
    }
}
