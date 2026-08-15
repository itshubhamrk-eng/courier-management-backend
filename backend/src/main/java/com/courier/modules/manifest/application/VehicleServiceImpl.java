package com.courier.modules.manifest.application;

import com.courier.modules.manifest.application.command.CreateVehicleCommand;
import com.courier.modules.manifest.application.command.UpdateVehicleCommand;
import com.courier.modules.manifest.domain.Vehicle;
import com.courier.modules.manifest.domain.VehicleRepository;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.security.Roles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
                .vehicleType(command.vehicleType())
                .make(command.make())
                .model(command.model())
                .fuelType(command.fuelType())
                .capacityKg(command.capacityKg())
                .currentOdometer(command.currentOdometer())
                .purchaseDate(command.purchaseDate())
                .registrationDate(command.registrationDate())
                .insuranceExpiry(command.insuranceExpiry())
                .pucExpiry(command.pucExpiry())
                .fitnessExpiry(command.fitnessExpiry())
                .permitExpiry(command.permitExpiry())
                .branchId(command.branchId())
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
    @Transactional
    @PreAuthorize(WRITERS)
    public Vehicle update(UUID id, UpdateVehicleCommand command) {
        UUID companyId = requireCompany();
        Vehicle vehicle = loadOrThrow(id, companyId);
        requireCurrentVersion(vehicle, command.expectedVersion());

        Map<String, Object> before = snapshot(vehicle);

        vehicle.setVehicleNumber(command.vehicleNumber());
        vehicle.setVehicleType(command.vehicleType());
        vehicle.setMake(command.make());
        vehicle.setModel(command.model());
        vehicle.setFuelType(command.fuelType());
        vehicle.setCapacityKg(command.capacityKg());
        vehicle.setCurrentOdometer(command.currentOdometer());
        vehicle.setPurchaseDate(command.purchaseDate());
        vehicle.setRegistrationDate(command.registrationDate());
        vehicle.setInsuranceExpiry(command.insuranceExpiry());
        vehicle.setPucExpiry(command.pucExpiry());
        vehicle.setFitnessExpiry(command.fitnessExpiry());
        vehicle.setPermitExpiry(command.permitExpiry());
        vehicle.setStatus(command.status());
        vehicle.setBranchId(command.branchId());
        vehicle.setRemarks(command.remarks());
        vehicle.applyInvariants();

        if (!vehicle.getVehicleNumber().equals(before.get("vehicleNumber"))
                && repository.existsByCompanyIdAndVehicleNumber(companyId, vehicle.getVehicleNumber())) {
            throw new BusinessRuleException(
                    "Vehicle number %s is already in use.".formatted(vehicle.getVehicleNumber()));
        }

        Vehicle saved = repository.save(vehicle);
        Map<String, Object> changes = changeDetails(before, snapshot(saved));
        auditService.record(AuditAction.VEHICLE_UPDATED, ENTITY, saved.getId(), changes);
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
        return repository.findAllByCompanyIdAndActiveTrueOrderByVehicleNumberAsc(requireCompany());
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
        vehicle.activate();
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
        vehicle.deactivate();
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

    private void requireCurrentVersion(Vehicle vehicle, Long expectedVersion) {
        if (expectedVersion == null) {
            return;
        }
        if (!Objects.equals(vehicle.getVersion(), expectedVersion)) {
            throw new ObjectOptimisticLockingFailureException(Vehicle.class, vehicle.getId());
        }
    }

    private Map<String, Object> snapshot(Vehicle v) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("vehicleNumber", v.getVehicleNumber());
        map.put("vehicleType", v.getVehicleType());
        map.put("make", v.getMake());
        map.put("model", v.getModel());
        map.put("fuelType", v.getFuelType());
        map.put("capacityKg", v.getCapacityKg());
        map.put("currentOdometer", v.getCurrentOdometer());
        map.put("purchaseDate", v.getPurchaseDate());
        map.put("registrationDate", v.getRegistrationDate());
        map.put("insuranceExpiry", v.getInsuranceExpiry());
        map.put("pucExpiry", v.getPucExpiry());
        map.put("fitnessExpiry", v.getFitnessExpiry());
        map.put("permitExpiry", v.getPermitExpiry());
        map.put("status", v.getStatus());
        map.put("branchId", v.getBranchId());
        map.put("remarks", v.getRemarks());
        return map;
    }

    private Map<String, Object> changeDetails(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> changes = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : after.entrySet()) {
            Object previous = before.get(entry.getKey());
            if (!Objects.equals(previous, entry.getValue())) {
                changes.put(entry.getKey(), entry.getValue());
            }
        }
        return changes;
    }
}
