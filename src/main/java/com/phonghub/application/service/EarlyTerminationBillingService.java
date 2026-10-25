package com.phonghub.application.service;

import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.InvoiceRepositoryPort;
import com.phonghub.application.port.out.MeterReadingRepositoryPort;
import com.phonghub.application.port.out.ServiceRateRepositoryPort;
import com.phonghub.domain.model.BillingCycle;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.InvoiceItem;
import com.phonghub.domain.model.InvoiceStatus;
import com.phonghub.domain.model.InvoiceType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Bills the current cycle and refunds prepaid future periods when a contract ends early. */
public class EarlyTerminationBillingService {

    private final InvoiceRepositoryPort invoiceRepository;
    private final UtilityBilling utilityBilling;
    private final AuditPort auditPort;

    public EarlyTerminationBillingService(
        InvoiceRepositoryPort invoiceRepository,
        MeterReadingRepositoryPort readingRepository,
        ServiceRateRepositoryPort rateRepository,
        AuditPort auditPort
    ) {
        this.invoiceRepository = invoiceRepository;
        this.utilityBilling = readingRepository == null || rateRepository == null
            ? UtilityBilling.none() : new UtilityBilling(readingRepository, rateRepository);
        this.auditPort = auditPort != null ? auditPort : event -> {};
    }

    /** Điện/nước tính theo chỉ số công tơ đã ghi; dùng khi không có biên bản check-out cung cấp chỉ số cuối. */
    public void billEarlyTermination(Contract contract, CurrentUser actor, LocalDate today) {
        billEarlyTermination(contract, actor, today, null);
    }

    /**
     * Lập hóa đơn phí hủy giữa kỳ khi check-out trước ngày kết thúc hợp đồng. {@code utilityCharges} là điện/nước theo
     * chỉ số check-out (null thì tính từ chỉ số công tơ đã ghi). Hóa đơn chưa trừ cọc: việc trừ cọc do chủ trọ chọn
     * lúc duyệt check-out, cùng một cơ chế với mọi khoản nợ khác (#222). Gọi nhiều lần chỉ lập một lần. Người gọi giữ
     * khóa hợp đồng và lưu hợp đồng sau khi gọi.
     */
    public void billEarlyTermination(Contract contract, CurrentUser actor, LocalDate today,
                                     List<InvoiceItem> utilityCharges) {
        if (contract == null || today == null || !today.isBefore(contract.getEndDate())) {
            return;
        }
        BillingCycle cycle = contract.billingCycleFor(today).orElse(null);
        if (cycle == null) {
            return;
        }

        List<Invoice> invoices = invoiceRepository.findByContractId(contract.getId());
        if (invoices.stream().anyMatch(invoice -> invoice.getType() == InvoiceType.CANCELLATION
            && invoice.getStatus() != InvoiceStatus.VOIDED)) {
            return;
        }
        if (utilityCharges == null) {
            utilityCharges = utilityBilling.cancellationCharges(contract, today, invoices);
        }
        BigDecimal rentFee = contract.getRentAmount().multiply(new BigDecimal("0.5"))
            .setScale(0, RoundingMode.HALF_UP);
        Invoice cancellationInvoice = invoices.stream()
            .filter(invoice -> invoice.getType() == InvoiceType.RENT
                && invoice.getYear() == cycle.period().getYear()
                && invoice.getMonth() == cycle.period().getMonthValue()
                && invoice.getStatus() != InvoiceStatus.VOIDED)
            .findFirst()
            .orElse(null);

        BigDecimal refundCredit = BigDecimal.ZERO;
        if (cancellationInvoice != null) {
            refundCredit = cancellationInvoice.replaceWithCancellationFee(cycle, rentFee, utilityCharges, today);
        } else {
            cancellationInvoice = Invoice.issueCancellationFee(contract, cycle, rentFee, utilityCharges,
                invoiceRepository.newUniquePaymentCode(), today);
        }
        if (refundCredit.signum() > 0) {
            contract.addSettlementCredit(refundCredit);
        }
        BigDecimal totalRefundCredit = refundCredit;

        for (Invoice invoice : invoices) {
            if (invoice.getType() != InvoiceType.RENT || invoice.getStatus() == InvoiceStatus.VOIDED
                || invoice.getCycleStartDate() == null || !invoice.getCycleStartDate().isAfter(cycle.startDate())) {
                continue;
            }
            BigDecimal futureRefund = invoice.voidForEarlyTermination();
            if (futureRefund.signum() > 0) {
                contract.addSettlementCredit(futureRefund);
                totalRefundCredit = totalRefundCredit.add(futureRefund);
            }
            invoiceRepository.save(invoice);
        }

        invoiceRepository.save(cancellationInvoice);

        auditPort.recordEvent(AuditPort.AuditEvent.of("EARLY_TERMINATION_BILLING_CREATED",
            actor != null ? actor.id() : null, "CONTRACT", contract.getId().toString(), contract.getPropertyId(),
            Map.of("cycleNumber", cycle.number(), "cycleStart", cycle.startDate().toString(),
                "cycleEnd", cycle.endDate().toString(), "rentFee", rentFee.toPlainString(),
                "refundCredit", totalRefundCredit.toPlainString(),
                "cancellationInvoiceId", cancellationInvoice.getId().toString())));
    }
}
