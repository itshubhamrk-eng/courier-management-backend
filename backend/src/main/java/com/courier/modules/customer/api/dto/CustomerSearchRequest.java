package com.courier.modules.customer.api.dto;

import com.courier.modules.customer.domain.CustomerStatus;
import com.courier.modules.customer.domain.CustomerType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.Set;

/** Query parameters of {@code GET /api/v1/customers}, bound as a parameter object. */
@Schema(name = "CustomerSearchRequest", description = "Customer search filters")
public record CustomerSearchRequest(
        Set<CustomerType> customerType,
        Set<CustomerStatus> status,
        @Size(max = 100)
        @Schema(description = "Free text over code, name, company name, mobile and email")
        String search
) {
    public static CustomerSearchRequest empty() {
        return new CustomerSearchRequest(null, null, null);
    }
}
