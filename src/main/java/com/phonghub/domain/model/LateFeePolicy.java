package com.phonghub.domain.model;

import com.phonghub.domain.exception.DomainException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * Chính sách phạt trả trễ của một nhà trọ (#80). Phạt là một khoản duy nhất cho mỗi hóa đơn tiền thuê, bắt đầu
 * tính khi quá hạn hơn {@code graceDays} ngày: theo phần trăm tiền hóa đơn hoặc số tiền cố định, không vượt
 * {@code capAmount} nếu có đặt trần.
 */
public record LateFeePolicy(
    UUID propertyId,
    boolean enabled,
    FeeType feeType,
    BigDecimal feeValue,
    int graceDays,
    BigDecimal capAmount,
    Instant updatedAt,
    UUID updatedBy
) {

    public enum FeeType {
        PERCENT,
        FIXED
    }

    public static final int MAX_GRACE_DAYS = 365;

    public LateFeePolicy {
        if (propertyId == null) {
            throw new IllegalArgumentException("Property id cannot be null");
        }
        if (feeType == null) {
            feeType = FeeType.PERCENT;
        }
        if (feeValue == null) {
            feeValue = BigDecimal.ZERO;
        }
        if (feeValue.signum() < 0) {
            throw new DomainException("Mức phạt không được âm.");
        }
        if (feeType == FeeType.PERCENT && feeValue.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new DomainException("Phạt theo phần trăm không được vượt quá 100%.");
        }
        if (graceDays < 0 || graceDays > MAX_GRACE_DAYS) {
            throw new DomainException("Số ngày ân hạn phải từ 0 đến " + MAX_GRACE_DAYS + ".");
        }
        if (capAmount != null && capAmount.signum() < 0) {
            throw new DomainException("Mức phạt tối đa không được âm.");
        }
        if (enabled && feeValue.signum() == 0) {
            throw new DomainException("Bật phạt trả trễ thì mức phạt phải lớn hơn 0.");
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    public static LateFeePolicy disabled(UUID propertyId) {
        return new LateFeePolicy(propertyId, false, FeeType.PERCENT, BigDecimal.ZERO, 0, null, Instant.now(), null);
    }

    /**
     * Tiền phạt cho hóa đơn có gốc {@code principal} đã quá hạn {@code daysOverdue} ngày; 0 nếu chính sách tắt
     * hoặc chưa quá ngày ân hạn. Làm tròn đến đồng.
     */
    public BigDecimal feeFor(BigDecimal principal, long daysOverdue) {
        if (!enabled || principal == null || principal.signum() <= 0 || daysOverdue <= graceDays) {
            return BigDecimal.ZERO;
        }
        BigDecimal fee = feeType == FeeType.PERCENT
            ? principal.multiply(feeValue).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
            : feeValue.setScale(0, RoundingMode.HALF_UP);
        if (capAmount != null && fee.compareTo(capAmount) > 0) {
            fee = capAmount.setScale(0, RoundingMode.HALF_UP);
        }
        return fee;
    }
}
