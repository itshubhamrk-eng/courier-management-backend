package com.courier.modules.pricing.application.validation;

import com.courier.modules.master.application.RouteService;
import com.courier.modules.master.domain.MasterStatus;
import com.courier.modules.master.domain.Route;
import com.courier.modules.pricing.application.PricingTestSupport;
import com.courier.modules.pricing.application.command.PricingCommand;
import com.courier.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RouteValidationTest {

    @Mock
    private RouteService routeService;

    private RouteValidation validation;

    @Test
    void activeRoute_isReturned() {
        validation = new RouteValidation(routeService);
        Route route = route(MasterStatus.ACTIVE);
        when(routeService.findByBranches(PricingTestSupport.BOOKING_BRANCH,
                PricingTestSupport.DELIVERY_BRANCH)).thenReturn(route);

        Route result = validation.validate(command());

        assertThat(result).isSameAs(route);
    }

    @Test
    void inactiveRoute_isRefused() {
        validation = new RouteValidation(routeService);
        when(routeService.findByBranches(PricingTestSupport.BOOKING_BRANCH,
                PricingTestSupport.DELIVERY_BRANCH)).thenReturn(route(MasterStatus.INACTIVE));

        assertThatThrownBy(() -> validation.validate(command()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("inactive");
    }

    @Test
    void noRouteBetweenBranches_isRefused() {
        validation = new RouteValidation(routeService);
        when(routeService.findByBranches(PricingTestSupport.BOOKING_BRANCH,
                PricingTestSupport.DELIVERY_BRANCH))
                .thenThrow(new BusinessRuleException("No route runs from branch A to branch B."));

        assertThatThrownBy(() -> validation.validate(command()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("No route");
    }

    private PricingCommand command() {
        return PricingTestSupport.command(new BigDecimal("2.000"));
    }

    private Route route(MasterStatus status) {
        Route route = new Route();
        route.setCode("PUNE_MUM");
        route.setStatus(status);
        route.setBookingBranchId(PricingTestSupport.BOOKING_BRANCH);
        route.setDeliveryBranchId(PricingTestSupport.DELIVERY_BRANCH);
        return route;
    }
}
