package com.courier.modules.shipment.infrastructure;

import com.courier.modules.shipment.application.storage.FileStoragePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Picks the file storage backend for this deployment. Same shape as
 * {@code PaymentGatewayConfig} — two explicit, mutually exclusive conditions, no
 * {@code @ConditionalOnMissingBean}, so which bean wins is never left to bean-scan order.
 *
 * <p>Enabling S3 without a bucket fails at startup rather than at the first upload: a
 * misconfigured backend should be a deploy failure, not a delivery agent's problem.
 * Credentials are never read here — {@link DefaultCredentialsProvider} resolves them (env
 * vars locally, the EC2 instance role in production), so nothing secret passes through
 * application config.
 */
@Slf4j
@Configuration
public class FileStorageConfig {

    @Bean
    @ConditionalOnProperty(prefix = "app.storage.s3", name = "enabled", havingValue = "true")
    public FileStoragePort s3FileStorage(S3Properties properties) {
        if (isBlank(properties.getBucket())) {
            throw new IllegalStateException(
                    "app.storage.s3.enabled is true but bucket is not set. Set AWS_S3_BUCKET, "
                            + "or disable S3 storage.");
        }
        S3Client client = S3Client.builder()
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
        log.info("S3 file storage enabled (bucket {}, region {})",
                properties.getBucket(), properties.getRegion());
        return new S3FileStorage(client, properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.storage.s3", name = "enabled",
            havingValue = "false", matchIfMissing = true)
    public FileStoragePort unconfiguredFileStorage() {
        log.warn("No file storage backend configured — POD file upload will be refused.");
        return new UnconfiguredFileStorage();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
