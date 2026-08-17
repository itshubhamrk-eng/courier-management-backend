package com.courier.modules.support.api.dto;

import java.util.UUID;

public record TicketCategoryResponse(UUID id, String name, boolean active, Long version) {
}
