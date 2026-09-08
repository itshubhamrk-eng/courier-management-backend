package com.courier.modules.master.domain;

import java.util.List;
import java.util.UUID;

/** Payment modes. */
public interface PaymentModeRepository extends MasterDataRepository<PaymentMode> {

    /** TO_PAY-shaped modes for a company — collects at delivery, but is not cash-on-delivery
     *  (the consignee's amount). Used by the dashboard's "TO_PAY awaiting delivery" count,
     *  which needs the actual ids to filter shipments on rather than a fixed code. */
    List<PaymentMode> findByCompanyIdAndCollectAtDeliveryTrueAndCashOnDeliveryFalse(UUID companyId);
}
