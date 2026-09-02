package com.courier.modules.master.application;

import com.courier.modules.master.application.port.PincodePostalLookupProvider;
import com.courier.modules.master.application.port.PincodePostalLookupProvider.PostOffice;
import com.courier.modules.master.application.command.PincodeCommand;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.DuplicateResourceException;
import com.courier.shared.security.Roles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Bulk-seeds pincodes across a numeric range, driving the same postal-lookup + auto-create
 * pipeline the create form's single-pincode auto-fetch uses ({@link
 * GeographyAutoResolver}/{@link PincodePostalLookupProvider}), one candidate code at a time.
 * `MASTER_DATA_IMPORT` has sat in the permission catalogue since Master Data shipped with no
 * endpoint behind it — this is that endpoint, gated the same as every other Pincode write
 * ({@link PincodeServiceImpl}'s own {@code WRITE}), reached only through it.
 *
 * <p>Deliberately calls {@link PincodeService#create} — the real, proxied bean — rather than
 * duplicating row-construction here: that is what makes every existing rule (invariants,
 * uniqueness, audit trail) apply to a bulk-imported row exactly as it does to one typed by
 * hand, and what gives each row its own transaction (a cross-bean call goes through Spring's
 * proxy, so {@code create}'s own {@code @Transactional} applies per call — one long
 * transaction across thousands of rows would hold locks for the run's entire duration, which
 * a range spanning an hour or more cannot afford).
 *
 * <p>India's postal directory has no "list every pincode in a state" endpoint — only
 * per-pincode lookup — so a state's pincodes are found by probing a numeric range and keeping
 * only the codes that resolve to a real post office. A candidate already on file is skipped
 * without a network call at all (existence is inferred from {@link PincodeService#create}'s own
 * {@link DuplicateResourceException}, since re-running the same range twice — the point of this
 * being a normal endpoint, not a one-shot script — must not re-fetch or duplicate what a
 * previous run already created).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PincodeBulkImportService {

    private final PincodePostalLookupProvider postalLookup;
    private final GeographyAutoResolver geographyResolver;
    private final PincodeService pincodeService;

    /** Same write audience as every other Pincode mutation ({@link PincodeServiceImpl}'s own
     *  {@code WRITE}) — checked here too, not only inside the per-row {@code create()} call,
     *  so an under-privileged caller cannot even trigger the (network-costing) postal-directory
     *  probes for a range that would create nothing. */
    private static final String WRITE =
            "hasAnyRole('" + Roles.SUPER_ADMIN + "', '" + Roles.COMPANY_ADMIN + "')";

    public record Range(String fromCode, String toCode) {
    }

    @PreAuthorize(WRITE)
    public PincodeBulkImportResult importRanges(List<Range> ranges) {
        PincodeBulkImportResult total = PincodeBulkImportResult.empty();
        for (Range range : ranges) {
            total = total.plus(importRange(range));
        }
        return total;
    }

    private PincodeBulkImportResult importRange(Range range) {
        String from = requireDigits(range.fromCode(), "fromCode");
        String to = requireDigits(range.toCode(), "toCode");
        if (from.length() != to.length()) {
            throw new BusinessRuleException(
                    "fromCode and toCode must have the same number of digits (got '%s' and '%s')."
                            .formatted(from, to));
        }
        long start = Long.parseLong(from);
        long end = Long.parseLong(to);
        if (end < start) {
            throw new BusinessRuleException(
                    "toCode must not be before fromCode.");
        }
        int width = from.length();

        int probed = 0, created = 0, alreadyExisted = 0, noPostalMatch = 0, failed = 0;
        for (long n = start; n <= end; n++) {
            String code = ("%0" + width + "d").formatted(n);
            probed++;
            try {
                List<PostOffice> matches = postalLookup.lookup(code);
                if (matches.isEmpty()) {
                    noPostalMatch++;
                    continue;
                }
                PostOffice best = matches.get(0);
                var area = geographyResolver.resolveArea(best).area();
                PincodeCommand command = new PincodeCommand(code, best.name(), null, 0,
                        area.getId(), true, true, true, true, null, false, null);
                pincodeService.create(command);
                created++;
            } catch (DuplicateResourceException e) {
                alreadyExisted++;
            } catch (Exception e) {
                failed++;
                log.warn("Bulk pincode import: {} failed: {}", code, e.getMessage());
            }
        }
        log.info("Bulk pincode import {}-{}: probed={} created={} alreadyExisted={} "
                        + "noPostalMatch={} failed={}",
                from, to, probed, created, alreadyExisted, noPostalMatch, failed);
        return new PincodeBulkImportResult(probed, created, alreadyExisted, noPostalMatch, failed);
    }

    private static String requireDigits(String value, String field) {
        if (value == null || !value.matches("^[0-9]{4,10}$")) {
            throw new BusinessRuleException(
                    "%s must be 4 to 10 digits.".formatted(field));
        }
        return value;
    }
}
