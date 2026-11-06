package com.courier.modules.printlog.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PrintLogRepository extends JpaRepository<PrintLog, UUID> {

    @Query("select p from PrintLog p where p.companyId = :companyId and p.docType = :type and p.docRef = :ref "
            + "order by p.printedAt desc")
    List<PrintLog> history(@Param("companyId") UUID companyId, @Param("type") PrintDocType type,
                           @Param("ref") String ref);

    @Query("select p.docRef, count(p) from PrintLog p where p.companyId = :companyId and p.docType = :type "
            + "and p.docRef in :refs group by p.docRef")
    List<Object[]> counts(@Param("companyId") UUID companyId, @Param("type") PrintDocType type,
                          @Param("refs") Collection<String> refs);
}
