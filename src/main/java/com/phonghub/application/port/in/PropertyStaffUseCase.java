package com.phonghub.application.port.in;

import com.phonghub.domain.model.StaffPropertyAssignment;
import com.phonghub.domain.model.User;
import java.util.List;
import java.util.UUID;

/** Owner (hoặc admin) phân công staff / kỹ thuật viên vào nhà trọ của mình và chỉnh quyền của staff. */
public interface PropertyStaffUseCase {

    List<Member> listMembers(UUID propertyId);

    /** Gán theo username hoặc email chính xác; gán lại thì cập nhật quyền. Kỹ thuật viên không có quyền thu tiền/hợp đồng. */
    Member assign(AssignCommand command);

    Member updatePermissions(UUID propertyId, UUID userId, boolean canCollectPayment, boolean canManageContracts);

    /** Gỡ khỏi nhà trọ; bị chặn khi kỹ thuật viên còn phiếu bảo trì đang giao/đang xử lý tại nhà trọ đó. */
    void unassign(UUID propertyId, UUID userId);

    record Member(User user, StaffPropertyAssignment assignment) {}

    record AssignCommand(UUID propertyId, String usernameOrEmail, boolean canCollectPayment, boolean canManageContracts) {
        public AssignCommand {
            if (propertyId == null) {
                throw new IllegalArgumentException("Property id cannot be null");
            }
            if (usernameOrEmail == null || usernameOrEmail.isBlank()) {
                throw new IllegalArgumentException("Cần nhập tên đăng nhập hoặc email của nhân viên");
            }
        }
    }
}
