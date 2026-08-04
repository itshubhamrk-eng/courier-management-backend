package com.courier.modules.company.domain;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The roles every new company is seeded with.
 *
 * <p>Data, not code paths: this is the single place that says what a
 * {@code BOOKING_OPERATOR} can do on day one. A company may re-permission or deactivate
 * them afterwards; the catalogue is only the starting point.
 *
 * <p>{@link #permissionsFor} filters out plan-gated permissions the company's plan does
 * not enable, so a role can never be seeded with a right the subscription excludes.
 *
 * <p><b>Changed in Phase 3:</b> the original five became eight. {@code OPERATOR} split
 * into {@link #BOOKING_OPERATOR} and {@link #DELIVERY_OPERATOR} — booking a parcel and
 * delivering it are different desks with different rights, and one role covering both
 * meant every counter clerk could also close deliveries. {@link #FINANCE_USER} and
 * {@link #CUSTOMER_SERVICE} were added for the same reason: they were previously served
 * by handing someone {@code BRANCH_MANAGER}, which is far more than either needs.
 */
public final class DefaultRoleCatalog {

    /** Code of the role granted to the first user created with the company. */
    public static final String COMPANY_ADMIN = "COMPANY_ADMIN";
    public static final String BRANCH_MANAGER = "BRANCH_MANAGER";
    public static final String HUB_MANAGER = "HUB_MANAGER";
    public static final String BOOKING_OPERATOR = "BOOKING_OPERATOR";
    public static final String DELIVERY_OPERATOR = "DELIVERY_OPERATOR";
    public static final String FINANCE_USER = "FINANCE_USER";
    /** The branch's money desk: wallet, payments, invoices, COD reconciliation. */
    public static final String ACCOUNTS = "ACCOUNTS";
    public static final String CUSTOMER_SERVICE = "CUSTOMER_SERVICE";
    public static final String VIEWER = "VIEWER";

    /**
     * The roles a branch may put its own staff into.
     *
     * <p>A branch manager staffs a counter; they do not decide who administers the
     * company. So the roles they may hand out are these four plus any <em>custom</em>
     * (non-system) role the company has defined — see {@link #isBranchAssignable}. The
     * set is closed deliberately: leaving it open, or expressing it as "anything except
     * COMPANY_ADMIN", makes every future system role branch-assignable on the day it is
     * added rather than on the day someone decides it should be.
     */
    public static final Set<String> BRANCH_ROLE_CODES =
            Set.of(BRANCH_MANAGER, BOOKING_OPERATOR, DELIVERY_OPERATOR, ACCOUNTS);

    /**
     * Whether a branch manager may hand this role to their own staff.
     *
     * @param roleCode   the role being assigned
     * @param systemRole whether it is one of this catalogue's seeded roles, as opposed to
     *                   a role the company defined itself
     * @return true for any of {@link #BRANCH_ROLE_CODES}, or any non-system role — a
     *         company's own custom role carries no assumption about who may hold it, so a
     *         branch manager is not blocked from a role the company built for exactly this
     */
    public static boolean isBranchAssignable(String roleCode, boolean systemRole) {
        return !systemRole || BRANCH_ROLE_CODES.contains(roleCode);
    }

    /**
     * @param code        stable key, unique per company
     * @param name        human-readable label
     * @param description what the role is for, shown in the roles screen
     * @param type        functional grouping
     * @param defaultRole whether a new user gets this role when none is specified
     * @param permissions permission codes it starts with, before plan gating
     */
    public record RoleDefinition(String code,
                                 String name,
                                 String description,
                                 RoleType type,
                                 boolean defaultRole,
                                 Set<String> permissions) {
    }

    /**
     * Modules a company role may never hold anything from, however senior it is.
     *
     * <p>These are the platform operator's rights: putting a company on a plan, renewing
     * or suspending it, editing the geography every company shares, and creating other
     * platform operators. "Full control of the company" stops at the company — an admin
     * who could renew their own subscription or rename a city for every other customer
     * is not an admin of one company any more.
     */
    private static final Set<PermissionModule> PLATFORM_ONLY_MODULES = Set.of(
            PermissionModule.SUBSCRIPTION,
            PermissionModule.SUPER_ADMIN_USER,
            PermissionModule.GLOBAL_MASTER);

    /**
     * Rights on the {@code COMPANY} module that belong to the platform rather than to the
     * company itself. The module is not wholly excluded, because a company admin does
     * legitimately read and edit their own company record.
     */
    private static final Set<PermissionAction> PLATFORM_ONLY_COMPANY_ACTIONS = Set.of(
            PermissionAction.CREATE, PermissionAction.DELETE, PermissionAction.SEARCH,
            PermissionAction.EXPORT, PermissionAction.ACTIVATE, PermissionAction.DEACTIVATE,
            PermissionAction.SUSPEND);

    /**
     * Every code {@link DefaultPermissionCatalog} defines <em>except</em> the platform
     * operator's — what {@code COMPANY_ADMIN} starts with.
     *
     * <p>Derived rather than listed, so a permission added there is automatically
     * administrable and no company is left unable to use a new feature. The exclusion is
     * expressed the same way, in terms of modules and actions rather than a list of
     * codes, so a right added to a platform-only module is excluded on the day it is
     * added instead of the day someone notices.
     */
    private static final Set<String> ALL_PERMISSION_CODES = DefaultPermissionCatalog.matrix()
            .entrySet().stream()
            .filter(entry -> !PLATFORM_ONLY_MODULES.contains(entry.getKey()))
            .flatMap(entry -> entry.getValue().stream()
                    .filter(action -> !(entry.getKey() == PermissionModule.COMPANY
                            && PLATFORM_ONLY_COMPANY_ACTIONS.contains(action)))
                    .map(action -> Permission.codeFor(entry.getKey(), action)))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    private static final List<RoleDefinition> DEFINITIONS = List.of(

            new RoleDefinition(COMPANY_ADMIN, "Company Admin",
                    "Full control of the company: users, roles, branches, rates and settings.",
                    RoleType.ADMINISTRATION, false,
                    ALL_PERMISSION_CODES),

            // The branch's own eleven responsibilities, in order: staff it, decide what
            // those staff see, keep its wallet funded, book, manifest, load, dispatch,
            // receive, run deliveries out, close them, and report on all of it. See
            // MEMORY/modules/branch.md §"What a branch runs".
            new RoleDefinition(BRANCH_MANAGER, "Branch Manager",
                    "Runs one branch: its staff and menus, its wallet, and everything from "
                            + "booking to delivery.",
                    RoleType.OPERATIONS, false,
                    Set.of(
                            "COMPANY_READ", "SETTINGS_READ", "BRANCH_READ", "HUB_READ",
                            // 1. Create branch users — its own counter staff, and nobody else's.
                            "USER_READ", "USER_SEARCH", "USER_CREATE", "USER_UPDATE",
                            "USER_ASSIGN", "USER_ACTIVATE", "USER_DEACTIVATE",
                            // 2. Assign menus. MENU_ASSIGN, never PERMISSION_ASSIGN: the
                            // first hands a subordinate role screens the manager already
                            // reaches, the second opens the whole catalogue.
                            "ROLE_READ", "PERMISSION_READ", "MENU_READ", "MENU_ASSIGN",
                            // 3. Recharge the branch wallet.
                            "WALLET_READ", "WALLET_SEARCH", "WALLET_EXPORT", "WALLET_RECHARGE",
                            // 4. Book a shipment — and the customer half of the booking flow.
                            "CUSTOMER_READ", "CUSTOMER_SEARCH", "CUSTOMER_CREATE", "CUSTOMER_UPDATE", "CUSTOMER_DELETE",
                            "ADDRESS_READ", "ADDRESS_CREATE", "ADDRESS_UPDATE",
                            "SHIPMENT_READ", "SHIPMENT_SEARCH", "SHIPMENT_CREATE", "SHIPMENT_UPDATE",
                            "SHIPMENT_ASSIGN", "SHIPMENT_DELETE", "SHIPMENT_PRINT", "SHIPMENT_IMPORT",
                            "SHIPMENT_UPLOAD",
                            // 5-8. Manifest: create it, load a vehicle onto it, send it,
                            // take the inbound one in.
                            "MANIFEST_CREATE", "MANIFEST_READ", "MANIFEST_UPDATE", "MANIFEST_SEARCH",
                            "MANIFEST_PRINT", "MANIFEST_ASSIGN", "MANIFEST_DISPATCH", "MANIFEST_RECEIVE",
                            "VEHICLE_READ", "VEHICLE_ASSIGN", "DRIVER_READ", "DRIVER_CREATE", "DRIVER_UPDATE",
                            "DRIVER_DELETE", "DRIVER_ASSIGN",
                            // 9-10. Out for delivery, then delivered.
                            "DELIVERY_READ", "DELIVERY_SEARCH", "DELIVERY_CREATE", "DELIVERY_UPDATE",
                            "DELIVERY_ASSIGN", "DELIVERY_DISPATCH", "DELIVERY_DELIVER", "DELIVERY_UPLOAD",
                            "PICKUP_READ", "PICKUP_CREATE", "PICKUP_ASSIGN",
                            "TRACKING_CREATE", "TRACKING_READ", "TRACKING_SEARCH",
                            // 11. Reports.
                            "REPORT_READ", "REPORT_SEARCH", "REPORT_EXPORT", "REPORT_DOWNLOAD",
                            "REPORT_PRINT", "DASHBOARD_READ",
                            "MASTER_DATA_READ", "RATE_MASTER_READ", "RATE_MASTER_CALCULATE")),

            new RoleDefinition(HUB_MANAGER, "Hub Manager",
                    "Runs a sorting hub: inbound and outbound movement, scans and vehicles.",
                    RoleType.OPERATIONS, false,
                    Set.of(
                            "COMPANY_READ", "BRANCH_READ", "HUB_READ", "HUB_CREATE","HUB_UPDATE","HUB_DELETE", "DRIVER_READ", "VEHICLE_READ", "VEHICLE_CREATE","VEHICLE_UPDATE","VEHICLE_DELETE", "SHIPMENT_READ","SHIPMENT_SEARCH", "SHIPMENT_UPDATE", "SHIPMENT_ASSIGN", "TRACKING_CREATE","TRACKING_READ", "REPORT_READ","DASHBOARD_READ")),

            // The counter desk, and the whole of the booking flow: search a customer by
            // mobile, reuse or create, then book. CUSTOMER_DELETE is deliberately absent —
            // "search, reuse or create" never removes anyone, and a clerk who mistypes a
            // mobile should get a second customer record to merge, not a deleted one.
            new RoleDefinition(BOOKING_OPERATOR, "Booking Operator",
                    "Books shipments at a counter: finds or registers the customer, then "
                            + "creates the shipment.",
                    RoleType.OPERATIONS, true,
                    Set.of(
                            "COMPANY_READ", "BRANCH_READ", "MENU_READ",
                            "CUSTOMER_READ", "CUSTOMER_SEARCH", "CUSTOMER_CREATE", "CUSTOMER_UPDATE",
                            "ADDRESS_READ", "ADDRESS_CREATE", "ADDRESS_UPDATE",
                            "SHIPMENT_READ", "SHIPMENT_SEARCH", "SHIPMENT_CREATE", "SHIPMENT_UPDATE",
                            "SHIPMENT_PRINT", "SHIPMENT_IMPORT", "SHIPMENT_UPLOAD",
                            "PICKUP_READ", "PICKUP_CREATE",
                            "TRACKING_CREATE", "TRACKING_READ",
                            // A PAID booking debits this branch's wallet, so the clerk has
                            // to be able to see the balance they are spending. Reading it
                            // is not recharging it: WALLET_RECHARGE is the manager's.
                            "WALLET_READ",
                            "MASTER_DATA_READ", "RATE_MASTER_READ", "RATE_MASTER_CALCULATE",
                            "REPORT_READ", "DASHBOARD_READ")),

            // The road: takes the inbound manifest in, runs the delivery out, closes it.
            // Never creates or prices a shipment — and TO_PAY makes DELIVERY_DELIVER a
            // money act, which is why it is a right of its own and stops here.
            new RoleDefinition(DELIVERY_OPERATOR, "Delivery Operator",
                    "Receives inbound manifests, runs deliveries out and closes them with "
                            + "proof of delivery.",
                    RoleType.OPERATIONS, false,
                    Set.of(
                            "COMPANY_READ", "BRANCH_READ", "MENU_READ",
                            "CUSTOMER_READ", "SHIPMENT_READ", "SHIPMENT_SEARCH", "SHIPMENT_UPDATE",
                            "MANIFEST_READ", "MANIFEST_SEARCH", "MANIFEST_RECEIVE",
                            "DELIVERY_READ", "DELIVERY_SEARCH", "DELIVERY_UPDATE",
                            "DELIVERY_DISPATCH", "DELIVERY_DELIVER", "DELIVERY_UPLOAD",
                            "PICKUP_READ", "PICKUP_UPDATE",
                            "TRACKING_CREATE", "TRACKING_READ",
                            "REPORT_READ", "DASHBOARD_READ")),

            // The branch's money desk. Reads operations, moves nothing on the road.
            new RoleDefinition(ACCOUNTS, "Accounts",
                    "The branch's money desk: wallet top-ups, payments, invoices and "
                            + "COD reconciliation.",
                    RoleType.FINANCE, false,
                    Set.of(
                            "COMPANY_READ", "SETTINGS_READ", "BRANCH_READ", "MENU_READ",
                            "CUSTOMER_READ", "CUSTOMER_SEARCH",
                            "SHIPMENT_READ", "SHIPMENT_SEARCH",
                            "MANIFEST_READ", "MANIFEST_SEARCH",
                            "WALLET_READ", "WALLET_SEARCH", "WALLET_EXPORT", "WALLET_RECHARGE",
                            "PAYMENT_CREATE", "PAYMENT_READ", "PAYMENT_UPDATE", "PAYMENT_SEARCH",
                            "PAYMENT_EXPORT", "PAYMENT_PRINT",
                            "INVOICE_CREATE", "INVOICE_READ", "INVOICE_UPDATE", "INVOICE_SEARCH",
                            "INVOICE_EXPORT", "INVOICE_PRINT", "INVOICE_DOWNLOAD",
                            "MASTER_DATA_READ", "RATE_MASTER_READ",
                            "REPORT_READ", "REPORT_SEARCH", "REPORT_EXPORT", "REPORT_DOWNLOAD",
                            "REPORT_PRINT", "DASHBOARD_READ")),

            new RoleDefinition(FINANCE_USER, "Finance User",
                    "Rates, invoicing and COD reconciliation. Reads operations, changes money.",
                    RoleType.FINANCE, false,
                    Set.of(
                            "COMPANY_READ", "SETTINGS_READ", "BRANCH_READ", "HUB_READ", "CUSTOMER_READ", "SHIPMENT_READ","SHIPMENT_SEARCH", "MASTER_DATA_READ",
                            "RATE_MASTER_READ", "RATE_MASTER_CREATE", "RATE_MASTER_UPDATE", "RATE_MASTER_DELETE",
                            "RATE_MASTER_ACTIVATE", "RATE_MASTER_DEACTIVATE", "RATE_MASTER_CALCULATE",
                            "REPORT_READ","DASHBOARD_READ", "REPORT_EXPORT")),

            new RoleDefinition(CUSTOMER_SERVICE, "Customer Service",
                    "Answers tracking queries and complaints; may cancel a booking on request.",
                    RoleType.SUPPORT, false,
                    Set.of(
                            "COMPANY_READ", "BRANCH_READ", "HUB_READ", "CUSTOMER_READ", "CUSTOMER_CREATE","CUSTOMER_UPDATE","CUSTOMER_DELETE", "DRIVER_READ", "SHIPMENT_READ","SHIPMENT_SEARCH", "SHIPMENT_UPDATE", "SHIPMENT_DELETE", "MASTER_DATA_READ", "RATE_MASTER_READ", "REPORT_READ","DASHBOARD_READ")),

            new RoleDefinition(VIEWER, "Viewer",
                    "Read-only access for auditors, finance and support.",
                    RoleType.READ_ONLY, false,
                    Set.of(
                            "COMPANY_READ", "SETTINGS_READ", "USER_READ", "ROLE_READ", "BRANCH_READ", "HUB_READ", "CUSTOMER_READ", "DRIVER_READ", "VEHICLE_READ", "SHIPMENT_READ","SHIPMENT_SEARCH", "MASTER_DATA_READ", "RATE_MASTER_READ", "REPORT_READ","DASHBOARD_READ")));

    private DefaultRoleCatalog() {
    }

    public static List<RoleDefinition> definitions() {
        return DEFINITIONS;
    }

    /** Codes of the seeded roles, for the migration and for tests to check against. */
    public static Set<String> systemRoleCodes() {
        return DEFINITIONS.stream()
                .map(RoleDefinition::code)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /**
     * The permission codes to seed for one role, minus any whose feature flag the
     * company's plan does not enable.
     *
     * @param definition   the catalogue entry
     * @param featureFlags the plan's flags; a missing or non-{@code true} value denies,
     *                     so an unknown flag fails closed
     * @return a new mutable set, safe to hand to a caller
     */
    public static Set<String> permissionsFor(RoleDefinition definition,
                                             Map<String, Object> featureFlags) {
        Set<String> granted = new LinkedHashSet<>();
        for (String code : definition.permissions()) {
            String requiredFlag = DefaultPermissionCatalog.requiredFeatureFlag(code);
            if (requiredFlag == null || isFeatureEnabled(featureFlags, requiredFlag)) {
                granted.add(code);
            }
        }
        return granted;
    }

    private static boolean isFeatureEnabled(Map<String, Object> featureFlags, String flag) {
        return featureFlags != null && Boolean.TRUE.equals(featureFlags.get(flag));
    }
}
