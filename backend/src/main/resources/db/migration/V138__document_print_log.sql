CREATE TABLE document_print_log (
    id         BINARY(16) NOT NULL,
    company_id BINARY(16) NOT NULL,

    doc_type        VARCHAR(20)  NOT NULL COMMENT 'DRS | LOADING_SHEET | THC',
    doc_ref         VARCHAR(64)  NOT NULL COMMENT 'manifest id for LOADING_SHEET/THC, DRS number for DRS',
    doc_number      VARCHAR(50)  NULL,
    printed_by      BINARY(16)   NOT NULL,
    printed_by_name VARCHAR(200) NULL,
    printed_at      TIMESTAMP(6) NOT NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    KEY idx_print_log_doc (company_id, doc_type, doc_ref)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'One row per print/reprint of a DRS, Loading Sheet or THC';
