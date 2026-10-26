package com.phonghub.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/** Immutable copy of one room asset at the time a condition baseline was recorded. */
public record RoomConditionItem(
    UUID id,
    UUID snapshotId,
    UUID assetId,
    int position,
    String name,
    int quantity,
    AssetStatus status,
    String note,
    BigDecimal estimatedValue
) {
    public RoomConditionItem {
        if (id == null || snapshotId == null || position < 0) {
            throw new IllegalArgumentException("Hạng mục hiện trạng cần mã, biên bản và thứ tự hợp lệ.");
        }
        if (name == null || name.isBlank() || name.trim().length() > RoomAsset.MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Tên tài sản hiện trạng không hợp lệ.");
        }
        if (quantity <= 0) throw new IllegalArgumentException("Số lượng tài sản phải lớn hơn 0.");
        if (status == null) throw new IllegalArgumentException("Cần chọn tình trạng tài sản.");
        if (note != null && note.length() > RoomAsset.MAX_NOTE_LENGTH) {
            throw new IllegalArgumentException("Ghi chú tài sản tối đa " + RoomAsset.MAX_NOTE_LENGTH + " ký tự.");
        }
        if (estimatedValue == null) estimatedValue = BigDecimal.ZERO;
        if (estimatedValue.signum() < 0 || estimatedValue.compareTo(RoomAsset.MAX_ESTIMATED_VALUE) > 0) {
            throw new IllegalArgumentException("Giá trị ước tính tài sản không hợp lệ.");
        }
        name = name.trim();
    }
}
