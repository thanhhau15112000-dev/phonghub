package com.phonghub.application.service;

import com.phonghub.application.port.out.InvoiceRepositoryPort;
import com.phonghub.application.port.out.MaintenanceTicketRepositoryPort;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.Invoice;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;

/**
 * Phát hành hóa đơn tháng của một hợp đồng: tiền thuê cộng các khoản phí sửa chữa chưa trả đồng nào
 * (mỗi khoản một dòng chi tiết). Dùng chung cho thao tác tay của quản lý và tác vụ tự động.
 */
class MonthlyInvoiceIssuer {

    private final InvoiceRepositoryPort invoiceRepository;
    private final MaintenanceTicketRepositoryPort ticketRepository;

    MonthlyInvoiceIssuer(InvoiceRepositoryPort invoiceRepository, MaintenanceTicketRepositoryPort ticketRepository) {
        this.invoiceRepository = invoiceRepository;
        this.ticketRepository = ticketRepository;
    }

    boolean exists(Contract contract, YearMonth period) {
        return invoiceRepository
            .findActiveRentByContractIdAndPeriod(contract.getId(), period.getYear(), period.getMonthValue())
            .isPresent();
    }

    /** Người gọi đã kiểm tra quyền và kiểm tra hóa đơn tháng chưa tồn tại. */
    Invoice issue(Contract contract, YearMonth period) {
        List<Invoice> rollable = invoiceRepository.findByContractId(contract.getId()).stream()
            .filter(Invoice::isRollableFee)
            .sorted(Comparator.comparing(Invoice::getCreatedAt))
            .toList();
        List<Invoice.RolledFee> rolledFees = rollable.stream()
            .map(fee -> new Invoice.RolledFee(fee, ticketTitle(fee)))
            .toList();

        Invoice invoice = Invoice.issueMonthlyRent(contract, period, invoiceRepository.newUniquePaymentCode(), rolledFees);
        // Lưu hóa đơn tháng trước (các khoản phí tham chiếu tới nó), rồi cập nhật các khoản đã gộp
        invoiceRepository.save(invoice);
        rollable.forEach(invoiceRepository::save);
        return invoice;
    }

    private String ticketTitle(Invoice fee) {
        if (fee.getTicketId() == null) {
            return null;
        }
        return ticketRepository.findById(fee.getTicketId()).map(t -> t.getTitle()).orElse(null);
    }
}
