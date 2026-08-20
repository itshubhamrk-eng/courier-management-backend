package com.courier.modules.pod.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PodReviewRequest(
        @NotNull Boolean approve,
        @Size(max = 1000) String remarks) {
}
