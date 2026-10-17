package com.phonghub.application.port.out;

import com.phonghub.domain.model.PaymentTransaction;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepositoryPort {
    /**
     * Ghi giao dịch nếu sepayId chưa tồn tại. Trả về false khi trùng (SePay gửi lại webhook),
     * kể cả khi hai request trùng chạy đồng thời — dựa trên ràng buộc UNIQUE(sepay_id).
     */
    boolean saveIfNew(PaymentTransaction transaction);
    Optional<PaymentTransaction> findById(UUID id);
    Optional<PaymentTransaction> findBySepayId(Long sepayId);
    boolean existsBySepayId(Long sepayId);
    List<PaymentTransaction> findAll();
    /** Giao dịch đã khớp với các hợp đồng này, mới nhất trước (đối soát theo phạm vi nhà trọ, #82). */
    List<PaymentTransaction> findByContractIds(Collection<UUID> contractIds);
    /** Giao dịch được ghi nhận cho một hóa đơn, cũ nhất trước (biên lai, #82). */
    List<PaymentTransaction> findByInvoiceId(UUID invoiceId);
}
