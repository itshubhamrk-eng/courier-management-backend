package com.courier.modules.master.api;

import com.courier.modules.master.api.dto.CountryResponse;
import com.courier.modules.master.api.dto.CreateCountryRequest;
import com.courier.modules.master.api.dto.MasterSearchRequest;
import com.courier.modules.master.api.dto.UpdateCountryRequest;
import com.courier.modules.master.application.CountryService;
import com.courier.modules.master.domain.Country;
import com.courier.shared.api.ApiResponse;
import com.courier.shared.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

/**
 * Countries — the root of the geography master.
 *
 * <p>All twelve master endpoints share this shape: create, update (full replacement with
 * an optimistic-lock {@code version}), read one, list, soft delete, activate, deactivate.
 * Authorisation is enforced on the service, not here: {@code COMPANY_ADMIN} writes, any
 * authenticated company user reads, {@code SUPER_ADMIN} reads across companies. The
 * company always comes from the JWT.
 *
 * <p>Delete is a soft delete and is refused while the row still has children — a country
 * with states cannot be removed. Cascading five levels of geography from one click is not
 * something anyone expects until it has happened to their production data.
 */
@RestController
@RequestMapping("/api/v1/global-masters/countries")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Global Masters - Countries", description = "Country master. Global: SUPER_ADMIN writes, anyone signed in reads.")
public class CountryController {

    private final CountryService service;
    private final CountryMasterMapper mapper;
    private final MasterCriteriaMapper criteriaMapper;

    @PostMapping
    @Operation(summary = "Create a country",
            description = "`COMPANY_ADMIN` only. Code and name are unique within the "
                    + "company, including against soft-deleted rows. The code is uppercased "
                    + "and immutable.")
    public ResponseEntity<ApiResponse<CountryResponse>> create(
            @Valid @RequestBody CreateCountryRequest request) {
        Country country = service.create(mapper.toCommand(request));
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/master/countries/{id}")
                        .buildAndExpand(country.getId()).toUri())
                .body(ApiResponse.success(mapper.toResponse(country), "Country created"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a country",
            description = "Full replacement of the editable fields. `version` is required; "
                    + "a stale value returns 409. The code cannot be changed.")
    public ApiResponse<CountryResponse> update(@PathVariable UUID id,
                                               @Valid @RequestBody UpdateCountryRequest request) {
        return ApiResponse.success(mapper.toResponse(service.update(id, mapper.toCommand(request))),
                "Country updated");
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a country", description = "Any authenticated company user.")
    public ApiResponse<CountryResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.getById(id)));
    }

    @GetMapping
    @Operation(summary = "List countries",
            description = """
                    Paged, sorted, filtered, searchable. Sort: `code`, `name`, `status`,
                    `displayOrder`, `createdDate`, `updatedDate`; the default is
                    `displayOrder` then `name`. `size` is capped at 100.
                    """)
    public ApiResponse<PageResponse<CountryResponse>> list(
            @Valid @ParameterObject MasterSearchRequest search,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(mapper.toPage(service.search(
                criteriaMapper.toCriteria(search), MasterSortSupport.sanitise(pageable))));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a country",
            description = "Soft delete, `COMPANY_ADMIN` only. Refused with 422 while the "
                    + "country still has states. The code stays reserved.")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.success("Country deleted");
    }

    @PatchMapping("/{id}/activate")
    @Operation(summary = "Activate a country", description = "`COMPANY_ADMIN`. Idempotent.")
    public ApiResponse<CountryResponse> activate(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.activate(id)), "Country activated");
    }

    @PatchMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a country",
            description = "`COMPANY_ADMIN`. Withdraws it from the pickers; existing "
                    + "references keep resolving, and nothing new may be filed under it. "
                    + "Idempotent.")
    public ApiResponse<CountryResponse> deactivate(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.deactivate(id)), "Country deactivated");
    }
}
