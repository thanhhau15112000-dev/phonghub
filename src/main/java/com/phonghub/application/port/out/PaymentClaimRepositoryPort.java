package com.phonghub.application.port.out;

import com.phonghub.domain.model.PaymentClaim;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentClaimRepositoryPort {
    PaymentClaim save(PaymentClaim claim);

    Optional<PaymentClaim> findById(UUID id);

    /** Khóa dòng khi chạy trong transaction để hai người xác nhận cùng lúc không cộng tiền hai lần. */
    Optional<PaymentClaim> findByIdForUpdate(UUID id);

    List<PaymentClaim> findByInvoiceId(UUID invoiceId);

    List<PaymentClaim> findByContractId(UUID contractId);

    /** Các yêu cầu chờ xác nhận của nhà trọ, cũ trước. */
    List<PaymentClaim> findPendingByPropertyId(UUID propertyId);
}
