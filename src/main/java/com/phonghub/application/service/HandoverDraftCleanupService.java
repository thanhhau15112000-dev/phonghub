package com.phonghub.application.service;

import com.phonghub.application.port.out.DraftHandoverCleanupPort;
import com.phonghub.application.port.out.FileStoragePort;
import com.phonghub.application.port.out.HandoverItemRepositoryPort;
import com.phonghub.application.port.out.HandoverPhotoRepositoryPort;
import com.phonghub.application.port.out.HandoverRepositoryPort;
import com.phonghub.domain.exception.InvalidHandoverStateException;
import com.phonghub.domain.model.HandoverStatus;
import com.phonghub.domain.model.HandoverType;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Cleans an unsubmitted check-in draft and its private photo objects when a contract draft is cancelled. */
public class HandoverDraftCleanupService implements DraftHandoverCleanupPort {

    private static final Logger log = LoggerFactory.getLogger(HandoverDraftCleanupService.class);

    private final HandoverRepositoryPort handovers;
    private final HandoverPhotoRepositoryPort photos;
    private final HandoverItemRepositoryPort items;
    private final FileStoragePort storage;

    public HandoverDraftCleanupService(HandoverRepositoryPort handovers, HandoverPhotoRepositoryPort photos,
                                       HandoverItemRepositoryPort items, FileStoragePort storage) {
        this.handovers = handovers;
        this.photos = photos;
        this.items = items;
        this.storage = storage;
    }

    @Override
    public void cancelDraftCheckIn(UUID contractId, UUID actorId) {
        var handover = handovers.find(contractId, HandoverType.CHECK_IN).orElse(null);
        if (handover == null) return;
        if (handover.status() != HandoverStatus.DRAFT) {
            throw new InvalidHandoverStateException(
                "Không thể hủy hợp đồng nháp vì biên bản check-in đã được gửi. Hãy xử lý biên bản trước.");
        }
        List<com.phonghub.domain.model.HandoverPhoto> attachments = photos.findByHandoverId(handover.id());
        items.replaceAll(handover.id(), List.of(), actorId);
        for (var photo : attachments) {
            photos.delete(photo.id());
            try {
                storage.delete(photo.imageKey());
            } catch (RuntimeException ex) {
                log.warn("Could not remove private draft check-in image {} for handover {}", photo.imageKey(),
                    handover.id(), ex);
            }
        }
        handovers.delete(handover.id());
    }
}
