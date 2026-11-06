package com.courier.modules.printlog.domain;

import java.util.Optional;
import java.util.UUID;

/** Display name of the printing user; {@code modules/company} supplies the adapter. */
public interface PrintLogUserPort {
    Optional<String> fullName(UUID userId, UUID companyId);
}
