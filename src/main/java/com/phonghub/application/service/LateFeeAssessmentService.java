package com.phonghub.application.service;

import com.phonghub.application.port.in.LateFeeAssessmentUseCase;
import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.application.port.out.InvoiceRepositoryPort;
import com.phonghub.application.port.out.LateFeePolicyRepositoryPort;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.Invoice;
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

    public LateFeeAssessmentService(
        InvoiceRepositoryPort invoiceRepository,
        ContractRepositoryPort contractRepository,
        LateFeePolicyRepositoryPort policyRepository,
        AuditPort auditPort
    ) {
        this.invoiceRepository = invoiceRepository;
        this.contractRepository = contractRepository;
        this.policyRepository = policyRepository;
        this.auditPort = auditPort;
    }

    @Override
    public List<UUID> findInvoicesToAssess(LocalDate today) {
        return invoiceRepository.findOverdueRentInvoiceIds(today);
    }

    @Override
    public boolean assessInvoice(UUID invoiceId, LocalDate today) {
        // Khóa dòng để một khoản tiền vừa nhận qua webhook không bị job này ghi đè.
        Optional<Invoice> locked = invoiceRepository.findByIdForUpdate(invoiceId);
        if (locked.isEmpty() || !locked.get().isOverdue(today)) {
            return false;
        }
        Invoice invoice = locked.get();
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
        BigDecimal fee = policy.get().feeFor(invoice.principalAmount(), daysOverdue);
        if (!invoice.applyLateFee(fee)) {
            return false;
        }
        invoiceRepository.save(invoice);
        auditPort.recordEvent(AuditPort.AuditEvent.of("LATE_FEE_APPLIED", null, "INVOICE", invoice.getId().toString(),
            Map.of("propertyId", propertyId.toString(), "amount", fee.toPlainString(), "daysOverdue", daysOverdue,
                "feeType", policy.get().feeType().name())));
        return true;
    }
}
