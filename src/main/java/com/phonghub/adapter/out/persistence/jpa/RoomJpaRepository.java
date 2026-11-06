package com.phonghub.adapter.out.persistence.jpa;

import com.phonghub.domain.model.RoomStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RoomJpaRepository extends JpaRepository<RoomEntity, UUID>, JpaSpecificationExecutor<RoomEntity> {

    @Query("""
        select r from RoomEntity r
        where r.property.id = :propertyId and r.archivedAt is null
        order by r.roomNumber asc
        """)
    List<RoomEntity> findInUseByPropertyId(@Param("propertyId") UUID propertyId);

    @Query("""
        select r from RoomEntity r
        where r.property.id = :propertyId and r.status = :status and r.archivedAt is null
        order by r.roomNumber asc
        """)
    List<RoomEntity> findInUseByPropertyIdAndStatus(
        @Param("propertyId") UUID propertyId,
        @Param("status") RoomStatus status
    );

    /** Kể cả phòng đã ngừng sử dụng; so khớp số phòng không phân biệt hoa thường. */
    @Query("""
        select r from RoomEntity r
        where r.property.id = :propertyId and lower(r.roomNumber) = lower(:roomNumber)
        """)
    Optional<RoomEntity> findByPropertyIdAndRoomNumberIgnoreCase(
        @Param("propertyId") UUID propertyId,
        @Param("roomNumber") String roomNumber
    );
}
