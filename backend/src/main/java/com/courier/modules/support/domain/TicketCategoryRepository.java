package com.courier.modules.support.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketCategoryRepository extends JpaRepository<TicketCategory, UUID> {

    List<TicketCategory> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    Optional<TicketCategory> findByNameIgnoreCase(String name);
}
