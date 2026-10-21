package com.phonghub.domain.model;

import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.exception.InvalidContractStateException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class Contract {
    private final UUID id;
    private final UUID propertyId;
    private final UUID roomId;
    private final UUID primaryTenantId;
    private BigDecimal depositAmount;
    private BigDecimal rentAmount;
    private LocalDate startDate;
    private LocalDate endDate;
    private int paymentDay;
    private ContractStatus status;
    private final List<ContractOccupant> occupants;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;

    public Contract(
        UUID id,
        UUID propertyId,
        UUID roomId,
        UUID primaryTenantId,
        BigDecimal depositAmount,
        BigDecimal rentAmount,
        LocalDate startDate,
        LocalDate endDate,
        int paymentDay,
        ContractStatus status,
        List<ContractOccupant> occupants,
        Instant createdAt,
        Instant updatedAt
    ) {
        if (id == null) {
            throw new IllegalArgumentException("Contract id cannot be null");
        }
        if (propertyId == null) {
            throw new IllegalArgumentException("Property id cannot be null");
        }
        if (roomId == null) {
            throw new IllegalArgumentException("Room id cannot be null");
        }
        if (primaryTenantId == null) {
            throw new IllegalArgumentException("Primary tenant id cannot be null");
        }
        if (depositAmount == null || depositAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Deposit amount cannot be negative");
        }
        if (rentAmount == null || rentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Rent amount must be greater than zero");
        }
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Start date and end date cannot be null");
        }
        if (!endDate.isAfter(startDate)) {
            throw new IllegalArgumentException("End date must be after start date");
        }
        if (paymentDay < 1 || paymentDay > 31) {
            throw new IllegalArgumentException("Payment day must be between 1 and 31");
        }

        this.id = id;
        this.propertyId = propertyId;
        this.roomId = roomId;
        this.primaryTenantId = primaryTenantId;
        this.depositAmount = depositAmount;
        this.rentAmount = rentAmount;
        this.startDate = startDate;
        this.endDate = endDate;
        this.paymentDay = paymentDay;
        this.status = status != null ? status : ContractStatus.DRAFT;
        this.occupants = new ArrayList<>();
        if (occupants != null) {
            this.occupants.addAll(occupants);
        }
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
    }

    public static Contract create(
        UUID propertyId,
        UUID roomId,
        UUID primaryTenantId,
        BigDecimal depositAmount,
        BigDecimal rentAmount,
        LocalDate startDate,
        LocalDate endDate,
        int paymentDay
    ) {
        UUID contractId = UUID.randomUUID();
        Instant now = Instant.now();
        Contract contract = new Contract(
            contractId,
            propertyId,
            roomId,
            primaryTenantId,
            depositAmount,
            rentAmount,
            startDate,
            endDate,
            paymentDay,
            ContractStatus.DRAFT,
            new ArrayList<>(),
            now,
            now
        );
        // Add primary occupant by default
        contract.addOccupant(primaryTenantId, true, startDate);
        return contract;
    }

    public ContractOccupant addOccupant(UUID tenantId, boolean isPrimary, LocalDate checkInDate) {
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant id cannot be null");
        }
        boolean exists = occupants.stream().anyMatch(o -> o.tenantId().equals(tenantId));
        if (exists) {
            throw new DomainException("Tenant is already an occupant in this contract");
        }
        if (isPrimary && occupants.stream().anyMatch(ContractOccupant::isPrimary)) {
            throw new DomainException("Contract already has a primary occupant");
        }
        ContractOccupant occupant = new ContractOccupant(
            UUID.randomUUID(),
            this.id,
            tenantId,
            isPrimary,
            checkInDate != null ? checkInDate : this.startDate,
            null
        );
        this.occupants.add(occupant);
        this.updatedAt = Instant.now();
        return occupant;
    }

    public ContractOccupant checkOutOccupant(UUID tenantId, LocalDate checkOutDate) {
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant id cannot be null");
        }
        // Hợp đồng đã hết hạn nhưng chưa trả phòng (#87) vẫn có người ở nên vẫn ghi nhận được người ở cùng rời đi
        if (!isResiding()) {
            throw new DomainException(String.format(
                "Chỉ có thể ghi nhận rời phòng cho hợp đồng còn người ở (đang hiệu lực hoặc đã hết hạn nhưng chưa trả "
                    + "phòng). Trạng thái hiện tại: '%s'.",
                this.status
            ));
        }
        if (checkOutDate == null) {
            checkOutDate = LocalDate.now();
        }

        int targetIndex = -1;
        ContractOccupant target = null;
        for (int i = 0; i < this.occupants.size(); i++) {
            ContractOccupant o = this.occupants.get(i);
            if (o.tenantId().equals(tenantId)) {
                targetIndex = i;
                target = o;
                break;
            }
        }

        if (target == null) {
            throw new DomainException(String.format(
                "Người thuê '%s' không có trong danh sách người ở của hợp đồng này.",
                tenantId
            ));
        }

        if (target.isPrimary()) {
            throw new com.phonghub.domain.exception.PrimaryOccupantRemovalException(
                "Không thể gỡ người thuê chính khỏi hợp đồng. Muốn người thuê chính rời đi phải chấm dứt toàn bộ hợp đồng."
            );
        }

        if (target.checkOutDate() != null) {
            throw new DomainException(String.format(
                "Người thuê này đã được ghi nhận rời phòng vào ngày %s.",
                target.checkOutDate()
            ));
        }

        if (checkOutDate.isBefore(target.checkInDate())) {
            throw new IllegalArgumentException(String.format(
                "Ngày rời phòng (%s) không được trước ngày nhận phòng (%s).",
                checkOutDate, target.checkInDate()
            ));
        }

        ContractOccupant updated = target.withCheckOutDate(checkOutDate);
        this.occupants.set(targetIndex, updated);
        this.updatedAt = Instant.now();
        return updated;
    }

    /**
     * Sửa điều khoản hợp đồng nháp (#89). Ngày vào ở của người ở trùng ngày bắt đầu cũ được dời theo ngày bắt đầu mới.
     */
    public void updateDraftTerms(
        BigDecimal newDeposit,
        BigDecimal newRent,
        LocalDate newStartDate,
        LocalDate newEndDate,
        int newPaymentDay
    ) {
        if (this.status != ContractStatus.DRAFT) {
            throw new InvalidContractStateException(String.format(
                "Chỉ sửa được hợp đồng ở trạng thái nháp. Trạng thái hiện tại: '%s'.", status));
        }
        if (newDeposit == null || newDeposit.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Tiền cọc không được âm.");
        }
        if (newRent == null || newRent.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Tiền thuê phải lớn hơn 0.");
        }
        if (newStartDate == null || newEndDate == null) {
            throw new IllegalArgumentException("Ngày bắt đầu và ngày kết thúc không được để trống.");
        }
        if (!newEndDate.isAfter(newStartDate)) {
            throw new IllegalArgumentException("Ngày kết thúc phải sau ngày bắt đầu.");
        }
        if (newPaymentDay < 1 || newPaymentDay > 31) {
            throw new IllegalArgumentException("Ngày thanh toán phải từ 1 đến 31.");
        }
        LocalDate oldStart = this.startDate;
        this.occupants.replaceAll(o -> o.checkOutDate() == null && o.checkInDate().equals(oldStart)
            ? new ContractOccupant(o.id(), o.contractId(), o.tenantId(), o.isPrimary(), newStartDate, null)
            : o);
        this.depositAmount = newDeposit;
        this.rentAmount = newRent;
        this.startDate = newStartDate;
        this.endDate = newEndDate;
        this.paymentDay = newPaymentDay;
        this.updatedAt = Instant.now();
    }

    /** Hủy hợp đồng nháp (#89): không xóa cứng, chuyển sang CANCELLED để còn lịch sử. */
    public void cancelDraft() {
        if (this.status != ContractStatus.DRAFT) {
            throw new InvalidContractStateException(String.format(
                "Chỉ hủy được hợp đồng ở trạng thái nháp. Trạng thái hiện tại: '%s'.", status));
        }
        this.status = ContractStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }

    public void activate() {
        if (this.status != ContractStatus.DRAFT) {
            throw new DomainException(String.format(
                "Contract %s cannot be activated because it is in status %s (must be DRAFT)",
                id, status
            ));
        }
        this.status = ContractStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    /** Kết thúc hợp đồng (trả phòng/chấm dứt); hợp đồng đã hết hạn nhưng chưa trả phòng cũng kết thúc qua đây. */
    public void terminate() {
        if (!isResiding()) {
            throw new DomainException(String.format(
                "Contract %s cannot be terminated because it is in status %s (must be ACTIVE or EXPIRED)",
                id, status
            ));
        }
        this.status = ContractStatus.TERMINATED;
        this.updatedAt = Instant.now();
    }

    /**
     * Gia hạn hợp đồng người thuê còn ở: dời ngày kết thúc, có thể đổi tiền thuê (áp dụng cho các kỳ hóa đơn phát
     * hành sau). Hợp đồng đã hết hạn mà chưa trả phòng (#87) trở lại hiệu lực.
     */
    public void renew(LocalDate newEndDate, BigDecimal newRent, LocalDate today) {
        if (!isResiding()) {
            throw new InvalidContractStateException(String.format(
                "Chỉ gia hạn được hợp đồng đang hiệu lực hoặc đã hết hạn nhưng chưa trả phòng. Trạng thái hiện tại: '%s'.",
                status));
        }
        if (newEndDate == null) {
            throw new IllegalArgumentException("Cần nhập ngày kết thúc mới.");
        }
        if (!newEndDate.isAfter(endDate)) {
            throw new IllegalArgumentException(String.format(
                "Ngày kết thúc mới phải sau ngày kết thúc hiện tại (%s).", endDate));
        }
        if (today != null && newEndDate.isBefore(today)) {
            throw new IllegalArgumentException("Ngày kết thúc mới không được trước hôm nay.");
        }
        if (newRent != null && newRent.signum() <= 0) {
            throw new IllegalArgumentException("Tiền thuê phải lớn hơn 0.");
        }
        this.endDate = newEndDate;
        if (newRent != null) {
            this.rentAmount = newRent;
        }
        if (this.status == ContractStatus.EXPIRED) {
            this.status = ContractStatus.ACTIVE;
        }
        this.updatedAt = Instant.now();
    }

    public void expire() {
        if (this.status != ContractStatus.ACTIVE) {
            throw new DomainException(String.format(
                "Contract %s cannot be expired because it is in status %s (must be ACTIVE)",
                id, status
            ));
        }
        this.status = ContractStatus.EXPIRED;
        this.updatedAt = Instant.now();
    }

    public boolean isActive() {
        return this.status == ContractStatus.ACTIVE;
    }

    /**
     * Người thuê còn ở phòng theo hợp đồng này: đang hiệu lực, hoặc đã hết hạn (#87) nhưng chưa check-out. Hết hạn
     * không tự trả phòng — phòng chỉ trống khi check-out được duyệt và hợp đồng chuyển TERMINATED.
     */
    public boolean isResiding() {
        return this.status == ContractStatus.ACTIVE || this.status == ContractStatus.EXPIRED;
    }

    /** Chưa kết thúc: nháp hoặc người thuê còn ở. Hồ sơ tenant chưa bị khóa/ẩn khi còn hợp đồng như vậy. */
    public boolean isOpen() {
        return this.status == ContractStatus.DRAFT || isResiding();
    }

    /** Hợp đồng đang hiệu lực đã qua ngày kết thúc (ngày kết thúc vẫn còn hiệu lực trọn ngày). */
    public boolean isPastEndDate(LocalDate today) {
        return isActive() && endDate != null && endDate.isBefore(today);
    }

    public UUID getId() {
        return id;
    }

    public UUID getPropertyId() {
        return propertyId;
    }

    public UUID getRoomId() {
        return roomId;
    }

    public UUID getPrimaryTenantId() {
        return primaryTenantId;
    }

    public BigDecimal getDepositAmount() {
        return depositAmount;
    }

    public BigDecimal getRentAmount() {
        return rentAmount;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public int getPaymentDay() {
        return paymentDay;
    }

    public ContractStatus getStatus() {
        return status;
    }

    public List<ContractOccupant> getOccupants() {
        return Collections.unmodifiableList(occupants);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Contract contract = (Contract) o;
        return Objects.equals(id, contract.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /** Phiên bản dùng cho khóa lạc quan; do tầng lưu trữ gán sau khi đọc/ghi. */
    public long getVersion() {
        return version;
    }

    public void restoreVersion(long version) {
        this.version = version;
    }
}
