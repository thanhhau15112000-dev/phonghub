package com.phonghub.adapter.out.persistence.jpa;

import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rooms")
class RoomEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false, updatable = false)
    private PropertyEntity property;

    @Column(name = "room_number", nullable = false, length = 50)
    private String roomNumber;

    @Column(nullable = false)
    private int floor;

    @Column(name = "area_sqm", precision = 6, scale = 2)
    private BigDecimal areaSqm;

    @Column(name = "base_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal basePrice;

    @Column(name = "max_occupants", nullable = false)
    private int maxOccupants;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RoomStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    /** Null khi chưa lưu: Hibernate coi là bản ghi mới và gán 0 khi insert. */
    @Version
    private Long version;

    protected RoomEntity() {
    }

    RoomEntity(UUID id, PropertyEntity property) {
        this.id = id;
        this.property = property;
    }

    UUID getId() {
        return id;
    }

    Long getVersion() {
        return version;
    }

    void apply(Room room) {
        this.roomNumber = room.getRoomNumber();
        this.floor = room.getFloor();
        this.areaSqm = room.getAreaSqm();
        this.basePrice = room.getBasePrice();
        this.maxOccupants = room.getMaxOccupants();
        this.status = room.getStatus();
        if (this.createdAt == null) {
            this.createdAt = room.getCreatedAt();
        }
        this.updatedAt = room.getUpdatedAt();
        this.archivedAt = room.getArchivedAt();
    }

    Room toDomain() {
        Room room = new Room(id, property.getId(), roomNumber, floor, areaSqm, basePrice, maxOccupants, status,
            createdAt, updatedAt);
        room.restoreVersion(version);
        room.restoreArchivedAt(archivedAt);
        return room;
    }
}
