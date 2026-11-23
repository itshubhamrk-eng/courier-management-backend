package com.courier.modules.branchreport.api;

import com.courier.modules.communication.application.BranchReportService;
import com.courier.modules.communication.application.BranchReportService.BranchSendStatus;
import com.courier.modules.communication.application.BranchReportService.SendOutcome;
import com.courier.modules.communication.domain.BranchStatementPort;
import com.courier.modules.communication.domain.BranchStatementPort.GeneratedStatement;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.GlobalExceptionHandler;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real method security + real exception handler around mocked service and port. */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = BranchReportControllerTest.TestConfig.class)
class BranchReportControllerTest {

    @Configuration
    @EnableWebMvc
    @EnableMethodSecurity
    @Import({BranchReportController.class, GlobalExceptionHandler.class})
    static class TestConfig {
        @Bean BranchReportService reportService() { return mock(BranchReportService.class); }
        @Bean BranchStatementPort statementPort() { return mock(BranchStatementPort.class); }
        @Bean AuditService auditService() { return mock(AuditService.class); }
    }

    private static final String BASE = "/api/v1/shipments/vendor-audit";
    private static final UUID COMPANY = UUID.randomUUID();
    private static final UUID BRANCH = UUID.randomUUID();

    @Autowired WebApplicationContext context;
    @Autowired BranchReportService service;
    @Autowired BranchStatementPort port;
    @Autowired AuditService audit;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        reset(service, port, audit);
        CompanyContext.setCompanyId(COMPANY);
        actAs("COMPANY_ADMIN");
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        CompanyContext.clear();
    }

    private void actAs(String role) {
        AuthenticatedUser p = new AuthenticatedUser(UUID.randomUUID(), COMPANY, "a@b.com", Set.of(role),
                Set.of(), "jti");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(p, null, p.authorities()));
    }

    @Test
    void operatorGets403OnAllFourEndpoints() throws Exception {
        actAs("OPERATOR");
        String q = "?from=2026-10-01&to=2026-10-31";
        mvc.perform(get(BASE + "/" + BRANCH + "/pdf" + q)).andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/" + BRANCH + "/send" + q)).andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/send-all" + q)).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/send-status" + q)).andExpect(status().isForbidden());
        verifyNoInteractions(service, port, audit);
    }

    @Test
    void branchManagerGets403() throws Exception {
        actAs("BRANCH_MANAGER");
        mvc.perform(get(BASE + "/send-status?from=2026-10-01&to=2026-10-31")).andExpect(status().isForbidden());
    }

    @Test
    void allowedRolesCanReadSendStatus() throws Exception {
        when(service.sendStatus(any(), any())).thenReturn(List.of(
                new BranchSendStatus(BRANCH, "Pune", 2, "SENT", Instant.parse("2026-11-01T03:30:00Z"))));
        for (String role : List.of("COMPANY_ADMIN", "FINANCE_USER", "ACCOUNTS")) {
            actAs(role);
            mvc.perform(get(BASE + "/send-status?from=2026-10-01&to=2026-10-31"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].branchName").value("Pune"))
                    .andExpect(jsonPath("$.data[0].managerPhoneCount").value(2))
                    .andExpect(jsonPath("$.data[0].lastStatus").value("SENT"));
        }
    }

    @Test
    void fromAfterToIs400() throws Exception {
        mvc.perform(get(BASE + "/send-status?from=2026-11-01&to=2026-10-01")).andExpect(status().isBadRequest());
        mvc.perform(post(BASE + "/send-all?from=2026-11-01&to=2026-10-01")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void rangeOver366DaysIs400AndExactly366Passes() throws Exception {
        mvc.perform(get(BASE + "/send-status?from=2025-01-01&to=2026-01-03")).andExpect(status().isBadRequest());
        when(service.sendStatus(any(), any())).thenReturn(List.of());
        mvc.perform(get(BASE + "/send-status?from=2025-01-01&to=2026-01-01")).andExpect(status().isOk());
    }

    @Test
    void missingDatesIs400() throws Exception {
        mvc.perform(get(BASE + "/send-status")).andExpect(status().isBadRequest());
    }

    @Test
    void otherCompanyBranchPdfIs404() throws Exception {
        when(port.generate(eq(COMPANY), eq(BRANCH), any(), any())).thenReturn(Optional.empty());
        mvc.perform(get(BASE + "/" + BRANCH + "/pdf?from=2026-10-01&to=2026-10-31"))
                .andExpect(status().isNotFound());
    }

    @Test
    void otherCompanyBranchSendIs404() throws Exception {
        when(service.sendForBranch(eq(BRANCH), any(), any(), anyBoolean()))
                .thenThrow(new ResourceNotFoundException("Branch", BRANCH));
        mvc.perform(post(BASE + "/" + BRANCH + "/send?from=2026-10-01&to=2026-10-31"))
                .andExpect(status().isNotFound());
        verify(audit, never()).record(any(), any(), any(), any());
    }

    @Test
    void pdfIsAttachmentStartingWithPdfMagic() throws Exception {
        byte[] pdf = "%PDF-1.7 body".getBytes(StandardCharsets.US_ASCII);
        when(port.generate(COMPANY, BRANCH, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))
                .thenReturn(Optional.of(new GeneratedStatement(pdf, "pune-2026-10.pdf", "Pune", "October 2026")));

        MvcResult r = mvc.perform(get(BASE + "/" + BRANCH + "/pdf?from=2026-10-01&to=2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"pune-2026-10.pdf\""))
                .andReturn();
        assertTrue(new String(r.getResponse().getContentAsByteArray(), StandardCharsets.US_ASCII).startsWith("%PDF"));
    }

    @Test
    void sendWithoutForceReturnsAlreadySentAnd200() throws Exception {
        when(service.sendForBranch(BRANCH, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), false))
                .thenReturn(new SendOutcome(BRANCH, "Pune", "ALREADY_SENT"));

        mvc.perform(post(BASE + "/" + BRANCH + "/send?from=2026-10-01&to=2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("ALREADY_SENT"))
                .andExpect(jsonPath("$.data.branchName").value("Pune"));
        verify(service).sendForBranch(BRANCH, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), false);
    }

    @Test
    void sendWithForcePassesForceTrueAndAudits() throws Exception {
        when(service.sendForBranch(BRANCH, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), true))
                .thenReturn(new SendOutcome(BRANCH, "Pune", "SENT"));

        mvc.perform(post(BASE + "/" + BRANCH + "/send?from=2026-10-01&to=2026-10-31&force=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("SENT"));
        verify(service).sendForBranch(BRANCH, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), true);
        verify(audit).record(any(), eq("Branch"), eq(BRANCH), any());
    }

    @Test
    void sendAllReturnsList() throws Exception {
        when(service.sendForAllBranches(any(), any(), eq(false))).thenReturn(List.of(
                new SendOutcome(BRANCH, "Pune", "SENT"),
                new SendOutcome(UUID.randomUUID(), "Goa", "NO_PHONE")));

        mvc.perform(post(BASE + "/send-all?from=2026-10-01&to=2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[1].result").value("NO_PHONE"));
    }
}
