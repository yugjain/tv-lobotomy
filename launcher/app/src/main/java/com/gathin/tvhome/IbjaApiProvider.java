package com.gathin.tvhome;

import org.json.JSONObject;

/** Fallback source: the community JSON mirror. It caches for about two hours, which is fine after a primary failure. */
final class IbjaApiProvider implements GoldRateProvider {

    @Override
    public GoldRate fetch(RequestBudget budget) {
        String body = HttpText.get(GoldConfig.API_URL, budget);
        if (body == null) return null;

        try {
            JSONObject json = new JSONObject(body);
            String amText = string(json, "lblGold" + GoldConfig.GOLD_PURITY + "_AM");
            String pmText = string(json, "lblGold" + GoldConfig.GOLD_PURITY + "_PM");
            long am = parse(amText);
            long pm = parse(pmText);

            GoldRate.Session session;
            long value;
            if (pm > 0) {
                session = GoldRate.Session.PM;
                value = pm;
            } else if (am > 0) {
                session = GoldRate.Session.AM;
                value = am;
            } else {
                return null;
            }

            String date = string(json, "date");
            String raw = "AM=" + amText + " PM=" + pmText + " date=" + date;
            return new GoldRate(value, session, date.isEmpty() ? null : date, raw);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String name() {
        return "ibja-api";
    }

    private static String string(JSONObject json, String key) {
        Object value = json.opt(key);
        if (value == null || value == JSONObject.NULL) return "";
        return String.valueOf(value);
    }

    private static long parse(String text) {
        if (text == null) return -1;
        String cleaned = text.replace(",", "").replace(" ", "").trim();
        if (cleaned.isEmpty()) return -1;
        try {
            return Long.parseLong(cleaned);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
