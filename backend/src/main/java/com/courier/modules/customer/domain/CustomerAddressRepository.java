package com.courier.modules.customer.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Addresses, within a company and scoped to one customer of it. Not a
 * {@code JpaSpecificationExecutor} — a customer's own address book is small and always
 * read whole; there is no independent address search endpoint in this module.
 */
public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, UUID> {

    @Query("select a from CustomerAddress a where a.id = :id and a.companyId = :companyId")
    Optional<CustomerAddress> findByIdWithinCompany(@Param("id") UUID id, @Param("companyId") UUID companyId);

    @Query("select a from CustomerAddress a where a.customerId = :customerId and a.companyId = :companyId "
            + "order by a.createdAt asc")
    List<CustomerAddress> findAllByCustomerIdWithinCompany(@Param("customerId") UUID customerId,
                                                           @Param("companyId") UUID companyId);

    long countByCompanyIdAndCustomerId(UUID companyId, UUID customerId);
}
