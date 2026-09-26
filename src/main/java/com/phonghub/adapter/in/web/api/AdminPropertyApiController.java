package com.phonghub.adapter.in.web.api;

import com.phonghub.adapter.in.web.api.dto.PropertyResponse;
import com.phonghub.application.port.in.PropertyUseCase;
import com.phonghub.domain.model.Property;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/properties")
public class AdminPropertyApiController {

    private final PropertyUseCase propertyUseCase;

    public AdminPropertyApiController(PropertyUseCase propertyUseCase) {
        this.propertyUseCase = propertyUseCase;
    }

    @PostMapping("/{propertyId}/verify")
    public ResponseEntity<PropertyResponse> verifyProperty(@PathVariable UUID propertyId) {
        Property property = propertyUseCase.verifyProperty(propertyId);
        return ResponseEntity.ok(PropertyResponse.from(property));
    }
}
