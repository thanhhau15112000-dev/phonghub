package com.phonghub.application.port.in;

import com.phonghub.domain.model.Property;
import java.util.List;
import java.util.UUID;

public interface PropertyUseCase {
    Property createProperty(CreatePropertyCommand command);
    List<Property> listAccessibleProperties();
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
