package com.courier.modules.ewaybill.application.provider;

import lombok.extern.slf4j.Slf4j;

/**
 * The provider used when no government/GSP integration is configured for this
 * deployment — the default, and today the only one wired up (see
 * {@code EwayBillProviderConfig}).
 *
 * <p>Fails closed on purpose, same reasoning as {@code UnconfiguredPaymentGateway} and
 * {@code UnconfiguredFileStorage}: every mutating call returns a graceful failure
 * outcome rather than fabricating a fake E-Way Bill number. Unlike a payment gateway,
 * this failure must never propagate as an exception that could roll back the caller's
 * transaction — a shipment still books, and a manifest still dispatches, with the
 * E-Way Bill left {@code FAILED} and retryable once a real provider is configured.
 */
@Slf4j
public class UnconfiguredEwayBillProvider implements EwayBillProvider {

    private static final String NAME = "UNCONFIGURED";
    private static final String MESSAGE = "No E-Way Bill provider is configured for this deployment. "
            + "A company administrator can retry once one is; the shipment itself is not affected.";

    @Override
    public PartAResult generatePartA(PartARequest request) {
        log.warn("E-Way Bill Part-A generation refused: no provider configured (invoice {})",
                request == null ? null : request.invoiceNumber());
        return PartAResult.failure(NAME, MESSAGE);
    }

    @Override
    public PartBResult updatePartB(PartBRequest request) {
        log.warn("E-Way Bill Part-B update refused: no provider configured (number {})",
                request == null ? null : request.ewayBillNumber());
        return PartBResult.failure(MESSAGE);
    }

    @Override
    public StatusResult getStatus(String ewayBillNumber) {
        return StatusResult.notFound();
    }

    @Override
    public CancelResult cancel(String ewayBillNumber, String reason) {
        log.warn("E-Way Bill cancellation refused: no provider configured (number {})", ewayBillNumber);
        return CancelResult.failure(MESSAGE);
    }
}
