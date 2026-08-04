package com.courier.modules.manifest.application;

import com.courier.modules.manifest.application.command.CreateVehicleCommand;
import com.courier.modules.manifest.domain.Vehicle;
import com.courier.modules.manifest.domain.VehicleRepository;
import com.courier.modules.manifest.domain.VehicleStatus;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.security.Roles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VehicleServiceImpl implements VehicleService {

    private static final String ENTITY = "Vehicle";
    private static final String WRITERS =
            "hasAnyRole('" + Roles.COMPANY_ADMIN + "', '" + Roles.BRANCH_MANAGER + "')";
    private static final String READERS = "isAuthenticated()";

    private final VehicleRepository repository;
    private final AuditService auditService;

    @Override
    @Transactional
    @PreAuthorize(WRITERS)
    public Vehicle create(CreateVehicleCommand command) {
        UUID companyId = requireCompany();

        Vehicle vehicle = Vehicle.builder()
                .vehicleNumber(command.vehicleNumber())
                .vehicleTypeId(command.vehicleTypeId())
                .capacityKg(command.capacityKg())
                .remarks(command.remarks())
                .build();
        vehicle.applyInvariants();

        if (repository.existsByCompanyIdAndVehicleNumber(companyId, vehicle.getVehicleNumber())) {
            throw new BusinessRuleException(
                    "Vehicle number %s is already in use.".formatted(vehicle.getVehicleNumber()));
        }

        Vehicle saved = repository.save(vehicle);
        auditService.record(AuditAction.VEHICLE_CREATED, ENTITY, saved.getId(),
                Map.of("vehicleNumber", saved.getVehicleNumber()));
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READERS)
    public Vehicle getById(UUID id) {
        return loadOrThrow(id, requireCompany());
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READERS)
    public List<Vehicle> listActive() {
        return repository.findAllByCompanyIdAndStatusOrderByVehicleNumberAsc(requireCompany(), VehicleStatus.ACTIVE);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READERS)
    public List<Vehicle> listAll() {
        return repository.findAllByCompanyIdOrderByVehicleNumberAsc(requireCompany());
    }

    @Override
    @Transactional
    @PreAuthorize(WRITERS)
    public Vehicle activate(UUID id) {
        UUID companyId = requireCompany();
        Vehicle vehicle = loadOrThrow(id, companyId);
        vehicle.setStatus(VehicleStatus.ACTIVE);
        Vehicle saved = repository.save(vehicle);
        auditService.record(AuditAction.VEHICLE_ACTIVATED, ENTITY, saved.getId(),
                Map.of("vehicleNumber", saved.getVehicleNumber()));
        return saved;
    }

    @Override
    @Transactional
    @PreAuthorize(WRITERS)
    public Vehicle deactivate(UUID id) {
        UUID companyId = requireCompany();
        Vehicle vehicle = loadOrThrow(id, companyId);
        vehicle.setStatus(VehicleStatus.INACTIVE);
        Vehicle saved = repository.save(vehicle);
        auditService.record(AuditAction.VEHICLE_DEACTIVATED, ENTITY, saved.getId(),
                Map.of("vehicleNumber", saved.getVehicleNumber()));
        return saved;
    }

    private Vehicle loadOrThrow(UUID id, UUID companyId) {
        return repository.findByIdWithinCompany(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, id));
    }

    private UUID requireCompany() {
        return CompanyContext.getCompanyId().orElseThrow(() -> new BusinessRuleException(
                "No company is bound to this request. Vehicles belong to a company."));
    }
}
