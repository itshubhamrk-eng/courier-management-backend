package com.courier.modules.master.api;

import com.courier.modules.master.api.dto.CreatePackageTypeRequest;
import com.courier.modules.master.api.dto.PackageTypeResponse;
import com.courier.modules.master.api.dto.UpdatePackageTypeRequest;
import com.courier.modules.master.application.command.PackageTypeCommand;
import com.courier.modules.master.domain.PackageType;
import com.courier.shared.api.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/** Wire contract to application/domain types for package types. */
@Component
public class PackageTypeMasterMapper {

    public PackageTypeCommand toCommand(CreatePackageTypeRequest r) {
        return new PackageTypeCommand(r.code(), r.name(), r.description(), r.displayOrder(),
                r.documentType(), r.fragileByDefault(), r.maxWeightKg(),
                r.defaultLengthCm(), r.defaultWidthCm(), r.defaultHeightCm(), null);
    }

    public PackageTypeCommand toCommand(UpdatePackageTypeRequest r) {
        return new PackageTypeCommand(null, r.name(), r.description(), r.displayOrder(),
                r.documentType(), r.fragileByDefault(), r.maxWeightKg(),
                r.defaultLengthCm(), r.defaultWidthCm(), r.defaultHeightCm(), r.version());
    }

    public PackageTypeResponse toResponse(PackageType p) {
        return new PackageTypeResponse(p.getId(), p.getCompanyId(), p.getCode(), p.getName(),
                p.getDescription(), p.getStatus(), p.getDisplayOrder(),
                p.isDocumentType(), p.isFragileByDefault(), p.getMaxWeightKg(),
                p.getDefaultLengthCm(), p.getDefaultWidthCm(), p.getDefaultHeightCm(),
                p.getCreatedBy(), p.getCreatedAt(), p.getUpdatedBy(), p.getUpdatedAt(), p.getVersion());
    }

    public PageResponse<PackageTypeResponse> toPage(Page<PackageType> page) {
        return PageResponse.from(page, this::toResponse);
    }
}
