package com.courier.modules.master.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Filter criteria for any master-data search. Every field optional; null means "do not
 * constrain".
 *
 * <p>One record for twelve lists. The parts they share — company, status, free text, an
 * explicit id scope — are named fields. The parts that differ — a parent id, a zone, a
 * weight unit, a branch — go in {@link #equalities}, keyed by the <b>JPA attribute name</b>
 * of the entity being searched.
 *
 * <p><b>Those keys are always code constants</b>, supplied by the mapper of the list being
 * searched (e.g. {@code "countryId"}), never a caller-supplied string. That is what makes
 * the generic {@code root.get(key)} in {@link MasterDataSpecifications} safe: an unknown
 * attribute would be a programming error caught by the first test of that list, not a way
 * for a request to reach a column it was not offered.
 *
 * @param companyId   restrict to one company — meaningful only for a SUPER_ADMIN; a company
 *                   user's is pinned to their own by the service
 * @param statuses   match any of these statuses
 * @param search     free text over code, name and description
 * @param ids        restrict to these ids; an <b>empty</b> set matches nothing, which is
 *                   the correct answer for a scope that resolved to none
 * @param equalities extra equality filters, attribute name to value
 */
public record MasterDataCriteria(
        UUID companyId,
        Set<MasterStatus> statuses,
        String search,
        Set<UUID> ids,
        Map<String, Object> equalities
) {

    public MasterDataCriteria {
        equalities = equalities == null ? Map.of() : Map.copyOf(equalities);
    }

    public static MasterDataCriteria none() {
        return new MasterDataCriteria(null, null, null, null, Map.of());
    }

    public static MasterDataCriteria of(UUID companyId, Set<MasterStatus> statuses, String search) {
        return new MasterDataCriteria(companyId, statuses, search, null, Map.of());
    }

    public MasterDataCriteria withCompanyId(UUID enforced) {
        return new MasterDataCriteria(enforced, statuses, search, ids, equalities);
    }

    public MasterDataCriteria withIds(Set<UUID> enforced) {
        return new MasterDataCriteria(companyId, statuses, search, enforced, equalities);
    }

    /**
     * Adds one equality filter. A null {@code value} is dropped rather than stored, so a
     * mapper can chain every optional filter unconditionally and an absent query parameter
     * never becomes {@code column IS NULL}.
     */
    public MasterDataCriteria with(String attribute, Object value) {
        if (value == null) {
            return this;
        }
        Map<String, Object> merged = new LinkedHashMap<>(equalities);
        merged.put(attribute, value);
        return new MasterDataCriteria(companyId, statuses, search, ids, merged);
    }
}
