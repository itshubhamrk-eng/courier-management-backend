package com.courier.modules.support.api;

import com.courier.modules.support.api.dto.CreateTicketCategoryRequest;
import com.courier.modules.support.api.dto.CreateTicketSubCategoryRequest;
import com.courier.modules.support.api.dto.RenameRequest;
import com.courier.modules.support.api.dto.TicketCategoryResponse;
import com.courier.modules.support.api.dto.TicketSubCategoryResponse;
import com.courier.modules.support.application.TicketCategoryService;
import com.courier.modules.support.application.command.CreateTicketCategoryCommand;
import com.courier.modules.support.application.command.CreateTicketSubCategoryCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.courier.shared.api.ApiResponse;

import java.util.List;
import java.util.UUID;

/** The global ticket category/sub-category catalogue. Reads: everyone. Writes: SUPER_ADMIN only. */
@RestController
@RequestMapping("/api/v1/support/categories")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Ticket Categories", description = "Global category/sub-category catalogue, SUPER_ADMIN-managed")
public class TicketCategoryController {

    private final TicketCategoryService service;
    private final TicketCategoryMapper mapper;

    @GetMapping
    public ApiResponse<List<TicketCategoryResponse>> list() {
        return ApiResponse.success(service.listCategories().stream().map(mapper::toResponse).toList());
    }

    @PostMapping
    @Operation(summary = "Create a category", description = "SUPER_ADMIN only.")
    public ResponseEntity<ApiResponse<TicketCategoryResponse>> create(@Valid @RequestBody CreateTicketCategoryRequest request) {
        var created = service.createCategory(new CreateTicketCategoryCommand(request.name()));
        return ResponseEntity.status(201).body(ApiResponse.success(mapper.toResponse(created), "Category created"));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Rename a category", description = "SUPER_ADMIN only.")
    public ApiResponse<TicketCategoryResponse> rename(@PathVariable UUID id, @Valid @RequestBody RenameRequest request) {
        return ApiResponse.success(mapper.toResponse(service.renameCategory(id, request.name())), "Category updated");
    }

    @PatchMapping("/{id}/activate")
    public ApiResponse<TicketCategoryResponse> activate(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.setCategoryActive(id, true)), "Category activated");
    }

    @PatchMapping("/{id}/deactivate")
    public ApiResponse<TicketCategoryResponse> deactivate(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.setCategoryActive(id, false)), "Category deactivated");
    }

    @GetMapping("/sub-categories")
    public ApiResponse<List<TicketSubCategoryResponse>> subCategories(@RequestParam UUID categoryId) {
        return ApiResponse.success(service.listSubCategories(categoryId).stream().map(mapper::toResponse).toList());
    }

    @PostMapping("/sub-categories")
    @Operation(summary = "Create a sub-category", description = "SUPER_ADMIN only.")
    public ResponseEntity<ApiResponse<TicketSubCategoryResponse>> createSubCategory(
            @Valid @RequestBody CreateTicketSubCategoryRequest request) {
        var created = service.createSubCategory(new CreateTicketSubCategoryCommand(request.categoryId(), request.name()));
        return ResponseEntity.status(201).body(ApiResponse.success(mapper.toResponse(created), "Sub-category created"));
    }

    @PatchMapping("/sub-categories/{id}")
    @Operation(summary = "Rename a sub-category", description = "SUPER_ADMIN only.")
    public ApiResponse<TicketSubCategoryResponse> renameSubCategory(@PathVariable UUID id, @Valid @RequestBody RenameRequest request) {
        return ApiResponse.success(mapper.toResponse(service.renameSubCategory(id, request.name())), "Sub-category updated");
    }

    @PatchMapping("/sub-categories/{id}/activate")
    public ApiResponse<TicketSubCategoryResponse> activateSubCategory(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.setSubCategoryActive(id, true)), "Sub-category activated");
    }

    @PatchMapping("/sub-categories/{id}/deactivate")
    public ApiResponse<TicketSubCategoryResponse> deactivateSubCategory(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.setSubCategoryActive(id, false)), "Sub-category deactivated");
    }
}
