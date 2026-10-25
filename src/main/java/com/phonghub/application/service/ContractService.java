package com.phonghub.application.service;

import com.phonghub.application.port.in.ContractUseCase;
import com.phonghub.application.port.in.MonthlyInvoiceUseCase;
import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.IdentityProviderPort;
import com.phonghub.application.port.out.HandoverRepositoryPort;
import com.phonghub.application.port.out.DraftHandoverCleanupPort;
import com.phonghub.application.port.out.PropertyRepositoryPort;
import com.phonghub.application.port.out.RoomRepositoryPort;
import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.application.port.out.UserRepositoryPort;
import com.phonghub.domain.exception.ContractNotFoundException;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.exception.DuplicateActiveContractException;
import com.phonghub.domain.exception.InvalidContractStateException;
import com.phonghub.domain.exception.InvalidPropertyStatusException;
import com.phonghub.domain.exception.InvalidRoomCapacityException;
import com.phonghub.domain.exception.PropertyNotFoundException;
import com.phonghub.domain.exception.RoomNotFoundException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractOccupant;
import com.phonghub.domain.model.ContractStatus;
import com.phonghub.domain.model.HandoverStatus;
import com.phonghub.domain.model.Handover;
import com.phonghub.domain.model.HandoverType;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.PropertyApprovalStatus;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomStatus;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class ContractService implements ContractUseCase {

    private final ContractRepositoryPort contractRepository;
    private final RoomRepositoryPort roomRepository;
    private final PropertyRepositoryPort propertyRepository;
    private final TenantRepositoryPort tenantRepository;
    private final UserRepositoryPort userRepository;
    private final CurrentUserPort currentUserPort;
    private final AuthorizationService authorizationService;
    private final AuditPort auditPort;
    private final TenantAccountLinker accountLinker;
    private final HandoverRepositoryPort handoverRepository;
    private final DraftHandoverCleanupPort draftHandoverCleanupPort;
    private final MonthlyInvoiceUseCase monthlyInvoiceUseCase;

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
        this(contractRepository, roomRepository, propertyRepository, tenantRepository, userRepository, identityProviderPort,
            currentUserPort, authorizationService, auditPort, null, null, null);
    }

    public ContractService(
        ContractRepositoryPort contractRepository,
        RoomRepositoryPort roomRepository,
        PropertyRepositoryPort propertyRepository,
        TenantRepositoryPort tenantRepository,
        UserRepositoryPort userRepository,
        IdentityProviderPort identityProviderPort,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        AuditPort auditPort,
        HandoverRepositoryPort handoverRepository
    ) {
        this(contractRepository, roomRepository, propertyRepository, tenantRepository, userRepository, identityProviderPort,
            currentUserPort, authorizationService, auditPort, handoverRepository, null, null);
    }

    public ContractService(
        ContractRepositoryPort contractRepository,
        RoomRepositoryPort roomRepository,
        PropertyRepositoryPort propertyRepository,
        TenantRepositoryPort tenantRepository,
        UserRepositoryPort userRepository,
        IdentityProviderPort identityProviderPort,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        AuditPort auditPort,
        HandoverRepositoryPort handoverRepository,
        DraftHandoverCleanupPort draftHandoverCleanupPort
    ) {
        this(contractRepository, roomRepository, propertyRepository, tenantRepository, userRepository, identityProviderPort,
            currentUserPort, authorizationService, auditPort, handoverRepository, draftHandoverCleanupPort, null);
    }

    public ContractService(
        ContractRepositoryPort contractRepository,
        RoomRepositoryPort roomRepository,
        PropertyRepositoryPort propertyRepository,
        TenantRepositoryPort tenantRepository,
        UserRepositoryPort userRepository,
        IdentityProviderPort identityProviderPort,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        AuditPort auditPort,
        HandoverRepositoryPort handoverRepository,
        DraftHandoverCleanupPort draftHandoverCleanupPort,
        MonthlyInvoiceUseCase monthlyInvoiceUseCase
    ) {
        this.contractRepository = contractRepository;
        this.roomRepository = roomRepository;
        this.propertyRepository = propertyRepository;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.currentUserPort = currentUserPort;
        this.authorizationService = authorizationService;
        this.auditPort = auditPort;
        this.accountLinker = new TenantAccountLinker(tenantRepository, userRepository, identityProviderPort, auditPort);
        this.handoverRepository = handoverRepository;
        this.draftHandoverCleanupPort = draftHandoverCleanupPort;
        this.monthlyInvoiceUseCase = monthlyInvoiceUseCase;
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

    public ContractService(
        ContractRepositoryPort contractRepository,
        RoomRepositoryPort roomRepository,
        PropertyRepositoryPort propertyRepository,
        TenantRepositoryPort tenantRepository,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        HandoverRepositoryPort handoverRepository
    ) {
        this(contractRepository, roomRepository, propertyRepository, tenantRepository, null, null, currentUserPort,
            authorizationService, null, handoverRepository, null);
    }

    @Override
    public Contract createContract(CreateContractCommand command) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        authorizationService.assertCanManageContracts(currentUser, command.propertyId());

        Property property = propertyRepository.findById(command.propertyId())
            .orElseThrow(() -> new PropertyNotFoundException("Không tìm thấy nhà trọ: " + command.propertyId()));

        if (property.isArchived()) {
            throw new com.phonghub.domain.exception.ConflictException(
                "Nhà trọ '" + property.name() + "' đã bị ẩn, không thể tạo hợp đồng mới.");
        }
        if (property.approvalStatus() != PropertyApprovalStatus.VERIFIED) {
            throw new DomainException(String.format(
                "Không thể tạo hợp đồng cho nhà trọ '%s' vì chưa được duyệt (trạng thái: %s).",
                property.name(), property.approvalStatus()
            ));
        }

        Room room = validateOwnershipOfRoom(currentUser, command.propertyId(), command.roomId());
        if (room.isArchived()) {
            throw new com.phonghub.domain.exception.ConflictException(
                "Phòng '" + room.getRoomNumber() + "' đã ngừng sử dụng, không thể tạo hợp đồng.");
        }

        // Room must not already have an active contract
        Optional<Contract> existingActive = contractRepository.findActiveByRoomId(room.getId());
        if (existingActive.isPresent()) {
            throw new DuplicateActiveContractException(String.format(
                "Phòng '%s' đã có hợp đồng đang hiệu lực (mã %s).", room.getRoomNumber(), existingActive.get().getId()
            ));
        }

        // Room cannot have a contract created if it is in MAINTENANCE
        if (room.getStatus() == RoomStatus.MAINTENANCE) {
            throw new DomainException(String.format(
                "Phòng '%s' đang bảo trì nên chưa tạo được hợp đồng.", room.getRoomNumber()
            ));
        }

        // Contracts bind to an existing, account-backed tenant profile; they never create personal profiles inline.
        Tenant tenant = listAvailablePrimaryTenants(command.propertyId(), null).stream()
            .filter(candidate -> candidate.id().equals(command.primaryTenantId()))
            .findFirst()
            .orElseThrow(() -> new DomainException("Người thuê không khả dụng trong nhà trọ này hoặc chưa có tài khoản."));
        tenantRepository.linkToProperty(tenant.id(), property.id(), currentUser.id());

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

        Contract saved = contractRepository.save(contract);
        audit("CONTRACT_CREATED", currentUser, saved, Map.of(
            "roomId", saved.getRoomId().toString(), "status", saved.getStatus().name(),
            "rentAmount", saved.getRentAmount().toPlainString(), "depositAmount", saved.getDepositAmount().toPlainString(),
            "startDate", saved.getStartDate().toString(), "endDate", saved.getEndDate().toString()));
        return saved;
    }

    @Override
    public Contract activateContract(UUID contractId) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        Contract contract = contractRepository.findById(contractId)
            .orElseThrow(() -> new ContractNotFoundException("Không tìm thấy hợp đồng: " + contractId));

        // Kiểm tra quyền trước trạng thái để người ngoài phạm vi nhận 403, không biết được trạng thái hợp đồng.
        Room room = validateOwnershipOfRoom(currentUser, contract.getPropertyId(), contract.getRoomId());

        if (contract.getStatus() != ContractStatus.DRAFT) {
            throw new InvalidContractStateException(String.format(
                "Chỉ có thể kích hoạt hợp đồng ở trạng thái DRAFT. Trạng thái hiện tại: '%s'.",
                contract.getStatus()
            ));
        }

        boolean checkInApproved = handoverRepository != null
            && handoverRepository.find(contractId, HandoverType.CHECK_IN)
                .filter(handover -> handover.status() == HandoverStatus.APPROVED && handover.confirmedByTenant())
                .isPresent();
        if (!checkInApproved) {
            throw new InvalidContractStateException("Cần có biên bản check-in được người thuê xác nhận và duyệt trước khi kích hoạt hợp đồng.");
        }

        // Invariant: Do not allow two active contracts for one room
        Optional<Contract> activeContract = contractRepository.findActiveByRoomId(room.getId());
        if (activeContract.isPresent() && !activeContract.get().getId().equals(contract.getId())) {
            throw new DuplicateActiveContractException(String.format(
                "Phòng '%s' đã có hợp đồng đang hiệu lực (mã %s).", room.getRoomNumber(), activeContract.get().getId()
            ));
        }

        LocalDate today = LocalDate.now();
        List<UUID> residingTenantIds = java.util.stream.Stream.concat(
                java.util.stream.Stream.of(contract.getPrimaryTenantId()),
                contract.getOccupants().stream()
                    .filter(o -> o.checkOutDate() == null || o.checkOutDate().isAfter(today))
                    .map(ContractOccupant::tenantId))
            .distinct().sorted().toList();
        residingTenantIds.forEach(this::assertTenantHasActiveAccount);
        // A stable lock order avoids deadlocks when two drafts share multiple tenants.
        residingTenantIds.forEach(tenantRepository::findByIdForUpdate);
        residingTenantIds.forEach(tenantId -> assertTenantHasNoActiveContract(tenantId, today));

        // Invariant: Activating contract changes the room to OCCUPIED
        contract.activate();
        room.occupy();

        roomRepository.save(room);
        Contract saved = contractRepository.save(contract);
        if (monthlyInvoiceUseCase != null) {
            LocalDate activationDate = saved.billingAnchorDate();
            boolean issued = monthlyInvoiceUseCase.issueForContract(saved.getId(),
                java.time.YearMonth.from(activationDate), activationDate);
            if (!issued) {
                throw new DomainException("Không tạo được hóa đơn tiền thuê kỳ đầu khi kích hoạt hợp đồng.");
            }
        }
        audit("CONTRACT_ACTIVATED", currentUser, saved, Map.of(
            "oldStatus", ContractStatus.DRAFT.name(), "newStatus", saved.getStatus().name()));
        return saved;
    }

    @Override
    public Contract terminateContract(UUID contractId, boolean requiresMaintenance) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        Contract contract = contractRepository.findById(contractId)
            .orElseThrow(() -> new ContractNotFoundException("Không tìm thấy hợp đồng: " + contractId));

        Room room = validateOwnershipOfRoom(currentUser, contract.getPropertyId(), contract.getRoomId());
        authorizationService.assertCanManageContracts(currentUser, contract.getPropertyId());
        assertResiding(contract);
        Handover checkOut = handoverRepository == null ? null
            : handoverRepository.find(contractId, HandoverType.CHECK_OUT).orElse(null);
        if (checkOut == null || checkOut.status() != HandoverStatus.APPROVED || !checkOut.confirmedByTenant()) {
            throw new com.phonghub.domain.exception.ConflictException(
                "Hợp đồng chỉ được kết thúc sau khi biên bản check-out đã được người thuê xác nhận và duyệt.");
        }
        return terminate(currentUser, contract, room, requiresMaintenance, Map.of());
    }

    @Override
    public Contract terminateEarly(UUID contractId, boolean requiresMaintenance, String reason) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        Contract contract = contractRepository.findById(contractId)
            .orElseThrow(() -> new ContractNotFoundException("Không tìm thấy hợp đồng: " + contractId));
        Room room = validateOwnershipOfRoom(currentUser, contract.getPropertyId(), contract.getRoomId());
        authorizationService.assertCanManageContracts(currentUser, contract.getPropertyId());
        assertResiding(contract);
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Cần nhập lý do chấm dứt hợp đồng.");
        }
        String trimmed = reason.trim();
        if (trimmed.length() > 500) {
            throw new IllegalArgumentException("Lý do chấm dứt tối đa 500 ký tự.");
        }
        throw new com.phonghub.domain.exception.ConflictException(
            "Hợp đồng không thể chấm dứt trực tiếp. Hãy hoàn tất biên bản check-out với lý do: " + trimmed);
    }

    private static void assertResiding(Contract contract) {
        if (!contract.isResiding()) {
            throw new InvalidContractStateException(String.format(
                "Chỉ có thể kết thúc hợp đồng đang hiệu lực hoặc đã hết hạn nhưng chưa trả phòng. Trạng thái hiện tại: '%s'.",
                contract.getStatus()
            ));
        }
    }

    private Contract terminate(
        CurrentUser currentUser, Contract contract, Room room, boolean requiresMaintenance, Map<String, Object> extra
    ) {
        // Invariant: Ending a contract changes room to AVAILABLE unless maintenance is explicitly required
        ContractStatus oldStatus = contract.getStatus();
        contract.terminate();
        room.vacate(requiresMaintenance);

        roomRepository.save(room);
        Contract saved = contractRepository.save(contract);
        Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("oldStatus", oldStatus.name());
        details.put("newStatus", saved.getStatus().name());
        details.put("requiresMaintenance", requiresMaintenance);
        details.putAll(extra);
        audit("CONTRACT_TERMINATED", currentUser, saved, details);
        return saved;
    }

    @Override
    public Contract renewContract(RenewContractCommand command) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        Contract contract = contractRepository.findById(command.contractId())
            .orElseThrow(() -> new ContractNotFoundException("Không tìm thấy hợp đồng: " + command.contractId()));
        // Quyền trước trạng thái: người ngoài phạm vi nhận 403, không biết được trạng thái hợp đồng.
        validateOwnershipOfRoom(currentUser, contract.getPropertyId(), contract.getRoomId());
        if (command.expectedVersion() != null && command.expectedVersion() != contract.getVersion()) {
            throw new com.phonghub.domain.exception.OptimisticLockConflictException("Hợp đồng");
        }

        Map<String, Object> before = terms(contract);
        ContractStatus oldStatus = contract.getStatus();
        contract.renew(command.newEndDate(), command.newRentAmount(), LocalDate.now());
        if (oldStatus != ContractStatus.ACTIVE) {
            // Hợp đồng hết hạn trở lại hiệu lực: phòng không được có hợp đồng hiệu lực khác
            contractRepository.findActiveByRoomId(contract.getRoomId())
                .filter(other -> !other.getId().equals(contract.getId()))
                .ifPresent(other -> {
                    throw new DuplicateActiveContractException(
                        "Phòng đã có hợp đồng khác đang hiệu lực nên không gia hạn được hợp đồng này.");
                });
        }
        Contract saved = contractRepository.save(contract);
        audit("CONTRACT_RENEWED", currentUser, saved, Map.of(
            "oldStatus", oldStatus.name(), "newStatus", saved.getStatus().name(),
            "before", before, "after", terms(saved)));
        return saved;
    }

    @Override
    public Contract updateDraftContract(UpdateDraftContractCommand command) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        Contract contract = contractRepository.findById(command.contractId())
            .orElseThrow(() -> new ContractNotFoundException("Không tìm thấy hợp đồng: " + command.contractId()));
        // Quyền trước trạng thái: người ngoài phạm vi nhận 403, không biết được trạng thái hợp đồng.
        validateOwnershipOfRoom(currentUser, contract.getPropertyId(), contract.getRoomId());
        if (command.expectedVersion() != null && command.expectedVersion() != contract.getVersion()) {
            throw new com.phonghub.domain.exception.OptimisticLockConflictException("Hợp đồng");
        }

        Map<String, Object> before = terms(contract);
        contract.updateDraftTerms(command.depositAmount(), command.rentAmount(), command.startDate(),
            command.endDate(), command.paymentDay());
        Contract saved = contractRepository.save(contract);
        audit("CONTRACT_UPDATED", currentUser, saved, Map.of("before", before, "after", terms(saved)));
        return saved;
    }

    @Override
    public Contract cancelDraftContract(UUID contractId) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        Contract contract = contractRepository.findById(contractId)
            .orElseThrow(() -> new ContractNotFoundException("Không tìm thấy hợp đồng: " + contractId));
        validateOwnershipOfRoom(currentUser, contract.getPropertyId(), contract.getRoomId());

        if (contract.getStatus() != ContractStatus.DRAFT) {
            throw new InvalidContractStateException("Chỉ có thể hủy hợp đồng ở trạng thái DRAFT.");
        }
        if (draftHandoverCleanupPort != null) {
            draftHandoverCleanupPort.cancelDraftCheckIn(contractId, currentUser == null ? null : currentUser.id());
        }

        contract.cancelDraft();
        Contract saved = contractRepository.save(contract);
        audit("CONTRACT_CANCELLED", currentUser, saved, Map.of(
            "oldStatus", ContractStatus.DRAFT.name(), "newStatus", saved.getStatus().name()));
        return saved;
    }

    private static Map<String, Object> terms(Contract contract) {
        Map<String, Object> terms = new java.util.LinkedHashMap<>();
        terms.put("depositAmount", contract.getDepositAmount().toPlainString());
        terms.put("rentAmount", contract.getRentAmount().toPlainString());
        terms.put("startDate", contract.getStartDate().toString());
        terms.put("endDate", contract.getEndDate().toString());
        terms.put("paymentDay", contract.getPaymentDay());
        return terms;
    }

    private void audit(String action, CurrentUser actor, Contract contract, Map<String, Object> details) {
        if (auditPort != null) {
            auditPort.recordEvent(AuditPort.AuditEvent.of(action, actor == null ? null : actor.id(), "CONTRACT",
                contract.getId().toString(), contract.getPropertyId(), details));
        }
    }

    @Override
    public Contract getContract(UUID contractId) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        Contract contract = contractRepository.findById(contractId)
            .orElseThrow(() -> new ContractNotFoundException("Không tìm thấy hợp đồng: " + contractId));

        if (currentUser.role() == UserRole.TENANT) {
            Optional<Tenant> tenantOpt = tenantRepository.findByUserId(currentUser.id());
            if (tenantOpt.isEmpty()) {
                throw new UnauthorizedPropertyAccessException("Không tìm thấy hồ sơ người thuê của tài khoản này.");
            }
            boolean isAuthorized = contract.getPrimaryTenantId().equals(tenantOpt.get().id()) ||
                contract.getOccupants().stream().anyMatch(o -> o.tenantId().equals(tenantOpt.get().id()));
            if (!isAuthorized) {
                throw new UnauthorizedPropertyAccessException("Người thuê chỉ xem được hợp đồng của mình.");
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
    public List<Tenant> listAvailablePrimaryTenants(UUID propertyId, String query) {
        CurrentUser actor = currentUserPort.getCurrentUser();
        authorizationService.assertCanManageContracts(actor, propertyId);
        if (userRepository == null) {
            return List.of();
        }

        String q = query == null ? "" : query.trim();
        String digits = q.replaceAll("\\D", "");
        String foldedQuery = foldTenantSearch(q);
        List<TenantRepositoryPort.PropertyLink> links = tenantRepository.findLinks(propertyId);
        Set<UUID> visibleLinkedIds = links.stream()
            .filter(link -> link.hiddenAt() == null)
            .map(TenantRepositoryPort.PropertyLink::tenantId)
            .collect(java.util.stream.Collectors.toSet());
        Set<UUID> hiddenLinkedIds = links.stream()
            .filter(link -> link.hiddenAt() != null)
            .map(TenantRepositoryPort.PropertyLink::tenantId)
            .collect(java.util.stream.Collectors.toSet());
        Set<UUID> localProfileIds = new java.util.HashSet<>(visibleLinkedIds);
        contractRepository.findByPropertyId(propertyId).forEach(contract -> {
            localProfileIds.add(contract.getPrimaryTenantId());
            contract.getOccupants().forEach(occupant -> localProfileIds.add(occupant.tenantId()));
        });

        List<Tenant> candidates;
        boolean exactCardSearch = digits.matches("\\d{9}|\\d{12}");
        if (exactCardSearch) {
            candidates = tenantRepository.findByIdentityCardNumber(digits).stream().toList();
        } else {
            List<Tenant> localCandidates = tenantRepository.findAllByIds(localProfileIds);
            boolean suffixSearch = q.matches("\\d+") && digits.length() >= 3;
            if (suffixSearch) {
                candidates = localCandidates;
            } else {
                candidates = java.util.stream.Stream.concat(localCandidates.stream(),
                        tenantRepository.findAccountProfilesWithoutPropertyLinks().stream())
                    .collect(java.util.stream.Collectors.toMap(Tenant::id, tenant -> tenant, (first, ignored) -> first))
                    .values().stream().toList();
            }
        }

        return candidates.stream()
            .filter(tenant -> tenant.userId() != null)
            .filter(tenant -> userRepository.findById(tenant.userId())
                .filter(user -> user.role() == UserRole.TENANT && user.status() == User.UserStatus.ACTIVE).isPresent())
            .filter(tenant -> isTenantSelectableForProperty(tenant, propertyId, visibleLinkedIds, hiddenLinkedIds))
            .filter(tenant -> !tenantResidesAnywhere(tenant.id()))
            .filter(tenant -> tenantMatchesQuery(tenant, q, digits, foldedQuery, localProfileIds.contains(tenant.id())))
            .sorted(java.util.Comparator.comparing((Tenant tenant) -> foldTenantSearch(tenant.fullName()))
                .thenComparing(Tenant::id))
            .toList();
    }

    private boolean isTenantSelectableForProperty(Tenant tenant, UUID propertyId, Set<UUID> visibleLinkedIds,
                                                  Set<UUID> hiddenLinkedIds) {
        if (hiddenLinkedIds.contains(tenant.id())) return false;
        if (visibleLinkedIds.contains(tenant.id())) return true;
        boolean hasDirectLinks = !tenantRepository.findLinkedPropertyIds(tenant.id()).isEmpty();
        boolean hasContractHistory = tenantHasContractHistory(tenant.id());
        if (hasDirectLinks || hasContractHistory) {
            return contractRepository.findByPrimaryTenantId(tenant.id()).stream()
                .anyMatch(contract -> contract.getPropertyId().equals(propertyId))
                || contractRepository.findByOccupantTenantId(tenant.id()).stream()
                    .anyMatch(contract -> contract.getPropertyId().equals(propertyId));
        }
        // Admin-created accounts with no property link or contract may be selected by any property.
        return true;
    }

    private boolean tenantHasContractHistory(UUID tenantId) {
        return !contractRepository.findByPrimaryTenantId(tenantId).isEmpty()
            || !contractRepository.findByOccupantTenantId(tenantId).isEmpty();
    }

    private boolean tenantResidesAnywhere(UUID tenantId) {
        return contractRepository.findByPrimaryTenantId(tenantId).stream().anyMatch(Contract::isResiding)
            || contractRepository.findByOccupantTenantId(tenantId).stream()
                .filter(Contract::isResiding)
                .anyMatch(contract -> contract.getOccupants().stream()
                    .anyMatch(occupant -> occupant.tenantId().equals(tenantId)
                        && (occupant.checkOutDate() == null || occupant.checkOutDate().isAfter(LocalDate.now()))));
    }

    private boolean tenantMatchesQuery(Tenant tenant, String q, String digits, String foldedQuery, boolean linkedHere) {
        if (q.isEmpty()) return true;
        if (foldTenantSearch(tenant.fullName()).contains(foldedQuery)) return true;
        if (digits.matches("\\d{9}|\\d{12}")) return tenant.identityCardNumber().equals(digits);
        return linkedHere && digits.length() >= 3 && tenant.identityCardNumber().endsWith(digits);
    }

    private static String foldTenantSearch(String text) {
        if (text == null) return "";
        String decomposed = java.text.Normalizer.normalize(text.trim(), java.text.Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "").replace('đ', 'd').replace('Đ', 'D')
            .toLowerCase(java.util.Locale.ROOT);
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
        // Hợp đồng đã hết hạn nhưng chưa check-out (#87): người thuê vẫn ở và vẫn cần thanh toán/check-out.
        return java.util.stream.Stream.concat(primaryContracts.stream(), occupantContracts.stream())
            .filter(Contract::isResiding)
            .findFirst();
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
    public AddOccupantResult addOccupant(AddOccupantCommand command) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        LocalDate today = LocalDate.now();
        OccupantContext context = loadOccupantContext(command.contractId(), currentUser);
        assertRoomHasSlots(context, 1, today);
        PreparedOccupant prepared = prepareOccupant(context, command, currentUser, today);

        List<TenantAccountLinker.Result> createdAccounts = new ArrayList<>();
        try {
            AddOccupantResult result = applyOccupant(context, prepared, currentUser, createdAccounts);
            contractRepository.save(context.contract());
            return result;
        } catch (RuntimeException ex) {
            createdAccounts.forEach(account -> accountLinker.deleteCreatedIdentityUser(account, ex));
            throw ex;
        }
    }

    /**
     * Thêm nhiều người một lượt: kiểm tra TOÀN BỘ trước (chỗ trống, trùng, đang ở nơi khác, tài khoản), chỉ khi tất cả hợp lệ
     * mới ghi, nên một người lỗi thì không có gì bị ghi dở.
     */
    @Override
    public AddOccupantsResult addOccupants(AddOccupantsCommand command) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        LocalDate today = LocalDate.now();
        OccupantContext context = loadOccupantContext(command.contractId(), currentUser);
        assertRoomHasSlots(context, command.occupants().size(), today);

        Set<UUID> profileIds = new HashSet<>();
        Set<String> identityCards = new HashSet<>();
        for (AddOccupantCommand occupant : command.occupants()) {
            if (occupant.tenantProfileId() != null && !profileIds.add(occupant.tenantProfileId())) {
                throw new DomainException("Không thể chọn trùng một hồ sơ người thuê trong cùng lượt thêm.");
            }
            if (occupant.identityCardNumber() != null && !occupant.identityCardNumber().isBlank()
                && !identityCards.add(occupant.identityCardNumber().trim())) {
                throw new DomainException("Danh sách có số CCCD bị trùng.");
            }
        }

        // Bước 1: kiểm tra hết, chưa ghi gì
        List<PreparedOccupant> prepared = new ArrayList<>();
        Set<UUID> tenantIds = new HashSet<>();
        for (AddOccupantCommand occupant : command.occupants()) {
            PreparedOccupant row = prepareOccupant(context, occupant, currentUser, today);
            if (row.existing() != null && !tenantIds.add(row.existing().id())) {
                throw new DomainException(row.existing().fullName() + " bị chọn trùng trong cùng lượt thêm.");
            }
            prepared.add(row);
        }
        if (prepared.size() > 1 && prepared.stream().anyMatch(this::needsNewAccount)) {
            throw new DomainException("Không thể tạo tài khoản đăng nhập mới cho nhiều người cùng lúc; hãy thêm từng người nếu cần cấp tài khoản.");
        }

        // Bước 2: ghi, lưu hợp đồng một lần
        List<TenantAccountLinker.Result> createdAccounts = new ArrayList<>();
        try {
            List<AddOccupantResult> results = new ArrayList<>();
            for (PreparedOccupant row : prepared) {
                results.add(applyOccupant(context, row, currentUser, createdAccounts));
            }
            contractRepository.save(context.contract());
            return new AddOccupantsResult(results);
        } catch (RuntimeException ex) {
            createdAccounts.forEach(account -> accountLinker.deleteCreatedIdentityUser(account, ex));
            throw ex;
        }
    }

    /** Hợp đồng, nhà trọ và phòng của lượt thêm người, đã kiểm tra quyền và trạng thái. */
    private record OccupantContext(Contract contract, Property property, Room room) {}

    /** Một người đã qua kiểm tra, chưa ghi gì. {@code existing} là hồ sơ có sẵn, null nghĩa là người mới. */
    private record PreparedOccupant(AddOccupantCommand command, Tenant existing, User selectedUser) {}

    private OccupantContext loadOccupantContext(UUID contractId, CurrentUser currentUser) {
        Contract contract = contractRepository.findById(contractId)
            .orElseThrow(() -> new ContractNotFoundException("Không tìm thấy hợp đồng: " + contractId));
        authorizationService.assertCanManageContracts(currentUser, contract.getPropertyId());

        Property property = propertyRepository.findById(contract.getPropertyId())
            .orElseThrow(() -> new PropertyNotFoundException("Không tìm thấy nhà trọ: " + contract.getPropertyId()));
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
            .orElseThrow(() -> new RoomNotFoundException("Không tìm thấy phòng: " + contract.getRoomId()));
        return new OccupantContext(contract, property, room);
    }

    /** Chỗ trống tính bằng một hàm duy nhất ({@link Contract#residingOccupantCount}) cho mọi đường thêm người. */
    private void assertRoomHasSlots(OccupantContext context, int adding, LocalDate today) {
        int max = context.room().getMaxOccupants();
        long residing = context.contract().residingOccupantCount(today);
        if (residing + adding > max) {
            throw new InvalidRoomCapacityException(String.format(
                "Chỉ còn %d chỗ trống trong phòng '%s' (sức chứa tối đa: %d người); không thể thêm %d người.",
                Math.max(0, max - residing), context.room().getRoomNumber(), max, adding));
        }
    }

    /**
     * Một người chỉ vào được phòng khi chưa có trong hợp đồng này và không đang ở theo hợp đồng khác. Dùng chung cho thêm
     * một người, thêm theo lô và tích chọn hồ sơ có sẵn; thông báo nêu tên người vi phạm.
     */
    private void assertCanJoin(Contract contract, Tenant tenant, LocalDate today) {
        if (tenantRepository.findByIdForUpdate(tenant.id()).isEmpty()) {
            throw new DomainException(tenant.fullName() + " không còn tồn tại trong hệ thống.");
        }
        UUID tenantId = tenant.id();
        if (tenantId.equals(contract.getPrimaryTenantId())
            || contract.getOccupants().stream().anyMatch(occupant -> occupant.tenantId().equals(tenantId))) {
            // 409 như trước: người này đã ở theo chính hợp đồng này
            throw new DuplicateActiveContractException(tenant.fullName() + " đã có trong danh sách người ở của hợp đồng.");
        }
        if (tenantResidesElsewhere(tenantId, contract.getId(), today)) {
            throw new DuplicateActiveContractException(tenant.fullName()
                + " hiện đang ở theo một hợp đồng khác (còn hiệu lực hoặc đã hết hạn nhưng chưa trả phòng).");
        }
    }

    /** Tìm hồ sơ và kiểm tra một người sẽ được thêm. Không ghi gì. */
    private PreparedOccupant prepareOccupant(OccupantContext context, AddOccupantCommand command,
                                             CurrentUser currentUser, LocalDate today) {
        User selectedUser = resolvePrimaryTenantUser(command.tenantUserId());
        Tenant existing;
        if (command.tenantProfileId() != null) {
            existing = tenantRepository.findById(command.tenantProfileId())
                .orElseThrow(() -> new DomainException("Không tìm thấy hồ sơ người thuê đã chọn."));
            authorizationService.assertCanAccessTenant(currentUser, existing.id());
            if (command.identityCardNumber() != null && !command.identityCardNumber().isBlank()
                && !existing.identityCardNumber().equals(command.identityCardNumber().trim())) {
                throw new DomainException("CCCD không khớp với hồ sơ người thuê đã chọn.");
            }
        } else {
            existing = tenantRepository.findByIdentityCardNumber(command.identityCardNumber().trim()).orElse(null);
        }
        if (existing != null) {
            if (selectedUser != null) {
                accountLinker.assertCanLink(existing, selectedUser);
            }
            assertCanJoin(context.contract(), existing, today);
        } else if (selectedUser != null && tenantRepository.findByUserId(selectedUser.id()).isPresent()) {
            throw new DomainException("Tài khoản này đã được liên kết với hồ sơ người thuê khác.");
        }
        PreparedOccupant prepared = new PreparedOccupant(command, existing, selectedUser);
        if (needsNewAccount(prepared) && (command.email() == null || command.email().isBlank())) {
            String name = existing != null ? existing.fullName() : command.fullName();
            throw new IllegalArgumentException(
                "Người ở cùng phải có tài khoản; nhập email để cấp tài khoản cho " + name + ".");
        }
        return prepared;
    }

    /** Người này chưa có tài khoản và không chọn tài khoản sẵn có, nên phải tạo hoặc gắn theo email. */
    private boolean needsNewAccount(PreparedOccupant row) {
        boolean hasAccount = row.selectedUser() != null || (row.existing() != null && row.existing().userId() != null);
        return !hasAccount;
    }

    /** Ghi một người đã qua kiểm tra vào hợp đồng (chưa lưu hợp đồng, người gọi lưu một lần). */
    private AddOccupantResult applyOccupant(OccupantContext context, PreparedOccupant prepared, CurrentUser currentUser,
                                            List<TenantAccountLinker.Result> createdAccounts) {
        AddOccupantCommand command = prepared.command();
        User selectedUser = prepared.selectedUser();
        Tenant tenant = prepared.existing();
        if (tenant == null) {
            tenant = tenantRepository.save(Tenant.create(selectedUser == null ? null : selectedUser.id(),
                command.fullName().trim(), command.identityCardNumber().trim(), command.phone().trim(),
                selectedUser != null ? selectedUser.email() : cleanEmail(command.email()),
                cleanAddress(command.permanentAddress())));
        } else if (!tenantRepository.findLinkedPropertyIds(tenant.id()).contains(context.property().id())) {
            // Hồ sơ đến từ nhà trọ khác: gắn vào nhà trọ này để người ở luôn thuộc danh sách người thuê của nhà trọ
            tenantRepository.linkToProperty(tenant.id(), context.property().id(), currentUser.id());
        }

        // Cập nhật thông tin bổ sung nếu tenant đã tồn tại nhưng chưa có email/địa chỉ
        String updateEmail = command.email() != null && !command.email().isBlank()
            ? command.email().trim()
            : tenant.email();
        String updateAddress = command.permanentAddress() != null && !command.permanentAddress().isBlank()
            ? command.permanentAddress().trim()
            : tenant.permanentAddress();
        if (!Objects.equals(updateEmail, tenant.email()) || !Objects.equals(updateAddress, tenant.permanentAddress())) {
            tenant = tenantRepository.save(new Tenant(
                tenant.id(), tenant.userId(), tenant.fullName(), tenant.identityCardNumber(), tenant.phone(),
                updateEmail, updateAddress, tenant.createdAt()));
        }

        // Mọi người ở cùng phải có tài khoản (#219): hồ sơ đã có tài khoản thì dùng, chưa có thì gắn hoặc tạo theo email.
        // Làm sau mọi kiểm tra để không tạo tài khoản đăng nhập thừa khi thêm người bị từ chối.
        TenantAccountLinker.Result account;
        if (tenant.userId() != null) {
            User linkedUser = assertTenantHasActiveAccount(tenant.id());
            account = new TenantAccountLinker.Result(tenant, linkedUser.username(), null, null);
        } else if (selectedUser != null) {
            tenant = accountLinker.linkUser(tenant, selectedUser);
            account = new TenantAccountLinker.Result(tenant, selectedUser.username(), null, null);
        } else {
            account = accountLinker.linkOrCreate(currentUser, tenant, command.email(), "OCCUPANT_ACCOUNT_CREATED");
            createdAccounts.add(account);
            tenant = account.tenant();
        }

        ContractOccupant occupant = context.contract().addOccupant(tenant.id(), false, command.checkInDate());
        return new AddOccupantResult(occupant, account.username(), account.temporaryPassword());
    }

    @Override
    public List<Tenant> listAvailableOccupants(UUID contractId) {
        Contract contract = contractRepository.findById(contractId)
            .orElseThrow(() -> new ContractNotFoundException("Không tìm thấy hợp đồng: " + contractId));
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        authorizationService.assertCanManageContracts(currentUser, contract.getPropertyId());
        if (!contract.isActive()) {
            throw new DomainException("Chỉ có thể chọn người thuê cho hợp đồng đang hiệu lực.");
        }

        Property property = propertyRepository.findById(contract.getPropertyId())
            .orElseThrow(() -> new PropertyNotFoundException("Không tìm thấy nhà trọ: " + contract.getPropertyId()));
        if (property.approvalStatus() != PropertyApprovalStatus.VERIFIED) {
            return List.of();
        }
        Room room = roomRepository.findById(contract.getRoomId())
            .orElseThrow(() -> new RoomNotFoundException("Không tìm thấy phòng: " + contract.getRoomId()));
        LocalDate today = LocalDate.now();
        if (room.getMaxOccupants() <= contract.residingOccupantCount(today)) {
            return List.of();
        }

        java.util.Set<UUID> unavailableIds = new java.util.HashSet<>();
        unavailableIds.add(contract.getPrimaryTenantId());
        contract.getOccupants().forEach(occupant -> unavailableIds.add(occupant.tenantId()));
        List<UUID> linkedIds = tenantRepository.findLinks(property.id()).stream()
            .filter(link -> link.hiddenAt() == null)
            .map(TenantRepositoryPort.PropertyLink::tenantId)
            .filter(id -> !unavailableIds.contains(id))
            .toList();
        return tenantRepository.findAllByIds(linkedIds).stream()
            .filter(tenant -> tenant.userId() != null)
            .filter(tenant -> userRepository != null && userRepository.findById(tenant.userId())
                .filter(user -> user.role() == UserRole.TENANT && user.status() == User.UserStatus.ACTIVE).isPresent())
            .filter(tenant -> !tenantResidesElsewhere(tenant.id(), contract.getId(), today))
            .sorted(java.util.Comparator.comparing(Tenant::fullName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    @Override
    public List<ContractOccupant> addExistingOccupants(AddExistingOccupantsCommand command) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        LocalDate today = LocalDate.now();
        OccupantContext context = loadOccupantContext(command.contractId(), currentUser);
        Contract contract = context.contract();
        assertRoomHasSlots(context, command.tenantIds().size(), today);

        java.util.Set<UUID> visiblePropertyTenantIds = tenantRepository.findLinks(context.property().id()).stream()
            .filter(link -> link.hiddenAt() == null)
            .map(TenantRepositoryPort.PropertyLink::tenantId)
            .collect(java.util.stream.Collectors.toSet());
        if (!visiblePropertyTenantIds.containsAll(command.tenantIds())) {
            throw new DomainException("Chỉ có thể thêm hồ sơ người thuê đang được liên kết với nhà trọ này.");
        }

        List<Tenant> tenants = tenantRepository.findAllByIds(command.tenantIds());
        Map<UUID, Tenant> tenantsById = new HashMap<>();
        tenants.forEach(tenant -> tenantsById.put(tenant.id(), tenant));
        if (tenantsById.size() != command.tenantIds().size()) {
            throw new DomainException("Một hoặc nhiều hồ sơ người thuê không còn tồn tại.");
        }

        if (tenants.stream().anyMatch(tenant -> tenant.userId() == null || userRepository == null
            || userRepository.findById(tenant.userId())
                .filter(user -> user.role() == UserRole.TENANT && user.status() == User.UserStatus.ACTIVE).isEmpty())) {
            throw new DomainException("Người ở cùng phải có tài khoản người thuê đang hoạt động.");
        }

        // Kiểm tra hết trước khi ghi; lỗi nêu tên người vi phạm
        for (UUID tenantId : command.tenantIds()) {
            assertCanJoin(contract, tenantsById.get(tenantId), today);
        }

        List<ContractOccupant> added = command.tenantIds().stream()
            .map(tenantId -> contract.addOccupant(tenantId, false, command.checkInDate()))
            .toList();
        contractRepository.save(contract);
        return added;
    }

    private User assertTenantHasActiveAccount(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new DomainException("Hồ sơ người thuê của hợp đồng không còn tồn tại."));
        if (tenant.userId() == null || userRepository == null) {
            throw new DomainException("Người thuê chính và người ở cùng phải có tài khoản người thuê đang hoạt động.");
        }
        User user = userRepository.findById(tenant.userId())
            .orElseThrow(() -> new DomainException("Tài khoản người thuê của hợp đồng không còn tồn tại."));
        if (user.role() != UserRole.TENANT || user.status() != User.UserStatus.ACTIVE) {
            throw new DomainException("Người thuê chính và người ở cùng phải có tài khoản người thuê đang hoạt động.");
        }
        return user;
    }

    private User resolvePrimaryTenantUser(UUID userId) {
        if (userId == null) {
            return null;
        }
        if (userRepository == null) {
            throw new IllegalArgumentException("Không thể xác thực tài khoản người thuê đã chọn.");
        }
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản người thuê đã chọn."));
        if (user.role() != UserRole.TENANT || user.status() != User.UserStatus.ACTIVE) {
            throw new IllegalArgumentException("Chỉ có thể liên kết tài khoản người thuê đang hoạt động.");
        }
        return user;
    }

    private boolean tenantResidesElsewhere(UUID tenantId, UUID contractId, LocalDate today) {
        boolean primary = contractRepository.findByPrimaryTenantId(tenantId).stream()
            .anyMatch(contract -> !contract.getId().equals(contractId) && contract.isResiding());
        if (primary) {
            return true;
        }
        return contractRepository.findByOccupantTenantId(tenantId).stream()
            .filter(contract -> !contract.getId().equals(contractId) && contract.isResiding())
            .anyMatch(contract -> contract.getOccupants().stream().anyMatch(occupant -> occupant.tenantId().equals(tenantId)
                && (occupant.checkOutDate() == null || occupant.checkOutDate().isAfter(today))));
    }


    private static String cleanEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim();
    }

    private static String cleanAddress(String address) {
        return address == null || address.isBlank() ? null : address.trim();
    }

    /** Một người không ở đồng thời theo hai hợp đồng (kể cả hợp đồng đã hết hạn nhưng chưa trả phòng, #87). */
    private void assertTenantHasNoActiveContract(UUID tenantId, LocalDate today) {
        boolean alreadyActiveAsPrimary = contractRepository.findByPrimaryTenantId(tenantId).stream()
            .anyMatch(Contract::isResiding);
        boolean alreadyActiveAsOccupant = contractRepository.findByOccupantTenantId(tenantId).stream()
            .filter(Contract::isResiding)
            .anyMatch(c -> c.getOccupants().stream().anyMatch(o -> o.tenantId().equals(tenantId)
                && (o.checkOutDate() == null || o.checkOutDate().isAfter(today))));
        if (alreadyActiveAsPrimary || alreadyActiveAsOccupant) {
            throw new DuplicateActiveContractException(
                "Người thuê hiện đang ở theo một hợp đồng khác (còn hiệu lực hoặc đã hết hạn nhưng chưa trả phòng).");
        }
    }

    @Override
    public ContractOccupant checkOutOccupant(CheckOutOccupantCommand command) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        Contract contract = contractRepository.findById(command.contractId())
            .orElseThrow(() -> new ContractNotFoundException("Không tìm thấy hợp đồng: " + command.contractId()));

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
            .orElseThrow(() -> new RoomNotFoundException("Không tìm thấy phòng: " + roomId));
        if (!room.getPropertyId().equals(propertyId)) {
            throw new UnauthorizedPropertyAccessException(String.format(
                "Phòng '%s' (ID: %s) không thuộc về nhà trọ (ID: %s).", room.getRoomNumber(), roomId, propertyId
            ));
        }
        authorizationService.assertCanManageContracts(user, room.getPropertyId());
        return room;
    }
}
