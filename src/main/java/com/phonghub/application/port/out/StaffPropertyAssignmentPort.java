package com.phonghub.application.port.out;

import com.phonghub.domain.model.StaffPropertyAssignment;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface StaffPropertyAssignmentPort {
    StaffPropertyAssignment save(StaffPropertyAssignment assignment);
    Set<UUID> findPropertyIdsByUserId(UUID userId);
    List<StaffPropertyAssignment> findByUserId(UUID userId);
    List<StaffPropertyAssignment> findByPropertyId(UUID propertyId);
    boolean isUserAssignedToProperty(UUID userId, UUID propertyId);
    void deleteByUserIdAndPropertyId(UUID userId, UUID propertyId);
}
