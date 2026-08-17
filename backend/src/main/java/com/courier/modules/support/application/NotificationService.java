package com.courier.modules.support.application;

import com.courier.modules.support.domain.Notification;
import com.courier.modules.support.domain.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/** The in-app notification feed. {@link #notify} is called internally by {@code
 *  TicketServiceImpl}/{@code TicketSlaSweepJob} — there is no public "create a
 *  notification" endpoint, notifications are always a side effect of a ticket event. */
public interface NotificationService {

    /** Fire-and-forget: never throws, a notification failure must not break the
     *  ticket action that triggered it. */
    void notify(UUID recipientUserId, NotificationType type, String title, String message, UUID ticketId);

    /** The current user's own feed, newest first. */
    Page<Notification> list(Pageable pageable);

    long unreadCount();

    void markRead(UUID id);

    void markAllRead();
}
