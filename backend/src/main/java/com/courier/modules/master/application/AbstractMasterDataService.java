package com.courier.modules.master.application;

import com.courier.modules.master.domain.GlobalMasters;
import com.courier.modules.master.domain.MasterDataCriteria;
import com.courier.modules.master.domain.MasterDataEntity;
import com.courier.modules.master.domain.MasterDataRepository;
import com.courier.modules.master.domain.MasterDataSpecifications;
import com.courier.modules.master.infrastructure.MasterUniquenessChecker;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.DuplicateResourceException;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.security.AuthenticatedUser;
import com.courier.shared.security.SecurityUtils;
import com.courier.shared.company.CompanyContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Everything the twelve master lists do identically.
 *
 * <p>Read, search, activate, deactivate and soft delete are <i>fully</i> generic and live
 * here. Create and update are templates: this class owns the order of operations —
 * normalise, check the code, run the list's own rules, save, audit — and each concrete
 * service supplies only the field copying and the rules that are its own.
 *
 * <p><b>Why the public methods are not here.</b> Every one of the twelve services
 * re-declares its endpoints as one-line delegates carrying their own
 * {@code @PreAuthorize}. That looks like boilerplate and is deliberate: a class-level
 * annotation on twelve subclasses could not express "reads are for any company user,
 * writes are for COMPANY_ADMIN", and an annotation on an inherited method is resolved
 * against the target class in a way that depends on the proxy — which is exactly the
 * fragility decision 16 in {@code MEMORY/AI_CONTEXT.md} was written to avoid. A security
 * rule that is visible next to the method it guards is worth twelve lines each.
 *
 * <p>Company isolation is the project's two layers: the Hibernate filter, plus
 * {@code findByIdWithinCompany} on every single-row load, because a primary-key load is not
 * filtered. A foreign id is a 404, never someone else's row.
 */
@Slf4j
public abstract class AbstractMasterDataService<E extends MasterDataEntity> {

    protected final MasterDataRepository<E> repository;
    protected final MasterUniquenessChecker uniqueness;
    protected final AuditService auditService;

    /** Human name used in errors and audit rows, e.g. {@code "Country"}. */
    protected final String entityName;

    /** Physical table, for the soft-delete-aware uniqueness check only. */
    protected final String tableName;

    protected AbstractMasterDataService(MasterDataRepository<E> repository,
                                        MasterUniquenessChecker uniqueness,
                                        AuditService auditService,
                                        String entityName,
                                        String tableName) {
        this.repository = repository;
        this.uniqueness = uniqueness;
        this.auditService = auditService;
        this.entityName = entityName;
        this.tableName = tableName;
    }

    // ------------------------------------------------------------------ template: create

    /**
     * Saves a newly built row. The subclass has already copied the command onto
     * {@code entity}; everything that must happen to <i>every</i> master row happens here.
     */
    protected E createEntity(E entity) {
        return withOwner(() -> doCreate(entity));
    }

    private E doCreate(E entity) {
        UUID companyId = requireCompany();

        entity.applyInvariants();
        requireCodeAvailable(companyId, entity.getCode(), null);
        validateBeforeSave(entity, companyId, null);

        // Flushed now, inside whatever owner binding this call is running under: a global
        // list's write runs inside withOwner's platform binding, and a deferred flush at
        // the transaction's commit boundary — after that binding has already unwound back
        // to the caller's own company — is what CompanyEntityListener's @PrePersist check
        // would see, not what it should. Flushing here is what INSERT already did for the
        // Hibernate session implicitly; UPDATE below does not, so it is made explicit for
        // both rather than relying on one to fail loud and the other to fail quiet.
        E saved = repository.saveAndFlush(entity);

        log.info("{} {} ({}) created in company {} by {}",
                entityName, saved.getCode(), saved.getId(), companyId, currentActor());
        auditService.record(AuditAction.MASTER_DATA_CREATED, entityName, saved.getId(),
                Map.of("code", saved.getCode(), "name", saved.getName()));
        return saved;
    }

    /**
     * Loads, version-checks, mutates and saves. The mutation is a callback rather than a
     * pre-built entity so that the row being written is always the managed one — building
     * a detached copy and saving it would silently reset {@code createdAt} and the
     * company stamp.
     */
    protected E updateEntity(UUID id, Long expectedVersion, Consumer<E> mutation) {
        return withOwner(() -> doUpdate(id, expectedVersion, mutation));
    }

