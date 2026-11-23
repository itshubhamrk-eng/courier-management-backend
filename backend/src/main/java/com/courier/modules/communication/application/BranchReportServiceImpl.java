package com.courier.modules.communication.application;

import com.courier.modules.communication.application.provider.ProviderSendResult;
import com.courier.modules.communication.application.provider.WhatsAppProvider;
import com.courier.modules.communication.domain.BranchReportLog;
import com.courier.modules.communication.domain.BranchReportLogRepository;
import com.courier.modules.communication.domain.BranchStatementPort;
import com.courier.modules.communication.domain.BranchStatementPort.BranchTarget;
import com.courier.modules.communication.domain.BranchStatementPort.GeneratedStatement;
import com.courier.modules.communication.domain.CommunicationChannel;
import com.courier.modules.communication.domain.CommunicationEventType;
import com.courier.modules.communication.domain.CommunicationSetting;
import com.courier.modules.communication.domain.CommunicationSettingRepository;
import com.courier.modules.communication.domain.CommunicationStatus;
import com.courier.modules.communication.domain.CommunicationTemplate;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Copy of {@link DailyReportServiceImpl}'s claim/retry shape, keyed per (branch, period, recipient).
 * Not {@code @Transactional}: each log write is its own commit so one failure never rolls back a
 * send that already went out. No {@code @PreAuthorize}, see {@link BranchReportService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BranchReportServiceImpl implements BranchReportService {

    static final int MAX_ATTEMPTS = 3;

    private final CommunicationSettingRepository settingRepository;
    private final BranchReportLogRepository logRepository;
    private final BranchStatementPort statementPort;
    private final WhatsAppProvider whatsAppProvider;
    private final CommunicationTemplateService templateService;
    private final TemplateRenderer templateRenderer;
    private final ObjectMapper objectMapper;

    @Override
    public int sendReportsFor(LocalDate from, LocalDate to) {
        int attempted = 0;
        for (CommunicationSetting setting : settingRepository.findAllByChannelAndEnabledTrue(CommunicationChannel.WHATSAPP)) {
            UUID companyId = setting.getCompanyId();
            try {
                attempted += CompanyContext.runAs(companyId, () -> processCompany(companyId, setting, from, to, false, null))
                        .attempts();
            } catch (RuntimeException e) {
                log.error("Branch report: unexpected failure for company {} for {} to {}", companyId, from, to, e);
            }
        }
        return attempted;
    }

    @Override
    public SendOutcome sendForBranch(UUID branchId, LocalDate from, LocalDate to, boolean force) {
        UUID companyId = CompanyContext.requireCompanyId();
        List<SendOutcome> outcomes = processCompany(companyId, enabledSetting(companyId), from, to, force, branchId)
                .outcomes();
        if (outcomes.isEmpty()) {
            throw new ResourceNotFoundException("Branch", branchId);
        }
        return outcomes.get(0);
    }

    @Override
    public List<SendOutcome> sendForAllBranches(LocalDate from, LocalDate to, boolean force) {
        UUID companyId = CompanyContext.requireCompanyId();
        return processCompany(companyId, enabledSetting(companyId), from, to, force, null).outcomes();
    }

    @Override
    public List<BranchSendStatus> sendStatus(LocalDate from, LocalDate to) {
        UUID companyId = CompanyContext.requireCompanyId();
        Map<UUID, List<BranchReportLog>> byBranch = new HashMap<>();
        for (BranchReportLog row : logRepository.findAllByCompanyIdAndPeriodFromAndPeriodTo(companyId, from, to)) {
            byBranch.computeIfAbsent(row.getBranchId(), k -> new ArrayList<>()).add(row);
        }
        List<BranchSendStatus> out = new ArrayList<>();
        for (BranchTarget target : statementPort.targets(companyId, from, to)) {
            List<BranchReportLog> rows = byBranch.getOrDefault(target.branchId(), List.of());
            String status = rows.stream().map(BranchReportLog::getStatus).min(Comparator.comparingInt(this::progress))
                    .map(Enum::name).orElse(null);
            Instant lastSent = rows.stream().map(BranchReportLog::getSentAt).filter(java.util.Objects::nonNull)
                    .max(Comparator.naturalOrder()).orElse(null);
            out.add(new BranchSendStatus(target.branchId(), target.branchName(), target.managerPhones().size(),
                    status, lastSent));
        }
        return out;
    }

    /** Lower is worse, so the least-progressed recipient is what the admin sees. */
    private int progress(CommunicationStatus s) {
        return switch (s) {
            case FAILED -> 0;
            case PENDING -> 1;
            case SENT -> 2;
            case DELIVERED -> 3;
            default -> 1;
        };
    }

    private CommunicationSetting enabledSetting(UUID companyId) {
        return settingRepository.findByCompanyIdAndChannel(companyId, CommunicationChannel.WHATSAPP)
                .filter(CommunicationSetting::isEnabled)
                .orElseThrow(() -> new BusinessRuleException("WhatsApp is not enabled for this company."));
    }

    private record CompanyResult(int attempts, List<SendOutcome> outcomes) {
    }

    private CompanyResult processCompany(UUID companyId, CommunicationSetting setting, LocalDate from, LocalDate to,
                                         boolean force, UUID onlyBranch) {
        List<BranchTarget> targets = statementPort.targets(companyId, from, to).stream()
                .filter(t -> onlyBranch == null || t.branchId().equals(onlyBranch)).toList();
        if (targets.isEmpty()) {
            return new CompanyResult(0, List.of());
        }
        CommunicationTemplate template = templateService
                .findActive(companyId, CommunicationEventType.BRANCH_MONTHLY_REPORT, CommunicationChannel.WHATSAPP)
                .orElse(null);
        if (template == null || !template.isApprovedOrUnknownAtMeta()) {
            // Not an attempt: nothing to retry until the template is switched on / Meta approves it.
            log.debug("Branch report: template missing or not approved at Meta — skipping company {}", companyId);
            return new CompanyResult(0, targets.stream()
                    .map(t -> new SendOutcome(t.branchId(), t.branchName(), TEMPLATE_NOT_APPROVED)).toList());
        }
        Map<String, String> config = CommunicationConfigJson.read(objectMapper, setting.getConfigJson());
        WhatsAppProvider.WhatsAppCredentials credentials = new WhatsAppProvider.WhatsAppCredentials(
                config.get("phoneNumberId"), config.get("businessAccountId"), setting.getSecret());

        int attempts = 0;
        List<SendOutcome> outcomes = new ArrayList<>();
        for (BranchTarget target : targets) {
            try {
                BranchRun run = new BranchRun();
                String result = processBranch(companyId, target, from, to, force, template, credentials, run);
                attempts += run.attempts;
                outcomes.add(new SendOutcome(target.branchId(), target.branchName(), result));
            } catch (RuntimeException e) { // never let one branch stop the rest
                log.error("Branch report: unexpected failure for branch {} in company {}", target.branchId(),
                        companyId, e);
                outcomes.add(new SendOutcome(target.branchId(), target.branchName(), FAILED));
            }
        }
        return new CompanyResult(attempts, outcomes);
    }

    /** Per-branch, per-run state: the PDF and its Meta media id are made once, then reused for every manager. */
    private static final class BranchRun {
        GeneratedStatement statement;
        String mediaId;
        int attempts;
    }

    private String processBranch(UUID companyId, BranchTarget target, LocalDate from, LocalDate to, boolean force,
                                 CommunicationTemplate template, WhatsAppProvider.WhatsAppCredentials credentials,
                                 BranchRun run) {
        if (target.managerPhones().isEmpty()) {
            return NO_PHONE;
        }
        int sent = 0;
        int failed = 0;
        boolean noStatement = false;
        for (String phone : target.managerPhones()) {
            Claim claim = claim(companyId, target.branchId(), from, to, phone, force);
            if (claim.skip == Skip.FAILED_AT_CAP) {
                failed++;
                continue;
            }
            if (claim.row == null) {
                continue; // already sent, or another node has it
            }
            BranchReportLog row = claim.row;
            try {
                if (run.statement == null) {
                    run.statement = statementPort.generate(companyId, target.branchId(), from, to).orElse(null);
                    if (run.statement == null) {
                        log.warn("Branch report: no statement for branch {} in company {} — skipped", target.branchId(),
                                companyId);
                        noStatement = true;
                        break; // row stays PENDING with no attempt, picked up on a later tick
                    }
                }
                if (run.mediaId == null) {
                    run.mediaId = whatsAppProvider.uploadDocument(run.statement.pdf(), run.statement.fileName(),
                            credentials);
                }
                WhatsAppProvider.WhatsAppMessage message = WhatsAppPayloadBuilder.build(phone, template,
                        templateRenderer, Map.of("branchName", run.statement.branchName(),
                                "month", run.statement.monthLabel()), objectMapper)
                        .withDocument(run.mediaId, run.statement.fileName());
                ProviderSendResult result = whatsAppProvider.send(message, credentials);
                row.setStatus(CommunicationStatus.SENT);
                row.setProviderMessageId(result.providerMessageId());
                row.setErrorMessage(null);
                row.setSentAt(Instant.now());
                sent++;
            } catch (RuntimeException e) { // includes ProviderSendException
                row.setStatus(CommunicationStatus.FAILED);
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                row.setErrorMessage(message.length() > 1000 ? message.substring(0, 1000) : message);
                log.warn("Branch report send failed: company={} branch={} to=***{} attempt={} — {}", companyId,
                        target.branchId(), last4(phone), row.getAttemptCount() + 1, message);
                failed++;
            }
            row.setAttemptCount(row.getAttemptCount() + 1);
            row.setLastAttemptAt(Instant.now());
            logRepository.save(row);
            run.attempts++;
        }
        if (sent > 0) {
            return SENT;
        }
        if (failed > 0 || noStatement) {
            return FAILED;
        }
        return ALREADY_SENT;
    }

    private enum Skip { NONE, DONE, FAILED_AT_CAP }

    private record Claim(BranchReportLog row, Skip skip) {
    }

    /** The row to send on (existing, or a fresh one inserted now so a second node/tick sees it).
     *  {@code force} ignores both the already-sent and the attempt cap. */
    private Claim claim(UUID companyId, UUID branchId, LocalDate from, LocalDate to, String phone, boolean force) {
        BranchReportLog existing = logRepository
                .findByCompanyIdAndBranchIdAndPeriodFromAndPeriodToAndRecipient(companyId, branchId, from, to, phone)
                .orElse(null);
        if (existing != null) {
            if (force) {
                return new Claim(existing, Skip.NONE);
            }
            if (existing.isSent()) {
                return new Claim(null, Skip.DONE);
            }
            return existing.getAttemptCount() >= MAX_ATTEMPTS
                    ? new Claim(null, Skip.FAILED_AT_CAP) : new Claim(existing, Skip.NONE);
        }
        BranchReportLog fresh = BranchReportLog.builder().branchId(branchId).periodFrom(from).periodTo(to)
                .recipient(phone).build();
        fresh.setCompanyId(companyId);
        try {
            return new Claim(logRepository.saveAndFlush(fresh), Skip.NONE);
        } catch (DataIntegrityViolationException e) {
            return new Claim(null, Skip.DONE); // another node inserted it between the lookup and here
        }
    }

    private static String last4(String phone) {
        return phone.length() <= 4 ? phone : phone.substring(phone.length() - 4);
    }
}
