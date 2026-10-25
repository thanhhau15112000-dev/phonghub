package com.phonghub.application.service;

import com.phonghub.application.port.out.InvoiceRepositoryPort;
import com.phonghub.application.port.out.MaintenanceTicketRepositoryPort;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.Invoice;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;

/**
 * Phát hành hóa đơn tháng của một hợp đồng: tiền thuê cộng các khoản phí sửa chữa chưa trả đồng nào
 * và tiền điện, nước, dịch vụ của kỳ (mỗi khoản một dòng chi tiết). Dùng chung cho thao tác tay của quản lý và tác vụ tự động.
 */
class MonthlyInvoiceIssuer {

    private final InvoiceRepositoryPort invoiceRepository;
    private final MaintenanceTicketRepositoryPort ticketRepository;

    private final UtilityBilling utilityBilling;

    MonthlyInvoiceIssuer(
        InvoiceRepositoryPort invoiceRepository,
        MaintenanceTicketRepositoryPort ticketRepository,
        UtilityBilling utilityBilling
    ) {
        this.invoiceRepository = invoiceRepository;
        this.ticketRepository = ticketRepository;
        this.utilityBilling = utilityBilling;
    }

    boolean exists(Contract contract, YearMonth period) {
        return invoiceRepository
            .findActiveRentByContractIdAndPeriod(contract.getId(), period.getYear(), period.getMonthValue())
            .isPresent();
    }

    /**
     * Người gọi đã kiểm tra quyền và kiểm tra hóa đơn tháng chưa tồn tại. {@code issuedOn} là ngày tạo theo giờ Việt
     * Nam, dùng để hạn thanh toán không rơi vào trước hoặc ngay ngày tạo.
     */
    Invoice issue(Contract contract, YearMonth period, LocalDate issuedOn) {
        List<Invoice> rollable = invoiceRepository.findByContractId(contract.getId()).stream()
            .filter(Invoice::isRollableFee)
            .sorted(Comparator.comparing(Invoice::getCreatedAt))
            .toList();
        List<Invoice.RolledFee> rolledFees = rollable.stream()
            .map(fee -> new Invoice.RolledFee(fee, ticketTitle(fee)))
            .toList();

        Invoice invoice = Invoice.issueMonthlyRent(
            contract, period, invoiceRepository.newUniquePaymentCode(), rolledFees, issuedOn);
        // Chỉ số điện nước đã có của kỳ này. Thiếu thì vẫn phát hành tiền thuê; scheduler bổ sung khi chỉ số được ghi.
        invoice.addCharges(utilityBilling.chargesFor(contract, period, true));
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
