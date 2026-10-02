package com.phonghub.application.port.out;

import com.phonghub.domain.model.Invoice;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepositoryPort {
    Invoice save(Invoice invoice);
    Optional<Invoice> findById(UUID id);
    List<Invoice> findByContractId(UUID contractId);
    /** Kỳ chưa bị hủy (status khác VOIDED) của hợp đồng trong tháng/năm. */
    Optional<Invoice> findActiveByContractIdAndPeriod(UUID contractId, int year, int month);
    /** Khóa dòng (SELECT ... FOR UPDATE) khi chạy trong transaction để tránh cộng tiền đồng thời. */
    Optional<Invoice> findByPaymentCodeForUpdate(String paymentCode);
    boolean existsByPaymentCode(String paymentCode);
}
