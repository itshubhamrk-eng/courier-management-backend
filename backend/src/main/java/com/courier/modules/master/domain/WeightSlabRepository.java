package com.courier.modules.master.domain;

import java.util.List;
import java.util.UUID;

/**
 * Weight slabs.
 *
 * <p>{@code findByCompanyIdAndWeightUnitAndStatus} backs the overlap rule. It loads the
 * whole active band set for one unit rather than asking the database for an intersection:
 * a company has tens of slabs, not thousands, and the comparison in
 * {@link WeightSlab#overlaps} is then the single definition of what overlapping means —
 * shared by the service and by its tests, with no SQL saying it a second way.
 */
public interface WeightSlabRepository extends MasterDataRepository<WeightSlab> {

    List<WeightSlab> findByCompanyIdAndWeightUnitAndStatus(UUID companyId, WeightUnit weightUnit,
                                                          MasterStatus status);
}
