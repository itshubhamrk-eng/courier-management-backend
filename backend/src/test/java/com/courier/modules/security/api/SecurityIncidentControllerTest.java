package com.courier.modules.security.api;

import com.courier.modules.auth.domain.Role;
import com.courier.modules.auth.domain.User;
import com.courier.modules.auth.domain.UserRepository;
import com.courier.modules.auth.domain.UserStatus;
import com.courier.modules.company.domain.BranchRepository;
import com.courier.modules.security.application.SecurityIncidentService;
import com.courier.modules.security.application.UserBanService;
import com.courier.modules.security.domain.AutoAction;
import com.courier.modules.security.domain.IncidentRule;
import com.courier.modules.security.domain.IncidentSeverity;
import com.courier.modules.security.domain.IncidentStatus;
import com.courier.modules.security.domain.SecurityIncident;
import com.courier.modules.security.domain.SecurityIncidentRepository;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.exception.GlobalExceptionHandler;
import com.courier.shared.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives the controller through MockMvc with method security switched on, over the REAL
 * {@link SecurityIncidentService} (mocked repositories), so company isolation and the
 * permission gate are exercised end to end rather than assumed.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = SecurityIncidentControllerTest.TestConfig.class)
class SecurityIncidentControllerTest {

    @Configuration
    @EnableWebMvc
    @EnableMethodSecurity
    @Import({SecurityIncidentController.class, IncidentResponseAssembler.class, GlobalExceptionHandler.class})
    static class TestConfig {
        @Bean SecurityIncidentRepository incidentRepository() { return mock(SecurityIncidentRepository.class); }
        @Bean UserRepository userRepository() { return mock(UserRepository.class); }
        @Bean BranchRepository branchRepository() { return mock(BranchRepository.class); }
        @Bean UserBanService userBanService() { return mock(UserBanService.class); }
        @Bean AuditService auditService() { return mock(AuditService.class); }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean SecurityIncidentService incidentService(SecurityIncidentRepository i, UserRepository u,
                                                      UserBanService b, AuditService a, ObjectMapper o) {
            return org.mockito.Mockito.spy(new SecurityIncidentService(i, u, b, a, o));
        }
    }

    @Autowired WebApplicationContext context;
    @Autowired SecurityIncidentRepository incidents;
    @Autowired UserRepository users;
    @Autowired BranchRepository branches;
    @Autowired UserBanService banService;
    @Autowired SecurityIncidentService service;

