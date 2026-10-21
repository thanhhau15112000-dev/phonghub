package com.phonghub.application.port.in;

import com.phonghub.domain.model.Property;
import java.util.List;
import java.util.UUID;

public interface PropertyUseCase {
    Property createProperty(CreatePropertyCommand command);

    /** Nhà trọ đang hoạt động trong phạm vi người dùng; nhà trọ đã ẩn xem ở {@link #listArchivedProperties()}. */
    List<Property> listAccessibleProperties();

    /** Nhà trọ đã ẩn mà người dùng có quyền quản lý (chủ trọ: nhà của mình, admin: tất cả). */
    List<Property> listArchivedProperties();

    /**
     * Ẩn (xóa mềm) nhà trọ (#89), chỉ chủ nhà trọ hoặc admin. Còn hợp đồng chưa kết thúc (nháp, hiệu lực, hết hạn chưa
     * trả phòng) hoặc hóa đơn chưa thu xong thì từ chối (409). Hợp đồng, hóa đơn cũ được giữ nguyên.
     */
    Property archiveProperty(UUID propertyId);

    /** Khôi phục nhà trọ đã ẩn (chủ nhà trọ hoặc admin). */
    Property restoreProperty(UUID propertyId);

    Property getProperty(UUID propertyId);
    Property verifyProperty(UUID propertyId);
    Property rejectProperty(UUID propertyId, String reason);

    /**
     * Sửa thông tin nhà trọ (owner của nhà trọ hoặc admin). Owner đổi tên hoặc địa chỉ của nhà trọ đã duyệt thì nhà trọ
     * quay về PENDING chờ duyệt lại; nhà trọ bị từ chối được gửi duyệt lại khi owner sửa. Admin sửa không đổi trạng thái.
     */
    Property updateProperty(UpdatePropertyCommand command);

    record UpdatePropertyCommand(UUID propertyId, String name, String address, String description, Integer totalRooms) {
        public UpdatePropertyCommand {
            if (propertyId == null) {
                throw new IllegalArgumentException("Property id is required");
            }
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Property name cannot be blank");
            }
            if (address == null || address.isBlank()) {
                throw new IllegalArgumentException("Property address cannot be blank");
            }
            if (totalRooms != null && totalRooms < 0) {
                throw new IllegalArgumentException("Total rooms must be non-negative");
            }
        }
    }

    record CreatePropertyCommand(
        String name,
        String address,
        String description,
        int totalRooms
    ) {
        public CreatePropertyCommand {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Property name cannot be blank");
            }
            if (address == null || address.isBlank()) {
                throw new IllegalArgumentException("Property address cannot be blank");
            }
            if (totalRooms < 0) {
                throw new IllegalArgumentException("Total rooms must be non-negative");
            }
        }
    }
}
