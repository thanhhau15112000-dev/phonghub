package com.phonghub.application.service;

import com.phonghub.application.port.in.HandoverReportUseCase;
import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.FileStoragePort;
import com.phonghub.application.port.out.HandoverItemRepositoryPort;
import com.phonghub.application.port.out.HandoverPdfPort;
import com.phonghub.application.port.out.HandoverPhotoRepositoryPort;
import com.phonghub.application.port.out.HandoverRepositoryPort;
import com.phonghub.application.port.out.PropertyRepositoryPort;
import com.phonghub.application.port.out.RoomRepositoryPort;
import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.application.port.out.UserRepositoryPort;
import com.phonghub.domain.exception.ContractNotFoundException;
import com.phonghub.domain.exception.HandoverNotFoundException;
import com.phonghub.domain.exception.InvalidHandoverStateException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractOccupant;
import com.phonghub.domain.model.Handover;
import com.phonghub.domain.model.HandoverItem;
import com.phonghub.domain.model.HandoverPhoto;
import com.phonghub.domain.model.HandoverPhotoKind;
import com.phonghub.domain.model.HandoverStatus;
import com.phonghub.domain.model.HandoverType;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.Tenant;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class HandoverReportService implements HandoverReportUseCase {

    private static final int MAX_PHOTOS_PER_SIDE = 3;
    private static final int MAX_PHOTO_BYTES = 4 * 1024 * 1024;

    private final HandoverRepositoryPort handoverRepository;
    private final HandoverItemRepositoryPort itemRepository;
    private final HandoverPhotoRepositoryPort photoRepository;
    private final ContractRepositoryPort contractRepository;
    private final RoomRepositoryPort roomRepository;
    private final PropertyRepositoryPort propertyRepository;
    private final TenantRepositoryPort tenantRepository;
    private final UserRepositoryPort userRepository;
    private final FileStoragePort storagePort;
    private final HandoverPdfPort pdfPort;
    private final CurrentUserPort currentUserPort;
    private final AuthorizationService authorizationService;
    private final AuditPort auditPort;

    public HandoverReportService(
        HandoverRepositoryPort handoverRepository,
        HandoverItemRepositoryPort itemRepository,
        HandoverPhotoRepositoryPort photoRepository,
        ContractRepositoryPort contractRepository,
        RoomRepositoryPort roomRepository,
        PropertyRepositoryPort propertyRepository,
        TenantRepositoryPort tenantRepository,
        UserRepositoryPort userRepository,
        FileStoragePort storagePort,
        HandoverPdfPort pdfPort,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        AuditPort auditPort
    ) {
        this.handoverRepository = handoverRepository;
        this.itemRepository = itemRepository;
        this.photoRepository = photoRepository;
        this.contractRepository = contractRepository;
        this.roomRepository = roomRepository;
        this.propertyRepository = propertyRepository;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.storagePort = storagePort;
        this.pdfPort = pdfPort;
        this.currentUserPort = currentUserPort;
        this.authorizationService = authorizationService;
        this.auditPort = auditPort;
    }

    @Override
    public Comparison compare(UUID contractId) {
        CurrentUser user = currentUserPort.getCurrentUser();
        Contract contract = findContract(contractId);
        assertCanView(user, contract);
        Handover checkIn = handoverRepository.find(contractId, HandoverType.CHECK_IN)
            .orElseThrow(() -> new HandoverNotFoundException("Hợp đồng chưa có biên bản check-in"));
        Handover checkOut = handoverRepository.find(contractId, HandoverType.CHECK_OUT).orElse(null);

        List<HandoverItem> inItems = itemRepository.findByHandoverId(checkIn.id());
        List<HandoverItem> outItems = checkOut == null ? List.of() : itemRepository.findByHandoverId(checkOut.id());
        List<ItemRow> rows = buildRows(inItems, outItems, checkOut != null);

        BigDecimal electric = checkOut == null ? null
            : checkOut.initialElectricMeter().subtract(checkIn.initialElectricMeter());
        BigDecimal water = checkOut == null ? null
            : checkOut.initialWaterMeter().subtract(checkIn.initialWaterMeter());
        return new Comparison(checkIn, checkOut, rows, roomPhotos(checkIn), roomPhotos(checkOut), electric, water);
    }

    @Override
    public PdfDocument exportPdf(UUID contractId, HandoverType type) {
        CurrentUser user = currentUserPort.getCurrentUser();
        Contract contract = findContract(contractId);
        assertCanView(user, contract);
        Handover handover = handoverRepository.find(contractId, type)
            .orElseThrow(() -> new HandoverNotFoundException("Hợp đồng chưa có biên bản " + type));
        if (handover.status() != HandoverStatus.APPROVED) {
            throw new InvalidHandoverStateException("Chỉ xuất PDF biên bản đã được duyệt (hiện: " + handover.status() + ")");
        }
        Room room = roomRepository.findById(contract.getRoomId()).orElse(null);
        Property property = propertyRepository.findById(contract.getPropertyId()).orElse(null);
        String tenantName = tenantRepository.findById(contract.getPrimaryTenantId()).map(Tenant::fullName).orElse("");
        String approver = handover.approvedBy() == null ? null
            : userRepository.findById(handover.approvedBy()).map(u -> u.fullName()).orElse(null);

        List<HandoverItem> own = itemRepository.findByHandoverId(handover.id());
        List<ItemRow> rows;
        if (type == HandoverType.CHECK_OUT) {
            Optional<Handover> checkIn = handoverRepository.find(contractId, HandoverType.CHECK_IN);
            List<HandoverItem> inItems = checkIn.map(h -> itemRepository.findByHandoverId(h.id())).orElse(List.of());
            rows = buildRows(inItems, own, true);
        } else {
            rows = buildRows(own, List.of(), false);
        }

        List<HandoverPdfPort.Photo> photos = new ArrayList<>();
        addPhotos(photos, handover, type == HandoverType.CHECK_OUT ? "Ảnh phòng khi trả" : "Ảnh phòng khi nhận");
        Instant now = Instant.now();
        HandoverPdfPort.Report report = new HandoverPdfPort.Report(
            type == HandoverType.CHECK_OUT ? "BIÊN BẢN TRẢ PHÒNG" : "BIÊN BẢN NHẬN PHÒNG",
            property == null ? "" : property.name(),
            room == null ? "" : room.getRoomNumber(),
            tenantName,
            contract.getId().toString(),
            handover.status().name(),
            type == HandoverType.CHECK_OUT,
            handover.sentAt(), handover.confirmedAt(), handover.approvedAt(), approver, now,
            handover.initialElectricMeter(), handover.initialWaterMeter(),
            handover.roomCondition(), handover.keysProvided(), toPdfRows(rows),
            handover.finalDeduction(), handover.contractTermsHash(), handover.houseRulesHash(), photos);
        byte[] pdf = pdfPort.render(report);
        auditPort.recordEvent(AuditPort.AuditEvent.of("HANDOVER_PDF_EXPORTED", user.id(), "CONTRACT",
            contractId.toString(), Map.of("type", type.name(), "handoverId", handover.id().toString())));
        String filename = (type == HandoverType.CHECK_OUT ? "bien-ban-tra-phong-" : "bien-ban-nhan-phong-")
            + contractId.toString().substring(0, 8) + ".pdf";
        return new PdfDocument(filename, pdf);
    }

    /** Ghép tài sản của check-in và check-out theo tài sản gốc (hoặc tên khi tài sản gốc đã bị xóa). */
    private List<ItemRow> buildRows(List<HandoverItem> inItems, List<HandoverItem> outItems, boolean hasCheckOut) {
        Map<String, HandoverItem[]> pairs = new LinkedHashMap<>();
        for (HandoverItem item : inItems) {
            pairs.computeIfAbsent(key(item), k -> new HandoverItem[2])[0] = item;
        }
        for (HandoverItem item : outItems) {
            pairs.computeIfAbsent(key(item), k -> new HandoverItem[2])[1] = item;
        }
        List<ItemRow> rows = new ArrayList<>();
        for (HandoverItem[] pair : pairs.values()) {
            HandoverItem in = pair[0];
            HandoverItem out = pair[1];
            HandoverItem any = in != null ? in : out;
            boolean changed = hasCheckOut && (in == null || out == null || in.status() != out.status()
                || !Objects.equals(blankToNull(in.conditionNote()), blankToNull(out.conditionNote())));
            rows.add(new ItemRow(any.assetName(), any.quantity(),
                in == null ? null : in.status(), in == null ? null : in.conditionNote(),
                out == null ? null : out.status(), out == null ? null : out.conditionNote(),
                out == null ? BigDecimal.ZERO : out.deductionAmount(), changed));
        }
        return rows;
    }

    private static String key(HandoverItem item) {
        return item.assetId() != null ? item.assetId().toString() : "name:" + item.assetName().trim().toLowerCase();
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    private List<HandoverPdfPort.Row> toPdfRows(List<ItemRow> rows) {
        return rows.stream().map(row -> new HandoverPdfPort.Row(row.assetName(), row.quantity(),
            row.checkInStatus() == null ? null : row.checkInStatus().name(), row.checkInNote(),
            row.checkOutStatus() == null ? null : row.checkOutStatus().name(), row.checkOutNote(),
            row.deduction(), row.changed())).toList();
    }

    private List<HandoverPhoto> roomPhotos(Handover handover) {
        if (handover == null) {
            return List.of();
        }
        return photoRepository.findByHandoverId(handover.id()).stream()
            .filter(photo -> photo.kind() == HandoverPhotoKind.ROOM).toList();
    }

    private void addPhotos(List<HandoverPdfPort.Photo> target, Handover handover, String caption) {
        int added = 0;
        for (HandoverPhoto photo : roomPhotos(handover)) {
            if (added >= MAX_PHOTOS_PER_SIDE) {
                break;
            }
            try {
                byte[] content = storagePort.get(photo.imageKey());
                if (content != null && content.length > 0 && content.length <= MAX_PHOTO_BYTES) {
                    target.add(new HandoverPdfPort.Photo(caption, content));
                    added++;
                }
            } catch (RuntimeException ignored) {
                // ảnh không tải được thì bỏ qua, biên bản vẫn xuất được
            }
        }
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

    private Contract findContract(UUID contractId) {
        if (contractId == null) {
            throw new IllegalArgumentException("Contract id is required");
        }
        return contractRepository.findById(contractId)
            .orElseThrow(() -> new ContractNotFoundException("Không tìm thấy hợp đồng: " + contractId));
    }
}
