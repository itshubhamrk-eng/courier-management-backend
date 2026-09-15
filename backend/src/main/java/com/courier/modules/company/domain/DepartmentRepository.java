package com.courier.modules.company.domain;

import com.courier.shared.domain.TimeOrderedUuid;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Departments within a company. Same isolation shape as {@link CompanyRoleRepository}:
 * the Hibernate filter narrows every derived query to the bound company, and single-row
 * loads still go through {@link #findByIdWithinCompany} because a primary-key load
 * bypasses that filter entirely.
 */
public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    List<Department> findAllByOrderByDepartmentNameAsc();

    List<Department> findAllByStatusOrderByDepartmentNameAsc(DepartmentStatus status);

    @Query("select d from Department d where d.id = :id and d.companyId = :companyId")
    Optional<Department> findByIdWithinCompany(@Param("id") UUID id,
                                               @Param("companyId") UUID companyId);

    long countByCompanyId(UUID companyId);

    /**
     * Is this code taken within the company, counting soft-deleted rows? Native and
     * byte-based for the same three reasons {@code CompanyRoleRepository} documents: the
     * unique key does not know about {@code deleted}, native SQL needs raw bytes for a
     * {@code BINARY(16)} column, and MySQL's {@code COUNT(*)} is a {@code BIGINT}.
     */
    default boolean isDepartmentCodeTaken(UUID companyId, String departmentCode, UUID excludeId) {
        return countByDepartmentCodeIncludingDeleted(TimeOrderedUuid.toBytes(companyId), departmentCode,
                TimeOrderedUuid.toBytes(excludeId)) > 0;
    }

    default boolean isDepartmentNameTaken(UUID companyId, String departmentName, UUID excludeId) {
        return countByDepartmentNameIncludingDeleted(TimeOrderedUuid.toBytes(companyId), departmentName,
                TimeOrderedUuid.toBytes(excludeId)) > 0;
    }

    @Query(value = """
            SELECT COUNT(*) FROM departments
            WHERE company_id = :companyId
              AND department_code = :departmentCode
              AND (:excludeId IS NULL OR id <> :excludeId)
            """, nativeQuery = true)
    long countByDepartmentCodeIncludingDeleted(@Param("companyId") byte[] companyId,
                                               @Param("departmentCode") String departmentCode,
                                               @Param("excludeId") byte[] excludeId);

    @Query(value = """
            SELECT COUNT(*) FROM departments
            WHERE company_id = :companyId
              AND LOWER(department_name) = LOWER(:departmentName)
              AND (:excludeId IS NULL OR id <> :excludeId)
            """, nativeQuery = true)
    long countByDepartmentNameIncludingDeleted(@Param("companyId") byte[] companyId,
                                               @Param("departmentName") String departmentName,
                                               @Param("excludeId") byte[] excludeId);
}
