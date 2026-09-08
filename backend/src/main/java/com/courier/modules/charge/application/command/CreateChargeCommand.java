package com.courier.modules.charge.application.command;

import java.util.UUID;

public record CreateChargeCommand(
        String chargeName,
        UUID serviceTypeId
) {
}
