package com.phonghub.application.port.in;

import com.phonghub.domain.model.Property;
import java.util.List;
import java.util.UUID;

public interface PropertyUseCase {
    Property createProperty(CreatePropertyCommand command);
    List<Property> listAccessibleProperties();
    Property getProperty(UUID propertyId);
    Property verifyProperty(UUID propertyId);

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
