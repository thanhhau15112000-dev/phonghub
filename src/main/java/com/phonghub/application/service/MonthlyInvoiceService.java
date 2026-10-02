package com.phonghub.application.service;

import com.phonghub.application.port.in.MonthlyInvoiceUseCase;
import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.application.port.out.InvoiceRepositoryPort;
import com.phonghub.application.port.out.MaintenanceTicketRepositoryPort;
import com.phonghub.domain.model.Contract;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class MonthlyInvoiceService implements MonthlyInvoiceUseCase {

    private final ContractRepositoryPort contractRepository;
    private final MonthlyInvoiceIssuer issuer;

    public MonthlyInvoiceService(
        ContractRepositoryPort contractRepository,
        InvoiceRepositoryPort invoiceRepository,
        MaintenanceTicketRepositoryPort ticketRepository
    ) {
        this.contractRepository = contractRepository;
        this.issuer = new MonthlyInvoiceIssuer(invoiceRepository, ticketRepository);
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

    private static boolean isEligible(Contract contract, YearMonth period) {
        if (!contract.isActive()) {
            return false;
        }
        YearMonth firstMonth = YearMonth.from(contract.getStartDate());
        YearMonth lastMonth = YearMonth.from(contract.getEndDate());
        return period.isAfter(firstMonth) && !period.isAfter(lastMonth);
    }
}
