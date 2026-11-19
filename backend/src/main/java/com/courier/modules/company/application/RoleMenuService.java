package com.courier.modules.company.application;

import com.courier.modules.company.application.command.RoleMenuItem;

import java.util.List;
import java.util.UUID;

/** The menus a company role holds. A user's sidebar follows these live. */
public interface RoleMenuService {

    List<RoleMenuItem> get(UUID roleId);

    /**
     * Replaces the role's menus with exactly {@code items} (an all-false item counts as
     * unticked; group menus are ignored) and keeps the role's module permissions in step so
     * the APIs behind a ticked menu keep working.
     */
    List<RoleMenuItem> replace(UUID roleId, List<RoleMenuItem> items);
}
