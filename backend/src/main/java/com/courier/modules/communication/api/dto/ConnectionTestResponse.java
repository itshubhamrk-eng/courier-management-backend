package com.courier.modules.communication.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ConnectionTestResponse")
public record ConnectionTestResponse(boolean ok, String message) {
}
