package com.courier.modules.master.application;

/** Tally of a bulk pincode import run — see {@link PincodeBulkImportService}. */
public record PincodeBulkImportResult(
        int probed, int created, int alreadyExisted, int noPostalMatch, int failed) {

    public static PincodeBulkImportResult empty() {
        return new PincodeBulkImportResult(0, 0, 0, 0, 0);
    }

    public PincodeBulkImportResult plus(PincodeBulkImportResult other) {
        return new PincodeBulkImportResult(
                probed + other.probed, created + other.created,
                alreadyExisted + other.alreadyExisted, noPostalMatch + other.noPostalMatch,
                failed + other.failed);
    }
}
