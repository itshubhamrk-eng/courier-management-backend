package com.courier.modules.pricing.application.factory;

import com.courier.modules.pricing.application.PricingContext;
import com.courier.modules.pricing.application.PricingResult;
import com.courier.modules.pricing.application.strategy.PricingStrategy;
import com.courier.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PricingFactoryImplTest {

    @Test
    void resolvesTheFirstSupportingStrategy() {
        PricingStrategy notApplicable = stub(false);
        PricingStrategy applicable = stub(true);
        PricingFactoryImpl factory = new PricingFactoryImpl(List.of(notApplicable, applicable));

        assertThat(factory.resolve(null)).isSameAs(applicable);
    }

    @Test
    void noSupportingStrategy_isRefused() {
        PricingFactoryImpl factory = new PricingFactoryImpl(List.of(stub(false)));

        assertThatThrownBy(() -> factory.resolve(null))
                .isInstanceOf(BusinessRuleException.class);
    }

    private PricingStrategy stub(boolean supports) {
        return new PricingStrategy() {
            @Override
            public boolean supports(PricingContext context) {
                return supports;
            }

            @Override
            public PricingResult price(PricingContext context) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
