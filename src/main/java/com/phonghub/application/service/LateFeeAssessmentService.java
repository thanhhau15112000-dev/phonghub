package com.phonghub.application.service;

import com.phonghub.application.port.in.LateFeeAssessmentUseCase;
import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.application.port.out.InvoiceRepositoryPort;
import com.phonghub.application.port.out.LateFeePolicyRepositoryPort;
import com.phonghub.application.port.out.MaintenanceTicketRepositoryPort;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.InvoiceType;
import com.phonghub.domain.model.LateFeePolicy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class LateFeeAssessmentService implements LateFeeAssessmentUseCase {

    private final InvoiceRepositoryPort invoiceRepository;
    private final ContractRepositoryPort contractRepository;
    private final LateFeePolicyRepositoryPort policyRepository;
    private final AuditPort auditPort;
    private final MaintenanceTicketRepositoryPort ticketRepository;
    private final MaintenanceNotifier notifier;

    public LateFeeAssessmentService(
        InvoiceRepositoryPort invoiceRepository,
        ContractRepositoryPort contractRepository,
        LateFeePolicyRepositoryPort policyRepository,
        AuditPort auditPort
    ) {
        this(invoiceRepository, contractRepository, policyRepository, auditPort, null, null);
    }

    public LateFeeAssessmentService(
        InvoiceRepositoryPort invoiceRepository,
        ContractRepositoryPort contractRepository,
        LateFeePolicyRepositoryPort policyRepository,
        AuditPort auditPort,
        MaintenanceTicketRepositoryPort ticketRepository,
        MaintenanceNotifier notifier
    ) {
        this.invoiceRepository = invoiceRepository;
        this.contractRepository = contractRepository;
        this.policyRepository = policyRepository;
        this.auditPort = auditPort;
        this.ticketRepository = ticketRepository;
        this.notifier = notifier;
    }

    @Override
    public List<UUID> findInvoicesToAssess(LocalDate today) {
        List<UUID> ids = new java.util.ArrayList<>(invoiceRepository.findOverdueRentInvoiceIds(today));
        ids.addAll(invoiceRepository.findOverdueMaintenanceInvoiceIds(today));
        return ids;
    }

    @Override
    public boolean assessInvoice(UUID invoiceId, LocalDate today) {
        // Khóa dòng để một khoản tiền vừa nhận qua webhook không bị job này ghi đè.
        Optional<Invoice> locked = invoiceRepository.findByIdForUpdate(invoiceId);
        if (locked.isEmpty() || !locked.get().isOverdue(today)) {
            return false;
        }
        Invoice invoice = locked.get();
        if (invoice.getType() == InvoiceType.MAINTENANCE) {
            return assessMaintenanceFee(invoice, today);
        }
        Optional<Contract> contract = contractRepository.findById(invoice.getContractId());
        if (contract.isEmpty()) {
            return false;
        }
        UUID propertyId = contract.get().getPropertyId();
        Optional<LateFeePolicy> policy = policyRepository.findByPropertyId(propertyId).filter(LateFeePolicy::enabled);
        if (policy.isEmpty()) {
            return false;
        }

        long daysOverdue = ChronoUnit.DAYS.between(invoice.getDueDate(), today);
        long penaltyDays = policy.get().penaltyDays(daysOverdue);
        BigDecimal fee = policy.get().feeForDays(invoice.principalAmount(), daysOverdue);
        if (!invoice.applyLateFee(fee, penaltyDays)) {
            return false;
        }
        invoiceRepository.save(invoice);
        auditPort.recordEvent(AuditPort.AuditEvent.of("LATE_FEE_APPLIED", null, "INVOICE", invoice.getId().toString(),
            Map.of("propertyId", propertyId.toString(), "amount", fee.toPlainString(), "daysOverdue", daysOverdue,
                "penaltyDays", penaltyDays, "feeType", policy.get().feeType().name())));
        return true;
    }

    /** Phí sửa chữa người thuê chịu: phạt 10% tổng tiền sửa chữa một lần, không phụ thuộc cấu hình phạt của nhà trọ. */
    private boolean assessMaintenanceFee(Invoice invoice, LocalDate today) {
        BigDecimal before = invoice.getTotalAmount();
        if (!invoice.applyMaintenanceLateFee(today)) {
            return false;
        }
        invoiceRepository.save(invoice);
        auditPort.recordEvent(AuditPort.AuditEvent.of("LATE_FEE_APPLIED", null, "INVOICE", invoice.getId().toString(),
            Map.of("invoiceType", InvoiceType.MAINTENANCE.name(), "amount",
                invoice.getTotalAmount().subtract(before).toPlainString(),
                "daysOverdue", ChronoUnit.DAYS.between(invoice.getDueDate(), today), "feeType", "PERCENT_10")));
        notifyMaintenancePenalty(invoice, invoice.getTotalAmount().subtract(before));
        return true;
    }

    /** Báo người thuê khoản phí sửa chữa đã bị cộng phạt trễ hạn; lỗi gửi thông báo không làm mất khoản phạt. */
    private void notifyMaintenancePenalty(Invoice invoice, BigDecimal penalty) {
        if (notifier == null || ticketRepository == null || invoice.getTicketId() == null) {
            return;
        }
        try {
            ticketRepository.findById(invoice.getTicketId()).ifPresent(ticket -> notifier.notifyTenant(ticket,
                "Đã cộng phạt trễ hạn phí sửa chữa", String.format(
                    "Phí sửa chữa của phiếu \"%s\" đã quá hạn, hệ thống cộng phạt 10%% (%,.0f đ). Tổng cần trả: %,.0f đ.",
                    ticket.getTitle(), penalty, invoice.remainingAmount())));
        } catch (RuntimeException ignored) {
            // thông báo là phụ
        }
    }
}
