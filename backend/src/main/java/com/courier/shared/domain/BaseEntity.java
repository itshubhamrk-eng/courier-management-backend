package com.courier.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Root of every persistent entity: UUID identity, audit columns, optimistic
 * locking and soft delete.
 *
 * <p>Entities that belong to a company must extend {@link CompanyOwnedEntity}
 * instead. Only platform-level tables ({@code companies}, {@code audit_logs})
 * extend this class directly.
 *
 * <p>The id is assigned in the constructor rather than by the database so that a
 * new aggregate has a stable identity before it is flushed — needed for emitting
 * events and building child references inside the same transaction.
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "id", columnDefinition = "BINARY(16)", updatable = false, nullable = false)
    private UUID id = TimeOrderedUuid.generate();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "created_by", columnDefinition = "BINARY(16)", updatable = false)
    private UUID createdBy;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @LastModifiedBy
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "updated_by", columnDefinition = "BINARY(16)")
    private UUID updatedBy;

    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "deleted_by", columnDefinition = "BINARY(16)")
    private UUID deletedBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /**
     * Marks this row deleted. Services must call this instead of
     * {@code repository.delete(...)}; nothing is ever physically removed.
     */
    public void softDelete(UUID actorId) {
        this.deleted = true;
        this.deletedAt = Instant.now();
        this.deletedBy = actorId;
    }

    public void restore() {
        this.deleted = false;
        this.deletedAt = null;
        this.deletedBy = null;
    }

    public boolean isNew() {
        return this.createdAt == null;
    }

    /**
     * Identity is the assigned UUID. Proxy-safe: a lazy proxy and its
     * initialised entity must compare equal, so the effective class is resolved
     * through {@link HibernateProxy} rather than using {@code getClass()}.
     */
    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }
        Class<?> thisType = effectiveClass(this);
        Class<?> otherType = effectiveClass(o);
        if (!thisType.equals(otherType)) {
            return false;
        }
        return this.id != null && this.id.equals(((BaseEntity) o).getId());
    }

    /**
     * Constant across the entity lifecycle because the id is never null and never
     * reassigned — safe to put a transient entity in a {@code HashSet}.
     */
    @Override
    public final int hashCode() {
        return Objects.hash(effectiveClass(this));
    }

    private static Class<?> effectiveClass(Object o) {
        return o instanceof HibernateProxy proxy
                ? proxy.getHibernateLazyInitializer().getPersistentClass()
                : o.getClass();
    }

    @Override
    public String toString() {
        return effectiveClass(this).getSimpleName() + "(id=" + id + ")";
    }
}
