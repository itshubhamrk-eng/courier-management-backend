package com.courier.modules.crossing.application;

import com.courier.modules.crossing.domain.ShipmentExceptionCriteria;
import com.courier.modules.crossing.domain.ShipmentExceptionType;
import com.courier.modules.crossing.domain.ShipmentException;
import com.courier.modules.shipment.application.ShipmentService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * The operational surface Hub Operations adds on top of what already exists — in-scan
 * (reuses {@code ShipmentService.inScan}/crossing's own {@code arriveAt}), Load Sheet
 * creation and dispatch (reuse {@code ManifestService} as-is), and shipment/destination
 * listing (reuse {@code GET /shipments} and {@code GET /manifests/eligible-destinations}).
 * What's genuinely new: out-scan (duplicate-scan-before-dispatch), exceptions, and a
 * hub-scoped dashboard rollup.
 */
public interface HubOperationsService {

    /**
     * Out-scans every given tracking number against {@code manifestId} — each must
     * belong to this company, be attached to this manifest, and be {@code
     * MANIFEST_CREATED}. A tracking number already out-scanned for this manifest is
     * rejected as a duplicate, backed by {@code hub_out_scans}' own unique constraint.
     */
    List<ShipmentService.MovementOutcome> outScan(UUID manifestId, UUID hubBranchId, List<String> trackingNumbers);

    /** Raises an exception against a shipment at a hub. Never touches {@code
     *  Shipment.status} — see {@code ShipmentException}'s own doc. Best-effort raises a
     *  support ticket too, exactly like {@code ShipmentServiceImpl.raiseShortageTicket}. */
    ShipmentException raiseException(UUID shipmentId, UUID hubBranchId, ShipmentExceptionType type, String remarks);

    /** Closes an open exception. Refused once already resolved. */
    ShipmentException resolveException(UUID id, String resolutionRemarks);

    ShipmentException getException(UUID id);

    Page<ShipmentException> searchExceptions(ShipmentExceptionCriteria criteria, Pageable pageable);

    /** The nine Hub Dashboard figures for one hub, "today" in the server's own zone. */
    HubDashboardStats dashboard(UUID hubBranchId);
}
