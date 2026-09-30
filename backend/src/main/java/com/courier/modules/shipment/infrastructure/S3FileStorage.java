package com.courier.modules.shipment.infrastructure;

import com.courier.modules.shipment.application.storage.FileStoragePort;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;

/**
 * S3 adapter. One call: put the object, return its URL.
 *
 * <p>The bucket is assumed private with public reads disabled — the URL {@link #upload}
 * returns is the plain virtual-hosted-style S3 URL, not a signed one, matching how {@code
 * ShipmentDocument.documentUrl} already treats a caller-supplied URL as directly fetchable.
 * If a bucket this is pointed at is actually private, reads will 403 until either the
 * bucket policy changes or a caller asks for a {@link #presignGet} URL instead — a
 * deliberate scope cut, not an oversight; see the PR/changelog note.
 */
@Slf4j
public class S3FileStorage implements FileStoragePort {

    private final S3Client s3Client;
    private final S3Presigner presigner;
    private final S3Properties properties;
    private final String urlPrefix;

    public S3FileStorage(S3Client s3Client, S3Presigner presigner, S3Properties properties) {
        this.s3Client = s3Client;
        this.presigner = presigner;
        this.properties = properties;
        this.urlPrefix = "https://%s.s3.%s.amazonaws.com/".formatted(properties.getBucket(), properties.getRegion());
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

        return new StoredFile(urlPrefix + key, key);
    }

    @Override
    public String presignGet(String storedUrl, Duration ttl) {
        if (storedUrl == null || !storedUrl.startsWith(urlPrefix)) {
            // Not one of our own objects (e.g. a caller-supplied external URL) — nothing to sign.
            return storedUrl;
        }
        String key = storedUrl.substring(urlPrefix.length());
        try {
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(ttl)
                    .getObjectRequest(GetObjectRequest.builder()
                            .bucket(properties.getBucket())
                            .key(key)
                            .build())
                    .build();
            return presigner.presignGetObject(presignRequest).url().toString();
        } catch (Exception e) {
            log.error("S3 presign failed for key {}", key, e);
            throw new BusinessRuleException(ErrorCode.SERVICE_UNAVAILABLE,
                    "The file could not be retrieved. Please retry.");
        }
    }
}
