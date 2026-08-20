-- V48 created pod_verification.pod_hash as CHAR(64); the entity
-- (PodVerification.podHash, plain @Column(length = 64)) maps a String to VARCHAR by
-- default, not CHAR — Hibernate's own schema-validation catches the mismatch on boot.
-- Corrective, forward-only fix rather than editing V48 in place.
ALTER TABLE pod_verification
    MODIFY COLUMN pod_hash VARCHAR(64) NULL;
