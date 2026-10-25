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
import com.phonghub.domain.exception.FileStorageUnavailableException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HandoverService implements HandoverUseCase {

    private static final Logger log = LoggerFactory.getLogger(HandoverService.class);
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
            throw new IllegalArgumentException("Cần chọn loại ảnh.");
        }
        if (!FileSanitizer.isImage(contentType)) {
            throw new IllegalArgumentException("Ảnh biên bản phải là JPEG, PNG hoặc WebP.");
        }
        if (photoRepository.findByHandoverId(handover.id()).size() >= MAX_PHOTOS) {
            throw new IllegalArgumentException("Biên bản có tối đa " + MAX_PHOTOS + " ảnh.");
        }
        FileSanitizer.SanitizedFile file = FileSanitizer.sanitize(contentType, content, maxFileSizeBytes);
        String key = PrivateFileStorageService.objectKey(contract.getPropertyId(), "handover", file.extension());
        try {
            storagePort.put(key, file.contentType(), file.content());
        } catch (RuntimeException ex) {
            // Chưa ghi bản ghi ảnh nên không có bản ghi mồ côi; lỗi gốc (bucket, khóa, mạng) chỉ ghi log
            log.error("Không lưu được ảnh biên bản {} vào kho lưu trữ (key {})", handover.id(), key, ex);
            throw new FileStorageUnavailableException(
                "Không lưu được ảnh vào kho lưu trữ, vui lòng thử lại sau. Nếu lỗi lặp lại, hãy báo quản trị viên.", ex);
        }
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
            .orElseThrow(() -> new HandoverNotFoundException("Không tìm thấy ảnh biên bản: " + photoId));
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
            .orElseThrow(() -> new HandoverNotFoundException("Không tìm thấy hạng mục biên bản: " + itemId));
        if (command.status() == null) {
            throw new IllegalArgumentException("Cần chọn tình trạng tài sản.");
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
            .orElseThrow(() -> new HandoverNotFoundException("Chưa có biên bản cho hợp đồng " + contractId));
        if (handover.status() != HandoverStatus.DRAFT) {
            throw new InvalidHandoverStateException("Chỉ gửi biên bản ở trạng thái DRAFT.");
        }
        List<HandoverPhoto> photos = photoRepository.findByHandoverId(handover.id());
        for (HandoverPhotoKind required : HandoverPhotoKind.values()) {
            if (photos.stream().noneMatch(photo -> photo.kind() == required)) {
                throw new IllegalArgumentException("Cần ít nhất một ảnh loại " + required + " trước khi gửi");
            }
        }

        Handover saved = sendDraft(type, contract, handover, user);
        audit(type, "SENT", user, saved, Map.of("items", itemRepository.findByHandoverId(saved.id()).size(),
            "photos", photos.size()));
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
            .orElseThrow(() -> new HandoverNotFoundException("Chưa có biên bản cho hợp đồng " + contractId));
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
        assertCanApprove(type, user, contract);
        Handover handover = handoverRepository.findForUpdate(contractId, type)
            .orElseThrow(() -> new HandoverNotFoundException("Chưa có biên bản cho hợp đồng " + contractId));
        return approvePending(type, contractId, handover, user, finalDeduction);
    }

    @Override
    public Handover skip(HandoverType type, UUID contractId) {
        CurrentUser user = currentUserPort.getCurrentUser();
        Contract contract = findContract(contractId);
        assertCanApprove(type, user, contract);
        if (handoverRepository.find(contractId, type).isEmpty()) {
            create(type, sampleCommand(type, contractId));
        }
        Handover handover = handoverRepository.findForUpdate(contractId, type)
            .orElseThrow(() -> new HandoverNotFoundException("Chưa có biên bản cho hợp đồng " + contractId));
        HandoverStatus from = handover.status();
        if (handover.status() == HandoverStatus.APPROVED) {
            throw new InvalidHandoverStateException("Biên bản " + label(type) + " đã được duyệt.");
        }
        if (handover.status() == HandoverStatus.REJECTED) {
            handover = handoverRepository.save(handover.reopen(user.id()));
        }
        if (handover.status() == HandoverStatus.DRAFT) {
            handover = sendDraft(type, contract, handover, user);
        }
        if (handover.status() == HandoverStatus.PENDING_TENANT) {
            handover = handoverRepository.save(handover.skipTenantConfirmation(user.id()));
        }
        Handover saved = approvePending(type, contractId, handover, user, null);
        audit(type, "SKIPPED", user, saved, Map.of("fromStatus", from.name()));
        return saved;
    }

    /** Biên bản mẫu cho nút bỏ qua: check-out lấy lại chỉ số check-in để không phát sinh tiêu thụ giả. */
    private HandoverCommand sampleCommand(HandoverType type, UUID contractId) {
        Handover checkIn = type == HandoverType.CHECK_OUT
            ? handoverRepository.find(contractId, HandoverType.CHECK_IN).orElse(null) : null;
        BigDecimal electric = checkIn != null ? checkIn.initialElectricMeter() : BigDecimal.ZERO;
        BigDecimal water = checkIn != null ? checkIn.initialWaterMeter() : BigDecimal.ZERO;
        return new HandoverCommand(contractId, electric, water, "Bỏ qua bàn giao (dữ liệu mẫu).", null);
    }

    private void assertCanApprove(HandoverType type, CurrentUser user, Contract contract) {
        if (type == HandoverType.CHECK_OUT) {
            authorizationService.assertOwnerOrAdmin(user, contract.getPropertyId());
        } else {
            authorizationService.assertCanManageContracts(user, contract.getPropertyId());
        }
    }

    /** DRAFT -> PENDING_TENANT: check-in chụp tài sản hiện có của phòng, kèm hash điều khoản và nội quy. */
    private Handover sendDraft(HandoverType type, Contract contract, Handover handover, CurrentUser user) {
        if (type == HandoverType.CHECK_IN) {
            List<HandoverItem> items = assetRepository.findActiveByRoomId(contract.getRoomId()).stream()
                .map(asset -> snapshot(handover.id(), asset)).toList();
            itemRepository.replaceAll(handover.id(), items, user.id());
        }
        PropertyHouseRules rules = rulesRepository.findByPropertyId(contract.getPropertyId()).orElse(null);
        return handoverRepository.save(handover.send(
            termsHash(contract),
            rules == null ? null : rules.content(),
            rules == null ? null : sha256(rules.content()),
            user.id()));
    }

    /** PENDING_APPROVAL -> APPROVED; check-out kết thúc hợp đồng và trả phòng trong cùng transaction. */
    private Handover approvePending(HandoverType type, UUID contractId, Handover handover, CurrentUser user,
                                    BigDecimal finalDeduction) {
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
            .orElseThrow(() -> new HandoverNotFoundException("Chưa có biên bản cho hợp đồng " + contractId));
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
            .orElseThrow(() -> new HandoverNotFoundException("Chưa có biên bản cho hợp đồng " + contractId));
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
        HandoverPhoto photo = findViewablePhoto(type, contractId, photoId);
        try {
            return storagePort.createSignedUrl(photo.imageKey(), SIGNED_URL_TTL);
        } catch (RuntimeException ex) {
            throw photoUnavailable(photo, ex);
        }
    }

    @Override
    public PhotoFile photoFile(HandoverType type, UUID contractId, UUID photoId) {
        HandoverPhoto photo = findViewablePhoto(type, contractId, photoId);
        try {
            return new PhotoFile(photo.contentType(), storagePort.get(photo.imageKey()));
        } catch (RuntimeException ex) {
            throw photoUnavailable(photo, ex);
        }
    }

    private HandoverPhoto findViewablePhoto(HandoverType type, UUID contractId, UUID photoId) {
        CurrentUser user = currentUserPort.getCurrentUser();
        Contract contract = findContract(contractId);
        assertCanView(user, contract);
        Handover handover = findHandover(contractId, type);
        return photoRepository.findById(photoId)
            .filter(found -> found.handoverId().equals(handover.id()))
            .orElseThrow(() -> new HandoverNotFoundException("Không tìm thấy ảnh biên bản: " + photoId));
    }

    private static FileStorageUnavailableException photoUnavailable(HandoverPhoto photo, RuntimeException cause) {
        log.error("Không lấy được ảnh biên bản {} từ kho lưu trữ (key {})", photo.id(), photo.imageKey(), cause);
        return new FileStorageUnavailableException("Không mở được ảnh từ kho lưu trữ, vui lòng thử lại sau.", cause);
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
                throw new UnauthorizedPropertyAccessException("Người thuê không thuộc hợp đồng này.");
            }
            return;
        }
        authorizationService.assertCanManageContracts(user, contract.getPropertyId());
    }

    private Tenant requirePrimaryTenant(CurrentUser user, Contract contract) {
        if (user == null || !user.isTenant()) {
            throw new UnauthorizedPropertyAccessException("Chỉ người thuê chính được xác nhận biên bản.");
        }
        Tenant tenant = tenantRepository.findByUserId(user.id()).orElse(null);
        if (tenant == null || !tenant.id().equals(contract.getPrimaryTenantId())) {
            throw new UnauthorizedPropertyAccessException("Chỉ người thuê chính được xác nhận biên bản.");
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
            .orElseThrow(() -> new ContractNotFoundException("Không tìm thấy hợp đồng: " + contractId));
    }

    private Handover findHandover(UUID contractId, HandoverType type) {
        return handoverRepository.find(contractId, type)
            .orElseThrow(() -> new HandoverNotFoundException("Chưa có biên bản cho hợp đồng " + contractId));
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
