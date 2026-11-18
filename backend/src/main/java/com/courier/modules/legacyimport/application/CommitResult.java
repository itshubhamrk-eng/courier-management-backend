package com.courier.modules.legacyimport.application;

import java.util.List;
import java.util.UUID;

public record CommitResult(UUID batchId, int created, int skippedExisting, int invalid, List<PreviewRow> rows) {
}
