package com.courier.modules.company.application;

import com.courier.modules.company.domain.MenuItem;

import java.util.List;
import java.util.UUID;

/**
 * The menu catalogue — platform-level, same posture as {@link PermissionService} for the
 * permission catalogue: {@code SUPER_ADMIN} writes it, everyone authenticated may read
 * it (it names screens, not data).
 */
public interface MenuService {

    /** Every active node, flat — the caller assembles the tree (or asks for one directly). */
    List<MenuItem> listAll();

    /** The same rows, nested by {@code parentId}, order-sorted at every level. */
    List<MenuNode> tree();

    MenuItem create(MenuItem item);

    MenuItem update(UUID id, MenuItem changes);

    void delete(UUID id);

    /** One node plus its already-nested children. */
    record MenuNode(MenuItem item, List<MenuNode> children) {
    }
}
