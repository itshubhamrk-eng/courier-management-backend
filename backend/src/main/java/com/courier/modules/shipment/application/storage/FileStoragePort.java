package com.courier.modules.shipment.application.storage;

/**
 * The seam between POD (proof of delivery) capture and whichever object store a
 * deployment uses. Nothing in the shipment module knows S3's name, wire format or SDK —
 * swapping providers is a new implementation of this interface and a config change, the
 * same split {@code PaymentGatewayPort} draws for Razorpay.
 */
public interface FileStoragePort {

    /**
     * Stores the given bytes and returns where they landed.
     *
     * @throws com.courier.shared.exception.BusinessRuleException no backend is configured,
     *         or the store could not be reached
     */
    StoredFile upload(UploadRequest request);

    /**
     * @param content     the whole file, already read off the multipart request
     * @param filename    the storage key, not the caller's original filename — the
     *                    controller is responsible for making this unique
     * @param contentType as declared by the browser; the backend does not sniff bytes
     * @param keyPrefix   groups objects by what they are, e.g. {@code pod/<companyId>/<shipmentId>}
     */
    record UploadRequest(byte[] content, String filename, String contentType, String keyPrefix) {
    }

    /**
     * @param url publicly resolvable location of the stored object
     * @param key the storage-native key, kept in case a future feature needs to delete
     *            or re-derive it without re-parsing the URL
     */
    record StoredFile(String url, String key) {
    }
}
