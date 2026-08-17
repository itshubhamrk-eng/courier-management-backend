package com.courier.modules.support.api;

import com.courier.modules.support.api.dto.TicketCategoryResponse;
import com.courier.modules.support.api.dto.TicketSubCategoryResponse;
import com.courier.modules.support.domain.TicketCategory;
import com.courier.modules.support.domain.TicketSubCategory;
import org.springframework.stereotype.Component;

@Component
public class TicketCategoryMapper {

    public TicketCategoryResponse toResponse(TicketCategory c) {
        return new TicketCategoryResponse(c.getId(), c.getName(), c.isActive(), c.getVersion());
    }

    public TicketSubCategoryResponse toResponse(TicketSubCategory c) {
        return new TicketSubCategoryResponse(c.getId(), c.getCategoryId(), c.getName(), c.isActive(), c.getVersion());
    }
}
