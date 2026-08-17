package com.courier.perftest;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Sizing knobs for {@link PerfDataGeneratorRunner}, all under {@code perf.gen.*}.
 *
 * <p>Defaults match PHASE 2 of the local performance-test brief exactly (10 tenants /
 * 500 users / 50 branches / 10,000 customers / 100,000 shipments / 10,000 tickets).
 * Every count is per-company except {@code branchesPerCompany}/{@code usersPerCompany}/
 * {@code customersPerCompany}/{@code shipmentsPerCompany}/{@code ticketsPerCompany}
 * themselves, which are literally per-company — scale the whole dataset by raising
 * {@code companies} and/or the per-company counts (e.g. {@code shipmentsPerCompany=50000}
 * for a 500K-shipment run across the same 10 companies).
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "perf.gen")
public class PerfGenProperties {

    /** Safety gate — nothing runs unless this is explicitly true. */
    private boolean enabled = false;

    /** Company-code/name prefix; change it to run a second, non-colliding cohort. */
    private String tenantPrefix = "PERFT";

    private int companies = 10;
    private int branchesPerCompany = 5;
    private int usersPerCompany = 50;
    private int customersPerCompany = 1000;
    private int shipmentsPerCompany = 10000;
    private int walletTransactionsPerCompany = 10000;
    private int ticketsPerCompany = 1000;

    /** JDBC batch size for every bulk INSERT. */
    private int batchSize = 1000;

    /**
     * Starting available balance credited to every generated branch wallet. Sized well
     * above {@code shipmentsPerCompany * max plausible per-shipment freight+GST} (~2360
     * at the generator's own weight/rate assumptions) so the wallet-transaction target is
     * actually reachable — insertWalletTransactions stops crediting once a wallet would go
     * negative (the real available_balance CHECK constraint), so too low a default here
     * silently produces far fewer wallet transactions than {@code walletTransactionsPerCompany}
     * asks for. See perf-tests/ISSUES.md ISSUE-002.
     */
    private String walletOpeningBalance = "50000000.0000";
}
