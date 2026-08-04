package com.courier.modules.pricing.application.strategy;

import com.courier.modules.pricing.application.PricingContext;
import com.courier.modules.pricing.application.PricingResult;
import com.courier.modules.pricing.application.PricingTestSupport;
import com.courier.modules.pricing.application.calculator.ChargeCalculator;
import com.courier.modules.pricing.application.calculator.DiscountCalculator;
import com.courier.modules.pricing.application.calculator.FreightCalculator;
import com.courier.modules.pricing.application.calculator.FuelCalculator;
import com.courier.modules.pricing.application.calculator.GSTCalculator;
import com.courier.modules.pricing.application.calculator.HandlingCalculator;
import com.courier.modules.pricing.application.calculator.InsuranceCalculator;
import com.courier.modules.pricing.application.calculator.ODAChargeCalculator;
import com.courier.modules.pricing.application.calculator.RoundOffCalculator;
import com.courier.modules.pricing.application.command.PricingCommand;
import com.courier.modules.pricing.domain.RoundingRule;
import com.courier.modules.rate.domain.Rate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs every real calculator together — the "grand total" end-to-end check the module's
 * Testing section asks for: Freight, Fuel, Handling, ODA, Insurance, GST, Discount, Round
 * Off, Net Amount all in one pass. */
class StandardPricingStrategyTest {

    private final StandardPricingStrategy strategy = new StandardPricingStrategy(List.of(
            new FreightCalculator(), new FuelCalculator(), new HandlingCalculator(),
            new ODAChargeCalculator(), new InsuranceCalculator(), new GSTCalculator(),
            new DiscountCalculator(), new RoundOffCalculator()));

    @Test
    void fullBreakup_exactSlab_withInsuranceAndDiscount() {
        Rate rate = PricingTestSupport.rate("RATE1", "0.000", "5.000");
        PricingCommand command = PricingTestSupport.command(new BigDecimal("2.000"), null, null,
                null, new BigDecimal("5000.00"), new BigDecimal("10"), null);
        PricingContext context = PricingTestSupport.contextWithMatchedRate(rate,
                new BigDecimal("2.000"), command,
                PricingTestSupport.configuration(true, true, true, true, RoundingRule.NEAREST_ONE));

        PricingResult result = strategy.price(context);

        assertThat(result.freight()).isEqualByComparingTo("100.00");
        assertThat(result.fuelCharge()).isEqualByComparingTo("10.00");
        assertThat(result.handlingCharge()).isEqualByComparingTo("5.00");
        assertThat(result.odaCharge()).isEqualByComparingTo("15.00");
        assertThat(result.insuranceCharge()).isEqualByComparingTo("8.00");
        // (100 + 10 + 5 + 15 + 8) * 18% = 24.84
        assertThat(result.gstAmount()).isEqualByComparingTo("24.84");
        // 10% of (138.00 + 24.84 = 162.84) = 16.28
        assertThat(result.discountAmount()).isEqualByComparingTo("16.28");
        // 162.84 - 16.28 = 146.56 -> rounds to 147.00, round off +0.44
        assertThat(result.roundOff()).isEqualByComparingTo("0.44");
        assertThat(result.netAmount()).isEqualByComparingTo("147.00");
    }

    @Test
    void disabledLines_contributeZero_notSkipped() {
        Rate rate = PricingTestSupport.rate("RATE1", "0.000", "5.000");
        PricingCommand command = PricingTestSupport.command(new BigDecimal("2.000"));
        PricingContext context = PricingTestSupport.contextWithMatchedRate(rate,
                new BigDecimal("2.000"), command,
                PricingTestSupport.configuration(false, false, false, false, RoundingRule.NONE));

        PricingResult result = strategy.price(context);

        assertThat(result.fuelCharge()).isEqualByComparingTo("0.00");
        assertThat(result.odaCharge()).isEqualByComparingTo("0.00");
        assertThat(result.insuranceCharge()).isEqualByComparingTo("0.00");
        assertThat(result.discountAmount()).isEqualByComparingTo("0.00");
        // freight (100) + handling (5, no toggle) + gst 18% of 105 = 18.90
        assertThat(result.gstAmount()).isEqualByComparingTo("18.90");
        assertThat(result.netAmount()).isEqualByComparingTo("123.90");
    }

    @Test
    void calculatorTypesAreAllRegisteredExactlyOnce() {
        List<ChargeCalculator> calculators = List.of(
                new FreightCalculator(), new FuelCalculator(), new HandlingCalculator(),
                new ODAChargeCalculator(), new InsuranceCalculator(), new GSTCalculator(),
                new DiscountCalculator(), new RoundOffCalculator());

        assertThat(calculators).extracting(ChargeCalculator::type).doesNotHaveDuplicates();
        assertThat(calculators).hasSize(8);
    }
}
