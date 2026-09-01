package com.courier.modules.company.application;

import com.courier.modules.company.application.command.AssignSubscriptionCommand;
import com.courier.modules.company.application.command.CreateCompanyCommand;
import com.courier.modules.company.application.command.RenewSubscriptionCommand;
import com.courier.modules.company.application.command.UpdateCompanyCommand;
import com.courier.modules.company.application.event.CompanyEvent;
import com.courier.modules.company.domain.Company;
import com.courier.modules.company.domain.CompanyCriteria;
import com.courier.modules.company.domain.CompanyRepository;
import com.courier.modules.company.domain.CompanyRole;
import com.courier.modules.company.domain.CompanyRoleRepository;
import com.courier.modules.company.domain.CompanySetting;
import com.courier.modules.company.domain.CompanySettingRepository;
import com.courier.modules.company.domain.CompanySpecifications;
import com.courier.modules.company.domain.CompanyStatus;
import com.courier.modules.shipment.application.storage.FileStoragePort;
import com.courier.modules.subscription.application.SubscriptionPlanService;
import com.courier.modules.subscription.domain.BillingCycle;
import com.courier.modules.subscription.domain.SubscriptionPlan;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.DuplicateResourceException;
import com.courier.shared.exception.ErrorCode;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.security.Roles;
import com.courier.shared.security.SecurityUtils;
import com.courier.shared.company.CompanyContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Company use cases.
 *
 * <p>The class-level {@code @PreAuthorize} is the authoritative access control: every
 * method requires {@code SUPER_ADMIN}, reads included — a company row carries another
 * business's contact details, tax numbers and commercial terms. {@code SecurityConfig}'s
 * URL rule is a deliberate second, coarser gate.
 *
 * <p>The role string is folded from {@link Roles#SUPER_ADMIN} at compile time, so a
 * rename of the constant cannot leave a stale literal inside a SpEL string the compiler
 * never checks.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('" + Roles.SUPER_ADMIN + "')")
public class CompanyServiceImpl implements CompanyService {

    private static final String ENTITY = "Company";

    private final CompanyRepository repository;
    private final CompanyRoleRepository roleRepository;
    private final CompanySettingRepository settingRepository;
    private final CompanyProvisioningService provisioningService;
    private final SubscriptionPlanService subscriptionPlanService;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final FileStoragePort fileStoragePort;

    @Override
    @Transactional
    public CreatedCompany create(CreateCompanyCommand command) {
        SubscriptionPlan plan = requirePlan(command.subscriptionPlanId());

        String companyCode = Company.normaliseCode(command.companyCode());
        String email = Company.normaliseEmail(command.email());
        String gst = Company.normaliseTaxId(command.gstNumber());
        String pan = Company.normaliseTaxId(command.panNumber());

        requireUnique(companyCode, email, gst, pan, null);

        LocalDate today = LocalDate.now();
        boolean onTrial = plan.getTrialDays() != null && plan.getTrialDays() > 0;

        Company company = Company.builder()
                .companyId(generateCompanyId())
                .companyCode(companyCode)
                .companyName(command.companyName())
                .legalName(command.legalName())
                .displayName(command.displayName())
                .subscriptionPlanId(plan.getId())
                // The plan decides whether this company starts in TRIAL: a plan with no
                // trial days would otherwise leave a trial window that never opened.
                .status(onTrial ? CompanyStatus.TRIAL : CompanyStatus.ACTIVE)
                .trialStartDate(onTrial ? today : null)
                .trialEndDate(onTrial ? today.plusDays(plan.getTrialDays()) : null)
                .subscriptionStartDate(command.subscriptionStartDate() != null
                        ? command.subscriptionStartDate()
                        : (onTrial ? today.plusDays(plan.getTrialDays()) : today))
                .email(email)
                .mobile(command.mobile())
                .alternateMobile(command.alternateMobile())
                .website(command.website())
                .gstNumber(gst)
                .panNumber(pan)
                .cinNumber(Company.normaliseTaxId(command.cinNumber()))
                .logo(command.logo())
                .favicon(command.favicon())
                .addressLine1(command.addressLine1())
                .addressLine2(command.addressLine2())
                .country(command.country())
                .state(command.state())
                .city(command.city())
                .postalCode(command.postalCode())
                .timezone(orDefault(command.timezone(), Company.DEFAULT_TIMEZONE))
                .currency(orDefault(command.currency(), plan.getCurrency()))
                .language(orDefault(command.language(), Company.DEFAULT_LANGUAGE))
                .dateFormat(orDefault(command.dateFormat(), Company.DEFAULT_DATE_FORMAT))
                .timeFormat(orDefault(command.timeFormat(), Company.DEFAULT_TIME_FORMAT))
                .remarks(command.remarks())
                .build();

        company.applyInvariants();
        Company saved = repository.save(company);

        // Roles, permissions, settings and the first administrator, in one transaction
        // with the company: a half-provisioned company is worse than none.
        CompanyProvisioningService.ProvisioningResult provisioned = provisioningService.provision(
                saved,
                plan,
                orDefault(Company.normaliseEmail(command.adminEmail()), saved.getEmail()),
                command.adminFirstName(),
                command.adminLastName(),
                orDefault(command.adminMobile(), saved.getMobile()));

        log.info("Company {} ({}) created on plan {} by {}",
                saved.getCompanyCode(), saved.getCompanyId(), plan.getPlanCode(), currentActor());

        auditService.record(AuditAction.COMPANY_CREATED, ENTITY, saved.getId(),
                Map.of("companyCode", saved.getCompanyCode(),
                        "companyId", saved.getCompanyId().toString(),
                        "planCode", plan.getPlanCode(),
                        "status", saved.getStatus().name(),
                        "adminUserId", provisioned.admin().userId().toString()));

        eventPublisher.publishEvent(new CompanyEvent.CompanyCreated(
                saved.getId(), saved.getCompanyId(), saved.getCompanyCode(),
                plan.getId(), provisioned.admin().userId(), saved.getStatus(), Instant.now()));

        return new CreatedCompany(saved,
                provisioned.admin().userId(),
                provisioned.admin().email(),
                provisioned.admin().temporaryPassword(),
                provisioned.admin().activationEmailSent(),
                provisioned.roleCount(),
                provisioned.settingCount());
    }

    @Override
    @Transactional
    public Company update(UUID id, UpdateCompanyCommand command) {
        Company company = loadOrThrow(id);
        requireCurrentVersion(company, command.expectedVersion());

        SubscriptionPlan plan = requirePlan(command.subscriptionPlanId());

        String email = Company.normaliseEmail(command.email());
        String gst = Company.normaliseTaxId(command.gstNumber());
        String pan = Company.normaliseTaxId(command.panNumber());

        // The code is immutable, so only the other three are re-checked, excluding
        // this row so a company does not collide with itself.
        requireUnique(null, email, gst, pan, id);

        Map<String, Object> before = snapshot(company);

        company.setCompanyName(command.companyName());
        company.setLegalName(command.legalName());
        company.setDisplayName(command.displayName());
        company.setSubscriptionPlanId(plan.getId());
        company.setEmail(email);
        company.setMobile(command.mobile());
        company.setAlternateMobile(command.alternateMobile());
        company.setWebsite(command.website());
        company.setGstNumber(gst);
        company.setPanNumber(pan);
        company.setCinNumber(Company.normaliseTaxId(command.cinNumber()));
        company.setLogo(command.logo());
        company.setFavicon(command.favicon());
        company.setAddressLine1(command.addressLine1());
        company.setAddressLine2(command.addressLine2());
        company.setCountry(command.country());
        company.setState(command.state());
        company.setCity(command.city());
        company.setPostalCode(command.postalCode());
        company.setTimezone(orDefault(command.timezone(), Company.DEFAULT_TIMEZONE));
        company.setCurrency(orDefault(command.currency(), Company.DEFAULT_CURRENCY));
        company.setLanguage(orDefault(command.language(), Company.DEFAULT_LANGUAGE));
        company.setDateFormat(orDefault(command.dateFormat(), Company.DEFAULT_DATE_FORMAT));
        company.setTimeFormat(orDefault(command.timeFormat(), Company.DEFAULT_TIME_FORMAT));
        company.setRemarks(command.remarks());
        company.setTrialEndDate(command.trialEndDate());
        company.setSubscriptionStartDate(command.subscriptionStartDate());
        company.setSubscriptionEndDate(command.subscriptionEndDate());

        company.applyInvariants();
        Company saved = repository.save(company);

        Map<String, Object> changes = changeDetails(before, snapshot(saved));

        log.info("Company {} updated by {} ({} field(s) changed)",
                saved.getCompanyCode(), currentActor(), changes.size());
        auditService.record(AuditAction.COMPANY_UPDATED, ENTITY, saved.getId(), changes);

        eventPublisher.publishEvent(new CompanyEvent.CompanyUpdated(
                saved.getId(), saved.getCompanyId(), saved.getCompanyCode(), changes, Instant.now()));

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Company getById(UUID id) {
        return loadOrThrow(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Company getByCompanyId(UUID companyId) {
        return repository.findByCompanyId(companyId)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, companyId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Company> search(CompanyCriteria criteria, Pageable pageable) {
        return repository.findAll(CompanySpecifications.matching(criteria), pageable);
    }

    @Override
    @Transactional
    public Company activate(UUID id) {
        Company company = loadOrThrow(id);
        CompanyStatus previous = company.getStatus();
        if (previous == CompanyStatus.ACTIVE) {
            // Idempotent, and no audit noise for a no-op.
            return company;
        }

        company.transitionTo(CompanyStatus.ACTIVE);
        Company saved = repository.save(company);

        log.info("Company {} activated from {} by {}",
                saved.getCompanyCode(), previous, currentActor());
        auditService.record(AuditAction.COMPANY_ACTIVATED, ENTITY, saved.getId(),
                Map.of("companyCode", saved.getCompanyCode(), "previousStatus", previous.name()));

        eventPublisher.publishEvent(new CompanyEvent.CompanyActivated(
                saved.getId(), saved.getCompanyId(), saved.getCompanyCode(), previous, Instant.now()));

        return saved;
    }

    @Override
    @Transactional
    public Company suspend(UUID id, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("A suspension reason is required.");
        }

        Company company = loadOrThrow(id);
        CompanyStatus previous = company.getStatus();
        if (previous == CompanyStatus.SUSPENDED) {
            return company;
        }

        company.transitionTo(CompanyStatus.SUSPENDED);
        company.setRemarks(reason);
        Company saved = repository.save(company);

        // Suspension takes effect on the next authentication attempt: the company
        // directory reports the company inactive, and existing access tokens expire
        // within their 15-minute window.
        log.warn("Company {} suspended from {} by {}: {}",
                saved.getCompanyCode(), previous, currentActor(), reason);
        auditService.record(AuditAction.COMPANY_SUSPENDED, ENTITY, saved.getId(),
                Map.of("companyCode", saved.getCompanyCode(),
                        "previousStatus", previous.name(),
                        "reason", reason));

        eventPublisher.publishEvent(new CompanyEvent.CompanySuspended(
                saved.getId(), saved.getCompanyId(), saved.getCompanyCode(),
                previous, reason, Instant.now()));

        return saved;
    }

    @Override
    @Transactional
    public Company deactivate(UUID id, String reason) {
        Company company = loadOrThrow(id);
        CompanyStatus previous = company.getStatus();
        if (previous == CompanyStatus.INACTIVE) {
            return company;
        }

        company.transitionTo(CompanyStatus.INACTIVE);
        if (reason != null && !reason.isBlank()) {
            company.setRemarks(reason);
        }
        Company saved = repository.save(company);

        log.info("Company {} deactivated from {} by {}{}",
                saved.getCompanyCode(), previous, currentActor(),
                reason == null || reason.isBlank() ? "" : ": " + reason);
        auditService.record(AuditAction.COMPANY_DEACTIVATED, ENTITY, saved.getId(),
                Map.of("companyCode", saved.getCompanyCode(),
                        "previousStatus", previous.name(),
                        "reason", reason == null ? "" : reason));

        eventPublisher.publishEvent(new CompanyEvent.CompanyDeactivated(
                saved.getId(), saved.getCompanyId(), saved.getCompanyCode(),
                previous, reason, Instant.now()));

        return saved;
    }

    @Override
    @Transactional
    public Company expire(UUID id) {
        Company company = loadOrThrow(id);
        CompanyStatus previous = company.getStatus();
        if (previous == CompanyStatus.EXPIRED) {
            return company;
        }

        company.transitionTo(CompanyStatus.EXPIRED);
        if (company.getSubscriptionEndDate() == null) {
            company.setSubscriptionEndDate(LocalDate.now());
        }
        Company saved = repository.save(company);

        log.info("Company {} expired from {} by {}",
                saved.getCompanyCode(), previous, currentActor());
        auditService.record(AuditAction.COMPANY_EXPIRED, ENTITY, saved.getId(),
                Map.of("companyCode", saved.getCompanyCode(),
                        "previousStatus", previous.name(),
                        "outcome", "EXPIRED"));

        eventPublisher.publishEvent(new CompanyEvent.CompanyExpired(
                saved.getId(), saved.getCompanyId(), saved.getCompanyCode(), previous, Instant.now()));

        return saved;
    }

    private static final Set<String> BRANDING_KINDS = Set.of("LOGO", "FAVICON");
    private static final Set<String> BRANDING_EXTENSIONS =
            Set.of("png", "jpg", "jpeg", "svg", "webp", "ico");

    @Override
    public String uploadBranding(String kind, byte[] content, String filename, String contentType) {
        String normalisedKind = kind == null ? "" : kind.trim().toUpperCase();
        if (!BRANDING_KINDS.contains(normalisedKind)) {
            throw new BusinessRuleException("kind must be one of " + BRANDING_KINDS + ".");
        }

        String extension = extensionOf(filename);
        if (!BRANDING_EXTENSIONS.contains(extension)) {
            throw new BusinessRuleException(ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                    "Only PNG/JPEG/SVG/WEBP/ICO images are accepted for a logo or favicon.");
        }

        String key = "%s-%s.%s".formatted(normalisedKind.toLowerCase(), UUID.randomUUID(), extension);
        FileStoragePort.StoredFile stored = fileStoragePort.upload(new FileStoragePort.UploadRequest(
                content, key, contentType, "company-branding"));
        return stored.url();
    }

    private static String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase();
    }

    // ------------------------------------------------------------- subscription

    @Override
    @Transactional
    public Company assignSubscription(UUID id, AssignSubscriptionCommand command) {
        Company company = loadOrThrow(id);
        SubscriptionPlan plan = requirePlan(command.subscriptionPlanId());

        LocalDate start = command.startDate() != null ? command.startDate() : LocalDate.now();
        LocalDate end = resolveEnd(start, command.endDate(), command.billingCycle(), command.periods());
        if (!end.isAfter(start)) {
            throw new BusinessRuleException(
                    "A subscription must end after it starts (start %s, end %s)."
                            .formatted(start, end));
        }

        UUID previousPlanId = company.getSubscriptionPlanId();
        CompanyStatus previousStatus = company.getStatus();

        company.setSubscriptionPlanId(plan.getId());
        company.setSubscriptionStartDate(start);
        company.setSubscriptionEndDate(end);
        // Assigning a paid plan ends any trial: leaving trialEndDate in the future
        // would leave two windows open and no way to say which one is in force.
        company.setTrialEndDate(null);
        company.setTrialStartDate(null);
        if (command.remarks() != null && !command.remarks().isBlank()) {
            company.setRemarks(command.remarks());
        }
        // Paying for a window means being able to use it. Activation is skipped only
        // when the company is already active, so no audit noise for a no-op.
        if (previousStatus != CompanyStatus.ACTIVE) {
            company.transitionTo(CompanyStatus.ACTIVE);
        }

        company.applyInvariants();
        Company saved = repository.save(company);

        log.info("Company {} assigned plan {} ({} → {}) by {}",
                saved.getCompanyCode(), plan.getPlanCode(), start, end, currentActor());
        auditService.record(AuditAction.COMPANY_SUBSCRIPTION_ASSIGNED, ENTITY, saved.getId(),
                Map.of("companyCode", saved.getCompanyCode(),
                        "planCode", plan.getPlanCode(),
                        "previousPlanId", String.valueOf(previousPlanId),
                        "previousStatus", previousStatus.name(),
                        "billingCycle", String.valueOf(command.billingCycle()),
                        "periods", String.valueOf(command.periods()),
                        "subscriptionStartDate", start.toString(),
                        "subscriptionEndDate", end.toString()));

        publishSubscriptionChanged(saved, "ASSIGNED", plan);
        return saved;
    }

    @Override
    @Transactional
    public Company renewSubscription(UUID id, RenewSubscriptionCommand command) {
        Company company = loadOrThrow(id);

        // An upgrade taking effect with the new period is a renewal, not a separate
        // assignment — the customer experiences one event and should see one audit row.
        SubscriptionPlan plan = command.subscriptionPlanId() != null
                ? requirePlan(command.subscriptionPlanId())
                : subscriptionPlanService.getById(company.getSubscriptionPlanId());

        LocalDate today = LocalDate.now();
        LocalDate currentEnd = company.getSubscriptionEndDate();

        // Extend from whichever is later. Renewing early must not forfeit the days
        // already paid for; renewing late must not bill for the lapsed gap.
        LocalDate from = (currentEnd == null || currentEnd.isBefore(today)) ? today : currentEnd;
        LocalDate end = resolveEnd(from, command.endDate(), command.billingCycle(), command.periods());

        if (currentEnd != null && !end.isAfter(currentEnd)) {
            throw new BusinessRuleException(
                    "A renewal must extend the subscription; %s is not after the current end %s."
                            .formatted(end, currentEnd));
        }

        CompanyStatus previousStatus = company.getStatus();
        UUID previousPlanId = company.getSubscriptionPlanId();

        company.setSubscriptionPlanId(plan.getId());
        if (company.getSubscriptionStartDate() == null) {
            company.setSubscriptionStartDate(from);
        }
        company.setSubscriptionEndDate(end);
        if (command.remarks() != null && !command.remarks().isBlank()) {
            company.setRemarks(command.remarks());
        }
        // A renewal is how an expired or suspended company comes back; that is the
        // point of the operation, so it activates rather than requiring a second call.
        if (previousStatus != CompanyStatus.ACTIVE) {
            company.transitionTo(CompanyStatus.ACTIVE);
        }

        company.applyInvariants();
        Company saved = repository.save(company);

        log.info("Company {} renewed on plan {} until {} by {}",
                saved.getCompanyCode(), plan.getPlanCode(), end, currentActor());
        auditService.record(AuditAction.COMPANY_SUBSCRIPTION_RENEWED, ENTITY, saved.getId(),
                Map.of("companyCode", saved.getCompanyCode(),
                        "planCode", plan.getPlanCode(),
                        "previousPlanId", String.valueOf(previousPlanId),
                        "previousStatus", previousStatus.name(),
                        "previousEndDate", String.valueOf(currentEnd),
                        "billingCycle", String.valueOf(command.billingCycle()),
                        "periods", String.valueOf(command.periods()),
                        "subscriptionEndDate", end.toString()));

        publishSubscriptionChanged(saved, "RENEWED", plan);
        return saved;
    }

    @Override
    @Transactional
    public Company suspendSubscription(UUID id, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("A subscription suspension reason is required.");
        }

        Company company = loadOrThrow(id);
        CompanyStatus previous = company.getStatus();
        LocalDate today = LocalDate.now();

        // Close the paid window as of today. Left open, a suspended company would still
        // read as "paid until December" on every renewals report.
        if (company.getSubscriptionEndDate() == null || company.getSubscriptionEndDate().isAfter(today)) {
            company.setSubscriptionEndDate(today);
        }
        company.setRemarks(reason);
        if (previous != CompanyStatus.SUSPENDED) {
            company.transitionTo(CompanyStatus.SUSPENDED);
        }

        company.applyInvariants();
        Company saved = repository.save(company);

        SubscriptionPlan plan = subscriptionPlanService.getById(saved.getSubscriptionPlanId());

        log.warn("Company {} subscription suspended from {} by {}: {}",
                saved.getCompanyCode(), previous, currentActor(), reason);
        auditService.record(AuditAction.COMPANY_SUBSCRIPTION_SUSPENDED, ENTITY, saved.getId(),
                Map.of("companyCode", saved.getCompanyCode(),
                        "planCode", plan.getPlanCode(),
                        "previousStatus", previous.name(),
                        "subscriptionEndDate", String.valueOf(saved.getSubscriptionEndDate()),
                        "reason", reason));

        publishSubscriptionChanged(saved, "SUSPENDED", plan);
        return saved;
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Company company = loadOrThrow(id);

        // Soft delete only, per the project invariant. The company's users, roles and
        // settings are intentionally left in place: they carry the company id, and
        // cascading a delete across every company-owned table is not reversible.
        // Deletion also deactivates, so nothing serves the company in the meantime.
        company.transitionTo(CompanyStatus.INACTIVE);
        company.softDelete(SecurityUtils.getCurrentUserId().orElse(null));
        repository.save(company);

        log.warn("Company {} ({}) soft deleted by {}",
                company.getCompanyCode(), company.getCompanyId(), currentActor());
        auditService.record(AuditAction.COMPANY_DELETED, ENTITY, company.getId(),
                Map.of("companyCode", company.getCompanyCode(),
                        "companyId", company.getCompanyId().toString()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CompanyRole> listRoles(UUID id) {
        Company company = loadOrThrow(id);
        // Roles are company-owned, and a SUPER_ADMIN request carries no company, so the
        // Hibernate filter has nothing to apply. Binding it explicitly is what keeps
        // this from returning every company's roles.
        return CompanyContext.runAs(company.getCompanyId(),
                roleRepository::findAllByOrderByRoleCodeAsc);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CompanySetting> listSettings(UUID id) {
        Company company = loadOrThrow(id);
        return CompanyContext.runAs(company.getCompanyId(),
                settingRepository::findAllByOrderByCategoryAscSettingKeyAsc);
    }

    // -------------------------------------------------------------------- helpers

    /**
     * The end of a paid window: an explicitly negotiated date when one is given,
     * otherwise {@code billingCycle × periods} from the start.
     *
     * <p>An explicit date wins because real contracts do not always land on a cycle
     * boundary, and forcing one would make the system disagree with the invoice.
     */
    private LocalDate resolveEnd(LocalDate start, LocalDate explicitEnd,
                                 BillingCycle cycle, int periods) {
        if (explicitEnd != null) {
            return explicitEnd;
        }
        if (cycle == null) {
            throw new BusinessRuleException(
                    "Either a billingCycle or an explicit endDate is required.");
        }
        return cycle.endOf(start, periods < 1 ? 1 : periods);
    }

    private void publishSubscriptionChanged(Company company, String action, SubscriptionPlan plan) {
        eventPublisher.publishEvent(new CompanyEvent.SubscriptionChanged(
                company.getId(), company.getCompanyId(), company.getCompanyCode(),
                action, plan.getId(), plan.getPlanCode(),
                company.getSubscriptionStartDate(), company.getSubscriptionEndDate(),
                Instant.now()));
    }

    private Company loadOrThrow(UUID id) {
        // Safe by primary key: this entity is the company root, so there is no company
        // filter for findById to bypass. @SQLRestriction still hides deleted rows.
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, id));
    }

    /**
     * Resolves the plan through the subscription module's <em>service</em>, never its
     * repository — the cross-feature rule. It also means a missing plan surfaces as
     * that module's own 404 rather than a foreign-key error at flush time.
     */
    private SubscriptionPlan requirePlan(UUID subscriptionPlanId) {
        if (subscriptionPlanId == null) {
            throw new BusinessRuleException("A subscription plan is required.");
        }
        SubscriptionPlan plan = subscriptionPlanService.getById(subscriptionPlanId);
        if (!plan.isActive()) {
            throw new BusinessRuleException(
                    "Subscription plan %s is not active and cannot be assigned."
                            .formatted(plan.getPlanCode()));
        }
        return plan;
    }

    /**
     * Uniqueness is checked against soft-deleted rows too, because the database unique
     * keys do not know about {@code deleted}. Without this the caller would get an
     * opaque 409 from the constraint instead of a message naming the field.
     */
    private void requireUnique(String companyCode, String email, String gst, String pan,
                               UUID excludeId) {
        if (companyCode != null && repository.isCompanyCodeTaken(companyCode, excludeId)) {
            throw new DuplicateResourceException(ENTITY, "companyCode", companyCode);
        }
        if (email != null && repository.isEmailTaken(email, excludeId)) {
            throw new DuplicateResourceException(ENTITY, "email", email);
        }
        if (repository.isGstNumberTaken(gst, excludeId)) {
            throw new DuplicateResourceException(ENTITY, "gstNumber", gst);
        }
        if (repository.isPanNumberTaken(pan, excludeId)) {
            throw new DuplicateResourceException(ENTITY, "panNumber", pan);
        }
    }

    /**
     * Time-ordered UUIDs cannot realistically collide, but a company id collision would
     * merge two companies' data — the single worst failure this system can have. One
     * indexed count is a trivial price for ruling it out.
     */
    private UUID generateCompanyId() {
        for (int attempt = 0; attempt < 3; attempt++) {
            UUID candidate = Company.newCompanyId();
            if (!repository.isCompanyIdTaken(candidate)) {
                return candidate;
            }
            log.error("Generated company id {} already exists; retrying", candidate);
        }
        throw new IllegalStateException("Could not generate a unique company id after 3 attempts");
    }

    /**
     * Explicit optimistic-lock check. {@code @Version} alone only catches a conflict
     * between load and flush inside one transaction; the real hazard is two admins
     * editing the same company across two requests, where the second silently
     * overwrites the first.
     */
    private void requireCurrentVersion(Company company, Long expectedVersion) {
        if (expectedVersion == null) {
            return;
        }
        if (!Objects.equals(company.getVersion(), expectedVersion)) {
            throw new ObjectOptimisticLockingFailureException(Company.class, company.getId());
        }
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private Map<String, Object> snapshot(Company company) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("companyName", company.getCompanyName());
        values.put("legalName", company.getLegalName());
        values.put("displayName", company.getDisplayName());
        values.put("subscriptionPlanId", String.valueOf(company.getSubscriptionPlanId()));
        values.put("email", company.getEmail());
        values.put("mobile", company.getMobile());
        values.put("alternateMobile", company.getAlternateMobile());
        values.put("website", company.getWebsite());
        values.put("gstNumber", company.getGstNumber());
        values.put("panNumber", company.getPanNumber());
        values.put("cinNumber", company.getCinNumber());
        values.put("addressLine1", company.getAddressLine1());
        values.put("addressLine2", company.getAddressLine2());
        values.put("country", company.getCountry());
        values.put("state", company.getState());
        values.put("city", company.getCity());
        values.put("postalCode", company.getPostalCode());
        values.put("timezone", company.getTimezone());
        values.put("currency", company.getCurrency());
        values.put("language", company.getLanguage());
        values.put("dateFormat", company.getDateFormat());
        values.put("timeFormat", company.getTimeFormat());
        values.put("trialEndDate", String.valueOf(company.getTrialEndDate()));
        values.put("subscriptionStartDate", String.valueOf(company.getSubscriptionStartDate()));
        values.put("subscriptionEndDate", String.valueOf(company.getSubscriptionEndDate()));
        values.put("remarks", company.getRemarks());
        return values;
    }

    /**
     * Only what changed reaches the audit trail and the update event. A full
     * before/after dump of twenty-six fields per edit makes both unreadable.
     */
    private Map<String, Object> changeDetails(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> changes = new LinkedHashMap<>();
        before.forEach((field, oldValue) -> {
            Object newValue = after.get(field);
            if (!Objects.equals(oldValue, newValue)) {
                Map<String, Object> pair = new HashMap<>();
                pair.put("from", oldValue);
                pair.put("to", newValue);
                changes.put(field, pair);
            }
        });
        return changes;
    }

    private String currentActor() {
        return SecurityUtils.getCurrentUser()
                .map(user -> user.email() == null ? user.userId().toString() : user.email())
                .orElse("system");
    }
}
