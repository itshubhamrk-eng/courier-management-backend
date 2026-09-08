package com.courier.modules.charge.application.command;

import java.util.UUID;

public record UpdateChargeCommand(
        String chargeName,
        UUID serviceTypeId,
        Long expectedVersion
) {
}
