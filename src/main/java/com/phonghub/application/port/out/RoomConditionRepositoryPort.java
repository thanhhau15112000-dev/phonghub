package com.phonghub.application.port.out;

import com.phonghub.domain.model.RoomConditionItem;
import com.phonghub.domain.model.RoomConditionSnapshot;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomConditionRepositoryPort {
    RoomConditionSnapshot save(RoomConditionSnapshot snapshot);
    Optional<RoomConditionSnapshot> findLatestByRoomId(UUID roomId);
    List<RoomConditionItem> findItemsBySnapshotId(UUID snapshotId);
    void saveItems(UUID snapshotId, List<RoomConditionItem> items);
}
