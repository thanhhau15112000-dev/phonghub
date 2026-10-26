package com.phonghub.application.port.in;

import com.phonghub.domain.model.AssetStatus;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomConditionItem;
import com.phonghub.domain.model.RoomConditionSnapshot;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Creates rooms with their immutable initial condition record. */
public interface RoomSetupUseCase {
    RoomSetupResult createRoom(CreateRoomCommand command);
    List<RoomSetupResult> createRooms(BulkCreateCommand command);
    RoomConditionDetails latestCondition(UUID roomId);

    record AssetInput(String name, int quantity, AssetStatus status, String note, BigDecimal estimatedValue) {}
    record RoomReadingInput(String roomNumber, BigDecimal electricMeter, BigDecimal waterMeter) {}

    record CreateRoomCommand(
        UUID propertyId, String roomNumber, int floor, BigDecimal areaSqm, BigDecimal basePrice, int maxOccupants,
        BigDecimal electricMeter, BigDecimal waterMeter, String conditionNote, boolean noAssets, String assetTemplate,
        List<AssetInput> assets, String electricMeterPhotoKey, String waterMeterPhotoKey
    ) {
        public CreateRoomCommand {
            assets = assets == null ? List.of() : List.copyOf(assets);
            assetTemplate = assetTemplate == null || assetTemplate.isBlank()
                ? "MANUAL" : assetTemplate.trim().toUpperCase(java.util.Locale.ROOT);
            validateCondition(electricMeter, waterMeter, conditionNote, noAssets, assetTemplate, assets);
        }

        public CreateRoomCommand(UUID propertyId, String roomNumber, int floor, BigDecimal areaSqm,
                                 BigDecimal basePrice, int maxOccupants, BigDecimal electricMeter,
                                 BigDecimal waterMeter, String conditionNote, boolean noAssets,
                                 List<AssetInput> assets, String electricMeterPhotoKey, String waterMeterPhotoKey) {
            this(propertyId, roomNumber, floor, areaSqm, basePrice, maxOccupants, electricMeter, waterMeter,
                conditionNote, noAssets, "MANUAL", assets, electricMeterPhotoKey, waterMeterPhotoKey);
        }
    }

    record BulkCreateCommand(
        UUID propertyId, int floor, BigDecimal areaSqm, BigDecimal basePrice, int maxOccupants,
        String conditionNote, String assetTemplate, List<RoomReadingInput> rooms
    ) {
        public static final int MAX_ROOMS = 50;
        public BulkCreateCommand {
            rooms = rooms == null ? List.of() : List.copyOf(rooms);
            if (rooms.isEmpty()) throw new IllegalArgumentException("Cần nhập ít nhất một phòng.");
            if (rooms.size() > MAX_ROOMS) throw new IllegalArgumentException("Mỗi lần tạo tối đa " + MAX_ROOMS + " phòng.");
            if (assetTemplate == null) throw new IllegalArgumentException("Mẫu tài sản không hợp lệ.");
            assetTemplate = assetTemplate.trim().toUpperCase(java.util.Locale.ROOT);
            if (!List.of("NONE", "BASIC", "FULL").contains(assetTemplate))
                throw new IllegalArgumentException("Mẫu tài sản không hợp lệ.");
            validateNote(conditionNote);
            for (RoomReadingInput room : rooms) {
                if (room == null || room.roomNumber() == null || room.roomNumber().isBlank()) {
                    throw new IllegalArgumentException("Số phòng không được để trống.");
                }
                validateMeters(room.electricMeter(), room.waterMeter());
            }
        }
    }

    record RoomSetupResult(Room room, RoomConditionSnapshot snapshot, List<RoomConditionItem> items) {
        public RoomSetupResult { items = items == null ? List.of() : List.copyOf(items); }
    }

    record RoomConditionDetails(RoomConditionSnapshot snapshot, List<RoomConditionItem> items) {
        public RoomConditionDetails { items = items == null ? List.of() : List.copyOf(items); }
    }

    private static void validateCondition(BigDecimal electric, BigDecimal water, String note, boolean noAssets,
                                          String assetTemplate, List<AssetInput> assets) {
        validateMeters(electric, water);
        validateNote(note);
        if (!List.of("MANUAL", "BASIC", "FULL").contains(assetTemplate)) {
            throw new IllegalArgumentException("Mẫu tài sản không hợp lệ.");
        }
        if ("MANUAL".equals(assetTemplate)) {
            if (noAssets == !assets.isEmpty()) {
                throw new IllegalArgumentException("Cần nhập tài sản hiện trạng hoặc xác nhận phòng không có tài sản.");
            }
        } else if (noAssets || !assets.isEmpty()) {
            throw new IllegalArgumentException("Chỉ chọn mẫu tài sản hoặc nhập thủ công, không chọn đồng thời.");
        }
        if (assets != null && assets.size() > 30) {
            throw new IllegalArgumentException("Mỗi phòng ghi nhận tối đa 30 loại tài sản.");
        }
        if (assets != null) {
            for (AssetInput asset : assets) {
                if (asset == null || asset.name() == null || asset.name().isBlank() || asset.quantity() <= 0
                    || asset.status() == null) {
                    throw new IllegalArgumentException("Thông tin tài sản hiện trạng không hợp lệ.");
                }
                if (asset.name().trim().length() > com.phonghub.domain.model.RoomAsset.MAX_NAME_LENGTH
                    || asset.note() != null && asset.note().length() > com.phonghub.domain.model.RoomAsset.MAX_NOTE_LENGTH
                    || asset.estimatedValue() != null && (asset.estimatedValue().signum() < 0
                        || asset.estimatedValue().compareTo(com.phonghub.domain.model.RoomAsset.MAX_ESTIMATED_VALUE) > 0)) {
                    throw new IllegalArgumentException("Tên, ghi chú hoặc giá trị tài sản hiện trạng vượt giới hạn cho phép.");
                }
            }
        }
    }

    private static void validateMeters(BigDecimal electric, BigDecimal water) {
        RoomConditionSnapshot.validateMeter(electric, "điện");
        RoomConditionSnapshot.validateMeter(water, "nước");
    }

    private static void validateNote(String note) {
        if (note == null || note.isBlank() || note.trim().length() > 2000) {
            throw new IllegalArgumentException("Ghi chú hiện trạng phòng là bắt buộc (tối đa 2000 ký tự).");
        }
    }
}
