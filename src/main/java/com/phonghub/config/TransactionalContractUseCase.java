package com.phonghub.config;

import com.phonghub.application.port.in.ContractUseCase;
import com.phonghub.domain.model.Contract;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class TransactionalContractUseCase implements ContractUseCase {

    private final ContractUseCase delegate;

    public TransactionalContractUseCase(ContractUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Contract createContract(CreateContractCommand command) {
        return delegate.createContract(command);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Contract activateContract(UUID contractId) {
        return delegate.activateContract(contractId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Contract terminateContract(UUID contractId, boolean requiresMaintenance) {
        return delegate.terminateContract(contractId, requiresMaintenance);
    }

    @Override
    public Contract getContract(UUID contractId) {
        return delegate.getContract(contractId);
    }

    @Override
    public List<Contract> listContractsForProperty(UUID propertyId) {
        return delegate.listContractsForProperty(propertyId);
    }

    @Override
    public Optional<Contract> getActiveContractForTenant(UUID tenantUserId) {
        return delegate.getActiveContractForTenant(tenantUserId);
    }

    @Override
    public List<Contract> listContractsForTenant(UUID tenantUserId) {
        return delegate.listContractsForTenant(tenantUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public com.phonghub.domain.model.ContractOccupant addOccupant(AddOccupantCommand command) {
        return delegate.addOccupant(command);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public com.phonghub.domain.model.ContractOccupant checkOutOccupant(CheckOutOccupantCommand command) {
        return delegate.checkOutOccupant(command);
    }
}
