package com.phonghub.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Chỉ số điện hoặc nước của một phòng trong một kỳ (tháng). Tiêu thụ = chỉ số mới - chỉ số kỳ trước.
 * Khi thay đồng hồ, chỉ số mới có thể nhỏ hơn chỉ số cũ: tiêu thụ = (chỉ số cuối đồng hồ cũ - chỉ số kỳ trước)
 * + (chỉ số mới - chỉ số bắt đầu của đồng hồ mới), và bắt buộc có ghi chú.
 */
public record MeterReading(
    UUID id,
    UUID roomId,
    UUID contractId,
    MeterType meterType,
    int periodYear,
    int periodMonth,
    BigDecimal previousValue,
    BigDecimal currentValue,
    boolean replaced,
    BigDecimal oldMeterFinalValue,
    BigDecimal newMeterStartValue,
    String replacementNote,
    BigDecimal consumption,
    LocalDate readingDate,
    String photoKey,
    Instant createdAt,
    UUID createdBy,
    Instant updatedAt,
    UUID updatedBy
) {
    public MeterReading {
        if (id == null || roomId == null || meterType == null || readingDate == null) {
            throw new IllegalArgumentException("Meter reading requires id, room, type and date");
        }
        if (periodMonth < 1 || periodMonth > 12 || periodYear < 2020) {
            throw new IllegalArgumentException("Invalid billing period");
        }
        if (previousValue == null || previousValue.signum() < 0 || currentValue == null || currentValue.signum() < 0) {
            throw new IllegalArgumentException("Meter values must be zero or more");
        }
        if (consumption == null || consumption.signum() < 0) {
            throw new IllegalArgumentException("Consumption cannot be negative");
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    public YearMonth period() {
        return YearMonth.of(periodYear, periodMonth);
    }

    /** Tạo bản ghi mới và tính tiêu thụ; ném IllegalArgumentException nếu chỉ số không hợp lệ. */
    public static MeterReading record(
        UUID roomId,
        UUID contractId,
        MeterType type,
        LocalDate readingDate,
        BigDecimal previous,
        BigDecimal current,
        ReplacementInfo replacement,
        UUID actorId
    ) {
        if (previous == null || current == null) {
            throw new IllegalArgumentException("Previous and current values are required");
        }
        BigDecimal consumption;
        boolean replaced = replacement != null;
        if (!replaced) {
            if (current.compareTo(previous) < 0) {
                throw new IllegalArgumentException(
                    "Chỉ số mới không được nhỏ hơn chỉ số kỳ trước; nếu đã thay đồng hồ hãy khai báo thay đồng hồ kèm ghi chú");
            }
            consumption = current.subtract(previous);
        } else {
            if (replacement.note() == null || replacement.note().isBlank()) {
                throw new IllegalArgumentException("Thay đồng hồ phải có ghi chú");
            }
            if (replacement.oldFinalValue() == null || replacement.newStartValue() == null) {
                throw new IllegalArgumentException("Thay đồng hồ cần chỉ số cuối đồng hồ cũ và chỉ số bắt đầu đồng hồ mới");
            }
            if (replacement.oldFinalValue().compareTo(previous) < 0) {
                throw new IllegalArgumentException("Chỉ số cuối đồng hồ cũ không được nhỏ hơn chỉ số kỳ trước");
            }
            if (replacement.newStartValue().signum() < 0 || current.compareTo(replacement.newStartValue()) < 0) {
                throw new IllegalArgumentException("Chỉ số mới không được nhỏ hơn chỉ số bắt đầu của đồng hồ mới");
            }
            consumption = replacement.oldFinalValue().subtract(previous)
                .add(current.subtract(replacement.newStartValue()));
        }
        YearMonth period = YearMonth.from(readingDate);
        Instant now = Instant.now();
        return new MeterReading(UUID.randomUUID(), roomId, contractId, type, period.getYear(),
            period.getMonthValue(), previous, current, replaced,
            replaced ? replacement.oldFinalValue() : null, replaced ? replacement.newStartValue() : null,
            replaced ? replacement.note().trim() : null, consumption, readingDate, null, now, actorId, now, actorId);
    }

    public MeterReading withPhoto(String key, UUID actorId) {
        return new MeterReading(id, roomId, contractId, meterType, periodYear, periodMonth, previousValue,
            currentValue, replaced, oldMeterFinalValue, newMeterStartValue, replacementNote, consumption,
            readingDate, key, createdAt, createdBy, Instant.now(), actorId);
    }

    public record ReplacementInfo(BigDecimal oldFinalValue, BigDecimal newStartValue, String note) {}
}
