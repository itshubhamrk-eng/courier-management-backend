package com.courier.modules.support.domain;

/** Whether the SLA-breach sweep is on for a company, plus its thresholds. */
public record ShipmentSlaConfig(boolean enabled, ShipmentSlaThresholds thresholds) {
}
