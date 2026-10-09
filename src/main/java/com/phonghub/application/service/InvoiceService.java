package com.phonghub.application.service;

import com.phonghub.application.port.in.ContractUseCase;
import com.phonghub.application.port.in.InvoiceUseCase;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.InvoiceRepositoryPort;
import com.phonghub.application.port.out.MaintenanceTicketRepositoryPort;
import com.phonghub.application.port.out.MeterReadingRepositoryPort;
import com.phonghub.application.port.out.ServiceRateRepositoryPort;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.Invoice;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class InvoiceService implements InvoiceUseCase {

    private final InvoiceRepositoryPort invoiceRepository;
    private final MonthlyInvoiceIssuer issuer;
    private final ContractUseCase contractUseCase;
    private final CurrentUserPort currentUserPort;
    private final AuthorizationService authorizationService;
    private final UtilityBilling utilityBilling;

    public InvoiceService(
        InvoiceRepositoryPort invoiceRepository,
        MaintenanceTicketRepositoryPort ticketRepository,
        ContractUseCase contractUseCase,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService
    ) {
        this(invoiceRepository, ticketRepository, contractUseCase, currentUserPort, authorizationService, null, null);
    }

    public InvoiceService(
        InvoiceRepositoryPort invoiceRepository,
        MaintenanceTicketRepositoryPort ticketRepository,
        ContractUseCase contractUseCase,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        MeterReadingRepositoryPort readingRepository,
        ServiceRateRepositoryPort rateRepository
    ) {
        this.invoiceRepository = invoiceRepository;
        this.utilityBilling = readingRepository == null || rateRepository == null
            ? UtilityBilling.none() : new UtilityBilling(readingRepository, rateRepository);
        this.issuer = new MonthlyInvoiceIssuer(invoiceRepository, ticketRepository, utilityBilling);
        this.contractUseCase = contractUseCase;
        this.currentUserPort = currentUserPort;
        this.authorizationService = authorizationService;
    }

    @Override
    public List<Invoice> listInvoicesForContract(UUID contractId) {
        // getContract áp dụng quy tắc xem hợp đồng (người thuê chỉ xem hợp đồng của mình).
        contractUseCase.getContract(contractId);
        return invoiceRepository.findByContractId(contractId).stream()
            .sorted(Comparator.comparing(Invoice::getYear).thenComparing(Invoice::getMonth).thenComparing(Invoice::getCreatedAt).reversed())
            .toList();
    }

    @Override
    public Invoice getInvoice(UUID invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new DomainException("Không tìm thấy hóa đơn."));
        // getContract áp dụng quy tắc xem hợp đồng (người thuê chỉ xem hợp đồng của mình).
        contractUseCase.getContract(invoice.getContractId());
        return invoice;
    }

    @Override
    public Map<UUID, List<String>> utilityWarnings(UUID contractId) {
        Contract contract = contractUseCase.getContract(contractId);
        Map<UUID, List<String>> warnings = new HashMap<>();
        for (Invoice invoice : invoiceRepository.findByContractId(contractId)) {
            List<String> found = utilityBilling.warnings(invoice, contract);
            if (!found.isEmpty()) {
                warnings.put(invoice.getId(), found);
            }
        }
        return warnings;
    }

    @Override
    public Invoice issueMonthlyInvoice(UUID contractId, YearMonth period) {
        Contract contract = contractUseCase.getContract(contractId);
        authorizationService.assertCanManageContracts(currentUserPort.getCurrentUser(), contract.getPropertyId());

        if (issuer.exists(contract, period)) {
            throw new DomainException(String.format(
                "Kỳ thanh toán %02d/%d đã tồn tại cho hợp đồng này.", period.getMonthValue(), period.getYear()
            ));
        }
        return issuer.issue(contract, period);
    }

    @Override
    public java.util.Optional<Invoice> findFeeInvoiceForTicket(UUID ticketId) {
        return invoiceRepository.findActiveByTicketId(ticketId).filter(invoice -> {
            try {
                contractUseCase.getContract(invoice.getContractId());
                return true;
            } catch (UnauthorizedPropertyAccessException ex) {
                return false;
            }
        });
    }
}
