package com.phonghub.application.port.in;

import com.phonghub.domain.model.StaffPropertyAssignment;
import com.phonghub.domain.model.User;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface UserUseCase {
    List<User> listUsers();
    User getUser(UUID userId);
    StaffPropertyAssignment assignStaffToProperty(AssignStaffCommand command);
    List<StaffPropertyAssignment> getStaffAssignments(UUID staffUserId);
    Set<UUID> getAssignedPropertyIds(UUID userId);

    record AssignStaffCommand(
        UUID staffUserId,
        UUID propertyId,
        boolean canCollectPayment,
        boolean canManageContracts
    ) {
        public AssignStaffCommand {
            if (staffUserId == null) {
                throw new IllegalArgumentException("Staff user id cannot be null");
            }
            if (propertyId == null) {
                throw new IllegalArgumentException("Property id cannot be null");
            }
        }
    }
}
