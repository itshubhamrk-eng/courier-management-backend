package com.courier.modules.support.application;

import com.courier.modules.support.application.command.UpsertSlaRuleCommand;
import com.courier.modules.support.domain.TicketPriority;
import com.courier.modules.support.domain.TicketSlaRule;

import java.util.List;
import java.util.UUID;

/** A company's own SLA targets, one row per {@link TicketPriority}. Reads: any
 *  authenticated company user (surfaced read-only on the ticket sidebar). Writes:
 *  {@code COMPANY_ADMIN} only. */
public interface TicketSlaRuleService {

    List<TicketSlaRule> list();

    /** Creates the rule for this priority if none exists yet, otherwise replaces it
     *  in place (same version-free "upsert" shape a settings row uses, not a ledger). */
    TicketSlaRule upsert(UpsertSlaRuleCommand command);

    TicketSlaRule setActive(UUID id, boolean active);
}
