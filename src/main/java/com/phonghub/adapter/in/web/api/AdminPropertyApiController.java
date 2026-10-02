package com.phonghub.adapter.in.web.api;

import com.phonghub.adapter.in.web.api.dto.PropertyResponse;
import com.phonghub.application.port.in.PropertyUseCase;
import com.phonghub.domain.model.Property;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/properties")
public class AdminPropertyApiController {

    private final PropertyUseCase propertyUseCase;

    public AdminPropertyApiController(PropertyUseCase propertyUseCase) {
        this.propertyUseCase = propertyUseCase;
    }

    public record RejectPropertyRequest(
        @NotBlank(message = "Lý do từ chối không được để trống")
        String reason
    ) {}

    @PostMapping("/{propertyId}/verify")
    public ResponseEntity<PropertyResponse> verifyProperty(@PathVariable UUID propertyId) {
        Property property = propertyUseCase.verifyProperty(propertyId);
        return ResponseEntity.ok(PropertyResponse.from(property));
    }

    @PostMapping("/{propertyId}/reject")
    public ResponseEntity<PropertyResponse> rejectProperty(
        @PathVariable UUID propertyId,
        @Valid @RequestBody RejectPropertyRequest request
    ) {
        Property property = propertyUseCase.rejectProperty(propertyId, request.reason());
        return ResponseEntity.ok(PropertyResponse.from(property));
    }
}
