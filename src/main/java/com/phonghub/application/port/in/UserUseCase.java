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
    User updateProfile(UpdateProfileCommand command);

    record UpdateProfileCommand(
        UUID userId,
        String fullName,
        String phone
    ) {
        public UpdateProfileCommand {
            if (userId == null) {
                throw new IllegalArgumentException("User id cannot be null");
            }
            if (fullName == null || fullName.isBlank()) {
                throw new IllegalArgumentException("Họ và tên không được để trống");
            }
            if (phone != null && !phone.isBlank()) {
                String cleanPhone = phone.trim();
                if (!cleanPhone.matches("^0\\d{9}$")) {
                    throw new IllegalArgumentException("Số điện thoại không hợp lệ (phải gồm 10 chữ số bắt đầu bằng 0)");
                }
            }
        }
    }

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
