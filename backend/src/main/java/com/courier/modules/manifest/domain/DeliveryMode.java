package com.courier.modules.manifest.domain;

/**
 * Who is responsible for getting a Load Sheet's shipments to the receiver, decided at
 * Load Sheet creation — never at booking (see {@code Shipment.deliveryBranchId}'s own
 * doc). {@code BRANCH_DELIVERY} is the original, only-ever-existing shape: a real
 * {@code Manifest.deliveryBranchId} receives the shipment (dispatch, in-scan, out for
 * delivery, deliver — unchanged). {@code DIRECT_COMPANY_DELIVERY} has no delivery branch
 * at all — the company's own vehicle/driver, assigned at dispatch exactly like
 * {@code BRANCH_DELIVERY}'s vehicle/driver, carries the shipment the rest of the way
 * itself. See {@code ShipmentServiceImpl.markPickedUpForDirectDelivery} for how a
 * DIRECT_COMPANY_DELIVERY shipment reaches {@code IN_SCAN} with no branch ever touching
 * it, and {@code ShipmentServiceImpl.deliver}/{@code creditDeliveryCommissionsIfEligible}
 * for which delivery-branch-attributed money (TO_PAY debit, COD debit, DRS charge,
 * delivery-weight commission) is deliberately skipped rather than attributed to a branch
 * that doesn't exist for this shipment.
 */
public enum DeliveryMode {
    BRANCH_DELIVERY,
    DIRECT_COMPANY_DELIVERY
}
