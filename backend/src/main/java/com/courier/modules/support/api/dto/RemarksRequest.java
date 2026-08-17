package com.courier.modules.support.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** Body of reopen/close — both optional, a decision needs no reason. */
@Schema(name = "RemarksRequest")
public record RemarksRequest(@Size(max = 1000) String remarks) {
}
