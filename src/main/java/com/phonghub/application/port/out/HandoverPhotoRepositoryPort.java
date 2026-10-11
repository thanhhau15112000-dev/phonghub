package com.phonghub.application.port.out;

import com.phonghub.domain.model.HandoverPhoto;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HandoverPhotoRepositoryPort {

    HandoverPhoto save(HandoverPhoto photo);

    Optional<HandoverPhoto> findById(UUID id);

    List<HandoverPhoto> findByHandoverId(UUID handoverId);

    void delete(UUID id);
}
