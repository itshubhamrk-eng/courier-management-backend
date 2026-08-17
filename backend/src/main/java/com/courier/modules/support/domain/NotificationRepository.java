package com.courier.modules.support.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    @Query("select n from Notification n where n.companyId = :companyId and n.recipientUserId = :recipientId "
            + "order by n.createdAt desc")
    Page<Notification> findByRecipient(@Param("companyId") UUID companyId,
                                        @Param("recipientId") UUID recipientId, Pageable pageable);

    long countByCompanyIdAndRecipientUserIdAndReadFalse(UUID companyId, UUID recipientUserId);

    Optional<Notification> findByIdAndCompanyIdAndRecipientUserId(UUID id, UUID companyId, UUID recipientUserId);

    @Modifying
    @Query("update Notification n set n.read = true where n.companyId = :companyId "
            + "and n.recipientUserId = :recipientId and n.read = false")
    int markAllRead(@Param("companyId") UUID companyId, @Param("recipientId") UUID recipientId);
}
