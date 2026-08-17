package com.courier.modules.support.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TicketSubCategoryRepository extends JpaRepository<TicketSubCategory, UUID> {

    List<TicketSubCategory> findAllByCategoryIdOrderByNameAsc(UUID categoryId);

    boolean existsByCategoryIdAndNameIgnoreCase(UUID categoryId, String name);
}
