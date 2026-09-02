package com.courier.modules.master.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Every Area a pincode's postal record names. See {@link PincodeArea}. */
public interface PincodeAreaRepository extends JpaRepository<PincodeArea, UUID> {

    List<PincodeArea> findByCompanyIdAndPincodeIdOrderByPrimaryDescCreatedAtAsc(
            UUID companyId, UUID pincodeId);

    Optional<PincodeArea> findByCompanyIdAndPincodeIdAndAreaId(
            UUID companyId, UUID pincodeId, UUID areaId);

    Optional<PincodeArea> findByIdAndCompanyIdAndPincodeId(UUID id, UUID companyId, UUID pincodeId);
}
