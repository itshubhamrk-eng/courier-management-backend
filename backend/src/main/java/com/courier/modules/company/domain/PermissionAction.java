package com.courier.modules.company.domain;

/**
 * What may be done to a resource. Second half of a permission code.
 *
 * <p>{@link #READ} and {@link #SEARCH} are separate on purpose: reading one shipment you
 * were given the number for is not the same right as listing every shipment in the
 * company, and support staff frequently get the first without the second.
 */
public enum PermissionAction {

    CREATE(1),
    READ(2),
    UPDATE(3),
    DELETE(4),
    /** List and filter across the whole resource, as opposed to reading one row. */
    SEARCH(5),
    EXPORT(6),
    IMPORT(7),
    APPROVE(8),
    REJECT(9),
    PRINT(10),
    UPLOAD(11),
    DOWNLOAD(12),
    ASSIGN(13),
    ACTIVATE(14),
    DEACTIVATE(15),

    /** Extend a paid window. Subscription-only; nothing else in the system renews. */
    RENEW(16),

    /**
     * Stop something punitively, as distinct from {@code DEACTIVATE}, which merely
     * switches it off. The two are separate rights because they are separate decisions:
     * suspending a paying customer is escalated, deactivating a dormant one is not.
     */
    SUSPEND(17),

    /**
     * Send a closed manifest, or a set of shipments, out of the building.
     *
     * <p>Not {@code UPDATE}: dispatching is the point of no return in a branch's day —
     * the goods have left, the manifest can no longer be edited, and the branch that
     * receives it is now expecting them. A desk that may correct a manifest's contents is
     * not thereby a desk that may declare it gone.
     */
    DISPATCH(18),

    /**
     * Take an inbound manifest in and account for what actually arrived. The other half
     * of {@link #DISPATCH}, and a separate right because it is a separate desk: the
     * branch that sends is rarely the branch that receives.
     */
    RECEIVE(19),

    /**
     * Close a shipment against the consignee — proof of delivery, and for a {@code TO_PAY}
     * booking the moment the delivery branch's wallet is debited. Distinct from
     * {@code UPDATE} for exactly that reason: it moves money, and only the delivery desk
     * may do it.
     */
    DELIVER(20),

    /**
     * Put money into a prepaid wallet. Separate from {@code UPDATE} because a wallet is
     * never updated — decision 35 — and separate from {@code CREATE} because a wallet is
     * created with its branch and by nobody else. A branch tops up its own wallet; that
     * is a right, not a side effect of being able to read the balance.
     */
    RECHARGE(21),

    /**
     * Price a shipment without booking it. Read-only — it looks up a matching rate and
     * returns a quote, and touches nothing. Its own right rather than folded into
     * {@code READ} because a counter clerk who may quote a price is not thereby someone
     * who should see the whole rate card, and vice versa.
     */
    CALCULATE(22);

    private final int offset;

    PermissionAction(int offset) {
        this.offset = offset;
    }

    /** Added to the module's base order so a permission list reads CRUD-first. */
    public int displayOffset() {
        return offset;
    }

    /** True for the actions that change state — the ones worth a second look in a review. */
    public boolean isMutating() {
        return switch (this) {
            case READ, SEARCH, EXPORT, PRINT, DOWNLOAD, CALCULATE -> false;
            default -> true;
        };
    }
}
