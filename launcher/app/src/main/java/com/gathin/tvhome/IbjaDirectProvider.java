package com.gathin.tvhome;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Primary source: the IBJA page itself. The page's current table has one span per purity and
 * session, e.g. {@code <span id="lblGold999_AM">141023</span>}; the PM span is empty until the
 * afternoon print. The page does not print a rate date next to that table, so the date comes from
 * the last label in the HdnGold chart data, which is the session the table belongs to.
 */
final class IbjaDirectProvider implements GoldRateProvider {

    private static final Pattern HDN_GOLD = Pattern.compile(
            "id=\"HdnGold\"[^>]*value=\"([^\"]*)\"");
    private static final Pattern LABELS = Pattern.compile(
            "\"labels\"\\s*:\\s*\\[(.*?)\\]");
    private static final Pattern CHART_DATE = Pattern.compile(
            "(\\d{2})/(\\d{2})/(\\d{4})");

    @Override
    public GoldRate fetch(RequestBudget budget) {
        String html = HttpText.get(GoldConfig.DIRECT_URL, budget);
        if (html == null) return null;

        String amText = span(html, "lblGold" + GoldConfig.GOLD_PURITY + "_AM");
        String pmText = span(html, "lblGold" + GoldConfig.GOLD_PURITY + "_PM");
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

        String date = chartDate(html);
        String raw = "AM=" + amText + " PM=" + pmText + " date=" + date;
        return new GoldRate(value, session, date, raw);
    }

    @Override
    public String name() {
        return "ibja-direct";
    }

    private static String span(String html, String id) {
        Matcher matcher = Pattern.compile(
                "id=\"" + Pattern.quote(id) + "\"[^>]*>([^<]*)<").matcher(html);
        return matcher.find() ? matcher.group(1).trim() : "";
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

    private static String chartDate(String html) {
        Matcher hidden = HDN_GOLD.matcher(html);
        if (!hidden.find()) return null;
        String value = hidden.group(1).replace("&quot;", "\"");
        Matcher labels = LABELS.matcher(value);
        if (!labels.find()) return null;
        Matcher date = CHART_DATE.matcher(labels.group(1));
        String last = null;
        while (date.find()) {
            last = date.group(3) + "-" + date.group(2) + "-" + date.group(1);
        }
        return last;
    }
}
