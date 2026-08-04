package com.courier.modules.pricing.application.validation;

import com.courier.modules.pricing.application.PricingTestSupport;
import com.courier.modules.rate.application.RateService;
import com.courier.modules.rate.domain.Rate;
import com.courier.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateValidationTest {

    @Mock
    private RateService rateService;

    private RateValidation validation;

    @Test
    void nonEmptyCandidates_areReturned() {
        validation = new RateValidation(rateService);
        UUID routeId = UUID.randomUUID();
        Rate rate = PricingTestSupport.rate("RATE1", "0.000", "5.000");
        when(rateService.findActiveCandidates(any(), any(), any(), any(), any()))
                .thenReturn(List.of(rate));

        List<Rate> result = validation.validate(routeId,
                PricingTestSupport.command(new java.math.BigDecimal("2.000")), LocalDate.now());

        assertThat(result).containsExactly(rate);
    }

    @Test
    void noActiveRate_isRefused() {
        validation = new RateValidation(rateService);
        when(rateService.findActiveCandidates(any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> validation.validate(UUID.randomUUID(),
                PricingTestSupport.command(new java.math.BigDecimal("2.000")),
                LocalDate.of(2026, 6, 1)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("No active rate is effective on 2026-06-01");
    }
}
