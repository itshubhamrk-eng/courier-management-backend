package com.courier.modules.shipment.infrastructure;

import com.courier.modules.shipment.application.storage.FileStoragePort;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * S3 adapter. One call: put the object, return its URL.
 *
 * <p>The bucket is assumed private with public reads disabled — the URL returned is the
 * plain virtual-hosted-style S3 URL, not a signed one, matching how {@code ShipmentDocument
 * .documentUrl} already treats a caller-supplied URL as directly fetchable. If a bucket
 * this is pointed at is actually private, reads will 403 until either the bucket policy or
 * this adapter (presigned GET) changes — a deliberate scope cut, not an oversight; see the
 * PR/changelog note.
 */
@Slf4j
public class S3FileStorage implements FileStoragePort {

    private final S3Client s3Client;
    private final S3Properties properties;

    public S3FileStorage(S3Client s3Client, S3Properties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    @Override
    public StoredFile upload(UploadRequest request) {
        String key = request.keyPrefix() + "/" + request.filename();
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(properties.getBucket())
                            .key(key)
                            .contentType(request.contentType())
                            .build(),
                    RequestBody.fromBytes(request.content()));
        } catch (Exception e) {
            // Never surface the SDK's own exception text: it can echo request details.
            log.error("S3 upload failed for key {}", key, e);
            throw new BusinessRuleException(ErrorCode.SERVICE_UNAVAILABLE,
                    "The file could not be stored. Please retry.");
        }

        String url = "https://%s.s3.%s.amazonaws.com/%s"
                .formatted(properties.getBucket(), properties.getRegion(), key);
        return new StoredFile(url, key);
    }
}
