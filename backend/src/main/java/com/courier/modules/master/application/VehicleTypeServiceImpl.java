package com.courier.modules.master.application;

import com.courier.modules.master.application.command.VehicleTypeCommand;
import com.courier.modules.master.domain.MasterDataCriteria;
import com.courier.modules.master.domain.VehicleType;
import com.courier.modules.master.domain.VehicleTypeRepository;
import com.courier.modules.master.infrastructure.MasterTable;
import com.courier.modules.master.infrastructure.MasterUniquenessChecker;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.security.Roles;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/** Vehicle types. A flat catalogue: no parent, so no hierarchy rules. */
@Slf4j
@Service
public class VehicleTypeServiceImpl extends AbstractMasterDataService<VehicleType>
        implements VehicleTypeService {

    private static final String WRITE = "hasRole('" + Roles.COMPANY_ADMIN + "')";
    private static final String READ = "isAuthenticated()";

    public VehicleTypeServiceImpl(VehicleTypeRepository vehicleTypes,
                                  MasterUniquenessChecker uniqueness,
                                  AuditService auditService) {
        super(vehicleTypes, uniqueness, auditService, "Vehicle type", MasterTable.VEHICLE_TYPES);
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public VehicleType create(VehicleTypeCommand command) {
        VehicleType vehicleType = new VehicleType();
        applyCommonFields(vehicleType, command.code(), command.name(), command.description(),
                command.displayOrder());
        applySpecific(vehicleType, command);
        return createEntity(vehicleType);
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public VehicleType update(UUID id, VehicleTypeCommand command) {
        return updateEntity(id, command.expectedVersion(), vehicleType -> {
            applyCommonFields(vehicleType, null, command.name(), command.description(),
                    command.displayOrder());
            applySpecific(vehicleType, command);
        });
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READ)
    public VehicleType getById(UUID id) {
        return doGetById(id);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READ)
    public Page<VehicleType> search(MasterDataCriteria criteria, Pageable pageable) {
        return doSearch(criteria, pageable);
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public void delete(UUID id) {
        doDelete(id);
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public VehicleType activate(UUID id) {
        return doActivate(id);
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public VehicleType deactivate(UUID id) {
        return doDeactivate(id);
    }

    // ---------------------------------------------------------------------------- rules

    @Override
    protected void validateBeforeSave(VehicleType vehicleType, UUID companyId, UUID excludeId) {
        requireAvailable(companyId, excludeId, Map.of("name", vehicleType.getName()),
                "name", vehicleType.getName());
    }

    @Override
    protected Map<String, Object> snapshot(VehicleType vehicleType) {
        Map<String, Object> values = super.snapshot(vehicleType);
        values.put("capacityKg", String.valueOf(vehicleType.getCapacityKg()));
        values.put("capacityCft", String.valueOf(vehicleType.getCapacityCft()));
        values.put("wheelCount", vehicleType.getWheelCount());
        values.put("requiresPermit", vehicleType.isRequiresPermit());
        return values;
    }

    private void applySpecific(VehicleType vehicleType, VehicleTypeCommand command) {
        vehicleType.setCapacityKg(command.capacityKg());
        vehicleType.setCapacityCft(command.capacityCft());
        vehicleType.setWheelCount(command.wheelCount());
        vehicleType.setRequiresPermit(Boolean.TRUE.equals(command.requiresPermit()));
    }
}
