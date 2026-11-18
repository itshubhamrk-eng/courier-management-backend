package com.courier.modules.legacyimport.application;

import java.util.List;

public record PreviewResult(String fileHash, int total, int newCount, int existingCount, int differsCount,
                            int invalidCount, List<OriginSummary> origins, List<PreviewRow> rows) {
}
