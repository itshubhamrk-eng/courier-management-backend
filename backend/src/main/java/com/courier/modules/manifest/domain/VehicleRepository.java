package com.courier.modules.manifest.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    @Query("select v from Vehicle v where v.id = :id and v.companyId = :companyId")
    Optional<Vehicle> findByIdWithinCompany(@Param("id") UUID id, @Param("companyId") UUID companyId);

    boolean existsByCompanyIdAndVehicleNumber(UUID companyId, String vehicleNumber);

    List<Vehicle> findAllByCompanyIdAndActiveTrueOrderByVehicleNumberAsc(UUID companyId);

    List<Vehicle> findAllByCompanyIdOrderByVehicleNumberAsc(UUID companyId);
}
