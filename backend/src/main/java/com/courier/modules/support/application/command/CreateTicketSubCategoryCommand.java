package com.courier.modules.support.application.command;

import java.util.UUID;

public record CreateTicketSubCategoryCommand(UUID categoryId, String name) {
}
