package com.courier.modules.support.application.command;

import com.courier.modules.support.domain.TicketPriority;

/** Create-or-replace: one rule per (company, priority) — see {@link
 *  com.courier.modules.support.domain.TicketSlaRule}. */
public record UpsertSlaRuleCommand(
        TicketPriority priority, int firstResponseMinutes, int resolutionMinutes) {
}
