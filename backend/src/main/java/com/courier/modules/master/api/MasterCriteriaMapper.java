package com.courier.modules.master.api;

import com.courier.modules.master.api.dto.MasterSearchRequest;
import com.courier.modules.master.domain.MasterDataCriteria;
import org.springframework.stereotype.Component;

/**
 * Turns the shared query parameters into search criteria.
 *
 * <p>One bean for twelve endpoints. A controller with its own filters chains them on with
 * {@link MasterDataCriteria#with}, passing the entity's JPA attribute name as a literal —
 * which is what keeps the generic {@code root.get(name)} in the specification safe.
 */
@Component
public class MasterCriteriaMapper {

    public MasterDataCriteria toCriteria(MasterSearchRequest request) {
        MasterSearchRequest safe = request == null ? MasterSearchRequest.empty() : request;
        return MasterDataCriteria.of(safe.companyId(), safe.status(), safe.search());
    }
}
