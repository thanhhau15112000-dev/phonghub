package com.phonghub.application.service;

import com.phonghub.application.port.in.ContractUseCase;
import com.phonghub.application.port.in.HandoverUseCase;
import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.FileStoragePort;
import com.phonghub.application.port.out.HandoverItemRepositoryPort;
import com.phonghub.application.port.out.HandoverPhotoRepositoryPort;
import com.phonghub.application.port.out.HandoverRepositoryPort;
import com.phonghub.application.port.out.PropertyHouseRulesRepositoryPort;
import com.phonghub.application.port.out.RoomAssetRepositoryPort;
import com.phonghub.application.port.out.TenantDocumentRepositoryPort;
import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.domain.exception.ContractNotFoundException;
import com.phonghub.domain.exception.HandoverAlreadyExistsException;
import com.phonghub.domain.exception.HandoverNotFoundException;
import com.phonghub.domain.exception.InvalidHandoverStateException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.AssetStatus;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractOccupant;
import com.phonghub.domain.model.DocumentType;
import com.phonghub.domain.model.Handover;
import com.phonghub.domain.model.HandoverItem;
import com.phonghub.domain.model.HandoverPhoto;
import com.phonghub.domain.model.HandoverPhotoKind;
import com.phonghub.domain.model.HandoverStatus;
import com.phonghub.domain.model.HandoverType;
import com.phonghub.domain.model.PropertyHouseRules;
import com.phonghub.domain.model.RoomAsset;
import com.phonghub.domain.model.Tenant;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class HandoverService implements HandoverUseCase {

    private static final int MAX_PHOTOS = 20;
    private static final Duration SIGNED_URL_TTL = Duration.ofMinutes(10);

    private final HandoverRepositoryPort handoverRepository;
    private final HandoverPhotoRepositoryPort photoRepository;
    private final HandoverItemRepositoryPort itemRepository;
    private final PropertyHouseRulesRepositoryPort rulesRepository;
    private final ContractRepositoryPort contractRepository;
    private final RoomAssetRepositoryPort assetRepository;
    private final TenantRepositoryPort tenantRepository;
    private final TenantDocumentRepositoryPort documentRepository;
    private final ContractUseCase contractUseCase;
    private final FileStoragePort storagePort;
    private final CurrentUserPort currentUserPort;
    private final AuthorizationService authorizationService;
    private final AuditPort auditPort;
    private final long maxFileSizeBytes;
    private final NotificationDispatcher notifications;

    public HandoverService(
        HandoverRepositoryPort handoverRepository,
        HandoverPhotoRepositoryPort photoRepository,
        HandoverItemRepositoryPort itemRepository,
        PropertyHouseRulesRepositoryPort rulesRepository,
        ContractRepositoryPort contractRepository,
        RoomAssetRepositoryPort assetRepository,
        TenantRepositoryPort tenantRepository,
        TenantDocumentRepositoryPort documentRepository,
        ContractUseCase contractUseCase,
        FileStoragePort storagePort,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        AuditPort auditPort,
        long maxFileSizeBytes
    ) {
        this(handoverRepository, photoRepository, itemRepository, rulesRepository, contractRepository, assetRepository,
            tenantRepository, documentRepository, contractUseCase, storagePort, currentUserPort, authorizationService,
            auditPort, maxFileSizeBytes, null);
    }

    /** {@code notifications} null thì không gửi thông báo yêu cầu xác nhận/duyệt biên bản (#86). */
    public HandoverService(
        HandoverRepositoryPort handoverRepository,
        HandoverPhotoRepositoryPort photoRepository,
        HandoverItemRepositoryPort itemRepository,
        PropertyHouseRulesRepositoryPort rulesRepository,
        ContractRepositoryPort contractRepository,
        RoomAssetRepositoryPort assetRepository,
        TenantRepositoryPort tenantRepository,
        TenantDocumentRepositoryPort documentRepository,
        ContractUseCase contractUseCase,
        FileStoragePort storagePort,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        AuditPort auditPort,
        long maxFileSizeBytes,
        NotificationDispatcher notifications
    ) {
        this.notifications = notifications;
        this.handoverRepository = handoverRepository;
        this.photoRepository = photoRepository;
        this.itemRepository = itemRepository;
        this.rulesRepository = rulesRepository;
        this.contractRepository = contractRepository;
        this.assetRepository = assetRepository;
        this.tenantRepository = tenantRepository;
        this.documentRepository = documentRepository;
        this.contractUseCase = contractUseCase;
        this.storagePort = storagePort;
        this.currentUserPort = currentUserPort;
        this.authorizationService = authorizationService;
        this.auditPort = auditPort;
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    @Override
    public Handover create(HandoverType type, HandoverCommand command) {
        CurrentUser user = currentUserPort.getCurrentUser();
        Contract contract = findContract(command.contractId());
        authorizationService.assertCanManageContracts(user, contract.getPropertyId());
        // Check-in cần hợp đồng đang hiệu lực; check-out được cả khi hợp đồng đã hết hạn mà chưa trả phòng (#87).
        boolean allowed = type == HandoverType.CHECK_OUT ? contract.isResiding() : contract.isActive();
        if (!allowed) {
            throw new InvalidHandoverStateException(String.format(
                "Chỉ lập biên bản cho hợp đồng đang hiệu lực%s. Trạng thái hiện tại: %s.",
                type == HandoverType.CHECK_OUT ? " hoặc đã hết hạn nhưng chưa trả phòng" : " (ACTIVE)",
                contract.getStatus()));
        }
        Handover checkIn = handoverRepository.find(contract.getId(), HandoverType.CHECK_IN).orElse(null);
        if (type == HandoverType.CHECK_OUT
            && (checkIn == null || checkIn.status() != HandoverStatus.APPROVED)) {
            throw new InvalidHandoverStateException(
                "Cần có biên bản check-in đã được duyệt trước khi check-out.");
        }
        if (handoverRepository.find(contract.getId(), type).isPresent()) {
            throw new HandoverAlreadyExistsException();
        }
        Handover saved = handoverRepository.save(Handover.draft(contract.getId(), type,
            command.initialElectricMeter(), command.initialWaterMeter(), command.roomCondition(),
            command.keysProvided(), user.id()));
        if (type == HandoverType.CHECK_OUT) {
            List<HandoverItem> start = itemRepository.findByHandoverId(checkIn.id()).stream()
                .map(item -> new HandoverItem(UUID.randomUUID(), saved.id(), item.assetId(), item.assetName(),
                    item.quantity(), item.status(), null, BigDecimal.ZERO))
                .toList();
            itemRepository.replaceAll(saved.id(), start, user.id());
        }
        audit(type, "CREATED", user, saved, Map.of());
        return saved;
    }

    @Override
    public Handover update(HandoverType type, HandoverCommand command) {
        CurrentUser user = currentUserPort.getCurrentUser();
        Contract contract = findContract(command.contractId());
        authorizationService.assertCanManageContracts(user, contract.getPropertyId());
        Handover handover = findHandover(contract.getId(), type);
        Handover saved = handoverRepository.save(handover.edit(command.initialElectricMeter(),
            command.initialWaterMeter(), command.roomCondition(), command.keysProvided(), user.id()));
        audit(type, "UPDATED", user, saved, Map.of());
        return saved;
    }

    @Override
    public HandoverPhoto addPhoto(HandoverType type, UUID contractId, HandoverPhotoKind kind, String contentType,
                                  byte[] content) {
        CurrentUser user = currentUserPort.getCurrentUser();
        Contract contract = findContract(contractId);
        authorizationService.assertCanManageContracts(user, contract.getPropertyId());
        Handover handover = findHandover(contractId, type);
        if (handover.status() != HandoverStatus.DRAFT) {
            throw new InvalidHandoverStateException("Chỉ thêm ảnh khi biên bản ở trạng thái DRAFT.");
        }
        if (kind == null) {
            throw new IllegalArgumentException("Photo kind is required");
        }
        if (!FileSanitizer.isImage(contentType)) {
            throw new IllegalArgumentException("Handover photos must be jpeg, png or webp");
        }
        if (photoRepository.findByHandoverId(handover.id()).size() >= MAX_PHOTOS) {
            throw new IllegalArgumentException("A handover can have at most " + MAX_PHOTOS + " photos");
        }
        FileSanitizer.SanitizedFile file = FileSanitizer.sanitize(contentType, content, maxFileSizeBytes);
        String key = PrivateFileStorageService.objectKey(contract.getPropertyId(), "handover", file.extension());
        storagePort.put(key, file.contentType(), file.content());
        HandoverPhoto saved;
        try {
            saved = photoRepository.save(new HandoverPhoto(UUID.randomUUID(), handover.id(), kind, key,
                file.contentType(), Instant.now(), user.id()));
        } catch (RuntimeException ex) {
            deleteQuietly(key);
            throw ex;
        }
        audit(type, "PHOTO_ADDED", user, handover, Map.of("kind", kind.name()));
        return saved;
    }

    @Override
    public void removePhoto(HandoverType type, UUID contractId, UUID photoId) {
        CurrentUser user = currentUserPort.getCurrentUser();
        Contract contract = findContract(contractId);
        authorizationService.assertCanManageContracts(user, contract.getPropertyId());
        Handover handover = findHandover(contractId, type);
        if (handover.status() != HandoverStatus.DRAFT) {
            throw new InvalidHandoverStateException("Chỉ xóa ảnh khi biên bản ở trạng thái DRAFT.");
        }
        HandoverPhoto photo = photoRepository.findById(photoId)
            .filter(found -> found.handoverId().equals(handover.id()))
            .orElseThrow(() -> new HandoverNotFoundException("Handover photo not found: " + photoId));
        photoRepository.delete(photo.id());
        deleteQuietly(photo.imageKey());
        audit(type, "PHOTO_REMOVED", user, handover, Map.of("kind", photo.kind().name()));
    }

    @Override
    public HandoverItem updateItem(HandoverType type, UUID contractId, UUID itemId, ItemCommand command) {
        CurrentUser user = currentUserPort.getCurrentUser();
        Contract contract = findContract(contractId);
        authorizationService.assertCanManageContracts(user, contract.getPropertyId());
        if (type != HandoverType.CHECK_OUT) {
            throw new InvalidHandoverStateException("Chỉ cập nhật tình trạng tài sản trong biên bản check-out.");
        }
        Handover handover = findHandover(contractId, type);
        if (handover.status() != HandoverStatus.DRAFT) {
            throw new InvalidHandoverStateException("Chỉ cập nhật tài sản khi biên bản ở trạng thái DRAFT.");
        }
        HandoverItem item = itemRepository.findById(itemId)
            .filter(found -> found.handoverId().equals(handover.id()))
            .orElseThrow(() -> new HandoverNotFoundException("Handover item not found: " + itemId));
        if (command.status() == null) {
            throw new IllegalArgumentException("Asset status is required");
        }
        BigDecimal deduction = command.deductionAmount() == null ? BigDecimal.ZERO : command.deductionAmount();
        if (deduction.signum() > 0 && command.status() == AssetStatus.GOOD) {
            throw new IllegalArgumentException("Tài sản còn tốt thì không thể đề xuất khấu trừ");
        }
        HandoverItem saved = itemRepository.save(item.withCondition(command.status(),
            command.conditionNote() == null || command.conditionNote().isBlank() ? null : command.conditionNote().trim(),
            deduction), user.id());
        audit(type, "ITEM_UPDATED", user, handover,
            Map.of("status", saved.status().name(), "deduction", saved.deductionAmount().toPlainString()));
        return saved;
    }

    @Override
    public Handover send(HandoverType type, UUID contractId) {
        CurrentUser user = currentUserPort.getCurrentUser();
        Contract contract = findContract(contractId);
        authorizationService.assertCanManageContracts(user, contract.getPropertyId());
        Handover handover = handoverRepository.findForUpdate(contractId, type)
            .orElseThrow(() -> new HandoverNotFoundException("Handover not found for contract " + contractId));
        if (handover.status() != HandoverStatus.DRAFT) {
            throw new InvalidHandoverStateException("Chỉ gửi biên bản ở trạng thái DRAFT.");
        }
        List<HandoverPhoto> photos = photoRepository.findByHandoverId(handover.id());
        for (HandoverPhotoKind required : HandoverPhotoKind.values()) {
            if (photos.stream().noneMatch(photo -> photo.kind() == required)) {
                throw new IllegalArgumentException("Cần ít nhất một ảnh loại " + required + " trước khi gửi");
            }
        }

        int itemCount;
        if (type == HandoverType.CHECK_IN) {
            List<HandoverItem> items = assetRepository.findActiveByRoomId(contract.getRoomId()).stream()
                .map(asset -> snapshot(handover.id(), asset)).toList();
            itemRepository.replaceAll(handover.id(), items, user.id());
            itemCount = items.size();
        } else {
            itemCount = itemRepository.findByHandoverId(handover.id()).size();
        }
        PropertyHouseRules rules = rulesRepository.findByPropertyId(contract.getPropertyId()).orElse(null);

        Handover saved = handoverRepository.save(handover.send(
            termsHash(contract),
            rules == null ? null : rules.content(),
            rules == null ? null : sha256(rules.content()),
            user.id()));
        audit(type, "SENT", user, saved, Map.of("items", itemCount, "photos", photos.size()));
        if (notifications != null) {
            // Chỉ người thuê chính xác nhận được biên bản (requirePrimaryTenant).
            notifications.notifyTenants(contract, false, "Biên bản " + label(type) + " cần bạn xác nhận",
                "Chủ trọ đã gửi biên bản " + label(type) + ". Vui lòng kiểm tra tài sản, ảnh và xác nhận.",
                link(type, contractId));
        }
        return saved;
    }

    @Override
    public Handover confirm(HandoverType type, UUID contractId) {
        CurrentUser user = currentUserPort.getCurrentUser();
        Contract contract = findContract(contractId);
        Tenant tenant = requirePrimaryTenant(user, contract);
        Handover handover = handoverRepository.findForUpdate(contractId, type)
            .orElseThrow(() -> new HandoverNotFoundException("Handover not found for contract " + contractId));
        if (handover.status() != HandoverStatus.PENDING_TENANT) {
            throw new InvalidHandoverStateException(String.format(
                "Không thể xác nhận biên bản ở trạng thái %s (cần PENDING_TENANT).", handover.status()));
        }
        if (type == HandoverType.CHECK_IN
            && documentRepository.findByTenantIdAndType(tenant.id(), DocumentType.CCCD).isEmpty()) {
            throw new IllegalArgumentException("Vui lòng tải ảnh CCCD trước khi xác nhận biên bản");
        }
        Handover saved = handoverRepository.save(handover.tenantConfirm(user.id()));
        audit(type, "TENANT_CONFIRMED", user, saved, Map.of());
        if (notifications != null) {
            // Người nhận khớp quyền duyệt ở approve(): check-out chỉ chủ trọ, check-in cả staff quản lý hợp đồng.
            notifications.notifyManagers(contract.getPropertyId(), type == HandoverType.CHECK_IN,
                "Biên bản " + label(type) + " chờ duyệt",
                "Người thuê đã xác nhận biên bản " + label(type) + ". Vui lòng kiểm tra và duyệt.",
                link(type, contractId));
        }
        return saved;
    }

    private static String label(HandoverType type) {
        return type == HandoverType.CHECK_IN ? "check-in" : "check-out";
    }

    /** Trang biên bản: /contracts/{id}/handover (check-in) hoặc /contracts/{id}/check-out. */
    private static String link(HandoverType type, UUID contractId) {
        return "/contracts/" + contractId + (type == HandoverType.CHECK_IN ? "/handover" : "/check-out");
    }

    @Override
    public Handover approve(HandoverType type, UUID contractId, BigDecimal finalDeduction) {
        CurrentUser user = currentUserPort.getCurrentUser();
        Contract contract = findContract(contractId);
        if (type == HandoverType.CHECK_OUT) {
            authorizationService.assertOwnerOrAdmin(user, contract.getPropertyId());
        } else {
            authorizationService.assertCanManageContracts(user, contract.getPropertyId());
        }
        Handover handover = handoverRepository.findForUpdate(contractId, type)
            .orElseThrow(() -> new HandoverNotFoundException("Handover not found for contract " + contractId));

        if (type == HandoverType.CHECK_IN) {
            Handover saved = handoverRepository.save(handover.approve(user.id()));
            audit(type, "APPROVED", user, saved, Map.of());
            return saved;
        }

        List<HandoverItem> items = itemRepository.findByHandoverId(handover.id());
        BigDecimal proposed = items.stream().map(HandoverItem::deductionAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal deduction = finalDeduction == null ? proposed : finalDeduction;
        Handover saved = handoverRepository.save(handover.approve(user.id(), deduction));
        boolean needsRepair = items.stream().anyMatch(HandoverItem::needsRepair);
        // Cùng một transaction: nếu kết thúc hợp đồng thất bại thì biên bản vẫn ở trạng thái chờ duyệt.
        contractUseCase.terminateContract(contractId, needsRepair);
        audit(type, "APPROVED", user, saved, Map.of(
            "finalDeduction", deduction.toPlainString(),
            "proposedDeduction", proposed.toPlainString(),
            "roomNeedsRepair", needsRepair));
        return saved;
    }

    @Override
    public Handover reject(HandoverType type, UUID contractId, String reason) {
        CurrentUser user = currentUserPort.getCurrentUser();
        Contract contract = findContract(contractId);
        authorizationService.assertCanManageContracts(user, contract.getPropertyId());
        Handover handover = handoverRepository.findForUpdate(contractId, type)
            .orElseThrow(() -> new HandoverNotFoundException("Handover not found for contract " + contractId));
        Handover saved = handoverRepository.save(handover.reject(reason, user.id()));
        audit(type, "REJECTED", user, saved, Map.of());
        return saved;
    }

    @Override
    public Handover reopen(HandoverType type, UUID contractId) {
        CurrentUser user = currentUserPort.getCurrentUser();
        Contract contract = findContract(contractId);
        authorizationService.assertCanManageContracts(user, contract.getPropertyId());
        Handover handover = handoverRepository.findForUpdate(contractId, type)
            .orElseThrow(() -> new HandoverNotFoundException("Handover not found for contract " + contractId));
        Handover saved = handoverRepository.save(handover.reopen(user.id()));
        audit(type, "REOPENED", user, saved, Map.of());
        return saved;
    }

    @Override
    public HandoverDetails get(HandoverType type, UUID contractId) {
        CurrentUser user = currentUserPort.getCurrentUser();
        Contract contract = findContract(contractId);
        assertCanView(user, contract);
        Handover handover = findHandover(contractId, type);
        return new HandoverDetails(handover, photoRepository.findByHandoverId(handover.id()),
            itemRepository.findByHandoverId(handover.id()));
    }

    @Override
    public URI photoSignedUrl(HandoverType type, UUID contractId, UUID photoId) {
        CurrentUser user = currentUserPort.getCurrentUser();
        Contract contract = findContract(contractId);
        assertCanView(user, contract);
        Handover handover = findHandover(contractId, type);
        HandoverPhoto photo = photoRepository.findById(photoId)
            .filter(found -> found.handoverId().equals(handover.id()))
            .orElseThrow(() -> new HandoverNotFoundException("Handover photo not found: " + photoId));
        return storagePort.createSignedUrl(photo.imageKey(), SIGNED_URL_TTL);
    }

    @Override
    public PropertyHouseRules getHouseRules(UUID propertyId) {
        CurrentUser user = currentUserPort.getCurrentUser();
        authorizationService.assertCanAccessProperty(user, propertyId);
        return rulesRepository.findByPropertyId(propertyId).orElse(null);
    }

    @Override
    public PropertyHouseRules saveHouseRules(UUID propertyId, String content) {
        CurrentUser user = currentUserPort.getCurrentUser();
        authorizationService.assertCanManageProperty(user, propertyId);
        int version = rulesRepository.findByPropertyId(propertyId).map(existing -> existing.version() + 1).orElse(1);
        PropertyHouseRules saved = rulesRepository.save(
            new PropertyHouseRules(propertyId, content, version, Instant.now(), user.id()));
        auditPort.recordEvent(AuditPort.AuditEvent.of("HOUSE_RULES_UPDATED", user.id(), "PROPERTY",
            propertyId.toString(), Map.of("version", saved.version())));
        return saved;
    }

    private void assertCanView(CurrentUser user, Contract contract) {
        if (user != null && user.isTenant()) {
            Tenant tenant = tenantRepository.findByUserId(user.id()).orElse(null);
            boolean linked = tenant != null && (contract.getPrimaryTenantId().equals(tenant.id())
                || contract.getOccupants().stream().map(ContractOccupant::tenantId).anyMatch(tenant.id()::equals));
            if (!linked) {
                throw new UnauthorizedPropertyAccessException("Tenant is not part of this contract");
            }
            return;
        }
        authorizationService.assertCanManageContracts(user, contract.getPropertyId());
    }

    private Tenant requirePrimaryTenant(CurrentUser user, Contract contract) {
        if (user == null || !user.isTenant()) {
            throw new UnauthorizedPropertyAccessException("Only the primary tenant can confirm the handover");
        }
        Tenant tenant = tenantRepository.findByUserId(user.id()).orElse(null);
        if (tenant == null || !tenant.id().equals(contract.getPrimaryTenantId())) {
            throw new UnauthorizedPropertyAccessException("Only the primary tenant can confirm the handover");
        }
        return tenant;
    }

    private HandoverItem snapshot(UUID handoverId, RoomAsset asset) {
        return new HandoverItem(UUID.randomUUID(), handoverId, asset.id(), asset.name(), asset.quantity(),
            asset.status(), asset.note());
    }

    private Contract findContract(UUID contractId) {
        if (contractId == null) {
            throw new IllegalArgumentException("Contract id is required");
        }
        return contractRepository.findById(contractId)
            .orElseThrow(() -> new ContractNotFoundException("Contract not found with ID: " + contractId));
    }

    private Handover findHandover(UUID contractId, HandoverType type) {
        return handoverRepository.find(contractId, type)
            .orElseThrow(() -> new HandoverNotFoundException("Handover not found for contract " + contractId));
    }

    private void deleteQuietly(String key) {
        try {
            storagePort.delete(key);
        } catch (RuntimeException ignored) {
            // best effort
        }
    }

    private void audit(HandoverType type, String step, CurrentUser user, Handover handover,
                       Map<String, Object> extra) {
        Map<String, Object> details = new HashMap<>(extra);
        details.put("contractId", handover.contractId().toString());
        details.put("status", handover.status().name());
        details.put("type", type.name());
        String prefix = type == HandoverType.CHECK_IN ? "HANDOVER_" : "CHECKOUT_";
        auditPort.recordEvent(AuditPort.AuditEvent.of(prefix + step, user.id(), "HANDOVER", handover.id().toString(),
            details));
    }

    static String termsHash(Contract contract) {
        String occupants = contract.getOccupants().stream().map(o -> o.tenantId().toString()).sorted()
            .collect(Collectors.joining(","));
        return sha256(String.join("|", contract.getId().toString(), contract.getPrimaryTenantId().toString(),
            String.valueOf(contract.getStartDate()), String.valueOf(contract.getEndDate()),
            contract.getRentAmount().stripTrailingZeros().toPlainString(),
            contract.getDepositAmount().stripTrailingZeros().toPlainString(),
            String.valueOf(contract.getPaymentDay()), occupants));
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
