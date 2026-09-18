package com.courier.modules.company.api;

import com.courier.modules.company.api.dto.MenuItemRequest;
import com.courier.modules.company.api.dto.MenuItemResponse;
import com.courier.modules.company.application.MenuService;
import com.courier.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The application's menu/submenu hierarchy. Platform-level, same posture as the
 * permission catalogue: readable by any authenticated user (it names screens, not
 * company data), writable only by {@code SUPER_ADMIN}.
 */
@RestController
@RequestMapping("/api/v1/menu-items")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Menu Items", description = "The menu/submenu hierarchy")
public class MenuController {

    private final MenuService service;

    @GetMapping("/tree")
    @Operation(summary = "The menu hierarchy, nested",
            description = "Every active node, unlimited depth, order-sorted at every level. "
                    + "Consumed by the User Permissions screen's tree UI.")
    public ApiResponse<List<MenuItemResponse>> tree() {
        return ApiResponse.success(service.tree().stream().map(MenuItemResponse::from).toList());
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Add a menu item", description = "SUPER_ADMIN only — the catalogue is platform-wide.")
    public ResponseEntity<ApiResponse<MenuItemResponse>> create(@Valid @RequestBody MenuItemRequest request) {
        var saved = service.create(request.toEntity());
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(MenuItemResponse.from(new MenuService.MenuNode(saved, List.of())), "Menu item created"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Update a menu item")
    public ApiResponse<MenuItemResponse> update(@PathVariable UUID id, @Valid @RequestBody MenuItemRequest request) {
        var saved = service.update(id, request.toEntity());
        return ApiResponse.success(
                MenuItemResponse.from(new MenuService.MenuNode(saved, List.of())), "Menu item updated");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Delete a menu item",
            description = "Refused for a system-seeded item, or one that still has children.")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success("Menu item deleted"));
    }
}
