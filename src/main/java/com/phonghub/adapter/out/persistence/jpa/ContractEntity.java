package com.phonghub.adapter.out.persistence.jpa;

import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractOccupant;
import com.phonghub.domain.model.ContractStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Entity
@Table(name = "contracts")
class ContractEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private PropertyEntity property;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private RoomEntity room;

    /** Người thuê thuộc bảng tenants, chưa ánh xạ JPA nên giữ khóa ngoại dạng UUID. */
    @Column(name = "primary_tenant_id", nullable = false)
    private UUID primaryTenantId;

    @Column(name = "deposit_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal depositAmount;

    @Column(name = "rent_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal rentAmount;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "payment_day", nullable = false)
    private int paymentDay;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ContractStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Null khi chưa lưu: Hibernate coi là bản ghi mới và gán 0 khi insert. */
    @Version
    private Long version;

    @OneToMany(mappedBy = "contract", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("primaryOccupant DESC, checkInDate ASC")
    private List<ContractOccupantEntity> occupants = new ArrayList<>();

    protected ContractEntity() {
    }

    ContractEntity(UUID id) {
        this.id = id;
    }

    UUID getId() {
        return id;
    }

    Long getVersion() {
        return version;
    }

    void apply(Contract contract, PropertyEntity property, RoomEntity room) {
        this.property = property;
        this.room = room;
        this.primaryTenantId = contract.getPrimaryTenantId();
        this.depositAmount = contract.getDepositAmount();
        this.rentAmount = contract.getRentAmount();
        this.startDate = contract.getStartDate();
        this.endDate = contract.getEndDate();
        this.paymentDay = contract.getPaymentDay();
        this.status = contract.getStatus();
        if (this.createdAt == null) {
            this.createdAt = contract.getCreatedAt();
        }
        this.updatedAt = contract.getUpdatedAt();
        syncOccupants(contract.getOccupants());
    }

    /**
     * Đồng bộ theo id thay vì xóa rồi thêm lại: Hibernate chạy INSERT trước DELETE khi flush, nên thay cả danh sách
     * sẽ vấp ràng buộc duy nhất (contract_id, tenant_id).
     */
    private void syncOccupants(List<ContractOccupant> desired) {
        Map<UUID, ContractOccupantEntity> current = occupants.stream()
            .collect(Collectors.toMap(ContractOccupantEntity::getId, Function.identity()));
        occupants.removeIf(entity -> desired.stream().noneMatch(o -> o.id().equals(entity.getId())));
        for (ContractOccupant occupant : desired) {
            ContractOccupantEntity entity = current.get(occupant.id());
            if (entity == null) {
                entity = new ContractOccupantEntity(occupant.id(), this, occupant.tenantId());
                occupants.add(entity);
            }
            entity.apply(occupant);
        }
    }

    Contract toDomain() {
        Contract contract = new Contract(id, property.getId(), room.getId(), primaryTenantId, depositAmount,
            rentAmount, startDate, endDate, paymentDay, status,
            occupants.stream().map(ContractOccupantEntity::toDomain).toList(), createdAt, updatedAt);
        contract.restoreVersion(version);
        return contract;
    }
}
