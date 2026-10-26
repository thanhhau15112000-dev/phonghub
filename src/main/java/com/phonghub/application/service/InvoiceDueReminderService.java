package com.phonghub.application.service;

import com.phonghub.application.port.in.InvoiceDueReminderUseCase;
import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.application.port.out.InvoiceReminderPort;
import com.phonghub.application.port.out.InvoiceRepositoryPort;
import com.phonghub.application.port.out.PropertyRepositoryPort;
import com.phonghub.application.port.out.RoomRepositoryPort;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.InvoiceType;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.Room;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** Nhắc người thuê (chính và ở cùng, có tài khoản) hóa đơn sắp đến hạn, mỗi hạn thanh toán một lần (#86). */
public class InvoiceDueReminderService implements InvoiceDueReminderUseCase {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final InvoiceRepositoryPort invoiceRepository;
    private final InvoiceReminderPort reminderPort;
    private final ContractRepositoryPort contractRepository;
    private final RoomRepositoryPort roomRepository;
    private final PropertyRepositoryPort propertyRepository;
    private final NotificationDispatcher dispatcher;
    private final int reminderDays;

    public InvoiceDueReminderService(
        InvoiceRepositoryPort invoiceRepository,
        InvoiceReminderPort reminderPort,
        ContractRepositoryPort contractRepository,
        RoomRepositoryPort roomRepository,
        PropertyRepositoryPort propertyRepository,
        NotificationDispatcher dispatcher,
        int reminderDays
    ) {
        if (reminderDays < 0) {
            throw new IllegalArgumentException("Số ngày nhắc trước hạn thanh toán không được âm");
        }
        this.invoiceRepository = Objects.requireNonNull(invoiceRepository, "invoiceRepository");
        this.reminderPort = Objects.requireNonNull(reminderPort, "reminderPort");
        this.contractRepository = Objects.requireNonNull(contractRepository, "contractRepository");
        this.roomRepository = Objects.requireNonNull(roomRepository, "roomRepository");
        this.propertyRepository = Objects.requireNonNull(propertyRepository, "propertyRepository");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.reminderDays = reminderDays;
    }

    @Override
    public int runDaily(LocalDate today) {
        int reminded = 0;
        for (Invoice invoice : invoiceRepository.findPayableDueBetween(today, today.plusDays(reminderDays))) {
            Contract contract = contractRepository.findById(invoice.getContractId()).orElse(null);
            if (contract == null || !reminderPort.markDueReminder(invoice.getId(), invoice.getDueDate())) {
                continue;
            }
            long days = ChronoUnit.DAYS.between(today, invoice.getDueDate());
            String message = String.format("%s (%s) còn %,.0f đ, hạn thanh toán %s%s.",
                label(invoice), describe(contract), invoice.remainingAmount(), invoice.getDueDate().format(DATE),
                days == 0 ? " (hôm nay)" : " (còn " + days + " ngày)");
            dispatcher.notifyTenants(contract, true, "Hóa đơn sắp đến hạn", message,
                "/invoices/" + invoice.getId() + "/pay");
            reminded++;
        }
        return reminded;
    }

    private static String label(Invoice invoice) {
        return switch (invoice.getType()) {
            case MAINTENANCE -> "Phí sửa chữa";
            case CHECK_OUT -> "Hóa đơn chốt điện/nước";
            case CANCELLATION -> "Phí hủy hợp đồng";
            case RENT -> String.format("Hóa đơn kỳ %02d/%d", invoice.getMonth(), invoice.getYear());
        };
    }

    private String describe(Contract contract) {
        String room = roomRepository.findById(contract.getRoomId()).map(Room::getRoomNumber).orElse("?");
        String property = propertyRepository.findById(contract.getPropertyId()).map(Property::name).orElse("?");
        return "phòng " + room + " - " + property;
    }
}
