package com.courier.modules.master.domain;

import java.util.List;
import java.util.UUID;

/**
 * States.
 *
 * <p>{@code countByCompanyIdAndCountryId} is what stops a country being deleted out from
 * under its states. It counts live rows only — {@code @SQLRestriction} hides soft-deleted
 * ones — which is the right reading: a deleted state is not a dependant.
 */
public interface StateRepository extends MasterDataRepository<State> {

    long countByCompanyIdAndCountryId(UUID companyId, UUID countryId);

    List<State> findByCompanyIdAndCountryIdOrderByDisplayOrderAscNameAsc(UUID companyId, UUID countryId);
}
