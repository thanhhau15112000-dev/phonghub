package com.phonghub.config;

import com.phonghub.application.port.in.ContractUseCase;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractOccupant;
import com.phonghub.domain.model.Tenant;
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
    @Transactional(rollbackFor = Exception.class)
    public Contract terminateEarly(UUID contractId, boolean requiresMaintenance, String reason) {
        return delegate.terminateEarly(contractId, requiresMaintenance, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Contract renewContract(RenewContractCommand command) {
        return delegate.renewContract(command);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Contract updateDraftContract(UpdateDraftContractCommand command) {
        return delegate.updateDraftContract(command);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Contract cancelDraftContract(UUID contractId) {
        return delegate.cancelDraftContract(contractId);
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
    @Transactional(readOnly = true)
    public List<Tenant> listAvailablePrimaryTenants(UUID propertyId, String query) {
        return delegate.listAvailablePrimaryTenants(propertyId, query);
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
    public AddOccupantResult addOccupant(AddOccupantCommand command) {
        return delegate.addOccupant(command);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Tenant> listAvailableOccupants(UUID contractId) {
        return delegate.listAvailableOccupants(contractId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<ContractOccupant> addExistingOccupants(AddExistingOccupantsCommand command) {
        return delegate.addExistingOccupants(command);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AddOccupantsResult addOccupants(AddOccupantsCommand command) {
        return delegate.addOccupants(command);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public com.phonghub.domain.model.ContractOccupant checkOutOccupant(CheckOutOccupantCommand command) {
        return delegate.checkOutOccupant(command);
    }
}
