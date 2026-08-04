package com.courier.modules.master.application;

import com.courier.modules.master.application.command.PackageTypeCommand;
import com.courier.modules.master.domain.MasterDataCriteria;
import com.courier.modules.master.domain.PackageType;
import com.courier.modules.master.domain.PackageTypeRepository;
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

/** Package types. */
@Slf4j
@Service
public class PackageTypeServiceImpl extends AbstractMasterDataService<PackageType>
        implements PackageTypeService {

    private static final String WRITE = "hasRole('" + Roles.COMPANY_ADMIN + "')";
    private static final String READ = "isAuthenticated()";

    public PackageTypeServiceImpl(PackageTypeRepository packageTypes,
                                  MasterUniquenessChecker uniqueness,
                                  AuditService auditService) {
        super(packageTypes, uniqueness, auditService, "Package type", MasterTable.PACKAGE_TYPES);
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public PackageType create(PackageTypeCommand command) {
        PackageType packageType = new PackageType();
        applyCommonFields(packageType, command.code(), command.name(), command.description(),
                command.displayOrder());
        applySpecific(packageType, command);
        return createEntity(packageType);
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public PackageType update(UUID id, PackageTypeCommand command) {
        return updateEntity(id, command.expectedVersion(), packageType -> {
            applyCommonFields(packageType, null, command.name(), command.description(),
                    command.displayOrder());
            applySpecific(packageType, command);
        });
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READ)
    public PackageType getById(UUID id) {
        return doGetById(id);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READ)
    public Page<PackageType> search(MasterDataCriteria criteria, Pageable pageable) {
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
    public PackageType activate(UUID id) {
        return doActivate(id);
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public PackageType deactivate(UUID id) {
        return doDeactivate(id);
    }

    // ---------------------------------------------------------------------------- rules

    @Override
    protected void validateBeforeSave(PackageType packageType, UUID companyId, UUID excludeId) {
        requireAvailable(companyId, excludeId, Map.of("name", packageType.getName()),
                "name", packageType.getName());
    }

    @Override
    protected Map<String, Object> snapshot(PackageType packageType) {
        Map<String, Object> values = super.snapshot(packageType);
        values.put("documentType", packageType.isDocumentType());
        values.put("fragileByDefault", packageType.isFragileByDefault());
        values.put("maxWeightKg", String.valueOf(packageType.getMaxWeightKg()));
        values.put("defaultLengthCm", String.valueOf(packageType.getDefaultLengthCm()));
        values.put("defaultWidthCm", String.valueOf(packageType.getDefaultWidthCm()));
        values.put("defaultHeightCm", String.valueOf(packageType.getDefaultHeightCm()));
        return values;
    }

    private void applySpecific(PackageType packageType, PackageTypeCommand command) {
        packageType.setDocumentType(Boolean.TRUE.equals(command.documentType()));
        packageType.setFragileByDefault(Boolean.TRUE.equals(command.fragileByDefault()));
        packageType.setMaxWeightKg(command.maxWeightKg());
        packageType.setDefaultLengthCm(command.defaultLengthCm());
        packageType.setDefaultWidthCm(command.defaultWidthCm());
        packageType.setDefaultHeightCm(command.defaultHeightCm());
    }
}
