package com.courier.modules.master.application;

import com.courier.modules.master.domain.MasterDataCriteria;
import com.courier.modules.master.domain.MasterDataEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * The seven use cases every master list supports.
 *
 * <p><b>Audiences</b>, enforced by {@code @PreAuthorize} on each implementation:
 * <ul>
 *   <li>{@code COMPANY_ADMIN} — creates, updates, activates, deactivates and deletes the
 *       company's master data.</li>
 *   <li>Any authenticated company user — reads. A booking clerk needs the pincode and
 *       service-type lists to do their job; withholding them would only push every screen
 *       into hard-coding the values.</li>
 *   <li>{@code SUPER_ADMIN} — reads across companies while investigating.</li>
 * </ul>
 *
 * <p>There is no bulk import here. {@code MASTER_DATA_IMPORT} exists in the permission
 * catalogue because a pincode upload is obviously coming, but the endpoint does not, and
 * seeding a right is cheap while renaming one after customers hold it is not.
 *
 * @param <E> the row type
 * @param <C> the command that creates or updates it
 */
public interface MasterDataService<E extends MasterDataEntity, C> {

    E create(C command);

    /** Full replacement of the editable fields. The code is immutable and is not among them. */
    E update(UUID id, C command);

    E getById(UUID id);

    Page<E> search(MasterDataCriteria criteria, Pageable pageable);

    /** Soft delete. The row is retained and its code stays reserved. */
    void delete(UUID id);

    E activate(UUID id);

    E deactivate(UUID id);
}
