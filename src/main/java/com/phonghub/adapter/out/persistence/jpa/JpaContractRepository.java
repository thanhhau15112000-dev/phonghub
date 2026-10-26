package com.phonghub.adapter.out.persistence.jpa;

import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.domain.exception.OptimisticLockConflictException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lưu hợp đồng cùng danh sách người ở (cascade) bằng JPA, khóa lạc quan qua cột version. Ghi xong flush ngay để
 * các bảng JDBC tham chiếu tới hợp đồng (hóa đơn, biên bản) ghi được trong cùng transaction.
 */
@Transactional
public class JpaContractRepository implements ContractRepositoryPort {

    private final ContractJpaRepository contracts;
    private final PropertyJpaRepository properties;
    private final RoomJpaRepository rooms;
    private final EntityManager entityManager;

    JpaContractRepository(
        ContractJpaRepository contracts,
        PropertyJpaRepository properties,
        RoomJpaRepository rooms,
        EntityManager entityManager
    ) {
        this.contracts = contracts;
        this.properties = properties;
        this.rooms = rooms;
        this.entityManager = entityManager;
    }

    @Override
    public Contract save(Contract contract) {
        ContractEntity entity = contracts.findById(contract.getId()).orElse(null);
        if (entity == null) {
            entity = new ContractEntity(contract.getId());
        } else if (!Objects.equals(entity.getVersion(), contract.getVersion())) {
            throw new OptimisticLockConflictException("Hợp đồng");
        }
        Long versionBefore = entity.getVersion();
        entity.apply(contract, properties.getReferenceById(contract.getPropertyId()),
            rooms.getReferenceById(contract.getRoomId()));
        entity = OptimisticSave.saveAndFlush(contracts, entityManager, entity, versionBefore,
            ContractEntity::getVersion, "Hợp đồng");
        contract.restoreVersion(entity.getVersion());
        return contract;
    }

    @Override
    public Optional<Contract> findById(UUID id) {
        return contracts.findById(id).map(ContractEntity::toDomain);
    }

    /** Đọc lại bản mới nhất và khóa dòng (SELECT ... FOR UPDATE) tới hết transaction hiện tại. */
    @Override
    public Optional<Contract> findByIdForUpdate(UUID id) {
        ContractEntity cached = entityManager.find(ContractEntity.class, id);
        if (cached == null) {
            return Optional.empty();
        }
        entityManager.refresh(cached, LockModeType.PESSIMISTIC_WRITE);
        return Optional.of(cached.toDomain());
    }

    @Override
    public List<Contract> findByPropertyId(UUID propertyId) {
        return toDomain(contracts.findByPropertyId(propertyId));
    }

    @Override
    public List<Contract> findByRoomId(UUID roomId) {
        return toDomain(contracts.findByRoomId(roomId));
    }

    @Override
    public Optional<Contract> findActiveByRoomId(UUID roomId) {
        return contracts.findByRoomIdAndStatusIn(roomId, List.of(ContractStatus.ACTIVE, ContractStatus.EXPIRED),
                ContractStatus.ACTIVE).stream()
            .findFirst()
            .map(ContractEntity::toDomain);
    }

    @Override
    public List<Contract> findByPrimaryTenantId(UUID tenantId) {
        return toDomain(contracts.findByPrimaryTenantId(tenantId));
    }

    @Override
    public List<Contract> findByOccupantTenantId(UUID tenantId) {
        return toDomain(contracts.findByResidingOccupant(tenantId));
    }

    @Override
    public List<Contract> findAll() {
        return toDomain(contracts.findAllNewestFirst());
    }

    private static List<Contract> toDomain(List<ContractEntity> entities) {
        return entities.stream().map(ContractEntity::toDomain).toList();
    }
}
