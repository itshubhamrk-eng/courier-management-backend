package com.courier.modules.communication.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommunicationSettingRepository extends JpaRepository<CommunicationSetting, UUID> {

    Optional<CommunicationSetting> findByCompanyIdAndChannel(UUID companyId, CommunicationChannel channel);

    List<CommunicationSetting> findAllByCompanyId(UUID companyId);

    long countByCompanyId(UUID companyId);

    boolean existsByCompanyIdAndEnabledTrue(UUID companyId);

    /** Cross-tenant on purpose (scheduler thread, no CompanyContext bound) — see
     *  {@code CommunicationLogRepository.findDueForDispatch}. */
    List<CommunicationSetting> findAllByChannelAndEnabledTrue(CommunicationChannel channel);
}
