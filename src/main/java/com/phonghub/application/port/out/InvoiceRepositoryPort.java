package com.phonghub.application.port.out;

import com.phonghub.domain.model.Invoice;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepositoryPort {
    Invoice save(Invoice invoice);
    Optional<Invoice> findById(UUID id);
    List<Invoice> findByContractId(UUID contractId);
    /** Kỳ tiền thuê chưa bị hủy (status khác VOIDED) của hợp đồng trong tháng/năm. */
    Optional<Invoice> findActiveRentByContractIdAndPeriod(UUID contractId, int year, int month);
    /** Hóa đơn còn phải trả (chưa trả đủ, chưa hủy, chưa gộp) có hạn thanh toán trong [from, to] (#86). */
    List<Invoice> findPayableDueBetween(LocalDate from, LocalDate to);

    /** Khoản phí sửa chữa chưa bị hủy của phiếu bảo trì. */
    Optional<Invoice> findActiveByTicketId(UUID ticketId);
    /** Khóa dòng (SELECT ... FOR UPDATE) khi chạy trong transaction để tránh cộng tiền đồng thời. */
    Optional<Invoice> findByPaymentCodeForUpdate(String paymentCode);
    boolean existsByPaymentCode(String paymentCode);
    /** Khóa dòng theo id (SELECT ... FOR UPDATE) để job chạy nền không ghi đè khoản tiền vừa nhận. */
    Optional<Invoice> findByIdForUpdate(UUID id);
    /** Payable invoices of a contract in stable id order, locked for an atomic checkout settlement. */
    default List<Invoice> findPayableByContractIdForUpdate(UUID contractId) {
        return findByContractId(contractId).stream().filter(Invoice::isPayable)
            .sorted(java.util.Comparator.comparing(Invoice::getId)).toList();
    }
    /** Id hóa đơn tiền thuê còn phải thanh toán, chưa gộp và đã quá hạn trước {@code today}. */
    List<UUID> findOverdueRentInvoiceIds(java.time.LocalDate today);

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
