package com.gathin.tvhome;

/** A source of one gold quote. Implementations must not throw: return null when the source fails. */
interface GoldRateProvider {

    GoldRate fetch(RequestBudget budget);

    /** Short name for the GoldRate log. */
    String name();
}
