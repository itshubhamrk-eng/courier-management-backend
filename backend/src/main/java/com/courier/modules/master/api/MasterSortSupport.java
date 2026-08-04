package com.courier.modules.master.api;

import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Sort whitelist and page-size cap, shared by all twelve master endpoints.
 *
 * <p>The whitelist exists because {@code Pageable} binds whatever property name arrives in
 * the query string straight into a JPQL {@code order by}. Unchecked, a caller can sort by
 * a column that is not in the response and read it one row at a time from the ordering —
 * and an unmapped name is a 500. So an unknown sort is a 400 that names the allowed set,
 * not a silent fallback: a client whose sort quietly stopped working never finds out.
 *
 * <p>Every master row has the same head, so one map serves every list. A list with a
 * column worth sorting on beyond it passes its own additions.
 */
public final class MasterSortSupport {

    /** Available on every master list. */
    public static final Map<String, String> COMMON = Map.of(
            "code", "code",
            "name", "name",
            "status", "status",
            "displayOrder", "displayOrder",
            "createdDate", "createdAt",
            "createdAt", "createdAt",
            "updatedDate", "updatedAt");

    private static final int MAX_PAGE_SIZE = 100;

    private MasterSortSupport() {
    }

    public static Pageable sanitise(Pageable pageable) {
        return sanitise(pageable, COMMON);
    }

    /** @param sortable the allowed {@code request name -> entity attribute} pairs */
    public static Pageable sanitise(Pageable pageable, Map<String, String> sortable) {
        int size = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);

        List<Sort.Order> orders = pageable.getSort().stream()
                .map(order -> {
                    String attribute = sortable.get(order.getProperty());
                    if (attribute == null) {
                        throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                                "Cannot sort by '%s'. Allowed: %s"
                                        .formatted(order.getProperty(),
                                                String.join(", ", new TreeSet<>(sortable.keySet()))));
                    }
                    return new Sort.Order(order.getDirection(), attribute);
                })
                .toList();

        // Default: the order an administrator arranged the picker in, then alphabetical.
        Sort sort = orders.isEmpty()
                ? Sort.by(Sort.Order.asc("displayOrder"), Sort.Order.asc("name"))
                : Sort.by(orders);
        return PageRequest.of(pageable.getPageNumber(), size, sort);
    }

    /** {@link #COMMON} plus one more pair. */
    public static Map<String, String> withExtra(String requestName, String attribute) {
        Map<String, String> merged = new java.util.LinkedHashMap<>(COMMON);
        merged.put(requestName, attribute);
        return Map.copyOf(merged);
    }
}
