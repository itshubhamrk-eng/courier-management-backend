package com.courier.modules.master.domain;

import java.util.UUID;

/**
 * The six geography lists are <b>global</b>: one country, state, district, city, area
 * and pincode catalogue shared by every company, written only by a {@code SUPER_ADMIN}
 * and readable by anyone signed in.
 *
 * <p>That is a change from how Master Data shipped. Per-company geography was defensible
 * on paper — a company could name its cities the way its own paperwork does — and wrong
 * in practice: {@code PUNE} meant a different row in every company, so no rate card,
 * serviceability check or report could ever be compared across two of them, and every
 * new company started with an empty map of the country it operates in.
 *
 * <h2>Why the rows still carry an owner column</h2>
 *
 * <p>Physically these tables are unchanged: {@code company_id NOT NULL}, the same unique
 * keys, the same Hibernate filter. Global rows are owned by the reserved id below, and
 * every read and write of a geography list binds that id instead of the caller's.
 *
 * <p>The alternative — a second entity hierarchy with no owner column — means the shared
 * head, the shared repository, the shared specification and the shared service can no
 * longer serve all twelve lists, because Java has one superclass. That is the machinery
 * decision 42 exists to protect. One reserved owner keeps twelve lists on one
 * implementation and costs a constant.
 *
 * <p>What the constant buys, concretely:
 * <ul>
 *   <li>{@code (company_id, code)} is already unique, so with one owner it <em>is</em> a
 *       global unique on code — no migration needed to make codes platform-wide.</li>
 *   <li>The Hibernate filter still runs. A geography read is filtered to this id, so a
 *       bug that forgot to bind it returns nothing rather than everything.</li>
 *   <li>{@code V12} rewrote the existing per-company rows onto it, deduplicating by code
 *       and repointing children at the survivor.</li>
 * </ul>
 *
 * <p>The id is a nil-adjacent constant, not a generated UUIDv7, precisely so that it is
 * recognisable at a glance in a row nobody expected and can never collide with a real
 * company id.
 */
public final class GlobalMasters {

    /**
     * Owner of every global master row. Deliberately not a valid time-ordered UUID:
     * a real {@code companyId} always has version 7 in it, so this cannot be mistaken
     * for one, and {@code CompanyRepository.isCompanyIdTaken} can never generate it.
     */
    public static final UUID PLATFORM_COMPANY_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private GlobalMasters() {
    }

    public static boolean isPlatformOwned(UUID companyId) {
        return PLATFORM_COMPANY_ID.equals(companyId);
    }
}
