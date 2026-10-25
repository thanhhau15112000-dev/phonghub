package com.phonghub.adapter.out.persistence.inmemory;

import com.phonghub.application.port.out.RoomConditionRepositoryPort;
import com.phonghub.domain.model.RoomConditionItem;
import com.phonghub.domain.model.RoomConditionSnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryRoomConditionRepository implements RoomConditionRepositoryPort {
    private final Map<UUID, RoomConditionSnapshot> snapshots = new ConcurrentHashMap<>();
    private final Map<UUID, List<RoomConditionItem>> items = new ConcurrentHashMap<>();

    @Override public RoomConditionSnapshot save(RoomConditionSnapshot snapshot) {
        snapshots.put(snapshot.id(), snapshot);
        return snapshot;
    }

    @Override public Optional<RoomConditionSnapshot> findLatestByRoomId(UUID roomId) {
        return snapshots.values().stream().filter(item -> item.roomId().equals(roomId))
            .max(Comparator.comparing(RoomConditionSnapshot::createdAt).thenComparing(RoomConditionSnapshot::id));
    }

    @Override public List<RoomConditionItem> findItemsBySnapshotId(UUID snapshotId) {
        return items.getOrDefault(snapshotId, List.of()).stream()
            .sorted(Comparator.comparingInt(RoomConditionItem::position)).toList();
    }

    @Override public void saveItems(UUID snapshotId, List<RoomConditionItem> additions) {
        if (!snapshots.containsKey(snapshotId)) throw new IllegalArgumentException("Không tìm thấy biên bản hiện trạng.");
        items.put(snapshotId, new ArrayList<>(additions));
    }

    public void clear() {
        snapshots.clear();
        items.clear();
    }
}
