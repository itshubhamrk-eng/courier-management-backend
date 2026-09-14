package com.courier.modules.company.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DepartmentRoleRepository extends JpaRepository<DepartmentRole, UUID> {

    List<DepartmentRole> findAllByDepartmentIdOrderByRoleCodeAsc(UUID departmentId);

    @Query("select dr from DepartmentRole dr where dr.departmentId in :departmentIds")
    List<DepartmentRole> findAllByDepartmentIdIn(@Param("departmentIds") Collection<UUID> departmentIds);

    boolean existsByRoleId(UUID roleId);
}
