package com.courier.modules.support.domain;

import com.courier.shared.domain.CompanyOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * A file attached to a ticket — either at creation ({@link #messageId} null) or to a
 * specific reply.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ticket_attachments",
        indexes = @Index(name = "idx_ticket_attachments_ticket", columnList = "company_id, ticket_id"))
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class TicketAttachment extends CompanyOwnedEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "ticket_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID ticketId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "message_id", columnDefinition = "BINARY(16)")
    private UUID messageId;

    @Column(name = "asset_url", nullable = false, length = 1000)
    private String assetUrl;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "filename", nullable = false)
    private String filename;

    @Column(name = "content_type", length = 150)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "uploaded_by_user_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID uploadedByUserId;
}
