package com.phonghub.adapter.out.persistence.jpa;

import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.PropertyApprovalStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "properties")
class PropertyEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false)
    private String address;

    private String description;

    @Column(name = "total_rooms", nullable = false)
    private int totalRooms;

    /** Chủ trọ thuộc bảng users, chưa ánh xạ JPA nên giữ khóa ngoại dạng UUID. */
    @Column(name = "owner_id")
    private UUID ownerId;

    @Convert(converter = PropertyApprovalStatusConverter.class)
    @Column(name = "approval_status", nullable = false, length = 30)
    private PropertyApprovalStatus approvalStatus;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    protected PropertyEntity() {
    }

    PropertyEntity(UUID id) {
        this.id = id;
    }

    UUID getId() {
        return id;
    }

    void apply(Property property, Instant now) {
        this.name = property.name();
        this.address = property.address();
        this.description = property.description();
        this.totalRooms = property.totalRooms();
        this.ownerId = property.ownerId();
        this.approvalStatus = property.approvalStatus();
        this.rejectionReason = property.rejectionReason();
        if (this.createdAt == null) {
            this.createdAt = property.createdAt();
        }
        this.updatedAt = now;
        this.archivedAt = property.archivedAt();
    }

    Property toDomain() {
        return new Property(id, name, address, description, totalRooms, ownerId, approvalStatus, rejectionReason,
            createdAt, archivedAt);
    }
}
