package com.gathin.tvhome;

/** One parsed IBJA quote: always rupees per 10 g, never per gram. */
final class GoldRate {

    enum Session { AM, PM }

    final long valuePerTenGramInr;
    final Session session;
    /** The date the source gives, or null when it gives none. */
    final String sessionDate;
    /** The small piece of source text that was parsed, for the GoldRate log. */
    final String rawText;

    GoldRate(long valuePerTenGramInr, Session session, String sessionDate, String rawText) {
        this.valuePerTenGramInr = valuePerTenGramInr;
        this.session = session;
        this.sessionDate = sessionDate;
        this.rawText = rawText;
    }
}
