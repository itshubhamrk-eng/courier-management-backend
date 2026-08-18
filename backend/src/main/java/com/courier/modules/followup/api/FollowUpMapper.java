package com.courier.modules.followup.api;

import com.courier.modules.followup.api.dto.CreateFollowUpRequest;
import com.courier.modules.followup.api.dto.FollowUpHistoryResponse;
import com.courier.modules.followup.api.dto.FollowUpResponse;
import com.courier.modules.followup.api.dto.FollowUpSearchRequest;
import com.courier.modules.followup.api.dto.UpdateFollowUpRequest;
import com.courier.modules.followup.application.command.CreateFollowUpCommand;
import com.courier.modules.followup.application.command.UpdateFollowUpCommand;
import com.courier.modules.followup.domain.FollowUp;
import com.courier.modules.followup.domain.FollowUpCriteria;
import com.courier.modules.followup.domain.FollowUpHistory;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class FollowUpMapper {

    public CreateFollowUpCommand toCommand(CreateFollowUpRequest r) {
        return new CreateFollowUpCommand(r.branchId(), r.referenceType(), r.referenceId(), r.customerId(),
                r.shipmentId(), r.assignedUserId(), r.title(), r.description(), r.followUpType(), r.priority(),
                r.dueDate());
    }

    public UpdateFollowUpCommand toCommand(UpdateFollowUpRequest r) {
        return new UpdateFollowUpCommand(r.branchId(), r.referenceType(), r.referenceId(), r.customerId(),
                r.shipmentId(), r.title(), r.description(), r.followUpType(), r.priority(), r.dueDate(), r.version());
    }

    public FollowUpCriteria toCriteria(FollowUpSearchRequest s) {
        if (s == null) {
            return FollowUpCriteria.none();
        }
        return new FollowUpCriteria(null, s.status(), s.priority(), s.type(), s.assignedUser(), s.dueDate(),
                s.overdue(), s.customer(), s.shipment(), s.branch(), s.search(), null, null);
    }

    public FollowUpResponse toResponse(FollowUp f) {
        boolean overdue = !f.getStatus().isTerminal() && f.getDueDate() != null && f.getDueDate().isBefore(Instant.now());
        return new FollowUpResponse(
                f.getId(), f.getCompanyId(), f.getBranchId(), f.getReferenceType(), f.getReferenceId(),
                f.getCustomerId(), f.getShipmentId(), f.getAssignedUserId(), f.getTitle(), f.getDescription(),
                f.getFollowUpType(), f.getPriority(), f.getStatus(), f.getDueDate(), f.getNextFollowUpDate(),
                overdue, f.getCompletedAt(), f.getCompletedBy(), f.getCreatedBy(),
                f.getCreatedAt(), f.getUpdatedAt(), f.getVersion());
    }

    public FollowUpHistoryResponse toResponse(FollowUpHistory h) {
        return new FollowUpHistoryResponse(h.getId(), h.getAction(), h.getFromStatus(), h.getToStatus(),
                h.getPreviousDueDate(), h.getNewDueDate(), h.getAssignedToUserId(), h.getNote(),
                h.getChangedByUserId(), h.getCreatedAt());
    }
}
