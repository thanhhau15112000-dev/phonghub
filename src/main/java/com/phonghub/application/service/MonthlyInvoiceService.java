package com.phonghub.application.service;

import com.phonghub.application.port.in.MonthlyInvoiceUseCase;
import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.application.port.out.InvoiceRepositoryPort;
import com.phonghub.application.port.out.MaintenanceTicketRepositoryPort;
import com.phonghub.application.port.out.MeterReadingRepositoryPort;
import com.phonghub.application.port.out.ServiceRateRepositoryPort;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.Invoice;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class MonthlyInvoiceService implements MonthlyInvoiceUseCase {

    private final ContractRepositoryPort contractRepository;
    private final InvoiceRepositoryPort invoiceRepository;
    private final MonthlyInvoiceIssuer issuer;
    private final UtilityBilling utilityBilling;

    /** Không tính điện nước vào hóa đơn (dùng khi chưa có kho chỉ số). */
    public MonthlyInvoiceService(
        ContractRepositoryPort contractRepository,
        InvoiceRepositoryPort invoiceRepository,
        MaintenanceTicketRepositoryPort ticketRepository
    ) {
        this(contractRepository, invoiceRepository, ticketRepository, UtilityBilling.none());
    }

    public MonthlyInvoiceService(
        ContractRepositoryPort contractRepository,
        InvoiceRepositoryPort invoiceRepository,
        MaintenanceTicketRepositoryPort ticketRepository,
        MeterReadingRepositoryPort readingRepository,
        ServiceRateRepositoryPort rateRepository
    ) {
        this(contractRepository, invoiceRepository, ticketRepository,
            new UtilityBilling(readingRepository, rateRepository));
    }

    private MonthlyInvoiceService(
        ContractRepositoryPort contractRepository,
        InvoiceRepositoryPort invoiceRepository,
        MaintenanceTicketRepositoryPort ticketRepository,
        UtilityBilling utilityBilling
    ) {
        this.contractRepository = contractRepository;
        this.invoiceRepository = invoiceRepository;
        this.utilityBilling = utilityBilling;
        this.issuer = new MonthlyInvoiceIssuer(invoiceRepository, ticketRepository, utilityBilling);
    }

    @Override
    public List<UUID> findContractsNeedingInvoice(YearMonth period) {
        return contractRepository.findAll().stream()
            .filter(contract -> isEligible(contract, period))
            .filter(contract -> !issuer.exists(contract, period))
            .map(Contract::getId)
            .toList();
    }

    @Override
    public boolean issueForContract(UUID contractId, YearMonth period) {
        Optional<Contract> contract = contractRepository.findById(contractId);
        if (contract.isEmpty() || !isEligible(contract.get(), period) || issuer.exists(contract.get(), period)) {
            return false;
        }
        issuer.issue(contract.get(), period);
        return true;
    }

    @Override
    public List<UUID> findContractsNeedingUtilityCharges(YearMonth period) {
        return contractRepository.findAll().stream()
            .filter(contract -> pendingCharges(contract, period))
            .map(Contract::getId)
            .toList();
    }

    @Override
    public boolean attachUtilityCharges(UUID contractId, YearMonth period) {
        Optional<Contract> contract = contractRepository.findById(contractId);
        if (contract.isEmpty()) {
            return false;
        }
        Optional<Invoice> existing = invoiceRepository.findActiveRentByContractIdAndPeriod(
            contractId, period.getYear(), period.getMonthValue());
        if (existing.isEmpty()) {
            return false;
        }
        // Khóa dòng hóa đơn rồi đọc lại để không ghi đè số tiền vừa được webhook thanh toán cộng vào
        Invoice invoice = invoiceRepository.findByPaymentCodeForUpdate(existing.get().getPaymentCode())
            .orElse(existing.get());
        if (!invoice.canAddCharges()) {
            return false;
        }
        int added = invoice.addCharges(utilityBilling.chargesFor(contract.get(), period, false));
        if (added == 0) {
            return false;
        }
        invoiceRepository.save(invoice);
        return true;
    }

    /** Hóa đơn tháng còn sửa được và đã có chỉ số/đơn giá điện nước chưa được tính vào. */
    private boolean pendingCharges(Contract contract, YearMonth period) {
        return invoiceRepository.findActiveRentByContractIdAndPeriod(
                contract.getId(), period.getYear(), period.getMonthValue())
            .filter(Invoice::canAddCharges)
            .filter(found -> utilityBilling.chargesFor(contract, period, false).stream()
                .anyMatch(item -> !found.hasCharge(item.readingId(), item.rateId())))
            .isPresent();
    }

    private static boolean isEligible(Contract contract, YearMonth period) {
        if (!contract.isActive()) {
            return false;
        }
        YearMonth firstMonth = YearMonth.from(contract.getStartDate());
        YearMonth lastMonth = YearMonth.from(contract.getEndDate());
        return period.isAfter(firstMonth) && !period.isAfter(lastMonth);
    }
}
