package com.phonghub.application.port.out;

import com.phonghub.domain.model.Invoice;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepositoryPort {
    Invoice save(Invoice invoice);
    Optional<Invoice> findById(UUID id);
    List<Invoice> findByContractId(UUID contractId);
    /** Kỳ tiền thuê chưa bị hủy (status khác VOIDED) của hợp đồng trong tháng/năm. */
    Optional<Invoice> findActiveRentByContractIdAndPeriod(UUID contractId, int year, int month);
    /** Khoản phí sửa chữa chưa bị hủy của phiếu bảo trì. */
    Optional<Invoice> findActiveByTicketId(UUID ticketId);
    /** Khóa dòng (SELECT ... FOR UPDATE) khi chạy trong transaction để tránh cộng tiền đồng thời. */
    Optional<Invoice> findByPaymentCodeForUpdate(String paymentCode);
    boolean existsByPaymentCode(String paymentCode);

    default String newUniquePaymentCode() {
        for (int i = 0; i < 10; i++) {
            String code = com.phonghub.domain.model.PaymentCode.generate();
            if (!existsByPaymentCode(code)) {
                return code;
            }
        }
        throw new com.phonghub.domain.exception.DomainException("Không tạo được mã thanh toán duy nhất, vui lòng thử lại.");
    }
}
