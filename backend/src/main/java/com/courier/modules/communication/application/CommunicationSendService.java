package com.courier.modules.communication.application;

import java.util.UUID;

/** One (shipment, event, channel) attempt: render -&gt; send -&gt; store result. Used by
 *  {@code CommunicationDispatchJob}; not exposed through any controller. */
public interface CommunicationSendService {

    void processOne(UUID logId, UUID companyId);
}
