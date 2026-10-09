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
