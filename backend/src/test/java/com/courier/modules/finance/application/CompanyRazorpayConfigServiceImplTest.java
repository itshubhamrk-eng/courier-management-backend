package com.courier.modules.finance.application;

import com.courier.modules.finance.domain.CompanyRazorpayConfig;
import com.courier.modules.finance.domain.CompanyRazorpayConfigRepository;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.company.CompanyContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataRetrievalFailureException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * A company's stored Razorpay {@code keySecret} can outlive the
 * {@code SECRETS_ENCRYPTION_KEY} it was encrypted under (key rotated after the row was
 * saved) — {@link CompanyRazorpayConfigServiceImpl#get()} must degrade to "not configured"
 * rather than 500 every read of company settings for that company.
 */
@ExtendWith(MockitoExtension.class)
class CompanyRazorpayConfigServiceImplTest {

    private static final UUID COMPANY = UUID.randomUUID();

    @Mock
    private CompanyRazorpayConfigRepository repository;
    @Mock
    private AuditService auditService;

    private CompanyRazorpayConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CompanyRazorpayConfigServiceImpl(repository, auditService);
        CompanyContext.setCompanyId(COMPANY);
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    @Test
    @DisplayName("get() returns a default config instead of throwing when the stored secret can't be decrypted")
    void getFallsBackWhenStoredSecretCannotBeDecrypted() {
        when(repository.findByCompanyId(COMPANY))
                .thenThrow(new DataRetrievalFailureException("Error attempting to apply AttributeConverter",
                        new IllegalStateException("Could not decrypt a stored value.")));

        CompanyRazorpayConfig result = service.get();

        assertThat(result.isEnabled()).isFalse();
        assertThat(result.getKeyId()).isNull();
        assertThat(result.getKeySecret()).isNull();
    }

    @Test
    @DisplayName("get() also falls back on a bare IllegalStateException (converter's own throw)")
    void getFallsBackOnIllegalStateException() {
        when(repository.findByCompanyId(COMPANY))
                .thenThrow(new IllegalStateException("Could not decrypt a stored value."));

        CompanyRazorpayConfig result = service.get();

        assertThat(result.isEnabled()).isFalse();
    }
}
