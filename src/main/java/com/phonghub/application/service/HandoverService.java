package com.phonghub.application.service;

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
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractOccupant;
import com.phonghub.domain.model.ContractStatus;
import com.phonghub.domain.model.DocumentType;
import com.phonghub.domain.model.Handover;
import com.phonghub.domain.model.HandoverItem;
import com.phonghub.domain.model.HandoverPhoto;
import com.phonghub.domain.model.HandoverPhotoKind;
import com.phonghub.domain.model.HandoverStatus;
import com.phonghub.domain.model.PropertyHouseRules;
import com.phonghub.domain.model.RoomAsset;
import com.phonghub.domain.model.Tenant;
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
    private final FileStoragePort storagePort;
    private final CurrentUserPort currentUserPort;
    private final AuthorizationService authorizationService;
    private final AuditPort auditPort;
    private final long maxFileSizeBytes;

    public HandoverService(
        HandoverRepositoryPort handoverRepository,
        HandoverPhotoRepositoryPort photoRepository,
        HandoverItemRepositoryPort itemRepository,
        PropertyHouseRulesRepositoryPort rulesRepository,
        ContractRepositoryPort contractRepository,
        RoomAssetRepositoryPort assetRepository,
        TenantRepositoryPort tenantRepository,
        TenantDocumentRepositoryPort documentRepository,
        FileStoragePort storagePort,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        AuditPort auditPort,
        long maxFileSizeBytes
    ) {
        this.handoverRepository = handoverRepository;
        this.photoRepository = photoRepository;
        this.itemRepository = itemRepository;
        this.rulesRepository = rulesRepository;
        this.contractRepository = contractRepository;
        this.assetRepository = assetRepository;
        this.tenantRepository = tenantRepository;
        this.documentRepository = documentRepository;
        this.storagePort = storagePort;
        this.currentUserPort = currentUserPort;
        this.authorizationService = authorizationService;
        this.auditPort = auditPort;
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    @Override
    public Handover create(HandoverCommand command) {
        CurrentUser user = currentUserPort.getCurrentUser();
        Contract contract = findContract(command.contractId());
        authorizationService.assertCanManageContracts(user, contract.getPropertyId());
        if (contract.getStatus() != ContractStatus.ACTIVE) {
            throw new InvalidHandoverStateException(String.format(
                "Chỉ check-in cho hợp đồng đang hiệu lực (ACTIVE). Trạng thái hiện tại: %s.", contract.getStatus()));
        }
        if (handoverRepository.findByContractId(contract.getId()).isPresent()) {
            throw new HandoverAlreadyExistsException();
        }
        Handover saved = handoverRepository.save(Handover.draft(contract.getId(), command.initialElectricMeter(),
            command.initialWaterMeter(), command.roomCondition(), command.keysProvided(), user.id()));
        audit("HANDOVER_CREATED", user, saved, Map.of());
        return saved;
    }

    @Override
    public Handover update(HandoverCommand command) {
        CurrentUser user = currentUserPort.getCurrentUser();
        Contract contract = findContract(command.contractId());
        authorizationService.assertCanManageContracts(user, contract.getPropertyId());
        Handover handover = findHandover(contract.getId());
        Handover saved = handoverRepository.save(handover.edit(command.initialElectricMeter(),
            command.initialWaterMeter(), command.roomCondition(), command.keysProvided(), user.id()));
        audit("HANDOVER_UPDATED", user, saved, Map.of());
        return saved;
    }

    @Override
    public HandoverPhoto addPhoto(UUID contractId, HandoverPhotoKind kind, String contentType, byte[] content) {
        CurrentUser user = currentUserPort.getCurrentUser();
        Contract contract = findContract(contractId);
        authorizationService.assertCanManageContracts(user, contract.getPropertyId());
        Handover handover = findHandover(contractId);
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
        audit("HANDOVER_PHOTO_ADDED", user, handover, Map.of("kind", kind.name()));
        return saved;
    }

    @Override
    public void removePhoto(UUID contractId, UUID photoId) {
        CurrentUser user = currentUserPort.getCurrentUser();
        Contract contract = findContract(contractId);
        authorizationService.assertCanManageContracts(user, contract.getPropertyId());
        Handover handover = findHandover(contractId);
        if (handover.status() != HandoverStatus.DRAFT) {
            throw new InvalidHandoverStateException("Chỉ xóa ảnh khi biên bản ở trạng thái DRAFT.");
        }
        HandoverPhoto photo = photoRepository.findById(photoId)
            .filter(found -> found.handoverId().equals(handover.id()))
            .orElseThrow(() -> new HandoverNotFoundException("Handover photo not found: " + photoId));
        photoRepository.delete(photo.id());
        deleteQuietly(photo.imageKey());
        audit("HANDOVER_PHOTO_REMOVED", user, handover, Map.of("kind", photo.kind().name()));
    }

    @Override
    public Handover send(UUID contractId) {
        CurrentUser user = currentUserPort.getCurrentUser();
        Contract contract = findContract(contractId);
        authorizationService.assertCanManageContracts(user, contract.getPropertyId());
        Handover handover = handoverRepository.findByContractIdForUpdate(contractId)
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

        List<HandoverItem> items = assetRepository.findActiveByRoomId(contract.getRoomId()).stream()
            .map(asset -> snapshot(handover.id(), asset)).toList();
        itemRepository.replaceAll(handover.id(), items, user.id());
        PropertyHouseRules rules = rulesRepository.findByPropertyId(contract.getPropertyId()).orElse(null);

        Handover sent = handover.send(
            termsHash(contract),
            rules == null ? null : rules.content(),
            rules == null ? null : sha256(rules.content()),
            user.id());
        Handover saved = handoverRepository.save(sent);
        audit("HANDOVER_SENT", user, saved, Map.of("items", items.size(), "photos", photos.size()));
        return saved;
    }

    @Override
    public Handover confirm(UUID contractId) {
        CurrentUser user = currentUserPort.getCurrentUser();
        Contract contract = findContract(contractId);
        Tenant tenant = requirePrimaryTenant(user, contract);
        Handover handover = handoverRepository.findByContractIdForUpdate(contractId)
            .orElseThrow(() -> new HandoverNotFoundException("Handover not found for contract " + contractId));
        if (handover.status() != HandoverStatus.PENDING_TENANT) {
            throw new InvalidHandoverStateException(String.format(
                "Không thể xác nhận biên bản ở trạng thái %s (cần PENDING_TENANT).", handover.status()));
        }
        if (documentRepository.findByTenantIdAndType(tenant.id(), DocumentType.CCCD).isEmpty()) {
            throw new IllegalArgumentException("Vui lòng tải ảnh CCCD trước khi xác nhận biên bản");
        }
        Handover saved = handoverRepository.save(handover.tenantConfirm(user.id()));
        audit("HANDOVER_TENANT_CONFIRMED", user, saved, Map.of());
        return saved;
    }

    @Override
    public Handover approve(UUID contractId) {
        CurrentUser user = currentUserPort.getCurrentUser();
        Contract contract = findContract(contractId);
        authorizationService.assertCanManageContracts(user, contract.getPropertyId());
        Handover handover = handoverRepository.findByContractIdForUpdate(contractId)
            .orElseThrow(() -> new HandoverNotFoundException("Handover not found for contract " + contractId));
        Handover saved = handoverRepository.save(handover.approve(user.id()));
        audit("HANDOVER_APPROVED", user, saved, Map.of());
        return saved;
    }

    @Override
    public Handover reject(UUID contractId, String reason) {
        CurrentUser user = currentUserPort.getCurrentUser();
        Contract contract = findContract(contractId);
        authorizationService.assertCanManageContracts(user, contract.getPropertyId());
        Handover handover = handoverRepository.findByContractIdForUpdate(contractId)
            .orElseThrow(() -> new HandoverNotFoundException("Handover not found for contract " + contractId));
        Handover saved = handoverRepository.save(handover.reject(reason, user.id()));
        audit("HANDOVER_REJECTED", user, saved, Map.of());
        return saved;
    }

    @Override
    public Handover reopen(UUID contractId) {
        CurrentUser user = currentUserPort.getCurrentUser();
        Contract contract = findContract(contractId);
        authorizationService.assertCanManageContracts(user, contract.getPropertyId());
        Handover handover = handoverRepository.findByContractIdForUpdate(contractId)
            .orElseThrow(() -> new HandoverNotFoundException("Handover not found for contract " + contractId));
        Handover saved = handoverRepository.save(handover.reopen(user.id()));
        audit("HANDOVER_REOPENED", user, saved, Map.of());
        return saved;
    }

    @Override
    public HandoverDetails get(UUID contractId) {
        CurrentUser user = currentUserPort.getCurrentUser();
        Contract contract = findContract(contractId);
        assertCanView(user, contract);
        Handover handover = findHandover(contractId);
        return new HandoverDetails(handover, photoRepository.findByHandoverId(handover.id()),
            itemRepository.findByHandoverId(handover.id()));
    }

    @Override
    public URI photoSignedUrl(UUID contractId, UUID photoId) {
        CurrentUser user = currentUserPort.getCurrentUser();
        Contract contract = findContract(contractId);
        assertCanView(user, contract);
        Handover handover = findHandover(contractId);
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

    private Handover findHandover(UUID contractId) {
        return handoverRepository.findByContractId(contractId)
            .orElseThrow(() -> new HandoverNotFoundException("Handover not found for contract " + contractId));
    }

    private void deleteQuietly(String key) {
        try {
            storagePort.delete(key);
        } catch (RuntimeException ignored) {
            // best effort
        }
    }

    private void audit(String action, CurrentUser user, Handover handover, Map<String, Object> extra) {
        Map<String, Object> details = new HashMap<>(extra);
        details.put("contractId", handover.contractId().toString());
        details.put("status", handover.status().name());
        auditPort.recordEvent(AuditPort.AuditEvent.of(action, user.id(), "HANDOVER", handover.id().toString(),
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
