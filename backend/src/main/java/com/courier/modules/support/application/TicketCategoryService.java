package com.courier.modules.support.application;

import com.courier.modules.support.application.command.CreateTicketCategoryCommand;
import com.courier.modules.support.application.command.CreateTicketSubCategoryCommand;
import com.courier.modules.support.domain.TicketCategory;
import com.courier.modules.support.domain.TicketSubCategory;

import java.util.List;
import java.util.UUID;

/** The global category/sub-category catalogue. Reads: everyone. Writes: SUPER_ADMIN only. */
public interface TicketCategoryService {

    List<TicketCategory> listCategories();

    List<TicketSubCategory> listSubCategories(UUID categoryId);

    TicketCategory createCategory(CreateTicketCategoryCommand command);

    TicketCategory renameCategory(UUID id, String name);

    TicketCategory setCategoryActive(UUID id, boolean active);

    TicketSubCategory createSubCategory(CreateTicketSubCategoryCommand command);

    TicketSubCategory renameSubCategory(UUID id, String name);

    TicketSubCategory setSubCategoryActive(UUID id, boolean active);
}
