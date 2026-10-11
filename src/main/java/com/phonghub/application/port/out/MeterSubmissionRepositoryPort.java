package com.phonghub.application.port.out;

import com.phonghub.domain.model.MeterSubmission;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MeterSubmissionRepositoryPort {

    MeterSubmission save(MeterSubmission submission);

    Optional<MeterSubmission> findById(UUID id);

    /** Mới nhất trước. */
    List<MeterSubmission> findByRoomId(UUID roomId);

    List<MeterSubmission> findByRoomIds(Collection<UUID> roomIds);
}
