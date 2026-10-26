package com.phonghub.application.service;

import com.phonghub.application.port.in.RoomAssetUseCase;
import com.phonghub.application.port.in.RoomSetupUseCase;
import com.phonghub.application.port.in.RoomUseCase;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.PropertyRepositoryPort;
import com.phonghub.application.port.out.RoomConditionRepositoryPort;
import com.phonghub.application.port.out.RoomRepositoryPort;
import com.phonghub.domain.exception.DuplicateRoomNumberException;
import com.phonghub.domain.exception.PropertyNotFoundException;
import com.phonghub.domain.exception.RoomNotFoundException;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomAsset;
import com.phonghub.domain.model.RoomConditionItem;
import com.phonghub.domain.model.RoomConditionSnapshot;
import com.phonghub.domain.model.RoomSearchCriteria;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public class RoomSetupService implements RoomSetupUseCase {
    private final RoomUseCase roomUseCase;
    private final RoomAssetUseCase assetUseCase;
    private final RoomConditionRepositoryPort conditionRepository;
    private final RoomRepositoryPort roomRepository;
    private final PropertyRepositoryPort propertyRepository;
    private final CurrentUserPort currentUserPort;
    private final AuthorizationService authorizationService;

    public RoomSetupService(RoomUseCase roomUseCase, RoomAssetUseCase assetUseCase,
                            RoomConditionRepositoryPort conditionRepository, RoomRepositoryPort roomRepository,
                            PropertyRepositoryPort propertyRepository, CurrentUserPort currentUserPort,
                            AuthorizationService authorizationService) {
        this.roomUseCase = roomUseCase;
        this.assetUseCase = assetUseCase;
        this.conditionRepository = conditionRepository;
        this.roomRepository = roomRepository;
        this.propertyRepository = propertyRepository;
        this.currentUserPort = currentUserPort;
        this.authorizationService = authorizationService;
    }

    @Override
    public RoomSetupResult createRoom(CreateRoomCommand command) {
        CurrentUser user = currentUserPort.getCurrentUser();
        authorizationService.assertCanManageRoomsAndAssets(user, command.propertyId());
        assertRoomCapacity(command.propertyId(), 1);
        validatePhotoKey(command.propertyId(), command.electricMeterPhotoKey());
        validatePhotoKey(command.propertyId(), command.waterMeterPhotoKey());
        Room room = roomUseCase.createRoom(new RoomUseCase.CreateRoomCommand(command.propertyId(), command.roomNumber(),
            command.floor(), command.areaSqm(), command.basePrice(), command.maxOccupants()));
        Supplier<List<RoomAsset>> createAssets = switch (command.assetTemplate()) {
            case "BASIC", "FULL" -> () -> assetUseCase.createFromTemplate(room.getId(), command.assetTemplate());
            default -> () -> command.assets().stream().map(item -> assetUseCase.create(
                new RoomAssetUseCase.CreateAssetCommand(room.getId(), null, item.name(), null, item.quantity(),
                    item.note(), null, item.estimatedValue(), item.status()))).toList();
        };
        return saveCondition(room, command.electricMeter(), command.waterMeter(), command.conditionNote(),
            createAssets, command.electricMeterPhotoKey(), command.waterMeterPhotoKey(), user);
    }

    @Override
    public List<RoomSetupResult> createRooms(BulkCreateCommand command) {
        CurrentUser user = currentUserPort.getCurrentUser();
        authorizationService.assertCanManageRoomsAndAssets(user, command.propertyId());
        assertRoomCapacity(command.propertyId(), command.rooms().size());
        Set<String> numbers = new HashSet<>();
        for (RoomReadingInput input : command.rooms()) {
            String key = input.roomNumber().trim().toLowerCase(Locale.ROOT);
            if (!numbers.add(key) || roomRepository.findByPropertyIdAndRoomNumber(command.propertyId(), input.roomNumber()).isPresent()) {
                throw new DuplicateRoomNumberException("Số phòng '" + input.roomNumber().trim() + "' đã tồn tại hoặc bị lặp; không có phòng nào được tạo.");
            }
        }
        List<RoomSetupResult> created = new ArrayList<>();
        for (RoomReadingInput input : command.rooms()) {
            Room room = roomUseCase.createRoom(new RoomUseCase.CreateRoomCommand(command.propertyId(),
                input.roomNumber(), command.floor(), command.areaSqm(), command.basePrice(), command.maxOccupants()));
            created.add(saveCondition(room, input.electricMeter(), input.waterMeter(), command.conditionNote(),
                () -> "NONE".equals(command.assetTemplate()) ? List.of()
                    : assetUseCase.createFromTemplate(room.getId(), command.assetTemplate()), null, null, user));
        }
        return List.copyOf(created);
    }

    @Override
    public RoomConditionDetails latestCondition(UUID roomId) {
        Room room = roomRepository.findById(roomId).orElseThrow(() -> new RoomNotFoundException("Không tìm thấy phòng."));
        authorizationService.assertCanAccessProperty(currentUserPort.getCurrentUser(), room.getPropertyId());
        RoomConditionSnapshot snapshot = conditionRepository.findLatestByRoomId(roomId).orElse(null);
        return snapshot == null ? new RoomConditionDetails(null, List.of())
            : new RoomConditionDetails(snapshot, conditionRepository.findItemsBySnapshotId(snapshot.id()));
    }

    private RoomSetupResult saveCondition(Room room, BigDecimal electric, BigDecimal water, String note,
                                          Supplier<List<RoomAsset>> createAssets, String electricPhoto, String waterPhoto,
                                          CurrentUser user) {
        RoomConditionSnapshot snapshot = conditionRepository.save(new RoomConditionSnapshot(UUID.randomUUID(), room.getId(),
            electric, water, note, electricPhoto, waterPhoto, Instant.now(), user.id()));
        List<RoomConditionItem> items = new ArrayList<>();
        int position = 0;
        for (RoomAsset saved : createAssets.get()) {
            items.add(new RoomConditionItem(UUID.randomUUID(), snapshot.id(), saved.id(), position++, saved.name(),
                saved.quantity(), saved.status(), saved.note(), saved.estimatedValue()));
        }
        conditionRepository.saveItems(snapshot.id(), items);
        return new RoomSetupResult(room, snapshot, items);
    }

    private static void validatePhotoKey(UUID propertyId, String objectKey) {
        if (objectKey == null) return;
        String prefix = "properties/" + propertyId + "/room-condition/";
        if (!objectKey.startsWith(prefix) || objectKey.contains("..")) {
            throw new IllegalArgumentException("Ảnh đồng hồ không thuộc nhà trọ này.");
        }
    }

    private void assertRoomCapacity(UUID propertyId, int roomCount) {
        // Serialize count-and-create for this property across concurrent application instances.
        propertyRepository.lockById(propertyId);
        Property property = propertyRepository.findById(propertyId)
            .orElseThrow(() -> new PropertyNotFoundException("Không tìm thấy nhà trọ: " + propertyId));
        int currentCount = roomRepository.findByPropertyId(propertyId).size()
            + Math.toIntExact(roomRepository.search(new RoomSearchCriteria(propertyId, null, null, null,
                null, null, 0, null, true)).totalElements());
        if (property.totalRooms() > 0 && currentCount + roomCount > property.totalRooms()) {
            throw new IllegalArgumentException("Số phòng tạo mới vượt tổng số phòng đã khai báo ("
                + property.totalRooms() + ").");
        }
    }
}
