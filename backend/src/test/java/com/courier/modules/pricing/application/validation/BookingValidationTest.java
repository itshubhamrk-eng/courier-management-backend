package com.courier.modules.pricing.application.validation;

import com.courier.modules.master.application.PackageTypeService;
import com.courier.modules.master.application.PaymentModeService;
import com.courier.modules.master.application.ServiceTypeService;
import com.courier.modules.pricing.application.PricingTestSupport;
import com.courier.modules.pricing.application.command.PricingCommand;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookingValidationTest {

    @Mock private ServiceTypeService serviceTypeService;
    @Mock private PackageTypeService packageTypeService;
    @Mock private PaymentModeService paymentModeService;

    private BookingValidation validation;

    @BeforeEach
    void setUp() {
        validation = new BookingValidation(serviceTypeService, packageTypeService, paymentModeService);
    }

    @Test
    void validCommand_resolvesTheBookingDate() {
        LocalDate resolved = validation.validate(command());

        assertThat(resolved).isEqualTo(LocalDate.of(2026, 6, 1));
    }

    @Test
    void missingBookingDate_defaultsToToday() {
        PricingCommand withoutDate = new PricingCommand(
                PricingTestSupport.BOOKING_BRANCH, PricingTestSupport.DELIVERY_BRANCH,
                "411001", "400001", PricingTestSupport.SERVICE_TYPE,
                PricingTestSupport.PACKAGE_TYPE, PricingTestSupport.PAYMENT_MODE,
                new BigDecimal("2.000"), null, null, null, null, null, null, null, null);

        assertThat(validation.validate(withoutDate)).isEqualTo(LocalDate.now());
    }

    @Test
    void unknownServiceType_isRefused() {
        when(serviceTypeService.getById(PricingTestSupport.SERVICE_TYPE))
                .thenThrow(new ResourceNotFoundException("Service type", PricingTestSupport.SERVICE_TYPE));

        assertThatThrownBy(() -> validation.validate(command()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("No such service type");
    }

    private PricingCommand command() {
        return PricingTestSupport.command(new BigDecimal("2.000"));
    }
}
