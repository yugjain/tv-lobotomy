package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.LocalDate;

/**
 * A persisted cap on outbound IBJA requests per IST calendar day. Every HTTP attempt, including
 * a retry or the fallback provider, must call {@link #consume()} first.
 */
final class RequestBudget {

    private final SharedPreferences preferences;
    private final String today;
    private int used;

    RequestBudget(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(GoldConfig.PREFS, Context.MODE_PRIVATE);
        today = LocalDate.now(GoldConfig.IST).toString();
        String storedDay = preferences.getString(GoldConfig.KEY_REQUEST_DAY, "");
        used = today.equals(storedDay) ? preferences.getInt(GoldConfig.KEY_REQUEST_COUNT, 0) : 0;
    }

    synchronized boolean consume() {
        if (used >= GoldConfig.MAX_REQUESTS_PER_DAY) return false;
        used++;
        preferences.edit()
                .putString(GoldConfig.KEY_REQUEST_DAY, today)
                .putInt(GoldConfig.KEY_REQUEST_COUNT, used)
                .apply();
        return true;
    }

    synchronized int used() {
        return used;
    }
}
