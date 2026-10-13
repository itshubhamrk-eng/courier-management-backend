package com.courier.modules.customerbilling.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CustomerInvoiceLineRepository extends JpaRepository<CustomerInvoiceLine, UUID> {

    @Query("select l from CustomerInvoiceLine l where l.companyId = :companyId and l.invoiceId = :invoiceId"
            + " order by l.bookingDate, l.shipmentNumber")
    List<CustomerInvoiceLine> findByInvoice(@Param("companyId") UUID companyId, @Param("invoiceId") UUID invoiceId);
}
