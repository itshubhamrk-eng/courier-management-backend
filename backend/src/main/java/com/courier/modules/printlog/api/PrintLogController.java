package com.courier.modules.printlog.api;

import com.courier.modules.printlog.domain.PrintDocType;
import com.courier.modules.printlog.domain.PrintLog;
import com.courier.modules.printlog.domain.PrintLogRepository;
import com.courier.modules.printlog.domain.PrintLogUserPort;
import com.courier.shared.api.ApiResponse;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.security.AuthenticatedUser;
import com.courier.shared.security.SecurityUtils;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Print tracking for DRS, Loading Sheet and THC: every print/reprint is a row (who, when);
 *  the print count is the row count. */
@RestController
@RequestMapping("/api/v1/print-logs")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Print Log", description = "Who printed a DRS / Loading Sheet / THC, and how many times")
public class PrintLogController {

    private final PrintLogRepository repository;
    private final PrintLogUserPort userPort;

    public record RecordRequest(@NotNull PrintDocType docType,
                                @NotBlank @Size(max = 64) String docRef,
                                @Size(max = 50) String docNumber) {
    }

    public record PrintEntry(String printedByName, UUID printedBy, Instant printedAt) {
    }

    public record PrintHistory(PrintDocType docType, String docRef, long count, List<PrintEntry> history) {
    }

    @PostMapping
    @Transactional
    public ApiResponse<PrintHistory> record(@Valid @RequestBody RecordRequest req) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        UUID companyId = companyId();
        String name = userPort.fullName(caller.userId(), companyId).orElse(caller.email());
        repository.save(PrintLog.builder().docType(req.docType()).docRef(req.docRef())
                .docNumber(req.docNumber()).printedBy(caller.userId()).printedByName(name)
                .printedAt(Instant.now()).build());
        return ApiResponse.success(load(companyId, req.docType(), req.docRef()), "Print recorded");
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ApiResponse<PrintHistory> history(@RequestParam PrintDocType docType, @RequestParam String docRef) {
        return ApiResponse.success(load(companyId(), docType, docRef));
    }

    private PrintHistory load(UUID companyId, PrintDocType docType, String docRef) {
        List<PrintEntry> rows = repository.history(companyId, docType, docRef).stream()
                .map(p -> new PrintEntry(p.getPrintedByName(), p.getPrintedBy(), p.getPrintedAt())).toList();
        return new PrintHistory(docType, docRef, rows.size(), rows);
    }

    @GetMapping("/counts")
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Long>> counts(@RequestParam PrintDocType docType,
                                                 @RequestParam List<String> docRefs) {
        Map<String, Long> out = new HashMap<>();
        if (!docRefs.isEmpty()) {
            for (Object[] r : repository.counts(companyId(), docType, docRefs)) {
                out.put((String) r[0], (Long) r[1]);
            }
        }
        return ApiResponse.success(out);
    }

    private UUID companyId() {
        return CompanyContext.getCompanyId()
                .orElseThrow(() -> new BusinessRuleException("No company is bound to this request."));
    }
}