    private E doUpdate(UUID id, Long expectedVersion, Consumer<E> mutation) {
        UUID companyId = requireCompany();
        E entity = loadOrThrow(id, companyId);
        requireCurrentVersion(entity, expectedVersion);

        Map<String, Object> before = snapshot(entity);
        mutation.accept(entity);
        entity.applyInvariants();
        validateBeforeSave(entity, companyId, id);

        E saved = repository.saveAndFlush(entity);

        Map<String, Object> changes = changeDetails(before, snapshot(saved));
        log.info("{} {} updated in company {} by {} ({} field(s))",
                entityName, saved.getCode(), companyId, currentActor(), changes.size());
        auditService.record(AuditAction.MASTER_DATA_UPDATED, entityName, saved.getId(), changes);
        return saved;
    }

    // -------------------------------------------------------------------------- generic

    protected E doGetById(UUID id) {
        if (global()) {
            // One catalogue, one owner — the caller's own company is irrelevant here,
            // and a super admin reading it sees exactly what a booking clerk does.
            return CompanyContext.runAs(GlobalMasters.PLATFORM_COMPANY_ID,
                    () -> loadOrThrow(id, GlobalMasters.PLATFORM_COMPANY_ID));
        }
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        // A super admin with no company bound is investigating across companies; every
        // other caller is confined to their own by the company-scoped load below.
        if (caller.isSuperAdmin() && CompanyContext.getCompanyId().isEmpty()) {
            return repository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException(entityName, id));
        }
        return loadOrThrow(id, CompanyContext.requireCompanyId());
    }

    protected Page<E> doSearch(MasterDataCriteria criteria, Pageable pageable) {
        MasterDataCriteria safe = criteria == null ? MasterDataCriteria.none() : criteria;

        if (global()) {
            MasterDataCriteria pinned = safe.withCompanyId(GlobalMasters.PLATFORM_COMPANY_ID);
            return CompanyContext.runAs(GlobalMasters.PLATFORM_COMPANY_ID,
                    () -> repository.findAll(MasterDataSpecifications.matching(pinned), pageable));
        }

        // A companyId in the query string is overridden, never honoured — decision 27.
        MasterDataCriteria effective =
                CompanyContext.getCompanyId().map(safe::withCompanyId).orElse(safe);
        return repository.findAll(MasterDataSpecifications.matching(effective), pageable);
    }

    protected E doActivate(UUID id) {
        return withOwner(() -> activateOwned(id));
    }

    private E activateOwned(UUID id) {
        UUID companyId = requireCompany();
        E entity = loadOrThrow(id, companyId);
        if (entity.isActive()) {
            return entity;
        }
        requireActivatable(entity, companyId);
        entity.activate();
        E saved = repository.saveAndFlush(entity);
        auditService.record(AuditAction.MASTER_DATA_ACTIVATED, entityName, saved.getId(),
                Map.of("code", saved.getCode()));
        return saved;
    }

    protected E doDeactivate(UUID id) {
        return withOwner(() -> deactivateOwned(id));
    }

    private E deactivateOwned(UUID id) {
        UUID companyId = requireCompany();
        E entity = loadOrThrow(id, companyId);
        if (!entity.isActive()) {
            return entity;
        }
        entity.deactivate();
        E saved = repository.saveAndFlush(entity);
        auditService.record(AuditAction.MASTER_DATA_DEACTIVATED, entityName, saved.getId(),
                Map.of("code", saved.getCode()));
        return saved;
    }

    protected void doDelete(UUID id) {
        withOwner(() -> {
            deleteOwned(id);
            return null;
        });
    }

    private void deleteOwned(UUID id) {
        UUID companyId = requireCompany();
        E entity = loadOrThrow(id, companyId);
        requireDeletable(entity, companyId);

        // Soft delete only, and deactivated alongside so a restored row is not silently
        // back in every picker. The code and name stay reserved: the unique keys do not
        // mention `deleted`, which is what stops a new row inheriting the meaning of an
        // old one that historical shipments still quote.
        entity.deactivate();
        entity.softDelete(SecurityUtils.getCurrentUserId().orElse(null));
        repository.saveAndFlush(entity);

        log.warn("{} {} ({}) soft deleted in company {} by {}",
                entityName, entity.getCode(), entity.getId(), companyId, currentActor());
        auditService.record(AuditAction.MASTER_DATA_DELETED, entityName, entity.getId(),
                Map.of("code", entity.getCode(), "name", entity.getName()));
    }

    // ----------------------------------------------------------------------------- hooks

    /**
     * The list's own rules — parent must exist and be active, name unique within parent,
     * slabs must not overlap. Runs after {@code applyInvariants} on both create and update.
     *
     * @param excludeId the row being updated, or null on create
     */
    protected void validateBeforeSave(E entity, UUID companyId, UUID excludeId) {
    }

    /** Refuse activation, e.g. a child whose parent is inactive. Default: allow. */
    protected void requireActivatable(E entity, UUID companyId) {
    }

    /** Refuse deletion, e.g. a parent that still has children. Default: allow. */
    protected void requireDeletable(E entity, UUID companyId) {
    }

    /**
     * Fields worth recording a before/after for. Subclasses override to add their own and
     * must call {@code super.snapshot(entity)} to keep the shared head.
     */
    protected Map<String, Object> snapshot(E entity) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", entity.getName());
        values.put("description", entity.getDescription());
        values.put("status", entity.getStatus() == null ? null : entity.getStatus().name());
        values.put("displayOrder", entity.getDisplayOrder());
        return values;
    }

    // --------------------------------------------------------------------------- helpers

    /** Copies the head every master row shares. {@code code} is ignored when null (update). */
    protected void applyCommonFields(E entity, String code, String name, String description,
                                     Integer displayOrder) {
        if (code != null) {
            entity.setCode(MasterDataEntity.normaliseCode(code));
        }
        entity.setName(name);
        entity.setDescription(description);
        entity.setDisplayOrder(displayOrder == null ? 0 : displayOrder);
    }

    protected E loadOrThrow(UUID id, UUID companyId) {
        return repository.findByIdWithinCompany(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(entityName, id));
    }

    protected void requireCodeAvailable(UUID companyId, String code, UUID excludeId) {
        if (uniqueness.isCodeTaken(tableName, companyId, excludeId, code)) {
            throw new DuplicateResourceException(entityName, "code", code);
        }
    }

    /**
     * Refuses a duplicate on some other combination — typically a name within its parent.
     *
     * @param columns physical column names to values; the keys are constants in this
     *                module, never anything a request supplied
     */
    protected void requireAvailable(UUID companyId, UUID excludeId, Map<String, Object> columns,
                                    String field, String value) {
        if (uniqueness.isTaken(tableName, companyId, excludeId, columns)) {
            throw new DuplicateResourceException(entityName, field, value);
        }
    }

    /**
     * Whether this list is one of the global ones — the geography hierarchy, shared by
     * every company and written only by a super admin. Company-owned catalogues leave
     * this false.
     *
     * <p>It is a method rather than a constructor flag so a subclass declares it next to
     * the {@code @PreAuthorize} that goes with it: a list that answers true here must
     * also require {@code SUPER_ADMIN} to write, and having both in one file is what
     * stops the two drifting apart.
     */
    protected boolean global() {
        return false;
    }

    /**
     * Runs an action bound to the row's owner: the platform for a global list, whatever
     * is already bound for a company-owned one.
     *
     * <p>Binding matters on the write path as much as the read path —
     * {@code CompanyEntityListener} stamps the owner from {@code CompanyContext} on
     * persist, so a global row created under a super admin's own binding would silently
     * belong to their home company and be invisible to everyone else.
     */
    protected final <T> T withOwner(java.util.function.Supplier<T> action) {
        return global()
                ? CompanyContext.runAs(GlobalMasters.PLATFORM_COMPANY_ID, action)
                : action.get();
    }

    protected UUID requireCompany() {
        if (global()) {
            return GlobalMasters.PLATFORM_COMPANY_ID;
        }
        return CompanyContext.getCompanyId().orElseThrow(() -> new BusinessRuleException(
                "No company is bound to this request. Master data belongs to a company, so "
                        + "this operation must be performed by a user of that company."));
    }

    /**
     * A stale version is a 409. Null is accepted rather than rejected so that the
     * activate/deactivate endpoints, which carry no body, are not forced to invent one.
     */
    protected void requireCurrentVersion(E entity, Long expectedVersion) {
        if (expectedVersion == null) {
            return;
        }
        if (!Objects.equals(entity.getVersion(), expectedVersion)) {
            throw new ObjectOptimisticLockingFailureException(entity.getClass(), entity.getId());
        }
    }

    protected Map<String, Object> changeDetails(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> changes = new LinkedHashMap<>();
        before.forEach((field, oldValue) -> {
            Object newValue = after.get(field);
            if (!Objects.equals(oldValue, newValue)) {
                Map<String, Object> pair = new LinkedHashMap<>();
                pair.put("from", String.valueOf(oldValue));
                pair.put("to", String.valueOf(newValue));
                changes.put(field, pair);
            }
        });
        return changes;
    }

    protected String currentActor() {
        return SecurityUtils.getCurrentUser()
                .map(user -> user.email() == null ? user.userId().toString() : user.email())
                .orElse("system");
    }
}
