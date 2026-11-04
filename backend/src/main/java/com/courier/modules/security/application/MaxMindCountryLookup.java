package com.courier.modules.security.application;

import com.maxmind.geoip2.DatabaseReader;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Optional;

/**
 * Production {@link CountryLookup} backed by an offline GeoLite2-Country database.
 * The reader is opened once at startup; any failure is logged and leaves the lookup
 * unavailable (every call then yields empty), never stopping the application.
 */
@Slf4j
@Component
public class MaxMindCountryLookup implements CountryLookup {

    private final SecurityProperties properties;
    private volatile DatabaseReader reader;

    public MaxMindCountryLookup(SecurityProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void open() {
        SecurityProperties.Geo geo = properties.getGeo();
        if (!geo.isEnabled()) {
            return;
        }
        try {
            String path = geo.getDbPath();
            if (path == null || path.isBlank()) {
                log.warn("Geo checks enabled but courier.security.geo.db-path is not set; countries will be UNKNOWN");
                return;
            }
            reader = new DatabaseReader.Builder(new File(path)).build();
            log.info("GeoLite2 country database loaded from {}", path);
        } catch (Exception e) {
            log.warn("Could not open GeoLite2 country database; countries will be UNKNOWN: {}", e.toString());
        }
    }

    @PreDestroy
    void close() {
        DatabaseReader r = reader;
        if (r != null) {
            try {
                r.close();
            } catch (IOException e) {
                log.debug("Closing GeoLite2 reader failed", e);
            }
        }
    }

    @Override
    public Optional<String> countryOf(InetAddress address) throws Exception {
        DatabaseReader r = reader;
        if (r == null) {
            return Optional.empty();
        }
        return r.tryCountry(address)
                .map(response -> response.getCountry().getIsoCode())
                .filter(code -> code != null && !code.isBlank());
    }
}
