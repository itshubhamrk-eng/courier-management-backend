package com.courier.modules.security.application.scan;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScanCursorRepository extends JpaRepository<ScanCursor, Integer> {
}
