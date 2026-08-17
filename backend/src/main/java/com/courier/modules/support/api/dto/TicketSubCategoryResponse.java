package com.courier.modules.support.api.dto;

import java.util.UUID;

public record TicketSubCategoryResponse(UUID id, UUID categoryId, String name, boolean active, Long version) {
}
