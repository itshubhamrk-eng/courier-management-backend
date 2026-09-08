-- =============================================================================
-- V59: delivery commission (DRS charge, weight-based delivery commission, and the
-- booking branch's TO_PAY/COD commission) now waits for POD approval instead of
-- crediting unconditionally at deliver() — direct user request. `pod_approved` is set
-- once a shipment's POD verification reaches PASS (AI auto-pass, manual review approve,
-- or a company-direct upload, which is always auto-approved); `commission_credited`
-- guards against crediting twice, since approval and delivery can now happen in either
-- order. Cash actually collected at delivery (CodCollectedAtDelivery, a debit not a
-- commission) is unaffected and still fires at deliver() regardless of POD status.
-- =============================================================================

ALTER TABLE shipments
    ADD COLUMN pod_approved BOOLEAN NOT NULL DEFAULT FALSE AFTER status,
    ADD COLUMN commission_credited BOOLEAN NOT NULL DEFAULT FALSE AFTER pod_approved;
