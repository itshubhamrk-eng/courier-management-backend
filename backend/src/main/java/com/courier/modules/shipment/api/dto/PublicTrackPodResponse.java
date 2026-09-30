package com.courier.modules.shipment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Proof of delivery for the public tracking page's verified detail view. {@code photoUrl}
 * and {@code signatureUrl} are short-lived presigned S3 links (see {@code
 * FileStoragePort#presignGet}), never the permanent unsigned URL the authenticated POD
 * endpoints return — a link handed to an unauthenticated caller must not work forever.
 * Only ever populated once a human reviewer has approved the capture (verification status
 * {@code PASS}); a shipment still awaiting review or with a failed capture gets no POD block
 * at all rather than one showing an unreviewed image.
 */
@Schema(name = "PublicTrackPodResponse")
public record PublicTrackPodResponse(
        String photoUrl,
        String signatureUrl,
        String deliveredBy,
        Instant deliveredAt
) {
}
