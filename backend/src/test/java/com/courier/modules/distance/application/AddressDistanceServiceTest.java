package com.courier.modules.distance.application;

import com.courier.modules.company.application.geocoding.GeocodingPort;
import com.courier.modules.company.domain.Branch;
import com.courier.modules.company.domain.BranchRepository;
import com.courier.modules.company.domain.BranchStatus;
import com.courier.modules.company.domain.BranchType;
import com.courier.modules.customer.domain.CustomerAddress;
import com.courier.modules.customer.domain.CustomerAddressRepository;
import com.courier.modules.distance.application.routing.RoutingPort;
import com.courier.modules.distance.domain.AddressDistance;
import com.courier.modules.distance.domain.AddressDistanceRepository;
import com.courier.modules.distance.domain.AddressType;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Distance resolution, caching and refresh, with repositories and routing mocked. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AddressDistanceServiceTest {

    private static final UUID COMPANY = UUID.randomUUID();

    @Mock private AddressDistanceRepository repository;
    @Mock private BranchRepository branchRepository;
    @Mock private CustomerAddressRepository customerAddressRepository;
    @Mock private RoutingPort routingPort;
    @Mock private GeocodingPort geocodingPort;

    private AddressDistanceService service;

    @BeforeEach
    void setUp() {
        service = new AddressDistanceService(
                repository, branchRepository, customerAddressRepository, routingPort, geocodingPort);
        CompanyContext.setCompanyId(COMPANY);
        when(repository.save(any(AddressDistance.class))).thenAnswer(i -> i.getArgument(0));
        when(geocodingPort.geocode(any())).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    private Branch branch(UUID id, BigDecimal lat, BigDecimal lon) {
        Branch b = Branch.builder().branchCode("PUNE").branchName("Pune")
                .branchType(BranchType.BOOKING_BRANCH).status(BranchStatus.ACTIVE)
                .latitude(lat).longitude(lon).build();
        b.setId(id);
        b.setCompanyId(COMPANY);
        return b;
    }

    private CustomerAddress customerAddress(UUID id, BigDecimal lat, BigDecimal lon) {
        CustomerAddress a = CustomerAddress.builder()
                .customerId(UUID.randomUUID()).addressLine1("1 MG Road")
                .latitude(lat).longitude(lon).build();
        a.setId(id);
        a.setCompanyId(COMPANY);
        return a;
    }

    // ------------------------------------------------------------------- resolve (branch)

    @Test
    @DisplayName("resolveBranchDistance returns the cached row without calling routing")
    void resolveBranchDistanceCached() {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        AddressDistance cached = AddressDistance.builder()
                .addressType(AddressType.BRANCH).fromId(from).toId(to)
                .distanceKm(new BigDecimal("12.500")).build();
        when(repository.findByAddressTypeAndFromIdAndToId(AddressType.BRANCH, from, to))
                .thenReturn(Optional.of(cached));

        AddressDistance result = service.resolveBranchDistance(from, to);

        assertThat(result).isSameAs(cached);
        verify(routingPort, never()).route(any(), any());
        verify(branchRepository, never()).findByIdWithinCompany(any(), any());
    }

    @Test
    @DisplayName("resolveBranchDistance computes and stores when absent")
    void resolveBranchDistanceComputes() {
        UUID fromId = UUID.randomUUID();
        UUID toId = UUID.randomUUID();
        Branch from = branch(fromId, new BigDecimal("18.520000"), new BigDecimal("73.850000"));
        Branch to = branch(toId, new BigDecimal("19.076000"), new BigDecimal("72.877700"));

        when(repository.findByAddressTypeAndFromIdAndToId(AddressType.BRANCH, fromId, toId))
                .thenReturn(Optional.empty());
        when(branchRepository.findByIdWithinCompany(fromId, COMPANY)).thenReturn(Optional.of(from));
        when(branchRepository.findByIdWithinCompany(toId, COMPANY)).thenReturn(Optional.of(to));
        when(routingPort.route(any(), any())).thenReturn(Optional.of(
                new RoutingPort.RouteResult(new BigDecimal("148300"), new BigDecimal("7200"))));

        AddressDistance result = service.resolveBranchDistance(fromId, toId);

        assertThat(result.getAddressType()).isEqualTo(AddressType.BRANCH);
        assertThat(result.getDistanceMeter()).isEqualByComparingTo("148300.00");
        assertThat(result.getDistanceKm()).isEqualByComparingTo("148.300");
        assertThat(result.getRequiredTimeMinutes()).isEqualByComparingTo("120.00");

        ArgumentCaptor<RoutingPort.Coordinates> fromCoords = ArgumentCaptor.forClass(RoutingPort.Coordinates.class);
        verify(routingPort).route(fromCoords.capture(), any());
        assertThat(fromCoords.getValue().latitude()).isEqualByComparingTo("18.520000");
    }

    @Test
    @DisplayName("resolving a previously-deleted pair restores the row instead of inserting a duplicate")
    void resolveAfterDeleteRestores() {
        UUID fromId = UUID.randomUUID();
        UUID toId = UUID.randomUUID();
        Branch from = branch(fromId, new BigDecimal("18"), new BigDecimal("73"));
        Branch to = branch(toId, new BigDecimal("19"), new BigDecimal("72"));
        AddressDistance restored = AddressDistance.builder()
                .addressType(AddressType.BRANCH).fromId(fromId).toId(toId)
                .distanceKm(new BigDecimal("50.000")).build();

        // No active row (soft-deleted rows are excluded by @SQLRestriction) — but a
        // deleted one occupies the unique-key slot, so a plain insert would collide.
        when(repository.findByAddressTypeAndFromIdAndToId(AddressType.BRANCH, fromId, toId))
                .thenReturn(Optional.empty(), Optional.of(restored));
        when(branchRepository.findByIdWithinCompany(fromId, COMPANY)).thenReturn(Optional.of(from));
        when(branchRepository.findByIdWithinCompany(toId, COMPANY)).thenReturn(Optional.of(to));
        when(routingPort.route(any(), any())).thenReturn(Optional.of(
                new RoutingPort.RouteResult(new BigDecimal("50000"), new BigDecimal("1800"))));
        when(repository.countDeletedPair(any(), eq("BRANCH"), any(), any())).thenReturn(1L);

        AddressDistance result = service.resolveBranchDistance(fromId, toId);

        assertThat(result).isSameAs(restored);
        verify(repository).restoreAndUpdate(any(), eq("BRANCH"), any(), any(), any(), any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("same branch on both sides is refused")
    void refusesSamePair() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> service.resolveBranchDistance(id, id))
                .isInstanceOf(BusinessRuleException.class);
        verify(repository, never()).findByAddressTypeAndFromIdAndToId(any(), any(), any());
    }

    @Test
    @DisplayName("a branch with no coordinates refuses the calculation")
    void refusesUnlocatedBranch() {
        UUID fromId = UUID.randomUUID();
        UUID toId = UUID.randomUUID();
        when(repository.findByAddressTypeAndFromIdAndToId(AddressType.BRANCH, fromId, toId))
                .thenReturn(Optional.empty());
        when(branchRepository.findByIdWithinCompany(fromId, COMPANY))
                .thenReturn(Optional.of(branch(fromId, null, null)));
        when(branchRepository.findByIdWithinCompany(toId, COMPANY))
                .thenReturn(Optional.of(branch(toId, new BigDecimal("19"), new BigDecimal("72"))));

        assertThatThrownBy(() -> service.resolveBranchDistance(fromId, toId))
                .isInstanceOf(BusinessRuleException.class);
        verify(routingPort, never()).route(any(), any());
    }

    @Test
    @DisplayName("an unlocated branch is geocoded on demand instead of refused")
    void geocodesUnlocatedBranchOnDemand() {
        UUID fromId = UUID.randomUUID();
        UUID toId = UUID.randomUUID();
        Branch unlocated = branch(fromId, null, null);
        when(repository.findByAddressTypeAndFromIdAndToId(AddressType.BRANCH, fromId, toId))
                .thenReturn(Optional.empty());
        when(branchRepository.findByIdWithinCompany(fromId, COMPANY)).thenReturn(Optional.of(unlocated));
        when(branchRepository.findByIdWithinCompany(toId, COMPANY))
                .thenReturn(Optional.of(branch(toId, new BigDecimal("19"), new BigDecimal("72"))));
        when(geocodingPort.geocode(any())).thenReturn(Optional.of(
                new GeocodingPort.Coordinates(new BigDecimal("18.520000"), new BigDecimal("73.850000"))));
        when(routingPort.route(any(), any())).thenReturn(Optional.of(
                new RoutingPort.RouteResult(new BigDecimal("1000"), new BigDecimal("60"))));

        AddressDistance result = service.resolveBranchDistance(fromId, toId);

        assertThat(result).isNotNull();
        assertThat(unlocated.getLatitude()).isEqualByComparingTo("18.520000");
        verify(branchRepository).save(unlocated);
    }

    @Test
    @DisplayName("an unresolvable route surfaces as a failure, not a silent miss")
    void routingFailureSurfaces() {
        UUID fromId = UUID.randomUUID();
        UUID toId = UUID.randomUUID();
        when(repository.findByAddressTypeAndFromIdAndToId(AddressType.BRANCH, fromId, toId))
                .thenReturn(Optional.empty());
        when(branchRepository.findByIdWithinCompany(fromId, COMPANY))
                .thenReturn(Optional.of(branch(fromId, new BigDecimal("18"), new BigDecimal("73"))));
        when(branchRepository.findByIdWithinCompany(toId, COMPANY))
                .thenReturn(Optional.of(branch(toId, new BigDecimal("19"), new BigDecimal("72"))));
        when(routingPort.route(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveBranchDistance(fromId, toId))
                .isInstanceOf(BusinessRuleException.class);
        verify(repository, never()).save(any());
    }

    // ---------------------------------------------------------------- resolve (customer)

    @Test
    @DisplayName("resolveCustomerAddressDistance reads CustomerAddress, not Customer")
    void resolveCustomerAddressDistanceComputes() {
        UUID fromId = UUID.randomUUID();
        UUID toId = UUID.randomUUID();
        when(repository.findByAddressTypeAndFromIdAndToId(AddressType.CUSTOMER, fromId, toId))
                .thenReturn(Optional.empty());
        when(customerAddressRepository.findByIdWithinCompany(fromId, COMPANY))
                .thenReturn(Optional.of(customerAddress(fromId, new BigDecimal("18.5"), new BigDecimal("73.8"))));
        when(customerAddressRepository.findByIdWithinCompany(toId, COMPANY))
                .thenReturn(Optional.of(customerAddress(toId, new BigDecimal("19.0"), new BigDecimal("72.8"))));
        when(routingPort.route(any(), any())).thenReturn(Optional.of(
                new RoutingPort.RouteResult(new BigDecimal("5000"), new BigDecimal("600"))));

        AddressDistance result = service.resolveCustomerAddressDistance(fromId, toId);

        assertThat(result.getAddressType()).isEqualTo(AddressType.CUSTOMER);
        verify(branchRepository, never()).findByIdWithinCompany(any(), any());
    }

    // ---------------------------------------------------------------------------- get

    @Test
    @DisplayName("get 404s for a foreign or unknown id")
    void getMissing() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdWithinCompany(id, COMPANY)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ------------------------------------------------------------------------- search

    @Test
    @DisplayName("search delegates filters straight through")
    void search() {
        service.search(AddressType.BRANCH, null, null);
        verify(repository).search(COMPANY, AddressType.BRANCH, null, null);
    }

    // ------------------------------------------------------------------------- delete

    @Test
    @DisplayName("delete soft-deletes the row")
    void delete() {
        UUID id = UUID.randomUUID();
        AddressDistance existing = AddressDistance.builder()
                .addressType(AddressType.BRANCH).fromId(UUID.randomUUID()).toId(UUID.randomUUID()).build();
        when(repository.findByIdWithinCompany(id, COMPANY)).thenReturn(Optional.of(existing));

        service.delete(id);

        assertThat(existing.isDeleted()).isTrue();
        verify(repository).save(existing);
    }

    // ------------------------------------------------------------------------ refresh

    @Test
    @DisplayName("refresh recomputes an existing row in place, keeping its identity")
    void refresh() {
        UUID id = UUID.randomUUID();
        UUID fromId = UUID.randomUUID();
        UUID toId = UUID.randomUUID();
        AddressDistance existing = AddressDistance.builder()
                .addressType(AddressType.BRANCH).fromId(fromId).toId(toId)
                .distanceKm(new BigDecimal("1.000")).build();
        when(repository.findByIdWithinCompany(id, COMPANY)).thenReturn(Optional.of(existing));
        when(branchRepository.findByIdWithinCompany(fromId, COMPANY))
                .thenReturn(Optional.of(branch(fromId, new BigDecimal("18"), new BigDecimal("73"))));
        when(branchRepository.findByIdWithinCompany(toId, COMPANY))
                .thenReturn(Optional.of(branch(toId, new BigDecimal("19"), new BigDecimal("72"))));
        when(routingPort.route(any(), any())).thenReturn(Optional.of(
                new RoutingPort.RouteResult(new BigDecimal("9000"), new BigDecimal("300"))));

        AddressDistance result = service.refresh(id);

        assertThat(result).isSameAs(existing);
        assertThat(result.getDistanceKm()).isEqualByComparingTo("9.000");
        assertThat(result.getRequiredTimeMinutes()).isEqualByComparingTo("5.00");
    }
}
