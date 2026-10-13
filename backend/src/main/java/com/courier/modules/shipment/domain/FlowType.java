package com.courier.modules.shipment.domain;

/**
 * Which operating flow a shipment (and the manifest that carries it) belongs to.
 *
 * <p>{@link #STANDARD} is the original booking / Load Sheet / THC flow and the default for
 * every row that existed before {@code V104}. {@link #MSRTC} reuses the same tables and
 * statuses but books straight to a delivery branch and only ever moves branch to branch.
 */
public enum FlowType {
    STANDARD,
    MSRTC
}
