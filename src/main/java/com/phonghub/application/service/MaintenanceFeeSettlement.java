package com.phonghub.application.service;

import com.phonghub.application.port.out.InvoiceRepositoryPort;
import com.phonghub.application.port.out.MaintenanceTicketRepositoryPort;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.InvoiceStatus;
import com.phonghub.domain.model.InvoiceType;
import com.phonghub.domain.model.MaintenanceStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Khi hóa đơn phí sửa chữa (riêng hoặc đã gộp vào hóa đơn tháng) được trả đủ thì phiếu bảo trì chờ thanh toán hoàn tất.
 * Dùng chung cho mọi đường ghi nhận tiền: webhook SePay và xác nhận thanh toán thủ công.
 */
public class MaintenanceFeeSettlement {

    private final InvoiceRepositoryPort invoiceRepository;
    private final MaintenanceTicketRepositoryPort ticketRepository;
    private final MaintenanceNotifier notifier;

    public MaintenanceFeeSettlement(InvoiceRepositoryPort invoiceRepository, MaintenanceTicketRepositoryPort ticketRepository) {
        this(invoiceRepository, ticketRepository, null);
    }

    public MaintenanceFeeSettlement(
        InvoiceRepositoryPort invoiceRepository,
        MaintenanceTicketRepositoryPort ticketRepository,
        MaintenanceNotifier notifier
    ) {
        this.invoiceRepository = invoiceRepository;
        this.ticketRepository = ticketRepository;
        this.notifier = notifier;
    }

    public void completeIfPaid(Invoice invoice) {
        if (invoice.getStatus() != InvoiceStatus.PAID || ticketRepository == null) {
            return;
        }
        // Hóa đơn phí riêng: phiếu của nó. Hóa đơn tháng: phiếu của các khoản phí đã gộp (dòng chi tiết).
        List<UUID> ticketIds = new ArrayList<>();
        if (invoice.getType() == InvoiceType.MAINTENANCE && invoice.getTicketId() != null) {
            ticketIds.add(invoice.getTicketId());
        }
        invoice.getItems().stream()
            .filter(item -> item.sourceInvoiceId() != null)
            .forEach(item -> invoiceRepository.findById(item.sourceInvoiceId())
                .map(Invoice::getTicketId)
                .ifPresent(ticketIds::add));

        for (UUID ticketId : ticketIds) {
            ticketRepository.findById(ticketId)
                .filter(ticket -> ticket.getStatus() == MaintenanceStatus.AWAITING_PAYMENT)
                .ifPresent(ticket -> {
                    ticket.markFeePaid();
                    ticketRepository.save(ticket);
                    notifyPaid(ticket);
                });
        }
    }

    /** Báo đã thu phí: người thuê mời nghiệm thu, chủ trọ/nhân viên và kỹ thuật viên được giao biết khoản đã thu. */
    private void notifyPaid(com.phonghub.domain.model.MaintenanceTicket ticket) {
        if (notifier == null) {
            return;
        }
        String title = "Đã thanh toán phí sửa chữa";
        notifier.notifyTenant(ticket, title, "Phí sửa chữa của phiếu \"" + ticket.getTitle()
            + "\" đã được ghi nhận thanh toán đủ. Mời bạn kiểm tra và nghiệm thu kết quả.");
        String message = "Đã thu phí sửa chữa của phiếu \"" + ticket.getTitle() + "\".";
        notifier.notifyManagers(ticket, title, message);
        notifier.notifyTechnician(ticket, title, message);
    }
}
