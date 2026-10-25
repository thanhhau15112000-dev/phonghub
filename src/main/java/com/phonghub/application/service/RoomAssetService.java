package com.phonghub.application.service;

import com.phonghub.application.port.in.RoomAssetUseCase;
import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.FileStoragePort;
import com.phonghub.application.port.out.MaintenanceTicketRepositoryPort;
import com.phonghub.application.port.out.RoomAssetEventRepositoryPort;
import com.phonghub.application.port.out.RoomAssetRepositoryPort;
import com.phonghub.application.port.out.RoomRepositoryPort;
import com.phonghub.domain.exception.DuplicateAssetCodeException;
import com.phonghub.domain.exception.RoomAssetNotFoundException;
import com.phonghub.domain.exception.RoomNotFoundException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.AssetStatus;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomAsset;
import com.phonghub.domain.model.RoomAssetEvent;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class RoomAssetService implements RoomAssetUseCase {

    private static final Duration SIGNED_URL_TTL = Duration.ofMinutes(10);
    private static final List<TemplateItem> BASIC_TEMPLATE = List.of(
        new TemplateItem("Giường", 1),
        new TemplateItem("Nệm", 1),
        new TemplateItem("Tủ quần áo", 1),
        new TemplateItem("Bàn học", 1),
        new TemplateItem("Ghế", 2)
    );
    private static final List<TemplateItem> FULL_TEMPLATE = concat(BASIC_TEMPLATE, List.of(
        new TemplateItem("Điều hòa", 1),
        new TemplateItem("Máy nước nóng", 1),
        new TemplateItem("Quạt trần", 1),
        new TemplateItem("Tủ lạnh mini", 1)
    ));
    private static final Map<String, List<TemplateItem>> TEMPLATES = Map.of(
        "BASIC", BASIC_TEMPLATE,
        "FULL", FULL_TEMPLATE
    );

    private final RoomAssetRepositoryPort assetRepository;
    private final RoomAssetEventRepositoryPort eventRepository;
    private final RoomRepositoryPort roomRepository;
    private final CurrentUserPort currentUserPort;
    private final AuthorizationService authorizationService;
    private final AuditPort auditPort;
    private final MaintenanceTicketRepositoryPort ticketRepository;
    private final FileStoragePort fileStoragePort;

    public RoomAssetService(
        RoomAssetRepositoryPort assetRepository,
        RoomAssetEventRepositoryPort eventRepository,
        RoomRepositoryPort roomRepository,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        AuditPort auditPort,
        MaintenanceTicketRepositoryPort ticketRepository,
        FileStoragePort fileStoragePort
    ) {
        this.assetRepository = assetRepository;
        this.eventRepository = eventRepository;
        this.roomRepository = roomRepository;
        this.currentUserPort = currentUserPort;
        this.authorizationService = authorizationService;
        this.auditPort = auditPort;
        this.ticketRepository = ticketRepository;
        this.fileStoragePort = fileStoragePort;
    }

    @Override
    public RoomAsset create(CreateAssetCommand command) {
        CurrentUser user = currentUserPort.getCurrentUser();
        Room room = findRoom(command.roomId());
        authorizationService.assertCanManageRoomsAndAssets(user, room.getPropertyId());

        String code = resolveAssetCode(command.assetCode());
        RoomAsset asset = newAsset(room.getId(), code, command.name(), command.serialNumber(),
            command.quantity(), command.note(), command.imageUrl(), command.estimatedValue(), command.status(), user);
        RoomAsset saved = assetRepository.save(asset);
        audit("ROOM_ASSET_CREATED", user, saved, Map.of("assetCode", saved.assetCode(), "name", saved.name()));
        return saved;
    }

    @Override
    public List<RoomAsset> createFromTemplate(UUID roomId, String template) {
        CurrentUser user = currentUserPort.getCurrentUser();
        Room room = findRoom(roomId);
        authorizationService.assertCanManageRoomsAndAssets(user, room.getPropertyId());

        List<TemplateItem> items = TEMPLATES.get(template == null ? "" : template.trim().toUpperCase(Locale.ROOT));
        if (items == null) {
            throw new IllegalArgumentException("Mẫu tài sản không hợp lệ (chỉ có BASIC hoặc FULL).");
        }
        List<RoomAsset> assets = new ArrayList<>();
        for (TemplateItem item : items) {
            assets.add(newAsset(room.getId(), generateAssetCode(), item.name(), null, item.quantity(),
                null, null, null, AssetStatus.GOOD, user));
        }
        List<RoomAsset> saved = assets.stream().map(assetRepository::save).toList();
        audit("ROOM_ASSET_TEMPLATE_APPLIED", user, saved.getFirst(),
            Map.of("template", template.trim().toUpperCase(Locale.ROOT), "count", saved.size()));
        return saved;
    }

    @Override
    public List<RoomAsset> createBatch(CreateBatchCommand command) {
        CurrentUser user = currentUserPort.getCurrentUser();
        Room primary = findRoom(command.roomId());
        authorizationService.assertCanManageRoomsAndAssets(user, primary.getPropertyId());

        java.util.LinkedHashSet<UUID> roomIds = new java.util.LinkedHashSet<>();
        roomIds.add(primary.getId());
        roomIds.addAll(command.alsoRoomIds());
        List<Room> rooms = new ArrayList<>();
        for (UUID roomId : roomIds) {
            Room room = findRoom(roomId);
            if (!room.getPropertyId().equals(primary.getPropertyId())) {
                throw new IllegalArgumentException("Chỉ áp dụng được cho các phòng cùng một nhà trọ.");
            }
            rooms.add(room);
        }

        // Dựng (và kiểm tra) toàn bộ tài sản trước khi lưu để một dòng sai không để lại phòng được thêm dở.
        java.util.Set<String> codes = new java.util.HashSet<>();
        List<RoomAsset> assets = new ArrayList<>();
        for (Room room : rooms) {
            for (BatchItem item : command.items()) {
                String code;
                do {
                    code = generateAssetCode();
                } while (!codes.add(code));
                assets.add(newAsset(room.getId(), code, item.name(), null, item.quantity(), item.note(), null,
                    item.estimatedValue(), item.status(), user));
            }
        }
        List<RoomAsset> saved = assets.stream().map(assetRepository::save).toList();
        for (Room room : rooms) {
            List<RoomAsset> inRoom = saved.stream().filter(a -> a.roomId().equals(room.getId())).toList();
            audit("ROOM_ASSET_BATCH_CREATED", user, inRoom.getFirst(), Map.of(
                "roomId", room.getId().toString(), "count", inRoom.size(),
                "names", inRoom.stream().map(RoomAsset::name).toList()));
        }
        return saved;
    }

    @Override
    public RoomAsset update(UpdateAssetCommand command) {
        CurrentUser user = currentUserPort.getCurrentUser();
        RoomAsset asset = findActiveAsset(command.assetId());
        authorizationService.assertCanManageRoomsAndAssets(user, propertyOf(asset));

        RoomAsset updated = asset.update(command.name(), command.serialNumber(), command.quantity(),
            command.note(), command.imageUrl(), command.estimatedValue(), user.id());
        RoomAsset saved = assetRepository.save(updated);
        audit("ROOM_ASSET_UPDATED", user, saved, Map.of("assetCode", saved.assetCode()));
        return saved;
    }

    @Override
    public void delete(UUID assetId) {
        CurrentUser user = currentUserPort.getCurrentUser();
        RoomAsset asset = findActiveAsset(assetId);
        authorizationService.assertCanManageRoomsAndAssets(user, propertyOf(asset));

        RoomAsset deleted = assetRepository.save(asset.softDelete(user.id()));
        audit("ROOM_ASSET_DELETED", user, deleted, Map.of("assetCode", deleted.assetCode()));
    }

    @Override
    public List<RoomAsset> list(UUID roomId) {
        CurrentUser user = currentUserPort.getCurrentUser();
        Room room = findRoom(roomId);
        assertCanView(user, room);
        return assetRepository.findActiveByRoomId(roomId);
    }

    @Override
    public RoomAsset get(UUID assetId) {
        CurrentUser user = currentUserPort.getCurrentUser();
        RoomAsset asset = findActiveAsset(assetId);
        assertCanView(user, findRoom(asset.roomId()));
        return asset;
    }

    @Override
    public RoomAssetEvent recordEvent(RecordEventCommand command) {
        CurrentUser user = currentUserPort.getCurrentUser();
        RoomAsset asset = findActiveAsset(command.assetId());
        authorizationService.assertCanManageRoomsAndAssets(user, propertyOf(asset));
        assertTicketBelongsToAssetRoom(command.maintenanceTicketId(), asset);

        RoomAssetEvent event = new RoomAssetEvent(UUID.randomUUID(), asset.id(), command.type(),
            command.description(), command.cost(), command.maintenanceTicketId(), Instant.now(), user.id());
        RoomAssetEvent saved = eventRepository.save(event);
        AssetStatus newStatus = command.type().resultingStatus();
        if (newStatus != asset.status()) {
            assetRepository.save(asset.withStatus(newStatus, user.id()));
        }
        audit("ROOM_ASSET_EVENT_RECORDED", user, asset,
            Map.of("assetCode", asset.assetCode(), "type", command.type().name(), "cost", saved.cost().toPlainString()));
        return saved;
    }

    @Override
    public List<RoomAssetEvent> history(UUID assetId) {
        CurrentUser user = currentUserPort.getCurrentUser();
        RoomAsset asset = findActiveAsset(assetId);
        assertCanView(user, findRoom(asset.roomId()));
        return eventRepository.findByAssetId(assetId);
    }

    @Override
    public URI imageSignedUrl(UUID assetId) {
        CurrentUser user = currentUserPort.getCurrentUser();
        RoomAsset asset = findActiveAsset(assetId);
        assertCanView(user, findRoom(asset.roomId()));
        String image = asset.imageUrl();
        if (image == null || image.isBlank()) {
            return null;
        }
        if (image.startsWith("http://") || image.startsWith("https://")) {
            return URI.create(image);
        }
        return fileStoragePort.createSignedUrl(image, SIGNED_URL_TTL);
    }

    private void assertTicketBelongsToAssetRoom(UUID ticketId, RoomAsset asset) {
        if (ticketId == null) {
            return;
        }
        var ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiếu bảo trì: " + ticketId));
        if (!ticket.getRoomId().equals(asset.roomId())) {
            throw new IllegalArgumentException("Phiếu bảo trì không thuộc phòng của tài sản này.");
        }
    }

    private void assertCanView(CurrentUser user, Room room) {
        if (user != null && user.isTenant()) {
            if (!authorizationService.canTenantViewRoom(user, room.getId())) {
                throw new UnauthorizedPropertyAccessException("Tenant has no active occupancy in room " + room.getId());
            }
            return;
        }
        authorizationService.assertCanAccessProperty(user, room.getPropertyId());
    }

    private Room findRoom(UUID roomId) {
        if (roomId == null) {
            throw new IllegalArgumentException("Room id is required");
        }
        return roomRepository.findById(roomId).orElseThrow(() -> new RoomNotFoundException("Không tìm thấy phòng."));
    }

    private RoomAsset findActiveAsset(UUID assetId) {
        if (assetId == null) {
            throw new IllegalArgumentException("Asset id is required");
        }
        return assetRepository.findById(assetId)
            .filter(asset -> !asset.isDeleted())
            .orElseThrow(() -> new RoomAssetNotFoundException(assetId));
    }

    private UUID propertyOf(RoomAsset asset) {
        return findRoom(asset.roomId()).getPropertyId();
    }

    private RoomAsset newAsset(
        UUID roomId, String code, String name, String serial, int quantity,
        String note, String imageUrl, java.math.BigDecimal value, AssetStatus status, CurrentUser user
    ) {
        Instant now = Instant.now();
        return new RoomAsset(UUID.randomUUID(), roomId, code, name, serial, quantity,
            status == null ? AssetStatus.GOOD : status,
            note, imageUrl, value, null, now, user.id(), now, user.id());
    }

    private String resolveAssetCode(String requested) {
        if (requested == null || requested.isBlank()) {
            return generateAssetCode();
        }
        String code = requested.trim();
        if (assetRepository.existsByAssetCode(code)) {
            throw new DuplicateAssetCodeException(code);
        }
        return code;
    }

    private String generateAssetCode() {
        String code;
        do {
            code = "TS-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        } while (assetRepository.existsByAssetCode(code));
        return code;
    }

    private void audit(String action, CurrentUser user, RoomAsset asset, Map<String, Object> details) {
        auditPort.recordEvent(AuditPort.AuditEvent.of(action, user.id(), "ROOM_ASSET", asset.id().toString(), details));
    }

    private static List<TemplateItem> concat(List<TemplateItem> first, List<TemplateItem> second) {
        List<TemplateItem> all = new ArrayList<>(first);
        all.addAll(second);
        return List.copyOf(all);
    }

    private record TemplateItem(String name, int quantity) {}
}
