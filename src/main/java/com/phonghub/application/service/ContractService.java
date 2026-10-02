package com.phonghub.application.service;

import com.phonghub.application.port.in.ContractUseCase;
import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.IdentityProviderPort;
import com.phonghub.application.port.out.PropertyRepositoryPort;
import com.phonghub.application.port.out.RoomRepositoryPort;
import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.application.port.out.UserRepositoryPort;
import com.phonghub.domain.exception.ContractNotFoundException;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.exception.DuplicateActiveContractException;
import com.phonghub.domain.exception.InvalidPropertyStatusException;
import com.phonghub.domain.exception.InvalidRoomCapacityException;
import com.phonghub.domain.exception.PropertyNotFoundException;
import com.phonghub.domain.exception.RoomNotFoundException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractOccupant;
import com.phonghub.domain.model.ContractStatus;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.PropertyApprovalStatus;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomStatus;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class ContractService implements ContractUseCase {

    private final ContractRepositoryPort contractRepository;
    private final RoomRepositoryPort roomRepository;
    private final PropertyRepositoryPort propertyRepository;
    private final TenantRepositoryPort tenantRepository;
    private final UserRepositoryPort userRepository;
    private final IdentityProviderPort identityProviderPort;
    private final CurrentUserPort currentUserPort;
    private final AuthorizationService authorizationService;
    private final AuditPort auditPort;

    public ContractService(
        ContractRepositoryPort contractRepository,
        RoomRepositoryPort roomRepository,
        PropertyRepositoryPort propertyRepository,
        TenantRepositoryPort tenantRepository,
        UserRepositoryPort userRepository,
        IdentityProviderPort identityProviderPort,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        AuditPort auditPort
    ) {
        this.contractRepository = contractRepository;
        this.roomRepository = roomRepository;
        this.propertyRepository = propertyRepository;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.identityProviderPort = identityProviderPort;
        this.currentUserPort = currentUserPort;
        this.authorizationService = authorizationService;
        this.auditPort = auditPort;
    }

    public ContractService(
        ContractRepositoryPort contractRepository,
        RoomRepositoryPort roomRepository,
        PropertyRepositoryPort propertyRepository,
        TenantRepositoryPort tenantRepository,
        UserRepositoryPort userRepository,
        IdentityProviderPort identityProviderPort,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService
    ) {
        this(contractRepository, roomRepository, propertyRepository, tenantRepository, userRepository, identityProviderPort, currentUserPort, authorizationService, null);
    }

    public ContractService(
        ContractRepositoryPort contractRepository,
        RoomRepositoryPort roomRepository,
        PropertyRepositoryPort propertyRepository,
        TenantRepositoryPort tenantRepository,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService
    ) {
        this(contractRepository, roomRepository, propertyRepository, tenantRepository, null, null, currentUserPort, authorizationService, null);
    }

    @Override
    public Contract createContract(CreateContractCommand command) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        authorizationService.assertCanManageContracts(currentUser, command.propertyId());

        Property property = propertyRepository.findById(command.propertyId())
            .orElseThrow(() -> new PropertyNotFoundException("Property not found with ID: " + command.propertyId()));

        if (property.approvalStatus() != PropertyApprovalStatus.VERIFIED) {
            throw new DomainException(String.format(
                "Không thể tạo hợp đồng cho nhà trọ '%s' vì chưa được duyệt (trạng thái: %s).",
                property.name(), property.approvalStatus()
            ));
        }

        Room room = validateOwnershipOfRoom(currentUser, command.propertyId(), command.roomId());

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

        if (contract.getStatus() != ContractStatus.DRAFT) {
            throw new DomainException(String.format(
                "Chỉ có thể kích hoạt hợp đồng ở trạng thái DRAFT. Trạng thái hiện tại: '%s'.",
                contract.getStatus()
            ));
        }

        Room room = validateOwnershipOfRoom(currentUser, contract.getPropertyId(), contract.getRoomId());

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

        if (contract.getStatus() != ContractStatus.ACTIVE) {
            throw new DomainException(String.format(
                "Chỉ có thể kết thúc hợp đồng ở trạng thái ACTIVE. Trạng thái hiện tại: '%s'.",
                contract.getStatus()
            ));
        }

        Room room = validateOwnershipOfRoom(currentUser, contract.getPropertyId(), contract.getRoomId());

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

    @Override
    public ContractOccupant addOccupant(AddOccupantCommand command) {
        Contract contract = contractRepository.findById(command.contractId())
            .orElseThrow(() -> new ContractNotFoundException("Contract not found with ID: " + command.contractId()));

        CurrentUser currentUser = currentUserPort.getCurrentUser();
        authorizationService.assertOwnerOrAdmin(currentUser, contract.getPropertyId());

        Property property = propertyRepository.findById(contract.getPropertyId())
            .orElseThrow(() -> new PropertyNotFoundException("Property not found with ID: " + contract.getPropertyId()));

        if (property.approvalStatus() != PropertyApprovalStatus.VERIFIED) {
            throw new InvalidPropertyStatusException(String.format(
                "Chỉ có thể thêm người thuê cho nhà trọ đã được duyệt (VERIFIED). Trạng thái hiện tại: '%s'.",
                property.approvalStatus()
            ));
        }

        if (!contract.isActive()) {
            throw new DomainException(String.format(
                "Chỉ có thể thêm người thuê vào hợp đồng đang có hiệu lực (ACTIVE). Trạng thái hợp đồng hiện tại: '%s'.",
                contract.getStatus()
            ));
        }

        Room room = roomRepository.findById(contract.getRoomId())
            .orElseThrow(() -> new RoomNotFoundException("Room not found with ID: " + contract.getRoomId()));

        LocalDate today = LocalDate.now();
        long residingCount = (contract.getOccupants() == null || contract.getOccupants().isEmpty())
            ? 1
            : contract.getOccupants().stream()
                .filter(o -> o.checkOutDate() == null || o.checkOutDate().isAfter(today))
                .count();

        if (residingCount + 1 > room.getMaxOccupants()) {
            throw new InvalidRoomCapacityException(String.format(
                "Số lượng người ở vượt quá sức chứa tối đa của phòng '%s' (tối đa: %d người, hiện có: %d người).",
                room.getRoomNumber(), room.getMaxOccupants(), residingCount
            ));
        }

        String cleanIdCard = command.identityCardNumber().trim();
        Tenant tenant = tenantRepository.findByIdentityCardNumber(cleanIdCard)
            .orElseGet(() -> {
                Tenant newTenant = Tenant.create(
                    null,
                    command.fullName().trim(),
                    cleanIdCard,
                    command.phone().trim(),
                    command.email() != null && !command.email().isBlank() ? command.email().trim() : null,
                    command.permanentAddress() != null && !command.permanentAddress().isBlank() ? command.permanentAddress().trim() : null
                );
                return tenantRepository.save(newTenant);
            });

        // Cập nhật thông tin bổ sung nếu tenant đã tồn tại nhưng chưa có email/địa chỉ
        String updateEmail = command.email() != null && !command.email().isBlank()
            ? command.email().trim()
            : tenant.email();
        String updateAddress = command.permanentAddress() != null && !command.permanentAddress().isBlank()
            ? command.permanentAddress().trim()
            : tenant.permanentAddress();
        if (!Objects.equals(updateEmail, tenant.email()) || !Objects.equals(updateAddress, tenant.permanentAddress())) {
            tenant = new Tenant(
                tenant.id(),
                tenant.userId(),
                tenant.fullName(),
                tenant.identityCardNumber(),
                tenant.phone(),
                updateEmail,
                updateAddress,
                tenant.createdAt()
            );
            tenant = tenantRepository.save(tenant);
        }

        // Một người không được ở đồng thời ở 2 hợp đồng đang hiệu lực -> 409
        final UUID tenantId = tenant.id();
        boolean alreadyActiveAsPrimary = contractRepository.findByPrimaryTenantId(tenantId).stream()
            .anyMatch(Contract::isActive);

        boolean alreadyActiveAsOccupant = contractRepository.findByOccupantTenantId(tenantId).stream()
            .filter(Contract::isActive)
            .anyMatch(c -> c.getOccupants().stream()
                .anyMatch(o -> o.tenantId().equals(tenantId) && (o.checkOutDate() == null || o.checkOutDate().isAfter(today))));

        if (alreadyActiveAsPrimary || alreadyActiveAsOccupant) {
            throw new DuplicateActiveContractException(String.format(
                "Người thuê '%s' (CCCD: %s) hiện đang có hợp đồng hiệu lực tại hệ thống.",
                tenant.fullName(), tenant.identityCardNumber()
            ));
        }

        // Tùy chọn: tạo tài khoản TENANT cho người thuê
        if (command.createAccount()) {
            if (command.email() == null || command.email().isBlank()) {
                throw new IllegalArgumentException("Cần cung cấp email để tạo tài khoản người dùng");
            }
            String normalizedEmail = command.email().trim().toLowerCase();
            if (!normalizedEmail.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
                throw new IllegalArgumentException("Định dạng email không hợp lệ: " + command.email());
            }

            if (tenant.userId() == null && userRepository != null) {
                Optional<User> existingUser = userRepository.findByEmail(normalizedEmail);
                UUID linkedUserId = null;
                if (existingUser.isPresent()) {
                    linkedUserId = existingUser.get().id();
                } else if (identityProviderPort != null) {
                    String temporaryPassword = UUID.randomUUID().toString().substring(0, 8) + "@Abc1";
                    UUID authUserId = identityProviderPort.adminCreateUser(normalizedEmail, temporaryPassword);

                    String baseUsername = normalizedEmail.split("@")[0].replaceAll("[^a-zA-Z0-9_]", "");
                    if (baseUsername.length() < 3) baseUsername = "tenant_" + baseUsername;
                    String username = baseUsername;
                    int counter = 1;
                    while (userRepository.findByUsername(username).isPresent()) {
                        username = baseUsername + counter++;
                    }

                    User newUser = new User(
                        authUserId,
                        username,
                        normalizedEmail,
                        tenant.fullName(),
                        tenant.phone(),
                        UserRole.TENANT,
                        User.UserStatus.ACTIVE,
                        true,
                        Instant.now()
                    );
                    User savedUser = userRepository.save(newUser);
                    linkedUserId = savedUser.id();
                }

                if (linkedUserId != null) {
                    tenant = new Tenant(
                        tenant.id(),
                        linkedUserId,
                        tenant.fullName(),
                        tenant.identityCardNumber(),
                        tenant.phone(),
                        normalizedEmail,
                        tenant.permanentAddress(),
                        tenant.createdAt()
                    );
                    tenantRepository.save(tenant);
                }
            }
        }

        ContractOccupant occupant = contract.addOccupant(tenant.id(), false, command.checkInDate());
        contractRepository.save(contract);
        return occupant;
    }

    @Override
    public ContractOccupant checkOutOccupant(CheckOutOccupantCommand command) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        Contract contract = contractRepository.findById(command.contractId())
            .orElseThrow(() -> new ContractNotFoundException("Contract not found with ID: " + command.contractId()));

        authorizationService.assertOwnerOrAdmin(currentUser, contract.getPropertyId());

        LocalDate checkOutDate = command.checkOutDate() != null ? command.checkOutDate() : LocalDate.now();

        ContractOccupant updatedOccupant = contract.checkOutOccupant(command.tenantId(), checkOutDate);
        contractRepository.save(contract);

        if (auditPort != null) {
            Map<String, Object> details = new HashMap<>();
            details.put("contractId", contract.getId().toString());
            details.put("tenantId", command.tenantId().toString());
            details.put("checkOutDate", checkOutDate.toString());
            details.put("occupantId", updatedOccupant.id().toString());

            auditPort.recordEvent(AuditPort.AuditEvent.of(
                "OCCUPANT_CHECKOUT",
                currentUser != null ? currentUser.id() : null,
                "CONTRACT_OCCUPANT",
                updatedOccupant.id().toString(),
                details
            ));
        }

        return updatedOccupant;
    }

    private Room validateOwnershipOfRoom(CurrentUser user, UUID propertyId, UUID roomId) {
        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new RoomNotFoundException("Room not found with ID: " + roomId));
        if (!room.getPropertyId().equals(propertyId)) {
            throw new UnauthorizedPropertyAccessException(String.format(
                "Phòng '%s' (ID: %s) không thuộc về nhà trọ (ID: %s).", room.getRoomNumber(), roomId, propertyId
            ));
        }
        authorizationService.assertCanManageContracts(user, room.getPropertyId());
        return room;
    }
}
