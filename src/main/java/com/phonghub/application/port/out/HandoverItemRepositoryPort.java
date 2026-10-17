package com.phonghub.application.port.out;

import com.phonghub.domain.model.HandoverItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HandoverItemRepositoryPort {

    /** Thay toàn bộ danh sách tài sản của biên bản bằng bản chụp mới. */
    void replaceAll(UUID handoverId, List<HandoverItem> items, UUID actorId);

    List<HandoverItem> findByHandoverId(UUID handoverId);

    Optional<HandoverItem> findById(UUID id);

    HandoverItem save(HandoverItem item, UUID actorId);
}
