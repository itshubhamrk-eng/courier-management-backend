package com.courier.modules.company.application.command;

import java.util.UUID;

/** One menu a role holds, with the actions ticked for it. */
public record RoleMenuItem(UUID menuItemId, boolean canCreate, boolean canRead,
                           boolean canUpdate, boolean canDelete) {

    public boolean anyTicked() {
        return canCreate || canRead || canUpdate || canDelete;
    }
}
