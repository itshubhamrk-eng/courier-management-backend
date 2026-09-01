package com.courier.modules.communication.application;

import com.courier.modules.communication.domain.CommunicationLog;
import com.courier.modules.communication.domain.CommunicationLogCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface CommunicationLogService {

    Page<CommunicationLog> search(CommunicationLogCriteria criteria, Pageable pageable);

    CommunicationLog getById(UUID id);

    /** For the Shipment Details "Communication" tab — every attempt for one shipment,
     *  across all events and channels. */
    List<CommunicationLog> forShipment(UUID shipmentId);

    /** Requeues a {@code FAILED} row immediately, bypassing its own backoff — the manual
     *  override "Retry Failed" gives an operator. Refused on any other status: {@code
     *  CANCELLED} means nothing was ever attempted (a config problem, not a transient one),
     *  {@code SENT}/{@code DELIVERED} already succeeded, and retrying a {@code PENDING} row
     *  would just race the dispatch job that is already about to pick it up. */
    CommunicationLog retry(UUID id);
}
