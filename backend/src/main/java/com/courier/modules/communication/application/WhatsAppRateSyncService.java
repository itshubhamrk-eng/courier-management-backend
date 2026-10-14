package com.courier.modules.communication.application;

import java.math.BigDecimal;
import java.util.List;

/**
 * Learns the company's real per-message WhatsApp prices from what Meta has actually billed
 * (Meta publishes no rate card through its API) and saves them as the rates the dashboard's cost
 * estimate uses — so nobody has to type them.
 */
public interface WhatsAppRateSyncService {

    /** How far back billing is read. A quarter smooths over a quiet month without reaching a price change. */
    int LOOKBACK_DAYS = 90;

    FetchedRates fetchAndSave();

    /**
     * @param currency       the account's billing currency, null if Meta did not say
     * @param rates          one entry per category that was actually billed in the window
     * @param saved          the categories whose rate was written to Channel Settings
     * @param billedTotal    everything Meta charged in the window for those categories
     * @param note           one plain sentence on what happened — including "nothing billed yet"
     */
    record FetchedRates(String currency, int days, List<Derived> rates, List<String> saved,
                        BigDecimal billedTotal, String note) {
    }

    /** @param rate cost / volume, four decimals */
    record Derived(String category, long volume, BigDecimal cost, BigDecimal rate) {
    }
}
