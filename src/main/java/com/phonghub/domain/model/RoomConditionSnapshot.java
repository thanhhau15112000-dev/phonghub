package com.phonghub.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Immutable condition baseline used by room detail and the first check-in. */
public record RoomConditionSnapshot(
    UUID id,
    UUID roomId,
    BigDecimal electricMeter,
    BigDecimal waterMeter,
    String conditionNote,
    String electricMeterPhotoKey,
    String waterMeterPhotoKey,
    Instant createdAt,
    UUID createdBy
) {
    private static final BigDecimal MAX_METER_READING = new BigDecimal("99999999.99");

    public RoomConditionSnapshot {
        if (id == null || roomId == null || createdBy == null) {
            throw new IllegalArgumentException("Mốc hiện trạng cần mã, phòng và người tạo.");
        }
        validateMeter(electricMeter, "điện");
        validateMeter(waterMeter, "nước");
        if (conditionNote == null || conditionNote.isBlank() || conditionNote.trim().length() > 2000) {
            throw new IllegalArgumentException("Ghi chú hiện trạng phòng là bắt buộc (tối đa 2000 ký tự).");
        }
        conditionNote = conditionNote.trim();
        if (createdAt == null) createdAt = Instant.now();
    }

    public static void validateMeter(BigDecimal value, String kind) {
        if (value == null || value.signum() < 0 || value.compareTo(MAX_METER_READING) > 0
            || value.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Chỉ số " + kind + " phải từ 0 đến 99.999.999,99 và tối đa 2 số lẻ.");
        }
    }
}
