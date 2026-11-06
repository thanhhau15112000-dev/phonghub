package com.phonghub.adapter.out.persistence.jpa;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PropertyJpaRepository extends JpaRepository<PropertyEntity, UUID> {

    List<PropertyEntity> findAllByOrderByCreatedAtDesc();

    List<PropertyEntity> findByIdInOrderByCreatedAtDesc(Collection<UUID> ids);

    List<PropertyEntity> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
}
