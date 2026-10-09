package com.phonghub.domain.model;

import java.util.UUID;

/** Bản chụp một tài sản của phòng tại thời điểm gửi biên bản, dùng để đối chiếu khi check-out. */
public record HandoverItem(
    UUID id,
    UUID handoverId,
    UUID assetId,
    String assetName,
    int quantity,
    AssetStatus status,
    String conditionNote
) {
    public HandoverItem {
        if (id == null || handoverId == null || assetName == null || assetName.isBlank() || quantity <= 0
            || status == null) {
            throw new IllegalArgumentException("Handover item requires id, handover, name, positive quantity, status");
        }
    }
}
