package com.phonghub.application.service;

import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.domain.exception.TenantProfileLockedException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractOccupant;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Hồ sơ tenant bị khóa (chỉ đọc) khi tenant đã check-out hết: không còn hợp đồng nháp/đang hiệu lực nào mà tenant
 * còn ở, và ít nhất một hợp đồng đã kết thúc. Trạng thái được suy ra từ hợp đồng nên tự mở khóa khi tenant ký hợp đồng mới.
 */
public class TenantLockPolicy {

    private final ContractRepositoryPort contractRepository;
    private final Duration retention;

    public TenantLockPolicy(ContractRepositoryPort contractRepository, Duration retention) {
        this.contractRepository = contractRepository;
        this.retention = retention;
    }

    /**
     * @param live còn hợp đồng nháp/đang hiệu lực mà tenant chưa rời đi
     * @param endedAt thời điểm check-out gần nhất; null nếu chưa từng kết thúc hợp đồng nào
     */
    public record LockInfo(boolean live, Instant endedAt, Instant purgeDueAt) {
        public boolean locked() {
            return !live && endedAt != null;
        }
    }

    public LockInfo lockInfo(UUID tenantId) {
        List<Contract> contracts = new ArrayList<>(contractRepository.findByPrimaryTenantId(tenantId));
        for (Contract contract : contractRepository.findByOccupantTenantId(tenantId)) {
            if (contracts.stream().noneMatch(existing -> existing.getId().equals(contract.getId()))) {
                contracts.add(contract);
            }
        }
        Instant endedAt = null;
        for (Contract contract : contracts) {
            Instant leftAt = contract.getOccupants().stream()
                .filter(occupant -> occupant.tenantId().equals(tenantId))
                .map(ContractOccupant::checkOutDate)
                .filter(java.util.Objects::nonNull)
                .map(date -> date.atStartOfDay(ZoneOffset.UTC).toInstant())
                .findFirst().orElse(null);
            // Hết hạn mà chưa check-out (#87) vẫn đang mở: không khóa/xóa hồ sơ người còn ở.
            boolean open = contract.isOpen();
            if (open && leftAt == null) {
                return new LockInfo(true, null, null);
            }
            Instant end = leftAt != null ? leftAt
                : (open ? null : contract.getUpdatedAt());
            if (end != null && (endedAt == null || end.isAfter(endedAt))) {
                endedAt = end;
            }
        }
        return new LockInfo(false, endedAt, endedAt == null ? null : endedAt.plus(retention));
    }

    public void assertNotLocked(UUID tenantId) {
        if (lockInfo(tenantId).locked()) {
            throw new TenantProfileLockedException(
                "Hồ sơ người thuê đã khóa (chỉ đọc) vì đã check-out; chỉ mở lại khi có hợp đồng mới.");
        }
    }
}
