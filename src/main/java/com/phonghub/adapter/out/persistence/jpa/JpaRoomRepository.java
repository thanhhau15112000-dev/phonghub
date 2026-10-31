package com.phonghub.adapter.out.persistence.jpa;

import com.phonghub.application.port.out.RoomRepositoryPort;
import com.phonghub.domain.exception.OptimisticLockConflictException;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomPage;
import com.phonghub.domain.model.RoomSearchCriteria;
import com.phonghub.domain.model.RoomStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

/** Lưu phòng bằng JPA, khóa lạc quan qua cột version (@Version). Ghi xong flush ngay như các repository JDBC. */
@Transactional
public class JpaRoomRepository implements RoomRepositoryPort {

    private static final Sort FLOOR_THEN_NUMBER = Sort.by("floor", "roomNumber");

    private final RoomJpaRepository rooms;
    private final PropertyJpaRepository properties;
    private final EntityManager entityManager;

    JpaRoomRepository(RoomJpaRepository rooms, PropertyJpaRepository properties, EntityManager entityManager) {
        this.rooms = rooms;
        this.properties = properties;
        this.entityManager = entityManager;
    }

    @Override
    public Room save(Room room) {
        RoomEntity entity = rooms.findById(room.getId()).orElse(null);
        if (entity == null) {
            entity = new RoomEntity(room.getId(), properties.getReferenceById(room.getPropertyId()));
        } else if (!Objects.equals(entity.getVersion(), room.getVersion())) {
            throw new OptimisticLockConflictException("Room");
        }
        Long versionBefore = entity.getVersion();
        entity.apply(room);
        entity = OptimisticSave.saveAndFlush(rooms, entityManager, entity, versionBefore, RoomEntity::getVersion, "Room");
        room.restoreVersion(entity.getVersion());
        return room;
    }

    @Override
    public RoomPage search(RoomSearchCriteria c) {
        if (c.restrictToRoomIds() != null && c.restrictToRoomIds().isEmpty()) {
            return new RoomPage(List.of(), 0, c.page(), c.size());
        }
        Specification<RoomEntity> spec = matching(c);
        if (c.size() == null) {
            List<Room> all = toDomain(rooms.findAll(spec, FLOOR_THEN_NUMBER));
            return new RoomPage(all, all.size(), c.page(), null);
        }
        Page<RoomEntity> page = rooms.findAll(spec, PageRequest.of(c.page(), c.size(), FLOOR_THEN_NUMBER));
        return new RoomPage(toDomain(page.getContent()), page.getTotalElements(), c.page(), c.size());
    }

    /** Các điều kiện null thì bỏ qua; còn lại kết hợp bằng AND. */
    private static Specification<RoomEntity> matching(RoomSearchCriteria c) {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.equal(root.get("property").get("id"), c.propertyId()));
            where.add(c.archived() ? cb.isNotNull(root.get("archivedAt")) : cb.isNull(root.get("archivedAt")));
            if (c.status() != null) {
                where.add(cb.equal(root.get("status"), c.status()));
            }
            if (c.minPrice() != null) {
                where.add(cb.greaterThanOrEqualTo(root.get("basePrice"), c.minPrice()));
            }
            if (c.maxPrice() != null) {
                where.add(cb.lessThanOrEqualTo(root.get("basePrice"), c.maxPrice()));
            }
            if (c.floor() != null) {
                where.add(cb.equal(root.get("floor"), c.floor()));
            }
            if (c.restrictToRoomIds() != null) {
                where.add(root.get("id").in(c.restrictToRoomIds()));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    @Override
    public Optional<Room> findById(UUID id) {
        return rooms.findById(id).map(RoomEntity::toDomain);
    }

    @Override
    public List<Room> findByPropertyId(UUID propertyId) {
        return toDomain(rooms.findInUseByPropertyId(propertyId));
    }

    @Override
    public List<Room> findByPropertyIdAndStatus(UUID propertyId, RoomStatus status) {
        return toDomain(rooms.findInUseByPropertyIdAndStatus(propertyId, status));
    }

    @Override
    public Optional<Room> findByPropertyIdAndRoomNumber(UUID propertyId, String roomNumber) {
        return rooms.findByPropertyIdAndRoomNumberIgnoreCase(propertyId, roomNumber.trim()).map(RoomEntity::toDomain);
    }

    @Override
    public List<Room> findAllById(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return toDomain(rooms.findAllById(ids));
    }

    @Override
    public boolean existsById(UUID id) {
        return rooms.existsById(id);
    }

    private static List<Room> toDomain(List<RoomEntity> entities) {
        return entities.stream().map(RoomEntity::toDomain).toList();
    }
}
