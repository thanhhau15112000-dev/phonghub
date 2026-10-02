package com.phonghub.application.service;

import com.phonghub.application.port.in.ContractUseCase;
import com.phonghub.application.port.in.InvoiceUseCase;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.InvoiceRepositoryPort;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.Invoice;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class InvoiceService implements InvoiceUseCase {

    private final InvoiceRepositoryPort invoiceRepository;
    private final ContractUseCase contractUseCase;
    private final CurrentUserPort currentUserPort;
    private final AuthorizationService authorizationService;

    public InvoiceService(
        InvoiceRepositoryPort invoiceRepository,
        ContractUseCase contractUseCase,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService
    ) {
        this.invoiceRepository = invoiceRepository;
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
    public Invoice issueMonthlyInvoice(UUID contractId, YearMonth period) {
        Contract contract = contractUseCase.getContract(contractId);
        authorizationService.assertCanManageContracts(currentUserPort.getCurrentUser(), contract.getPropertyId());

        if (invoiceRepository.findActiveRentByContractIdAndPeriod(contractId, period.getYear(), period.getMonthValue()).isPresent()) {
            throw new DomainException(String.format(
                "Kỳ thanh toán %02d/%d đã tồn tại cho hợp đồng này.", period.getMonthValue(), period.getYear()
            ));
        }

        Invoice invoice = Invoice.issueMonthlyRent(contract, period, invoiceRepository.newUniquePaymentCode());
        return invoiceRepository.save(invoice);
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
