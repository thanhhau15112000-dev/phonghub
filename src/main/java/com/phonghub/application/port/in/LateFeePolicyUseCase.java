package com.phonghub.application.port.in;

import com.phonghub.domain.model.LateFeePolicy;
import java.math.BigDecimal;
import java.util.UUID;

/** Owner (hoặc admin) cấu hình phạt trả trễ cho nhà trọ của mình. */
public interface LateFeePolicyUseCase {

    /** Chính sách hiện tại; chưa cấu hình thì trả về chính sách đang tắt. */
    LateFeePolicy getPolicy(UUID propertyId);

    LateFeePolicy updatePolicy(UpdateCommand command);

    /**
     * @param feeValue phần trăm (0-100) hoặc số tiền cố định tùy {@code feeType}
     * @param capAmount mức phạt tối đa; null là không đặt trần
     */
    record UpdateCommand(
        UUID propertyId,
        boolean enabled,
        LateFeePolicy.FeeType feeType,
        BigDecimal feeValue,
        int graceDays,
        BigDecimal capAmount
    ) {}
}