    private MockMvc mvc;
    private final UUID companyA = UUID.randomUUID();
    private final UUID companyB = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID targetId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        reset(incidents, users, branches, banService);
        when(incidents.save(any(SecurityIncident.class))).thenAnswer(inv -> inv.getArgument(0));
        actAs(actorId, companyA, Set.of("COMPANY_ADMIN"), Set.of("SECURITY_INCIDENT_READ", "SECURITY_INCIDENT_UPDATE"));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void actAs(UUID userId, UUID companyId, Set<String> roles, Set<String> permissions) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, companyId, "a@b.com", roles, permissions, "jti");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
    }

    private SecurityIncident incident(UUID company, UUID user, IncidentStatus status, String evidence) {
        SecurityIncident i = SecurityIncident.builder()
                .companyId(company).userId(user).rule(IncidentRule.SESSION_MULTI_IP)
                .severity(IncidentSeverity.MEDIUM).status(status).autoAction(AutoAction.NONE)
                .evidence(evidence).detectedAt(Instant.now()).build();
        i.setId(UUID.randomUUID());
        when(incidents.findById(i.getId())).thenReturn(Optional.of(i));
        return i;
    }

    // ------------------------------------------------------------------ list

    @Test
    void listIsScopedToCallersCompanyAndSortedNewestFirst() throws Exception {
        SecurityIncident mine = incident(companyA, null, IncidentStatus.OPEN, "{\"ip\":\"1.2.3.4\"}");
        when(incidents.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of(mine), inv.getArgument(1), 1));

        mvc.perform(get("/api/v1/security/incidents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(mine.getId().toString()))
                .andExpect(jsonPath("$.data.content[0].evidence.ip").value("1.2.3.4"));

        ArgumentCaptor<Specification<SecurityIncident>> spec = ArgumentCaptor.forClass(Specification.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(incidents).findAll(spec.capture(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageable.getValue().getSort().getOrderFor("detectedAt").isDescending()).isTrue();

        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Root<SecurityIncident> root = mock(Root.class);
        Path<Object> companyPath = mock(Path.class);
        when(root.get("companyId")).thenReturn(companyPath);
        when(cb.equal(any(), any(Object.class))).thenReturn(mock(Predicate.class));
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));
        spec.getValue().toPredicate(root, mock(CriteriaQuery.class), cb);
        verify(cb).equal(companyPath, companyA);
    }

    @Test
    void listPageSizeIsCappedAtOneHundred() throws Exception {
        when(incidents.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<SecurityIncident>(List.of(), inv.getArgument(1), 0));

        mvc.perform(get("/api/v1/security/incidents").param("size", "5000").param("page", "2"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(incidents).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
    }

    @Test
    void listWithInvalidEnumFilterIsBadRequest() throws Exception {
        mvc.perform(get("/api/v1/security/incidents").param("status", "NOPE"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/security/incidents").param("severity", "NOPE"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/security/incidents").param("rule", "NOPE"))
                .andExpect(status().isBadRequest());
        verify(incidents, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void listResolvesNamesWithOneBatchedLookup() throws Exception {
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        SecurityIncident a = incident(companyA, u1, IncidentStatus.OPEN, null);
        SecurityIncident b = incident(companyA, u2, IncidentStatus.OPEN, null);
        when(incidents.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of(a, b), inv.getArgument(1), 2));
        User user1 = user(u1, "Asha", "Rao", branchId, Role.OPERATOR);
        User user2 = user(u2, "Ravi", "K", null, Role.DRIVER);
        when(users.findAllByIdIn(any())).thenReturn(List.of(user1, user2));
        com.courier.modules.company.domain.Branch branch = mock(com.courier.modules.company.domain.Branch.class);
        when(branch.getId()).thenReturn(branchId);
        when(branch.getBranchName()).thenReturn("Pune Central");
        when(branches.findAllByIdIn(any())).thenReturn(List.of(branch));

        mvc.perform(get("/api/v1/security/incidents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].userDisplayName").value("Asha Rao"))
                .andExpect(jsonPath("$.data.content[0].branchName").value("Pune Central"))
                .andExpect(jsonPath("$.data.content[1].userDisplayName").value("Ravi K"))
                .andExpect(jsonPath("$.data.content[1].branchName").doesNotExist());

        verify(users, org.mockito.Mockito.times(1)).findAllByIdIn(any());
        verify(branches, org.mockito.Mockito.times(1)).findAllByIdIn(any());
        verify(users, never()).findById(any());
    }

    private User user(UUID id, String first, String last, UUID branchId, Role role) {
        User u = User.builder().email(first.toLowerCase() + "@x.com").firstName(first).lastName(last)
                .branchId(branchId).status(UserStatus.ACTIVE).roles(EnumSet.of(role))
                .passwordHash("SECRET-HASH").build();
        u.setId(id);
        return u;
    }

    // ------------------------------------------------------------------ detail

    @Test
    void detailIncludesUserRolesBranchAndParsedEvidenceWithoutSecrets() throws Exception {
        UUID branchId = UUID.randomUUID();
        SecurityIncident i = incident(companyA, targetId, IncidentStatus.OPEN, "{\"ips\":[\"1.1.1.1\"],\"count\":3}");
        when(users.findAllByIdIn(any())).thenReturn(List.of(user(targetId, "Asha", "Rao", branchId, Role.OPERATOR)));
        com.courier.modules.company.domain.Branch branch = mock(com.courier.modules.company.domain.Branch.class);
        when(branch.getId()).thenReturn(branchId);
        when(branch.getBranchName()).thenReturn("Pune Central");
        when(branches.findAllByIdIn(any())).thenReturn(List.of(branch));

        String body = mvc.perform(get("/api/v1/security/incidents/{id}", i.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userDisplayName").value("Asha Rao"))
                .andExpect(jsonPath("$.data.userEmail").value("asha@x.com"))
                .andExpect(jsonPath("$.data.userRoles[0]").value("OPERATOR"))
                .andExpect(jsonPath("$.data.branchName").value("Pune Central"))
                .andExpect(jsonPath("$.data.evidence.count").value(3))
                .andExpect(jsonPath("$.data.evidence.ips[0]").value("1.1.1.1"))
                .andExpect(jsonPath("$.data.evidenceUnreadable").value(false))
                .andExpect(jsonPath("$.data.rule").value("SESSION_MULTI_IP"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("SECRET-HASH").doesNotContain("passwordHash");
    }

    @Test
    void corruptEvidenceDegradesToEmptyObjectWithFlag() throws Exception {
        SecurityIncident i = incident(companyA, null, IncidentStatus.OPEN, "{not json");

        mvc.perform(get("/api/v1/security/incidents/{id}", i.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.evidence").isMap())
                .andExpect(jsonPath("$.data.evidence").isEmpty())
                .andExpect(jsonPath("$.data.evidenceUnreadable").value(true))
                .andExpect(jsonPath("$.data.userDisplayName").doesNotExist());
    }

    @Test
    void otherCompanysIncidentIsNotFound() throws Exception {
        SecurityIncident other = incident(companyB, null, IncidentStatus.OPEN, null);

        mvc.perform(get("/api/v1/security/incidents/{id}", other.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void platformAdminCanGetNullCompanyIncident() throws Exception {
        actAs(actorId, null, Set.of("PLATFORM_ADMIN"), Set.of());
        SecurityIncident i = incident(null, null, IncidentStatus.OPEN, null);

        mvc.perform(get("/api/v1/security/incidents/{id}", i.getId()))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ permissions

    @Test
    void callerWithoutPermissionIsForbidden() throws Exception {
        actAs(actorId, companyA, Set.of("OPERATOR"), Set.of("SHIPMENT_READ"));
        SecurityIncident i = incident(companyA, null, IncidentStatus.OPEN, null);

        mvc.perform(get("/api/v1/security/incidents")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/security/incidents/summary")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/security/incidents/{id}", i.getId())).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/security/incidents/{id}/dismiss", i.getId())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
    }

    @Test
    void readOnlyHolderCannotBanDismissOrUnban() throws Exception {
        actAs(actorId, companyA, Set.of("COMPANY_ADMIN"), Set.of("SECURITY_INCIDENT_READ"));
        SecurityIncident i = incident(companyA, targetId, IncidentStatus.BANNED, null);

        for (String action : List.of("ban", "dismiss", "unban")) {
            mvc.perform(post("/api/v1/security/incidents/{id}/" + action, i.getId())
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }
        verify(banService, never()).ban(any(), any());
        verify(banService, never()).unban(any());
    }

    // ------------------------------------------------------------------ actions

    @Test
    void banDismissUnbanUseTheCallerAsActor() throws Exception {
        SecurityIncident toBan = incident(companyA, targetId, IncidentStatus.OPEN, null);
        SecurityIncident toDismiss = incident(companyA, null, IncidentStatus.OPEN, null);
        SecurityIncident toUnban = incident(companyA, null, IncidentStatus.BANNED, null);
        when(users.findById(targetId)).thenReturn(Optional.of(user(targetId, "T", "U", null, Role.OPERATOR)));

        act("ban", toBan, "  confirmed  ");
        act("dismiss", toDismiss, "false alarm");
        act("unban", toUnban, "ok");

        verify(service).ban(toBan.getId(), actorId, "confirmed");
        verify(service).dismiss(toDismiss.getId(), actorId, "false alarm");
        verify(service).unban(toUnban.getId(), actorId, "ok");
        assertThat(toBan.getResolvedBy()).isEqualTo(actorId);
        assertThat(toBan.getStatus()).isEqualTo(IncidentStatus.BANNED);
        assertThat(toDismiss.getStatus()).isEqualTo(IncidentStatus.DISMISSED);
        assertThat(toUnban.getStatus()).isEqualTo(IncidentStatus.UNBANNED);
    }

    private void act(String action, SecurityIncident i, String note) throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/security/incidents/{id}/" + action, i.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"" + note + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void bodyCannotSpoofTheActor() throws Exception {
        UUID spoof = UUID.randomUUID();
        SecurityIncident i = incident(companyA, null, IncidentStatus.OPEN, null);

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/security/incidents/{id}/dismiss", i.getId())
                        .param("actorId", spoof.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"x\",\"actorId\":\"" + spoof + "\",\"resolvedBy\":\"" + spoof + "\"}"))
                .andExpect(status().isOk());

        verify(service).dismiss(i.getId(), actorId, "x");
        assertThat(i.getResolvedBy()).isEqualTo(actorId).isNotEqualTo(spoof);
    }

    @Test
    void blankOrMissingNoteIsAllowedAndTooLongNoteIsRejected() throws Exception {
        SecurityIncident i = incident(companyA, null, IncidentStatus.OPEN, null);

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/security/incidents/{id}/dismiss", i.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"   \"}"))
                .andExpect(status().isOk());
        verify(service).dismiss(i.getId(), actorId, null);

        SecurityIncident j = incident(companyA, null, IncidentStatus.OPEN, null);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/security/incidents/{id}/dismiss", j.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"" + "a".repeat(1001) + "\"}"))
                .andExpect(status().isBadRequest());
        verify(service, never()).dismiss(eq(j.getId()), any(), anyString());
    }

    @Test
    void platformAdminCanUnbanNullCompanyIncident() throws Exception {
        actAs(actorId, null, Set.of("PLATFORM_ADMIN"), Set.of());
        SecurityIncident i = incident(null, null, IncidentStatus.BANNED, null);

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/security/incidents/{id}/unban", i.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UNBANNED"));
    }

    @Test
    void companyAdminCannotBanPlatformAdminTargetButPlatformAdminCan() throws Exception {
        SecurityIncident i = incident(companyA, targetId, IncidentStatus.OPEN, null);
        when(users.findById(targetId)).thenReturn(Optional.of(user(targetId, "P", "A", null, Role.PLATFORM_ADMIN)));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/security/incidents/{id}/ban", i.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        verify(banService, never()).ban(any(), any());

        actAs(actorId, null, Set.of("PLATFORM_ADMIN"), Set.of());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/security/incidents/{id}/ban", i.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        verify(banService).ban(eq(targetId), anyString());
    }

    // ------------------------------------------------------------------ summary

    @Test
    void summaryReturnsCountsByStatus() throws Exception {
        when(incidents.count(any(Specification.class))).thenReturn(4L, 2L, 7L, 1L);

        mvc.perform(get("/api/v1/security/incidents/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.OPEN").value(4))
                .andExpect(jsonPath("$.data.BANNED").value(2))
                .andExpect(jsonPath("$.data.DISMISSED").value(7))
                .andExpect(jsonPath("$.data.UNBANNED").value(1));
    }

    @Test
    void summaryForCallerWithoutCompanyIsAllZeroAndRunsNoQuery() throws Exception {
        actAs(actorId, null, Set.of("COMPANY_ADMIN"), Set.of("SECURITY_INCIDENT_READ"));

        mvc.perform(get("/api/v1/security/incidents/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.OPEN").value(0));
        verify(incidents, never()).count(any(Specification.class));
    }
}
