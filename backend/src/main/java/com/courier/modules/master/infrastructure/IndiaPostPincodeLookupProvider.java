package com.courier.modules.master.infrastructure;

import com.courier.modules.master.application.port.PincodePostalLookupProvider;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * India Post's own free, no-key public directory (api.postalpincode.in) — real post-office
 * data, not a heuristic, and needs no vendor account, unlike every other "no real
 * implementation in this dev environment" provider in this codebase. A pincode with no
 * digits (blank/malformed) is the caller's job to refuse before this is reached.
 *
 * <p>The upstream response is an array of one result object per queried pincode (this
 * module only ever queries one), each carrying {@code Status}/{@code PostOffice}. A
 * network failure or a non-{@code Success} status both resolve to an empty list rather
 * than throwing — the caller's fallback is "let the operator pick an Area manually," not
 * a 500.
 */
@Slf4j
public class IndiaPostPincodeLookupProvider implements PincodePostalLookupProvider {

    private static final String BASE_URL = "https://api.postalpincode.in";

    private final RestClient restClient;

    public IndiaPostPincodeLookupProvider(RestClient.Builder builder) {
        this.restClient = builder.baseUrl(BASE_URL).build();
    }

    @Override
    public List<PostOffice> lookup(String pincode) {
        try {
            List<LookupResult> results = restClient.get()
                    .uri("/pincode/{pincode}", pincode)
                    // The upstream's own front end resets the connection for the JDK
                    // HttpClient's default "Java-http-client/…" User-Agent (confirmed by
                    // reproducing with curl: identical request succeeds with any other
                    // UA, including a blank one) — not this deployment's outbound access
                    // being blocked, and not an HTTP/2 interop issue.
                    .header(HttpHeaders.USER_AGENT, "CourierManagement-PincodeLookup/1.0")
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<LookupResult>>() { });

            if (results == null || results.isEmpty()) {
                return List.of();
            }
            LookupResult first = results.get(0);
            if (!"Success".equalsIgnoreCase(first.status()) || first.postOffices() == null) {
                return List.of();
            }
            return first.postOffices().stream()
                    .map(po -> new PostOffice(po.name(), po.division(), po.district(),
                            po.state(), po.country()))
                    .toList();
        } catch (Exception e) {
            log.warn("Postal lookup for pincode {} failed: {}", pincode, e.getMessage());
            return List.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LookupResult(
            @JsonProperty("Status") String status,
            @JsonProperty("PostOffice") List<PostOfficeDto> postOffices) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PostOfficeDto(
            @JsonProperty("Name") String name,
            @JsonProperty("Division") String division,
            @JsonProperty("District") String district,
            @JsonProperty("State") String state,
            @JsonProperty("Country") String country) {
    }
}
