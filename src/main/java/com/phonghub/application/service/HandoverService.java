package com.phonghub.application.service;

import com.phonghub.application.port.in.ContractUseCase;
import com.phonghub.application.port.in.HandoverUseCase;
import com.phonghub.application.port.in.RoomAssetUseCase;
import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.FileStoragePort;
import com.phonghub.application.port.out.HandoverItemRepositoryPort;
import com.phonghub.application.port.out.HandoverPhotoRepositoryPort;
import com.phonghub.application.port.out.HandoverRepositoryPort;
import com.phonghub.application.port.out.InvoicePaymentRecordRepositoryPort;
import com.phonghub.application.port.out.InvoiceRepositoryPort;
import com.phonghub.application.port.out.MeterReadingRepositoryPort;
import com.phonghub.application.port.out.PropertyHouseRulesRepositoryPort;
import com.phonghub.application.port.out.RoomConditionRepositoryPort;
import com.phonghub.application.port.out.ServiceRateRepositoryPort;
import com.phonghub.application.port.out.RoomAssetRepositoryPort;
import com.phonghub.application.port.out.TenantDocumentRepositoryPort;
import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.domain.exception.ContractNotFoundException;
import com.phonghub.domain.exception.FileStorageUnavailableException;
import com.phonghub.domain.exception.HandoverAlreadyExistsException;
import com.phonghub.domain.exception.HandoverNotFoundException;
import com.phonghub.domain.exception.ConflictException;
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
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.InvoiceItem;
import com.phonghub.domain.model.InvoicePaymentRecord;
import com.phonghub.domain.model.InvoiceType;
import com.phonghub.domain.model.MeterReading;
import com.phonghub.domain.model.MeterType;
import com.phonghub.domain.model.ChargeType;
import com.phonghub.domain.model.ServiceRate;
import com.phonghub.domain.model.PropertyHouseRules;
import com.phonghub.domain.model.RoomAsset;
import com.phonghub.domain.model.RoomConditionItem;
import com.phonghub.domain.model.RoomConditionSnapshot;
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
    private static final String MISSING_BASELINE_NOTE =
        "Chưa có dữ liệu hiện trạng. Vui lòng kiểm tra và cập nhật chỉ số điện, nước trước khi gửi.";

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
    private final RoomConditionRepositoryPort roomConditionRepository;
    private final RoomAssetUseCase roomAssetUseCase;
    private final InvoiceRepositoryPort invoiceRepository;
    private final InvoicePaymentRecordRepositoryPort invoicePaymentRecordRepository;
    private final MeterReadingRepositoryPort meterReadingRepository;
    private final ServiceRateRepositoryPort serviceRateRepository;
    private final EarlyTerminationBillingService earlyTerminationBilling;

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
            auditPort, maxFileSizeBytes, null, null, null);
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
        this(handoverRepository, photoRepository, itemRepository, rulesRepository, contractRepository, assetRepository,
            tenantRepository, documentRepository, contractUseCase, storagePort, currentUserPort, authorizationService,
            auditPort, maxFileSizeBytes, notifications, null, null);
    }

    /** Room condition is optional for legacy/manual callers; configured application wiring supplies it. */
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
        NotificationDispatcher notifications,
        RoomConditionRepositoryPort roomConditionRepository
    ) {
        this(handoverRepository, photoRepository, itemRepository, rulesRepository, contractRepository, assetRepository,
            tenantRepository, documentRepository, contractUseCase, storagePort, currentUserPort, authorizationService,
            auditPort, maxFileSizeBytes, notifications, roomConditionRepository, null);
    }

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
        NotificationDispatcher notifications,
        RoomConditionRepositoryPort roomConditionRepository,
        RoomAssetUseCase roomAssetUseCase
    ) {
        this(handoverRepository, photoRepository, itemRepository, rulesRepository, contractRepository, assetRepository,
            tenantRepository, documentRepository, contractUseCase, storagePort, currentUserPort, authorizationService,
            auditPort, maxFileSizeBytes, notifications, roomConditionRepository, roomAssetUseCase, null, null, null, null);
    }

    public HandoverService(
        HandoverRepositoryPort handoverRepository, HandoverPhotoRepositoryPort photoRepository,
        HandoverItemRepositoryPort itemRepository, PropertyHouseRulesRepositoryPort rulesRepository,
        ContractRepositoryPort contractRepository, RoomAssetRepositoryPort assetRepository,
        TenantRepositoryPort tenantRepository, TenantDocumentRepositoryPort documentRepository,
        ContractUseCase contractUseCase, FileStoragePort storagePort, CurrentUserPort currentUserPort,
        AuthorizationService authorizationService, AuditPort auditPort, long maxFileSizeBytes,
        NotificationDispatcher notifications, RoomConditionRepositoryPort roomConditionRepository,
        RoomAssetUseCase roomAssetUseCase, InvoiceRepositoryPort invoiceRepository,
        InvoicePaymentRecordRepositoryPort invoicePaymentRecordRepository,
        MeterReadingRepositoryPort meterReadingRepository, ServiceRateRepositoryPort serviceRateRepository
    ) {
        this(handoverRepository, photoRepository, itemRepository, rulesRepository, contractRepository, assetRepository,
            tenantRepository, documentRepository, contractUseCase, storagePort, currentUserPort, authorizationService,
            auditPort, maxFileSizeBytes, notifications, roomConditionRepository, roomAssetUseCase, invoiceRepository,
            invoicePaymentRecordRepository, meterReadingRepository, serviceRateRepository, null);
    }

    public HandoverService(
        HandoverRepositoryPort handoverRepository, HandoverPhotoRepositoryPort photoRepository,
        HandoverItemRepositoryPort itemRepository, PropertyHouseRulesRepositoryPort rulesRepository,
        ContractRepositoryPort contractRepository, RoomAssetRepositoryPort assetRepository,
        TenantRepositoryPort tenantRepository, TenantDocumentRepositoryPort documentRepository,
        ContractUseCase contractUseCase, FileStoragePort storagePort, CurrentUserPort currentUserPort,
        AuthorizationService authorizationService, AuditPort auditPort, long maxFileSizeBytes,
        NotificationDispatcher notifications, RoomConditionRepositoryPort roomConditionRepository,
        RoomAssetUseCase roomAssetUseCase, InvoiceRepositoryPort invoiceRepository,
        InvoicePaymentRecordRepositoryPort invoicePaymentRecordRepository,
        MeterReadingRepositoryPort meterReadingRepository, ServiceRateRepositoryPort serviceRateRepository,
        EarlyTerminationBillingService earlyTerminationBilling
    ) {
        this.earlyTerminationBilling = earlyTerminationBilling;
        this.notifications = notifications;
        this.roomConditionRepository = roomConditionRepository;
        this.roomAssetUseCase = roomAssetUseCase;
        this.invoiceRepository = invoiceRepository;
        this.invoicePaymentRecordRepository = invoicePaymentRecordRepository;
        this.meterReadingRepository = meterReadingRepository;
        this.serviceRateRepository = serviceRateRepository;
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
    public Handover prepareCheckIn(UUID contractId) {
        CurrentUser user = currentUserPort.getCurrentUser();
        Contract contract = findContract(contractId);
        authorizationService.assertCanManageContracts(user, contract.getPropertyId());
        if (contract.getStatus() != com.phonghub.domain.model.ContractStatus.DRAFT) {
            throw new InvalidHandoverStateException("Chỉ chuẩn bị check-in cho hợp đồng nháp.");
        }
        Handover existing = handoverRepository.find(contractId, HandoverType.CHECK_IN).orElse(null);
        if (existing != null) return existing;

        RoomConditionSnapshot baseline = roomConditionRepository == null ? null
            : roomConditionRepository.findLatestByRoomId(contract.getRoomId()).orElse(null);
        Handover previousCheckOut = contractRepository.findByRoomId(contract.getRoomId()).stream()
            .filter(previous -> !previous.getId().equals(contractId))
            .map(previous -> handoverRepository.find(previous.getId(), HandoverType.CHECK_OUT).orElse(null))
            .filter(previous -> previous != null && previous.status() == HandoverStatus.APPROVED
                && previous.approvedAt() != null)
            .max(java.util.Comparator.comparing(Handover::approvedAt)).orElse(null);
        boolean checkoutIsNewer = previousCheckOut != null
            && (baseline == null || previousCheckOut.approvedAt().isAfter(baseline.createdAt()));
        BigDecimal electric = checkoutIsNewer ? previousCheckOut.initialElectricMeter()
            : baseline == null ? BigDecimal.ZERO : baseline.electricMeter();
        BigDecimal water = checkoutIsNewer ? previousCheckOut.initialWaterMeter()
            : baseline == null ? BigDecimal.ZERO : baseline.waterMeter();
        String condition = checkoutIsNewer ? previousCheckOut.roomCondition()
            : baseline == null ? MISSING_BASELINE_NOTE : baseline.conditionNote();
        Handover saved = handoverRepository.save(Handover.draft(contractId, HandoverType.CHECK_IN,
            electric, water, condition, null, user.id()));
        List<HandoverItem> baselineItems = checkoutIsNewer
            ? itemRepository.findByHandoverId(previousCheckOut.id()).stream()
                .map(item -> new HandoverItem(UUID.randomUUID(), saved.id(), item.assetId(), item.assetName(),
                    item.quantity(), item.status(), item.conditionNote(), BigDecimal.ZERO)).toList()
            : baseline == null ? null : roomConditionRepository.findItemsBySnapshotId(baseline.id()).stream()
                .map(item -> snapshot(saved.id(), item)).toList();
        if (baselineItems == null) {
            baselineItems = assetRepository.findActiveByRoomId(contract.getRoomId()).stream()
                .map(asset -> snapshot(saved.id(), asset)).toList();
        }
        itemRepository.replaceAll(saved.id(), baselineItems, user.id());
        audit(HandoverType.CHECK_IN, "CREATED", user, saved,
            Map.of("items", baselineItems.size(), "source", checkoutIsNewer ? "previous_check_out"
                : baseline == null ? "room_assets" : "room_condition"));
        return saved;
    }

    @Override
    public Handover prepareEarlyTerminationCheckout(UUID contractId, String reason) {
        CurrentUser user = currentUserPort.getCurrentUser();
        Contract contract = findContract(contractId);
        authorizationService.assertCanManageContracts(user, contract.getPropertyId());
        if (!contract.isResiding()) {
            throw new InvalidHandoverStateException("Chỉ có thể mở check-out sớm cho hợp đồng đang ở.");
        }
        if (reason == null || reason.isBlank() || reason.trim().length() > 500) {
            throw new IllegalArgumentException("Lý do kết thúc sớm phải có và tối đa 500 ký tự.");
        }
        Handover existing = handoverRepository.find(contractId, HandoverType.CHECK_OUT).orElse(null);
        if (existing != null) {
            if (existing.status() != HandoverStatus.DRAFT) {
                throw new ConflictException("Biên bản check-out đã được gửi; không thể đổi lý do kết thúc sớm.");
            }
            Handover updated = handoverRepository.save(existing.asEarlyTermination(reason, user.id()));
            audit(HandoverType.CHECK_OUT, "EARLY_TERMINATION_REQUESTED", user, updated,
                Map.of("reason", reason.trim()));
            return updated;
        }

        Handover checkIn = handoverRepository.find(contractId, HandoverType.CHECK_IN)
            .filter(h -> h.status() == HandoverStatus.APPROVED)
            .orElseThrow(() -> new InvalidHandoverStateException("Cần có biên bản check-in đã được duyệt trước khi check-out."));
        BigDecimal electric = latestMeter(contract, MeterType.ELECTRIC, checkIn.initialElectricMeter());
        BigDecimal water = latestMeter(contract, MeterType.WATER, checkIn.initialWaterMeter());
        HandoverCommand command = new HandoverCommand(contractId, electric, water,
            "Chờ kiểm tra hiện trạng phòng khi trả.", null, true, reason.trim());
        Handover created = create(HandoverType.CHECK_OUT, command);
        audit(HandoverType.CHECK_OUT, "EARLY_TERMINATION_REQUESTED", user, created,
            Map.of("reason", reason.trim()));
        return created;
    }

    private BigDecimal latestMeter(Contract contract, MeterType type, BigDecimal fallback) {
        if (meterReadingRepository == null) return fallback;
        return meterReadingRepository.findLatest(contract.getRoomId(), type)
            .filter(reading -> reading.contractId() == null || contract.getId().equals(reading.contractId()))
            .map(MeterReading::currentValue).orElse(fallback);
    }

    @Override
    public Handover create(HandoverType type, HandoverCommand command) {
        CurrentUser user = currentUserPort.getCurrentUser();
        Contract contract = findContract(command.contractId());
        authorizationService.assertCanManageContracts(user, contract.getPropertyId());
        // Check-in cần hợp đồng đang hiệu lực; check-out được cả khi hợp đồng đã hết hạn mà chưa trả phòng (#87).
        boolean allowed = type == HandoverType.CHECK_OUT ? contract.isResiding()
            : contract.isActive() || contract.getStatus() == com.phonghub.domain.model.ContractStatus.DRAFT;
        if (!allowed) {
            throw new InvalidHandoverStateException(String.format(
                "Chỉ lập biên bản cho hợp đồng đang hiệu lực%s. Trạng thái hiện tại: %s.",
                type == HandoverType.CHECK_OUT ? " hoặc đã hết hạn nhưng chưa trả phòng" : " (DRAFT hoặc ACTIVE)",
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
        Handover draft = Handover.draft(contract.getId(), type, command.initialElectricMeter(),
            command.initialWaterMeter(), command.roomCondition(), command.keysProvided(), user.id());
        if (command.earlyTermination()) {
            draft = draft.asEarlyTermination(command.terminationReason(), user.id());
        }
        Handover saved = handoverRepository.save(draft);
        if (type == HandoverType.CHECK_IN) {
            itemRepository.replaceAll(saved.id(), checkInSnapshotItems(saved.id(), contract.getRoomId()), user.id());
        } else {
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
    public HandoverItem addCheckInAsset(UUID contractId, NewAssetCommand command) {
        CurrentUser user = currentUserPort.getCurrentUser();
        Contract contract = findContract(contractId);
        authorizationService.assertCanManageContracts(user, contract.getPropertyId());
        Handover handover = findHandover(contractId, HandoverType.CHECK_IN);
        if (contract.getStatus() != com.phonghub.domain.model.ContractStatus.DRAFT
            || handover.status() != HandoverStatus.DRAFT) {
            throw new InvalidHandoverStateException("Chỉ thêm tài sản khi check-in của hợp đồng nháp đang ở trạng thái DRAFT.");
        }
        if (command == null || command.name() == null || command.name().isBlank() || command.quantity() <= 0
            || command.status() == null) {
            throw new IllegalArgumentException("Tên, số lượng và tình trạng tài sản là bắt buộc.");
        }
        if (roomAssetUseCase == null) {
            throw new IllegalStateException("Room asset use case is not configured.");
        }
        RoomAsset asset = roomAssetUseCase.create(new RoomAssetUseCase.CreateAssetCommand(contract.getRoomId(), null,
            command.name(), null, command.quantity(), command.note(), null, command.estimatedValue(), command.status()));
        HandoverItem item = snapshot(handover.id(), asset);
        itemRepository.save(item, user.id());
        audit(HandoverType.CHECK_IN, "ASSET_ADDED", user, handover,
            Map.of("assetId", asset.id().toString(), "name", asset.name()));
        return item;
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
                throw new IllegalArgumentException("Cần ít nhất một ảnh " + switch (required) {
                    case ROOM -> "phòng";
                    case METER_ELECTRIC -> "đồng hồ điện";
                    case METER_WATER -> "đồng hồ nước";
                } + " trước khi gửi");
            }
        }
        if (type == HandoverType.CHECK_IN && MISSING_BASELINE_NOTE.equals(handover.roomCondition())) {
            throw new IllegalArgumentException("Phòng chưa có hiện trạng trước đó. Hãy nhập chỉ số điện, nước và tình trạng thực tế trước khi gửi.");
        }

        int itemCount;
        if (type == HandoverType.CHECK_IN) {
            List<HandoverItem> items = itemRepository.findByHandoverId(handover.id());
            if (items.isEmpty()) {
                items = checkInSnapshotItems(handover.id(), contract.getRoomId());
                itemRepository.replaceAll(handover.id(), items, user.id());
            }
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

    private List<HandoverItem> checkInSnapshotItems(UUID handoverId, UUID roomId) {
        if (roomConditionRepository != null) {
            RoomConditionSnapshot baseline = roomConditionRepository.findLatestByRoomId(roomId).orElse(null);
            if (baseline != null) {
                return roomConditionRepository.findItemsBySnapshotId(baseline.id()).stream()
                    .map(item -> snapshot(handoverId, item)).toList();
            }
        }
        // Phòng cũ chưa có biên bản hiện trạng thì giữ cách chụp từ tài sản đang gắn với phòng.
        return assetRepository.findActiveByRoomId(roomId).stream().map(asset -> snapshot(handoverId, asset)).toList();
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
            throw new IllegalArgumentException("Vui lòng tải ảnh CCCD ở Hồ sơ cá nhân trước khi xác nhận biên bản");
        }
        Handover saved = handoverRepository.save(handover.tenantConfirm(user.id()));
        if (type == HandoverType.CHECK_OUT) {
            Handover checkIn = handoverRepository.find(contractId, HandoverType.CHECK_IN)
                .filter(found -> found.status() == HandoverStatus.APPROVED)
                .orElseThrow(() -> new InvalidHandoverStateException(
                    "Cần có biên bản check-in đã được duyệt trước khi xác nhận check-out."));
            issueCheckOutInvoices(contract, checkIn, saved, user);
        }
        audit(type, "TENANT_CONFIRMED", user, saved, Map.of());
        if (notifications != null) {
            // Cả chủ trọ và staff quản lý hợp đồng đều có thể duyệt check-out.
            notifications.notifyManagers(contract.getPropertyId(), true,
                "Biên bản " + label(type) + " chờ duyệt",
                "Người thuê đã xác nhận biên bản " + label(type) + ". Vui lòng kiểm tra và duyệt.",
                link(type, contractId));
        }
        return saved;
    }

    /**
     * Người thuê đã xác nhận check-out: chốt các khoản phải trả trước khi duyệt. Trả phòng trước ngày kết thúc hợp đồng
     * thì lập phí hủy giữa kỳ (50% tiền thuê chu kỳ đang ở, #225) đã gồm điện/nước theo chỉ số check-out; từ ngày kết
     * thúc trở đi chỉ lập hóa đơn chốt điện/nước.
     */
    private void issueCheckOutInvoices(Contract contract, Handover checkIn, Handover checkOut, CurrentUser user) {
        if (invoiceRepository == null || meterReadingRepository == null || serviceRateRepository == null) return;
        contract = contractRepository.findByIdForUpdate(contract.getId())
            .orElseThrow(() -> new ContractNotFoundException("Không tìm thấy hợp đồng: " + checkOut.contractId()));
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        List<InvoiceItem> charges = finalUtilityCharges(contract, checkIn, checkOut);
        Invoice existing = invoiceRepository.findByContractId(contract.getId()).stream()
            .filter(invoice -> (invoice.getType() == com.phonghub.domain.model.InvoiceType.CANCELLATION
                || invoice.getType() == com.phonghub.domain.model.InvoiceType.CHECK_OUT)
                && invoice.getStatus() != com.phonghub.domain.model.InvoiceStatus.VOIDED)
            .findFirst().orElse(null);
        if (existing != null) {
            Invoice invoice = invoiceRepository.findByIdForUpdate(existing.getId()).orElseThrow();
            BigDecimal previousTotal = invoice.getTotalAmount();
            BigDecimal previousCredit = invoice.getSettlementCreditAmount();
            invoice.reviseCheckOutUtilities(charges);
            contract.reviseSettlementCredit(previousCredit, invoice.getSettlementCreditAmount());
            contractRepository.save(contract);
            invoiceRepository.save(invoice);
            auditPort.recordEvent(AuditPort.AuditEvent.of("CHECKOUT_INVOICE_REVISED", user.id(), "INVOICE",
                invoice.getId().toString(), contract.getPropertyId(), Map.of(
                    "handoverId", checkOut.id().toString(), "previousTotal", previousTotal.toPlainString(),
                    "totalAmount", invoice.getTotalAmount().toPlainString(),
                    "refundCredit", invoice.getSettlementCreditAmount().toPlainString())));
            return;
        }
        boolean beforeEndDate = contract.getStatus() == com.phonghub.domain.model.ContractStatus.ACTIVE
            && today.isBefore(contract.getEndDate());
        if (earlyTerminationBilling != null && beforeEndDate) {
            earlyTerminationBilling.billEarlyTermination(contract, user, today, charges);
            contractRepository.save(contract);
            return;
        }
        issueFinalUtilityInvoice(contract, checkOut, charges, today, user.id());
    }

    private List<InvoiceItem> finalUtilityCharges(Contract contract, Handover checkIn, Handover checkOut) {
        List<InvoiceItem> charges = new java.util.ArrayList<>();
        List<ServiceRate> rates = serviceRateRepository.findByPropertyId(contract.getPropertyId());
        finalMeterCharges(contract, checkIn, checkOut, MeterType.ELECTRIC, ChargeType.PER_KWH, rates,
            "kWh", charges);
        finalMeterCharges(contract, checkIn, checkOut, MeterType.WATER, ChargeType.PER_M3, rates,
            "m³", charges);
        return charges;
    }

    private void issueFinalUtilityInvoice(Contract contract, Handover checkOut, List<InvoiceItem> charges,
                                          java.time.LocalDate today, UUID actorId) {
        if (charges.isEmpty()) return;
        boolean alreadyIssued = invoiceRepository.findByContractId(contract.getId()).stream()
            .anyMatch(existing -> existing.getType() == com.phonghub.domain.model.InvoiceType.CHECK_OUT
                && existing.getStatus() != com.phonghub.domain.model.InvoiceStatus.VOIDED);
        if (alreadyIssued) return;
        Invoice invoice = Invoice.issueCheckOutUtilities(contract, java.time.YearMonth.from(today),
            invoiceRepository.newUniquePaymentCode(), today.plusDays(Invoice.MIN_DAYS_TO_PAY), charges);
        invoiceRepository.save(invoice);
        auditPort.recordEvent(AuditPort.AuditEvent.of("CHECKOUT_UTILITY_INVOICE_ISSUED", actorId, "INVOICE",
            invoice.getId().toString(), contract.getPropertyId(), Map.of(
                "contractId", contract.getId().toString(), "handoverId", checkOut.id().toString(),
                "totalAmount", invoice.getTotalAmount().toPlainString())));
    }

    private void finalMeterCharges(
        Contract contract, Handover checkIn, Handover checkOut, MeterType meterType, ChargeType chargeType,
        List<ServiceRate> rates, String unit, List<InvoiceItem> out
    ) {
        BigDecimal finalValue = meterType == MeterType.ELECTRIC
            ? checkOut.initialElectricMeter() : checkOut.initialWaterMeter();
        BigDecimal fallback = meterType == MeterType.ELECTRIC
            ? checkIn.initialElectricMeter() : checkIn.initialWaterMeter();
        BigDecimal fromValue = latestMeter(contract, meterType, fallback);
        if (finalValue.compareTo(fromValue) < 0 || finalValue.compareTo(fromValue) == 0) return;
        BigDecimal quantity = finalValue.subtract(fromValue);
        for (ServiceRate rate : rates) {
            if (rate.chargeType() == chargeType && rate.pricePerUnit().signum() > 0) {
                out.add(InvoiceItem.checkOutCharge(rate.serviceName(), quantity, rate.pricePerUnit(), unit, rate.id()));
            }
        }
    }

    private static String label(HandoverType type) {
        return type == HandoverType.CHECK_IN ? "check-in" : "check-out";
    }

    /** Trang biên bản: /contracts/{id}/handover (check-in) hoặc /contracts/{id}/check-out. */
    private static String link(HandoverType type, UUID contractId) {
        return "/contracts/" + contractId + (type == HandoverType.CHECK_IN ? "/handover" : "/check-out");
    }

    @Override
    public Handover approve(
        HandoverType type, UUID contractId, BigDecimal finalDeduction, List<UUID> depositOffsetInvoiceIds
    ) {
        CurrentUser user = currentUserPort.getCurrentUser();
        Contract contract = findContract(contractId);
        authorizationService.assertCanManageContracts(user, contract.getPropertyId());
        Handover handover = handoverRepository.findForUpdate(contractId, type)
            .orElseThrow(() -> new HandoverNotFoundException("Chưa có biên bản cho hợp đồng " + contractId));

        if (type == HandoverType.CHECK_IN) {
            Handover saved = handoverRepository.save(handover.approve(user.id()));
            if (contract.getStatus() == com.phonghub.domain.model.ContractStatus.DRAFT) {
                // TransactionalHandoverUseCase giữ việc duyệt và kích hoạt trong cùng transaction.
                contractUseCase.activateContract(contractId);
            }
            audit(type, "APPROVED", user, saved, Map.of());
            return saved;
        }

        if (!handover.confirmedByTenant()) {
            throw new ConflictException("Người thuê cần xác nhận biên bản check-out trước khi duyệt.");
        }
        List<HandoverItem> items = itemRepository.findByHandoverId(handover.id());
        BigDecimal proposed = items.stream().map(HandoverItem::deductionAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal deduction = finalDeduction == null ? proposed : finalDeduction;
        BigDecimal offsetAmount = applyDepositOffsets(contract, handover, deduction, depositOffsetInvoiceIds, user);
        Handover saved = handoverRepository.save(handover.approve(user.id(), deduction));
        boolean needsRepair = items.stream().anyMatch(HandoverItem::needsRepair);
        // Cùng một transaction: nếu kết thúc hợp đồng thất bại thì biên bản vẫn ở trạng thái chờ duyệt.
        contractUseCase.terminateContract(contractId, needsRepair);
        Map<String, Object> auditDetails = new HashMap<>();
        auditDetails.put("finalDeduction", deduction.toPlainString());
        auditDetails.put("proposedDeduction", proposed.toPlainString());
        auditDetails.put("depositOffset", offsetAmount.toPlainString());
        auditDetails.put("roomNeedsRepair", needsRepair);
        if (saved.earlyTermination()) {
            auditDetails.put("early", true);
            auditDetails.put("reason", saved.terminationReason());
        }
        audit(type, "APPROVED", user, saved, auditDetails);
        return saved;
    }

    private BigDecimal applyDepositOffsets(
        Contract contract, Handover handover, BigDecimal deduction, List<UUID> requestedIds, CurrentUser user
    ) {
        if (invoiceRepository == null || invoicePaymentRecordRepository == null) {
            throw new IllegalStateException("Checkout invoice settlement repositories are not configured.");
        }
        List<Invoice> outstanding = invoiceRepository.findPayableByContractIdForUpdate(contract.getId()).stream()
            .filter(invoice -> invoice.remainingAmount().signum() > 0).toList();
        java.util.Set<UUID> selectedIds = requestedIds == null ? java.util.Set.of()
            : requestedIds.stream().filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        java.util.Map<UUID, Invoice> byId = outstanding.stream()
            .collect(java.util.stream.Collectors.toMap(Invoice::getId, java.util.function.Function.identity()));
        if (!byId.keySet().containsAll(selectedIds)) {
            throw new ConflictException("Một hoặc nhiều hóa đơn đã được thanh toán, bị hủy hoặc không thuộc hợp đồng này. Vui lòng tải lại trang.");
        }

        List<Invoice> unselected = outstanding.stream().filter(invoice -> !selectedIds.contains(invoice.getId())).toList();
        if (!unselected.isEmpty()) {
            throw new ConflictException("Còn " + unselected.size()
                + " hóa đơn chưa thanh toán. Hãy thanh toán hoặc chọn cấn trừ đủ từng khoản vào tiền cọc trước khi duyệt: "
                + debtSummary(unselected));
        }

        BigDecimal remainingAfterDeduction = contract.availableDeposit().subtract(deduction);
        if (remainingAfterDeduction.signum() < 0) {
            throw new ConflictException("Khoản khấu trừ tài sản không được vượt quá tiền cọc còn lại.");
        }
        BigDecimal usableDeposit = remainingAfterDeduction;
        BigDecimal offsetTotal = outstanding.stream().map(Invoice::remainingAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (offsetTotal.compareTo(usableDeposit) > 0) {
            BigDecimal shortfall = offsetTotal.subtract(usableDeposit);
            throw new ConflictException("Tiền cọc còn lại sau khấu trừ tài sản không đủ trả công nợ; còn thiếu "
                + shortfall.toPlainString() + " đ. Hãy thanh toán phần thiếu trước khi duyệt.");
        }

        java.time.Instant paidAt = java.time.Instant.now();
        for (Invoice invoice : outstanding) {
            BigDecimal amount = invoice.remainingAmount();
            contract.applyDeposit(amount);
            invoice.applyPaymentFromDeposit(amount, paidAt);
            invoiceRepository.save(invoice);
            invoicePaymentRecordRepository.save(new InvoicePaymentRecord(UUID.randomUUID(), contract.getId(),
                invoice.getId(), amount, InvoicePaymentRecord.Type.DEPOSIT_OFFSET, paidAt, user.id(),
                "Cấn trừ tiền cọc khi check-out " + handover.id()));
        }
        if (offsetTotal.signum() > 0) {
            contractRepository.save(contract);
        }
        return offsetTotal;
    }

    private static String debtSummary(List<Invoice> invoices) {
        return invoices.stream().map(invoice -> String.format("%02d/%d còn %s đ (hạn %s)", invoice.getMonth(),
            invoice.getYear(), invoice.remainingAmount().toPlainString(), invoice.getDueDate()))
            .collect(java.util.stream.Collectors.joining("; "));
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
    public PropertyHouseRules getHouseRulesForContract(UUID contractId) {
        CurrentUser user = currentUserPort.getCurrentUser();
        Contract contract = findContract(contractId);
        if (user != null && user.isTenant()) {
            assertCanView(user, contract);
        } else {
            authorizationService.assertCanAccessProperty(user, contract.getPropertyId());
        }
        return rulesRepository.findByPropertyId(contract.getPropertyId()).orElse(null);
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

    private HandoverItem snapshot(UUID handoverId, RoomConditionItem item) {
        return new HandoverItem(UUID.randomUUID(), handoverId, item.assetId(), item.name(), item.quantity(),
            item.status(), item.note());
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
