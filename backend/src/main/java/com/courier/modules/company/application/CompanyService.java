package com.courier.modules.company.application;

import com.courier.modules.company.application.command.AssignSubscriptionCommand;
import com.courier.modules.company.application.command.CreateCompanyCommand;
import com.courier.modules.company.application.command.RenewSubscriptionCommand;
import com.courier.modules.company.application.command.UpdateCompanyCommand;
import com.courier.modules.company.domain.Company;
import com.courier.modules.company.domain.CompanyCriteria;
import com.courier.modules.company.domain.CompanyRole;
import com.courier.modules.company.domain.CompanySetting;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Use cases for companies — the ownership roots of the platform, and the only thing a
 * super admin creates directly.
 *
 * <p><b>Every method requires {@code SUPER_ADMIN}</b>, enforced by a class-level
 * {@code @PreAuthorize} on {@link CompanyServiceImpl}. It is on the implementation
 * rather than here so it holds whichever proxy strategy Spring picks.
 *
 * <p>Returns entities, not DTOs: the wire contract belongs to the {@code api} layer.
 */
public interface CompanyService {

    /**
     * Creates a company and everything it needs to operate: company id, default roles
     * and permissions, default settings, and the first administrator.
     */
    CreatedCompany create(CreateCompanyCommand command);

    /** Full replacement. Fails with 409 if {@code expectedVersion} is stale. */
    Company update(UUID id, UpdateCompanyCommand command);

    Company getById(UUID id);

    Company getByCompanyId(UUID companyId);

    Page<Company> search(CompanyCriteria criteria, Pageable pageable);

    Company activate(UUID id);

    /**
     * Switches a company off without the punitive meaning of suspension: it is dormant,
     * not in trouble. Its users can no longer authenticate either way, but only one of
     * the two shows up in a support conversation as an accusation.
     *
     * @param reason optional; recorded in the audit trail when given
     */
    Company deactivate(UUID id, String reason);

    /** @param reason required — it is the first thing support will ask for */
    Company suspend(UUID id, String reason);

    Company expire(UUID id);

    /**
     * Uploads a logo or favicon image and returns where it landed. Not tied to any one
     * company: the create form has no company id yet, and the edit form still writes the
     * URL through the ordinary {@link #update} call — this only stores bytes.
     *
     * @param kind {@code LOGO} or {@code FAVICON}
     */
    String uploadBranding(String kind, byte[] content, String filename, String contentType);

    // ------------------------------------------------------------- subscription

    /**
     * Moves the company onto a plan and opens a paid window, activating it.
     *
     * <p>Separate from {@link #update} even though both can change the plan id:
     * {@code update} is an edit of the company record, this is the commercial act, and
     * conflating them makes "when did they go up to ENTERPRISE" unanswerable.
     */
    Company assignSubscription(UUID id, AssignSubscriptionCommand command);

    /** Extends the paid window from the later of its current end and today. */
    Company renewSubscription(UUID id, RenewSubscriptionCommand command);

    /**
     * Stops the subscription: the company is suspended and the paid window is closed as
     * of today. Reversible by renewing.
     *
     * @param reason required — non-payment, a chargeback, a compliance hold
     */
    Company suspendSubscription(UUID id, String reason);

    /** Soft delete. The row is retained; nothing is ever physically removed. */
    void delete(UUID id);

    /** The company's roles, for the roles screen. */
    List<CompanyRole> listRoles(UUID id);

    /** The company's settings, ordered by category. */
    List<CompanySetting> listSettings(UUID id);

    /**
     * Result of a creation, carrying what the caller cannot read back afterwards: the
     * administrator's temporary password, how much was provisioned, and whether the
     * activation email actually went out.
     *
     * @param company               the persisted company
     * @param adminUserId           the created administrator
     * @param adminEmail            their login address
     * @param temporaryPassword     readable exactly once, here. Never logged, audited,
     *                              emailed or retrievable again — a lost one is reset,
     *                              not recovered
     * @param activationEmailSent   false means the account exists but the link must be
     *                              reissued before anyone can sign in
     * @param roleCount             default roles created
     * @param settingCount          default settings created
     */
    record CreatedCompany(Company company,
                          UUID adminUserId,
                          String adminEmail,
                          String temporaryPassword,
                          boolean activationEmailSent,
                          int roleCount,
                          int settingCount) {
    }
}
