package com.courier.modules.shipment.infrastructure;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * S3 file storage settings, bound from {@code app.storage.s3.*}.
 *
 * <p>{@code enabled} is off by default and there is no default bucket. A deployment that
 * has not configured a bucket gets {@code UnconfiguredFileStorage}, which refuses every
 * upload with a clear 422 — the same fail-closed choice {@code RazorpayProperties} makes
 * for the payment gateway. Credentials are deliberately not fields here: the AWS SDK's
 * default provider chain resolves them (env vars locally, the EC2 instance role in
 * production), so there is nothing here to leak.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.storage.s3")
public class S3Properties {

    private boolean enabled = false;

    private String bucket;

    private String region = "us-east-1";
}
