package com.courier.modules.finance.application;

import com.courier.modules.finance.domain.CompanyRazorpayConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Clears a company's Razorpay secret(s) that no longer decrypt under the running
 * {@code SECRETS_ENCRYPTION_KEY}, in a brand new transaction.
 *
 * <p>A separate bean/transaction is required, not a convenience: once a Hibernate session
 * has thrown mid-read (the {@code AttributeConverter} failure this repairs), that session
 * must be discarded — reusing it for a further write in the same transaction is
 * unreliable. {@code REQUIRES_NEW} gets a fresh session regardless of what the caller's
 * own transaction is doing, and self-invocation from within the same class would bypass
 * Spring's proxy and silently ignore the propagation, hence this being its own bean.
 */
@Service
@RequiredArgsConstructor
public class CompanyRazorpayConfigRepairService {

    private final CompanyRazorpayConfigRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void clearUnreadableSecrets(UUID companyId) {
        repository.clearUnreadableSecrets(companyId);
    }
}
