package com.courier.modules.support.application.command;

import java.util.UUID;

/** Body of assign/reassign/escalate — {@code assigneeUserId} is required for assign/reassign,
 *  optional for escalate (a null value keeps the current assignee, only raises the flag). */
public record AssignmentCommand(UUID assigneeUserId, String remarks) {
}
