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

    public MaintenanceFeeSettlement(InvoiceRepositoryPort invoiceRepository, MaintenanceTicketRepositoryPort ticketRepository) {
        this.invoiceRepository = invoiceRepository;
        this.ticketRepository = ticketRepository;
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
                });
        }
    }
}
