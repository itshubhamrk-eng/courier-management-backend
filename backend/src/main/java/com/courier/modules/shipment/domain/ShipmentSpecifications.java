package com.courier.modules.shipment.domain;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Criteria API predicates for the shipment search. All combine with {@code AND}; an
 * absent filter contributes nothing. Company scoping is the Hibernate filter's job, not
 * this class's, the same convention {@code CustomerSpecifications} follows.
 */
public final class ShipmentSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private ShipmentSpecifications() {
    }

    public static Specification<Shipment> matching(ShipmentCriteria criteria) {
        ShipmentCriteria safe = criteria == null ? ShipmentCriteria.none() : criteria;

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (safe.statuses() != null && !safe.statuses().isEmpty()) {
                predicates.add(root.get("status").in(safe.statuses()));
            }
            if (safe.bookingBranchId() != null) {
                predicates.add(cb.equal(root.get("bookingBranchId"), safe.bookingBranchId()));
            }
            if (safe.deliveryBranchId() != null) {
                predicates.add(cb.equal(root.get("deliveryBranchId"), safe.deliveryBranchId()));
            }
            if (safe.currentLocationId() != null) {
                predicates.add(cb.equal(root.get("currentLocationId"), safe.currentLocationId()));
            }
            if (safe.nextLocationId() != null) {
                predicates.add(cb.equal(root.get("nextLocationId"), safe.nextLocationId()));
            }
            if (safe.manifestId() != null) {
                predicates.add(cb.equal(root.get("manifestId"), safe.manifestId()));
            }
            if (safe.bookingDateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("bookingDate"), safe.bookingDateFrom()));
            }
            if (safe.bookingDateTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("bookingDate"), safe.bookingDateTo()));
            }
            if (hasText(safe.search())) {
                String pattern = "%" + escapeLike(safe.search().trim().toLowerCase()) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("shipmentNumber")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("trackingNumber")), pattern, LIKE_ESCAPE)));
            }

            return predicates.isEmpty()
                    ? cb.conjunction()
                    : cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String escapeLike(String raw) {
        return raw.replace(String.valueOf(LIKE_ESCAPE), LIKE_ESCAPE + "\\")
                .replace("%", LIKE_ESCAPE + "%")
                .replace("_", LIKE_ESCAPE + "_");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
