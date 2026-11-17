package com.phonghub.application.port.in;

import com.phonghub.domain.model.StaffPropertyAssignment;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.util.List;
import java.util.UUID;

/** Owner (hoặc admin) phân công staff / kỹ thuật viên vào nhà trọ của mình và chỉnh quyền của staff. */
public interface PropertyStaffUseCase {

    List<Member> listMembers(UUID propertyId);

    /**
     * Danh sách thả xuống khi phân công (#249): mọi tài khoản STAFF/TECHNICIAN đang hoạt động, kể cả người đang làm cho
     * chủ trọ khác, trừ người đã ở nhà trọ này. Tìm theo họ tên, tên đăng nhập, email hoặc 4 số cuối số điện thoại;
     * mỗi dòng chỉ có họ tên, vai trò và liên hệ đã che.
     */
    List<Candidate> listCandidates(UUID propertyId, String query, int limit);

    /**
     * Gán theo người chọn trong danh sách ({@code userId}, phải nằm trong {@link #listCandidates}) hoặc theo username/email
     * gõ đúng; gán lại theo username/email thì cập nhật quyền. Kỹ thuật viên không có quyền thu tiền/hợp đồng.
     */
    Member assign(AssignCommand command);

    Member updatePermissions(UUID propertyId, UUID userId, boolean canCollectPayment, boolean canManageContracts,
                             boolean canViewDocuments, boolean canManageRoomsAndAssets);

    default Member updatePermissions(UUID propertyId, UUID userId, boolean canCollectPayment,
                                     boolean canManageContracts, boolean canViewDocuments) {
        return updatePermissions(propertyId, userId, canCollectPayment, canManageContracts, canViewDocuments, false);
    }

    /** Gỡ khỏi nhà trọ; bị chặn khi kỹ thuật viên còn phiếu bảo trì đang giao/đang xử lý tại nhà trọ đó. */
    void unassign(UUID propertyId, UUID userId);

    record Member(User user, StaffPropertyAssignment assignment) {}

    record Candidate(UUID userId, String fullName, UserRole role, String maskedContact) {}

    /** Có {@code userId} (chọn từ danh sách) thì dùng nó; không thì dùng {@code usernameOrEmail} gõ tay. */
    record AssignCommand(UUID propertyId, UUID userId, String usernameOrEmail, boolean canCollectPayment,
                         boolean canManageContracts, boolean canViewDocuments, boolean canManageRoomsAndAssets) {
        public AssignCommand(UUID propertyId, String usernameOrEmail, boolean canCollectPayment,
                             boolean canManageContracts, boolean canViewDocuments, boolean canManageRoomsAndAssets) {
            this(propertyId, null, usernameOrEmail, canCollectPayment, canManageContracts, canViewDocuments,
                canManageRoomsAndAssets);
        }

        public AssignCommand(UUID propertyId, String usernameOrEmail, boolean canCollectPayment,
                             boolean canManageContracts, boolean canViewDocuments) {
            this(propertyId, usernameOrEmail, canCollectPayment, canManageContracts, canViewDocuments, false);
        }

        public AssignCommand(UUID propertyId, String usernameOrEmail, boolean canCollectPayment,
                             boolean canManageContracts) {
            this(propertyId, usernameOrEmail, canCollectPayment, canManageContracts, false, false);
        }

        public AssignCommand {
            if (propertyId == null) {
                throw new IllegalArgumentException("Property id cannot be null");
            }
            if (userId == null && (usernameOrEmail == null || usernameOrEmail.isBlank())) {
                throw new IllegalArgumentException(
                    "Cần chọn nhân sự trong danh sách hoặc nhập tên đăng nhập/email của nhân viên");
            }
        }
    }
}
