package com.courier.modules.support.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Just enough for the marketing site's success screen to show a reference number. */
@Schema(name = "PublicLeadResponse")
public record PublicLeadResponse(String ticketNumber) {
}
