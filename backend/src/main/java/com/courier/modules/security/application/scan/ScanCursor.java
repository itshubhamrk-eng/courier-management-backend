package com.courier.modules.security.application.scan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** The single row (id 1) recording where the last scan window ended. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "security_scan_cursor")
public class ScanCursor {

    public static final int SINGLETON_ID = 1;

    @Id
    @Column(name = "id")
    private Integer id;

    @Column(name = "last_scanned_at", nullable = false)
    private Instant lastScannedAt;
}
