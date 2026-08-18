package com.courier.modules.followup.application;

import com.courier.modules.followup.application.command.CreateFollowUpCommand;
import com.courier.modules.followup.application.command.RescheduleCommand;
import com.courier.modules.followup.application.command.UpdateFollowUpCommand;
import com.courier.modules.followup.domain.FollowUp;
import com.courier.modules.followup.domain.FollowUpCriteria;
import com.courier.modules.followup.domain.FollowUpHistory;
import com.courier.modules.followup.domain.FollowUpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/** Follow-up Management: create -> (assign) -> in progress -> reschedule/notes ->
 *  completed/cancelled, company- and branch-isolated. See {@code FollowUpServiceImpl}
 *  for the full scoping/authorisation story. */
public interface FollowUpService {

    FollowUp create(CreateFollowUpCommand command);

    FollowUp update(UUID id, UpdateFollowUpCommand command);

    FollowUp getById(UUID id);

    Page<FollowUp> search(FollowUpCriteria criteria, Pageable pageable);

    FollowUp changeStatus(UUID id, FollowUpStatus status, String remarks);

    FollowUp reschedule(UUID id, RescheduleCommand command);

    FollowUp assign(UUID id, UUID assignedUserId, String remarks);

    FollowUpHistory addNote(UUID id, String note);

    List<FollowUpHistory> history(UUID id);

    FollowUpDashboardStats dashboard();
}
