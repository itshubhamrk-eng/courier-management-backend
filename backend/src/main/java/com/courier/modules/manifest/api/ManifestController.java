package com.courier.modules.manifest.api;

import com.courier.modules.manifest.api.dto.CreateManifestRequest;
import com.courier.modules.manifest.api.dto.ManifestResponse;
import com.courier.modules.manifest.api.dto.ManifestSearchRequest;
import com.courier.modules.manifest.application.ManifestService;
import com.courier.modules.manifest.domain.Manifest;
import com.courier.modules.shipment.api.ShipmentMapper;
import com.courier.modules.shipment.api.dto.ShipmentSummaryResponse;
import com.courier.modules.shipment.application.ShipmentService;
import com.courier.modules.shipment.domain.Shipment;
import com.courier.modules.shipment.domain.ShipmentCriteria;
import com.courier.shared.api.ApiResponse;
import com.courier.shared.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The minimal Manifest module Shipment Movement needs underneath it — see {@code Manifest}'s
 * own class-level note. Not part of the brief's own REST API list (which starts at
 * {@code /shipment-movement/out-scan}, assuming a manifest already exists); this is the
 * prerequisite that makes one.
 */
@RestController
@RequestMapping("/api/v1/manifests")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Manifest", description = "Group shipments travelling one branch-to-branch run")
public class ManifestController {

    private final ManifestService manifestService;
    private final ManifestMapper mapper;
    private final ShipmentService shipmentService;
    private final ShipmentMapper shipmentMapper;

    @PostMapping
    @Operation(summary = "Create a manifest",
            description = "Every shipment id must be BOOKED and travel exactly this "
                    + "booking/delivery branch pair — each is transitioned to MANIFEST_CREATED.")
    public ResponseEntity<ApiResponse<ManifestResponse>> create(
            @Valid @RequestBody CreateManifestRequest request) {
        Manifest created = manifestService.create(mapper.toCommand(request));
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/manifests/{id}")
                        .buildAndExpand(created.getId()).toUri())
                .body(ApiResponse.success(mapper.toResponse(created), "Manifest created"));
    }

    @GetMapping("/{id}")
    public ApiResponse<ManifestResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(manifestService.getById(id)));
    }

    @GetMapping
    @Operation(summary = "List manifests", description = "Paged, filtered by status/branch/search.")
    public ApiResponse<PageResponse<ManifestResponse>> list(
            @ParameterObject ManifestSearchRequest search,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        Page<Manifest> page = manifestService.search(mapper.toCriteria(search), pageable);
        return ApiResponse.success(PageResponse.from(page, mapper::toResponse));
    }

    @GetMapping("/{id}/shipments")
    @Operation(summary = "List the shipments scanned onto this manifest",
            description = "Out Scan's own \"Search Manifest -> Display Shipments\" and Dispatch's "
                    + "manifest picker both read this.")
    public ApiResponse<List<ShipmentSummaryResponse>> shipments(@PathVariable UUID id) {
        manifestService.getById(id); // 404s a foreign/unknown manifest before listing anything
        ShipmentCriteria criteria = new ShipmentCriteria(null, null, null, id, null, null, null);
        Page<Shipment> page = shipmentService.search(criteria, Pageable.unpaged());
        Map<UUID, BigDecimal> netAmounts = shipmentService.netAmountsFor(
                page.getContent().stream().map(Shipment::getId).toList());
        List<ShipmentSummaryResponse> summaries = page.getContent().stream()
                .map(s -> shipmentMapper.toSummary(s, netAmounts.get(s.getId())))
                .toList();
        return ApiResponse.success(summaries);
    }
}
