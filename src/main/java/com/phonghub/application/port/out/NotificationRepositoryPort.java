package com.phonghub.application.port.out;

import com.phonghub.domain.model.Notification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepositoryPort {
    Notification save(Notification notification);
    List<Notification> findAll();
    Optional<Notification> findById(UUID id);
    void deleteById(UUID id);
    long countUnresolved();
}
