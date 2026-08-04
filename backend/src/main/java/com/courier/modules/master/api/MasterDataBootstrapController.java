package com.courier.modules.master.api;

import com.courier.modules.master.api.dto.MasterBootstrapResponse;
import com.courier.modules.master.application.MasterDataBootstrapService;
import com.courier.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fills the flat catalogues with the industry-standard set for the calling company.
 *
 * <p>A single explicit action rather than automatic seeding during company provisioning:
 * that would have pointed {@code modules/company} at a module it knows nothing about, and
 * it would have left every company created before this release with empty lists anyway.
 *
 * <p>Idempotent. A code that already exists is skipped, never overwritten, so running it
 * twice changes nothing and it cannot resurrect an entry an administrator removed.
 */
@RestController
@RequestMapping("/api/v1/master/bootstrap")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Master Data - Bootstrap", description = "Seed the standard catalogues")
public class MasterDataBootstrapController {

    private final MasterDataBootstrapService service;

    @PostMapping
    @Operation(summary = "Seed the standard master catalogues",
            description = """
                    `COMPANY_ADMIN` only. Creates the standard vehicle types (BIKE, AUTO,
                    PICKUP, TRUCK, CONTAINER), package types (DOCUMENT, PARCEL, BOX, BAG,
                    PALLET), service types (SAME_DAY, EXPRESS, STANDARD, ECONOMY), payment
                    modes (PAID, TO_PAY, TBB, COD) and five kilogram weight slabs.

                    Idempotent: rows whose code already exists are reported in `skipped`.
                    The geography hierarchy is **not** seeded — there is no set of countries
                    and pincodes that is right for an arbitrary courier.
                    """)
    public ApiResponse<MasterBootstrapResponse> bootstrap() {
        MasterDataBootstrapService.BootstrapResult result = service.seedDefaults();
        return ApiResponse.success(
                new MasterBootstrapResponse(result.created(), result.skipped()),
                "Master data catalogues seeded");
    }
}
