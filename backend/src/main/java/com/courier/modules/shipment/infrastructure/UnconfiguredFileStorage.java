package com.courier.modules.shipment.infrastructure;

import com.courier.modules.shipment.application.storage.FileStoragePort;
import com.courier.shared.exception.BusinessRuleException;
import lombok.extern.slf4j.Slf4j;

/**
 * The storage backend used when no bucket is configured. Refuses every upload.
 *
 * <p>Fails closed on purpose, the same reasoning as {@code UnconfiguredPaymentGateway}:
 * silently discarding an upload (or worse, keeping bytes only in memory and reporting
 * success) is a POD photo that looks captured but is not there for a dispute later. A
 * deployment without a bucket simply has no file upload; the plain-URL fields on
 * {@code DeliverRequest}/{@code ShipmentDocument} still work.
 */
@Slf4j
public class UnconfiguredFileStorage implements FileStoragePort {

    private static final String MESSAGE =
            "File upload is not available: no storage backend is configured for this "
                    + "deployment. A URL can still be attached directly.";

    @Override
    public StoredFile upload(UploadRequest request) {
        log.warn("File upload refused: no storage backend configured (filename {})", request.filename());
        throw new BusinessRuleException(MESSAGE);
    }
}
