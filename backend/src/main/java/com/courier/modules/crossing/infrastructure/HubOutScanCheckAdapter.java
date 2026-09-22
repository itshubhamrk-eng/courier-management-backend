package com.courier.modules.crossing.infrastructure;

import com.courier.modules.crossing.domain.HubOutScanRepository;
import com.courier.modules.manifest.domain.HubOutScanCheckPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Answers Manifest's "has every shipment on this hub-originated manifest been
 *  out-scanned yet" question. See {@code CrossingBranchDirectory} for the identical
 *  consumer-owns-the-port arrangement this mirrors. */
@Component
@RequiredArgsConstructor
public class HubOutScanCheckAdapter implements HubOutScanCheckPort {

    private final HubOutScanRepository hubOutScanRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean allScanned(UUID companyId, UUID manifestId, List<UUID> shipmentIds) {
        if (shipmentIds == null || shipmentIds.isEmpty()) {
            return true;
        }
        Set<UUID> scanned = Set.copyOf(
                hubOutScanRepository.findScannedShipmentIds(companyId, manifestId, shipmentIds));
        return scanned.containsAll(shipmentIds);
    }
}
