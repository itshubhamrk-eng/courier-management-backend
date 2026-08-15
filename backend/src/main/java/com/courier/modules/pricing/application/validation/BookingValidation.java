package com.courier.modules.pricing.application.validation;

import com.courier.modules.master.application.PackageTypeService;
import com.courier.modules.master.application.PaymentModeService;
import com.courier.modules.master.application.ServiceTypeService;
import com.courier.modules.pricing.application.command.PricingCommand;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Function;

/**
 * Flow step 2: everything about the booking itself, other than the lane (already
 * {@link RouteValidation}'s job) and the rate ({@link RateValidation}'s job).
 *
 * <ul>
 *   <li>Service type, package type and payment mode exist ("Invalid Service Type").</li>
 *   <li>Resolves the effective booking date, defaulting to today — the same convention
 *       Rate Master's calculator uses.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class BookingValidation {

    private final ServiceTypeService serviceTypeService;
    private final PackageTypeService packageTypeService;
    private final PaymentModeService paymentModeService;

    public LocalDate validate(PricingCommand command) {
        requireExists(command.serviceTypeId(), serviceTypeService::getById, "service type");
        requireExists(command.packageTypeId(), packageTypeService::getById, "package type");
        requireExists(command.paymentModeId(), paymentModeService::getById, "payment mode");

        return command.bookingDate() == null ? LocalDate.now() : command.bookingDate();
    }

    private void requireExists(UUID id, Function<UUID, ?> lookup, String label) {
        if (id == null) {
            throw new BusinessRuleException("A " + label + " is required.");
        }
        try {
            lookup.apply(id);
        } catch (ResourceNotFoundException e) {
            throw new BusinessRuleException("No such " + label + ": " + id);
        }
    }
}
