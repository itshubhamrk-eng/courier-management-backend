package com.courier.modules.communication.application;

import com.courier.modules.communication.application.BranchReportService.BranchSendStatus;
import com.courier.modules.communication.application.BranchReportService.SendOutcome;
import com.courier.modules.communication.application.provider.ProviderSendException;
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
import com.courier.modules.communication.domain.DefaultCommunicationTemplates;
import com.courier.shared.company.CompanyContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BranchReportServiceImplTest {

    private static final LocalDate FROM = LocalDate.of(2026, 12, 1);
    private static final LocalDate TO = LocalDate.of(2026, 12, 31);

    @Mock CommunicationSettingRepository settingRepository;
    @Mock BranchReportLogRepository logRepository;
    @Mock BranchStatementPort statementPort;
    @Mock WhatsAppProvider whatsAppProvider;
    @Mock CommunicationTemplateService templateService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UUID companyId = UUID.randomUUID();
    private final UUID branchA = UUID.randomUUID();
    private final UUID branchB = UUID.randomUUID();
    private CommunicationTemplate template;
    private BranchReportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BranchReportServiceImpl(settingRepository, logRepository, statementPort, whatsAppProvider,
                templateService, new TemplateRenderer("http://x/{trackingNumber}"), objectMapper);
        var seed = DefaultCommunicationTemplates.BRANCH_MONTHLY_REPORT;
        template = CommunicationTemplate.builder().eventType(seed.eventType()).channel(seed.channel())
                .templateName(seed.templateName()).content(seed.content()).build();
        when(templateService.findActive(eq(companyId), eq(CommunicationEventType.BRANCH_MONTHLY_REPORT),
                eq(CommunicationChannel.WHATSAPP))).thenReturn(Optional.of(template));
        CommunicationSetting setting = setting();
        when(settingRepository.findAllByChannelAndEnabledTrue(CommunicationChannel.WHATSAPP))
                .thenReturn(List.of(setting));
        when(settingRepository.findByCompanyIdAndChannel(companyId, CommunicationChannel.WHATSAPP))
                .thenReturn(Optional.of(setting));
        when(logRepository.findByCompanyIdAndBranchIdAndPeriodFromAndPeriodToAndRecipient(any(), any(), any(), any(),
                any())).thenReturn(Optional.empty());
        when(logRepository.saveAndFlush(any(BranchReportLog.class))).thenAnswer(i -> i.getArgument(0));
        when(logRepository.save(any(BranchReportLog.class))).thenAnswer(i -> i.getArgument(0));
        when(statementPort.generate(eq(companyId), any(), any(), any())).thenAnswer(i -> Optional.of(
                new GeneratedStatement(new byte[]{1, 2, 3}, "branch-2026-12.pdf", "Branch", "December 2026")));
        when(whatsAppProvider.uploadDocument(any(), any(), any())).thenReturn("media-1");
        when(whatsAppProvider.send(any(), any())).thenReturn(new ProviderSendResult("wamid.X"));
        CompanyContext.setCompanyId(companyId);
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    private CommunicationSetting setting() {
        CommunicationSetting s = CommunicationSetting.builder().channel(CommunicationChannel.WHATSAPP)
                .enabled(true).configJson("{\"phoneNumberId\":\"111\",\"businessAccountId\":\"222\"}").build();
        s.setCompanyId(companyId);
        s.setSecret("token");
        return s;
    }

    private void targets(BranchTarget... t) {
        when(statementPort.targets(companyId, FROM, TO)).thenReturn(List.of(t));
    }

    private BranchReportLog row(UUID branchId, String phone, CommunicationStatus status, int attempts) {
        BranchReportLog r = BranchReportLog.builder().branchId(branchId).periodFrom(FROM).periodTo(TO)
                .recipient(phone).status(status).attemptCount(attempts).build();
        r.setCompanyId(companyId);
        when(logRepository.findByCompanyIdAndBranchIdAndPeriodFromAndPeriodToAndRecipient(companyId, branchId, FROM,
                TO, phone)).thenReturn(Optional.of(r));
        return r;
    }

    @Test
    void twoManagersGetTwoSendsAndTwoLogRowsWithOnePdf() {
        targets(new BranchTarget(branchA, "Pune Branch", List.of("919876543210", "919123456780")));

        int attempted = service.sendReportsFor(FROM, TO);

        assertThat(attempted).isEqualTo(2);
        ArgumentCaptor<WhatsAppProvider.WhatsAppMessage> sent = ArgumentCaptor.forClass(WhatsAppProvider.WhatsAppMessage.class);
        verify(whatsAppProvider, times(2)).send(sent.capture(), any());
        assertThat(sent.getAllValues()).extracting(WhatsAppProvider.WhatsAppMessage::toE164)
                .containsExactly("919876543210", "919123456780");
        WhatsAppProvider.WhatsAppMessage m = sent.getValue();
        assertThat(m.approvedTemplateName()).isEqualTo("branch_monthly_report");
        assertThat(m.templateParameters()).containsExactly("Branch", "December 2026");
        assertThat(m.documentMediaId()).isEqualTo("media-1");
        assertThat(m.documentFileName()).isEqualTo("branch-2026-12.pdf");
        verify(logRepository, times(2)).saveAndFlush(any(BranchReportLog.class));
        verify(statementPort, times(1)).generate(companyId, branchA, FROM, TO);
        verify(whatsAppProvider, times(1)).uploadDocument(any(), any(), any());
    }

    @Test
    void successMarksTheRowSent() {
        targets(new BranchTarget(branchA, "Pune Branch", List.of("919876543210")));
        service.sendReportsFor(FROM, TO);
        ArgumentCaptor<BranchReportLog> saved = ArgumentCaptor.forClass(BranchReportLog.class);
        verify(logRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(CommunicationStatus.SENT);
        assertThat(saved.getValue().getProviderMessageId()).isEqualTo("wamid.X");
        assertThat(saved.getValue().getAttemptCount()).isEqualTo(1);
        assertThat(saved.getValue().getSentAt()).isNotNull();
    }

    @Test
    void noManagerPhoneIsNoPhoneAndNoProviderCall() {
        targets(new BranchTarget(branchA, "Pune Branch", List.of()));

        List<SendOutcome> out = service.sendForAllBranches(FROM, TO, false);

        assertThat(out).containsExactly(new SendOutcome(branchA, "Pune Branch", "NO_PHONE"));
        verify(whatsAppProvider, never()).send(any(), any());
        verify(whatsAppProvider, never()).uploadDocument(any(), any(), any());
        verify(statementPort, never()).generate(any(), any(), any(), any());
    }

    @Test
    void alreadySentRowIsSkippedUnlessForced() {
        targets(new BranchTarget(branchA, "Pune Branch", List.of("919876543210")));
        row(branchA, "919876543210", CommunicationStatus.SENT, 1);

        assertThat(service.sendForBranch(branchA, FROM, TO, false).result()).isEqualTo("ALREADY_SENT");
        verify(whatsAppProvider, never()).send(any(), any());
    }

    @Test
    void deliveredRowCountsAsAlreadySent() {
        targets(new BranchTarget(branchA, "Pune Branch", List.of("919876543210")));
        row(branchA, "919876543210", CommunicationStatus.DELIVERED, 1);

        assertThat(service.sendForBranch(branchA, FROM, TO, false).result()).isEqualTo("ALREADY_SENT");
    }

    @Test
    void forceResendReusesTheRowAndIncrementsAttempts() {
        targets(new BranchTarget(branchA, "Pune Branch", List.of("919876543210")));
        BranchReportLog existing = row(branchA, "919876543210", CommunicationStatus.SENT, 1);

        SendOutcome out = service.sendForBranch(branchA, FROM, TO, true);

        assertThat(out.result()).isEqualTo("SENT");
        verify(whatsAppProvider, times(1)).send(any(), any());
        verify(logRepository, never()).saveAndFlush(any());
        verify(logRepository).save(existing);
        assertThat(existing.getAttemptCount()).isEqualTo(2);
    }

    @Test
    void failedForcedResendKeepsSentAndDeliveredRowsAndSchedulerSendsNothing() {
        targets(new BranchTarget(branchA, "A", List.of("919876543210", "919123456780")));
        Instant sentAt = Instant.parse("2027-01-01T04:00:00Z");
        BranchReportLog sent = row(branchA, "919876543210", CommunicationStatus.SENT, 1);
        sent.setSentAt(sentAt);
        sent.setProviderMessageId("wamid.old1");
        BranchReportLog delivered = row(branchA, "919123456780", CommunicationStatus.DELIVERED, 1);
        delivered.setSentAt(sentAt);
        delivered.setProviderMessageId("wamid.old2");
        when(whatsAppProvider.send(any(), any())).thenThrow(new ProviderSendException("Meta said no"));

        assertThat(service.sendForBranch(branchA, FROM, TO, true).result()).isEqualTo("FAILED");

        assertThat(sent.getStatus()).isEqualTo(CommunicationStatus.SENT);
        assertThat(delivered.getStatus()).isEqualTo(CommunicationStatus.DELIVERED);
        for (BranchReportLog r : List.of(sent, delivered)) {
            assertThat(r.getErrorMessage()).contains("Meta said no");
            assertThat(r.getAttemptCount()).isEqualTo(2);
            assertThat(r.getLastAttemptAt()).isNotNull();
        }
        org.mockito.Mockito.clearInvocations(whatsAppProvider);

        assertThat(service.sendReportsFor(FROM, TO)).isZero();
        verify(whatsAppProvider, never()).send(any(), any());
    }

    @Test
    void forceIgnoresTheAttemptCap() {
        targets(new BranchTarget(branchA, "Pune Branch", List.of("919876543210")));
        BranchReportLog existing = row(branchA, "919876543210", CommunicationStatus.FAILED, 3);

        assertThat(service.sendForBranch(branchA, FROM, TO, true).result()).isEqualTo("SENT");
        assertThat(existing.getAttemptCount()).isEqualTo(4);
    }

    @Test
    void schedulerSkipsAFailedRowThatUsedAllAttempts() {
        targets(new BranchTarget(branchA, "Pune Branch", List.of("919876543210")));
        row(branchA, "919876543210", CommunicationStatus.FAILED, 3);

        assertThat(service.sendReportsFor(FROM, TO)).isZero();
        verify(whatsAppProvider, never()).send(any(), any());
    }

    @Test
    void schedulerRetriesAFailedRowUnderTheCap() {
        targets(new BranchTarget(branchA, "Pune Branch", List.of("919876543210")));
        BranchReportLog existing = row(branchA, "919876543210", CommunicationStatus.FAILED, 2);

        assertThat(service.sendReportsFor(FROM, TO)).isEqualTo(1);
        assertThat(existing.getStatus()).isEqualTo(CommunicationStatus.SENT);
        assertThat(existing.getAttemptCount()).isEqualTo(3);
    }

    @Test
    void templatePendingAtMetaIsNotAnAttempt() {
        template.setMetaStatus("PENDING");
        targets(new BranchTarget(branchA, "Pune Branch", List.of("919876543210")));

        assertThat(service.sendForBranch(branchA, FROM, TO, false).result()).isEqualTo("TEMPLATE_NOT_APPROVED");
        assertThat(service.sendReportsFor(FROM, TO)).isZero();
        verify(whatsAppProvider, never()).send(any(), any());
        verify(logRepository, never()).saveAndFlush(any());
    }

    @Test
    void providerFailureMarksFailedWithMessageAndCountsAttempt() {
        targets(new BranchTarget(branchA, "Pune Branch", List.of("919876543210")));
        when(whatsAppProvider.send(any(), any())).thenThrow(new ProviderSendException("Meta said no"));

        SendOutcome out = service.sendForBranch(branchA, FROM, TO, false);

        assertThat(out.result()).isEqualTo("FAILED");
        ArgumentCaptor<BranchReportLog> saved = ArgumentCaptor.forClass(BranchReportLog.class);
        verify(logRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(CommunicationStatus.FAILED);
        assertThat(saved.getValue().getErrorMessage()).contains("Meta said no");
        assertThat(saved.getValue().getAttemptCount()).isEqualTo(1);
    }

    @Test
    void uploadFailureAlsoMarksFailed() {
        targets(new BranchTarget(branchA, "Pune Branch", List.of("919876543210")));
        when(whatsAppProvider.uploadDocument(any(), any(), any())).thenThrow(new ProviderSendException("upload bad"));

        assertThat(service.sendForBranch(branchA, FROM, TO, false).result()).isEqualTo("FAILED");
        verify(whatsAppProvider, never()).send(any(), any());
    }

    @Test
    void oneBranchFailingDoesNotStopTheOthers() {
        targets(new BranchTarget(branchA, "A", List.of("919876543210")),
                new BranchTarget(branchB, "B", List.of("919123456780")));
        when(whatsAppProvider.send(any(), any())).thenThrow(new ProviderSendException("boom"))
                .thenReturn(new ProviderSendResult("wamid.2"));

        List<SendOutcome> out = service.sendForAllBranches(FROM, TO, false);

        assertThat(out).extracting(SendOutcome::result).containsExactly("FAILED", "SENT");
    }

    @Test
    void exceptionFromOneBranchGenerationDoesNotStopTheOthers() {
        targets(new BranchTarget(branchA, "A", List.of("919876543210")),
                new BranchTarget(branchB, "B", List.of("919123456780")));
        when(statementPort.generate(companyId, branchA, FROM, TO)).thenThrow(new IllegalStateException("pdf"));

        List<SendOutcome> out = service.sendForAllBranches(FROM, TO, false);

        assertThat(out).extracting(SendOutcome::result).containsExactly("FAILED", "SENT");
    }

    @Test
    void severalManagersSentIfAtLeastOneSucceeded() {
        targets(new BranchTarget(branchA, "A", List.of("919876543210", "919123456780")));
        when(whatsAppProvider.send(any(), any())).thenThrow(new ProviderSendException("boom"))
                .thenReturn(new ProviderSendResult("wamid.2"));

        assertThat(service.sendForBranch(branchA, FROM, TO, false).result()).isEqualTo("SENT");
    }

    @Test
    void branchWithNoStatementIsSkippedWithoutAnAttempt() {
        targets(new BranchTarget(branchA, "A", List.of("919876543210")));
        when(statementPort.generate(companyId, branchA, FROM, TO)).thenReturn(Optional.empty());

        service.sendReportsFor(FROM, TO);

        verify(whatsAppProvider, never()).send(any(), any());
        verify(logRepository, never()).save(any());
    }

    @Test
    void raceOnTheUniqueKeyIsSkipped() {
        targets(new BranchTarget(branchA, "A", List.of("919876543210")));
        when(logRepository.saveAndFlush(any(BranchReportLog.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThat(service.sendReportsFor(FROM, TO)).isZero();
        verify(whatsAppProvider, never()).send(any(), any());
    }

    @Test
    void schedulerBindsEachCompanyForTheWorkAndRestoresAfter() {
        CompanyContext.clear();
        targets(new BranchTarget(branchA, "A", List.of("919876543210")));
        List<UUID> seen = new ArrayList<>();
        when(statementPort.generate(eq(companyId), any(), any(), any())).thenAnswer(i -> {
            seen.add(CompanyContext.getCompanyId().orElse(null));
            return Optional.of(new GeneratedStatement(new byte[]{1}, "f.pdf", "A", "December 2026"));
        });

        service.sendReportsFor(FROM, TO);

        assertThat(seen).containsExactly(companyId);
        assertThat(CompanyContext.isSet()).isFalse();
    }

    @Test
    void sendStatusReportsTheLeastProgressedRecipient() {
        targets(new BranchTarget(branchA, "A", List.of("919876543210", "919123456780")),
                new BranchTarget(branchB, "B", List.of()));
        Instant t1 = Instant.parse("2027-01-01T04:00:00Z");
        Instant t2 = Instant.parse("2027-01-01T05:00:00Z");
        BranchReportLog delivered = row(branchA, "919876543210", CommunicationStatus.DELIVERED, 1);
        delivered.setSentAt(t1);
        BranchReportLog failed = row(branchA, "919123456780", CommunicationStatus.FAILED, 1);
        failed.setSentAt(t2);
        when(logRepository.findAllByCompanyIdAndPeriodFromAndPeriodTo(companyId, FROM, TO))
                .thenReturn(List.of(delivered, failed));

        List<BranchSendStatus> status = service.sendStatus(FROM, TO);

        assertThat(status).containsExactly(
                new BranchSendStatus(branchA, "A", 2, "FAILED", t2),
                new BranchSendStatus(branchB, "B", 0, null, null));
    }
}
