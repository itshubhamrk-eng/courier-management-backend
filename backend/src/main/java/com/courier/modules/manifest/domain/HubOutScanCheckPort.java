package com.courier.modules.manifest.domain;

import java.util.List;
import java.util.UUID;

/**
 * What Manifest needs to know about Hub Operations' out-scan ledger — and nothing more.
 * The consumer (Manifest) owns this interface; {@code modules/crossing} supplies the
 * adapter, since it owns {@code HubOutScan}. Manifest never depends on Crossing's
 * entities or repositories directly.
 */
public interface HubOutScanCheckPort {

    /** True if every id in {@code shipmentIds} has been out-scanned against this
     *  manifest. Called only when the manifest's booking branch is a hub — see
     *  {@code ManifestServiceImpl.dispatch}. */
    boolean allScanned(UUID companyId, UUID manifestId, List<UUID> shipmentIds);
}
