package com.courier.modules.master.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * The reads every master list needs, declared once.
 *
 * <p>{@code #{#entityName}} is resolved per concrete repository by Spring Data, so a
 * single query definition serves all twelve tables without a native string anywhere.
 *
 * <p>{@link #findByIdWithinCompany} rather than {@code findById}: a primary-key load goes
 * through {@code EntityManager.find} and is <b>not</b> filtered by the Hibernate company
 * filter (ARCHITECTURE §3). Every single-row load in this module goes through it, so a
 * foreign id is a 404 rather than another company's row.
 *
 * <p>Uniqueness is <i>not</i> here. The unique keys do not know about {@code deleted},
 * so the check has to see soft-deleted rows, which {@code @SQLRestriction} hides from
 * every query on this interface. {@code MasterUniquenessChecker} does it natively.
 */
@NoRepositoryBean
public interface MasterDataRepository<E extends MasterDataEntity>
        extends JpaRepository<E, UUID>, JpaSpecificationExecutor<E> {

    @Query("select e from #{#entityName} e where e.id = :id and e.companyId = :companyId")
    Optional<E> findByIdWithinCompany(@Param("id") UUID id, @Param("companyId") UUID companyId);

    @Query("select e from #{#entityName} e where e.companyId = :companyId and upper(e.code) = upper(:code)")
    Optional<E> findByCodeWithinCompany(@Param("code") String code, @Param("companyId") UUID companyId);

    long countByCompanyId(UUID companyId);
}
