package com.courier.perftest;

import com.courier.modules.finance.domain.WalletNumberGenerator;
import com.courier.shared.domain.TimeOrderedUuid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * PHASE 2 local performance-test fixture generator: 10 synthetic tenants with a full,
 * referentially-plausible dataset (branches/users/customers/shipments/status-history/
 * wallet-transactions/tickets) sized to the brief's own starting numbers, configurable
 * via {@link PerfGenProperties} ({@code perf.gen.*}) for the larger runs (50K/100K/500K/
 * 1M shipments) the brief also asks for.
 *
 * <p><b>Why raw JDBC batch inserts, not the real services/repositories</b>: this data
 * exists so Phase 4-9's queries (search/pagination/dashboard/reports) have real volume
 * to run against — it is never replayed through business logic, only ever read. Going
 * through {@code ShipmentService.create} et al. for 100K+ rows would mean 100K live
 * pricing-engine calls, wallet debits and audit-log writes for data nobody will ever
 * transact against again; a load test's own k6 scenarios are what exercises the real
 * service layer (see {@code perf-tests/}). Every id/number format below still matches
 * production exactly (see {@link TimeOrderedUuid#toBytes}, {@link WalletNumberGenerator}),
 * and every per-branch/per-company sequence counter this run advances past is written
 * back to {@code branch_shipment_sequences}/{@code company_shipment_sequences}/
 * {@code company_ticket_sequences} at the end of each company, so a real booking made
 * afterward through the actual app never collides with a synthetic number.
 *
 * <p><b>Safety</b>: inert unless {@code perf.gen.enabled=true} is passed explicitly.
 * Runs against whatever {@code spring.datasource} the active profile points at — for
 * this project that is deliberately the same {@code courier_db} dev uses (see
 * MEMORY: perf fixtures live alongside dev fixtures, never cleaned up). Company codes
 * are prefixed {@code perf.gen.tenantPrefix} (default {@code PERFT}) and unique, so a
 * second run with the same prefix fails fast on a duplicate-key error instead of
 * silently doubling the dataset — change the prefix to add a second cohort.
 *
 * <p>Run standalone, without booting the full app's other profiles' side effects
 * (scheduling is harmless here — no ticket/shipment SLA rows this generator writes
 * carry due dates):
 * <pre>
 * DB_USERNAME=root DB_PASSWORD=Root@1234 \
 *   mvn -B spring-boot:run -pl backend \
 *   -Dspring-boot.run.profiles=test,perfgen \
 *   -Dspring-boot.run.arguments=--perf.gen.enabled=true
 * </pre>
 * Scale up by overriding counts, e.g. {@code --perf.gen.shipmentsPerCompany=50000} for
 * a 500K-shipment run across the same 10 companies.
 */
@Slf4j
@Component
@Profile("perfgen")
@RequiredArgsConstructor
public class PerfDataGeneratorRunner implements CommandLineRunner {

    private static final DateTimeFormatter YEAR_MONTH =
            DateTimeFormatter.ofPattern("yyMM").withZone(ZoneOffset.UTC);

    private static final String[] SHIPMENT_STATUSES = {
            "BOOKED", "READY_FOR_MANIFEST", "MANIFEST_CREATED", "DISPATCHED",
            "IN_SCAN", "OUT_FOR_DELIVERY", "DELIVERED", "RETURNED", "CANCELLED"
    };
    // Parallel to SHIPMENT_STATUSES: how much of the mix lands on each final status.
    // Weighted toward DELIVERED, same shape a real courier's historical data has.
    private static final int[] SHIPMENT_STATUS_WEIGHTS = {5, 5, 5, 10, 10, 5, 55, 3, 2};

    private static final String[] TICKET_STATUSES =
            {"OPEN", "ASSIGNED", "IN_PROGRESS", "WAITING_FOR_USER", "RESOLVED", "CLOSED"};
    private static final int[] TICKET_STATUS_WEIGHTS = {10, 10, 10, 5, 20, 45};
    private static final String[] TICKET_PRIORITIES = {"LOW", "MEDIUM", "HIGH", "CRITICAL"};
    private static final int[] TICKET_PRIORITY_WEIGHTS = {40, 35, 20, 5};

    private static final String[] FIRST_NAMES = {
            "Aarav", "Vivaan", "Aditya", "Vihaan", "Arjun", "Sai", "Reyansh", "Krishna",
            "Ishaan", "Rohan", "Ananya", "Diya", "Priya", "Aadhya", "Saanvi", "Myra",
            "Anika", "Kiara", "Riya", "Sneha"
    };
    private static final String[] LAST_NAMES = {
            "Sharma", "Verma", "Patel", "Gupta", "Kumar", "Singh", "Reddy", "Nair",
            "Iyer", "Joshi", "Mehta", "Rao", "Desai", "Kulkarni", "Chopra"
    };
    private static final String[] CITIES = {"Pune", "Mumbai", "Latur", "Nagpur", "Nashik"};

    private final DataSource dataSource;
    private final PerfGenProperties props;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);

    @Override
    public void run(String... args) throws Exception {
        if (!props.isEnabled()) {
            log.warn("perf.gen.enabled=false — PerfDataGeneratorRunner is a no-op. "
                    + "Pass --perf.gen.enabled=true to actually generate data.");
            return;
        }

        log.info("Perf data generation starting: {} companies x ({} branches, {} users, "
                        + "{} customers, {} shipments, {} wallet txns, {} tickets each)",
                props.getCompanies(), props.getBranchesPerCompany(), props.getUsersPerCompany(),
                props.getCustomersPerCompany(), props.getShipmentsPerCompany(),
                props.getWalletTransactionsPerCompany(), props.getTicketsPerCompany());

        String sharedPasswordHash = passwordEncoder.encode("Password@1234");
        Instant started = Instant.now();

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);

            UUID subscriptionPlanId = fetchAnySubscriptionPlanId(connection);
            List<UUID> ticketCategoryIds = fetchTicketCategoryIds(connection);

            long totalShipments = 0;
            long totalStatusHistory = 0;
            long totalWalletTxns = 0;
            long totalTickets = 0;

            for (int companyIndex = 1; companyIndex <= props.getCompanies(); companyIndex++) {
                Instant companyStarted = Instant.now();
                CompanyCtx ctx = generateCompany(connection, companyIndex, subscriptionPlanId,
                        ticketCategoryIds, sharedPasswordHash);
                connection.commit();

                totalShipments += ctx.shipmentIds.size();
                totalStatusHistory += ctx.statusHistoryRowCount;
                totalWalletTxns += ctx.walletTxnRowCount;
                totalTickets += ctx.ticketRowCount;

                log.info("Company {}/{} ({}) done in {}s: {} branches, {} users, {} customers, "
                                + "{} shipments, {} status-history rows, {} wallet txns, {} tickets",
                        companyIndex, props.getCompanies(), ctx.companyCode,
                        java.time.Duration.between(companyStarted, Instant.now()).toSeconds(),
                        ctx.branchIds.size(), ctx.userIds.size(), ctx.customerIds.size(),
                        ctx.shipmentIds.size(), ctx.statusHistoryRowCount, ctx.walletTxnRowCount,
                        ctx.ticketRowCount);
            }

            log.info("Perf data generation complete in {}s. Totals: {} companies, {} branches, "
                            + "{} users, {} customers, {} shipments, {} status-history rows, "
                            + "{} wallet transactions, {} tickets.",
                    java.time.Duration.between(started, Instant.now()).toSeconds(),
                    props.getCompanies(), props.getCompanies() * props.getBranchesPerCompany(),
                    props.getCompanies() * props.getUsersPerCompany(),
                    props.getCompanies() * props.getCustomersPerCompany(),
                    totalShipments, totalStatusHistory, totalWalletTxns, totalTickets);
        }
    }

    // ---------------------------------------------------------------- per-company

    private CompanyCtx generateCompany(Connection cx, int companyIndex, UUID subscriptionPlanId,
                                        List<UUID> ticketCategoryIds, String sharedPasswordHash)
            throws SQLException {
        CompanyCtx ctx = new CompanyCtx();
        ctx.companyCode = "%s%02d".formatted(props.getTenantPrefix(), companyIndex);
        ctx.companyId = TimeOrderedUuid.generate();

        insertCompany(cx, ctx, companyIndex, subscriptionPlanId);
        insertBranchesAndWallets(cx, ctx, companyIndex);
        insertMasterTypes(cx, ctx);
        insertFreightFactorGrid(cx, ctx);
        insertUsers(cx, ctx, companyIndex, sharedPasswordHash);
        insertCustomersAndAddresses(cx, ctx, companyIndex);
        insertShipments(cx, ctx, companyIndex);
        insertWalletTransactions(cx, ctx);
        insertTickets(cx, ctx, ticketCategoryIds);
        advanceSequences(cx, ctx);

        return ctx;
    }

    private void insertCompany(Connection cx, CompanyCtx ctx, int idx, UUID planId) throws SQLException {
        String sql = """
                INSERT INTO companies
                    (id, company_id, company_code, company_name, subscription_plan_id, status,
                     email, mobile, is_active, created_at, updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """;
        try (PreparedStatement ps = cx.prepareStatement(sql)) {
            UUID rowId = TimeOrderedUuid.generate();
            Timestamp now = Timestamp.from(Instant.now());
            int i = 1;
            ps.setBytes(i++, TimeOrderedUuid.toBytes(rowId));
            ps.setBytes(i++, TimeOrderedUuid.toBytes(ctx.companyId));
            ps.setString(i++, ctx.companyCode);
            ps.setString(i++, "Perf Test Tenant %02d".formatted(idx));
            ps.setBytes(i++, TimeOrderedUuid.toBytes(planId));
            ps.setString(i++, "ACTIVE");
            ps.setString(i++, "%s.tenant%02d@loadtest.local".formatted(props.getTenantPrefix().toLowerCase(), idx));
            ps.setString(i++, "90000000%02d".formatted(idx));
            ps.setBoolean(i++, true);
            ps.setTimestamp(i++, now);
            ps.setTimestamp(i, now);
            ps.executeUpdate();
        }
    }

    private void insertBranchesAndWallets(Connection cx, CompanyCtx ctx, int companyIdx) throws SQLException {
        String branchSql = """
                INSERT INTO branches
                    (id, company_id, branch_code, branch_name, branch_type, status, email, mobile,
                     city, allow_booking, allow_delivery, allow_pickup, allow_manifest,
                     allow_cash_collection, allow_wallet, created_at, updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """;
        String walletSql = """
                INSERT INTO wallets
                    (id, company_id, wallet_number, branch_id, status, available_balance,
                     hold_balance, currency, created_at, updated_at)
                VALUES (?,?,?,?,?,?,0,?,?,?)
                """;
        Timestamp now = Timestamp.from(Instant.now());
        try (PreparedStatement branchPs = cx.prepareStatement(branchSql);
             PreparedStatement walletPs = cx.prepareStatement(walletSql)) {
            for (int b = 1; b <= props.getBranchesPerCompany(); b++) {
                UUID branchId = TimeOrderedUuid.generate();
                String branchCode = "BR%02d".formatted(b);
                String city = CITIES[(b - 1) % CITIES.length];

                int i = 1;
                branchPs.setBytes(i++, TimeOrderedUuid.toBytes(branchId));
                branchPs.setBytes(i++, TimeOrderedUuid.toBytes(ctx.companyId));
                branchPs.setString(i++, branchCode);
                branchPs.setString(i++, "%s Branch %02d".formatted(city, b));
                branchPs.setString(i++, "BOOKING_DELIVERY_BRANCH");
                branchPs.setString(i++, "ACTIVE");
                branchPs.setString(i++, "branch%02d@t%02d.perf.local".formatted(b, companyIdx));
                branchPs.setString(i++, "91%08d".formatted(companyIdx * 1000 + b));
                branchPs.setString(i++, city);
                branchPs.setBoolean(i++, true);
                branchPs.setBoolean(i++, true);
                branchPs.setBoolean(i++, true);
                branchPs.setBoolean(i++, true);
                branchPs.setBoolean(i++, true);
                branchPs.setBoolean(i++, true);
                branchPs.setTimestamp(i++, now);
                branchPs.setTimestamp(i, now);
                branchPs.addBatch();

                UUID walletId = TimeOrderedUuid.generate();
                i = 1;
                walletPs.setBytes(i++, TimeOrderedUuid.toBytes(walletId));
                walletPs.setBytes(i++, TimeOrderedUuid.toBytes(ctx.companyId));
                walletPs.setString(i++, WalletNumberGenerator.walletNumber());
                walletPs.setBytes(i++, TimeOrderedUuid.toBytes(branchId));
                walletPs.setString(i++, "ACTIVE");
                walletPs.setBigDecimal(i++, new BigDecimal(props.getWalletOpeningBalance()));
                walletPs.setString(i++, "INR");
                walletPs.setTimestamp(i++, now);
                walletPs.setTimestamp(i, now);
                walletPs.addBatch();

                ctx.branchIds.add(branchId);
                ctx.branchCodes.add(branchCode);
                ctx.walletIds.add(walletId);
                ctx.walletRunningBalance.add(new BigDecimal(props.getWalletOpeningBalance()));
                ctx.branchShipmentSerial.add(0L);
            }
            branchPs.executeBatch();
            walletPs.executeBatch();
        }
    }

    private void insertMasterTypes(Connection cx, CompanyCtx ctx) throws SQLException {
        Timestamp now = Timestamp.from(Instant.now());

        // service types: code, name, deliveryDays, express
        Object[][] serviceTypes = {
                {"STD", "Standard", 3, false}, {"EXP", "Express", 1, true}, {"ECO", "Economy", 5, false}
        };
        String svcSql = """
                INSERT INTO master_service_types
                    (id, company_id, code, name, status, delivery_days, is_express, created_at, updated_at)
                VALUES (?,?,?,?,'ACTIVE',?,?,?,?)
                """;
        try (PreparedStatement ps = cx.prepareStatement(svcSql)) {
            for (Object[] st : serviceTypes) {
                UUID id = TimeOrderedUuid.generate();
                int i = 1;
                ps.setBytes(i++, TimeOrderedUuid.toBytes(id));
                ps.setBytes(i++, TimeOrderedUuid.toBytes(ctx.companyId));
                ps.setString(i++, (String) st[0]);
                ps.setString(i++, (String) st[1]);
                ps.setInt(i++, (Integer) st[2]);
                ps.setBoolean(i++, (Boolean) st[3]);
                ps.setTimestamp(i++, now);
                ps.setTimestamp(i, now);
                ps.addBatch();
                ctx.serviceTypeIds.add(id);
            }
            ps.executeBatch();
        }

        // package types: code, name, isDocument
        Object[][] packageTypes = {
                {"DOC", "Document", true}, {"PCL", "Parcel", false}, {"CGO", "Cargo", false}
        };
        String pkgSql = """
                INSERT INTO master_package_types
                    (id, company_id, code, name, status, is_document, created_at, updated_at)
                VALUES (?,?,?,?,'ACTIVE',?,?,?)
                """;
        try (PreparedStatement ps = cx.prepareStatement(pkgSql)) {
            for (Object[] pt : packageTypes) {
                UUID id = TimeOrderedUuid.generate();
                int i = 1;
                ps.setBytes(i++, TimeOrderedUuid.toBytes(id));
                ps.setBytes(i++, TimeOrderedUuid.toBytes(ctx.companyId));
                ps.setString(i++, (String) pt[0]);
                ps.setString(i++, (String) pt[1]);
                ps.setBoolean(i++, (Boolean) pt[2]);
                ps.setTimestamp(i++, now);
                ps.setTimestamp(i, now);
                ps.addBatch();
                ctx.packageTypeIds.add(id);
            }
            ps.executeBatch();
        }

        // payment modes: code, name, collectAtBooking, collectAtDelivery, requiresCredit, isCod
        Object[][] paymentModes = {
                {"PAID", "Paid", true, false, false, false},
                {"TOPAY", "To Pay", false, true, false, false},
                {"TBB", "To Be Billed", false, false, true, false},
                {"COD", "Cash on Delivery", false, true, false, true}
        };
        String paySql = """
                INSERT INTO master_payment_modes
                    (id, company_id, code, name, status, collect_at_booking, collect_at_delivery,
                     requires_credit_account, is_cash_on_delivery, created_at, updated_at)
                VALUES (?,?,?,?,'ACTIVE',?,?,?,?,?,?)
                """;
        try (PreparedStatement ps = cx.prepareStatement(paySql)) {
            for (Object[] pm : paymentModes) {
                UUID id = TimeOrderedUuid.generate();
                int i = 1;
                ps.setBytes(i++, TimeOrderedUuid.toBytes(id));
                ps.setBytes(i++, TimeOrderedUuid.toBytes(ctx.companyId));
                ps.setString(i++, (String) pm[0]);
                ps.setString(i++, (String) pm[1]);
                ps.setBoolean(i++, (Boolean) pm[2]);
                ps.setBoolean(i++, (Boolean) pm[3]);
                ps.setBoolean(i++, (Boolean) pm[4]);
                ps.setBoolean(i++, (Boolean) pm[5]);
                ps.setTimestamp(i++, now);
                ps.setTimestamp(i, now);
                ps.addBatch();
                ctx.paymentModeIds.add(id);
                ctx.paymentModeCollectAtBooking.add((Boolean) pm[2]);
            }
            ps.executeBatch();
        }
    }

    /**
     * One catch-all freight-factor cell per company (0-99999 km x 0-99999 kg, factor 40)
     * so every k6-booked shipment (Phase 5+) prices successfully regardless of the
     * distance the address-distance module resolves between two synthetic pincodes —
     * these companies have no Rate Master routes/rates configured (out of PHASE 2's own
     * scope), so without this every real {@code POST /shipments} 422s with "no freight
     * factor slab covers X km / Y kg", the exact gap the Pricing Engine's own fallback
     * exists to report.
     */
    private void insertFreightFactorGrid(Connection cx, CompanyCtx ctx) throws SQLException {
        String sql = """
                INSERT INTO freight_factor
                    (id, company_id, from_km, to_km, from_weight, to_weight, factor, status,
                     created_at, updated_at)
                VALUES (?,?,0,99999,0,99999,40,'ACTIVE',?,?)
                """;
        try (PreparedStatement ps = cx.prepareStatement(sql)) {
            Timestamp now = Timestamp.from(Instant.now());
            ps.setBytes(1, TimeOrderedUuid.toBytes(TimeOrderedUuid.generate()));
            ps.setBytes(2, TimeOrderedUuid.toBytes(ctx.companyId));
            ps.setTimestamp(3, now);
            ps.setTimestamp(4, now);
            ps.executeUpdate();
        }
    }

    private void insertUsers(Connection cx, CompanyCtx ctx, int companyIdx, String passwordHash)
            throws SQLException {
        String userSql = """
                INSERT INTO users
                    (id, company_id, email, password_hash, first_name, last_name, phone, status,
                     email_verified, email_verified_at, branch_id, created_at, updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                """;
        String roleSql = "INSERT INTO user_roles (user_id, role) VALUES (?,?)";
        Timestamp now = Timestamp.from(Instant.now());
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        try (PreparedStatement userPs = cx.prepareStatement(userSql);
             PreparedStatement rolePs = cx.prepareStatement(roleSql)) {

            // 1 COMPANY_ADMIN, no branch.
            addUser(userPs, rolePs, ctx, "admin@t%02d.perf.local".formatted(companyIdx),
                    "Admin", "User", passwordHash, now, null, "COMPANY_ADMIN");

            // 1 BRANCH_MANAGER per branch.
            int staffSoFar = 1;
            for (int b = 0; b < ctx.branchIds.size() && staffSoFar < props.getUsersPerCompany(); b++) {
                addUser(userPs, rolePs, ctx,
                        "manager%02d@t%02d.perf.local".formatted(b + 1, companyIdx),
                        "Manager", "B%02d".formatted(b + 1), passwordHash, now,
                        ctx.branchIds.get(b), "BRANCH_MANAGER");
                staffSoFar++;
            }

            // Remaining headcount as OPERATOR, spread round-robin across branches.
            int opSeq = 1;
            while (staffSoFar < props.getUsersPerCompany()) {
                UUID branchId = ctx.branchIds.isEmpty() ? null
                        : ctx.branchIds.get((opSeq - 1) % ctx.branchIds.size());
                String first = FIRST_NAMES[rnd.nextInt(FIRST_NAMES.length)];
                String last = LAST_NAMES[rnd.nextInt(LAST_NAMES.length)];
                addUser(userPs, rolePs, ctx, "op%04d@t%02d.perf.local".formatted(opSeq, companyIdx),
                        first, last, passwordHash, now, branchId, "OPERATOR");
                opSeq++;
                staffSoFar++;
            }

            userPs.executeBatch();
            rolePs.executeBatch();
        }
    }

    private void addUser(PreparedStatement userPs, PreparedStatement rolePs, CompanyCtx ctx,
                          String email, String firstName, String lastName, String passwordHash,
                          Timestamp now, UUID branchId, String role) throws SQLException {
        UUID userId = TimeOrderedUuid.generate();
        int i = 1;
        userPs.setBytes(i++, TimeOrderedUuid.toBytes(userId));
        userPs.setBytes(i++, TimeOrderedUuid.toBytes(ctx.companyId));
        userPs.setString(i++, email);
        userPs.setString(i++, passwordHash);
        userPs.setString(i++, firstName);
        userPs.setString(i++, lastName);
        userPs.setString(i++, "9" + String.format("%09d", Math.abs(email.hashCode()) % 1_000_000_000L));
        userPs.setString(i++, "ACTIVE");
        userPs.setBoolean(i++, true);
        userPs.setTimestamp(i++, now);
        if (branchId != null) {
            userPs.setBytes(i++, TimeOrderedUuid.toBytes(branchId));
        } else {
            userPs.setNull(i++, java.sql.Types.BINARY);
        }
        userPs.setTimestamp(i++, now);
        userPs.setTimestamp(i, now);
        userPs.addBatch();

        rolePs.setBytes(1, TimeOrderedUuid.toBytes(userId));
        rolePs.setString(2, role);
        rolePs.addBatch();

        ctx.userIds.add(userId);
    }

    private void insertCustomersAndAddresses(Connection cx, CompanyCtx ctx, int companyIdx)
            throws SQLException {
        String custSql = """
                INSERT INTO customers
                    (id, company_id, customer_code, customer_type, first_name, last_name, mobile,
                     email, status, created_at, updated_at)
                VALUES (?,?,?,'INDIVIDUAL',?,?,?,?,'ACTIVE',?,?)
                """;
        String addrSql = """
                INSERT INTO customer_addresses
                    (id, company_id, customer_id, address_type, address_line1,
                     is_default_pickup, is_default_delivery, status, created_at, updated_at)
                VALUES (?,?,?,'HOME',?,TRUE,TRUE,'ACTIVE',?,?)
                """;
        Timestamp now = Timestamp.from(Instant.now());
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int batchSize = props.getBatchSize();

        try (PreparedStatement custPs = cx.prepareStatement(custSql);
             PreparedStatement addrPs = cx.prepareStatement(addrSql)) {
            for (int c = 1; c <= props.getCustomersPerCompany(); c++) {
                UUID customerId = TimeOrderedUuid.generate();
                String first = FIRST_NAMES[rnd.nextInt(FIRST_NAMES.length)];
                String last = LAST_NAMES[rnd.nextInt(LAST_NAMES.length)];
                String mobile = "9" + String.format("%09d", rnd.nextLong(1_000_000_000L));

                int i = 1;
                custPs.setBytes(i++, TimeOrderedUuid.toBytes(customerId));
                custPs.setBytes(i++, TimeOrderedUuid.toBytes(ctx.companyId));
                custPs.setString(i++, "CUST%06d".formatted(c));
                custPs.setString(i++, first);
                custPs.setString(i++, last);
                custPs.setString(i++, mobile);
                custPs.setString(i++, "cust%06d@t%02d.perf.local".formatted(c, companyIdx));
                custPs.setTimestamp(i++, now);
                custPs.setTimestamp(i, now);
                custPs.addBatch();

                UUID addressId = TimeOrderedUuid.generate();
                String city = CITIES[rnd.nextInt(CITIES.length)];
                i = 1;
                addrPs.setBytes(i++, TimeOrderedUuid.toBytes(addressId));
                addrPs.setBytes(i++, TimeOrderedUuid.toBytes(ctx.companyId));
                addrPs.setBytes(i++, TimeOrderedUuid.toBytes(customerId));
                addrPs.setString(i++, (100 + c) + " MG Road, " + city);
                addrPs.setTimestamp(i++, now);
                addrPs.setTimestamp(i, now);
                addrPs.addBatch();

                ctx.customerIds.add(customerId);
                ctx.customerAddressIds.add(addressId);

                if (c % batchSize == 0) {
                    custPs.executeBatch();
                    addrPs.executeBatch();
                }
            }
            custPs.executeBatch();
            addrPs.executeBatch();
        }
    }

    private void insertShipments(Connection cx, CompanyCtx ctx, int companyIdx) throws SQLException {
        // Sender/receiver are plain text on the shipment itself, not a Customer reference
        // — V18 disconnected Shipment Booking from the Customer module entirely (the
        // booking screen has no customer lookup). current_location_id/next_location_id
        // (V26, crossing) are set to booking/delivery branch verbatim, the documented
        // "non-crossing" convention.
        String shipmentSql = """
                INSERT INTO shipments
                    (id, company_id, shipment_number, tracking_number, booking_date,
                     booking_branch_id, delivery_branch_id, pickup_pincode, delivery_pincode,
                     sender_name, sender_address, sender_contact,
                     receiver_name, receiver_address, receiver_contact,
                     current_location_id, next_location_id,
                     service_type_id, package_type_id, payment_mode_id, shipment_type,
                     actual_weight, volumetric_weight, chargeable_weight, declared_value,
                     number_of_packages, status, created_at, updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """;
        String itemSql = """
                INSERT INTO shipment_items
                    (id, company_id, shipment_id, item_name, quantity, weight, created_at, updated_at)
                VALUES (?,?,?,'Package',1,?,?,?)
                """;
        String chargeSql = """
                INSERT INTO shipment_charges
                    (id, company_id, shipment_id, freight, gst_amount, net_amount, created_at, updated_at)
                VALUES (?,?,?,?,?,?,?,?)
                """;
        String historySql = """
                INSERT INTO shipment_status_history
                    (id, company_id, shipment_id, branch_id, status, previous_status, changed_by,
                     changed_at, created_at, updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                """;

        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int batchSize = props.getBatchSize();

        try (PreparedStatement shipmentPs = cx.prepareStatement(shipmentSql);
             PreparedStatement itemPs = cx.prepareStatement(itemSql);
             PreparedStatement chargePs = cx.prepareStatement(chargeSql);
             PreparedStatement historyPs = cx.prepareStatement(historySql)) {

            for (int s = 1; s <= props.getShipmentsPerCompany(); s++) {
                int branchIdx = rnd.nextInt(ctx.branchIds.size());
                int deliveryBranchIdx = ctx.branchIds.size() > 1
                        ? (branchIdx + 1 + rnd.nextInt(ctx.branchIds.size() - 1)) % ctx.branchIds.size()
                        : branchIdx;
                UUID bookingBranchId = ctx.branchIds.get(branchIdx);
                UUID deliveryBranchId = ctx.branchIds.get(deliveryBranchIdx);

                String senderName = FIRST_NAMES[rnd.nextInt(FIRST_NAMES.length)] + " "
                        + LAST_NAMES[rnd.nextInt(LAST_NAMES.length)];
                String receiverName = FIRST_NAMES[rnd.nextInt(FIRST_NAMES.length)] + " "
                        + LAST_NAMES[rnd.nextInt(LAST_NAMES.length)];
                String senderCity = CITIES[rnd.nextInt(CITIES.length)];
                String receiverCity = CITIES[rnd.nextInt(CITIES.length)];
                String senderContact = "9" + String.format("%09d", rnd.nextLong(1_000_000_000L));
                String receiverContact = "9" + String.format("%09d", rnd.nextLong(1_000_000_000L));
                String pickupPincode = "4%05d".formatted(rnd.nextInt(100000));
                String deliveryPincode = "4%05d".formatted(rnd.nextInt(100000));

                UUID serviceTypeId = ctx.serviceTypeIds.get(rnd.nextInt(ctx.serviceTypeIds.size()));
                UUID packageTypeId = ctx.packageTypeIds.get(rnd.nextInt(ctx.packageTypeIds.size()));
                int paymentIdx = rnd.nextInt(ctx.paymentModeIds.size());
                UUID paymentModeId = ctx.paymentModeIds.get(paymentIdx);

                long branchSerial = ctx.branchShipmentSerial.get(branchIdx) + 1;
                ctx.branchShipmentSerial.set(branchIdx, branchSerial);
                String shipmentNumber = "%s-%06d".formatted(ctx.branchCodes.get(branchIdx), branchSerial);

                ctx.companyShipmentSerial++;
                String trackingNumber = YEAR_MONTH.format(Instant.now())
                        + "%07d".formatted(ctx.companyShipmentSerial);

                LocalDate bookingDate = LocalDate.now().minusDays(rnd.nextInt(1, 181));
                String status = weightedPick(SHIPMENT_STATUSES, SHIPMENT_STATUS_WEIGHTS, rnd);

                BigDecimal weight = BigDecimal.valueOf(0.5 + rnd.nextDouble(49.5))
                        .setScale(3, java.math.RoundingMode.HALF_UP);
                BigDecimal freight = weight.multiply(BigDecimal.valueOf(40))
                        .setScale(4, java.math.RoundingMode.HALF_UP);
                BigDecimal gst = freight.multiply(BigDecimal.valueOf(0.18))
                        .setScale(4, java.math.RoundingMode.HALF_UP);
                BigDecimal net = freight.add(gst);
                BigDecimal declaredValue = weight.multiply(BigDecimal.valueOf(500))
                        .setScale(4, java.math.RoundingMode.HALF_UP);

                UUID shipmentId = TimeOrderedUuid.generate();
                Timestamp bookedAt = Timestamp.from(
                        bookingDate.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(rnd.nextInt(0, 86400)));

                int i = 1;
                shipmentPs.setBytes(i++, TimeOrderedUuid.toBytes(shipmentId));
                shipmentPs.setBytes(i++, TimeOrderedUuid.toBytes(ctx.companyId));
                shipmentPs.setString(i++, shipmentNumber);
                shipmentPs.setString(i++, trackingNumber);
                shipmentPs.setDate(i++, java.sql.Date.valueOf(bookingDate));
                shipmentPs.setBytes(i++, TimeOrderedUuid.toBytes(bookingBranchId));
                shipmentPs.setBytes(i++, TimeOrderedUuid.toBytes(deliveryBranchId));
                shipmentPs.setString(i++, pickupPincode);
                shipmentPs.setString(i++, deliveryPincode);
                shipmentPs.setString(i++, senderName);
                shipmentPs.setString(i++, (100 + s) + " MG Road, " + senderCity);
                shipmentPs.setString(i++, senderContact);
                shipmentPs.setString(i++, receiverName);
                shipmentPs.setString(i++, (100 + s) + " Station Road, " + receiverCity);
                shipmentPs.setString(i++, receiverContact);
                shipmentPs.setBytes(i++, TimeOrderedUuid.toBytes(bookingBranchId));
                shipmentPs.setBytes(i++, TimeOrderedUuid.toBytes(deliveryBranchId));
                shipmentPs.setBytes(i++, TimeOrderedUuid.toBytes(serviceTypeId));
                shipmentPs.setBytes(i++, TimeOrderedUuid.toBytes(packageTypeId));
                shipmentPs.setBytes(i++, TimeOrderedUuid.toBytes(paymentModeId));
                shipmentPs.setString(i++, "NON_DOCUMENT");
                shipmentPs.setBigDecimal(i++, weight);
                shipmentPs.setBigDecimal(i++, BigDecimal.ZERO);
                shipmentPs.setBigDecimal(i++, weight);
                shipmentPs.setBigDecimal(i++, declaredValue);
                shipmentPs.setInt(i++, 1 + rnd.nextInt(5));
                shipmentPs.setString(i++, status);
                shipmentPs.setTimestamp(i++, bookedAt);
                shipmentPs.setTimestamp(i, bookedAt);
                shipmentPs.addBatch();

                UUID itemId = TimeOrderedUuid.generate();
                i = 1;
                itemPs.setBytes(i++, TimeOrderedUuid.toBytes(itemId));
                itemPs.setBytes(i++, TimeOrderedUuid.toBytes(ctx.companyId));
                itemPs.setBytes(i++, TimeOrderedUuid.toBytes(shipmentId));
                itemPs.setBigDecimal(i++, weight);
                itemPs.setTimestamp(i++, bookedAt);
                itemPs.setTimestamp(i, bookedAt);
                itemPs.addBatch();

                UUID chargeId = TimeOrderedUuid.generate();
                i = 1;
                chargePs.setBytes(i++, TimeOrderedUuid.toBytes(chargeId));
                chargePs.setBytes(i++, TimeOrderedUuid.toBytes(ctx.companyId));
                chargePs.setBytes(i++, TimeOrderedUuid.toBytes(shipmentId));
                chargePs.setBigDecimal(i++, freight);
                chargePs.setBigDecimal(i++, gst);
                chargePs.setBigDecimal(i++, net);
                chargePs.setTimestamp(i++, bookedAt);
                chargePs.setTimestamp(i, bookedAt);
                chargePs.addBatch();

                for (String[] step : statusPath(status)) {
                    UUID historyId = TimeOrderedUuid.generate();
                    Timestamp changedAt = Timestamp.from(bookedAt.toInstant()
                            .plus(Long.parseLong(step[2]), ChronoUnit.HOURS));
                    UUID actingBranch = "DELIVERED".equals(step[0]) || "OUT_FOR_DELIVERY".equals(step[0])
                            || "IN_SCAN".equals(step[0]) || "RETURNED".equals(step[0])
                            ? deliveryBranchId : bookingBranchId;
                    UUID changedBy = ctx.userIds.isEmpty() ? null
                            : ctx.userIds.get(rnd.nextInt(ctx.userIds.size()));

                    i = 1;
                    historyPs.setBytes(i++, TimeOrderedUuid.toBytes(historyId));
                    historyPs.setBytes(i++, TimeOrderedUuid.toBytes(ctx.companyId));
                    historyPs.setBytes(i++, TimeOrderedUuid.toBytes(shipmentId));
                    historyPs.setBytes(i++, TimeOrderedUuid.toBytes(actingBranch));
                    historyPs.setString(i++, step[0]);
                    if (step[1] == null) {
                        historyPs.setNull(i++, java.sql.Types.VARCHAR);
                    } else {
                        historyPs.setString(i++, step[1]);
                    }
                    historyPs.setBytes(i++, TimeOrderedUuid.toBytes(changedBy));
                    historyPs.setTimestamp(i++, changedAt);
                    historyPs.setTimestamp(i++, changedAt);
                    historyPs.setTimestamp(i, changedAt);
                    historyPs.addBatch();
                    ctx.statusHistoryRowCount++;
                }

                ctx.shipmentIds.add(shipmentId);
                ctx.shipmentBookingBranchIdx.add(branchIdx);
                ctx.shipmentNetAmount.add(net);
                ctx.shipmentCollectAtBooking.add(ctx.paymentModeCollectAtBooking.get(paymentIdx));

                if (s % batchSize == 0) {
                    shipmentPs.executeBatch();
                    itemPs.executeBatch();
                    chargePs.executeBatch();
                    historyPs.executeBatch();
                }
            }
            shipmentPs.executeBatch();
            itemPs.executeBatch();
            chargePs.executeBatch();
            historyPs.executeBatch();
        }
    }

    /**
     * {status, previousStatus, hoursAfterBooking} triples forming the transition path a
     * shipment at {@code finalStatus} must have walked, per {@code ShipmentStatus
     * .canTransitionTo}'s graph — BOOKED always first, everything else in the same order
     * the real state machine allows.
     */
    private static String[][] statusPath(String finalStatus) {
        return switch (finalStatus) {
            case "BOOKED" -> new String[][]{{"BOOKED", null, "0"}};
            case "CANCELLED" -> new String[][]{{"BOOKED", null, "0"}, {"CANCELLED", "BOOKED", "4"}};
            case "READY_FOR_MANIFEST" -> new String[][]{
                    {"BOOKED", null, "0"}, {"READY_FOR_MANIFEST", "BOOKED", "3"}};
            case "MANIFEST_CREATED" -> new String[][]{
                    {"BOOKED", null, "0"}, {"MANIFEST_CREATED", "BOOKED", "3"}};
            case "DISPATCHED" -> new String[][]{
                    {"BOOKED", null, "0"}, {"MANIFEST_CREATED", "BOOKED", "3"},
                    {"DISPATCHED", "MANIFEST_CREATED", "6"}};
            case "IN_SCAN" -> new String[][]{
                    {"BOOKED", null, "0"}, {"MANIFEST_CREATED", "BOOKED", "3"},
                    {"DISPATCHED", "MANIFEST_CREATED", "6"}, {"IN_SCAN", "DISPATCHED", "24"}};
            case "OUT_FOR_DELIVERY" -> new String[][]{
                    {"BOOKED", null, "0"}, {"MANIFEST_CREATED", "BOOKED", "3"},
                    {"DISPATCHED", "MANIFEST_CREATED", "6"}, {"IN_SCAN", "DISPATCHED", "24"},
                    {"OUT_FOR_DELIVERY", "IN_SCAN", "30"}};
            case "RETURNED" -> new String[][]{
                    {"BOOKED", null, "0"}, {"MANIFEST_CREATED", "BOOKED", "3"},
                    {"DISPATCHED", "MANIFEST_CREATED", "6"}, {"IN_SCAN", "DISPATCHED", "24"},
                    {"RETURNED", "IN_SCAN", "48"}};
            case "DELIVERED" -> new String[][]{
                    {"BOOKED", null, "0"}, {"MANIFEST_CREATED", "BOOKED", "3"},
                    {"DISPATCHED", "MANIFEST_CREATED", "6"}, {"IN_SCAN", "DISPATCHED", "24"},
                    {"OUT_FOR_DELIVERY", "IN_SCAN", "30"}, {"DELIVERED", "OUT_FOR_DELIVERY", "36"}};
            default -> throw new IllegalStateException("Unhandled status " + finalStatus);
        };
    }

    private void insertWalletTransactions(Connection cx, CompanyCtx ctx) throws SQLException {
        String sql = """
                INSERT INTO wallet_transactions
                    (id, company_id, wallet_id, transaction_no, transaction_type,
                     sub_transaction_type, amount, balance_before, balance_after,
                     reference_type, reference_id, created_at, updated_at)
                VALUES (?,?,?,?,'DR','SBK',?,?,?,'SHIPMENT',?,?,?)
                """;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int batchSize = props.getBatchSize();
        int target = props.getWalletTransactionsPerCompany();
        int written = 0;

        // Only prepaid-at-booking shipments generate a booking debit — same rule the
        // real app follows (ShipmentBookingWalletListener). If fewer prepaid shipments
        // exist than the target count, every eligible shipment gets one and the target
        // is simply not reached (reported in the per-company log line).
        try (PreparedStatement ps = cx.prepareStatement(sql)) {
            for (int idx = 0; idx < ctx.shipmentIds.size() && written < target; idx++) {
                if (!ctx.shipmentCollectAtBooking.get(idx)) {
                    continue;
                }
                int branchIdx = ctx.shipmentBookingBranchIdx.get(idx);
                BigDecimal amount = ctx.shipmentNetAmount.get(idx);
                BigDecimal before = ctx.walletRunningBalance.get(branchIdx);
                BigDecimal after = before.subtract(amount);
                if (after.compareTo(BigDecimal.ZERO) < 0) {
                    continue; // wallet exhausted for this branch — skip rather than violate the CHECK
                }
                ctx.walletRunningBalance.set(branchIdx, after);

                UUID txnId = TimeOrderedUuid.generate();
                Timestamp now = Timestamp.from(Instant.now());
                int i = 1;
                ps.setBytes(i++, TimeOrderedUuid.toBytes(txnId));
                ps.setBytes(i++, TimeOrderedUuid.toBytes(ctx.companyId));
                ps.setBytes(i++, TimeOrderedUuid.toBytes(ctx.walletIds.get(branchIdx)));
                ps.setString(i++, WalletNumberGenerator.transactionNumber());
                ps.setBigDecimal(i++, amount);
                ps.setBigDecimal(i++, before);
                ps.setBigDecimal(i++, after);
                ps.setString(i++, ctx.shipmentIds.get(idx).toString());
                ps.setTimestamp(i++, now);
                ps.setTimestamp(i, now);
                ps.addBatch();
                written++;
                ctx.walletTxnRowCount++;

                if (written % batchSize == 0) {
                    ps.executeBatch();
                }
            }
            ps.executeBatch();
        }

        // Persist the final running balance per wallet — keeps each wallet's own ledger
        // and balance internally consistent for Phase 7's concurrency tests to start from.
        String updateSql = "UPDATE wallets SET available_balance = ? WHERE id = ?";
        try (PreparedStatement ps = cx.prepareStatement(updateSql)) {
            for (int b = 0; b < ctx.walletIds.size(); b++) {
                ps.setBigDecimal(1, ctx.walletRunningBalance.get(b));
                ps.setBytes(2, TimeOrderedUuid.toBytes(ctx.walletIds.get(b)));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void insertTickets(Connection cx, CompanyCtx ctx, List<UUID> ticketCategoryIds)
            throws SQLException {
        String sql = """
                INSERT INTO tickets
                    (id, company_id, ticket_number, subject, description, category_id, priority,
                     status, related_shipment_id, related_branch_id, created_by_user_id,
                     created_at, updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                """;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int batchSize = props.getBatchSize();

        try (PreparedStatement ps = cx.prepareStatement(sql)) {
            for (int t = 1; t <= props.getTicketsPerCompany(); t++) {
                ctx.companyTicketSerial++;
                String ticketNumber = "TKT-%06d".formatted(ctx.companyTicketSerial);
                UUID categoryId = ticketCategoryIds.get(rnd.nextInt(ticketCategoryIds.size()));
                String priority = weightedPick(TICKET_PRIORITIES, TICKET_PRIORITY_WEIGHTS, rnd);
                String status = weightedPick(TICKET_STATUSES, TICKET_STATUS_WEIGHTS, rnd);
                UUID createdBy = ctx.userIds.get(rnd.nextInt(ctx.userIds.size()));
                UUID branchId = ctx.branchIds.get(rnd.nextInt(ctx.branchIds.size()));
                boolean linkShipment = !ctx.shipmentIds.isEmpty() && rnd.nextInt(100) < 50;

                UUID ticketId = TimeOrderedUuid.generate();
                Timestamp now = Timestamp.from(Instant.now());
                int i = 1;
                ps.setBytes(i++, TimeOrderedUuid.toBytes(ticketId));
                ps.setBytes(i++, TimeOrderedUuid.toBytes(ctx.companyId));
                ps.setString(i++, ticketNumber);
                ps.setString(i++, "Perf-test ticket #%d".formatted(t));
                ps.setString(i++, "Synthetic ticket generated for local load testing.");
                ps.setBytes(i++, TimeOrderedUuid.toBytes(categoryId));
                ps.setString(i++, priority);
                ps.setString(i++, status);
                if (linkShipment) {
                    UUID shipmentId = ctx.shipmentIds.get(rnd.nextInt(ctx.shipmentIds.size()));
                    ps.setBytes(i++, TimeOrderedUuid.toBytes(shipmentId));
                } else {
                    ps.setNull(i++, java.sql.Types.BINARY);
                }
                ps.setBytes(i++, TimeOrderedUuid.toBytes(branchId));
                ps.setBytes(i++, TimeOrderedUuid.toBytes(createdBy));
                ps.setTimestamp(i++, now);
                ps.setTimestamp(i, now);
                ps.addBatch();
                ctx.ticketRowCount++;

                if (t % batchSize == 0) {
                    ps.executeBatch();
                }
            }
            ps.executeBatch();
        }
    }

    /**
     * Writes the counter tables the real app's own generators read from
     * ({@code branch_shipment_sequences}/{@code company_shipment_sequences}/
     * {@code company_ticket_sequences}) forward to what this run consumed, so a real
     * booking made through the app after this run picks up the next number instead of
     * colliding with a synthetic one.
     */
    private void advanceSequences(Connection cx, CompanyCtx ctx) throws SQLException {
        String branchSeqSql = """
                INSERT INTO branch_shipment_sequences (branch_id, sequence_value) VALUES (?, ?)
                ON DUPLICATE KEY UPDATE sequence_value = GREATEST(sequence_value, VALUES(sequence_value))
                """;
        try (PreparedStatement ps = cx.prepareStatement(branchSeqSql)) {
            for (int b = 0; b < ctx.branchIds.size(); b++) {
                ps.setBytes(1, TimeOrderedUuid.toBytes(ctx.branchIds.get(b)));
                ps.setLong(2, ctx.branchShipmentSerial.get(b));
                ps.addBatch();
            }
            ps.executeBatch();
        }

        String companySeqSql = """
                INSERT INTO company_shipment_sequences (company_id, sequence_value) VALUES (?, ?)
                ON DUPLICATE KEY UPDATE sequence_value = GREATEST(sequence_value, VALUES(sequence_value))
                """;
        try (PreparedStatement ps = cx.prepareStatement(companySeqSql)) {
            ps.setBytes(1, TimeOrderedUuid.toBytes(ctx.companyId));
            ps.setLong(2, ctx.companyShipmentSerial);
            ps.executeUpdate();
        }

        String ticketSeqSql = """
                INSERT INTO company_ticket_sequences (company_id, sequence_value) VALUES (?, ?)
                ON DUPLICATE KEY UPDATE sequence_value = GREATEST(sequence_value, VALUES(sequence_value))
                """;
        try (PreparedStatement ps = cx.prepareStatement(ticketSeqSql)) {
            ps.setBytes(1, TimeOrderedUuid.toBytes(ctx.companyId));
            ps.setLong(2, ctx.companyTicketSerial);
            ps.executeUpdate();
        }
    }

    // ---------------------------------------------------------------- lookups / helpers

    private UUID fetchAnySubscriptionPlanId(Connection cx) throws SQLException {
        try (PreparedStatement ps = cx.prepareStatement("SELECT id FROM subscription_plans LIMIT 1");
             var rs = ps.executeQuery()) {
            if (!rs.next()) {
                throw new NoSuchElementException(
                        "No row in subscription_plans — create at least one plan before running "
                                + "the perf data generator (this app never seeds one via migration).");
            }
            return bytesToUuid(rs.getBytes(1));
        }
    }

    private List<UUID> fetchTicketCategoryIds(Connection cx) throws SQLException {
        List<UUID> ids = new ArrayList<>();
        try (PreparedStatement ps = cx.prepareStatement("SELECT id FROM ticket_categories WHERE active = TRUE");
             var rs = ps.executeQuery()) {
            while (rs.next()) {
                ids.add(bytesToUuid(rs.getBytes(1)));
            }
        }
        if (ids.isEmpty()) {
            throw new NoSuchElementException(
                    "No active row in ticket_categories — V39's own seed migration should have "
                            + "created 12; something removed them.");
        }
        return ids;
    }

    /** Inverse of {@link TimeOrderedUuid#toBytes}: big-endian 16 bytes back to a UUID. */
    private static UUID bytesToUuid(byte[] bytes) {
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(bytes);
        return new UUID(buf.getLong(), buf.getLong());
    }

    private static String weightedPick(String[] values, int[] weights, ThreadLocalRandom rnd) {
        int total = 0;
        for (int w : weights) {
            total += w;
        }
        int r = rnd.nextInt(total);
        int cumulative = 0;
        for (int i = 0; i < values.length; i++) {
            cumulative += weights[i];
            if (r < cumulative) {
                return values[i];
            }
        }
        return values[values.length - 1];
    }

    // ---------------------------------------------------------------- per-company state

    private static final class CompanyCtx {
        String companyCode;
        UUID companyId;

        final List<UUID> branchIds = new ArrayList<>();
        final List<String> branchCodes = new ArrayList<>();
        final List<UUID> walletIds = new ArrayList<>();
        final List<BigDecimal> walletRunningBalance = new ArrayList<>();
        final List<Long> branchShipmentSerial = new ArrayList<>();
        long companyShipmentSerial = 0;
        long companyTicketSerial = 0;

        final List<UUID> serviceTypeIds = new ArrayList<>();
        final List<UUID> packageTypeIds = new ArrayList<>();
        final List<UUID> paymentModeIds = new ArrayList<>();
        final List<Boolean> paymentModeCollectAtBooking = new ArrayList<>();

        final List<UUID> userIds = new ArrayList<>();

        final List<UUID> customerIds = new ArrayList<>();
        final List<UUID> customerAddressIds = new ArrayList<>();

        final List<UUID> shipmentIds = new ArrayList<>();
        final List<Integer> shipmentBookingBranchIdx = new ArrayList<>();
        final List<BigDecimal> shipmentNetAmount = new ArrayList<>();
        final List<Boolean> shipmentCollectAtBooking = new ArrayList<>();
        long statusHistoryRowCount = 0;
        long walletTxnRowCount = 0;
        long ticketRowCount = 0;
    }
}
