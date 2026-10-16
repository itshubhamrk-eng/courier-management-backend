package com.courier.modules.platformintegration.api;

import com.courier.modules.platformintegration.application.PlatformTenantService;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

/**
 * The OCCPL platform's provisioning contract (see its ADR-0008). Both calls are
 * idempotent: the platform repeats a call after a timeout with the job id as
 * {@code Idempotency-Key}. Authentication is the dedicated chain in
 * {@code PlatformSecurityConfig}; the plain {@code ApiResponse} envelope is not used
 * because the caller is a machine speaking the platform's contract.
 */
@RestController
@RequestMapping("/provisioning/v1/tenants/{companyId}")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "occpl.platform", name = "enabled", havingValue = "true")
public class ProvisioningController {

    private final PlatformTenantService service;

    /** Fields beyond these (jobId, productCode, configVersion, async...) are accepted and ignored. */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record ProvisionRequest(String companyId,
                                   @Size(max = 50) String companyCode,
                                   @Size(max = 150) String companyName,
                                   @Size(max = 255) String adminEmail,
                                   @Size(max = 100) String adminFirstName,
                                   @Size(max = 100) String adminLastName,
                                   @Size(max = 20) String mobile) {
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> provision(@PathVariable UUID companyId,
                                                         @RequestBody ProvisionRequest request) {
        if (request.companyId() != null && !request.companyId().equals(companyId.toString())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "companyId does not match the path.");
        }
        PlatformTenantService.Result result = service.provision(companyId,
                new PlatformTenantService.ProvisionCommand(request.companyCode(), request.companyName(),
                        request.adminEmail(), request.adminFirstName(), request.adminLastName(),
                        request.mobile()));
        return ResponseEntity.ok(Map.of(
                "companyId", companyId.toString(),
                "status", "PROVISIONED",
                "created", result.created()));
    }

    /** Deactivates only; the company's data is kept. 404 when never provisioned. */
    @DeleteMapping
    public ResponseEntity<Void> deprovision(@PathVariable UUID companyId) {
        return service.deprovision(companyId)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
