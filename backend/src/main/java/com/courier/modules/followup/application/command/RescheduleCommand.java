package com.courier.modules.followup.application.command;

import java.time.Instant;

public record RescheduleCommand(Instant newDueDate, String reason) {
}
