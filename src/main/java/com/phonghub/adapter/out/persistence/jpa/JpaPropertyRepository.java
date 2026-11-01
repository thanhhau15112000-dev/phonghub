package com.phonghub.adapter.out.persistence.jpa;

import com.phonghub.application.port.out.PropertyRepositoryPort;
import com.phonghub.domain.model.Property;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lưu nhà trọ bằng JPA. Ghi xong flush ngay để các repository JDBC chạy chung transaction đọc được dữ liệu mới
 * và khóa ngoại tới bảng properties không bị lỗi.
 */
@Transactional
public class JpaPropertyRepository implements PropertyRepositoryPort {

    private final PropertyJpaRepository properties;
    private final EntityManager entityManager;

    JpaPropertyRepository(PropertyJpaRepository properties, EntityManager entityManager) {
        this.properties = properties;
        this.entityManager = entityManager;
    }

    @Override
    public Property save(Property property) {
        PropertyEntity entity = properties.findById(property.id())
            .orElseGet(() -> new PropertyEntity(property.id()));
        entity.apply(property, Instant.now());
        properties.saveAndFlush(entity);
        return property;
    }

    @Override
    public Optional<Property> findById(UUID id) {
        return properties.findById(id).map(PropertyEntity::toDomain);
    }

    /** Khóa dòng nhà trọ (SELECT ... FOR UPDATE) tới hết transaction hiện tại. */
    @Override
    public void lockById(UUID id) {
        entityManager.find(PropertyEntity.class, id, LockModeType.PESSIMISTIC_WRITE);
    }

    @Override
    public List<Property> findAll() {
        return properties.findAllByOrderByCreatedAtDesc().stream().map(PropertyEntity::toDomain).toList();
    }

    @Override
    public List<Property> findAllById(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return properties.findByIdInOrderByCreatedAtDesc(ids).stream().map(PropertyEntity::toDomain).toList();
    }

    @Override
    public List<Property> findByOwnerId(UUID ownerId) {
        if (ownerId == null) {
            return List.of();
        }
        return properties.findByOwnerIdOrderByCreatedAtDesc(ownerId).stream().map(PropertyEntity::toDomain).toList();
    }

    @Override
    public boolean existsById(UUID id) {
        return properties.existsById(id);
    }

    @Override
    public void deleteById(UUID id) {
        properties.deleteById(id);
        properties.flush();
    }
}
