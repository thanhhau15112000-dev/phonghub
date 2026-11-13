package com.phonghub.adapter.out.persistence.jpa;

import com.phonghub.domain.model.ContractOccupant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/** Cột created_at không ánh xạ: để mặc định của CSDL điền khi insert. */
@Entity
@Table(name = "contract_occupants")
class ContractOccupantEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false, updatable = false)
    private ContractEntity contract;

    /** Người thuê thuộc bảng tenants, chưa ánh xạ JPA nên giữ khóa ngoại dạng UUID. */
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "is_primary", nullable = false)
    private boolean primaryOccupant;

    @Column(name = "check_in_date", nullable = false)
    private LocalDate checkInDate;

    @Column(name = "check_out_date")
    private LocalDate checkOutDate;

    protected ContractOccupantEntity() {
    }

    ContractOccupantEntity(UUID id, ContractEntity contract, UUID tenantId) {
        this.id = id;
        this.contract = contract;
        this.tenantId = tenantId;
    }

    UUID getId() {
        return id;
    }

    void apply(ContractOccupant occupant) {
        this.primaryOccupant = occupant.isPrimary();
        this.checkInDate = occupant.checkInDate();
        this.checkOutDate = occupant.checkOutDate();
    }

    ContractOccupant toDomain() {
        return new ContractOccupant(id, contract.getId(), tenantId, primaryOccupant, checkInDate, checkOutDate);
    }
}
