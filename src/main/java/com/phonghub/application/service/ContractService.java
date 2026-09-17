package com.phonghub.application.service;

import com.phonghub.application.port.in.ContractUseCase;
import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.PropertyRepositoryPort;
import com.phonghub.application.port.out.RoomRepositoryPort;
import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.domain.exception.ContractNotFoundException;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.exception.DuplicateActiveContractException;
import com.phonghub.domain.exception.PropertyNotFoundException;
import com.phonghub.domain.exception.RoomNotFoundException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractStatus;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomStatus;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.UserRole;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ContractService implements ContractUseCase {

    private final ContractRepositoryPort contractRepository;
    private final RoomRepositoryPort roomRepository;
    private final PropertyRepositoryPort propertyRepository;
    private final TenantRepositoryPort tenantRepository;
    private final CurrentUserPort currentUserPort;
    private final AuthorizationService authorizationService;

    public ContractService(
        ContractRepositoryPort contractRepository,
        RoomRepositoryPort roomRepository,
        PropertyRepositoryPort propertyRepository,
        TenantRepositoryPort tenantRepository,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService
    ) {
        this.contractRepository = contractRepository;
        this.roomRepository = roomRepository;
        this.propertyRepository = propertyRepository;
        this.tenantRepository = tenantRepository;
        this.currentUserPort = currentUserPort;
        this.authorizationService = authorizationService;
    }

    @Override
    public Contract createContract(CreateContractCommand command) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        authorizationService.assertCanManageContracts(currentUser, command.propertyId());

        if (!propertyRepository.existsById(command.propertyId())) {
            throw new PropertyNotFoundException("Property not found with ID: " + command.propertyId());
        }

        Room room = roomRepository.findById(command.roomId())
            .orElseThrow(() -> new RoomNotFoundException("Room not found with ID: " + command.roomId()));

        if (!room.getPropertyId().equals(command.propertyId())) {
            throw new DomainException(String.format(
                "Room %s does not belong to property %s", room.getRoomNumber(), command.propertyId()
            ));
        }

        // Room must not already have an active contract
        Optional<Contract> existingActive = contractRepository.findActiveByRoomId(room.getId());
        if (existingActive.isPresent()) {
            throw new DuplicateActiveContractException(String.format(
                "Room '%s' already has an active contract (ID: %s)", room.getRoomNumber(), existingActive.get().getId()
            ));
        }

        // Room cannot have a contract created if it is in MAINTENANCE
        if (room.getStatus() == RoomStatus.MAINTENANCE) {
            throw new DomainException(String.format(
                "Cannot create contract for room '%s' while in MAINTENANCE status", room.getRoomNumber()
            ));
        }

        // Resolve or create primary tenant
        Tenant tenant = tenantRepository.findByIdentityCardNumber(command.primaryTenantIdCard().trim())
            .orElseGet(() -> {
                Tenant newTenant = Tenant.create(
                    command.primaryTenantUserId(),
                    command.primaryTenantFullName().trim(),
                    command.primaryTenantIdCard().trim(),
                    command.primaryTenantPhone().trim(),
                    command.primaryTenantEmail(),
                    null
                );
                return tenantRepository.save(newTenant);
            });

        Contract contract = Contract.create(
            command.propertyId(),
            command.roomId(),
            tenant.id(),
            command.depositAmount(),
            command.rentAmount(),
            command.startDate(),
            command.endDate(),
            command.paymentDay()
        );

        return contractRepository.save(contract);
    }

    @Override
    public Contract activateContract(UUID contractId) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        Contract contract = contractRepository.findById(contractId)
            .orElseThrow(() -> new ContractNotFoundException("Contract not found with ID: " + contractId));

        authorizationService.assertCanManageContracts(currentUser, contract.getPropertyId());

        Room room = roomRepository.findById(contract.getRoomId())
            .orElseThrow(() -> new RoomNotFoundException("Room not found with ID: " + contract.getRoomId()));

        // Invariant: Do not allow two active contracts for one room
        Optional<Contract> activeContract = contractRepository.findActiveByRoomId(room.getId());
        if (activeContract.isPresent() && !activeContract.get().getId().equals(contract.getId())) {
            throw new DuplicateActiveContractException(String.format(
                "Room '%s' already has an active contract (ID: %s)", room.getRoomNumber(), activeContract.get().getId()
            ));
        }

        // Invariant: Activating contract changes the room to OCCUPIED
        contract.activate();
        room.occupy();

        roomRepository.save(room);
        return contractRepository.save(contract);
    }

    @Override
    public Contract terminateContract(UUID contractId, boolean requiresMaintenance) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        Contract contract = contractRepository.findById(contractId)
            .orElseThrow(() -> new ContractNotFoundException("Contract not found with ID: " + contractId));

        authorizationService.assertCanManageContracts(currentUser, contract.getPropertyId());

        Room room = roomRepository.findById(contract.getRoomId())
            .orElseThrow(() -> new RoomNotFoundException("Room not found with ID: " + contract.getRoomId()));

        // Invariant: Ending a contract changes room to AVAILABLE unless maintenance is explicitly required
        contract.terminate();
        room.vacate(requiresMaintenance);

        roomRepository.save(room);
        return contractRepository.save(contract);
    }

    @Override
    public Contract getContract(UUID contractId) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        Contract contract = contractRepository.findById(contractId)
            .orElseThrow(() -> new ContractNotFoundException("Contract not found with ID: " + contractId));

        if (currentUser.role() == UserRole.TENANT) {
            Optional<Tenant> tenantOpt = tenantRepository.findByUserId(currentUser.id());
            if (tenantOpt.isEmpty()) {
                throw new UnauthorizedPropertyAccessException("Tenant profile not found");
            }
            boolean isAuthorized = contract.getPrimaryTenantId().equals(tenantOpt.get().id()) ||
                contract.getOccupants().stream().anyMatch(o -> o.tenantId().equals(tenantOpt.get().id()));
            if (!isAuthorized) {
                throw new UnauthorizedPropertyAccessException("Tenant can only view their own contract");
            }
        } else {
            authorizationService.assertCanAccessProperty(currentUser, contract.getPropertyId());
        }

        return contract;
    }

    @Override
    public List<Contract> listContractsForProperty(UUID propertyId) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        authorizationService.assertCanAccessProperty(currentUser, propertyId);

        if (currentUser.role() == UserRole.TENANT) {
            return listContractsForTenant(currentUser.id()).stream()
                .filter(c -> c.getPropertyId().equals(propertyId))
                .toList();
        }

        return contractRepository.findByPropertyId(propertyId);
    }

    @Override
    public Optional<Contract> getActiveContractForTenant(UUID tenantUserId) {
        Optional<Tenant> tenantOpt = tenantRepository.findByUserId(tenantUserId);
        if (tenantOpt.isEmpty()) {
            return Optional.empty();
        }
        Tenant tenant = tenantOpt.get();
        List<Contract> primaryContracts = contractRepository.findByPrimaryTenantId(tenant.id());
        for (Contract c : primaryContracts) {
            if (c.isActive()) {
                return Optional.of(c);
            }
        }
        List<Contract> occupantContracts = contractRepository.findByOccupantTenantId(tenant.id());
        for (Contract c : occupantContracts) {
            if (c.isActive()) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }

    @Override
    public List<Contract> listContractsForTenant(UUID tenantUserId) {
        Optional<Tenant> tenantOpt = tenantRepository.findByUserId(tenantUserId);
        if (tenantOpt.isEmpty()) {
            return Collections.emptyList();
        }
        Tenant tenant = tenantOpt.get();
        List<Contract> primaryContracts = contractRepository.findByPrimaryTenantId(tenant.id());
        List<Contract> occupantContracts = contractRepository.findByOccupantTenantId(tenant.id());

        return java.util.stream.Stream.concat(primaryContracts.stream(), occupantContracts.stream())
            .distinct()
            .toList();
    }
}
