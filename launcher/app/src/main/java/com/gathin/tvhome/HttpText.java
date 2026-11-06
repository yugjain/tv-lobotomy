package com.gathin.tvhome;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Tiny blocking GET helper with the 8-second timeout, one retry, and a persisted request budget. */
final class HttpText {

    private static final String TAG = "GoldRate";

    static String get(String address, RequestBudget budget) {
        for (int attempt = 0; attempt <= GoldConfig.RETRY_COUNT; attempt++) {
            if (!budget.consume()) {
                Log.w(TAG, "daily request cap reached; not fetching " + address);
                return null;
            }
            try {
                String body = request(address);
                if (body != null) return body;
            } catch (IOException e) {
                Log.w(TAG, "GET failed (" + attempt + ") " + address + ": " + e.getMessage());
            }
        }
        return null;
    }

    private static String request(String address) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setConnectTimeout(GoldConfig.TIMEOUT_MS);
            connection.setReadTimeout(GoldConfig.TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", GoldConfig.USER_AGENT);
            connection.setRequestProperty("Accept", "text/html,application/json");
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "GET " + address + " returned HTTP " + status);
                return null;
            }
            InputStream stream = connection.getInputStream();
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                char[] buffer = new char[8192];
                int read;
                while ((read = reader.read(buffer)) != -1) body.append(buffer, 0, read);
            }
            return body.toString();
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private HttpText() {
    }
}
