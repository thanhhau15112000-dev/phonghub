package com.phonghub.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Bản chụp một tài sản của phòng trong biên bản. Check-in chụp từ tài sản hiện có của phòng; check-out khởi tạo từ
 * bản chụp check-in rồi nhân viên cập nhật tình trạng lúc trả và số tiền đề xuất khấu trừ.
 */
public record HandoverItem(
    UUID id,
    UUID handoverId,
    UUID assetId,
    String assetName,
    int quantity,
    AssetStatus status,
    String conditionNote,
    BigDecimal deductionAmount
) {
    public HandoverItem {
        if (id == null || handoverId == null || assetName == null || assetName.isBlank() || quantity <= 0
            || status == null) {
            throw new IllegalArgumentException("Handover item requires id, handover, name, positive quantity, status");
        }
        if (deductionAmount == null) {
            deductionAmount = BigDecimal.ZERO;
        }
        if (deductionAmount.signum() < 0) {
            throw new IllegalArgumentException("Khoản khấu trừ không được âm.");
        }
    }

    public HandoverItem(UUID id, UUID handoverId, UUID assetId, String assetName, int quantity, AssetStatus status,
                        String conditionNote) {
        this(id, handoverId, assetId, assetName, quantity, status, conditionNote, BigDecimal.ZERO);
    }

    public HandoverItem withCondition(AssetStatus newStatus, String note, BigDecimal deduction) {
        return new HandoverItem(id, handoverId, assetId, assetName, quantity, newStatus, note, deduction);
    }

    /** Tài sản hỏng hoặc mất nên phòng cần được sửa trước khi cho thuê lại. */
    public boolean needsRepair() {
        return status == AssetStatus.DAMAGED || status == AssetStatus.LOST;
    }
}
