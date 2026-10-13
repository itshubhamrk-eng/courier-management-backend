package com.courier.modules.customerbilling.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CustomerInvoicePaymentRepository extends JpaRepository<CustomerInvoicePayment, UUID> {

    @Query("select p from CustomerInvoicePayment p where p.id = :id and p.invoiceId = :invoiceId and p.companyId = :companyId")
    java.util.Optional<CustomerInvoicePayment> findOne(@Param("id") UUID id, @Param("invoiceId") UUID invoiceId,
                                                       @Param("companyId") UUID companyId);

    @Query("select p from CustomerInvoicePayment p where p.companyId = :companyId and p.invoiceId = :invoiceId"
            + " order by p.paidOn, p.createdAt")
    List<CustomerInvoicePayment> findByInvoice(@Param("companyId") UUID companyId, @Param("invoiceId") UUID invoiceId);
}
