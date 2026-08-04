package com.courier.modules.pricing.application.validation;

import com.courier.modules.pricing.application.PricingTestSupport;
import com.courier.modules.pricing.application.command.PricingCommand;
import com.courier.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WeightValidationTest {

    private final WeightValidation validation = new WeightValidation();

    @Test
    void positiveWeight_andNoDimensions_isAccepted() {
        assertThatCode(() -> validation.validate(PricingTestSupport.command(new BigDecimal("2.000"))))
                .doesNotThrowAnyException();
    }

    @Test
    void zeroWeight_isRefused() {
        PricingCommand command = PricingTestSupport.command(BigDecimal.ZERO);

        assertThatThrownBy(() -> validation.validate(command))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Actual weight");
    }

    @Test
    void missingWeight_isRefused() {
        PricingCommand command = PricingTestSupport.command(null);

        assertThatThrownBy(() -> validation.validate(command))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void zeroDimension_isRefused() {
        PricingCommand command = PricingTestSupport.command(new BigDecimal("2.000"),
                BigDecimal.ZERO, new BigDecimal("20"), new BigDecimal("10"), null, null, null);

        assertThatThrownBy(() -> validation.validate(command))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Length");
    }

    @Test
    void positiveDimensions_areAccepted() {
        PricingCommand command = PricingTestSupport.command(new BigDecimal("2.000"),
                new BigDecimal("30"), new BigDecimal("20"), new BigDecimal("10"), null, null, null);

        assertThatCode(() -> validation.validate(command)).doesNotThrowAnyException();
    }
}
