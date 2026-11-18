package com.courier.modules.legacyimport.application;

import java.util.UUID;

/** A legacy "From" text with how many NEW rows use it and the branch it maps to (null = unmapped). */
public record OriginSummary(String originKey, int newRows, UUID mappedBranchId) {
}
