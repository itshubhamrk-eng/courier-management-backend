package com.courier.modules.dashboard.application;

import com.courier.modules.finance.application.WalletService;
import com.courier.modules.shipment.domain.ShipmentChargeRepository;
import com.courier.modules.shipment.domain.ShipmentRepository;
import com.courier.modules.shipment.domain.ShipmentStatus;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.security.AuthenticatedUser;
import com.courier.shared.security.Roles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ISSUE-001 regression coverage (see {@code perf-tests/ISSUES.md} in the repo root):
 * a {@code COMPANY_ADMIN} dashboard call must never touch the unscoped/cross-tenant
 * repository methods, and a {@code SUPER_ADMIN} call must never be scoped to a real
 * company id (which would silently filter against the sentinel company id their own
 * JWT carries, per {@link CompanyContext}'s own javadoc).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardServiceImplTest {

    private static final UUID COMPANY = UUID.randomUUID();
    private static final UUID CALLER = UUID.randomUUID();

    @Mock private ShipmentRepository shipmentRepository;
    @Mock private ShipmentChargeRepository shipmentChargeRepository;
    @Mock private WalletService walletService;

    private DashboardServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DashboardServiceImpl(shipmentRepository, shipmentChargeRepository, walletService);
        // Every caller in these tests has no own branch (company/platform admins) —
        // the same degrade-rather-than-throw path DashboardServiceImpl.ownWallet()
        // already documents.
        when(walletService.getForBranch(null)).thenThrow(new BusinessRuleException("no own branch"));
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("COMPANY_ADMIN: every figure is scoped to the caller's own company, never the unscoped cross-tenant methods")
    void companyAdminIsScopedToOwnCompany() {
        CompanyContext.setCompanyId(COMPANY);
        signedIn(Roles.COMPANY_ADMIN);

        when(shipmentRepository.countByCompanyIdAndBookingDateBetween(
                eq(COMPANY), any(LocalDate.class), any(LocalDate.class))).thenReturn(1L);
        when(shipmentRepository.countByCompanyIdAndStatusAndBookingDateBetween(
                eq(COMPANY), eq(ShipmentStatus.DELIVERED), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(2L);
        when(shipmentRepository.countByCompanyIdAndStatusInAndBookingDateBetween(
                eq(COMPANY), any(Collection.class), any(LocalDate.class), any(LocalDate.class))).thenReturn(3L);
        when(shipmentRepository.countByCompanyId(COMPANY)).thenReturn(4L);
        when(shipmentChargeRepository.sumNetAmountByCompanyId(COMPANY)).thenReturn(BigDecimal.TEN);
        when(shipmentChargeRepository.sumNetAmountByCompanyIdAndBookingDateBetween(
                eq(COMPANY), any(LocalDate.class), any(LocalDate.class))).thenReturn(BigDecimal.ONE);
        when(shipmentRepository.findTop5ByCompanyIdOrderByCreatedAtDesc(COMPANY)).thenReturn(List.of());

        service.summary();

        verify(shipmentRepository).countByCompanyId(COMPANY);
        verify(shipmentRepository).countByCompanyIdAndStatusAndBookingDateBetween(
                eq(COMPANY), eq(ShipmentStatus.DELIVERED), any(LocalDate.class), any(LocalDate.class));
        verify(shipmentRepository).findTop5ByCompanyIdOrderByCreatedAtDesc(COMPANY);
        verify(shipmentChargeRepository).sumNetAmountByCompanyId(COMPANY);

        // The whole point of ISSUE-001: none of the unscoped/cross-tenant methods may
        // ever be reached for a company-bound caller.
        verify(shipmentRepository, never()).count();
        verify(shipmentRepository, never()).countByStatus(any());
        verify(shipmentRepository, never()).countByStatusIn(any());
        verify(shipmentRepository, never()).countByStatusAndBookingDateBetween(any(), any(), any());
        verify(shipmentRepository, never()).countByStatusInAndBookingDateBetween(any(), any(), any());
        verify(shipmentRepository, never()).countByBookingDateBetween(any(), any());
        verify(shipmentRepository, never()).findTop5ByOrderByCreatedAtDesc();
        verify(shipmentChargeRepository, never()).sumNetAmount();
        verify(shipmentChargeRepository, never()).sumNetAmountForBookingDateBetween(any(), any());
    }

    @Test
    @DisplayName("SUPER_ADMIN: genuinely cross-tenant, never filtered against the sentinel company id")
    void superAdminIsGenuinelyCrossTenant() {
        // Deliberately NOT binding CompanyContext to a real company here — mirrors the
        // sentinel-cid gotcha this whole bug traces back to: a SUPER_ADMIN's JWT binds
        // a non-null sentinel, so the fix must branch on caller.isSuperAdmin(), never on
        // "is CompanyContext empty".
        signedIn(Roles.SUPER_ADMIN);

        when(shipmentRepository.countByBookingDateBetween(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(10L);
        when(shipmentRepository.countByStatusAndBookingDateBetween(
                eq(ShipmentStatus.DELIVERED), any(LocalDate.class), any(LocalDate.class))).thenReturn(20L);
        when(shipmentRepository.countByStatusInAndBookingDateBetween(
                any(Collection.class), any(LocalDate.class), any(LocalDate.class))).thenReturn(30L);
        when(shipmentRepository.count()).thenReturn(40L);
        when(shipmentChargeRepository.sumNetAmount()).thenReturn(BigDecimal.TEN);
        when(shipmentChargeRepository.sumNetAmountForBookingDateBetween(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(BigDecimal.ONE);
        when(shipmentRepository.findTop5ByOrderByCreatedAtDesc()).thenReturn(List.of());

        service.summary();

        verify(shipmentRepository).count();
        verify(shipmentRepository).countByStatusAndBookingDateBetween(
                eq(ShipmentStatus.DELIVERED), any(LocalDate.class), any(LocalDate.class));
        verify(shipmentRepository).findTop5ByOrderByCreatedAtDesc();
        verify(shipmentChargeRepository).sumNetAmount();

        verify(shipmentRepository, never()).countByStatus(any());
        verify(shipmentRepository, never()).countByStatusIn(any());
        verify(shipmentRepository, never()).countByCompanyId(any());
        verify(shipmentRepository, never()).countByCompanyIdAndStatus(any(), any());
        verify(shipmentRepository, never()).countByCompanyIdAndStatusIn(any(), any());
        verify(shipmentRepository, never()).countByCompanyIdAndStatusAndBookingDateBetween(any(), any(), any(), any());
        verify(shipmentRepository, never()).countByCompanyIdAndStatusInAndBookingDateBetween(any(), any(), any(), any());
        verify(shipmentRepository, never()).countByCompanyIdAndBookingDateBetween(any(), any(), any());
        verify(shipmentRepository, never()).findTop5ByCompanyIdOrderByCreatedAtDesc(any());
        verify(shipmentChargeRepository, never()).sumNetAmountByCompanyId(any());
        verify(shipmentChargeRepository, never()).sumNetAmountByCompanyIdAndBookingDateBetween(any(), any(), any());
    }

    private void signedIn(String role) {
        AuthenticatedUser principal = new AuthenticatedUser(CALLER, COMPANY, "user@test.local", Set.of(role), "jti");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
    }
}
