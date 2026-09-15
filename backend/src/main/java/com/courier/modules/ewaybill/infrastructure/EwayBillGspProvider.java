package com.courier.modules.ewaybill.infrastructure;

import com.courier.modules.ewaybill.application.provider.EwayBillProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * A generic REST/JSON adapter for a government/GSP E-Way Bill API, enabled only via
 * {@code app.ewaybill.gsp.enabled=true} with real credentials configured (see
 * {@code EwayBillProviderConfig} — this bean does not even exist otherwise).
 *
 * <p><b>This is a generic shape, not a specific vendor's contract.</b> No two GSPs
 * expose identical request/response fields; the exact field names below
 * ({@code ewayBillNo}, {@code validUpto}, ...) are the common denominator across most
 * NIC-compatible GSP APIs, but must be reconciled against whichever vendor's real API
 * documentation is in hand before this is switched on in production. Nothing here
 * fabricates a successful response — every branch that cannot positively confirm
 * success returns a failure outcome instead.
 *
 * <p>Every public method catches its own exceptions (timeout, connection refused, 4xx/
 * 5xx, malformed response) and converts them to a failure outcome — this class must
 * never throw, because {@code EwayBillServiceImpl}'s callers (shipment booking,
 * manifest dispatch) must never have their own transaction affected by a government API
 * being unreachable. The client secret/password are sent only as request headers/body
 * to the configured {@code baseUrl}, never logged, never included in any outcome
 * returned to the caller.
 */
@Slf4j
public class EwayBillGspProvider implements EwayBillProvider {

    private static final String NAME = "GSP";

    private final EwayBillGspProperties properties;
    private final RestClient restClient;

    public EwayBillGspProvider(EwayBillGspProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("client-id", properties.getClientId())
                .defaultHeader("client-secret", properties.getClientSecret())
                .defaultHeader("gstin", properties.getGstin())
                .build();
    }

    @Override
    public PartAResult generatePartA(PartARequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("invoiceNo", request.invoiceNumber());
        body.put("invoiceDate", request.invoiceDate());
        body.put("invoiceValue", request.invoiceValue());
        body.put("docType", request.documentType());
        body.put("fromGstin", request.consignorGstin());
        body.put("fromTrdName", request.consignorName());
        body.put("fromAddr", request.consignorAddress());
        body.put("fromPincode", request.consignorPincode());
        body.put("toGstin", request.consigneeGstin());
        body.put("toTrdName", request.consigneeName());
        body.put("toAddr", request.consigneeAddress());
        body.put("toPincode", request.consigneePincode());
        body.put("productDesc", request.productDescription());

        try {
            Map<?, ?> response = restClient.post().uri(properties.getPartAPath())
                    .body(body).retrieve().body(Map.class);
            if (response == null || response.get("ewayBillNo") == null) {
                return PartAResult.failure(NAME, "The E-Way Bill provider returned no E-Way Bill number.");
            }
            return PartAResult.success(
                    String.valueOf(response.get("ewayBillNo")),
                    parseInstant(response.get("validFrom")),
                    parseInstant(response.get("validUpto")),
                    NAME,
                    response.get("referenceId") == null ? null : String.valueOf(response.get("referenceId")));
        } catch (Exception e) {
            log.error("E-Way Bill GSP Part-A call failed for invoice {}: {}",
                    request.invoiceNumber(), e.getMessage());
            return PartAResult.failure(NAME, "The E-Way Bill provider could not be reached or rejected the request.");
        }
    }

    @Override
    public PartBResult updatePartB(PartBRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("ewayBillNo", request.ewayBillNumber());
        body.put("vehicleNo", request.vehicleNumber());
        body.put("transporterId", request.transporterId());
        body.put("transMode", request.transportMode());

        try {
            Map<?, ?> response = restClient.post().uri(properties.getPartBPath())
                    .body(body).retrieve().body(Map.class);
            if (response == null || !Boolean.TRUE.equals(response.get("success"))) {
                return PartBResult.failure("The E-Way Bill provider refused the transport update.");
            }
            return PartBResult.success(
                    response.get("referenceId") == null ? null : String.valueOf(response.get("referenceId")));
        } catch (Exception e) {
            log.error("E-Way Bill GSP Part-B call failed for {}: {}", request.ewayBillNumber(), e.getMessage());
            return PartBResult.failure("The E-Way Bill provider could not be reached or rejected the request.");
        }
    }

    @Override
    public StatusResult getStatus(String ewayBillNumber) {
        try {
            Map<?, ?> response = restClient.get()
                    .uri(properties.getStatusPath() + "/{no}", ewayBillNumber)
                    .retrieve().body(Map.class);
            if (response == null || response.get("status") == null) {
                return StatusResult.notFound();
            }
            return new StatusResult(true, String.valueOf(response.get("status")),
                    response.get("detail") == null ? null : String.valueOf(response.get("detail")));
        } catch (Exception e) {
            log.warn("E-Way Bill GSP status check failed for {}: {}", ewayBillNumber, e.getMessage());
            return StatusResult.notFound();
        }
    }

    @Override
    public CancelResult cancel(String ewayBillNumber, String reason) {
        Map<String, Object> body = Map.of("ewayBillNo", ewayBillNumber, "cancelRemarks", reason == null ? "" : reason);
        try {
            Map<?, ?> response = restClient.post().uri(properties.getCancelPath())
                    .body(body).retrieve().body(Map.class);
            if (response == null || !Boolean.TRUE.equals(response.get("success"))) {
                return CancelResult.failure("The E-Way Bill provider refused the cancellation.");
            }
            return CancelResult.ok();
        } catch (Exception e) {
            log.error("E-Way Bill GSP cancel call failed for {}: {}", ewayBillNumber, e.getMessage());
            return CancelResult.failure("The E-Way Bill provider could not be reached or rejected the cancellation.");
        }
    }

    private static Instant parseInstant(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }
}
