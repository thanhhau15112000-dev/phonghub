package com.phonghub.application.port.in;

import com.phonghub.domain.model.AssetEventType;
import com.phonghub.domain.model.RoomAsset;
import com.phonghub.domain.model.RoomAssetEvent;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;

public interface RoomAssetUseCase {

    RoomAsset create(CreateAssetCommand command);

    List<RoomAsset> createFromTemplate(UUID roomId, String template);

    /**
     * Thêm nhiều tài sản một lần (người dùng tự nhập danh sách) vào một phòng, có thể áp thêm cho các phòng khác cùng nhà
     * trọ. Kiểm tra hết quyền và dữ liệu trước khi lưu: một dòng sai thì không phòng nào được thêm.
     */
    List<RoomAsset> createBatch(CreateBatchCommand command);

    RoomAsset update(UpdateAssetCommand command);

    void delete(UUID assetId);

    List<RoomAsset> list(UUID roomId);

    RoomAsset get(UUID assetId);

    RoomAssetEvent recordEvent(RecordEventCommand command);

    List<RoomAssetEvent> history(UUID assetId);

    /** Link ký có hạn tới ảnh tài sản lưu trong kho riêng tư; null nếu tài sản không có ảnh. */
    URI imageSignedUrl(UUID assetId);

    record CreateAssetCommand(
        UUID roomId,
        String assetCode,
        String name,
        String serialNumber,
        int quantity,
        String note,
        String imageUrl,
        BigDecimal estimatedValue
    ) {}

    record BatchItem(String name, int quantity, BigDecimal estimatedValue) {}

    record CreateBatchCommand(UUID roomId, List<UUID> alsoRoomIds, List<BatchItem> items) {
        public static final int MAX_ITEMS = 30;
        public static final int MAX_ROOMS = 50;

        public CreateBatchCommand {
            if (roomId == null) {
                throw new IllegalArgumentException("Room id is required");
            }
            alsoRoomIds = alsoRoomIds == null ? List.of() : List.copyOf(alsoRoomIds);
            items = items == null ? List.of() : List.copyOf(items);
            if (items.isEmpty()) {
                throw new IllegalArgumentException("Cần nhập ít nhất một tài sản.");
            }
            if (items.size() > MAX_ITEMS) {
                throw new IllegalArgumentException("Mỗi lần thêm tối đa " + MAX_ITEMS + " tài sản.");
            }
            if (alsoRoomIds.size() + 1 > MAX_ROOMS) {
                throw new IllegalArgumentException("Mỗi lần áp dụng cho tối đa " + MAX_ROOMS + " phòng.");
            }
        }
    }

    record UpdateAssetCommand(
        UUID assetId,
        String name,
        String serialNumber,
        int quantity,
        String note,
        String imageUrl,
        BigDecimal estimatedValue
    ) {}

    record RecordEventCommand(
        UUID assetId,
        AssetEventType type,
        String description,
        BigDecimal cost,
        UUID maintenanceTicketId
    ) {}
}
