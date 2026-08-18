package com.courier.modules.support.application;

import com.courier.modules.support.domain.Notification;
import com.courier.modules.support.domain.NotificationRepository;
import com.courier.modules.support.domain.NotificationType;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.courier.shared.company.CompanyContext;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository repository;

    @Override
    @Transactional
    public void notify(UUID recipientUserId, NotificationType type, String title, String message, UUID ticketId) {
        try {
            if (recipientUserId == null) {
                return;
            }
            repository.save(Notification.builder()
                    .recipientUserId(recipientUserId)
                    .type(type)
                    .title(title)
                    .message(message)
                    .ticketId(ticketId)
                    .read(false)
                    .build());
        } catch (Exception e) {
            // A notification failure must never break the ticket action that triggered
            // it — same posture as AuditLogWriter.
            log.error("Failed to write notification (type={}, recipient={}, ticket={})",
                    type, recipientUserId, ticketId, e);
        }
    }

    @Override
    @Transactional
    public void notifyFollowUp(UUID recipientUserId, NotificationType type, String title, String message,
                                UUID followUpId) {
        try {
            if (recipientUserId == null) {
                return;
            }
            repository.save(Notification.builder()
                    .recipientUserId(recipientUserId)
                    .type(type)
                    .title(title)
                    .message(message)
                    .followUpId(followUpId)
                    .read(false)
                    .build());
        } catch (Exception e) {
            log.error("Failed to write notification (type={}, recipient={}, followUp={})",
                    type, recipientUserId, followUpId, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Notification> list(Pageable pageable) {
        return repository.findByRecipient(requireCompany(), SecurityUtils.requireCurrentUser().userId(), pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public long unreadCount() {
        return repository.countByCompanyIdAndRecipientUserIdAndReadFalse(
                requireCompany(), SecurityUtils.requireCurrentUser().userId());
    }

    @Override
    @Transactional
    public void markRead(UUID id) {
        UUID userId = SecurityUtils.requireCurrentUser().userId();
        Notification notification = repository.findByIdAndCompanyIdAndRecipientUserId(id, requireCompany(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", id));
        notification.setRead(true);
        repository.save(notification);
    }

    @Override
    @Transactional
    public void markAllRead() {
        repository.markAllRead(requireCompany(), SecurityUtils.requireCurrentUser().userId());
    }

    private UUID requireCompany() {
        return CompanyContext.getCompanyId().orElseThrow(() -> new BusinessRuleException(
                "No company is bound to this request."));
    }
}
