package com.phonghub.adapter.out.persistence.jpa;

import com.phonghub.domain.model.ContractStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Mọi truy vấn nạp kèm danh sách người ở (một câu SQL) vì adapter chuyển sang domain sau khi đọc. */
interface ContractJpaRepository extends JpaRepository<ContractEntity, UUID> {

    @Override
    @EntityGraph(attributePaths = "occupants")
    Optional<ContractEntity> findById(UUID id);

    @EntityGraph(attributePaths = "occupants")
    @Query("select c from ContractEntity c order by c.createdAt desc")
    List<ContractEntity> findAllNewestFirst();

    @EntityGraph(attributePaths = "occupants")
    @Query("select c from ContractEntity c where c.property.id = :propertyId order by c.createdAt desc")
    List<ContractEntity> findByPropertyId(@Param("propertyId") UUID propertyId);

    @EntityGraph(attributePaths = "occupants")
    @Query("select c from ContractEntity c where c.room.id = :roomId order by c.createdAt desc")
    List<ContractEntity> findByRoomId(@Param("roomId") UUID roomId);

    @EntityGraph(attributePaths = "occupants")
    @Query("select c from ContractEntity c where c.primaryTenantId = :tenantId order by c.createdAt desc")
    List<ContractEntity> findByPrimaryTenantId(@Param("tenantId") UUID tenantId);

    /** Hợp đồng người thuê còn ở: chưa có ngày rời phòng hoặc ngày rời phòng sau hôm nay. */
    @EntityGraph(attributePaths = "occupants")
    @Query("""
        select distinct c from ContractEntity c join c.occupants o
        where o.tenantId = :tenantId and (o.checkOutDate is null or o.checkOutDate > current_date)
        order by c.createdAt desc
        """)
    List<ContractEntity> findByResidingOccupant(@Param("tenantId") UUID tenantId);

    /** Hợp đồng {@code preferred} xếp trước, sau đó mới nhất trước. */
    @EntityGraph(attributePaths = "occupants")
    @Query("""
        select c from ContractEntity c
        where c.room.id = :roomId and c.status in :statuses
        order by case when c.status = :preferred then 0 else 1 end, c.updatedAt desc
        """)
    List<ContractEntity> findByRoomIdAndStatusIn(
        @Param("roomId") UUID roomId,
        @Param("statuses") List<ContractStatus> statuses,
        @Param("preferred") ContractStatus preferred
    );
}
