package com.courier.modules.legacyimport.api;

import com.courier.modules.legacyimport.application.CommitResult;
import com.courier.modules.legacyimport.application.LegacyFileParser;
import com.courier.modules.legacyimport.application.LegacyImportService;
import com.courier.modules.legacyimport.application.PreviewResult;
import com.courier.modules.legacyimport.domain.LegacyImportBatch;
import com.courier.shared.api.ApiResponse;
import com.courier.shared.exception.BusinessRuleException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Legacy consignment import: upload the old software's export, preview it, map its origins to
 * branches, then commit it as history-only DELIVERED shipments. COMPANY_ADMIN only, and the
 * caller must also hold the module's own permission code.
 */
@RestController
@RequestMapping("/api/v1/legacy-import")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Legacy Import", description = "Bring the old software's consignment export in as history-only shipments")
public class LegacyImportController {

    private static final String READ = "hasRole('COMPANY_ADMIN') and hasAuthority('LEGACY_IMPORT_READ')";
    private static final String WRITE = "hasRole('COMPANY_ADMIN') and hasAuthority('LEGACY_IMPORT_CREATE')";

    private final LegacyImportService service;

    public record OriginMapRequest(@NotBlank String origin, @NotNull UUID branchId) {
    }

    public record BatchResponse(UUID id, String fileName, int newCount, int existingCount, int invalidCount,
                                String status, Instant createdAt) {
        static BatchResponse of(LegacyImportBatch b) {
            return new BatchResponse(b.getId(), b.getFileName(), b.getNewCount(), b.getExistingCount(),
                    b.getInvalidCount(), b.getStatus(), b.getCreatedAt());
        }
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(WRITE)
    @Operation(summary = "Preview a legacy consignment export",
            description = "Classifies every row NEW / EXISTING / INVALID and lists the origins that need a branch. Writes nothing.")
    public ApiResponse<PreviewResult> preview(@RequestParam("file") MultipartFile file) {
        return ApiResponse.success(service.preview(file.getOriginalFilename(), bytes(file)), "Preview ready");
    }

    @PutMapping("/origin-map")
    @PreAuthorize(WRITE)
    @Operation(summary = "Map a legacy origin to one of our branches",
            description = "Remembered for the company, so the next upload needs no re-mapping.")
    public ApiResponse<Void> mapOrigin(@Valid @RequestBody OriginMapRequest request) {
        service.saveOriginMapping(request.origin(), request.branchId());
        return ApiResponse.success("Origin mapped");
    }

    @PostMapping(value = "/commit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(WRITE)
    @Operation(summary = "Import the file as history-only shipments",
            description = "NEW rows become DELIVERED shipments with no pricing, wallet, commission or notification. "
                    + "Existing consignments are never modified. Refused if any origin is unmapped.")
    public ApiResponse<CommitResult> commit(@RequestParam("file") MultipartFile file) {
        CommitResult result = service.commit(file.getOriginalFilename(), bytes(file));
        return ApiResponse.success(result, "Import complete: %d created, %d already present, %d invalid"
                .formatted(result.created(), result.skippedExisting(), result.invalid()));
    }

    @GetMapping("/batches")
    @PreAuthorize(READ)
    @Operation(summary = "Past imports, newest first")
    public ApiResponse<List<BatchResponse>> batches() {
        return ApiResponse.success(service.listBatches().stream().map(BatchResponse::of).toList());
    }

    private static byte[] bytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("Choose a file to upload.");
        }
        if (file.getSize() > LegacyFileParser.MAX_BYTES) {
            throw new BusinessRuleException("The uploaded file is larger than 10 MB.");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!(name.endsWith(".xls") || name.endsWith(".xlsx"))) {
            throw new BusinessRuleException("Upload the old software's .xls or .xlsx export.");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BusinessRuleException("The uploaded file could not be read.");
        }
    }
}
